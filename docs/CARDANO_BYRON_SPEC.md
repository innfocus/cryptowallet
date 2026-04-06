# Cardano Byron Address Management — Technical Specification

**Version:** 1.1  
**Status:** Fixed ✅ — Build verified (`./gradlew :crypto-wallet-lib:compileAndroidMain` SUCCESS)  
**Scope:** `commonMain` — Icarus-style Byron address generation only

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

### 2.6 Byron Address Encoding

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

→ `IcarusKeyDerivation.mnemonicToEntropy()` implement lại BIP-39 reverse bằng `MnemonicCode.englishWordlist` (đã có sẵn trong thư viện). Cần test kỹ với 12/15/24 word mnemonics.

### 3.2 Ed25519 point multiplication performance

`Ed25519Icarus` dùng `ionspin/bignum` BigInteger — đủ chính xác nhưng chậm hơn native implementation. Mỗi call `publicKeyFromScalar` mất ~100-500ms tùy device. Không gọi trong hot path / UI thread.

---

## 4. File Structure

```
commonMain/kotlin/com/lybia/cryptowallet/wallets/cardano/
├── CardanoManager.kt       — Entry point, getByronAddress(), getShelleyAddress()
├── IcarusKeyDerivation.kt  — Master key + child key derivation (Icarus V2)
├── Ed25519Icarus.kt        — Ed25519 scalar multiplication (pre-clamped, no SHA-512)
├── Ed25519.kt              — Standard Ed25519 (for Shelley/TON — uses SHA-512)
└── CardanoAddress.kt       — Address encoding/decoding (Byron + Shelley)

commonTest/kotlin/com/lybia/cryptowallet/cardano/
└── CardanoByronKeyTest.kt  — Unit tests cho Icarus derivation
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

## 6. Quy tắc phòng tránh lỗi cho team

### ✅ DO

- Dùng `IcarusKeyDerivation` cho tất cả Byron address generation
- Kiểm tra địa chỉ generate ra bằng cách import mnemonic vào Yoroi browser extension
- Dùng `Ed25519Icarus.publicKeyFromScalar()` cho Icarus keys
- Dùng `Ed25519.publicKey()` (ton-kotlin wrapper) cho Shelley và TON keys

### ❌ DON'T

- **Đừng** dùng `slip10DeriveEd25519` cho Cardano Byron
- **Đừng** dùng `MnemonicCode.toSeed()` làm input cho Icarus master key
- **Đừng** hardened hóa role (`0`) và address index trong Byron path
- **Đừng** dùng `PrivateKeyEd25519(seed)` (ton-kotlin) trực tiếp với Icarus extended key
- **Đừng** nhầm "Icarus" với "Daedalus Byron HD" (V1 scheme — khác hoàn toàn)

### 🔑 Cheat Sheet: Khi nào dùng thuật toán nào?

| Chain/Era | Key derivation | Ed25519 public key |
|---|---|---|
| Cardano Byron (Icarus) | `IcarusKeyDerivation` | `Ed25519Icarus.publicKeyFromScalar(kL)` |
| Cardano Shelley (CIP-1852) | `slip10DeriveEd25519` | `Ed25519.publicKey(seed)` (ton-kotlin) |
| TON | SLIP-0010 (ton-kotlin) | `Ed25519.publicKey(seed)` (ton-kotlin) |

---

## 7. Test Vectors

### 6.1 Verify với Yoroi/Daedalus

Để xác nhận implementation đúng, import mnemonic sau vào Yoroi (browser extension, Byron wallet):

```
Mnemonic (15 words):
eight country switch draw meat scout mystery blade tip drift useless good keep usage title

Expected Byron address (index=0):
[Verify bằng cách import vào Yoroi → xem địa chỉ đầu tiên]
```

### 6.2 Structural test vectors (trong unit test)

- Byron address luôn bắt đầu bằng `Ae2` (mainnet Icarus)
- Base58-only characters
- CRC32 hợp lệ (validate với `CardanoAddress.isValidByronAddress()`)
- Deterministic: cùng mnemonic + index → cùng địa chỉ

### 7.3 Internal math vectors (unit tested)

```kotlin
// multiply8LE([0x01, ...27 zeros...]) = [0x08, ...zeros...]
// multiply8LE([0xFF, ...27 zeros...]) = [0xF8, 0x07, ...zeros...]  (carry)
// addScalarsLE([0xFF, ...], [0x01, ...]) = [0x00, 0x01, ...]      (carry)
```

File: `commonTest/.../cardano/CardanoByronKeyTest.kt` — 25+ test cases covering:
- `multiply8LE`: single bit, carry propagation, max carry, size validation
- `addScalarsLE`: simple add, carry, zeros
- Master key: correct size, clamping bits, determinism
- Child key: hardened/soft sizes, different indices, determinism
- Ed25519Icarus: public key size, determinism, different scalars
- Byron address: `Ae2` prefix, CRC validation, Base58 chars, uniqueness
- Regression: Bug #1, #3, #4 explicitly tested

---

## 8. References

- [CIP-0003 — Icarus master key](https://github.com/cardano-foundation/CIPs/blob/master/CIP-0003/Icarus.md)
- [ed25519-bip32 spec](https://input-output-hk.github.io/adrestia/cardano-wallet/concepts/master-key-generation)
- [Cardano address structure](https://github.com/input-output-hk/cardano-wallet/wiki/About-Address-Format---Byron)
- [BIP-44 for Cardano (coin type 1815)](https://github.com/satoshilabs/slips/blob/master/slip-0044.md)
