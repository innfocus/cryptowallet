# Ripple (XRP) Integration cho Android

Hướng dẫn tích hợp Ripple (XRP) vào ứng dụng Android, bao gồm gửi/nhận XRP, destination tag, memo, và service fee.

> **Tài liệu kỹ thuật chi tiết:** [Ripple spec](../chains/ripple.md)

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

`CoinsManager` là entry point chính trên Android. Ripple được delegate sang `CommonCoinsManager` bên trong.

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

// Chọn network
Config.shared.setNetwork(Network.MAINNET) // hoặc Network.TESTNET

// Set mnemonic (BIP-39, 12 hoặc 24 từ)
CoinsManager.shared.updateMnemonic("your mnemonic words ...")
```

### 2.2 Trực tiếp qua RippleManager (advanced)

Dùng khi cần truy cập API chi tiết hơn (fee estimation, reliable submission, pagination).

```kotlin
import com.lybia.cryptowallet.wallets.ripple.RippleManager

val mnemonic = "your mnemonic words ..."
val rippleManager = RippleManager(mnemonic)
```

---

## 3. Lấy địa chỉ ví

XRP là account-based — chỉ cần **1 địa chỉ** duy nhất (không có change address như Bitcoin).

```kotlin
// Qua CoinsManager
val address = CoinsManager.shared.addresses()[ACTCoin.Ripple]
// Trả về: r... (Base58Ripple, 25-35 ký tự)

// Qua RippleManager
val address = rippleManager.getAddress()
// Trả về: "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh" (ví dụ)
```

> **Derivation path:** `m/44'/144'/0'/0/0` (BIP-44, coin_type = 144, secp256k1)
>
> **Lưu ý:** Địa chỉ XRP giống nhau trên Mainnet và Testnet — chỉ khác RPC endpoint.

---

## 4. Lấy số dư

```kotlin
import com.lybia.cryptowallet.coinkits.BalanceHandle

// Qua CoinsManager — trả về XRP (Double)
CoinsManager.shared.getBalance(ACTCoin.Ripple, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        if (success) {
            Log.d("XRP", "Balance: $balance XRP")
        }
    }
})

// Qua RippleManager (suspend function)
viewModelScope.launch {
    val balance = rippleManager.getBalance() // Double, đơn vị XRP
    Log.d("XRP", "Balance: $balance XRP")
}
```

> **Quy đổi:** 1 XRP = 1,000,000 drops. API trả về đã quy đổi sang XRP.
>
> **Reserve:** Account cần tối thiểu **10 XRP** để kích hoạt trên ledger. Số dư khả dụng = balance - 10 XRP.

---

## 5. Lịch sử giao dịch

```kotlin
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.TransationData

// Qua CoinsManager
CoinsManager.shared.getTransactions(ACTCoin.Ripple, null, object : TransactionsHandle {
    override fun completionHandler(
        transactions: Array<TransationData>?,
        moreParam: JsonObject?,
        errStr: String
    ) {
        transactions?.forEach { tx ->
            Log.d("XRP", "Hash: ${tx.iD}, Amount: ${tx.amount}")
        }
    }
})
```

### Phân trang (qua RippleManager)

```kotlin
viewModelScope.launch {
    // Trang đầu
    val (transactions, nextMarker) = rippleManager.getTransactionHistoryPaginated(
        address = address,
        limit = 100,
        marker = null
    )

    // Trang tiếp theo (nếu nextMarker != null)
    if (nextMarker != null) {
        val (page2, marker2) = rippleManager.getTransactionHistoryPaginated(
            address = address,
            limit = 100,
            marker = nextMarker
        )
    }
}
```

---

## 6. Ước lượng phí (Fee Estimation)

XRP có phí rất thấp và tương đối ổn định. Có 2 cách:

```kotlin
// Cách 1: Dùng fee mặc định
val defaultFee = ACTCoin.Ripple.feeDefault() // 0.000012 XRP (12 drops)

// Cách 2: Dynamic fee từ network (qua RippleManager)
viewModelScope.launch {
    val feeDynamic = rippleManager.estimateFeeDynamic() // Long, đơn vị drops
    Log.d("XRP", "Network fee: $feeDynamic drops")
}
```

> **Lưu ý:** Khi có service fee, tổng phí = `fee * 2` (vì gửi 2 transaction).

---

## 7. Gửi XRP

### 7.1 Cơ bản (qua CoinsManager)

```kotlin
CoinsManager.shared.sendCoin(
    coin = ACTCoin.Ripple,
    toAddress = "rRecipient...",
    amount = 5.0,            // 5 XRP
    fee = 0.000012           // 12 drops
) { txHash, error ->
    if (error == null) {
        Log.d("XRP", "TX submitted: $txHash")
    } else {
        Log.e("XRP", "Error: $error")
    }
}
```

### 7.2 Nâng cao (qua RippleManager)

```kotlin
viewModelScope.launch {
    val result = rippleManager.sendXrp(
        toAddress = "rRecipient...",
        amountDrops = 5_000_000L,       // 5 XRP
        feeDrops = 0L,                  // 0 = tự estimate
        destinationTag = 12345L,        // tùy chọn (bắt buộc khi gửi cho sàn)
        memoText = "Invoice #42"        // tùy chọn
    )

    if (result.success) {
        Log.d("XRP", "TX hash: ${result.hash}")
    } else {
        Log.e("XRP", "Error: ${result.error}")
    }
}
```

### 7.3 Với Destination Tag

**Destination Tag** là số nguyên 32-bit dùng để phân biệt người nhận khi nhiều user dùng chung 1 address (thường gặp ở sàn giao dịch).

```kotlin
val result = rippleManager.sendXrp(
    toAddress = "rExchangeAddress...",
    amountDrops = 10_000_000L,       // 10 XRP
    destinationTag = 987654321L      // BẮT BUỘC khi sàn yêu cầu
)
```

> **Cảnh báo:** Gửi XRP đến sàn mà **thiếu Destination Tag** có thể dẫn đến **mất tiền**.

### 7.4 Với Service Fee

Khi ứng dụng thu phí dịch vụ, library tự động gửi 2 transaction:

```kotlin
val result = rippleManager.sendXrp(
    toAddress = "rRecipient...",
    amountDrops = 5_000_000L,           // 5 XRP cho người nhận
    serviceAddress = "rServiceAddr...", // Địa chỉ nhận phí dịch vụ
    serviceFeeDrops = 100_000L          // 0.1 XRP service fee
)
// Nội bộ:
// TX 1: gửi 5 XRP cho rRecipient (sequence = N)
// TX 2: gửi 0.1 XRP cho rServiceAddr (sequence = N+1, fire-and-forget)
```

### 7.5 Reliable Submission (đợi confirmed)

```kotlin
val result = rippleManager.sendXrp(
    toAddress = "rRecipient...",
    amountDrops = 5_000_000L,
    awaitValidated = true    // Poll cho đến khi TX confirmed hoặc expired
)
// result.success = true khi TX đã validated trên ledger
// Timeout: ~80 giây (20 lần poll * 4 giây/lần)
```

---

## 8. Threading

- `CoinsManager` chạy trên `Dispatchers.IO`, callback trả về trên `Dispatchers.Main`.
- `RippleManager` là suspend functions — gọi trong `CoroutineScope` hoặc `viewModelScope`:

```kotlin
viewModelScope.launch {
    val balance = rippleManager.getBalance()
    // Update UI trực tiếp (đã ở Main thread nếu dùng viewModelScope)
}
```

---

## 9. Quy tắc giao dịch (XRP Ledger Protocol)

| Quy tắc | Giá trị |
|---|---|
| Account reserve | 10 XRP (bắt buộc giữ để kích hoạt account) |
| Minimum amount | 1 XRP (`ACTCoin.Ripple.minimumAmount()`) |
| Default fee | 0.000012 XRP (12 drops) |
| Destination Tag range | 0 — 4,294,967,295 (UInt32) |
| TX finality | ~3-5 giây |
| LastLedgerSequence offset | +75 ledger (~5 phút timeout) |

---

## 10. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| RPC Endpoint | `s1.ripple.com:51234` | `s.altnet.rippletest.net:51234` |
| Explorer | [bithomp.com](https://bithomp.com) | [test.bithomp.com](https://test.bithomp.com) |
| Xem TX | `bithomp.com/explorer/{txHash}` | `test.bithomp.com/explorer/{txHash}` |

Chuyển đổi network:
```kotlin
Config.shared.setNetwork(Network.TESTNET)
// Tất cả RPC call tự động đổi sang testnet endpoint
```

---

## 11. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Balance = 0 dù đã gửi XRP | Account chưa kích hoạt (< 10 XRP) | Gửi >= 10 XRP lần đầu để activate |
| `tecUNFUNDED_PAYMENT` | Không đủ số dư (balance - 10 XRP reserve < amount + fee) | Kiểm tra balance khả dụng trước khi gửi |
| `tecNO_DST` | Địa chỉ đích chưa tồn tại trên ledger | Gửi >= 10 XRP để kích hoạt account đích |
| `tecNO_DST_INSUF_XRP` | Gửi < 10 XRP đến account mới | Gửi >= 10 XRP |
| `tefPAST_SEQ` | Sequence number đã dùng | Retry — sequence sẽ tự refresh |
| `tefMAX_LEDGER` | TX hết hạn (LastLedgerSequence đã qua) | Tạo và gửi lại TX mới |
| Mất tiền khi gửi sàn | Thiếu Destination Tag | Luôn hỏi user nhập tag khi gửi đến sàn |
| Fee x2 bất ngờ | Có service fee address | `fee * 2` khi `serviceAddress` được set |
