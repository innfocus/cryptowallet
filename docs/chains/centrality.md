# Centrality (CENNZnet) — Đặc tả Kỹ thuật

**Version:** 1.0  
**Status:** ✅ Cross-platform (commonMain) | ✅ Android | ⏳ iOS (chưa tích hợp)  
**Scope:** `commonMain/kotlin/com/lybia/cryptowallet/wallets/centrality/`  
**Related:** [CENNZnet Wiki](https://wiki.cennz.net/), [Substrate Docs](https://docs.substrate.io/)  
**Audit:** 2026-04-06 — So sánh với source cũ (`/demo/cryptowallet`), xem [Mục 16](#16-audit--known-issues)

---

## 1. Tổng quan

Module Centrality trong `crypto-wallet-lib` hỗ trợ tạo ví CENNZnet từ BIP-39 mnemonic, ký và gửi giao dịch asset transfer qua JSON-RPC (Substrate node), với multi-asset (CENNZ/CPAY).

### Tính năng chính

| Tính năng | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Tạo ví từ BIP-39 mnemonic | ✅ | PBKDF2-SHA512, Sr25519 |
| Địa chỉ SS58 | ✅ | Parse, validate, encode |
| Truy vấn số dư | ✅ | Explorer API `scanAccount` |
| Lịch sử giao dịch (phân trang) | ✅ | Explorer API `scanTransfers` + row/page |
| Gửi CENNZ/CPAY (asset transfer) | ✅ | Full flow: chain state → build → sign → submit |
| Multi-asset (CENNZ + CPAY) | ✅ | `assetId` param: 1 = CENNZ, 2 = CPAY |
| SCALE codec | ✅ | Compact encoding/decoding cho Substrate |
| Extrinsic builder | ✅ | Substrate extrinsic v4 (version byte 132) |
| Era (mortality) | ✅ | Mortal transaction, calPeriod = 128 |
| Error handling | ✅ | Sealed class `CentralityError` |
| Mainnet / Testnet | ❌ | Chỉ mainnet — hardcode endpoints |
| Local Sr25519 signing | ❌ | Phụ thuộc external API (`fgwallet.srsfc.com`) |

### So sánh với Ripple

| Đặc điểm | Ripple (XRP) | Centrality (CENNZnet) |
|-----------|-------------|----------------------|
| Mô hình | Account-based | **Account-based** (Substrate) |
| Consensus | RPCA | **BABE/GRANDPA** (PoS) |
| Signing algorithm | secp256k1 (ECDSA) | **Sr25519** (Schnorrkel) |
| Address format | Base58Ripple (`r...`) | **SS58** (`5...` hoặc `cg...`) |
| Transaction format | Binary canonical | **SCALE-encoded extrinsic** |
| Multi-asset | Không (chỉ XRP native) | **Có** (CENNZ = 1, CPAY = 2) |
| Phí | Dynamic (drops) | Mặc định 15287 units (~1.5 CENNZ) |
| Reserve | 10 XRP | Không |
| Thời gian block | 3-5 giây | **~5 giây** |

---

## 2. Tiêu chuẩn Blockchain (Standards)

### 2.1. Substrate Framework

- **Mô tả:** CENNZnet được xây dựng trên Substrate — framework blockchain modular của Parity Technologies. Substrate sử dụng SCALE encoding cho tất cả dữ liệu on-chain và extrinsic format riêng cho giao dịch.
- **Runtime versioning:** Mỗi phiên bản runtime có `specVersion` và `transactionVersion`. Phải lấy từ chain trước khi build extrinsic.
- **Link:** https://docs.substrate.io/

### 2.2. Cryptographic Curve — Sr25519 (Schnorrkel)

- **Mô tả:** CENNZnet sử dụng **Sr25519** — signature scheme dựa trên Ristretto group (Curve25519), thiết kế bởi Parity/Web3 Foundation.
- **Key format:** 32-byte public key.
- **Signature:** 64-byte Sr25519 signature.
- **So sánh Ed25519:** Sr25519 hỗ trợ VRF (Verifiable Random Function), an toàn hơn khi dùng cùng key cho nhiều protocol.
- **Link:** https://wiki.polkadot.network/docs/learn-cryptography

### 2.3. SS58 Address Encoding

- **Mô tả:** SS58 là format địa chỉ chuẩn của Substrate, tương tự Base58Check của Bitcoin.
- **Format:** `Base58(network_prefix + public_key + checksum)`
- **Checksum:** Blake2b-512 với prefix `"SS58PRE"`, lấy 2 byte đầu.
- **Network prefix:** CENNZnet = 42 (generic Substrate).
- **Quy trình:**
  1. Prepend network prefix byte
  2. Hash = `Blake2b_512("SS58PRE" + prefix + public_key)`
  3. Checksum = hash[0:2]
  4. Encode `(prefix + public_key + checksum)` bằng Base58
- **Kết quả:** Chuỗi bắt đầu bằng `5`, dài ~48 ký tự.
- **Link:** https://docs.substrate.io/reference/address-formats/

### 2.4. SCALE Encoding (Simple Concatenated Aggregate Little-Endian)

- **Mô tả:** Codec nhị phân compact của Substrate, dùng cho tất cả on-chain data.
- **Compact integer encoding:**
  - Mode 0 (single-byte): giá trị 0–63 → `(value << 2) | 0b00`
  - Mode 1 (two-byte): giá trị 64–16383 → `(value << 2) | 0b01` (LE)
  - Mode 2 (four-byte): giá trị 16384–2^30-1 → `(value << 2) | 0b10` (LE)
  - Mode 3 (big-integer): giá trị >= 2^30 → `(byte_count - 4) << 2 | 0b11` + LE bytes
- **Link:** https://docs.substrate.io/reference/scale-codec/

### 2.5. Extrinsic Format (Substrate v4)

- **Mô tả:** Giao dịch Substrate (extrinsic) gồm compact length prefix + signed payload.
- **Version byte:** `0x84` (132) = signed extrinsic, version 4.
- **Format:**
  ```
  compact_length(
    version_byte(0x84)
    + signer_address(32 bytes)
    + signature_type(0x01 = Sr25519)
    + signature(64 bytes)
    + era(2 bytes)
    + compact(nonce)
    + compact(tip = 0)
    + method(call_index + args)
  )
  ```
- **Call index cho asset transfer:** `0x0401` (pallet 4, function 1).
- **Link:** https://docs.substrate.io/reference/transaction-format/

### 2.6. Mortal Era (Transaction Expiry)

- **Mô tả:** CENNZnet dùng mortal era — giao dịch chỉ hợp lệ trong một số block nhất định.
- **calPeriod:** 128 blocks (~10 phút).
- **Encoding:** 2 bytes, computed từ `currentBlockNumber % calPeriod`.
- **Công thức:**
  ```
  quantizedPhase = currentBlockNumber % calPeriod
  encoded = 6 + (quantizedPhase << 4)
  era[0] = encoded & 0xff
  era[1] = encoded >> 8
  ```
- **Link:** https://docs.substrate.io/reference/transaction-format/#mortal-transactions

### 2.7. BIP-44 Derivation cho CENNZnet

- **Mô tả:** CENNZnet dùng BIP-44 standard với coin_type = 392.
- **Path:** `m/44'/392'/0'/0/0`
- **Seed derivation:** PBKDF2-SHA512(entropy, "mnemonic", 2048 iterations, 32 bytes).
- **Lưu ý:** Seed hex được gửi đến external API để derive Sr25519 keypair. Không derive local.
- **Registered:** SLIP-44 — coin type 392.
- **Link:** https://github.com/satoshilabs/slips/blob/master/slip-0044.md

---

## 3. Derivation Path

```
m / 44' / 392' / 0' / 0 / 0
     │      │      │    │   └── address_index
     │      │      │    └────── change (0 = external)
     │      │      └─────────── account
     │      └────────────────── coin_type (CENNZnet = 392)
     └───────────────────────── purpose (BIP-44)
```

> **Lưu ý:** Module chỉ derive **1 địa chỉ** cho CENNZnet. Account-based chain nên không cần nhiều địa chỉ.

> **Quan trọng:** Address derivation phụ thuộc external API (`fgwallet.srsfc.com/cennz-address`). Seed hex được gửi qua HTTPS đến API, trả về SS58 address + public key. Không có local Sr25519 key derivation.

---

## 4. Kiến trúc Source Code

```
crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/
├── wallets/centrality/
│   ├── CentralityManager.kt              # Manager chính — address, balance, transfer, sendCoin
│   ├── CentralityError.kt                # Sealed class error hierarchy
│   ├── codec/
│   │   └── ScaleCodec.kt                 # SCALE compact encoding/decoding
│   └── model/
│       ├── CentralityAddress.kt           # SS58 parse, validate, encode
│       ├── ExtrinsicBuilder.kt            # Substrate extrinsic builder (v4)
│       ├── CentralityApiModels.kt         # ScanAccount, ScanTransfer response wrappers
│       ├── CennzTransfer.kt               # Transaction history item
│       ├── CennzExtrinsic.kt              # Detailed extrinsic info
│       ├── CennzPartialFee.kt             # Fee estimation response
│       └── CennzScanAsset.kt              # Asset balance (assetId, free, lock)
├── services/
│   └── CentralityApiService.kt            # JSON-RPC + REST client (Ktor)
├── enums/
│   ├── ACTCoin.kt                         # ACTCoin.Centrality entry
│   └── Network.kt                         # NetworkName.CENTRALITY
└── coinkits/
    ├── ChainManagerFactory.kt             # CENTRALITY → CentralityManager
    └── CommonCoinsManager.kt              # Facade: getCentralityBalance, sendCentrality
```

---

## 5. API Chi tiết

### 5.1. CentralityManager

```kotlin
class CentralityManager(
    private val mnemonic: String,
    private val apiService: CentralityApiService = CentralityApiService(),
    private val assetId: Int = 1        // 1 = CENNZ, 2 = CPAY
) : IWalletManager
```

#### Tạo địa chỉ

```kotlin
val cennzManager = CentralityManager(mnemonic, assetId = 1)

// Phải gọi initAddress() trước (async — gọi external API)
cennzManager.initAddress()
val address = cennzManager.getAddress()
// → "5GjQP..." (SS58 format)
```

> **Quan trọng:** `getAddress()` trả về `""` nếu chưa gọi `initAddress()` hoặc `getAddressAsync()`. Đây là do address derivation phụ thuộc external API.

#### Truy vấn số dư

```kotlin
val balance: Double = cennzManager.getBalance() // Đơn vị CENNZ (đã chia BASE_UNIT)
// Nội bộ: asset.free / 10_000.0
```

#### Lịch sử giao dịch

```kotlin
// Cơ bản
val history: List<CennzTransfer>? = cennzManager.getTransactionHistory()

// Phân trang
val transactions: List<CennzTransfer> = cennzManager.getTransactionHistoryPaginated(
    address = address,
    row = 100,       // max per page
    page = 0         // 0-based page number
)
```

#### Gửi CENNZ/CPAY

```kotlin
val result = cennzManager.sendCoin(
    fromAddress = "5GjQP...",
    toAddress = "5Recipient...",
    amount = 100.0,              // Đơn vị display (CENNZ)
    assetId = 1                  // 1 = CENNZ, 2 = CPAY
)
// result: TransferResponseModel(success, error, txHash)
```

**Flow nội bộ:**
1. `getRuntimeVersion()` → lấy specVersion, transactionVersion
2. `chainGetBlockHash()` → genesis hash
3. `chainGetFinalizedHead()` → block hash hiện tại
4. `chainGetHeader(blockHash)` → block number hiện tại
5. `systemAccountNextIndex(fromAddress)` → nonce
6. Tính era option từ block number (calPeriod = 128)
7. Build `ExtrinsicBuilder`: paramsMethod → paramsSignature → signOptions
8. `createPayload()` → `signMessage(seed, payloadHex)` (external API)
9. `submitExtrinsic(signedHex)` → tx hash

#### Ước lượng phí

```kotlin
// Mặc định (không gọi chain)
val fee: Double = ACTCoin.Centrality.feeDefault() // 15287.0 (CENNZ units)
// = 1.5287 CENNZ

// Dynamic từ chain (available nhưng chưa dùng trong facade)
val partialFee: CennzPartialFee = apiService.paymentQueryInfo(extrinsicHex)
```

#### Hằng số quan trọng

```kotlin
const val BASE_UNIT = 10_000           // 1 CENNZ = 10,000 smallest units
const val CAL_PERIOD = 128             // Era period (blocks)
const val CALL_INDEX = "0x0401"        // assets.transfer
const val EXTRINSIC_VERSION = 132      // 0x84 = signed v4
```

### 5.2. ExtrinsicBuilder

```kotlin
class ExtrinsicBuilder {
    fun paramsMethod(to: String, amount: Long, assetId: Int = 1): ExtrinsicBuilder
    fun paramsSignature(signer: String, nonce: Int): ExtrinsicBuilder
    fun signOptions(specVersion: Int, transactionVersion: Int,
                    genesisHash: String, blockHash: String, era: ByteArray): ExtrinsicBuilder
    fun sign(signatureHex: String): ExtrinsicBuilder
    fun createPayload(): ByteArray
    fun toU8a(): ByteArray
    fun toHex(): String     // "0x" prefix
}
```

**Quy trình build chi tiết:**

```
1. BUILD — Tạo extrinsic fields
   ├── CallIndex = 0x0401 (assets.transfer)
   ├── Args: compact(assetId) + publicKey(to) + compact(amount)
   ├── Signer = SS58 address → publicKey (32 bytes)
   └── Nonce = account nonce (compact encoded)

2. CREATE PAYLOAD — Dữ liệu cần ký
   ├── Method bytes (call index + args)
   ├── Era (2 bytes mortal)
   ├── Nonce (compact encoded)
   ├── Transaction payment (2 bytes, default 0)
   ├── specVersion (4 bytes LE)
   ├── transactionVersion (4 bytes LE)
   ├── genesisHash (32 bytes)
   └── blockHash (32 bytes)

3. SIGN — External API
   ├── Send seed + payload hex to fgwallet.srsfc.com
   └── Receive 64-byte Sr25519 signature

4. ENCODE — Signed extrinsic
   ├── compact_length prefix
   ├── Version byte: 0x84
   ├── Signer public key (32 bytes)
   ├── Signature type: 0x01 (Sr25519)
   ├── Signature (64 bytes)
   ├── Era + nonce + tip
   └── Method bytes
```

### 5.3. CentralityAddress

```kotlin
class CentralityAddress(address: String) {
    val address: String
    val publicKey: ByteArray?

    fun isValid(): Boolean

    companion object {
        fun parseAddress(address: String): ByteArray?    // Extract public key
        fun encodeSS58(publicKey: ByteArray): String      // Encode to SS58
    }
}
```

### 5.4. ScaleCodec

```kotlin
object ScaleCodec {
    fun compactToU8a(value: BigInteger): ByteArray     // Encode compact integer
    fun compactFromU8a(input: ByteArray): Pair<BigInteger, Int>  // Decode compact integer
    fun toArrayLikeLE(value: BigInteger, byteLength: Int): ByteArray  // LE encoding
    fun compactAddLength(input: ByteArray): ByteArray   // Prepend compact length
}
```

---

## 6. JSON-RPC API

### 6.1. Endpoints

| Loại | URL | Mục đích |
|------|-----|---------|
| **RPC (Substrate node)** | `https://cennznet.unfrastructure.io/public` | JSON-RPC methods |
| **Explorer API** | `https://service.eks.centralityapp.com/cennznet-explorer-api` | Balance, transactions |
| **Local signing API** | `https://fgwallet.srsfc.com` | Address derivation, signing |

> **Lưu ý:** Tất cả endpoints chỉ mainnet. Không có testnet configuration.

### 6.2. JSON-RPC Methods (Substrate Node)

#### `state_getRuntimeVersion` — Phiên bản runtime

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "state_getRuntimeVersion",
    "params": []
}
```

**Response quan trọng:**
- `result.specVersion` — Spec version (e.g., 39)
- `result.transactionVersion` — TX version (e.g., 5)

#### `chain_getBlockHash` — Genesis hash

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "chain_getBlockHash",
    "params": [0]
}
```

**Response:** `result` = genesis hash hex string (32 bytes).

#### `chain_getFinalizedHead` — Block hash cuối cùng đã finalize

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "chain_getFinalizedHead",
    "params": []
}
```

**Response:** `result` = finalized block hash hex string.

#### `chain_getHeader` — Thông tin block header

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "chain_getHeader",
    "params": ["0xBlockHash..."]
}
```

**Response quan trọng:**
- `result.number` — Block number (hex string, e.g., `"0x6211cb"`)

#### `system_accountNextIndex` — Nonce cho TX tiếp theo

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "system_accountNextIndex",
    "params": ["5GjQP..."]
}
```

**Response:** `result` = integer (next nonce).

#### `payment_queryInfo` — Fee estimation

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "payment_queryInfo",
    "params": ["0xSignedExtrinsicHex..."]
}
```

**Response:**
```json
{
    "result": {
        "class": "Normal",
        "partialFee": "15287",
        "weight": 195000000
    }
}
```

#### `author_submitExtrinsic` — Submit signed extrinsic

```json
{
    "id": 1, "jsonrpc": "2.0",
    "method": "author_submitExtrinsic",
    "params": ["0xSignedExtrinsicHex..."]
}
```

**Response:** `result` = extrinsic hash hex string.

### 6.3. Explorer REST API

#### `scanAccount` — Số dư tài khoản

```
POST https://service.eks.centralityapp.com/cennznet-explorer-api/api/scan/account
Content-Type: application/json

{ "address": "5GjQP..." }
```

**Response:**
```json
{
    "code": 0, "message": "Success",
    "data": {
        "address": "5GjQP...",
        "nonce": 42,
        "balances": [
            { "assetId": 1, "free": 150000, "lock": 0 },
            { "assetId": 2, "free": 80000, "lock": 0 }
        ]
    }
}
```

#### `scanTransfers` — Lịch sử giao dịch

```
POST https://service.eks.centralityapp.com/cennznet-explorer-api/api/scan/transfers
Content-Type: application/json

{ "address": "5GjQP...", "row": 100, "page": 0 }
```

**Response:**
```json
{
    "code": 0, "message": "Success",
    "data": {
        "count": 42,
        "transfers": [
            {
                "from": "5Sender...",
                "to": "5Receiver...",
                "hash": "0xabc...",
                "block_num": 6427083,
                "block_timestamp": 1680000000,
                "amount": 50000,
                "asset_id": 1,
                "success": true,
                "extrinsic_index": "6427083-2"
            }
        ]
    }
}
```

### 6.4. Local Signing API

#### `getPublicAddress` — Derive SS58 address từ seed

```
POST https://fgwallet.srsfc.com/cennz-address
Content-Type: application/json

{ "seed": "0xSeedHex..." }
```

**Response:**
```json
{
    "address": "5GjQP...",
    "publicKey": "0xPublicKeyHex..."
}
```

#### `signMessage` — Ký transaction payload

```
POST https://fgwallet.srsfc.com/cennz-sign
Content-Type: application/json

{ "seed": "0xSeedHex...", "message": "0xPayloadHex..." }
```

**Response:**
```json
{
    "signature": "0xSignatureHex..."
}
```

> **Cảnh báo bảo mật:** Seed hex được gửi qua HTTPS đến external API. Đây là trade-off do chưa có KMP-compatible Sr25519 library. Xem [Gap G2](#gap-2-high-external-signing-service--single-point-of-failure).

---

## 7. Data Models

### CennzTransfer

```kotlin
@Serializable
data class CennzTransfer(
    val from: String = "",
    val to: String = "",
    @SerialName("extrinsic_index") val extrinsicIndex: String = "",
    val hash: String = "",
    @SerialName("block_num") val blockNum: Long = 0,
    @SerialName("block_timestamp") val blockTimestamp: Long = 0,
    val amount: Long = 0,
    @SerialName("asset_id") val assetId: Int = 0,
    val success: Boolean = false
)
```

### CennzScanAsset

```kotlin
@Serializable
data class CennzScanAsset(
    @SerialName("assetId") val assetId: Int = 0,
    val free: Long = 0,
    val lock: Long = 0
)
```

### CennzExtrinsic

```kotlin
@Serializable
data class CennzExtrinsic(
    @SerialName("account_id") val accountId: String = "",
    @SerialName("block_num") val blockNum: Long = 0,
    @SerialName("block_timestamp") val blockTimestamp: Long = 0,
    @SerialName("extrinsic_hash") val extrinsicHash: String = "",
    @SerialName("call_module") val callModule: String = "",
    @SerialName("call_module_function") val callModuleFunction: String = "",
    val fee: Long = 0,
    val success: Boolean = false,
    val nonce: Long = 0
)
```

### CennzPartialFee

```kotlin
@Serializable
data class CennzPartialFee(
    @SerialName("class") val classFee: String = "",
    val partialFee: Int = 0,
    val weight: Int = 0
)
```

### CentralityError (Sealed Class)

| Subclass | Parameters | Khi nào |
|----------|-----------|---------|
| `RpcError` | method, code, message | JSON-RPC trả về error |
| `InvalidSS58Address` | address | Địa chỉ SS58 không hợp lệ |
| `SigningFailed` | reason | Ký extrinsic thất bại |
| `ExtrinsicSubmitFailed` | hash, reason | Submit extrinsic bị reject |
| `InvalidScaleEncoding` | value, reason | SCALE encode/decode lỗi |

---

## 8. Network Configuration

```kotlin
// Endpoints cố định — không chuyển đổi được
object CENNZ_ENDPOINTS {
    const val RPC_SERVER = "https://cennznet.unfrastructure.io"
    const val EXPLORER_SERVER = "https://service.eks.centralityapp.com/cennznet-explorer-api"
    const val LOCAL_API_SERVER = "https://fgwallet.srsfc.com"
}
```

> **Lưu ý:** Khác với BTC/ETH/XRP, Centrality **không hỗ trợ** chuyển đổi mainnet/testnet qua `Config.shared.setNetwork()`. Tất cả endpoints hardcode mainnet.

**Block Explorer:**
- Mainnet: `https://uncoverexplorer.com/extrinsic/{txHash}`

---

## 9. Hướng dẫn tích hợp Android

### 9.1. Dependency

```kotlin
// build.gradle.kts
implementation("io.github.innfocus:crypto-wallet-lib:$version")
```

### 9.2. Khởi tạo và tạo địa chỉ

```kotlin
import com.lybia.cryptowallet.wallets.centrality.CentralityManager
import com.lybia.cryptowallet.services.CentralityApiService

val mnemonic = "your twelve word mnemonic phrase here ..."
val cennzManager = CentralityManager(mnemonic, assetId = 1) // CENNZ

// PHẢI gọi initAddress() trước khi dùng — async, gọi external API
launch {
    cennzManager.initAddress()
    val address = cennzManager.getAddress() // "5GjQP..."
}
```

### 9.3. Lấy số dư và lịch sử

```kotlin
launch {
    val balance = cennzManager.getBalance() // Double, đơn vị CENNZ

    val history = cennzManager.getTransactionHistory()
    // history: List<CennzTransfer>? — đã filter theo assetId và success

    withContext(Dispatchers.Main) {
        tvBalance.text = "$balance CENNZ"
    }
}
```

### 9.4. Lịch sử phân trang

```kotlin
launch {
    var page = 0
    val pageSize = 50

    val transactions = cennzManager.getTransactionHistoryPaginated(
        address = address,
        row = pageSize,
        page = page
    )
    // Trang tiếp: page++
}
```

### 9.5. Gửi CENNZ/CPAY

```kotlin
launch {
    val result = cennzManager.sendCoin(
        fromAddress = myAddress,
        toAddress = "5Recipient...",
        amount = 10.0,           // 10 CENNZ (display units)
        assetId = 1              // 1 = CENNZ, 2 = CPAY
    )

    withContext(Dispatchers.Main) {
        if (result.success) {
            Toast.makeText(ctx, "TX: ${result.txHash}", Toast.LENGTH_LONG).show()
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
    // CommonCoinsManager delegates to CentralityManager
    val balance = coinsManager.commonCoinsManager.getCentralityBalance(address)

    // Send
    val result = coinsManager.commonCoinsManager.sendCentrality(
        fromAddress = address,
        toAddress = "5Recipient...",
        amount = 10.0,
        assetId = 1
    )
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

let cennzManager = CentralityManager(mnemonic: mnemonic, assetId: 1)
```

### 10.3. Async operations (Swift async/await)

```swift
// Khởi tạo address (bắt buộc)
try await cennzManager.initAddress()
let address = cennzManager.getAddress()

// Balance
let balance = try await cennzManager.getBalance(address: nil, coinNetwork: nil)

// Lịch sử
let history = try await cennzManager.getTransactionHistory(address: nil, coinNetwork: nil)

// Gửi CENNZ
let result = try await cennzManager.sendCoin(
    fromAddress: address,
    toAddress: "5Recipient...",
    amount: 10.0,
    assetId: 1
)

if result.success {
    print("TX Hash: \(result.txHash ?? "")")
}
```

> **Lưu ý:** iOS chưa tích hợp thực tế. Ktor Darwin engine sử dụng NSURLSession. Suspend functions → Swift `async throws`.

---

## 11. Đơn vị và chuyển đổi

| Đơn vị | Giá trị | Sử dụng |
|--------|---------|---------|
| **smallest unit** | 1 | `CennzScanAsset.free`, `CennzTransfer.amount` |
| **CENNZ** | 10,000 smallest units | `getBalance()` trả về CENNZ |

```kotlin
// Chuyển đổi
val cennz = smallestUnits / 10_000.0
val smallestUnits = (cennz * 10_000).toLong()
```

### Asset IDs

| Asset ID | Symbol | Tên đầy đủ | Mục đích |
|----------|--------|-----------|---------|
| 1 | CENNZ | CENNZnet | Token chính (governance, staking) |
| 2 | CPAY | CPAY | Token phí giao dịch |

---

## 12. Chạy Tests

```bash
# Compile kiểm tra
./gradlew :crypto-wallet-lib:compileAndroidMain

# Chạy test Centrality
./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*Centrality*"
./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*ScaleCodec*"
./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*ExtrinsicBuilder*"
```

### Test Coverage

| Test File | Nội dung |
|-----------|---------|
| `CentralityManagerTest.kt` | makeEraOption, convertHexToBlockNumber |
| `CentralityAddressTest.kt` | SS58 parse, validate, public key extraction |
| `ExtrinsicBuilderTest.kt` | createPayload, toU8a, toHex, sign |
| `ScaleCodecTest.kt` | compactToU8a boundary values, LE encoding, round-trip |
| `CennzModelsSerializationTest.kt` | JSON serialization round-trip cho tất cả models |
| `CentralityApiServiceTest.kt` | JSON-RPC request format, error handling |

---

## 13. Lưu ý quan trọng

1. **Address init bắt buộc:** Phải gọi `initAddress()` (async) trước khi dùng `getAddress()`. Nếu không → trả về `""`.
2. **External signing:** Seed hex gửi qua HTTPS đến `fgwallet.srsfc.com`. Nếu service down → không gửi TX, không tạo address.
3. **Asset ID:** Luôn chỉ định `assetId` khi thao tác. Default = 1 (CENNZ). CPAY = 2.
4. **Amount units:** `sendCoin()` nhận display units (CENNZ). Internal conversion × BASE_UNIT trước khi encode vào extrinsic.
5. **Era mortality:** TX hết hạn sau ~128 blocks (~10 phút). Nếu network congestion → TX có thể hết hạn.
6. **SS58 format:** Địa chỉ CENNZnet bắt đầu bằng `5` (generic Substrate prefix 42). Regex: `(?:(5|[a-km-zA-HJ-NP-Z1-9]{47,}))`.
7. **No testnet:** Chỉ hỗ trợ mainnet. Không switch được qua `Config.shared`.
8. **Fee mặc định:** 15,287 smallest units (~1.53 CENNZ). Dùng `paymentQueryInfo` cho fee chính xác.
9. **SCALE encoding:** Tất cả integer trong extrinsic dùng SCALE compact encoding (little-endian).

---

## 14. Dependencies

| Thư viện | Version | Mục đích |
|----------|---------|---------|
| `bitcoin-kmp` | 0.30.0 | Base58 encoding (SS58 address) |
| `ktor-client-core` | 3.4.2 | HTTP client (JSON-RPC + REST) |
| `kotlinx-serialization-json` | 1.10.0 | JSON serialization |
| `kotlinx-coroutines-core` | 1.10.2 | Async/await |
| `bignum` (ionspin) | 0.3.10 | BigInteger cho SCALE codec |
| `kermit` (touchlab) | 2.0.5 | Logging đa nền tảng |

---

## 15. Tài liệu tham khảo

| Tài liệu | Link | Nội dung |
|-----------|------|---------|
| CENNZnet Wiki | https://wiki.cennz.net/ | Tổng quan blockchain |
| Substrate Docs | https://docs.substrate.io/ | Framework documentation |
| SS58 Address Format | https://docs.substrate.io/reference/address-formats/ | SS58 encoding |
| SCALE Codec | https://docs.substrate.io/reference/scale-codec/ | Binary codec spec |
| Transaction Format | https://docs.substrate.io/reference/transaction-format/ | Extrinsic v4 format |
| Polkadot Cryptography | https://wiki.polkadot.network/docs/learn-cryptography | Sr25519 spec |
| SLIP-44 Coin Types | https://github.com/satoshilabs/slips/blob/master/slip-0044.md | CENNZnet = 392 |
| BIP-44 Standard | https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki | HD derivation path |
| UNcover Explorer | https://uncoverexplorer.com/ | CENNZnet block explorer |
| Schnorrkel/Ristretto | https://github.com/nickvdsp/schnorrkel | Sr25519 reference impl |

---

## 16. Audit & Known Issues

> **Ngày audit:** 2026-04-06  
> **So sánh với:** Source cũ tại `/Users/thanhphat/GitHub/demo/cryptowallet` (Android-only, Retrofit + callback)  
> **Kết luận tổng quát:** Migration hoàn chỉnh 17/17 tasks. KMP code **cải thiện đáng kể** so với code cũ (sendCoin/getTransactions trước đây chưa wire up trong CoinsManager). Tuy nhiên phát hiện **1 bug** và **1 rủi ro bảo mật**.

### Tổng hợp trạng thái

| # | Vấn đề | Mức độ | File | Trạng thái |
|---|--------|--------|------|-----------|
| BUG-1 | sendCoin amount thiếu nhân BASE_UNIT | 🔴 HIGH | `CentralityManager.kt:117` | ⚠️ Cần fix |
| RISK-1 | External signing service dependency | 🔴 HIGH | `CentralityApiService.kt` | ⚠️ Cần giải quyết |
| GAP-1 | Không hỗ trợ testnet | 🟡 MEDIUM | `CentralityApiService.kt` | 📋 Future |
| GAP-2 | SS58 encode round-trip test thiếu | 🟢 LOW | `CentralityAddressTest.kt` | 📋 Future |
| GAP-3 | Fee estimation dùng hardcode | 🟢 LOW | `CommonCoinsManager.kt` | 📋 Future |

### Chi tiết từng vấn đề

#### BUG-1: sendCoin amount thiếu nhân BASE_UNIT (HIGH)

**Mô tả:** `CentralityManager.sendCoin()` truyền `amount.toLong()` trực tiếp vào `ExtrinsicBuilder.paramsMethod()`. Amount ở đơn vị display (CENNZ), nhưng extrinsic cần đơn vị smallest unit (× 10,000).

**File:** `CentralityManager.kt:117`
```kotlin
// HIỆN TẠI — SAI
.paramsMethod(toAddress, amount.toLong(), assetId)

// Nếu amount = 1.5 CENNZ → toLong() = 1 → gửi 1 smallest unit thay vì 15000
```

**Code cũ** — `CentralityNetwork.kt` nhân `amount * BASE_UNIT`:
```kotlin
val amountInUnits = (amount * BASE_UNIT).toLong()  // 1.5 CENNZ → 15000
```

**Ảnh hưởng:** Gửi sai số tiền — user gửi 10 CENNZ nhưng thực tế gửi 10 smallest units (= 0.001 CENNZ).

**Cách fix:**
```kotlin
.paramsMethod(toAddress, (amount * BASE_UNIT).toLong(), assetId)
```

---

#### RISK-1: External signing service — single point of failure (HIGH)

**Mô tả:** Module phụ thuộc `https://fgwallet.srsfc.com` cho 2 operations critical:
- `getPublicAddress(seed)` → tạo address
- `signMessage(seed, payload)` → ký transaction

**Rủi ro:**
1. **Availability:** Nếu service down → không tạo address, không gửi TX
2. **Security:** Seed hex (master key) được gửi qua network, dù qua HTTPS
3. **Centralization:** Phụ thuộc single external service

**Giải pháp đề xuất:**
1. Investigate KMP-compatible Sr25519 library (e.g., `sr25519-kotlin`, `polkadot-java`)
2. Thêm timeout 10s cho signing API calls
3. Throw `CentralityError.SigningFailed` thay vì generic error khi API fail

---

#### GAP-1: Không hỗ trợ testnet (MEDIUM)

**Mô tả:** `CENNZ_ENDPOINTS` hardcode mainnet URLs. Khác với BTC/ETH/XRP có thể switch qua `Config.shared.setNetwork()`.

**Ảnh hưởng:** Không test được trên testnet/devnet.

**Giải pháp:** Nếu CENNZnet testnet endpoints available, thêm configuration switching.

---

#### GAP-2: SS58 encode round-trip test thiếu (LOW)

**Mô tả:** `CentralityAddress.encodeSS58()` là feature mới (code cũ chỉ có parse). Chưa có test encode → parse round-trip.

**Giải pháp:** Thêm test trong `CentralityAddressTest.kt`:
```kotlin
@Test fun `encode then parse round-trip`() {
    val pubKey = knownPublicKey
    val address = CentralityAddress.encodeSS58(pubKey)
    val parsed = CentralityAddress.parseAddress(address)
    assertContentEquals(pubKey, parsed)
}
```

---

#### GAP-3: Fee estimation dùng hardcode (LOW)

**Mô tả:** `CommonCoinsManager.estimateFee()` trả về `ACTCoin.Centrality.feeDefault()` = 15287.0 (hardcode). `paymentQueryInfo` RPC tồn tại trong `CentralityApiService` nhưng không được dùng.

**Giải pháp:** Optional — gọi `paymentQueryInfo` cho fee thực tế, fallback về default khi fail.

---

### So sánh KMP vs Code cũ

| Thành phần | Code cũ (Android) | KMP (commonMain) | Nhận xét |
|---|---|---|---|
| Async pattern | Deeply-nested callbacks (8+ interfaces) | Coroutines (suspend) | ✅ KMP tốt hơn |
| Signing algorithm | Sr25519 via external API | Sr25519 via external API | ⚠️ Giống nhau — vẫn phụ thuộc external |
| Address format | SS58 parse only | SS58 parse + encode | ✅ KMP thêm encode |
| SCALE codec | U8a (encode only) | ScaleCodec (encode + decode) | ✅ KMP thêm decode |
| Amount handling | × BASE_UNIT trước encode | `.toLong()` trực tiếp | 🔧 Bug — KMP thiếu convert |
| sendCoin wiring | ❌ "Not supported" trong CoinsManager | ✅ Full flow hoạt động | ✅ KMP cải thiện lớn |
| getTransactions wiring | ❌ "Not supported" trong CoinsManager | ✅ Hoạt động + phân trang | ✅ KMP cải thiện lớn |
| Error handling | String errors | Sealed class hierarchy | ✅ KMP tốt hơn |
| Fee estimation | `paymentQueryInfo` (dynamic) | Hardcode 15287.0 | 🔧 Code cũ tốt hơn |
| JSON parsing | Gson + java.io.Serializable | kotlinx.serialization | ✅ KMP cross-platform |
| HTTP client | 3 Retrofit services | 1 Ktor service | ✅ KMP gọn hơn |
| Cross-platform | ❌ Android-only | ✅ commonMain | ✅ KMP tốt hơn |
| Testnet | ❌ Mainnet only | ❌ Mainnet only | ⚠️ Giống nhau |

---

## 17. Tiêu chuẩn cần bổ sung (Roadmap)

### Priority 1 — Cần cho production

| Tiêu chuẩn | Mô tả | File cần sửa | Tham khảo |
|---|---|---|---|
| **Amount × BASE_UNIT** | Fix `sendCoin()` nhân amount × 10000 trước encode | `CentralityManager.kt:117` | Code cũ `CentralityNetwork.kt` |
| **Local Sr25519 signing** | Thay thế external API bằng KMP Sr25519 library | `CentralityApiService.kt`, `CentralityManager.kt` | https://github.com/nickvdsp/schnorrkel |
| **Signing API timeout** | Thêm timeout 10s cho external signing calls | `CentralityApiService.kt` | — |

### Priority 2 — Nên có

| Tiêu chuẩn | Mô tả | Tham khảo |
|---|---|---|
| **Dynamic fee estimation** | Dùng `paymentQueryInfo` thay vì hardcode, fallback khi fail | Code cũ `CentralityNetwork.calculateEstimateFee()` |
| **Testnet support** | Configurable endpoints qua Config.shared | — |
| **SS58 encode round-trip test** | Test encode → parse → verify pubkey match | — |
| **Balance pre-check trước send** | Validate đủ balance trước khi build extrinsic | Pattern từ `RippleManager.sendXrp()` |

### Priority 3 — Mở rộng tương lai

| Tiêu chuẩn | Mô tả | Tham khảo |
|---|---|---|
| **Staking (CENNZ)** | Stake CENNZ tokens, claim rewards | CENNZnet staking pallet |
| **Governance** | Vote on proposals | CENNZnet governance pallet |
| **NFT support** | CENNZnet NFT module | CENNZnet NFT pallet |
| **Multi-address** | Derive multiple addresses cho privacy | BIP-44 account index |
| **Batch transactions** | Gửi nhiều TX trong 1 extrinsic (utility.batch) | Substrate utility pallet |
