# Ripple (XRP) — Đặc tả Kỹ thuật

**Version:** 1.2  
**Status:** ✅ Cross-platform (commonMain) | ✅ Android | ✅ iOS  
**Scope:** `commonMain/kotlin/com/lybia/cryptowallet/wallets/ripple/`  
**Related:** [XRP Ledger Dev Portal](https://xrpl.org/), [rippled API](https://xrpl.org/public-api-methods.html)  
**Audit:** 2026-04-06 — So sánh với source cũ (`/demo/cryptowallet`), xem [Mục 16](#16-audit--known-issues)

---

## 1. Tổng quan

Module Ripple trong `crypto-wallet-lib` hỗ trợ tạo ví XRP từ BIP-39 mnemonic, ký và gửi giao dịch Payment qua JSON-RPC, với dynamic fee estimation và destination tag.

### Tính năng chính

| Tính năng | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Tạo ví từ BIP-39 mnemonic | ✅ | BIP-44 path `m/44'/144'/0'/0/0` |
| Địa chỉ r-address (Base58Ripple) | ✅ | `r...` format |
| Truy vấn số dư | ✅ | JSON-RPC `account_info` |
| Lịch sử giao dịch (phân trang) | ✅ | JSON-RPC `account_tx` + marker |
| Gửi XRP (Payment) | ✅ | Client-side signing + submit |
| Destination Tag | ✅ | Tùy chọn (cho sàn giao dịch) |
| Dynamic fee estimation | ✅ | JSON-RPC `fee` |
| Memo support | ✅ | STArray serialization (MemoType + MemoData) — fixed 2026-04-06 |
| Balance validation trước send | ✅ | Check reserve 10 XRP + amount + fee — fixed 2026-04-06 |
| Local Transaction ID | ✅ | SHA-512Half(TXN\0 + blob) — fixed 2026-04-06 |
| Service fee (multi-TX) | ❌ **Thiếu** | Code cũ gửi TX thứ 2 cho service fee — xem [#BUG-5](#bug-5-thiếu-service-fee-second-transaction-low) |
| Input validation (signer) | ✅ | Validate key size, amount range, sequence — fixed 2026-04-06 |
| Mainnet / Testnet | ✅ | Chuyển đổi qua `Config.shared` |

### So sánh với Bitcoin

| Đặc điểm | Bitcoin | Ripple (XRP) |
|-----------|---------|-------------|
| Mô hình | UTXO-based | **Account-based** |
| Sequence | Không có | Bắt buộc (account sequence) |
| Phí | Biến động theo vsize | Cố định/dynamic (drops) |
| Thời gian block | ~10 phút | **3-5 giây** |
| Reserve | Không | **10 XRP** minimum balance |
| Signing | SHA-256d | **SHA-512Half** |

---

## 2. Tiêu chuẩn Blockchain (Standards)

### 2.1. XRP Ledger Protocol

- **Mô tả:** XRP Ledger là một blockchain account-based, sử dụng consensus algorithm (không phải PoW/PoS).
- **Finality:** ~3-5 giây. Mỗi ledger version có `ledger_index` tăng dần.
- **Reserve:** Mỗi account phải giữ tối thiểu **10 XRP** (base reserve) để kích hoạt trên ledger.
- **Link:** https://xrpl.org/xrp-ledger-overview.html

### 2.2. Cryptographic Curve — secp256k1

- **Mô tả:** XRP Ledger hỗ trợ 2 curves: **secp256k1** (default) và Ed25519. Module này dùng **secp256k1**.
- **Key format:** 33-byte compressed public key.
- **Signature:** ECDSA → DER encoding.
- **Link:** https://xrpl.org/cryptographic-keys.html

### 2.3. Base58Ripple Encoding

- **Mô tả:** XRP sử dụng bảng chữ cái Base58 **khác Bitcoin**. Ký tự đầu tiên là `r` thay vì `1`.
- **Alphabet:** `rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz`
- **So sánh Bitcoin:** `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`
- **Checksum:** SHA-256d (double SHA-256), lấy 4 byte đầu.
- **Link:** https://xrpl.org/base58-encodings.html

### 2.4. AccountID (Address Generation)

- **Mô tả:** Địa chỉ XRP = Base58Ripple(version_byte + AccountID + checksum)
- **Quy trình:**
  1. `pubKeyHash = RIPEMD160(SHA256(compressed_pubkey))` → 20 bytes (AccountID)
  2. Prepend version byte `0x00`
  3. Checksum = `SHA256(SHA256(0x00 + pubKeyHash))[0:4]`
  4. Encode `(0x00 + pubKeyHash + checksum)` bằng Base58Ripple
- **Kết quả:** Chuỗi bắt đầu bằng `r`, dài 25-35 ký tự.
- **Link:** https://xrpl.org/accounts.html#address-encoding

### 2.5. BIP-44 Derivation cho XRP

- **Mô tả:** XRP dùng BIP-44 standard với coin_type = 144.
- **Path:** `m/44'/144'/0'/0/0`
- **Registered:** [SLIP-44](https://github.com/satoshilabs/slips/blob/master/slip-0044.md) — coin type 144.
- **Link:** https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki

### 2.6. Canonical Binary Serialization

- **Mô tả:** Giao dịch XRP được serialize theo format binary riêng trước khi ký.
- **Field header:** 1-3 bytes tùy thuộc type code và field code.
- **Variable-Length (VL) encoding:** Length prefix cho các trường có độ dài thay đổi (Blob, AccountID).
- **Amount encoding:** Bit 63 = 0 (native XRP, không phải IOU), Bit 62 = 1 (positive flag).
- **Link:** https://xrpl.org/serialization.html

### 2.7. Transaction Signing (SHA-512Half)

- **Mô tả:** XRP dùng **SHA-512Half** — lấy 32 byte đầu của SHA-512.
- **Hash prefix:** `STX\0` (bytes: `0x53, 0x54, 0x58, 0x00`) — prepend trước serialized TX.
- **Quy trình:**
  1. Serialize TX fields (không có TxnSignature)
  2. Prepend hash prefix `STX\0`
  3. Hash = `SHA512(prefix + serialized)[0:32]` (SHA-512Half)
  4. Sign hash bằng secp256k1 (compact format)
  5. Convert compact → DER signature
  6. Rebuild TX với DER signature
  7. Serialize lại → hex `tx_blob`
- **Link:** https://xrpl.org/transaction-basics.html#signing-and-submitting-transactions

### 2.8. LastLedgerSequence (TX Expiry)

- **Mô tả:** Giới hạn ledger index mà TX phải được validate trước. Nếu quá → TX bị reject.
- **Khuyến nghị (XRPL docs):** `currentLedgerIndex + 4` (tối thiểu) đến `+ 75` (~5 phút).
- **Code cũ:** `ledgerIndex + 75` (~5 phút) — an toàn, tránh false expiry khi network congestion.
- **KMP hiện tại:** `ledgerIndex + 20` (~1 phút) — xem [#BUG-2](#bug-2-lastledgersequence-offset-quá-nhỏ-medium).
- **Link:** https://xrpl.org/reliable-transaction-submission.html

### 2.9. Destination Tag

- **Mô tả:** Số nguyên 32-bit gắn vào giao dịch để phân biệt người nhận trên cùng 1 address (thường dùng bởi sàn giao dịch).
- **Range:** 0 — 4,294,967,295 (uint32)
- **Bắt buộc:** Tùy thuộc vào receiver — sàn giao dịch thường yêu cầu.
- **Link:** https://xrpl.org/source-and-destination-tags.html

---

## 3. Derivation Path

```
m / 44' / 144' / 0' / 0 / 0
     │      │      │    │   └── address_index
     │      │      │    └────── change (0 = external)
     │      │      └─────────── account
     │      └────────────────── coin_type (XRP = 144)
     └───────────────────────── purpose (BIP-44)
```

> **Lưu ý:** Module chỉ derive **1 địa chỉ** cho XRP (`derivateIdxMax(External) = 1`). XRP là account-based nên không cần nhiều địa chỉ như Bitcoin.

---

## 4. Kiến trúc Source Code

```
crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/
├── wallets/ripple/
│   ├── RippleManager.kt              # Manager chính — address, balance, transfer, fee
│   └── XrpTransactionSigner.kt       # Binary serialization + SHA-512Half signing
├── models/ripple/
│   └── RippleApiModel.kt             # Tất cả data models cho JSON-RPC
├── services/
│   └── RippleApiService.kt           # JSON-RPC client (account_info, account_tx, submit, fee)
├── utils/
│   └── Base58Ext.kt                  # Base58 encoding với Ripple alphabet
└── wallets/hdwallet/bip44/
    ├── ACTHDWallet.kt                # HD wallet derivation
    ├── ACTAddress.kt                 # Address generation
    └── ACTNetwork.kt                 # coin_type = 144
```

---

## 5. API Chi tiết

### 5.1. RippleManager

```kotlin
class RippleManager(
    mnemonic: String,
    private val apiService: RippleApiService = RippleApiService.INSTANCE
) : BaseCoinManager(), IWalletManager
```

#### Tạo địa chỉ

```kotlin
val xrpManager = RippleManager(mnemonic)
val address = xrpManager.getAddress()
// → "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh" (ví dụ)
```

#### Truy vấn số dư

```kotlin
val balance: Double = xrpManager.getBalance() // Đơn vị XRP
// Nội bộ: drops / 1_000_000.0
```

#### Lịch sử giao dịch

```kotlin
// Cơ bản
val history: List<RippleTransactionEntry>? = xrpManager.getTransactionHistory()

// Phân trang
val (transactions, nextMarker) = xrpManager.getTransactionHistoryPaginated(
    address = address,
    limit = 100,
    marker = null  // trang đầu
)
// Trang tiếp: truyền nextMarker vào lần gọi sau
```

#### Gửi XRP

```kotlin
val result = xrpManager.sendXrp(
    toAddress = "rRecipient...",
    amountDrops = 5_000_000L,        // 5 XRP
    feeDrops = 0L,                   // 0 = tự estimate
    destinationTag = 12345L          // tùy chọn (cho sàn)
)
// result: TransferResponseModel(success, error, hash)
```

**Flow nội bộ:**
1. Validate `walletAddress` tồn tại
2. Estimate fee nếu `feeDrops == 0`
3. Lấy `sequence` từ `account_info`
4. Lấy `currentLedgerIndex`, tính `lastLedgerSequence = ledgerIndex + 20`
5. Ký TX bằng `XrpTransactionSigner.signPayment()` → `tx_blob` (hex)
6. Submit `tx_blob` qua `submit` RPC
7. Kiểm tra `engine_result`: `tesSUCCESS` hoặc `terQUEUED` = thành công

#### Ước lượng phí

```kotlin
// Dynamic fee từ network (drops)
val feeDrops: Long = xrpManager.estimateFeeDynamic()

// Dynamic fee (XRP)
val feeXrp: Double = xrpManager.estimateFeeDynamicXrp()

// Default fee
val defaultFee: Double = xrpManager.estimateFee() // 0.000012 XRP (12 drops)
```

#### Hằng số quan trọng

```kotlin
const val XRP_DROPS_PER_UNIT = 1_000_000.0  // 1 XRP = 1,000,000 drops
const val DEFAULT_FEE_DROPS = 12L            // 0.000012 XRP
```

### 5.2. XrpTransactionSigner

```kotlin
object XrpTransactionSigner {
    fun signPayment(
        privateKey: ByteArray,           // 32-byte secp256k1
        publicKey: ByteArray,            // 33-byte compressed
        account: String,                 // r-address người gửi
        destination: String,             // r-address người nhận
        amountDrops: Long,
        feeDrops: Long,
        sequence: Long,                  // Account sequence number
        destinationTag: Long? = null,
        lastLedgerSequence: Long? = null
    ): String  // hex-encoded tx_blob
}
```

**Quy trình ký chi tiết:**

```
1. BUILD — Tạo Payment TX fields
   ├── TransactionType = "Payment" (type code 0)
   ├── Flags = 0x80000000 (tfFullyCanonicalSig)
   ├── Sequence = account sequence
   ├── Amount = amountDrops (XRP native encoding)
   ├── Fee = feeDrops
   ├── Account = sender AccountID (20 bytes)
   ├── Destination = recipient AccountID (20 bytes)
   ├── SigningPubKey = 33-byte compressed pubkey
   ├── DestinationTag = (optional)
   └── LastLedgerSequence = (optional)

2. SERIALIZE — Canonical binary format
   ├── Encode field headers (type_code << 4 | field_code)
   ├── Encode values (UInt32, Amount, AccountID, VL...)
   └── Output: serialized bytes (no TxnSignature)

3. HASH — SHA-512Half
   ├── Prepend: 0x53545800 (STX\0)
   ├── Input: prefix + serialized
   └── Output: SHA512(input)[0:32] — 32 bytes

4. SIGN — secp256k1 ECDSA
   ├── Sign hash with privateKey → compact signature
   └── Convert compact → DER format

5. REBUILD — Thêm TxnSignature
   ├── Insert DER signature vào TX fields
   ├── Serialize lại toàn bộ (có TxnSignature)
   └── Output: hex string → tx_blob
```

### 5.3. Base58Ext (Ripple Alphabet)

```kotlin
object Base58Ext {
    enum class Base58Type {
        Basic,    // Bitcoin alphabet: 123456789ABCDEFGH...
        Ripple    // Ripple alphabet:  rpshnaf39wBUDNEGH...
    }

    fun encode(input: ByteArray, type: Base58Type = Basic): String
    fun decode(input: String, type: Base58Type = Basic): ByteArray
}
```

> **Quan trọng:** Nếu dùng nhầm Bitcoin alphabet để decode XRP address → sai hoàn toàn. Luôn dùng `Base58Type.Ripple`.

---

## 6. JSON-RPC API

### 6.1. Endpoints

| Network | URL |
|---------|-----|
| **Mainnet** | `https://s1.ripple.com:51234/` |
| **Testnet (Altnet)** | `https://s.altnet.rippletest.net:51234/` |

### 6.2. Methods

#### `account_info` — Thông tin tài khoản

```json
{
    "method": "account_info",
    "params": [{
        "account": "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
        "ledger_index": "current"
    }]
}
```

**Response quan trọng:**
- `result.account_data.Balance` — Số dư (drops)
- `result.account_data.Sequence` — Sequence number cho TX tiếp theo
- `result.ledger_current_index` — Ledger index hiện tại

#### `account_tx` — Lịch sử giao dịch

```json
{
    "method": "account_tx",
    "params": [{
        "account": "rHb9CJA...",
        "limit": 100,
        "forward": false
    }]
}
```

**Response:** `result.transactions[]` — danh sách TX, `result.marker` — pagination token.

#### `submit` — Gửi giao dịch

```json
{
    "method": "submit",
    "params": [{
        "tx_blob": "1200002280000000..."
    }]
}
```

**Response quan trọng:**
- `result.engine_result` — Kết quả: `tesSUCCESS`, `terQUEUED`, hoặc error code
- `result.engine_result_message` — Mô tả kết quả
- `result.tx_json.hash` — Transaction hash

#### `fee` — Phí network

```json
{
    "method": "fee",
    "params": [{}]
}
```

**Response:**
```json
{
    "result": {
        "drops": {
            "base_fee": "10",
            "median_fee": "5000",
            "minimum_fee": "10",
            "open_ledger_fee": "10"
        }
    }
}
```

> **Khuyến nghị:** Dùng `open_ledger_fee` để TX được include vào ledger tiếp theo.

---

## 7. Data Models

### RippleAccountData

```kotlin
@Serializable
data class RippleAccountData(
    val account: String,       // r-address
    val balance: String,       // Drops (string)
    val sequence: Long,        // Sequence number
    val flags: Long = 0
)
```

### RippleTransactionEntry

```kotlin
@Serializable
data class RippleTransactionEntry(
    val tx: RippleTxData?,
    val meta: RippleTxMeta?,
    val validated: Boolean = false
)

@Serializable
data class RippleTxData(
    val account: String,
    val destination: String?,
    val amount: String,                // Drops
    val fee: String,                   // Drops
    val hash: String,
    val date: Long,                    // Ripple epoch (offset 946684800 từ Unix)
    val destinationTag: Long?,
    val memos: List<RippleMemoWrapper>?,
    val transactionType: String        // "Payment", etc.
)
```

### RippleMarker (Pagination)

```kotlin
@Serializable
data class RippleMarker(
    val ledger: Long,
    val seq: Long
)
```

### Engine Result Codes

| Code | Loại | Ý nghĩa |
|------|------|---------|
| `tesSUCCESS` | ✅ Success | TX được chấp nhận vào ledger |
| `terQUEUED` | ✅ Queued | TX đang chờ, sẽ retry |
| `tecUNFUNDED_PAYMENT` | ❌ Claim | Không đủ XRP |
| `tecNO_DST` | ❌ Claim | Destination account chưa tồn tại |
| `tecNO_DST_INSUF_XRP` | ❌ Claim | Gửi không đủ để kích hoạt account mới (< 10 XRP) |
| `tefPAST_SEQ` | ❌ Failure | Sequence number đã dùng |
| `tefMAX_LEDGER` | ❌ Failure | LastLedgerSequence đã qua |

---

## 8. Network Configuration

```kotlin
// Chuyển network
Config.shared.setNetwork(Network.MAINNET) // hoặc Network.TESTNET

// RippleApiService tự chọn URL dựa trên Config
val rpcUrl = when (Config.shared.getNetwork()) {
    Network.MAINNET -> "https://s1.ripple.com:51234/"
    Network.TESTNET -> "https://s.altnet.rippletest.net:51234/"
}
```

**Block Explorer:**
- Mainnet: `https://bithomp.com/explorer/{txHash}`
- Testnet: `https://test.bithomp.com/explorer/{txHash}`

---

## 9. Hướng dẫn tích hợp Android

### 9.1. Dependency

```kotlin
// build.gradle.kts
implementation("io.github.innfocus:crypto-wallet-lib:$version")
```

### 9.2. Khởi tạo và tạo địa chỉ

```kotlin
import com.lybia.cryptowallet.wallets.ripple.RippleManager
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.Network

Config.shared.setNetwork(Network.MAINNET)

val mnemonic = "your twelve word mnemonic phrase here ..."
val xrpManager = RippleManager(mnemonic)
val address = xrpManager.getAddress() // "rXXX..."
```

### 9.3. Lấy số dư và lịch sử

```kotlin
// Trong CoroutineScope
launch {
    val balance = xrpManager.getBalance() // Double, đơn vị XRP

    val history = xrpManager.getTransactionHistory()
    // history: List<RippleTransactionEntry>?

    withContext(Dispatchers.Main) {
        tvBalance.text = "$balance XRP"
    }
}
```

### 9.4. Lịch sử phân trang

```kotlin
launch {
    var marker: RippleMarker? = null

    do {
        val (transactions, nextMarker) = xrpManager.getTransactionHistoryPaginated(
            address = address,
            limit = 50,
            marker = marker
        )
        // Xử lý transactions
        marker = nextMarker
    } while (marker != null)
}
```

### 9.5. Gửi XRP

```kotlin
launch {
    val result = xrpManager.sendXrp(
        toAddress = "rRecipient...",
        amountDrops = 10_000_000L,    // 10 XRP
        feeDrops = 0L,                // 0 = auto estimate
        destinationTag = 12345L       // bắt buộc nếu gửi đến sàn
    )

    withContext(Dispatchers.Main) {
        if (result.success) {
            Toast.makeText(ctx, "TX: ${result.hash}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(ctx, "Error: ${result.error}", Toast.LENGTH_LONG).show()
        }
    }
}
```

### 9.6. Qua CoinsManager (Android wrapper)

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager

val coinsManager = CoinsManager.shared
coinsManager.setMnemonic(mnemonic)

launch {
    // CommonCoinsManager delegates to RippleManager
    val balance = coinsManager.commonCoinsManager.getBalance(NetworkName.XRP)
}
```

---

## 10. Hướng dẫn tích hợp iOS

### 10.1. Dependency

```swift
// XCFramework qua Swift Package Manager
// Framework: crypto-wallet-lib
```

### 10.2. Khởi tạo

```swift
import CryptoWalletLib

Config.shared.setNetwork(network: .mainnet)

let xrpManager = RippleManager(mnemonic: mnemonic)
let address = xrpManager.getAddress() // "rXXX..."
```

### 10.3. Async operations (Swift async/await)

```swift
// Balance
let balance = try await xrpManager.getBalance(address: nil, coinNetwork: nil)

// Lịch sử
let history = try await xrpManager.getTransactionHistory(address: nil, coinNetwork: nil)

// Gửi XRP
let result = try await xrpManager.sendXrp(
    toAddress: "rRecipient...",
    amountDrops: 10_000_000,
    feeDrops: 0,               // auto estimate
    destinationTag: 12345
)

if result.success {
    print("TX Hash: \(result.hash ?? "")")
}
```

> **Lưu ý:** Ktor Darwin engine sử dụng NSURLSession. Suspend functions → Swift `async throws`.

---

## 11. Đơn vị và chuyển đổi

| Đơn vị | Giá trị | Sử dụng |
|--------|---------|---------|
| **drop** | 1 drop | `amountDrops`, `feeDrops`, API balance |
| **XRP** | 1,000,000 drops | `getBalance()` trả về XRP |

```kotlin
// Chuyển đổi
val xrp = drops / 1_000_000.0
val drops = (xrp * 1_000_000).toLong()
```

---

## 12. Chạy Tests

```bash
# Compile kiểm tra
./gradlew :crypto-wallet-lib:compileAndroidMain

# Chạy test Ripple
./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*Ripple*"
```

---

## 13. Lưu ý quan trọng

1. **Account reserve:** Mỗi XRP account cần tối thiểu **10 XRP** để kích hoạt trên ledger. Gửi ít hơn đến account mới → `tecNO_DST_INSUF_XRP`. ✅ `sendXrp()` đã validate reserve trước send.
2. **Destination Tag:** Bắt buộc khi gửi đến sàn giao dịch. Quên → mất XRP.
3. **Base58 Alphabet:** XRP dùng bảng chữ cái **khác Bitcoin**. Không bao giờ dùng Bitcoin Base58 để decode XRP address.
4. **Sequence number:** Mỗi TX phải có sequence tăng dần. Lấy từ `account_info` trước mỗi lần gửi.
5. **LastLedgerSequence:** Đã sửa thành `+75` (~5 phút), an toàn khi network congestion.
6. **Ripple epoch:** Thời gian trong TX = Unix timestamp - 946684800 (offset từ 2000-01-01).
7. **SHA-512Half:** XRP dùng 32 byte đầu của SHA-512, **không phải** SHA-256 như Bitcoin.
8. **Fee mặc định:** 12 drops (0.000012 XRP). Dùng `estimateFeeDynamic()` cho fee chính xác.
9. **Memo:** ✅ Đã hỗ trợ memo text (STArray format, MemoType = "text/plain").

---

## 14. Dependencies

| Thư viện | Version | Mục đích |
|----------|---------|---------|
| `secp256k1-kmp` | 0.23.0 | ECDSA signing (secp256k1) |
| `bitcoin-kmp` | 0.30.0 | Base58 encoding utilities |
| `krypto` (Korlibs) | 4.0.10 | SHA-512 hashing |
| `ktor-client-core` | 3.4.2 | HTTP client (JSON-RPC) |
| `kotlinx-serialization-json` | 1.10.0 | JSON serialization |
| `kotlinx-coroutines-core` | 1.10.2 | Async/await |

---

## 15. Tài liệu tham khảo

| Tài liệu | Link | Nội dung |
|-----------|------|---------|
| XRP Ledger Overview | https://xrpl.org/xrp-ledger-overview.html | Tổng quan blockchain |
| Cryptographic Keys | https://xrpl.org/cryptographic-keys.html | secp256k1 / Ed25519 |
| Address Encoding | https://xrpl.org/accounts.html#address-encoding | Base58Ripple, AccountID |
| Base58 Encodings | https://xrpl.org/base58-encodings.html | Ripple alphabet |
| Serialization | https://xrpl.org/serialization.html | Canonical binary format |
| Transaction Signing | https://xrpl.org/transaction-basics.html | SHA-512Half, STX prefix |
| Payment Transaction | https://xrpl.org/payment.html | Payment fields |
| Reliable Submission | https://xrpl.org/reliable-transaction-submission.html | LastLedgerSequence |
| Destination Tags | https://xrpl.org/source-and-destination-tags.html | Tag cho sàn giao dịch |
| Fee Voting | https://xrpl.org/fee-voting.html | Cơ chế phí network |
| Transaction Results | https://xrpl.org/transaction-results.html | Engine result codes |
| SLIP-44 Coin Types | https://github.com/satoshilabs/slips/blob/master/slip-0044.md | XRP = 144 |
| BIP-44 Standard | https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki | HD derivation path |
| rippled API Reference | https://xrpl.org/public-api-methods.html | JSON-RPC methods |
| XRP Testnet Faucet | https://xrpl.org/xrp-testnet-faucet.html | Lấy XRP testnet |
| Reserves | https://xrpl.org/reserves.html | Account reserve (10 XRP base + owner) |
| Memo Field | https://xrpl.org/transaction-common-fields.html#memos-field | STArray Memo serialization |
| X-Address Format | https://xrpaddress.info/ | r-address + tag → X-address |
| RequireFullyCanonicalSig | https://xrpl.org/known-amendments.html#requirefullycanonicalsig | Bắt buộc flag 0x80000000 |
| Transaction Types | https://xrpl.org/transaction-types.html | Tất cả TX types (Payment, TrustSet, ...) |

---

## 16. Audit & Known Issues

> **Ngày audit:** 2026-04-06  
> **So sánh với:** Source cũ tại `/Users/thanhphat/GitHub/demo/cryptowallet` (Android-only, Retrofit + callback)  
> **Kết luận tổng quát:** Kiến trúc KMP sạch hơn, crypto đúng, nhưng **thiếu một số tính năng** mà code cũ đã có.

### Tổng hợp trạng thái

| # | Vấn đề | Mức độ | File | Trạng thái |
|---|--------|--------|------|-----------|
| BUG-1 | Thiếu Memo serialization | 🟡 MEDIUM | `XrpTransactionSigner.kt` | ✅ Fixed 2026-04-06 |
| BUG-2 | LastLedgerSequence offset quá nhỏ (20 vs 75) | 🟡 MEDIUM | `RippleManager.kt` | ✅ Fixed 2026-04-06 |
| BUG-3 | Thiếu balance validation trước send | 🟠 LOW-MEDIUM | `RippleManager.kt` | ✅ Fixed 2026-04-06 |
| BUG-4 | Thiếu local Transaction ID | 🟢 LOW | `XrpTransactionSigner.kt` | ✅ Fixed 2026-04-06 |
| BUG-5 | Thiếu service fee (second TX) | 🟢 LOW | `RippleManager.kt` | ❌ Chưa fix |
| BUG-6 | Thiếu input validation trong signer | 🟡 MEDIUM | `XrpTransactionSigner.kt:92-98` | ✅ Fixed 2026-04-06 |
| BUG-7 | Duplicate RPC call (getSequence + getLedgerIndex) | 🟢 LOW | `RippleManager.kt` | ✅ Fixed 2026-04-06 |
| IMPROVE-1 | `e.printStackTrace()` thay vì logger | 🟢 LOW | `RippleApiService.kt` | ❌ Chưa fix |
| IMPROVE-2 | Thiếu HTTP request timeout | 🟡 MEDIUM | `RippleApiService.kt` | ❌ Chưa fix |
| IMPROVE-3 | Duplicate Base58 decode (signer vs Base58Ext) | 🟢 LOW | `XrpTransactionSigner.kt:351` | ❌ Chưa fix |

### Chi tiết từng vấn đề

#### BUG-1: Thiếu Memo serialization (MEDIUM)

**Mô tả:** Code cũ serialize memo text đầy đủ theo XRP Ledger STArray format. KMP chỉ define `FIELD_MEMOS = 9` nhưng **không bao giờ sử dụng** trong `buildPaymentFields()`.

**Code cũ** — `XRPTransactionRaw.kt:98-107`:
```kotlin
if (memo != null && memo?.memo != null && memo?.memo!!.isNotEmpty()) {
    data += XRPMemoEnum.Starts.value   // 0xF9 — STArray begin
    data += XRPMemoEnum.Start.value    // 0xEA — STObject begin  
    data += XRPMemoEnum.Data.value     // 0x7D — MemoData field
    data += bs.count().toByte()
    data += bs
    data += XRPMemoEnum.End.value      // 0xE1 — STObject end
    data += XRPMemoEnum.Ends.value     // 0xF1 — STArray end
}
```

**KMP hiện tại** — `XrpTransactionSigner.kt`: Hoàn toàn không có memo serialization.

**Ảnh hưởng:** Không thể gửi TX có memo text. DestinationTag vẫn hoạt động (khác field).

**Cách fix:** Thêm memo parameter vào `signPayment()` và serialize theo STArray format trong `buildPaymentFields()`.

**Spec tham khảo:** https://xrpl.org/transaction-common-fields.html#memos-field

---

#### BUG-2: LastLedgerSequence offset quá nhỏ (MEDIUM)

**Mô tả:** KMP dùng offset `+20` (~1 phút), code cũ dùng `+75` (~5 phút).

| | Offset | Thời gian | Rủi ro |
|---|---|---|---|
| **Code cũ** | `ledgerIndex + 75` | ~5 phút | Thấp — đủ thời gian cho network congestion |
| **KMP mới** | `ledgerIndex + 20` | ~1 phút | Cao — TX có thể hết hạn khi mạng chậm |

**File:** `RippleManager.kt:169`
```kotlin
val lastLedgerSeq = if (ledgerIndex > 0) ledgerIndex + 20 else null
```

**Cách fix:** Đổi `20` → `75` hoặc cho phép cấu hình.

---

#### BUG-3: Thiếu balance validation trước khi gửi (LOW-MEDIUM)

**Mô tả:** Code cũ kiểm tra balance đủ (bao gồm reserve 10 XRP + fee) trước khi build TX. KMP gửi thẳng, phụ thuộc ledger reject.

**Code cũ** — `Gxrp.kt:183-184`:
```kotlin
if (balance < (((amount + nw.coin.minimumAmount()) * XRPCoin) + networkFee)) {
    return completionHandler.completionHandler("", null, false, "Insufficient Funds")
}
```

**KMP hiện tại:** Không validate → ledger trả `tecUNFUNDED_PAYMENT` → UX kém (user phải đợi submit mới biết lỗi).

**Cách fix:** Thêm balance check trong `sendXrp()` trước khi ký:
```kotlin
val balance = getBalance() 
val requiredXrp = amountDrops / XRP_DROPS_PER_UNIT + actualFee / XRP_DROPS_PER_UNIT + 10.0 // reserve
if (balance < requiredXrp) return TransferResponseModel(false, "Insufficient Funds", null)
```

---

#### BUG-4: Thiếu local Transaction ID (LOW)

**Mô tả:** Code cũ tính Transaction ID locally bằng `SHA-512Half(0x54584E00 + signed_blob)` → có TX hash ngay sau khi ký, trước khi submit. KMP chỉ lấy hash từ submit response `tx_json.hash`.

**Rủi ro:** Nếu submit thành công nhưng response bị lỗi mạng → mất TX hash, không track được.

**Code cũ** — `XRPTransactionRaw.kt:26-29`:
```kotlin
fun transactionID(): String? {
    val ser = serializer(XRPHashPrefix.TransactionID) ?: return null
    return ser.sha512().prefix(32).toHexString()
}
```

**Cách fix:** Thêm hàm `computeTransactionId()` trong `XrpTransactionSigner`:
```kotlin
private val HASH_PREFIX_TX_ID = byteArrayOf(0x54, 0x58, 0x4E, 0x00) // "TXN\0"
fun computeTransactionId(signedBlob: ByteArray): String {
    return sha512Half(HASH_PREFIX_TX_ID + signedBlob).toHexString()
}
```

---

#### BUG-5: Thiếu service fee (second transaction) (LOW)

**Mô tả:** Code cũ trong `CoinsManager` gửi TX thứ 2 với `sequence + 1` để thu phí dịch vụ cho ứng dụng. KMP chưa triển khai.

**Ảnh hưởng:** Không ảnh hưởng chức năng cơ bản. Cần khi integrate service fee vào app.

**Cách fix:** Thêm parameter `serviceAddress` + `serviceFeeDrops` vào `sendXrp()`, gửi TX thứ 2 nếu có.

---

#### BUG-6: Thiếu input validation trong XrpTransactionSigner (MEDIUM)

**Mô tả:** `signPayment()` không validate input parameters → invalid input tạo TX blob sai, chỉ bị reject bởi ledger.

**File:** `XrpTransactionSigner.kt:61`

**Cách fix:**
```kotlin
fun signPayment(...): String {
    require(privateKey.size == 32) { "Private key must be 32 bytes" }
    require(publicKey.size == 33) { "Public key must be 33 bytes (compressed)" }
    require(amountDrops in 1..9_999_999_999_999_999L) { "Amount out of valid range" }
    require(feeDrops in 1..1_000_000_000L) { "Fee out of valid range" }
    require(sequence >= 1) { "Sequence must be >= 1" }
    // ...
}
```

---

#### BUG-7: Duplicate RPC call (LOW)

**Mô tả:** `sendXrp()` gọi `account_info` **2 lần** riêng biệt — 1 cho `getSequence()`, 1 cho `getCurrentLedgerIndex()`.

**File:** `RippleManager.kt:167-168`
```kotlin
val sequence = getSequence()              // → getAccountInfo(addr) lần 1
val ledgerIndex = getCurrentLedgerIndex() // → getAccountInfo(addr) lần 2
```

**Cách fix:** Gộp thành 1 call:
```kotlin
val info = apiService.getAccountInfo(addr) ?: throw Exception("...")
val sequence = info.result.accountData?.sequence ?: throw Exception("...")
val ledgerIndex = info.result.ledgerCurrentIndex ?: 0L
```

---

#### IMPROVE-1: Dùng logger thay printStackTrace() (LOW)

**File:** `RippleApiService.kt` — lines 65, 120, 144, 169

**Hiện tại:** `e.printStackTrace()` → output ra stderr, không format.

**Cách fix:** Dùng Kermit logger (đã có trong project):
```kotlin
private val logger = co.touchlab.kermit.Logger.withTag("RippleApiService")
// ...
catch (e: Exception) { logger.e(e) { "RPC call failed" }; null }
```

---

#### IMPROVE-2: Thiếu HTTP request timeout (MEDIUM)

**File:** `RippleApiService.kt`

**Ảnh hưởng:** Request có thể treo vô thời hạn khi mạng có vấn đề.

**Cách fix:** Cấu hình timeout cho HttpClient:
```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = 30_000
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 30_000
}
```

---

#### IMPROVE-3: Duplicate Base58 decode (LOW)

**Mô tả:** `XrpTransactionSigner.kt:271-289` có `base58Decode()` riêng, trùng với `Base58Ext.decode()`.

**Cách fix:** Dùng `Base58Ext.decode(input, Base58Type.Ripple)` thay thế.

---

### So sánh KMP vs Code cũ

| Thành phần | Code cũ (Android) | KMP (commonMain) | Nhận xét |
|---|---|---|---|
| Async pattern | Callback (Retrofit) | Coroutines (Ktor) | ✅ KMP tốt hơn |
| Signing algorithm | SHA-512Half + secp256k1 | SHA-512Half + secp256k1 | ✅ Giống nhau |
| Serialization order | Enum-based field codes | Computed field headers | ✅ Cả 2 đúng spec |
| Memo serialization | ✅ STArray format | ❌ Chưa có | 🔧 Cần port |
| Balance pre-check | ✅ Check reserve + fee | ❌ Không check | 🔧 Cần thêm |
| Local TX ID | ✅ SHA-512Half(TXN\0 + blob) | ❌ Chỉ từ response | 🔧 Cần thêm |
| Fee estimation | Hardcode 12 drops | ✅ Dynamic (open_ledger_fee) | ✅ KMP tốt hơn |
| LastLedgerSequence | +75 (~5 min) | +20 (~1 min) | 🔧 KMP quá chặt |
| Service fee | ✅ TX thứ 2, sequence+1 | ❌ Chưa có | 🔧 Cần thêm |
| Pagination | ✅ Marker-based | ✅ Marker-based | ✅ Giống nhau |
| Error handling | Callback error string | ✅ TransferResponseModel | ✅ KMP tốt hơn |
| Success check | `engineResultCode == 0` | `tesSUCCESS` or `terQUEUED` | ✅ KMP chính xác hơn |
| Cross-platform | ❌ Android-only | ✅ commonMain | ✅ KMP tốt hơn |
| Input validation | Không | Không | 🔧 Cả 2 thiếu |

---

## 17. Tiêu chuẩn cần bổ sung (Roadmap)

### Priority 1 — Cần cho production

| Tiêu chuẩn | Mô tả | File cần sửa | Link |
|---|---|---|---|
| **Memo Fields (STArray)** | Serialize MemoType + MemoData trong STArray wrapper. Code cũ đã có. | `XrpTransactionSigner.kt` | https://xrpl.org/transaction-common-fields.html#memos-field |
| **Account Reserve Validation** | Check 10 XRP base reserve + owner reserve trước send | `RippleManager.kt` | https://xrpl.org/reserves.html |
| **Reliable TX Submission** | Retry logic + verify qua `tx` RPC sau submit | `RippleManager.kt` | https://xrpl.org/reliable-transaction-submission.html |
| **Transaction ID (local)** | Compute TX hash = SHA-512Half(`TXN\0` + signed_blob) | `XrpTransactionSigner.kt` | https://xrpl.org/transaction-basics.html |

### Priority 2 — Nên có

| Tiêu chuẩn | Mô tả | Link |
|---|---|---|
| **X-Address format** | Format mới gộp r-address + destination tag thành 1 chuỗi `X...` | https://xrpaddress.info/ |
| **Account activation detection** | Kiểm tra destination account tồn tại chưa, yêu cầu >= 10 XRP nếu account mới | https://xrpl.org/accounts.html#creating-accounts |
| **`tx` RPC method** | Verify TX đã được validated trên ledger (post-submit confirmation) | https://xrpl.org/tx.html |
| **`server_info` RPC** | Lấy trạng thái node, network_id, validated_ledger | https://xrpl.org/server_info.html |

### Priority 3 — Mở rộng tương lai

| Tiêu chuẩn | Transaction Type | Mô tả | Link |
|---|---|---|---|
| **TrustSet** | `TrustSet` | Tạo trust line cho IOU tokens (USD, EUR, etc.) | https://xrpl.org/trustset.html |
| **IOU Payment** | `Payment` (with currency) | Gửi issued currencies qua trust lines | https://xrpl.org/payment.html |
| **AccountSet** | `AccountSet` | Cấu hình account flags (requireDestTag, disallowXRP, etc.) | https://xrpl.org/accountset.html |
| **Escrow** | `EscrowCreate/Finish/Cancel` | Conditional payments (time-lock, crypto-condition) | https://xrpl.org/escrow.html |
| **Payment Channels** | `PaymentChannelCreate/Fund/Claim` | Off-ledger micropayments | https://xrpl.org/payment-channels.html |
| **NFTokenMint** | `NFTokenMint/Offer` | XLS-20 NFT support | https://xrpl.org/nftoken-tester-tutorial.html |
| **AMM** | `AMMCreate/Deposit/Withdraw` | Automated Market Maker (XLS-30d) | https://xrpl.org/automated-market-makers.html |
| **Multi-signing** | `SignerListSet` | Multi-signature transactions | https://xrpl.org/multi-signing.html |
| **Ed25519 key** | — | Hỗ trợ Ed25519 ngoài secp256k1 | https://xrpl.org/cryptographic-keys.html |
