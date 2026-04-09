# Cardano Byron Address Management — Technical Specification

**Version:** 1.4  
**Status:** Address generation ✅ Fixed | Transaction signing ✅ Fixed | IOHK protocol gaps ✅ Fixed | Build verified  
**Scope:** `commonMain` — Icarus-style Byron address generation + transaction signing

---

## 1. Overview

Byron là era đầu tiên của Cardano (ra mắt 2017). Địa chỉ Byron vẫn được dùng trong các ví cũ (Daedalus, Yoroi legacy) và cần hỗ trợ để import/migrate. Địa chỉ Byron được encode bằng **Base58**, bắt đầu bằng `Ae2`.

Có hai loại Byron wallet:
- **Daedalus (Byron HD)** — derivation scheme V1, không còn được khuyến nghị.
- **Icarus** — derivation scheme V2, được dùng bởi Yoroi và phần lớn ví hiện đại. **Đây là loại chúng ta implement.**

---

## 2. Thuật toán tạo địa chỉ Byron (Icarus)

### 2.1 Tổng quan flow

```
Mnemonic (BIP-39)
    ↓  [BIP-39 entropy extraction — KHÔNG dùng BIP-39 seed!]
Entropy (bytes)
    ↓  PBKDF2-HMAC-SHA512(password="", salt=entropy, iter=4096, dkLen=96)
96-byte raw
    ↓  Clamp
Master Extended Key (kL: 64 bytes) + Master Chain Code (32 bytes)
    ↓  Icarus V2 child derivation: m/44'/1815'/0'/0/index
Child Extended Key (kL: 64 bytes) + Child Chain Code (32 bytes)
    ↓  Ed25519 point multiplication: A = kL[0..31] * B  (pre-clamped scalar, NO SHA-512!)
Public Key (32 bytes) + Chain Code (32 bytes)
    ↓  createByronAddress(pubKey, chainCode)
Byron Address (Base58)
```

### 2.2 Master Key Derivation

```
input  : entropy  (byte[])   — từ BIP-39 decode, KHÔNG phải BIP-39 seed
output : extKey   (byte[64]) — extended private key (kL || kR)
         chainCode (byte[32]) — chain code

raw = PBKDF2-HMAC-SHA512(
    password  = "",           // empty password
    salt      = entropy,
    iterations = 4096,
    dkLen     = 96            // 64 bytes key + 32 bytes chain code
)

// Clamp (Icarus cofactor clearing + bit manipulation)
raw[0]  &= 0xF8   // clear bits 0, 1, 2 (cofactor of Ed25519 = 8)
raw[31] &= 0x1F   // clear bits 5, 6, 7
raw[31] |= 0x40   // set bit 6

extKey    = raw[0..63]   // 64 bytes: kL (32 bytes) || kR (32 bytes)
chainCode = raw[64..95]  // 32 bytes
```

### 2.3 Child Key Derivation (Icarus V2 / ed25519-bip32)

```
input  : extKey    (byte[64])  — parent extended key
         chainCode (byte[32])  — parent chain code
         index     (Int)       — derivation index
         hardened  (Boolean)

// V2: index serialized as 4-byte LITTLE-ENDIAN
indexLE = [index & 0xFF, (index>>8)&0xFF, (index>>16)&0xFF, (index>>24)&0xFF]

if (hardened):
    tagZ  = 0x00, tagCC = 0x01
    keyMaterial = extKey  // 64-byte extended key
else:
    tagZ  = 0x02, tagCC = 0x03
    keyMaterial = publicKeyFromExtended(extKey)  // 32-byte public key

Z       = HMAC-SHA512(chainCode, [tagZ  || keyMaterial || indexLE])
newCC_raw = HMAC-SHA512(chainCode, [tagCC || keyMaterial || indexLE])
newCC   = newCC_raw[32..63]

// New left scalar: zL[0..27] * 8 + parent kL  (little-endian arithmetic)
zL  = Z[0..27]     // only 28 bytes used (maintains group order property)
zR  = Z[32..63]
kL  = extKey[0..31]
kR  = extKey[32..63]

newKL = (zL * 8) + kL    // 256-bit LE addition, no modular reduction
newKR = zR + kR           // 256-bit LE addition

newExtKey = newKL || newKR
```

**⚠️ Khác biệt quan trọng so với SLIP-0010:**
- Icarus **cộng dồn** `zL*8` vào `kL` cũ → kL thay đổi dần qua các level
- SLIP-0010 **thay thế** kL hoàn toàn → không tương thích
- Icarus hỗ trợ **soft (non-hardened)** derivation cho role và index
- SLIP-0010 chỉ hỗ trợ hardened

### 2.4 Derivation Path

```
Byron Icarus: m / 44' / 1815' / 0' / 0 / index
                  ───   ────   ─   ─   ─────
                  BIP44  ADA  acct  0  address
                        coin type  role (external)

44'   = hardened — BIP-44 purpose
1815' = hardened — Cardano coin type
0'    = hardened — account index
0     = NON-hardened — external chain (role)
index = NON-hardened — address index
```

**⚠️ Lỗi phổ biến:** Dùng `hardenedIndex(0)` cho role và index sẽ tạo ra địa chỉ SAI.

### 2.5 Public Key từ Extended Key (Icarus-specific)

```
input  : extKey (byte[64])
scalar = extKey[0..31]  // kL — PRE-CLAMPED, không hash thêm SHA-512

// Standard Ed25519 point multiplication
A = scalar * BasePoint  (Ed25519 twisted Edwards curve)

// Encode compressed: little-endian y-coordinate, MSB = sign bit of x
publicKey = encodePoint(A)  // 32 bytes
```

**⚠️ Nguy hiểm:** `PrivateKeyEd25519(seed)` từ ton-kotlin sẽ SHA-512 hash `seed` trước khi multiply. Với Icarus keys, `kL` ĐÃ là scalar rồi — không được hash lại! Phải dùng `Ed25519Icarus.publicKeyFromScalar(kL)`.

### 2.6 Byron Transaction Signing (Icarus Ed25519)

Byron transactions spending UTXOs từ Byron addresses phải dùng **Bootstrap witnesses** (key 2 trong CBOR witness set), không phải VKey witnesses (key 0 dành cho Shelley).

#### 2.6.1 Signing algorithm (ed25519-bip32)

```
sign(extKey64, message):
    kL = extKey64[0..31]   // pre-clamped scalar (KHÔNG hash SHA-512)
    kR = extKey64[32..63]  // nonce generation material (prefix)

    scalar = fromLE(kL)    // interpret as little-endian integer

    // 1. Nonce r (deterministic, from kR)
    r_bytes = SHA512(kR || message)  // 64 bytes
    r_int   = fromLE(r_bytes) mod L  // L = Ed25519 group order

    // 2. Commitment R
    R     = r_int * BasePoint        // EC scalar multiplication
    R_enc = encodePoint(R)           // 32 bytes compressed

    // 3. Challenge k
    A     = publicKeyFromScalar(kL)  // 32 bytes — Icarus public key
    k     = SHA512(R_enc || A || message)
    k_int = fromLE(k) mod L

    // 4. Signature S
    S     = (r_int + k_int * scalar) mod L
    S_enc = toLE32(S)               // 32 bytes

    return R_enc || S_enc           // 64 bytes total
```

**Khác với RFC 8032 ở chỗ:**
- RFC 8032: `scalar = clamp(SHA512(seed)[0..31])` — hash seed rồi lấy nửa đầu
- Icarus: `scalar = kL` trực tiếp — kL ĐÃ là scalar pre-clamped, không hash thêm
- Prefix cho nonce: RFC 8032 dùng `SHA512(seed)[32..63]`, Icarus dùng `kR` (extKey[32..63])

#### 2.6.2 Bootstrap Witness structure

```
BootstrapWitness = CBOR array [
    pubKey32    : bytes  // Ed25519 public key (Icarus, 32 bytes)
    signature64 : bytes  // Icarus Ed25519 signature (64 bytes)
    chainCode32 : bytes  // Icarus chain code (32 bytes) — from derivation
    attributes  : bytes  // CBOR empty map = 0xa0
]
```

Witness set CBOR map:
```
{
    2: [BootstrapWitness, ...]   // key 2 = Bootstrap witnesses (Byron)
    // key 0 = VKey witnesses (Shelley) — KHÔNG dùng cho Byron
}
```

#### 2.6.3 Full Byron transaction flow

```
1. Derive Byron key: CardanoManager.deriveByronKey(index) — cached internally
   (Internally: master key (L1) → account key m/44'/1815'/0' (L2) → soft derives → pubkey)
   Trả về: (pubKey32, chainCode32, extKey64)
2. Build tx body (CBOR map: inputs, outputs, fee, ttl)
4. TxID = Blake2b-256(CBOR_encode(tx_body_map))  → 32 bytes (raw, not hex)
5. Sign:   sig64 = Ed25519Icarus.sign(extKey64, txID_bytes)
6. Build witness: BootstrapWitness(pubKey32, sig64, chainCode32, 0xa0)
7. Build WitnessSet: {2: [BootstrapWitness]}
8. Signed tx: CBOR array [tx_body, witness_set, null]
9. Submit: POST txBytes to Blockfrost /tx/submit (Content-Type: application/cbor)
```

---

### 2.7 Byron Address Encoding

```
xpub = publicKey (32 bytes) || chainCode (32 bytes)  // 64 bytes total

// Step 1: Address root hash
rootInput = CBOR([
    0,              // addrType = ATPubKey
    [0, xpub],      // [addrType, xpub bytes]
    {}              // empty attributes
])
sha3Hash    = SHA3-256(CBOR.encode(rootInput))
addressRoot = Blake2b-224(sha3Hash)   // 28 bytes

// Step 2: Address payload
payload = CBOR([
    addressRoot,   // 28 bytes
    {},            // empty attributes
    0              // addrType = ATPubKey
])
payloadBytes = CBOR.encode(payload)
crc32        = CRC32(payloadBytes)

// Step 3: Final encoding
byronAddr = CBOR([tag24(payloadBytes), crc32])
return Base58.encode(CBOR.encode(byronAddr))
```

---

## 3. Implementation Notes

### 3.1 `MnemonicCode.toEntropy` không tồn tại trong bitcoin-kmp

`fr.acinq.bitcoin.MnemonicCode` chỉ expose `toSeed()` và `toMnemonics()`. Không có `toEntropy()`.

→ `IcarusKeyDerivation.mnemonicToEntropy()` implement lại BIP-39 reverse và **hỗ trợ cả 10 ngôn ngữ BIP-39** (English, Japanese, Chinese Simplified/Traditional, French, Italian, Spanish, Korean, Czech, Portuguese) thông qua registry `com.lybia.cryptowallet.wallets.bip39.Bip39Language`.

**Auto-detection:** `Bip39Language.detect(words)` dùng pre-filter Unicode script (Hiragana → Japanese, Hangul → Korean, CJK → Chinese, Latin → English/French/...) để chỉ load wordlist cùng script với mnemonic input. Cold-start cho mnemonic English chỉ load 1 wordlist (~10ms), warm <1µs.

**Override khi caller đã biết ngôn ngữ:**
```kotlin
IcarusKeyDerivation.masterKeyFromMnemonic(words, Bip39Language.JAPANESE)
```

**Giới hạn ngôn ngữ ở app startup** (nếu muốn lock-down vì bảo mật/hiệu năng):
```kotlin
Bip39Language.setEnabledLanguages(listOf(Bip39Language.ENGLISH, Bip39Language.JAPANESE))
```
Setting này chỉ ảnh hưởng auto-detect; gọi explicit với enum value vẫn dùng được mọi ngôn ngữ.

### 3.2 Ed25519 point multiplication performance

`Ed25519Icarus` dùng `ionspin/bignum` BigInteger — đủ chính xác nhưng chậm hơn native implementation. Mỗi call `publicKeyFromScalar` mất ~100-500ms tùy device. Không gọi trong hot path / UI thread.

---

## 4. File Structure

```
commonMain/kotlin/com/lybia/cryptowallet/wallets/cardano/
├── CardanoManager.kt          — Entry point; getByronAddress(), buildAndSignByronTransaction()
├── IcarusKeyDerivation.kt     — Master key + child key derivation (Icarus V2)
├── Ed25519Icarus.kt           — Icarus Ed25519: publicKeyFromScalar() + sign(extKey64, msg)
├── Ed25519.kt                 — Standard Ed25519 (for Shelley/TON — uses SHA-512)
└── CardanoAddress.kt          — Address encoding/decoding (Byron + Shelley)

commonTest/kotlin/com/lybia/cryptowallet/cardano/
└── CardanoByronKeyTest.kt     — 45 unit tests (address derivation + Icarus signing)
```

---

## 5. Bug Fixes & Post-mortem

### Bug #1 — Sai thuật toán master key derivation

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt:37,44` | Dùng `MnemonicCode.toSeed()` (BIP-39 seed, 64 bytes) → SLIP-0010 master key |
| **Đúng** | `IcarusKeyDerivation.kt` | Dùng `MnemonicCode.toEntropy()` → PBKDF2(entropy, 4096, 96) → clamped Icarus key |

**Nguyên nhân gốc:** Developer hiểu nhầm "seed" trong tài liệu Cardano là BIP-39 seed. Thực ra Icarus dùng entropy (raw bytes) làm PBKDF2 salt, không dùng BIP-39 mnemonic-to-seed algorithm.

**Dấu hiệu nhận biết lỗi:** Địa chỉ generate ra không khớp với Daedalus/Yoroi khi import cùng mnemonic.

### Bug #2 — Sai thuật toán child key derivation

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt:43-60` (`slip10DeriveEd25519`) | SLIP-0010: `kL = HMAC(kR, [0x00\|\|kL\|\|idx])` — thay thế kL |
| **Đúng** | `IcarusKeyDerivation.deriveChildKey()` | Icarus V2: `newKL = zL*8 + kL` — cộng dồn |

**Nguyên nhân gốc:** SLIP-0010 và Icarus đều dùng HMAC-SHA512 nhưng hoàn toàn khác nhau. SLIP-0010 là chuẩn cho Solana/Polkadot. Cardano dùng ed25519-bip32 (Icarus/Byron).

### Bug #3 — Sai derivation path (tất cả index bị hardened)

| | Derivation path |
|---|---|
| **Sai** | `m/44'/1815'/0'/0'/index'` (role=0' và index' đều hardened) |
| **Đúng** | `m/44'/1815'/0'/0/index` (role=0 và index là soft/non-hardened) |

**Nguyên nhân gốc:** SLIP-0010 chỉ hỗ trợ hardened, nên developer hardcode tất cả. Icarus hỗ trợ soft derivation và bắt buộc dùng soft cho role + index để xpub có thể derive public child keys.

### Bug #4 — Sai cách tính public key

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt:67-75` | `Ed25519.publicKey(privateKey)` — nhận 32-byte seed, internally SHA-512 hash |
| **Đúng** | `IcarusKeyDerivation.publicKeyFromExtended()` | Dùng `Ed25519Icarus.publicKeyFromScalar(kL[0..31])` — dùng kL trực tiếp làm scalar |

**Nguyên nhân gốc:** ton-kotlin `PrivateKeyEd25519(seed)` implement RFC 8032: `scalar = SHA512(seed)[0..31] clamped`. Với Icarus, `kL` ĐÃ là scalar rồi (đã clamp từ bước master key). Nếu hash lại → sai hoàn toàn.

---

## 5b. Bug Fixes — Transaction Signing (✅ Đã fix)

> Phần này document 4 bugs trong signing Byron transactions, phát hiện qua so sánh với source cũ tại `/Users/thanhphat/GitHub/demo/cryptowallet/crypto-wallet-lib/src/androidMain/kotlin/com/lybia/cryptowallet/coinkits/cardano/`.

### Bug #5 — Sai loại witness: VKey thay vì Bootstrap ✅ Đã fix

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt:428-433` | `CardanoWitnessBuilder().addVKeyWitness(pubKey, sig)` → key 0 trong CBOR |
| **Đúng** | — | `CardanoWitnessBuilder().addBootstrapWitness(pubKey, sig, chainCode, 0xa0)` → key 2 |

**Nguyên nhân gốc:** Developer dùng Shelley witness (VKeyWitness, key 0) cho Byron UTXOs. Cardano node sẽ **từ chối** transaction vì không thể verify chữ ký Byron mà không có chainCode và attributes.

**So sánh với source cũ:**
```kotlin
// Source cũ (đúng) — TransactionWitnessSet.kt
class TransactionWitnessSet(val bootstraps: Array<TxWitness>) {
    fun serializer() = CborBuilder().addMap()
        .putArray(2)   // key 2 = Bootstrap witnesses
        ...
}

// Source mới (sai) — CardanoWitnessBuilder.kt
entries.add(CborValue.CborUInt(0u) to ...)  // key 0 = VKey witnesses (Shelley only)
```

---

### Bug #6 — Sai thuật toán ký: RFC 8032 thay vì Icarus Ed25519 ✅ Đã fix

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt:427` | `ed25519Sign(paymentPriv, txHash)` → ton-kotlin RFC 8032 |
| **Đúng** | — | `Ed25519Icarus.sign(extKey64, txHash)` → Icarus ed25519-bip32 |

**Chi tiết khác biệt:**

| Bước | RFC 8032 (sai) | Icarus ed25519-bip32 (đúng) |
|---|---|---|
| Scalar | `SHA512(seed32)[0..31]` clamped | `kL` trực tiếp (đã clamped) |
| Prefix cho nonce | `SHA512(seed32)[32..63]` | `kR = extKey64[32..63]` |
| `r` | `SHA512(prefix \|\| msg) mod L` | `SHA512(kR \|\| msg) mod L` |
| S | `(r + k * scalar) mod L` | `(r + k * scalar) mod L` |

**So sánh với source cũ:**
```kotlin
// Source cũ (đúng) — CarKeyPair.sign() dùng 64-byte extended key
val scalar = BigInteger(1, privBytes.copyOfRange(0, 32).reversedArray())  // kL directly
val prefix = privBytes.copyOfRange(32, 64)  // kR as prefix
// r = SHA512(prefix || message)
```

---

### Bug #7 — Thiếu hàm `Ed25519Icarus.sign()` ✅ Đã fix

| | Trạng thái |
|---|---|
| **Cũ** | `Ed25519Icarus.kt` chỉ có `publicKeyFromScalar()` — không có `sign()` |
| **Đã fix** | `fun sign(extKey64: ByteArray, message: ByteArray): ByteArray` — thêm vào `Ed25519Icarus.kt` |

**Pseudo-code cần implement:**
```kotlin
// Thêm vào Ed25519Icarus.kt
fun sign(extKey64: ByteArray, message: ByteArray): ByteArray {
    require(extKey64.size == 64)
    val kL = extKey64.copyOfRange(0, 32)
    val kR = extKey64.copyOfRange(32, 64)
    val scalar = fromLittleEndian(kL)

    // Nonce r
    val rHash = sha512(kR + message)
    val r = fromLittleEndian(rHash).mod(L)

    // R = r * B
    val (rx, ry) = scalarMultBase(r)
    val rEnc = encodePoint(rx, ry)   // 32 bytes

    // Public key A
    val aEnc = publicKeyFromScalar(kL)  // 32 bytes

    // Challenge k
    val kHash = sha512(rEnc + aEnc + message)
    val k = fromLittleEndian(kHash).mod(L)

    // S = (r + k * scalar) mod L
    val s = (r + k * scalar).mod(L)
    val sEnc = toLittleEndian32(s)   // 32 bytes

    return rEnc + sEnc   // 64 bytes
}
```

Cần thêm:
- `L` (Ed25519 group order): `1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed` (hex)
- SHA-512 utility function (platform-specific actual implementation)

---

### Bug #8 — `buildAndSignTransaction` dùng sai key cho Byron UTXOs ✅ Đã fix

| | Code location | Triệu chứng |
|---|---|---|
| **Sai** | `CardanoManager.kt` (cũ) | `derivePaymentKey(0, 0)` → SLIP-0010 32-byte key |
| **Đã fix** | `CardanoManager.buildAndSignByronTransaction()` | `deriveByronKey(index)` → Icarus (pubKey32, chainCode32, extKey64) |

**Nguyên nhân gốc:** `buildAndSignTransaction()` hardcode dùng Shelley payment key cho mọi loại transaction. Byron UTXOs cần Icarus key (64 bytes) để tạo chữ ký và cung cấp chainCode trong Bootstrap witness.

**Fix đã implement:**
```kotlin
suspend fun buildAndSignByronTransaction(
    toAddress: String, amount: Long, fee: Long, fromIndex: Int = 0, ...
): CardanoSignedTransaction {
    val (pubKey32, chainCode32, extKey64) = deriveByronKey(fromIndex)
    val fromAddress = CardanoAddress.createByronAddress(pubKey32, chainCode32)
    // ... fetch UTXOs for fromAddress, build body ...
    val sig64 = Ed25519Icarus.sign(extKey64, body.getHash())
    val witnessSet = CardanoWitnessBuilder()
        .addBootstrapWitness(pubKey32, sig64, chainCode32, byteArrayOf(0xa0.toByte()))
        .build()
    return CardanoSignedTransaction(body, witnessSet)
}
```

---

## 5c. IOHK Protocol Gap Fixes (✅ Đã fix)

> Phần này document 4 điểm thiếu sót so với tiêu chuẩn kỹ thuật IOHK, phát hiện qua so sánh với source cũ (`Gada.kt`) và protocol params chính thức.

### Fix #9 — Input Deduplication ✅

| | File | Chi tiết |
|---|---|---|
| **Cũ** | `CardanoTransaction.kt:227-230` | `inputs.add(...)` — không kiểm tra trùng lặp |
| **Đã fix** | `CardanoTransaction.kt` | Kiểm tra `!inputs.contains(input)` trước khi add |

**Nguyên nhân:** Nếu cùng một UTXO (txHash + index) được add 2 lần, node sẽ reject transaction vì "duplicate input". Cần dedup tại tầng builder.

```kotlin
// Đã fix trong CardanoTransactionBuilder.addInput()
fun addInput(txHash: ByteArray, index: Int): CardanoTransactionBuilder {
    val input = CardanoTransactionInput(txHash, index)
    if (!inputs.contains(input)) {
        inputs.add(input)
    }
    return this
}
```

> `CardanoTransactionInput.equals()` so sánh `txHash.contentEquals()` + `index` — đúng với protocol (một UTXO = một txHash+index tuple).

---

### Fix #10 — Minimum UTXO Output: 1 ADA ✅

| | File | Chi tiết |
|---|---|---|
| **Cũ** | `CardanoTransaction.kt:236-239` | `addOutput()` cho phép `lovelace < 1_000_000` |
| **Đã fix** | `CardanoTransactionBuilder` | `require(lovelace >= MIN_UTXO_LOVELACE)` trong `addOutput()` và `addMultiAssetOutput()` |

**Protocol param:** `minUTxOValue = 1_000_000 lovelace` (1 ADA). Node từ chối output nhỏ hơn.

```kotlin
companion object {
    /** Minimum lovelace per output (IOHK protocol parameter). */
    const val MIN_UTXO_LOVELACE = 1_000_000L

    /** Maximum serialised transaction size in bytes (IOHK protocol parameter). */
    const val MAX_TX_SIZE_BYTES = 16_384
}
```

**So sánh với source cũ:**
```kotlin
// Source cũ (Gada.kt)
val MIN_AMOUNT_PER_TX = 1000000.0  // 1 ADA minimum per output
```

---

### Fix #11 — Change < 1 ADA → Absorb vào Fee ✅

| | File | Chi tiết |
|---|---|---|
| **Cũ** | `CardanoManager.kt:413-417` | `if (change > 0) builder.addOutput(fromAddress, change)` — tạo output nhỏ hơn 1 ADA |
| **Đã fix** | `buildAndSignTransaction()` và `buildAndSignByronTransaction()` | Nếu `0 < change < MIN_UTXO`, gộp vào fee thay vì tạo output |

**Lý do:** Nếu change < 1 ADA mà vẫn tạo change output → node reject (vi phạm Min UTXO). Nếu bỏ change output mà không điều chỉnh fee → `inputs - outputs ≠ declared_fee` → node reject. Giải pháp đúng: tăng `declared_fee` lên bằng `fee + change`.

```kotlin
// Đã fix trong buildAndSignTransaction() và buildAndSignByronTransaction()
val rawChange = collected - amount - fee - serviceFeeTotal
val effectiveFee: Long
val actualChange: Long
if (rawChange > 0L && rawChange < CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
    effectiveFee = fee + rawChange  // absorb dust into fee
    actualChange = 0L
} else {
    effectiveFee = fee
    actualChange = rawChange
}
if (actualChange > 0L) {
    builder.addOutput(fromAddressBytes, actualChange)
}
builder.setFee(effectiveFee)
```

**So sánh với source cũ:**
```kotlin
// Source cũ (Gada.kt)
val changeAmount = input - fee - amount
if (changeAmount < MIN_AMOUNT_PER_TX) {
    // absorb into fee — no change output
} else {
    // create change output
}
```

---

### Fix #12 — Transaction Size Limit: 16,384 bytes ✅

| | File | Chi tiết |
|---|---|---|
| **Cũ** | `CardanoManager.kt` | Không có kiểm tra kích thước transaction |
| **Đã fix** | `buildAndSignTransaction()` và `buildAndSignByronTransaction()` | Check `signedTx.serialize().size <= MAX_TX_SIZE_BYTES` sau khi tạo `CardanoSignedTransaction` |

**Protocol param:** `maxTxSize = 16_384 bytes`. Node từ chối transaction lớn hơn.

```kotlin
val signedTx = CardanoSignedTransaction(body, witnessSet)
val txBytes = signedTx.serialize()
if (txBytes.size > CardanoTransactionBuilder.MAX_TX_SIZE_BYTES) {
    throw CardanoError.ApiError(
        statusCode = null,
        message = "Transaction too large: ${txBytes.size} bytes (max ${CardanoTransactionBuilder.MAX_TX_SIZE_BYTES})"
    )
}
return signedTx
```

**Khi nào vi phạm:** Khi tx có quá nhiều inputs (> ~50-100 UTXOs nhỏ). Caller cần chọn UTXO lớn hơn hoặc consolidate trước.

---

## 6. Quy tắc phòng tránh lỗi cho team

### ✅ DO

- Dùng `IcarusKeyDerivation` cho tất cả Byron address generation
- Kiểm tra địa chỉ generate ra bằng cách import mnemonic vào Yoroi browser extension
- Dùng `Ed25519Icarus.publicKeyFromScalar()` cho Icarus public keys
- Dùng `Ed25519Icarus.sign(extKey64, msg)` (khi có) cho Byron transaction signing
- Dùng `addBootstrapWitness(pubKey, sig, chainCode, 0xa0)` cho Byron witness — key 2 trong CBOR
- Dùng `Ed25519.publicKey()` (ton-kotlin wrapper) cho Shelley và TON keys

### ❌ DON'T

- **Đừng** dùng `slip10DeriveEd25519` cho Cardano Byron
- **Đừng** dùng `MnemonicCode.toSeed()` làm input cho Icarus master key
- **Đừng** hardened hóa role (`0`) và address index trong Byron path
- **Đừng** dùng `PrivateKeyEd25519(seed)` (ton-kotlin) trực tiếp với Icarus extended key
- **Đừng** nhầm "Icarus" với "Daedalus Byron HD" (V1 scheme — khác hoàn toàn)
- **Đừng** dùng `addVKeyWitness` (Shelley key 0) để ký Byron UTXOs — node sẽ reject
- **Đừng** dùng 32-byte key cho Byron signing — cần 64-byte extKey (kL||kR) vì kR là prefix cho nonce

### 🔑 Cheat Sheet: Khi nào dùng thuật toán nào?

| Chain/Era | Key derivation | Ed25519 public key | Signing | Witness type |
|---|---|---|---|---|
| Cardano Byron (Icarus) | `IcarusKeyDerivation` | `Ed25519Icarus.publicKeyFromScalar(kL)` | `Ed25519Icarus.sign(extKey64, msg)` | Bootstrap (key 2) — cần chainCode |
| Cardano Shelley (CIP-1852) | Icarus (sau khi fix) | `IcarusKeyDerivation.publicKeyFromExtended(ext)` | `Ed25519.sign(kL, msg)` | VKey (key 0) |
| TON | SLIP-0010 (ton-kotlin) | `Ed25519.publicKey(seed)` (ton-kotlin) | ton-kotlin | — |

---

## 7. Test Vectors & Lệnh Chạy Test

### 7.1 Known vectors (verified với Yoroi)

| Mnemonic | Index | Loại | Expected address | Status |
|---|---|---|---|---|
| `left arena awkward spin damp pipe liar ribbon few husband execute whisper` | 0 | Byron mainnet | `Ae2tdPwUPEZ6tWFJJ7kmDCN2GyGnJgH4nCARQyrkgWNMzBHnUqEJmX5V15F` | ✅ PASS |
| `left arena awkward spin damp pipe liar ribbon few husband execute whisper` | 0 | Shelley mainnet | `addr1qxhj6eqf65yt283f4vwuasfjag7v485g0szrce84hhldd8jrmw23wageh85y8qgjrgxd70k8s44j2wuex329wk5xqfpqu3zkwl` | ⏭ SKIP (Shelley chưa fix) |

### 7.2 Lệnh chạy test

```bash
# Chạy toàn bộ CardanoByronKeyTest (45 tests: address derivation + Icarus signing)
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.CardanoByronKeyTest"

# Chạy known-vector tests (Byron pass, Shelley skipped)
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.CardanoByronKeyTest.knownVector_byronAddress_index0" \
  --tests "com.lybia.cryptowallet.cardano.CardanoManagerTest.knownVector_byronAddress_index0" \
  --tests "com.lybia.cryptowallet.cardano.CardanoManagerTest.knownVector_shelleyAddress_account0_index0"

# Chạy toàn bộ test Cardano
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.*"
```

> **Lưu ý:** Dùng `cleanJvmTest` trước `jvmTest` để tránh Gradle cache kết quả cũ (`UP-TO-DATE` / `FROM-CACHE`).

### 7.3 Kết quả test hiện tại

```
CardanoByronKeyTest[jvm]  — 45 tests, 0 failures ✅
  ✅ multiply8LE_singleBit
  ✅ multiply8LE_carryPropagation
  ✅ multiply8LE_maxCarryToOverflowByte
  ✅ multiply8LE_requiresExactly28Bytes
  ✅ multiply8LE_allZeros
  ✅ addScalarsLE_simple
  ✅ addScalarsLE_carryPropagation
  ✅ addScalarsLE_zeros
  ✅ masterKeyFromMnemonic_correctSize
  ✅ masterKeyFromMnemonic_clamping
  ✅ masterKeyFromMnemonic_deterministic
  ✅ masterKeyFromMnemonic_12words
  ✅ masterKeyFromMnemonic_differentMnemonicsProduceDifferentKeys
  ✅ deriveChildKey_hardened_returnsCorrectSize
  ✅ deriveChildKey_soft_returnsCorrectSize
  ✅ deriveChildKey_differentIndicesProduceDifferentKeys
  ✅ deriveChildKey_hardened_vs_soft_differ
  ✅ deriveChildKey_deterministic
  ✅ ed25519Icarus_publicKeySize
  ✅ ed25519Icarus_deterministicForSameScalar
  ✅ ed25519Icarus.differentScalarsDifferentKeys
  ✅ ed25519Icarus_rejectsNon32ByteInput
  ✅ publicKeyFromExtended_uses_kL_only
  ✅ deriveByronPath_returnsCorrectSizes
  ✅ deriveByronPath_deterministic
  ✅ deriveByronPath_differentIndexProducesDifferentKey
  ✅ byronAddress_startsWithAe2
  ✅ byronAddress_validFormat
  ✅ byronAddress_base58OnlyChars
  ✅ byronAddress_reasonableLength
  ✅ byronAddress_multipleIndices_allValid
  ✅ byronAddress_allIndicesUnique
  ✅ byronAddress_12wordMnemonic_valid
  ✅ regression_bug1_masterKeyUsesEntropyNotSeed
  ✅ regression_bug3_byronPathUsesSoftDerivationForRoleAndIndex
  ✅ regression_bug4_icarusPublicKeyDiffersFromStandardEd25519
  ✅ icarusSign_returnsCorrectSize             (signing tests)
  ✅ icarusSign_isDeterministic
  ✅ icarusSign_differentMessages_differentSignatures
  ✅ icarusSign_differentKeys_differentSignatures
  ✅ icarusSign_rejectsNon64ByteKey
  ✅ icarusSign_sComponentLessThanGroupOrder
  ✅ icarusSign_nonZeroComponents
  ✅ icarusSign_bootstrapWitnessTupleSizes
  ✅ knownVector_byronAddress_index0

CardanoManagerTest[jvm]
  ✅ knownVector_byronAddress_index0
  ⏭ knownVector_shelleyAddress_account0_index0  (SKIPPED — Shelley chưa fix)
```

### 7.4 Internal math vectors

```kotlin
// multiply8LE([0x01, ...27 zeros...]) = [0x08, ...zeros...]
// multiply8LE([0xFF, ...27 zeros...]) = [0xF8, 0x07, ...zeros...]  (carry)
// addScalarsLE([0xFF, ...], [0x01, ...]) = [0x00, 0x01, ...]      (carry)
```

---

## 8. Phân tích iOS source — So sánh 3 phiên bản

> Source iOS tại `/Users/thanhphat/GitHub/fg-wallet/ios/FGWallet/CoinKit/Cardano/`  
> iOS đã **revert** về Ed25519 sau khi migration sang Sr25519 bị broken.

### 8.1 Kiến trúc signing của iOS — Điểm then chốt

iOS có **hai code path hoàn toàn khác nhau** cho Shelley và Byron:

#### Shelley → GadaShelley.swift — Server-side signing ✅

```swift
// GadaShelley.transferAda() — gửi privateKey lên server, server ký và broadcast
let params = [
    "fromAddress": fromAddress,
    "toAddress": toAddress,
    "privateKey": userDefaults.string(forKey: "AdaWIFS") ?? "",  // private key gửi lên server
    ...
]
AF.request(ADAAPI.transfer, ...)  // fgwallet.srsfc.com ký và broadcast
```

**CarKeyPair.sign() KHÔNG được gọi cho Shelley.** Signing hoàn toàn ở server side. Đây là lý do iOS team nói "hoạt động tốt" — họ đang test Shelley, không phải Byron.

#### Byron → Gada.swift — On-device signing

```swift
// Gada.createTxAux()
prvKeys.append(keys!.1.raw.bytes)       // 64-byte Icarus extKey (kL||kR)
chainCodes.append(keys!.1.chainCode.bytes)
// ...
let inWitnesses = TxWitnessBuilder.builder(txId: txId, prvKeys: prvKeys, chainCodes: chainCodes)
```

```swift
// TxWitnessBuilder.builder()
let pub = CarPublicKey.derive(fromSecret: prvKeys[i])  // ed25519_extract_public_key → kL*B
let pairKey = try CarKeyPair(publicKey: pub.bytes, privateKey: prvKeys[i])
let signature = pairKey.sign(Data(hex: txId).bytes)   // ← ĐÂY là chỗ CryptoKit được gọi
let witness = TxWitness(extendedPublicKey: pub.bytes, signature: signature, chainCode: chainCodes[i], attributes: Data(hex: "0xa0").bytes)
```

**CarKeyPair.sign() ĐƯỢC gọi cho Byron.** Và đây là nơi có bug CryptoKit.

---

### 8.2 Phân tích bug signing trong Byron path

**`CarPublicKey.derive(fromSecret:)` — Đúng ✅**
```swift
ed25519_extract_public_key(pub.baseAddress, priv.baseAddress)
// priv = 64-byte Icarus extKey → dùng priv[0..31] = kL làm scalar
// pubKey = kL * B  ← đúng với Icarus
```

**`CarKeyPair.sign()` — Primary path: Sai ❌**
```swift
let seedData = Data(privBytes.prefix(32))  // chỉ lấy kL (32 bytes)
let ckPrivateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: seedData)
// CryptoKit RFC 8032: scalar_internal = SHA-512(kL)[0..31] clamped  ← KHÁC kL!
// Public key tương ứng của CryptoKit = SHA-512(kL)[0..31] * B ≠ kL * B
let ckSignature = try ckPrivateKey.signature(for: msgData)
// → Signature ký bằng SHA-512(kL) scalar
// → Nhưng pubKey trong witness = kL * B (từ bước trên)
// → KHÔNG nhất quán → Node reject
```

**`CarKeyPair.sign()` — Fallback: Đúng ✅ nhưng không bao giờ chạy**
```swift
} catch {
    // Chỉ chạy khi CryptoKit throw — nhưng CryptoKit không throw với 32-byte input hợp lệ
    ed25519_sign(sigAddr, msgAddr, messageCount, pubAddr, privAddr)
    // privAddr = full 64-byte kL||kR → kL làm scalar, kR làm nonce prefix → ĐÚNG Icarus
}
```

**Kết quả của Byron transaction với CryptoKit:**
- Witness = `[pubKey = kL*B, sig = SHA-512(kL)_signed, chainCode, 0xa0]`
- Node verify: `sig` phải verify với `pubKey = kL*B`
- Nhưng `sig` được tạo với scalar `SHA-512(kL)[0..31]` → point tương ứng = `SHA-512(kL)[0..31]*B` ≠ `kL*B`
- **Verification fail → Node reject transaction**

iOS có test function `testSigningLogic()` chính xác để phát hiện điều này:
```swift
if sigC == sigCK {
    logger.debug("=> RESULTS MATCH! (CryptoKit is safe to replace CEd25519)")
} else {
    logger.error("=> RESULTS DIFFER! (Warning: You MUST rollback to CEd25519 because Cardano uses Extended-Ed25519)")
}
```
Test này sẽ print **RESULTS DIFFER** vì CEd25519 (đúng) ≠ CryptoKit (sai).

---

### 8.3 Tại sao iOS team nói "hoạt động tốt với Byron"?

**Khả năng 1 — Đang test Shelley, nhầm là Byron:**  
`GadaShelley` (server-side) hoạt động hoàn toàn vì server ký. iOS team có thể đang test Shelley địa chỉ mà nhầm là Byron.

**Khả năng 2 — "Hoạt động tốt" nghĩa là xem balance, không phải send:**  
`CarPublicKey.derive()` đúng → Byron address generation đúng → balance display đúng. Nhưng SEND từ Byron address thực sự chưa được test kỹ.

**Khả năng 3 — Yoroi Byron API đã deprecate:**  
`Gada.swift` dùng `iohk-mainnet.yoroiwallet.com` API. API này có thể đã ngưng hỗ trợ, nên "hoạt động tốt" theo nghĩa không crash (API trả về error không có nghĩa là sign đúng).

---

### 8.4 Fix cần thiết cho iOS Byron

Chỉ cần thay thế CryptoKit primary path bằng CEd25519 trong `CarKeyPair.sign()`:

```swift
// HIỆN TẠI (SAI):
do {
    let seedData = Data(privBytes.prefix(32))
    let ckPrivateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: seedData)
    let ckSignature = try ckPrivateKey.signature(for: msgData)
    ckSignature.copyBytes(to: sigAddr, count: 64)
} catch {
    ed25519_sign(sigAddr, msgAddr, messageCount, pubAddr, privAddr)
}

// CẦN FIX (ĐÚNG):
// Bỏ CryptoKit hoàn toàn — dùng CEd25519 trực tiếp
// privAddr = pointer to full 64-byte kL||kR → CEd25519 dùng kL làm scalar, kR làm nonce prefix
ed25519_sign(sigAddr, msgAddr, messageCount, pubAddr, privAddr)
```

---

### 8.5 So sánh 3 phiên bản

| Tiêu chí | KMP (current) | Android cũ | iOS (reverted) |
|---|---|---|---|
| **Shelley signing** | On-device ✅ | On-device ✅ | **Server-side** ✅ |
| **Byron key derivation** | `IcarusKeyDerivation` ✅ | `CarDerivation.kt` ✅ | `CarDerivation.swift` ✅ |
| **Byron pubKey từ extKey** | `Ed25519Icarus.publicKeyFromScalar(kL)` ✅ | `CarPublicKey.derive()` CEd25519 ✅ | `ed25519_extract_public_key(kL)` ✅ |
| **Byron signing algorithm** | `Ed25519Icarus.sign(extKey64)` — kL scalar, kR nonce ✅ | `CarKeyPair.sign()` — kL scalar, kR nonce ✅ | CryptoKit `SHA-512(kL)` scalar ❌ |
| **Byron witness type** | Bootstrap key 2 + chainCode ✅ | Bootstrap key 2 + chainCode ✅ | Bootstrap key 2 + chainCode ✅ |
| **Min UTXO** | 1 ADA enforced ✅ | Có trong `Gada.kt` ✅ | Có trong `YOROIAPI.MIN_AMOUNT_PER_TX` ✅ |
| **Change dust absorption** | ✅ Fix #11 | ✅ Có trong `Gada.kt` | ✅ Có trong `Gada.swift` |
| **Max tx size** | ✅ Fix #12 | Không có | Không có |
| **Input dedup** | ✅ Fix #9 | Không có | Không có |

**Kết luận:**
- **KMP** — đúng hoàn toàn sau các fix (#5–#12). **Implementation tham chiếu tốt nhất.**
- **Android cũ** — signing đúng, protocol checks cơ bản đủ dùng. Thiếu tx size check và dedup.
- **iOS (reverted)** — Shelley đúng (server-side). Byron signing **vẫn sai** (CryptoKit SHA-512s kL). Cần thay CryptoKit bằng CEd25519 `ed25519_sign()` trực tiếp.

---

## 9. References

**Address generation:**
- [CIP-0003 — Icarus master key](https://github.com/cardano-foundation/CIPs/blob/master/CIP-0003/Icarus.md)
- [ed25519-bip32 spec](https://input-output-hk.github.io/adrestia/cardano-wallet/concepts/master-key-generation)
- [Cardano address structure — Byron](https://github.com/input-output-hk/cardano-wallet/wiki/About-Address-Format---Byron)
- [BIP-44 for Cardano (coin type 1815)](https://github.com/satoshilabs/slips/blob/master/slip-0044.md)

**Transaction signing:**
- [IOHK ed25519-bip32 paper](https://iohk.io/en/research/library/papers/ouroboros-praos-an-adaptively-secure-semi-synchronous-proof-of-stake-protocol/) — original ed25519-bip32 signing spec
- [RFC 8032 — Ed25519](https://tools.ietf.org/html/rfc8032) — standard Ed25519 (dùng để hiểu điểm khác biệt)
- [Cardano CDDL — transaction witness set](https://github.com/input-output-hk/cardano-ledger/blob/master/eras/shelley/test-suites/cddl-files/shelley.cddl) — CBOR witness format (key 2 = bootstrap_witnesses)
- [Blockfrost — submit transaction](https://docs.blockfrost.io/#tag/Cardano-Transactions/paths/~1tx~1submit/post) — submission API
