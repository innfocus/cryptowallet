# TON Integration cho Android

Hướng dẫn tích hợp TON (The Open Network) vào ứng dụng Android, bao gồm gửi/nhận TON, Jetton (token), NFT, staking, và DNS.

> **Tài liệu kỹ thuật chi tiết:** [TON spec](../chains/ton.md)
> **CommonCoinsManager API:** [API Reference](../api/common-coins-manager.md)

---

## 1. Dependency

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib-android:1.0.2'
}
```

---

## 2. Khởi tạo

### 2.1 Qua CoinsManager (recommended)

`CoinsManager` là entry point chính trên Android. TON được delegate sang `CommonCoinsManager` bên trong.

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

// Chọn network
Config.shared.setNetwork(Network.MAINNET) // hoặc Network.TESTNET

// Set mnemonic (BIP-39 12 từ hoặc TON native 24 từ)
CoinsManager.shared.updateMnemonic("your mnemonic words ...")
```

### 2.2 Qua CommonCoinsManager (KMP, recommended cho code mới)

```kotlin
import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.enums.NetworkName

CommonCoinsManager.initialize("your mnemonic words ...")
val ccm = CommonCoinsManager.shared
```

### 2.3 Trực tiếp qua TonManager (advanced)

Dùng khi cần truy cập API chi tiết (W5 signing, DNS, pool detection).

```kotlin
import com.lybia.cryptowallet.wallets.ton.TonManager
import com.lybia.cryptowallet.wallets.ton.WalletVersion

val tonManager = TonManager("your mnemonic words ...", WalletVersion.W5)
```

---

## 3. Lấy địa chỉ ví

TON là account-based — chỉ cần **1 address**. Address **khác nhau** giữa mainnet/testnet khi dùng W5.

```kotlin
// Qua CommonCoinsManager
val address = ccm.getAddress(NetworkName.TON)
// Trả về: UQ... hoặc EQ... (Base64 user-friendly, non-bounceable)

// Qua TonManager
val address = tonManager.getAddress()
```

> **Wallet Version:**
> - **W5 (V5R1):** Mặc định. Address khác nhau giữa mainnet/testnet (do wallet_id encode networkGlobalId).
> - **W4 (V4R2):** Legacy. Address giống nhau trên cả 2 network.
>
> **Mnemonic:**
> - 24 từ: TON native mnemonic (không phải BIP-39)
> - 12 từ: BIP-39 + SLIP-0010 path `m/44'/607'/0'` (tương thích Tonkeeper)

---

## 4. Lấy số dư

```kotlin
// Qua CommonCoinsManager (suspend)
viewModelScope.launch {
    val result = ccm.getBalance(NetworkName.TON)
    if (result.success) {
        Log.d("TON", "Balance: ${result.balance} TON")
    }
}

// Qua CoinsManager (callback)
CoinsManager.shared.getBalance(ACTCoin.TON, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        Log.d("TON", "Balance: $balance TON")
    }
})
```

> **Quy đổi:** 1 TON = 1,000,000,000 nanoTON. API trả về đã quy đổi sang TON (Double).

---

## 5. Lịch sử giao dịch

### 5.1 Không phân trang

```kotlin
viewModelScope.launch {
    val result = ccm.getTransactionHistory(NetworkName.TON)
    // result là List<TonTransaction>
}
```

### 5.2 Có phân trang (cursor-based)

```kotlin
viewModelScope.launch {
    // Trang đầu
    val page1 = ccm.getTransactionHistoryPaginated(
        coin = NetworkName.TON,
        limit = 20
    )

    // Trang tiếp theo
    if (page1.hasMore) {
        val page2 = ccm.getTransactionHistoryPaginated(
            coin = NetworkName.TON,
            limit = 20,
            pageParam = page1.nextPageParam // {"lt": "...", "hash": "..."}
        )
    }
}
```

---

## 6. Gửi TON

### 6.1 Qua CommonCoinsManager (recommended)

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoin(
        coin = NetworkName.TON,
        toAddress = "UQRecipient...",
        amount = 1.5,                          // 1.5 TON
        memo = MemoData("Payment for order #42")
    )
    if (result.success) {
        Log.d("TON", "TX: ${result.txHash}")
    }
}
```

### 6.2 Với smallest unit (nanoTON)

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoinExact(
        coin = NetworkName.TON,
        toAddress = "UQRecipient...",
        amountSmallestUnit = 1_500_000_000L,  // 1.5 TON in nanoTON
        memo = MemoData("Exact amount")
    )
}
```

### 6.3 Với Service Fee

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoin(
        coin = NetworkName.TON,
        toAddress = "UQRecipient...",
        amount = 5.0,
        serviceAddress = "UQServiceAddr...",
        serviceFee = 0.1  // 0.1 TON phí dịch vụ
    )
    // Nội bộ: 2 transaction riêng biệt (main + service fee)
}
```

### 6.4 Nâng cao (qua TonManager)

```kotlin
viewModelScope.launch {
    val coinNetwork = CoinNetwork(NetworkName.TON)
    val seqno = tonManager.getSeqno(coinNetwork)
    val boc = tonManager.signTransaction(
        toAddress = "UQRecipient...",
        amountNano = 1_500_000_000L,
        seqno = seqno,
        memo = "Hello TON"
    )
    val result = tonManager.transfer(boc, coinNetwork)
    // result.success, result.txHash
}
```

### 6.5 Address Validation

```kotlin
// Validate trước khi gửi — tránh mất tiền do sai address
val isValid = ccm.isValidTonAddress("UQRecipient...")
// hoặc: TonManager.isValidTonAddress("UQRecipient...")
```

### 6.6 Send với on-chain confirmation

```kotlin
viewModelScope.launch {
    val result = ccm.sendCoinWithConfirmation(
        coin = NetworkName.TON,
        toAddress = "UQRecipient...",
        amount = 1.5
    )
    // result.txHash = real hash (không phải "pending") khi đã confirm
    // Timeout mặc định: 60s (12 lần poll × 5s)
}
```

### 6.7 Bulk Transfer (gửi nhiều người nhận trong 1 tx)

```kotlin
viewModelScope.launch {
    val result = ccm.sendBulkTransfer(listOf(
        TonDestination("UQAddr1...", 1_000_000_000L, memo = "Payment 1"),
        TonDestination("UQAddr2...", 500_000_000L),
        TonDestination("UQAddr3...", 2_000_000_000L, memo = "Payment 3"),
    ))
    // Chỉ hỗ trợ W5 wallet. Tối đa 255 recipients/tx.
    // Tiết kiệm fee so với gửi từng tx riêng.
}
```

---

## 7. Jetton (Token)

### 7.1 Lấy metadata

```kotlin
viewModelScope.launch {
    val metadata = ccm.getJettonMetadata("EQJettonMasterAddr...")
    // metadata?.name = "Tether USD"
    // metadata?.symbol = "USDT"
    // metadata?.decimals = 6
    // metadata?.image = "https://..."
}
```

### 7.2 Lấy số dư token

```kotlin
viewModelScope.launch {
    val result = ccm.getTokenBalance(
        coin = NetworkName.TON,
        address = address,
        contractAddress = "EQJettonMasterAddr..."
    )
    Log.d("TON", "USDT balance: ${result.balance}")
}
```

> **Lưu ý decimals:** `getTokenBalance` dùng default 9. Với USDT (6 decimals), kết quả sẽ sai 1000x.
> Dùng `TonManager.getBalanceToken(addr, contract, coinNetwork, decimals = 6)` trực tiếp để chính xác.

### 7.3 Gửi Jetton (recommended)

```kotlin
viewModelScope.launch {
    val result = ccm.sendJetton(
        toAddress = "UQRecipient...",
        jettonMasterAddress = "EQJettonMasterAddr...",
        amount = 10.5,       // 10.5 USDT
        decimals = 6,        // USDT = 6, mặc định = 9
        memo = "Payment"
    )
    if (result.success) {
        Log.d("TON", "Jetton sent: ${result.txHash}")
    }
}
```

### 7.4 Lịch sử giao dịch Jetton (phân trang)

```kotlin
viewModelScope.launch {
    val page1 = ccm.getTokenTransactionHistoryPaginated(
        coin = NetworkName.TON,
        policyId = "EQJettonMasterAddr...",  // Jetton Master address
        assetName = "",
        limit = 20
    )
    // page1.transactions là List<JettonTransactionParsed>
    // Mỗi item có: type (send/receive/burn), amountNano, sender, recipient, memo
}
```

### 7.5 Qua TonService (callback, legacy)

```kotlin
val tonService = TonService(
    mnemonicProvider = { CoinsManager.shared.currentMnemonic },
    scope = CoinsManager.shared  // CoroutineScope
)

// Balance
tonService.getTokenBalance(address, "EQJettonMaster...") { tokenInfo, success, error ->
    // tokenInfo.balance, tokenInfo.symbol, tokenInfo.decimals
}

// Send
tonService.sendToken(
    toAddress = "UQ...",
    contractAddress = "EQ...",
    amount = 10.5,
    decimals = 6,
    memo = "Payment"
) { txHash, success, error -> ... }
```

---

## 8. NFT

### 8.1 Lấy danh sách NFT

```kotlin
viewModelScope.launch {
    val nfts = ccm.getNFTs(NetworkName.TON, address)
    nfts?.forEach { nft ->
        Log.d("TON", "NFT: ${nft.name} - ${nft.imageUrl}")
    }
}
```

### 8.2 Transfer NFT

```kotlin
viewModelScope.launch {
    val result = ccm.transferNFT(
        coin = NetworkName.TON,
        nftAddress = "EQNftContractAddr...",
        toAddress = "UQRecipient...",
        memo = "Gift"
    )
}
```

---

## 9. Staking

### 9.1 Detect pool type

```kotlin
viewModelScope.launch {
    val poolType = ccm.detectTonPoolType("EQPoolAddr...")
    // NOMINATOR, TONSTAKERS, BEMO, UNKNOWN
}
```

### 9.2 Stake

```kotlin
viewModelScope.launch {
    val result = ccm.stake(
        coin = NetworkName.TON,
        amount = 10_000_000_000L,  // 10 TON in nanoTON
        poolAddress = "EQPoolAddr..."
    )
    // Tự động detect pool type và gọi đúng method
}
```

### 9.3 Unstake

```kotlin
viewModelScope.launch {
    // poolAddress BẮT BUỘC cho TON (Tonstakers/Bemo)
    val result = ccm.unstake(
        coin = NetworkName.TON,
        amount = 5_000_000_000L,
        poolAddress = "EQPoolAddr..."
    )
}
```

> **Lưu ý:**
> - **Tonstakers/Bemo:** Unstake bằng cách burn staking tokens (tsTON/stTON)
> - **Nominator:** Không cần gọi unstake — pool tự trả tiền khi hết epoch

### 9.4 Lấy staking balance

```kotlin
viewModelScope.launch {
    val result = ccm.getStakingBalance(
        coin = NetworkName.TON,
        poolAddress = "EQPoolAddr..."
    )
    // result.balance = gốc + lãi quy về TON
}
```

---

## 10. DNS

### 10.1 Resolve domain

```kotlin
viewModelScope.launch {
    val address = ccm.resolveTonDns("alice.ton")
    // "UQ..." hoặc null nếu không tìm thấy
}
```

### 10.2 Reverse resolve

```kotlin
viewModelScope.launch {
    val domain = ccm.reverseResolveTonDns("UQAddress...")
    // "alice.ton" hoặc null
}
```

---

## 11. Fee Estimation

### 11.1 Tổng fee
```kotlin
viewModelScope.launch {
    val fee = ccm.estimateFee(
        coin = NetworkName.TON,
        amount = 1.0,
        toAddress = "UQRecipient..."
    )
    Log.d("TON", "Estimated fee: ${fee.fee} ${fee.unit}")
}
```

### 11.2 Fee breakdown chi tiết

```kotlin
viewModelScope.launch {
    val breakdown = ccm.estimateTonFeeDetailed(
        toAddress = "UQRecipient...",
        amount = 1.0
    )
    if (breakdown != null) {
        Log.d("TON", """
            Fee breakdown:
              in_fwd_fee:  ${breakdown.inFwdFee} TON   (forward message vào)
              storage_fee: ${breakdown.storageFee} TON  (lưu trữ state)
              gas_fee:     ${breakdown.gasFee} TON      (TVM computation)
              fwd_fee:     ${breakdown.fwdFee} TON      (forward message ra)
              ─────────────────────────────
              Source total: ${breakdown.totalSourceFee} TON
              Dest fees:   ${breakdown.destinationFees.size} entries
              TOTAL:       ${breakdown.totalFee} TON
        """.trimIndent())
    }
}
```

---

## 12. Threading

- `CommonCoinsManager` methods đều là `suspend fun` — gọi trong `viewModelScope.launch {}` hoặc bất kỳ `CoroutineScope` nào.
- `CoinsManager` (legacy) chạy trên `Dispatchers.IO`, callback trả về trên `Dispatchers.Main`.
- `TonService` (callback bridge) cũng chạy IO + callback Main.

```kotlin
viewModelScope.launch {
    val balance = ccm.getBalance(NetworkName.TON)
    // Update UI trực tiếp (viewModelScope mặc định Main)
}
```

---

## 13. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| RPC v2 | `toncenter.com/api/v2/jsonRPC` | `testnet.toncenter.com/api/v2/jsonRPC` |
| RPC v3 | `toncenter.com/api/v3` | `testnet.toncenter.com/api/v3` |
| Explorer | [tonscan.org](https://tonscan.org) | [testnet.tonscan.org](https://testnet.tonscan.org) |

Chuyển đổi network:
```kotlin
Config.shared.setNetwork(Network.TESTNET)
// Tất cả endpoint tự động đổi sang testnet
```

API key (tùy chọn, tăng rate limit):
```kotlin
Config.shared.apiKeyToncenter = "your-api-key"
```

---

## 14. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| `WalletError.NetworkError` | API fail khi lấy seqno | Retry — có thể do rate limit hoặc mất mạng |
| Balance token sai 1000x | Dùng default decimals (9) cho USDT (6) | Truyền `decimals = 6` khi gọi `getBalanceToken` hoặc `sendJetton` |
| TX fail sau khi sign | `validUntil` hết hạn (60s) | Sign và broadcast ngay, không delay |
| NFT transfer fail | Không đủ TON cho gas | Cần tối thiểu 0.1 TON trong ví để transfer NFT |
| Unstake throw error | Thiếu `poolAddress` | Truyền `poolAddress` cho `unstake()` (bắt buộc với TON) |
| Address khác mainnet/testnet | W5 wallet_id encode network | Đúng behavior — W5 address phụ thuộc network |
