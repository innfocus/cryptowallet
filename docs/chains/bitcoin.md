# Bitcoin — Đặc tả Kỹ thuật

**Version:** 1.0  
**Status:** ✅ Cross-platform (commonMain) | ✅ Android | ✅ iOS  
**Scope:** `commonMain/kotlin/com/lybia/cryptowallet/wallets/bitcoin/`  
**Related:** [BIP-32](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki), [BIP-39](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki), [BIP-44](https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki), [BIP-84](https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki), [BIP-49](https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki)

---

## 1. Tổng quan

Module Bitcoin trong `crypto-wallet-lib` hỗ trợ **đầy đủ 3 loại địa chỉ** (Legacy, Nested SegWit, Native SegWit), xây dựng và ký giao dịch client-side hoặc qua BlockCypher API, với UTXO selection và fee estimation tự động.

### Tính năng chính

| Tính năng | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Tạo ví từ BIP-39 mnemonic | ✅ | 12/24 từ |
| Legacy address (P2PKH) | ✅ | BIP-44 |
| Nested SegWit (P2SH-P2WPKH) | ✅ | BIP-49 |
| Native SegWit (P2WPKH) | ✅ | BIP-84, mặc định |
| Truy vấn số dư | ✅ | BlockCypher API |
| Lịch sử giao dịch | ✅ | BlockCypher API |
| Gửi BTC qua BlockCypher | ✅ | API-assisted signing |
| Gửi BTC local (Esplora) | ✅ | Full client-side build & sign |
| Ước lượng phí | ✅ | BlockCypher + Mempool.space |
| Service fee (multi-output) | ✅ | Thêm output phí dịch vụ |
| Mainnet / Testnet | ✅ | Chuyển đổi qua `Config.shared` |

---

## 2. Tiêu chuẩn Blockchain (Standards)

Đây là danh sách các BIP (Bitcoin Improvement Proposal) mà module triển khai. Dev cần nắm các tiêu chuẩn này để hiểu cách hệ thống hoạt động.

### 2.1. BIP-39 — Mnemonic Code

- **Mô tả:** Tạo seed từ chuỗi mnemonic (12 hoặc 24 từ tiếng Anh).
- **Quy trình:** Mnemonic → PBKDF2 (2048 rounds, HMAC-SHA512) → 512-bit Seed
- **Wordlist:** 2048 từ tiếng Anh chuẩn ([english.txt](https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt))
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki

### 2.2. BIP-32 — HD Wallet (Hierarchical Deterministic)

- **Mô tả:** Từ 1 seed duy nhất, sinh ra **cây khóa vô hạn** (master key → child keys).
- **Thuật toán:** HMAC-SHA512 để derive child key từ parent key + index.
- **Hardened derivation:** Index ≥ 2³¹ (ký hiệu `'`) — không thể suy ngược parent key.
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki

### 2.3. BIP-44 — Multi-Account Hierarchy

- **Mô tả:** Định nghĩa cấu trúc path chuẩn cho HD wallet.
- **Format:** `m / purpose' / coin_type' / account' / change / address_index`
- **Bitcoin coin_type:** `0` (mainnet), `1` (testnet)
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki

### 2.4. BIP-84 — Native SegWit (Bech32)

- **Mô tả:** Derivation path cho địa chỉ **P2WPKH** (Native SegWit, Bech32).
- **Purpose:** `84'`
- **Script:** `OP_0 <20-byte-pubkey-hash>` (witness program v0)
- **Encoding:** Bech32 ([BIP-173](https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki))
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki

### 2.5. BIP-49 — Nested SegWit (P2SH-P2WPKH)

- **Mô tả:** Derivation path cho địa chỉ **P2SH-P2WPKH** — SegWit gói trong P2SH để tương thích ngược.
- **Purpose:** `49'`
- **Script:** `OP_HASH160 <hash(redeemScript)> OP_EQUAL`, với redeemScript = `OP_0 <pubkey-hash>`
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki

### 2.6. BIP-141 — Segregated Witness

- **Mô tả:** Tách chữ ký (witness) ra khỏi transaction data → giảm kích thước, tăng throughput.
- **Sighash:** `SIGHASH_ALL` cho tất cả input.
- **Weight:** `base_size * 3 + total_size` → `vsize = weight / 4`
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki

### 2.7. BIP-143 — Transaction Digest for SegWit

- **Mô tả:** Thuật toán hash mới cho SegWit — giải quyết O(n²) hashing bug của legacy sighash.
- **Áp dụng:** Bắt buộc cho P2WPKH và P2SH-P2WPKH. Dùng `SigVersion.SIGVERSION_WITNESS_V0`.
- **Script code khi ký:** `OP_DUP OP_HASH160 <pubkey-hash> OP_EQUALVERIFY OP_CHECKSIG` (pay2pkh của public key).
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki

---

## 3. Derivation Paths

| Loại địa chỉ | BIP | Path | Script | Prefix (Mainnet) | Prefix (Testnet) | Phí |
|---------------|-----|------|--------|-------------------|-------------------|-----|
| **Native SegWit** | BIP-84 | `m/84'/0'/account'/0/0` | P2WPKH | `bc1q...` | `tb1q...` | Thấp nhất |
| **Nested SegWit** | BIP-49 | `m/49'/0'/account'/0/0` | P2SH-P2WPKH | `3...` | `2...` | Trung bình |
| **Legacy** | BIP-44 | `m/44'/0'/account'/0/0` | P2PKH | `1...` | `m.../n...` | Cao nhất |

> **Lưu ý:** `coin_type = 0` cho Mainnet, `1` cho Testnet. Mặc định sử dụng **Native SegWit** vì phí thấp nhất.

---

## 4. Kiến trúc Source Code

```
crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/
├── wallets/bitcoin/
│   ├── BitcoinManager.kt              # Manager chính — address, balance, transfer
│   ├── BitcoinAddressType.kt          # Enum: NATIVE_SEGWIT, NESTED_SEGWIT, LEGACY
│   └── BitcoinTransactionBuilder.kt   # UTXO selection, build & sign, vsize estimation
├── models/bitcoin/
│   ├── BitcoinTransactionModel.kt     # BlockCypher TX model (tosign, signatures)
│   ├── BTCApiModel.kt                 # Balance & history response model
│   └── UtxoInfo.kt                    # UTXO data model (Esplora)
├── services/
│   ├── BitcoinApiService.kt           # BlockCypher API client
│   └── EsploraApiService.kt           # Esplora + Mempool.space API client
└── base/
    └── BaseCoinManager.kt             # Interface cha (getAddress, getBalance, transfer...)
```

---

## 5. API Chi tiết

### 5.1. BitcoinManager

```kotlin
class BitcoinManager(mnemonics: String) : BaseCoinManager()
```

#### Tạo địa chỉ

```kotlin
// Mặc định — Native SegWit
val address = bitcoinManager.getAddress()
// → "bc1q..." (mainnet) hoặc "tb1q..." (testnet)

// Chọn loại cụ thể
val legacy = bitcoinManager.getAddressByType(BitcoinAddressType.LEGACY, accountIndex = 0)
val nested = bitcoinManager.getAddressByType(BitcoinAddressType.NESTED_SEGWIT)
val native = bitcoinManager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT)
```

#### Truy vấn số dư

```kotlin
// Trả về BTC (satoshis / 100_000_000)
val balance: Double = bitcoinManager.getBalance()
```

#### Lịch sử giao dịch

```kotlin
val history = bitcoinManager.getTransactionHistory()
// Trả về List<BTCApiModel.Tx>
```

#### Gửi BTC — Qua BlockCypher (API-Assisted)

```kotlin
val result = bitcoinManager.sendBtc(
    toAddress = "bc1q...",
    amountSatoshi = 50_000L,          // 0.0005 BTC
    addressType = BitcoinAddressType.NATIVE_SEGWIT,
    accountIndex = 0,
    serviceAddress = "bc1q...",       // (tùy chọn) địa chỉ nhận phí dịch vụ
    serviceFeeAmount = 1_000L         // (tùy chọn) phí dịch vụ (satoshi)
)
// result: TransferResponseModel(success, error, hash)
```

**Flow:**
1. BlockCypher tạo TX skeleton (`POST /txs/new`) với UTXO selection tự động
2. Client ký từng hash trong `tosign` bằng private key
3. BlockCypher broadcast TX đã ký (`POST /txs/send`)

#### Gửi BTC — Local Build (Esplora)

```kotlin
val result = bitcoinManager.sendBtcLocal(
    toAddress = "bc1q...",
    amountSatoshi = 50_000L,
    addressType = BitcoinAddressType.NATIVE_SEGWIT,
    accountIndex = 0,
    feeRateSatPerVbyte = null,        // null = tự lấy từ Mempool.space
    serviceAddress = "bc1q...",
    serviceFeeAmount = 1_000L
)
```

**Flow:**
1. Lấy UTXOs từ Esplora API
2. Chọn UTXOs và tính phí locally (largest-first strategy)
3. Build & sign raw TX hoàn toàn client-side (bitcoin-kmp)
4. Broadcast raw TX hex qua Esplora

#### Ước lượng phí

```kotlin
// BlockCypher
val fee: Long? = bitcoinManager.estimateFee(toAddress, amountSatoshi)

// Local (Mempool.space fee rates)
val fee: Long? = bitcoinManager.estimateFeeLocal(toAddress, amountSatoshi)
```

### 5.2. BitcoinTransactionBuilder

#### UTXO Selection

```kotlin
fun selectUtxos(
    utxos: List<UtxoInfo>,
    targetAmount: Long,              // satoshi cần gửi
    feeRatePerVbyte: Long,           // sat/vB
    addressType: BitcoinAddressType,
    extraOutputCount: Int = 0        // số output thêm (service fee)
): Pair<List<UtxoInfo>, Long>?       // (UTXOs đã chọn, fee) hoặc null nếu thiếu
```

- **Chiến lược:** Largest-first — ưu tiên UTXO lớn nhất
- **Chỉ dùng confirmed UTXOs** (`status.confirmed == true`)
- **Dust threshold:** P2WPKH = 294 sat, P2PKH = 546 sat — change nhỏ hơn sẽ gộp vào phí

#### Build & Sign

```kotlin
fun buildAndSign(
    utxos: List<UtxoInfo>,
    toAddress: String,
    amountSat: Long,
    feeSat: Long,
    changeAddress: String,
    privateKey: PrivateKey,
    publicKey: PublicKey,
    addressType: BitcoinAddressType,
    chain: Chain,
    additionalOutputs: List<AdditionalTxOutput> = emptyList()
): BuildResult  // (rawTxHex, txid, fee, vsize)
```

#### Vsize Estimation

| Component | Vbytes |
|-----------|--------|
| TX overhead (version, locktime, segwit flags, counts) | 11 |
| P2WPKH input | 68 |
| P2SH-P2WPKH input | 91 |
| P2PKH input | 148 |
| P2WPKH output | 31 |
| P2PKH output | 34 |
| P2SH output | 32 |

### 5.3. Signing Flow theo loại địa chỉ

#### P2WPKH (Native SegWit)
- `scriptSig`: rỗng
- `witness`: `[DER_sig + SIGHASH_ALL_byte, compressed_pubkey]`
- Sighash: BIP-143

#### P2SH-P2WPKH (Nested SegWit)
- `scriptSig`: `push(redeemScript)` — redeemScript = `OP_0 <20-byte-pubkey-hash>`
- `witness`: `[DER_sig + SIGHASH_ALL_byte, compressed_pubkey]`
- Sighash: BIP-143

#### P2PKH (Legacy)
- `scriptSig`: `<DER_sig + SIGHASH_ALL_byte> <compressed_pubkey>`
- `witness`: rỗng
- Sighash: Legacy sighash

---

## 6. API Services

### 6.1. BlockCypher API

**Base URL:**
- Mainnet: `https://api.blockcypher.com/v1/btc/main`
- Testnet: `https://api.blockcypher.com/v1/btc/test3`

| Endpoint | Method | Mục đích |
|----------|--------|---------|
| `/addrs/{address}/balance` | GET | Truy vấn số dư (satoshi) |
| `/addrs/{address}/full` | GET | Lịch sử giao dịch đầy đủ |
| `/txs/new` | POST | Tạo TX skeleton (UTXO selection phía server) |
| `/txs/send` | POST | Broadcast TX đã ký |

### 6.2. Esplora API (Blockstream)

**Base URL:**
- Mainnet: `https://blockstream.info/api`
- Testnet: `https://blockstream.info/testnet/api`

| Endpoint | Method | Mục đích |
|----------|--------|---------|
| `/address/{address}/utxo` | GET | Danh sách UTXO |
| `/tx/{txid}/hex` | GET | Raw transaction hex |
| `/tx` | POST | Broadcast raw TX hex |

### 6.3. Mempool.space (Fee Rates)

**Base URL:**
- Mainnet: `https://mempool.space/api`
- Testnet: `https://mempool.space/testnet/api`

| Endpoint | Method | Mục đích |
|----------|--------|---------|
| `/v1/fees/recommended` | GET | Fee rates (sat/vB) |

**Response:**
```json
{
    "fastestFee": 25,
    "halfHourFee": 15,
    "hourFee": 10,
    "economyFee": 5,
    "minimumFee": 1
}
```

> **Fallback:** Nếu không lấy được fee rate → dùng mặc định **10 sat/vB**.

---

## 7. Data Models

### UtxoInfo

```kotlin
@Serializable
data class UtxoInfo(
    val txid: String,        // Hash của TX trước
    val vout: Int,           // Output index
    val value: Long,         // Giá trị (satoshi)
    val status: UtxoStatus
)
```

### BitcoinTransactionModel (BlockCypher)

```kotlin
@Serializable
data class BitcoinTransactionModel(
    val tx: Tx,
    val tosign: List<String>,           // Các hash cần ký
    val signatures: List<String>?,      // Chữ ký DER (hex)
    val pubkeys: List<String>?          // Public keys (hex)
)
```

### BuildResult (Local)

```kotlin
data class BuildResult(
    val rawTxHex: String,   // Raw TX để broadcast
    val txid: String,       // Transaction ID
    val fee: Long,          // Phí thực tế (satoshi)
    val vsize: Int          // Virtual size
)
```

---

## 8. Network Configuration

```kotlin
// Chuyển network — ảnh hưởng tất cả manager
Config.shared.setNetwork(Network.MAINNET)  // hoặc Network.TESTNET

// Trong BitcoinManager
val isMainnet = Config.shared.getNetwork() == Network.MAINNET
val coinType = if (isMainnet) 0 else 1     // BIP-44 coin_type
val chain = if (isMainnet) Chain.Mainnet else Chain.Testnet4
```

---

## 9. Hướng dẫn tích hợp Android

### 9.1. Dependency

```toml
# gradle/libs.versions.toml — đã khai báo sẵn
bitcoinKmp = "0.30.0"
secp256k1Kmp = "0.23.0"
```

```kotlin
// build.gradle.kts
implementation("io.github.innfocus:crypto-wallet-lib:$version")
```

### 9.2. Khởi tạo và tạo địa chỉ

```kotlin
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.Network

// Chọn network
Config.shared.setNetwork(Network.MAINNET)

// Tạo manager từ mnemonic
val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
val btcManager = BitcoinManager(mnemonic)

// Lấy địa chỉ
val address = btcManager.getAddress() // Native SegWit mặc định
```

### 9.3. Lấy số dư và lịch sử

```kotlin
// Trong CoroutineScope (Activity/ViewModel)
launch {
    val balance = btcManager.getBalance() // Double, đơn vị BTC
    val history = btcManager.getTransactionHistory()

    withContext(Dispatchers.Main) {
        // Cập nhật UI
        tvBalance.text = "$balance BTC"
    }
}
```

### 9.4. Gửi BTC

```kotlin
launch {
    // Cách 1: Qua BlockCypher
    val result = btcManager.sendBtc(
        toAddress = "bc1qrecipient...",
        amountSatoshi = 100_000L  // 0.001 BTC
    )

    // Cách 2: Local build (khuyến nghị)
    val result = btcManager.sendBtcLocal(
        toAddress = "bc1qrecipient...",
        amountSatoshi = 100_000L,
        feeRateSatPerVbyte = 15L  // hoặc null để tự estimate
    )

    if (result.success) {
        Log.d("BTC", "TX Hash: ${result.hash}")
    } else {
        Log.e("BTC", "Error: ${result.error}")
    }
}
```

### 9.5. Qua CoinsManager (Android wrapper)

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager

val coinsManager = CoinsManager.shared
coinsManager.setMnemonic(mnemonic)

// Balance
launch {
    val balance = coinsManager.getBTCBalance()
}
```

---

## 10. Hướng dẫn tích hợp iOS

### 10.1. Dependency

```swift
// Package.swift hoặc qua SPM
// Framework: crypto-wallet-lib XCFramework
```

### 10.2. Khởi tạo

```swift
import CryptoWalletLib

// Chọn network
Config.shared.setNetwork(network: .mainnet)

// Tạo manager
let btcManager = BitcoinManager(mnemonics: mnemonic)
let address = btcManager.getAddress() // Native SegWit
```

### 10.3. Async operations (Swift async/await)

```swift
// Balance
let balance = try await btcManager.getBalance(address: nil, coinNetwork: nil)

// Gửi BTC
let result = try await btcManager.sendBtcLocal(
    toAddress: "bc1q...",
    amountSatoshi: 100_000,
    addressType: .nativeSegwit,
    accountIndex: 0,
    feeRateSatPerVbyte: nil,   // tự estimate
    serviceAddress: nil,
    serviceFeeAmount: 0
)

if result.success {
    print("TX: \(result.hash ?? "")")
}
```

> **Lưu ý:** iOS sử dụng Ktor Darwin engine (NSURLSession). Các suspend function được expose qua Kotlin/Native `@Throws` → Swift `async throws`.

---

## 11. Đơn vị và chuyển đổi

| Đơn vị | Giá trị | Sử dụng trong code |
|--------|---------|---------------------|
| **satoshi** | 1 sat | `amountSatoshi`, `feeDrops`, UTXO value |
| **BTC** | 100,000,000 sat | `getBalance()` trả về BTC |

```kotlin
// Chuyển đổi
val btc = satoshis / 100_000_000.0
val satoshis = (btc * 100_000_000).toLong()
```

---

## 12. Known Test Vectors

**Mnemonic chuẩn BIP-39:**
```
abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about
```

**Địa chỉ mong đợi (Mainnet, account 0):**

| Loại | Địa chỉ |
|------|---------|
| Legacy (P2PKH) | `1LqBGSKauBjvYMoAstge...` |
| Nested SegWit (P2SH-P2WPKH) | `37VucYSaXLCAsHYYDSPn...` |
| Native SegWit (P2WPKH) | `bc1qcr8te4kr609gcawutm...` |

> Xác minh bằng cách so sánh với [iancoleman.io/bip39](https://iancoleman.io/bip39/) hoặc chạy test.

---

## 13. Chạy Tests

```bash
# Compile kiểm tra
./gradlew :crypto-wallet-lib:compileAndroidMain

# Chạy test (nếu có)
./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*Bitcoin*"
```

---

## 14. Lưu ý quan trọng

1. **Mặc định dùng Native SegWit (BIP-84)** — phí thấp nhất, tương thích tốt nhất.
2. **Không bao giờ dùng unconfirmed UTXO** — `selectUtxos()` chỉ chọn confirmed.
3. **Dust handling:** Change < 294 sat (P2WPKH) hoặc < 546 sat (P2PKH) sẽ gộp vào phí.
4. **Service fee:** Thêm output phí dịch vụ thông qua `serviceAddress` + `serviceFeeAmount`.
5. **Fee fallback:** 10 sat/vB nếu không lấy được từ Mempool.space.
6. **Network switching:** `Config.shared.setNetwork()` tự động đổi endpoint cho tất cả service.

---

## 15. Dependencies

| Thư viện | Version | Mục đích |
|----------|---------|---------|
| `bitcoin-kmp` | 0.30.0 | Address generation, TX building, signing |
| `secp256k1-kmp` | 0.23.0 | ECDSA signing (secp256k1 curve) |
| `secp256k1-kmp-jni-android` | 0.23.0 | JNI bindings cho Android |
| `ktor-client-core` | 3.4.2 | HTTP client (KMP) |
| `kotlinx-serialization-json` | 1.10.0 | JSON serialization |
| `kotlinx-coroutines-core` | 1.10.2 | Async/await |

---

## 16. Tài liệu tham khảo

| Tài liệu | Link | Nội dung |
|-----------|------|---------|
| BIP-32 HD Wallets | https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki | Hierarchical Deterministic key derivation |
| BIP-39 Mnemonic | https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki | Mnemonic seed phrase |
| BIP-44 Multi-Account | https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki | Derivation path structure |
| BIP-49 Nested SegWit | https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki | P2SH-P2WPKH derivation |
| BIP-84 Native SegWit | https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki | P2WPKH (Bech32) derivation |
| BIP-141 SegWit | https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki | Segregated Witness consensus |
| BIP-143 SegWit Sighash | https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki | SegWit transaction digest |
| BIP-173 Bech32 | https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki | Bech32 address encoding |
| bitcoin-kmp | https://github.com/AcidLeroy/bitcoin-kmp | Kotlin Multiplatform Bitcoin library |
| BlockCypher API | https://www.blockcypher.com/dev/bitcoin/ | REST API cho Bitcoin |
| Esplora API | https://github.com/Blockstream/esplora/blob/master/API.md | Blockstream Esplora REST API |
| Mempool.space API | https://mempool.space/docs/api | Fee estimation API |
