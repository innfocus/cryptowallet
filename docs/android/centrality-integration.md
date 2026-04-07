# Centrality (CENNZ/CPAY) Integration cho Android

Hướng dẫn tích hợp Centrality (CENNZnet) vào ứng dụng Android, bao gồm gửi/nhận CENNZ và CPAY — 2 token trên cùng 1 chain, cùng 1 address.

> **Tài liệu kỹ thuật chi tiết:** [Centrality spec](../chains/centrality.md)
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

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

Config.shared.setNetwork(Network.MAINNET)
CoinsManager.shared.updateMnemonic("your mnemonic words ...")
```

### 2.2 Qua CommonCoinsManager (KMP)

```kotlin
import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.enums.NetworkName

CommonCoinsManager.initialize("your mnemonic words ...")
val ccm = CommonCoinsManager.shared
```

### 2.3 Trực tiếp qua CentralityManager (advanced)

```kotlin
import com.lybia.cryptowallet.wallets.centrality.CentralityManager

val centralityManager = CentralityManager(mnemonic = "your mnemonic words ...")
```

---

## 3. Multi-Asset Pattern: CENNZ vs CPAY

Centrality có **2 token trên cùng 1 chain**, dùng cùng address và key pair. Phân biệt bằng `assetId`:

| Token | assetId | Mô tả |
|-------|---------|-------|
| **CENNZ** | 1 | Token chính của CENNZnet |
| **CPAY** | 2 | Token thanh toán dịch vụ |

```kotlin
// ACTCoin enum
ACTCoin.Centrality  // assetId = 1, symbolName = "CENNZ"
ACTCoin.CPAY        // assetId = 2, symbolName = "CPAY"

// Hoặc dùng constant
CentralityManager.ASSET_CENNZ  // 1
CentralityManager.ASSET_CPAY   // 2
```

> **Quan trọng:** Mọi method (balance, history, send) đều nhận `assetId` để phân biệt CENNZ vs CPAY.

---

## 4. Lấy địa chỉ ví

Address Centrality là **SS58 format**, bắt đầu bằng "5", dài ~48 ký tự.

```kotlin
// Qua CoinsManager (async — address được populate ở background)
val addresses = CoinsManager.shared.addresses()
val cennzAddress = addresses[ACTCoin.Centrality]  // Cùng address cho CENNZ và CPAY

// Qua CommonCoinsManager (suspend)
viewModelScope.launch {
    val address = ccm.getAddressAsync(NetworkName.CENTRALITY)
    Log.d("CENNZ", "Address: $address")
    // "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY" (ví dụ)
}
```

> **Lưu ý:** Address derivation cần gọi API bên ngoài (`fgwallet.srsfc.com`), nên là async.
> CENNZ và CPAY dùng **cùng 1 address** — không cần lấy address riêng.

### Derivation
- **Algorithm:** Sr25519
- **Seed:** PBKDF2-SHA512 từ BIP-39 mnemonic
- **Format:** SS58 (Base58 + Blake2b-512 checksum, network prefix = 42)

---

## 5. Lấy số dư

### 5.1 Qua CoinsManager (callback)

```kotlin
// Balance CENNZ
CoinsManager.shared.getBalance(ACTCoin.Centrality, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        Log.d("CENNZ", "CENNZ Balance: $balance")
    }
})

// Balance CPAY (cùng address, khác assetId)
CoinsManager.shared.getBalance(ACTCoin.CPAY, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        Log.d("CENNZ", "CPAY Balance: $balance")
    }
})
```

### 5.2 Qua CommonCoinsManager (suspend)

```kotlin
viewModelScope.launch {
    // CENNZ
    val cennzResult = ccm.getCentralityBalance(address, CentralityManager.ASSET_CENNZ)
    Log.d("CENNZ", "CENNZ: ${cennzResult.balance}")

    // CPAY
    val cpayResult = ccm.getCentralityBalance(address, CentralityManager.ASSET_CPAY)
    Log.d("CENNZ", "CPAY: ${cpayResult.balance}")
}
```

> **Quy đổi:** 1 CENNZ/CPAY = 10,000 smallest units (BASE_UNIT). API trả về đã quy đổi.

---

## 6. Lịch sử giao dịch

### 6.1 Không phân trang

```kotlin
// Qua CoinsManager (callback)
CoinsManager.shared.getTransactions(ACTCoin.Centrality, null, object : TransactionsHandle {
    override fun completionHandler(transactions: Array<TransationData>?, moreParam: JsonObject?, errStr: String) {
        transactions?.forEach { tx ->
            Log.d("CENNZ", "TX: ${tx.iD}, Amount: ${tx.amount}")
        }
    }
})
```

### 6.2 Có phân trang

```kotlin
viewModelScope.launch {
    // Trang đầu — CENNZ transactions
    val page1 = ccm.getTransactionHistoryPaginated(
        coin = NetworkName.CENTRALITY,
        limit = 100,
        pageParam = mapOf("page" to 0, "assetId" to CentralityManager.ASSET_CENNZ)
    )

    // Trang tiếp
    if (page1.hasMore) {
        val page2 = ccm.getTransactionHistoryPaginated(
            coin = NetworkName.CENTRALITY,
            limit = 100,
            pageParam = page1.nextPageParam  // {"page": 1, "assetId": 1}
        )
    }

    // CPAY transactions
    val cpayTxs = ccm.getTransactionHistoryPaginated(
        coin = NetworkName.CENTRALITY,
        limit = 100,
        pageParam = mapOf("page" to 0, "assetId" to CentralityManager.ASSET_CPAY)
    )
}
```

---

## 7. Gửi CENNZ / CPAY

### 7.1 Qua CoinsManager (callback)

```kotlin
// Gửi CENNZ
CoinsManager.shared.sendCoin(
    coin = ACTCoin.Centrality,
    toAddress = "5RecipientAddr...",
    amount = 100.0,      // 100 CENNZ
    fee = 15287.0        // fee mặc định
) { txHash, success, error ->
    if (success) {
        Log.d("CENNZ", "TX: $txHash")
    }
}

// Gửi CPAY
CoinsManager.shared.sendCoin(
    coin = ACTCoin.CPAY,
    toAddress = "5RecipientAddr...",
    amount = 50.0,       // 50 CPAY
    fee = 15287.0
) { txHash, success, error -> ... }
```

### 7.2 Qua CommonCoinsManager (suspend)

```kotlin
viewModelScope.launch {
    // Gửi CENNZ
    val result = ccm.sendCentrality(
        fromAddress = myAddress,
        toAddress = "5RecipientAddr...",
        amount = 100.0,
        assetId = CentralityManager.ASSET_CENNZ
    )
    Log.d("CENNZ", "TX: ${result.txHash}, Success: ${result.success}")

    // Gửi CPAY
    val cpayResult = ccm.sendCentrality(
        fromAddress = myAddress,
        toAddress = "5RecipientAddr...",
        amount = 50.0,
        assetId = CentralityManager.ASSET_CPAY
    )
}
```

### 7.3 Transaction Signing Flow (internal)

```
1. Lấy chain state (runtime version, genesis hash, finalized block, nonce)
2. Build extrinsic (callIndex=0x0401 + assetId + recipient + amount)
3. Tạo signing payload (method + era + nonce + chain metadata)
4. Ký bằng Sr25519 (qua external API fgwallet.srsfc.com)
5. Inject signature vào extrinsic
6. SCALE encode → hex
7. Submit qua RPC author_submitExtrinsic
```

> **Lưu ý:** Signing được thực hiện qua external API, không phải local. Cần kết nối mạng.

---

## 8. Fee Estimation

```kotlin
// Fee mặc định (static)
val defaultFee = ACTCoin.Centrality.feeDefault()  // 15287.0 (smallest units)
// = 1.5287 CENNZ (/ 10,000)

// Qua CommonCoinsManager
viewModelScope.launch {
    val fee = ccm.estimateFee(NetworkName.CENTRALITY, amount = 100.0)
    Log.d("CENNZ", "Fee: ${fee.fee}")
}
```

> **Lưu ý:** Hiện tại chỉ trả về fee mặc định. Dynamic fee qua RPC `payment_queryInfo` chưa được expose.

---

## 9. Threading

- `CoinsManager` chạy trên `Dispatchers.IO`, callback trả về trên `Dispatchers.Main`
- `CommonCoinsManager` methods là `suspend fun` — gọi trong `viewModelScope.launch {}`
- Address derivation là **async** — cần `getAddressAsync()`, không dùng `getAddress()` đồng bộ

```kotlin
viewModelScope.launch {
    val address = ccm.getAddressAsync(NetworkName.CENTRALITY)
    val balance = ccm.getCentralityBalance(address, CentralityManager.ASSET_CENNZ)
    // Update UI trực tiếp
}
```

---

## 10. Network & Explorer

| Mục | Endpoint |
|---|---|
| RPC Node | `cennznet.unfrastructure.io/public` |
| Explorer API | `service.eks.centralityapp.com/cennznet-explorer-api/api` |
| Signing Service | `fgwallet.srsfc.com` |

> **Lưu ý:** Centrality hiện chỉ hỗ trợ **mainnet**. Chưa có config testnet.

---

## 11. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Address trả về rỗng | API derivation fail | Kiểm tra kết nối mạng, retry `getAddressAsync()` |
| `CentralityError.SigningFailed` | External signing API down | Kiểm tra kết nối đến `fgwallet.srsfc.com` |
| Balance = 0 dù có tiền | Sai `assetId` | CENNZ = 1, CPAY = 2 — kiểm tra đúng asset |
| TX history lẫn lộn | Không filter `assetId` | Truyền `assetId` vào `getTransactionHistoryPaginated` |
| `CentralityError.InvalidSS58Address` | Address sai format | Phải bắt đầu bằng "5", dài ~48 ký tự |
| Fee không đủ | CPAY không đủ trả phí | Cần CPAY trong ví để trả TX fee |

---

## 12. So sánh CENNZ vs CPAY

| Thuộc tính | CENNZ | CPAY |
|-----------|-------|------|
| Asset ID | 1 | 2 |
| Công dụng | Token chính | Token thanh toán |
| Address | Chung | Chung |
| Key pair | Chung | Chung |
| Fee | 15,287 units | 15,287 units |
| Unit value | 10,000 | 10,000 |
| Memo | Không hỗ trợ | Không hỗ trợ |
