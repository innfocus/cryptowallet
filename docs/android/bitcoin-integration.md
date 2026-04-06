# Bitcoin (BTC) Integration cho Android

Hướng dẫn tích hợp Bitcoin (BTC) vào ứng dụng Android, bao gồm 3 loại địa chỉ (Legacy, Nested SegWit, Native SegWit), gửi/nhận BTC, fee estimation, và service fee.

> **Tài liệu kỹ thuật chi tiết:** [Bitcoin spec](../chains/bitcoin.md)

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

`CoinsManager` là entry point chính trên Android. Bitcoin được delegate sang `CommonCoinsManager` bên trong.

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

### 2.2 Trực tiếp qua BitcoinManager (advanced)

Dùng khi cần truy cập API chi tiết hơn (chọn loại địa chỉ, local TX building, fee rate).

```kotlin
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager

val mnemonic = "your mnemonic words ..."
val bitcoinManager = BitcoinManager(mnemonic)
```

---

## 3. Loại địa chỉ (Address Types)

Bitcoin hỗ trợ **3 loại địa chỉ**, mỗi loại có đặc điểm riêng:

| Loại | BIP | Prefix (Mainnet) | Prefix (Testnet) | Phí | Mô tả |
|------|-----|-------------------|-------------------|-----|-------|
| **Native SegWit** | BIP-84 | `bc1q...` | `tb1q...` | Thấp nhất | Mặc định, khuyên dùng |
| **Nested SegWit** | BIP-49 | `3...` | `2...` | Trung bình | Tương thích ngược |
| **Legacy** | BIP-44 | `1...` | `m.../n...` | Cao nhất | Cũ, hỗ trợ mọi nơi |

---

## 4. Lấy địa chỉ ví

### 4.1 Qua CoinsManager (Native SegWit mặc định)

```kotlin
val address = CoinsManager.shared.addresses()[ACTCoin.Bitcoin]
// Trả về: bc1q... (Native SegWit, mặc định)
```

### 4.2 Qua BitcoinManager (chọn loại)

```kotlin
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType

// Native SegWit (mặc định, phí thấp nhất)
val nativeSegWit = bitcoinManager.getNativeSegWitAddress(accountIndex = 0)
// → "bc1q..." (mainnet) / "tb1q..." (testnet)

// Nested SegWit (tương thích ngược)
val nestedSegWit = bitcoinManager.getNestedSegWitAddress(accountIndex = 0)
// → "3..." (mainnet) / "2..." (testnet)

// Legacy
val legacy = bitcoinManager.getLegacyAddress(accountIndex = 0)
// → "1..." (mainnet) / "m..." hoặc "n..." (testnet)

// Hoặc dùng hàm chung
val address = bitcoinManager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, accountIndex = 0)
```

### 4.3 Derivation Paths

```
Native SegWit:  m/84'/0'/account'/0/0   (mainnet)
                m/84'/1'/account'/0/0   (testnet)

Nested SegWit:  m/49'/0'/account'/0/0   (mainnet)
                m/49'/1'/account'/0/0   (testnet)

Legacy:         m/44'/0'/account'/0/0   (mainnet)
                m/44'/1'/account'/0/0   (testnet)
```

> **Lưu ý:** `accountIndex` cho phép derive nhiều ví con từ cùng 1 mnemonic. Thường dùng `0`.

---

## 5. Lấy số dư

```kotlin
import com.lybia.cryptowallet.coinkits.BalanceHandle

// Qua CoinsManager — trả về BTC (Double)
CoinsManager.shared.getBalance(ACTCoin.Bitcoin, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        if (success) {
            Log.d("BTC", "Balance: $balance BTC")
        }
    }
})

// Qua BitcoinManager (suspend function)
viewModelScope.launch {
    val balance = bitcoinManager.getBalance() // Double, đơn vị BTC
    Log.d("BTC", "Balance: $balance BTC")
}
```

> **Quy đổi:** 1 BTC = 100,000,000 satoshi. API trả về đã quy đổi sang BTC.

---

## 6. Lịch sử giao dịch

```kotlin
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.TransationData

CoinsManager.shared.getTransactions(ACTCoin.Bitcoin, null, object : TransactionsHandle {
    override fun completionHandler(
        transactions: Array<TransationData>?,
        moreParam: JsonObject?,
        errStr: String
    ) {
        transactions?.forEach { tx ->
            Log.d("BTC", "Hash: ${tx.iD}, Amount: ${tx.amount}")
        }
    }
})

// Qua BitcoinManager
viewModelScope.launch {
    val history = bitcoinManager.getTransactionHistory()
    history?.forEach { tx ->
        Log.d("BTC", "Hash: ${tx.hash}, Fee: ${tx.fees}")
    }
}
```

---

## 7. Ước lượng phí (Fee Estimation)

Bitcoin phí biến động theo tình trạng mempool. Có 2 cách ước lượng:

### 7.1 BlockCypher (API-assisted)

```kotlin
viewModelScope.launch {
    val feeSatoshi = bitcoinManager.estimateFee(
        toAddress = "bc1q...",
        amountSatoshi = 50_000L          // 0.0005 BTC
    )
    // feeSatoshi: phí ước lượng bằng satoshi (Long?)
    Log.d("BTC", "Estimated fee: $feeSatoshi sat")
}
```

### 7.2 Local (Mempool.space fee rate)

```kotlin
import com.lybia.cryptowallet.services.EsploraApiService

viewModelScope.launch {
    // Lấy fee rate từ Mempool.space
    val feeRates = EsploraApiService.INSTANCE.getRecommendedFeeRates()
    // feeRates.fastestFee     — xác nhận block tiếp theo (sat/vB)
    // feeRates.halfHourFee    — ~30 phút (mặc định)
    // feeRates.hourFee        — ~1 giờ
    // feeRates.economyFee     — tiết kiệm
    // feeRates.minimumFee     — tối thiểu

    // Ước lượng fee cụ thể cho 1 giao dịch
    val feeEstimate = bitcoinManager.estimateFeeLocal(
        toAddress = "bc1q...",
        amountSatoshi = 50_000L,
        feeRateSatPerVbyte = feeRates?.halfHourFee  // null = tự lấy, fallback 10 sat/vB
    )
    Log.d("BTC", "Local estimated fee: $feeEstimate sat")
}
```

### 7.3 Bảng tham chiếu vsize

| Loại địa chỉ | Input (vB) | Output (vB) | Base (vB) |
|---------------|-----------|-------------|-----------|
| Native SegWit (P2WPKH) | 68 | 31 | 11 |
| Nested SegWit (P2SH-P2WPKH) | 91 | 32 | 11 |
| Legacy (P2PKH) | 148 | 34 | 11 |

> **Công thức:** `fee = vsize * feeRate (sat/vB)`
>
> **Ví dụ:** 1 input + 2 output (Native SegWit) = 11 + 68 + 31*2 = 141 vB × 10 sat/vB = 1,410 sat

---

## 8. Gửi BTC

### 8.1 Cơ bản (qua CoinsManager)

```kotlin
CoinsManager.shared.sendCoin(
    coin = ACTCoin.Bitcoin,
    toAddress = "bc1q...",
    amount = 0.001,          // 0.001 BTC
    fee = 0.00001            // phí (BTC)
) { txHash, error ->
    if (error == null) {
        Log.d("BTC", "TX submitted: $txHash")
    } else {
        Log.e("BTC", "Error: $error")
    }
}
```

### 8.2 API-Assisted (BlockCypher)

BlockCypher xử lý UTXO selection trên server, client chỉ cần ký:

```kotlin
viewModelScope.launch {
    val result = bitcoinManager.sendBtc(
        toAddress = "bc1q...",
        amountSatoshi = 100_000L,                          // 0.001 BTC
        addressType = BitcoinAddressType.NATIVE_SEGWIT,    // mặc định
        accountIndex = 0
    )

    if (result.success) {
        Log.d("BTC", "TX hash: ${result.hash}")
    } else {
        Log.e("BTC", "Error: ${result.error}")
    }
}
```

**Flow nội bộ:**
1. Gọi BlockCypher `/txs/new` → nhận TX skeleton + `tosign` hashes
2. Ký từng hash bằng private key (secp256k1 ECDSA → DER)
3. Gọi BlockCypher `/txs/send` → broadcast TX đã ký

### 8.3 Local (Client-Side, khuyên dùng)

Full client-side: fetch UTXOs, build TX, ký, và broadcast — không phụ thuộc BlockCypher:

```kotlin
viewModelScope.launch {
    val result = bitcoinManager.sendBtcLocal(
        toAddress = "bc1q...",
        amountSatoshi = 100_000L,                          // 0.001 BTC
        addressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex = 0,
        feeRateSatPerVbyte = null                          // null = tự lấy từ Mempool.space
    )

    if (result.success) {
        Log.d("BTC", "TX ID: ${result.hash}")
    } else {
        Log.e("BTC", "Error: ${result.error}")
    }
}
```

**Flow nội bộ:**
1. Fetch UTXOs từ Esplora (`blockstream.info`)
2. Lấy fee rate từ Mempool.space (hoặc dùng giá trị truyền vào)
3. UTXO selection (largest-first) + fee estimation
4. Build & sign TX hoàn toàn trên client
5. Broadcast qua Esplora `/tx`

### 8.4 Với Service Fee

Service fee được thêm như output phụ trong cùng 1 transaction (khác XRP cần 2 TX):

```kotlin
// API-Assisted
val result = bitcoinManager.sendBtc(
    toAddress = "bc1qRecipient...",
    amountSatoshi = 100_000L,                 // 0.001 BTC cho người nhận
    addressType = BitcoinAddressType.NATIVE_SEGWIT,
    accountIndex = 0,
    serviceAddress = "bc1qService...",        // Địa chỉ nhận phí dịch vụ
    serviceFeeAmount = 5_000L                 // 5,000 sat service fee
)
// TX có 3 outputs: recipient + service fee + change

// Local
val result = bitcoinManager.sendBtcLocal(
    toAddress = "bc1qRecipient...",
    amountSatoshi = 100_000L,
    addressType = BitcoinAddressType.NATIVE_SEGWIT,
    accountIndex = 0,
    serviceAddress = "bc1qService...",
    serviceFeeAmount = 5_000L
)
```

### 8.5 Chọn loại địa chỉ khi gửi

Loại địa chỉ **gửi từ** ảnh hưởng đến phí (vì script size khác nhau):

```kotlin
// Gửi từ Native SegWit (phí thấp nhất)
bitcoinManager.sendBtcLocal(
    toAddress = "1LegacyAddr...",             // Có thể gửi đến BẤT KỲ loại nào
    amountSatoshi = 50_000L,
    addressType = BitcoinAddressType.NATIVE_SEGWIT  // Địa chỉ GỬI
)

// Gửi từ Legacy (phí cao hơn ~2x)
bitcoinManager.sendBtcLocal(
    toAddress = "bc1q...",
    amountSatoshi = 50_000L,
    addressType = BitcoinAddressType.LEGACY
)
```

---

## 9. UTXO Selection & Dust

### UTXO Selection

Library dùng thuật toán **largest-first** (tham lam):
1. Lọc chỉ UTXO đã confirmed
2. Sắp xếp theo giá trị giảm dần
3. Chọn đến khi tổng input >= amount + fee ước lượng
4. Tính change = tổng input - amount - fee

### Dust Threshold

Change quá nhỏ sẽ bị hấp thụ vào phí:

| Loại | Dust Threshold |
|------|---------------|
| P2WPKH (Native SegWit) | 294 sat |
| P2PKH (Legacy) | 546 sat |

> **Ví dụ:** Nếu change = 200 sat (P2WPKH) → change bị bỏ, cộng vào fee.

---

## 10. Threading

- `CoinsManager` chạy trên `Dispatchers.IO`, callback trả về trên `Dispatchers.Main`.
- `BitcoinManager` là suspend functions — gọi trong `CoroutineScope` hoặc `viewModelScope`:

```kotlin
viewModelScope.launch {
    val balance = bitcoinManager.getBalance()
    // Update UI trực tiếp (đã ở Main thread nếu dùng viewModelScope)
}
```

---

## 11. Network & Explorer

| Mục | Mainnet | Testnet |
|---|---|---|
| BlockCypher API | `api.blockcypher.com/v1/btc/main` | `api.blockcypher.com/v1/btc/test3` |
| Esplora API | `blockstream.info/api` | `blockstream.info/testnet/api` |
| Mempool.space | `mempool.space/api` | `mempool.space/testnet/api` |
| Explorer | [blockchain.com/btc](https://www.blockchain.com/btc) | [testnet.blockchain.info](https://testnet.blockchain.info) |

Chuyển đổi network:
```kotlin
Config.shared.setNetwork(Network.TESTNET)
// Tất cả API call + derivation path tự động đổi sang testnet
// coin_type: 0 (mainnet) → 1 (testnet)
// Address prefix: bc1q → tb1q, 1... → m.../n..., 3... → 2...
```

---

## 12. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|---|---|---|
| Balance = 0 dù có BTC | Sai loại địa chỉ khi query | Dùng đúng address type đã nhận BTC |
| `insufficient funds` | Tổng UTXO < amount + fee | Kiểm tra balance trước khi gửi |
| Fee quá cao | Mempool đang congested | Dùng `economyFee` hoặc chờ phí giảm |
| Fee = 0 | API fee estimation fail | Fallback 10 sat/vB hoặc retry |
| TX không confirm | Fee quá thấp | Dùng `fastestFee` rate thay vì `economyFee` |
| Address sai prefix | Sai network config | Kiểm tra `Config.shared.setNetwork()` |
| Change "biến mất" | Change < dust threshold | Bình thường — change nhỏ bị cộng vào fee |
| `null` result từ API | Network timeout / rate limit | Retry hoặc chuyển sang local method |
