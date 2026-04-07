# Phân tích hiệu suất mã hóa Cardano trên Android

> **Ngày phân tích:** 2026-04-08
> **Trạng thái:** Draft — chờ review
> **Mức độ ưu tiên:** Cao — ảnh hưởng trực tiếp UX trên Android

---

## Mục lục

- [Phần A — Phân tích hiệu suất](#phần-a--phân-tích-hiệu-suất)
  - [1. Tổng quan thuật toán mã hóa Cardano](#1-tổng-quan-thuật-toán-mã-hóa-cardano)
  - [2. Phân tích flow và bottleneck](#2-phân-tích-flow-và-bottleneck)
  - [3. Phân tích chi tiết từng bottleneck](#3-phân-tích-chi-tiết-từng-bottleneck)
  - [4. Giải pháp đề xuất (5 tiers)](#4-giải-pháp-đề-xuất)
  - [5. So sánh BouncyCastle vs Rust vs Status quo](#5-so-sánh-bouncycastle-vs-rust-vs-status-quo)
  - [6. Lưu ý bảo mật](#6-lưu-ý-bảo-mật)
- [Phần B — Spec: Cache Master Key & Account Key (Phase 1)](#phần-b--spec-cache-master-key--account-key-phase-1)
  - [B1. Yêu cầu](#b1-yêu-cầu)
  - [B2. Thiết kế kỹ thuật](#b2-thiết-kế-kỹ-thuật)
  - [B3. Kế hoạch triển khai](#b3-kế-hoạch-triển-khai)

---

# Phần A — Phân tích hiệu suất

## 1. Tổng quan thuật toán mã hóa Cardano

Cardano sử dụng **Icarus V2 (CIP-0003)** — một biến thể của ed25519-bip32, khác biệt hoàn toàn so với SLIP-0010 (dùng bởi TON, Solana):

| Thuật toán | Mục đích | Implementation hiện tại |
|---|---|---|
| **PBKDF2-HMAC-SHA512** (4096 iter) | Mnemonic → Master key | Pure Kotlin (`ACTCrypto.pbkdf2SHA512`) |
| **Ed25519 scalar multiplication** | Public key từ private scalar | Pure BigInteger (`Ed25519Icarus`) |
| **Ed25519 signing** (EdDSA) | Ký transaction | Pure BigInteger (`Ed25519Icarus.sign`) |
| **HMAC-SHA512** | Child key derivation (mỗi level) | krypto lib (`HMAC.hmacSHA512`) |
| **Blake2b-224** | Key hash (Shelley address) | Pure Kotlin (`Blake2b.kt`) |
| **SHA3-256** | Address root (Byron) | Pure Kotlin (`SHA3.kt`) |
| **CBOR encoding** | Transaction serialization | Pure Kotlin (`cbor/CborEncoder.kt`) |
| **Bech32** | Shelley address encoding | Pure Kotlin (`Bech32.kt`) |
| **Base58** | Byron address encoding | bitcoin-kmp lib |
| **CRC32** | Byron address checksum | Pure Kotlin (`CRC32.kt`) |

---

## 2. Phân tích flow và bottleneck

### 2.1 Shelley Address Generation — BOTTLENECK CHÍNH

**Flow: `CardanoManager.getShelleyAddress()`**

```
getShelleyAddress(account=0, index=0)
├── deriveShelleyPaymentKey(0, 0)          ← gọi masterKeyFromMnemonic() lần 1
│   ├── PBKDF2-4096 (96 bytes output)      ⏱ ~1.5-2s
│   ├── deriveChildKey(1852', hardened)     ⏱ ~1ms (2x HMAC-SHA512, no pubkey)
│   ├── deriveChildKey(1815', hardened)     ⏱ ~1ms
│   ├── deriveChildKey(0', hardened)        ⏱ ~1ms
│   ├── deriveChildKey(0, soft)            ⏱ ~50-100ms ← cần publicKeyFromExtended()
│   │   └── Ed25519Icarus.publicKeyFromScalar()  ← scalar mult #1
│   ├── deriveChildKey(index, soft)        ⏱ ~50-100ms ← cần publicKeyFromExtended()
│   │   └── Ed25519Icarus.publicKeyFromScalar()  ← scalar mult #2
│   └── publicKeyFromExtended(finalKey)    ⏱ ~50-100ms ← scalar mult #3
│
├── deriveShelleyStakingKey(0)             ← gọi masterKeyFromMnemonic() LẦN 2 ❌
│   ├── PBKDF2-4096 (LẶP LẠI!)           ⏱ ~1.5-2s ← DUPLICATE WORK
│   ├── deriveChildKey(1852', hardened)     ⏱ ~1ms
│   ├── deriveChildKey(1815', hardened)     ⏱ ~1ms
│   ├── deriveChildKey(0', hardened)        ⏱ ~1ms
│   ├── deriveChildKey(2, soft)            ⏱ ~50-100ms ← scalar mult #4
│   ├── deriveChildKey(0, soft)            ⏱ ~50-100ms ← scalar mult #5
│   └── publicKeyFromExtended(finalKey)    ⏱ ~50-100ms ← scalar mult #6
│
├── Blake2b.hash(paymentPub, 28)           ⏱ ~0.5ms
├── Blake2b.hash(stakingPub, 28)           ⏱ ~0.5ms
└── Bech32.encode(...)                     ⏱ ~0.1ms
```

**Tổng thời gian ước tính: ~3.5-4.5 giây**

### 2.2 Phân bổ thời gian (% ước tính)

```
┌─────────────────────────────────────────┐
│ PBKDF2-4096 x2 (duplicate)      ~70%   │ ████████████████████████████
│ Ed25519 scalar mult x6          ~25%   │ ██████████
│ HMAC-SHA512 (child derive)       ~3%   │ █
│ Blake2b + Bech32 + CBOR          ~2%   │ █
└─────────────────────────────────────────┘
```

### 2.3 Số lần gọi PBKDF2 trùng lặp theo operation

| Operation | Hàm gọi | Số lần PBKDF2 | Thời gian lãng phí |
|---|---|---|---|
| `getAddress()` | `getShelleyAddress()` → payment + staking | **2x** | ~1.5-2s |
| `getShelleyAddress()` | payment + staking | **2x** | ~1.5-2s |
| `getByronAddress()` | `deriveByronKey()` | **1x** | 0 (unavoidable nếu lần đầu) |
| `getStakingAddress()` | staking | **1x** | 0 |
| `getBalance()` | gọi `getAddress()` | **2x** | ~1.5-2s |
| `buildAndSignTransaction()` | `getAddress()` + signing key | **3x** | ~3-4s |
| `sendToken()` | `getAddress()` + signing key | **3x** | ~3-4s |
| `stake()` | `getAddress()` + staking address + keys | **5x** | ~6-8s |
| `unstake()` | `getAddress()` + staking address + keys | **5x** | ~6-8s |

### 2.4 Transaction Signing

**Flow: `Ed25519Icarus.sign(extKey64, txBodyHash)`**

```
sign(extKey64, message)
├── sha512(kR + message)                  ⏱ ~0.1ms
├── scalarMultBase(r)                     ⏱ ~50-100ms  ← scalar mult #1
│   └── 255 iterations × pointAdd()
│       └── mỗi pointAdd: 8-10 BigInteger multiply + 2 modInverse
├── publicKeyFromScalar(kL)               ⏱ ~50-100ms  ← scalar mult #2
├── sha512(rEnc + aEnc + message)         ⏱ ~0.1ms
└── BigInteger add/multiply/mod           ⏱ ~0.1ms
```

**Tổng: ~100-200ms per signature**

---

## 3. Phân tích chi tiết từng bottleneck

### 3.1 PBKDF2-HMAC-SHA512 — Bottleneck #1

**File:** `ACTCrypto.kt:20-50`

**Vấn đề cốt lõi:** Pure Kotlin loop với 4096 iterations, mỗi iteration:
- Gọi `HMAC.hmacSHA512()` → allocate 64-byte result
- `u.copyOf()` → allocate 64-byte copy
- XOR loop 64 bytes

**Memory pressure:** 4096 × 2 × 64 bytes = **512 KB garbage per master key**, gây GC pressure.

**Vấn đề nghiêm trọng hơn:** `getShelleyAddress()` gọi `masterKeyFromMnemonic()` **2 lần** — lần cho payment key, lần cho staking key — mỗi lần derive lại master key từ đầu. **Tốn gấp đôi thời gian không cần thiết.**

```kotlin
// CardanoManager.kt:38-46 — payment key
private fun deriveShelleyPaymentKey(account: Int, index: Int) {
    val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords) // PBKDF2 lần 1
    // ... derive m/1852'/1815'/account'/0/index
}

// CardanoManager.kt:51-59 — staking key
private fun deriveShelleyStakingKey(account: Int) {
    val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords) // PBKDF2 lần 2 ❌
    // ... derive m/1852'/1815'/account'/2/0
}
```

### 3.2 Ed25519 Scalar Multiplication — Bottleneck #2

**File:** `androidMain/.../Ed25519Icarus.kt:91-105`

**Vấn đề cốt lõi:** Double-and-add algorithm trên `java.math.BigInteger`:

```kotlin
for (i in 0 until 255) {                    // 255 iterations
    if (s.testBit(i)) {
        val (nx, ny) = pointAdd(rX, rY, cX, cY)  // conditional add
        rX = nx; rY = ny
    }
    val (dx, dy) = pointAdd(cX, cY, cX, cY)      // always double
    cX = dx; cY = dy
}
```

**Mỗi `pointAdd()` (line 108-127):**
- 8-10 `BigInteger.multiply().mod(P)` — mỗi lần allocate BigInteger mới
- 2 `BigInteger.modInverse(P)` — Extended Euclidean, O(log p) trên 256-bit
- Tổng: ~10 BigInteger objects allocated per pointAdd

**Tổng per public key:**
- ~510 `pointAdd()` calls (average)
- ~1020 `modInverse()` operations
- ~5000 `BigInteger.multiply()` + `mod()` operations
- ~5000+ BigInteger object allocations → GC pressure

**Vấn đề thêm:** Thuật toán double-and-add **không constant-time** — timing side-channel attack possible (branch trên `s.testBit(i)`). Nhưng đây là vấn đề security, không phải performance.

### 3.3 Blake2b — Bottleneck nhỏ

**File:** `Blake2b.kt:50-74`

Pure Kotlin, allocate 2 `ULongArray(16)` per block. Với input 32 bytes (1 block), impact nhỏ (~0.5ms). Chỉ trở thành vấn đề khi hash input lớn hoặc gọi nhiều lần liên tiếp.

### 3.4 CBOR Canonical Encoding — Bottleneck tiềm ẩn

**File:** `cbor/CborEncoder.kt`

Map entries được sort bằng cách **encode cả 2 keys thành bytes để so sánh**, rồi lại encode lần nữa khi ghi output. Với multi-asset transactions (nhiều policy ID × nhiều asset name), số lần encode tăng theo O(N log N).

### 3.5 `mnemonicToEntropy` — Micro bottleneck

**File:** `IcarusKeyDerivation.kt:55-76`

```kotlin
val wordMap = MnemonicCode.englishWordlist.mapIndexed { i, w -> w to i }.toMap()
```

Tạo lại `wordMap` (2048 entries HashMap) **mỗi lần gọi**. Với Shelley address, gọi 2 lần → 2 HashMap allocations không cần thiết.

---

## 4. Giải pháp đề xuất

### Tier 1: Cache master key & account key — Ưu tiên cao

**Impact:** Giảm từ ~4s xuống ~2s (lần đầu), **<1ms** (lần sau) cho Shelley address generation.

> Chi tiết kỹ thuật đầy đủ: xem [Phần B — Spec: Cache Master Key & Account Key](#phần-b--spec-cache-master-key--account-key-phase-1)

### Tier 2: Thay thế Ed25519Icarus bằng BouncyCastle — Ưu tiên cao

**Impact:** Giảm Ed25519 scalar multiplication từ ~50-100ms xuống ~1-3ms (30-50x nhanh hơn).

BouncyCastle đã có sẵn trong project (dependency của web3j) và có Ed25519 implementation tối ưu:

```kotlin
// androidMain/wallets/cardano/Ed25519Icarus.kt — thay thế bằng BouncyCastle
import org.bouncycastle.math.ec.rfc8032.Ed25519

internal actual object Ed25519Icarus {

    actual fun publicKeyFromScalar(scalar32: ByteArray): ByteArray {
        require(scalar32.size == 32)
        val publicKey = ByteArray(32)
        Ed25519.scalarMultBaseEncoded(scalar32, publicKey, 0)
        return publicKey
    }

    actual fun sign(extKey64: ByteArray, message: ByteArray): ByteArray {
        require(extKey64.size == 64)
        val kL = extKey64.copyOfRange(0, 32)
        val kR = extKey64.copyOfRange(32, 64)

        // 1. Nonce r = SHA512(kR || message) mod L
        val rHash = sha512(kR + message)
        val r = reduceScalar(rHash) // mod L

        // 2. R = r * B
        val rEnc = ByteArray(32)
        Ed25519.scalarMultBaseEncoded(r, rEnc, 0)

        // 3. Public key A = kL * B
        val aEnc = ByteArray(32)
        Ed25519.scalarMultBaseEncoded(kL, aEnc, 0)

        // 4. k = SHA512(R || A || message) mod L
        val kHash = sha512(rEnc + aEnc + message)
        val k = reduceScalar(kHash)

        // 5. S = (r + k * scalar) mod L
        val s = addScalars(r, multiplyScalars(k, kL))
        return rEnc + s
    }

    // ... reduceScalar, addScalars, multiplyScalars helper functions
}
```

**Tại sao BouncyCastle nhanh hơn nhiều:**
- Dùng **extended coordinates** (4 coordinates thay vì 2) → loại bỏ `modInverse()` trong pointAdd
- Dùng **precomputed table** cho base point → giảm số point additions từ ~510 xuống ~64
- Dùng **fixed-window method** thay vì double-and-add bit-by-bit
- Native `BigInteger` operations tối ưu cho 256-bit

**Lưu ý:** BouncyCastle `Ed25519` class sử dụng SHA-512 nội bộ cho RFC 8032 standard signing. Icarus KHÔNG dùng SHA-512 cho key derivation (scalar = kL trực tiếp). Cần dùng `scalarMultBaseEncoded()` thay vì `generatePublicKey()` để bypass SHA-512 step.

**Khả thi:** BouncyCastle 1.83 (đã có trong project) hỗ trợ `Ed25519.scalarMultBaseEncoded()`. Chỉ cần thay implementation, không thêm dependency.

### Tier 3: Tối ưu PBKDF2 — Ưu tiên trung bình

#### Option A: Dùng BouncyCastle PBKDF2

```kotlin
// androidMain — actual implementation dùng BouncyCastle
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.digests.SHA512Digest

actual fun pbkdf2SHA512(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val generator = PKCS5S2ParametersGenerator(SHA512Digest())
    generator.init(password, salt, iterations)
    return (generator.generateDerivedParameters(keyLength * 8) as KeyParameter).key
}
```

**Impact:** ~2-5x nhanh hơn pure Kotlin.

#### Option B: Dùng Android javax.crypto

```kotlin
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun pbkdf2SHA512(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val spec = PBEKeySpec(String(password).toCharArray(), salt, iterations, keyLength * 8)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    return factory.generateSecret(spec).encoded
}
```

**Impact:** ~3-10x nhanh hơn pure Kotlin. Với Cardano Icarus (password rỗng), convert CharArray không gây vấn đề.

### Tier 4: Thay thế Blake2b bằng BouncyCastle — Ưu tiên thấp

```kotlin
import org.bouncycastle.crypto.digests.Blake2bDigest

actual fun blake2b(input: ByteArray, outputLength: Int): ByteArray {
    val digest = Blake2bDigest(outputLength * 8)
    digest.update(input, 0, input.size)
    val output = ByteArray(outputLength)
    digest.doFinal(output, 0)
    return output
}
```

**Impact:** ~3-5x nhanh hơn, nhưng absolute time đã nhỏ (<1ms).

### Tier 5: Tối ưu CBOR canonical encoding — Ưu tiên thấp

Cache encoded key bytes trong comparator thay vì encode lại mỗi lần so sánh:

```kotlin
private fun encodeCanonicalMap(entries: List<Pair<CborValue, CborValue>>, buf: MutableList<Byte>) {
    val keysWithEncoded = entries.map { (k, v) -> Triple(k, v, encodeCanonical(k)) }
    val sorted = keysWithEncoded.sortedWith(Comparator { a, b ->
        val ka = a.third  // dùng cached bytes
        val kb = b.third
        // compare...
    })
    // ...
}
```

### Tổng hợp giải pháp

| # | Giải pháp | Impact | Effort | Thời gian tiết kiệm |
|---|---|---|---|---|
| **Tier 1** | Cache master key + account key | **Rất cao** | Thấp (~1-2 ngày) | ~1.5-2s (loại bỏ PBKDF2 duplicate) |
| **Tier 1** | Cache wordMap | Thấp | Rất thấp (~1h) | ~10-20ms |
| **Tier 2** | BouncyCastle Ed25519 thay BigInteger | **Cao** | Trung bình (~2-3 ngày) | ~300-600ms (6 scalar mults) |
| **Tier 3** | BouncyCastle/javax PBKDF2 | **Cao** | Thấp (~1 ngày) | ~1-1.5s (2-5x faster PBKDF2) |
| **Tier 4** | BouncyCastle Blake2b | Thấp | Thấp (~0.5 ngày) | ~1-2ms |
| **Tier 5** | CBOR cache encoded keys | Thấp | Thấp (~0.5 ngày) | ~5-20ms (multi-asset tx) |

### Kết quả kỳ vọng sau tối ưu

```
                        Trước       Sau (Tier 1-3)
Shelley address:        ~3.5-4.5s   ~0.3-0.5s      (8-15x nhanh hơn)
Byron address:          ~2-2.5s     ~0.2-0.3s       (8-10x nhanh hơn)
Transaction signing:    ~100-200ms  ~5-10ms         (20-40x nhanh hơn)
```

---

## 5. So sánh: BouncyCastle vs Rust vs Status quo

| Tiêu chí | Status quo (BigInteger) | BouncyCastle (có sẵn) | Rust (UniFFI) |
|---|---|---|---|
| **Ed25519 pubkey** | ~50-100ms | ~1-3ms | ~0.1-0.5ms |
| **PBKDF2-4096** | ~1.5-2s | ~0.3-0.8s | ~0.1-0.3s |
| **Thêm dependency** | Không | Không (đã có) | Rust toolchain + FFI |
| **Build complexity** | Không | Không | Rất cao |
| **Effort** | — | 3-5 ngày | 2-4 tuần |
| **Risk** | — | Thấp | Cao |
| **KMP compatibility** | Common | Android-only (cần expect/actual) | Android-only (cần expect/actual) |

**Khuyến nghị:** Dùng BouncyCastle cho Android là giải pháp tối ưu nhất về tỷ lệ lợi ích/chi phí. Performance đạt ~90% so với Rust nhưng không thêm build complexity. iOS có thể dùng `CryptoKit.Curve25519` khi cần.

---

## 6. Lưu ý bảo mật

### 6.1 Timing side-channel

`Ed25519Icarus.scalarMultBase()` hiện tại **không constant-time** — branch trên `s.testBit(i)`. Trên mobile device, timing attack khó thực hiện hơn server, nhưng vẫn nên fix khi chuyển sang BouncyCastle (đã constant-time).

### 6.2 Memory zeroization

Sau khi dùng xong private key / scalar, nên zero-fill ByteArray:

```kotlin
scalar32.fill(0)
extKey64.fill(0)
```

Hiện tại code không thực hiện điều này.

### 6.3 BigInteger GC

`java.math.BigInteger` objects chứa private key material không thể zero-fill trước khi GC. BouncyCastle Ed25519 dùng internal byte arrays có thể kiểm soát lifecycle tốt hơn.

---

# Phần B — Spec: Cache Master Key & Account Key (Phase 1)

## B1. Yêu cầu

### Giới thiệu

Hiệu suất Cardano trên Android hiện rất chậm do các thao tác mã hóa (PBKDF2-4096, Ed25519 scalar multiplication) được tính lại từ đầu mỗi khi gọi bất kỳ hàm nào trong `CardanoManager`. Mỗi thao tác PBKDF2-4096 tốn ~1.5-2 giây trên Android — và một số flow hiện tại gọi lại PBKDF2 đến **5 lần** trong cùng một operation (`stake()`).

Yêu cầu này triển khai hệ thống cache cho master key, account-level key, và các derived key đã tính — loại bỏ hoàn toàn các tính toán trùng lặp trong lifecycle của `CardanoManager` instance.

### Thuật ngữ

- **Master Key**: Cặp (extKey[64], chainCode[32]) được derive từ mnemonic qua PBKDF2-HMAC-SHA512 với 4096 iterations. Đây là bước tốn thời gian nhất (~1.5-2s).
- **Account Key**: Cặp (extKey[64], chainCode[32]) tại level `m/purpose'/1815'/account'` — kết quả của 3 hardened child derivations từ master key. Shared giữa payment key và staking key trong cùng account.
- **Shelley Payment Key**: Key tại `m/1852'/1815'/account'/0/index` — dùng cho Shelley address và transaction signing.
- **Shelley Staking Key**: Key tại `m/1852'/1815'/account'/2/0` — dùng cho staking address và delegation signing.
- **Byron Key**: Key tại `m/44'/1815'/0'/0/index` — dùng cho Byron legacy address.
- **Soft Derivation**: Child derivation không hardened — yêu cầu tính public key (Ed25519 scalar multiplication) từ parent extended key.
- **PBKDF2-4096**: Password-Based Key Derivation Function 2 với 4096 iterations HMAC-SHA512, dùng để derive master key từ mnemonic entropy.

### Yêu cầu 1: Cache master key (PBKDF2 result)

**User Story:** Là một người dùng ví, tôi muốn các thao tác Cardano (xem địa chỉ, gửi ADA, staking) phản hồi nhanh, thay vì phải chờ vài giây mỗi lần.

#### Tiêu chí chấp nhận

1. WHEN một `CardanoManager` instance được tạo với mnemonic, PBKDF2-4096 SHALL chỉ chạy **tối đa 1 lần** trong toàn bộ lifecycle của instance đó.
2. THE master key (extKey[64] + chainCode[32]) SHALL được cache lazily — chỉ tính khi lần đầu tiên cần, không tính trong constructor.
3. WHEN master key đã được cache, tất cả các hàm gọi tiếp theo (`getShelleyAddress`, `getByronAddress`, `buildAndSignTransaction`, `stake`, `unstake`) SHALL sử dụng cached value mà không gọi lại `masterKeyFromMnemonic()`.
4. THE cache SHALL tồn tại trong suốt lifecycle của `CardanoManager` instance — không expire, không invalidate tự động.
5. WHEN `CardanoManager` instance bị garbage collected, cached key data SHALL bị garbage collected cùng — không memory leak.

### Yêu cầu 2: Cache account-level key

**User Story:** Là một nhà phát triển, tôi muốn các derived key ở account level được cache riêng, vì payment key và staking key share cùng 3 hardened derivation levels.

#### Tiêu chí chấp nhận

1. THE account key tại `m/purpose'/1815'/account'` SHALL được cache theo cặp (purpose, account) — phân biệt giữa Shelley (purpose=1852) và Byron (purpose=44).
2. WHEN `deriveShelleyPaymentKey(account=0)` và `deriveShelleyStakingKey(account=0)` được gọi, 3 hardened derivations (purpose'/1815'/account') SHALL chỉ chạy **1 lần** cho cùng account.
3. WHEN `deriveByronKey(index)` được gọi, 3 hardened derivations (44'/1815'/0') SHALL được cache riêng và chỉ chạy 1 lần.
4. THE cache key SHALL phân biệt giữa các account index khác nhau — `account=0` và `account=1` là 2 cache entries riêng biệt.
5. WHEN nhiều hàm cùng cần account key cho `account=0` (ví dụ: `getShelleyAddress()` cần cả payment và staking), chỉ **1 lần** derive account key.

### Yêu cầu 3: Cache derived key kết quả cuối

**User Story:** Là một nhà phát triển, tôi muốn các key cuối cùng (payment key, staking key, Byron key) cũng được cache, vì chúng được dùng lại nhiều lần trong signing và address generation.

#### Tiêu chí chấp nhận

1. THE Shelley payment key Triple(pubKey, chainCode, extKey) tại `(account, index)` SHALL được cache sau lần derive đầu tiên.
2. THE Shelley staking key Triple(pubKey, chainCode, extKey) tại `(account)` SHALL được cache sau lần derive đầu tiên.
3. THE Byron key Triple(pubKey, chainCode, extKey) tại `(index)` SHALL được cache sau lần derive đầu tiên.
4. WHEN `buildAndSignTransaction()` gọi `getAddress()` rồi gọi `deriveShelleyPaymentKey(0, 0)` để sign, toàn bộ key derivation chain SHALL chỉ chạy **1 lần** — lần thứ 2 trả về cached result.
5. WHEN `stake()` cần cả payment key, staking key và staking address, tổng số Ed25519 scalar multiplication SHALL giảm từ 6+ xuống **tối đa 4** (2 soft derives cho payment + 2 soft derives cho staking — mỗi nhóm chỉ chạy 1 lần).

### Yêu cầu 4: Cache wordMap trong IcarusKeyDerivation

#### Tiêu chí chấp nhận

1. THE `MnemonicCode.englishWordlist` → Map<String, Int> SHALL được tạo **1 lần duy nhất** ở module level (lazy initialization).
2. WHEN `mnemonicToEntropy()` được gọi nhiều lần, wordMap SHALL tái sử dụng instance đã tạo.

### Yêu cầu 5: Thread safety

#### Tiêu chí chấp nhận

1. THE master key cache SHALL thread-safe — nếu 2 coroutine cùng gọi `getAddress()` đồng thời, PBKDF2 SHALL chỉ chạy 1 lần (không race condition dẫn đến tính 2 lần).
2. THE account key cache và derived key cache SHALL thread-safe với cùng guarantee.
3. THE implementation SHALL sử dụng `@Synchronized` (JVM/Android target) cho synchronous methods. Nếu sau này cần Kotlin/Native, refactor sang `atomicfu` hoặc `stately-concurrency`.

### Yêu cầu 6: Backward compatibility

#### Tiêu chí chấp nhận

1. ALL public method signatures của `CardanoManager` SHALL giữ nguyên — không thay đổi parameter hoặc return type.
2. ALL generated addresses (Shelley, Byron, Staking) SHALL giữ nguyên bit-for-bit với implementation hiện tại cho cùng mnemonic.
3. ALL generated signatures SHALL giữ nguyên bit-for-bit.
4. `IcarusKeyDerivation` public API SHALL giữ nguyên — cache là internal concern của `CardanoManager`.
5. EXISTING unit tests SHALL pass mà không cần sửa đổi.

### Yêu cầu 7: Security — zeroization

#### Tiêu chí chấp nhận

1. `CardanoManager` SHALL cung cấp method `clearCachedKeys()` để xóa tất cả cached key data (fill ByteArray bằng 0x00).
2. WHEN `clearCachedKeys()` được gọi, tất cả cached ByteArray (master key, account keys, derived keys) SHALL được zero-filled trước khi set reference = null.
3. AFTER `clearCachedKeys()`, các hàm derive SHALL tính lại từ đầu (lazy re-initialization).

---

## B2. Thiết kế kỹ thuật

### Kiến trúc Cache 3 tầng

```
                    ┌─────────────────────┐
   Cache L1         │   Master Key        │  ← PBKDF2-4096 (~1.5-2s)
   (lazy)           │ (extKey64, cc32)    │     Chỉ tính 1 lần per instance
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │                               │
   Cache L2   ▼                               ▼
   (map)  ┌───────────────┐           ┌───────────────┐
          │ Shelley Acct  │           │ Byron Acct    │
          │ m/1852'/1815' │           │ m/44'/1815'   │
          │ /account'     │           │ /0'           │
          └───────┬───────┘           └───────┬───────┘
                  │                           │
          ┌───────┼───────┐                   │
          │               │                   │
   Cache  ▼               ▼                   ▼
   L3   ┌─────────┐  ┌──────────┐    ┌──────────────┐
  (map) │ Payment │  │ Staking  │    │ Byron addr   │
        │ /0/idx  │  │ /2/0     │    │ /0/idx       │
        │→ Triple │  │→ Triple  │    │→ Triple      │
        └─────────┘  └──────────┘    └──────────────┘
```

### Cache structure trong CardanoManager

```kotlin
class CardanoManager(mnemonic: String, ...) {

    // ── Cache key ─────────────────────────────────────────────
    private data class AccountCacheKey(val purpose: Int, val account: Int)

    // ── Cache L1: Master key ──────────────────────────────────
    @Volatile private var masterKeyCache: Pair<ByteArray, ByteArray>? = null

    @Synchronized
    private fun getMasterKey(): Pair<ByteArray, ByteArray> {
        return masterKeyCache ?: IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
            .also { masterKeyCache = it }
    }

    // ── Cache L2: Account-level keys ──────────────────────────
    private val accountKeyCache = mutableMapOf<AccountCacheKey, Pair<ByteArray, ByteArray>>()

    // ── Cache L3: Final derived keys ──────────────────────────
    private val shelleyPaymentKeyCache = mutableMapOf<Pair<Int,Int>, Triple<ByteArray, ByteArray, ByteArray>>()
    private val shelleyStakingKeyCache = mutableMapOf<Int, Triple<ByteArray, ByteArray, ByteArray>>()
    private val byronKeyCache = mutableMapOf<Int, Triple<ByteArray, ByteArray, ByteArray>>()
}
```

### Refactor deriveShelleyPaymentKey()

**Trước:**
```kotlin
private fun deriveShelleyPaymentKey(account: Int, index: Int): Triple<...> {
    val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords) // PBKDF2!
    var k = masterExt; var cc = masterCC
    // 3 hardened derives + 2 soft derives...
    return Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)
}
```

**Sau:**
```kotlin
private fun deriveShelleyPaymentKey(account: Int, index: Int): Triple<...> {
    val cacheKey = account to index
    synchronized(shelleyPaymentKeyCache) {
        shelleyPaymentKeyCache[cacheKey]?.let { return it }
    }

    val (k0, cc0) = getShelleyAccountKey(account)    // cache L2
    var k = k0; var cc = cc0
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

    synchronized(shelleyPaymentKeyCache) {
        shelleyPaymentKeyCache[cacheKey] = result
    }
    return result
}
```

### Refactor deriveShelleyStakingKey()

```kotlin
private fun deriveShelleyStakingKey(account: Int): Triple<...> {
    synchronized(shelleyStakingKeyCache) {
        shelleyStakingKeyCache[account]?.let { return it }
    }

    val (k0, cc0) = getShelleyAccountKey(account)    // cache L2 — SHARED với payment key
    var k = k0; var cc = cc0
    IcarusKeyDerivation.deriveChildKey(k, cc, 2, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

    synchronized(shelleyStakingKeyCache) {
        shelleyStakingKeyCache[account] = result
    }
    return result
}
```

### Account key helpers

```kotlin
@Synchronized
private fun getShelleyAccountKey(account: Int): Pair<ByteArray, ByteArray> {
    val cacheKey = AccountCacheKey(purpose = 1852, account = account)
    accountKeyCache[cacheKey]?.let { return it }

    val (masterExt, masterCC) = getMasterKey()            // cache L1
    var k = masterExt; var cc = masterCC
    IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }

    return (k to cc).also { accountKeyCache[cacheKey] = it }
}

@Synchronized
private fun getByronAccountKey(): Pair<ByteArray, ByteArray> {
    val cacheKey = AccountCacheKey(purpose = 44, account = 0)
    accountKeyCache[cacheKey]?.let { return it }

    val (masterExt, masterCC) = getMasterKey()            // cache L1
    var k = masterExt; var cc = masterCC
    IcarusKeyDerivation.deriveChildKey(k, cc, 44, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = true).let { (nk, nc) -> k = nk; cc = nc }

    return (k to cc).also { accountKeyCache[cacheKey] = it }
}
```

### Refactor deriveByronKey()

```kotlin
private fun deriveByronKey(index: Int): Triple<...> {
    synchronized(byronKeyCache) {
        byronKeyCache[index]?.let { return it }
    }

    val (k0, cc0) = getByronAccountKey()              // cache L2
    var k = k0; var cc = cc0
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
    val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

    synchronized(byronKeyCache) {
        byronKeyCache[index] = result
    }
    return result
}
```

### wordMap cache trong IcarusKeyDerivation

```kotlin
internal object IcarusKeyDerivation {
    // Module-level lazy cache — tạo 1 lần, dùng mãi
    private val WORD_MAP: Map<String, Int> by lazy {
        MnemonicCode.englishWordlist.mapIndexed { i, w -> w to i }.toMap()
    }

    private fun mnemonicToEntropy(words: List<String>): ByteArray {
        require(words.all { it in WORD_MAP }) { "Mnemonic contains unknown words" }
        val allBits = words.flatMap { word ->
            val idx = WORD_MAP.getValue(word)
            // ...
        }
        // ...
    }
}
```

### clearCachedKeys() — Security zeroization

```kotlin
@Synchronized
fun clearCachedKeys() {
    // L1: Master key
    masterKeyCache?.let { (ext, cc) ->
        ext.fill(0)
        cc.fill(0)
    }
    masterKeyCache = null

    // L2: Account keys
    accountKeyCache.values.forEach { (ext, cc) ->
        ext.fill(0)
        cc.fill(0)
    }
    accountKeyCache.clear()

    // L3: Derived keys
    shelleyPaymentKeyCache.values.forEach { (pub, cc, ext) ->
        pub.fill(0); cc.fill(0); ext.fill(0)
    }
    shelleyPaymentKeyCache.clear()

    shelleyStakingKeyCache.values.forEach { (pub, cc, ext) ->
        pub.fill(0); cc.fill(0); ext.fill(0)
    }
    shelleyStakingKeyCache.clear()

    byronKeyCache.values.forEach { (pub, cc, ext) ->
        pub.fill(0); cc.fill(0); ext.fill(0)
    }
    byronKeyCache.clear()
}
```

### Thread Safety — Quyết định

| Concern | Giải pháp | Lý do |
|---|---|---|
| Master key (L1) | `@Volatile` field + `@Synchronized` getter | Prevent duplicate PBKDF2 |
| Account keys (L2) | `@Synchronized` trên getter methods | `getOrPut()` trên mutableMap không atomic |
| Derived keys (L3) | `synchronized(map)` blocks | Cho phép concurrent reads khác nhau |
| `clearCachedKeys()` | `@Synchronized` | Exclusive access khi zeroize |

**Lưu ý:** `@Synchronized` chỉ work JVM/Android. Project hiện chỉ active Android target. Nếu cần iOS sau này, refactor sang `atomicfu` hoặc `stately-concurrency`.

### Hiệu suất kỳ vọng

| Operation | PBKDF2 (trước) | PBKDF2 (sau) | Ed25519 scalarMult (trước) | Ed25519 scalarMult (sau) |
|---|---|---|---|---|
| `getAddress()` (lần đầu) | 2 | **1** | 6 | **4** |
| `getAddress()` (lần 2+) | 2 | **0** | 6 | **0** |
| `buildAndSignTransaction()` (lần đầu) | 3 | **1** | 9 | **4** |
| `buildAndSignTransaction()` (đã có address) | 3 | **0** | 9 | **0** |
| `stake()` (lần đầu) | 5 | **1** | 15+ | **8** |
| `stake()` (đã có keys) | 5 | **0** | 15+ | **0** |

| Operation | Trước | Sau (lần đầu) | Sau (cached) |
|---|---|---|---|
| `getAddress()` | ~3.5-4.5s | ~2-2.5s | **<1ms** |
| `buildAndSignTransaction()` | ~5-6s | ~2-2.5s | **~100-200ms** (signing only) |
| `stake()` | ~8-10s | ~2.5-3s | **~100-200ms** (signing only) |

### Ảnh hưởng file

| File | Thay đổi |
|---|---|
| `commonMain/.../cardano/CardanoManager.kt` | Thêm cache fields, refactor 4 derive methods, thêm helper methods, thêm `clearCachedKeys()` |
| `commonMain/.../cardano/IcarusKeyDerivation.kt` | wordMap cache (1 dòng thêm, 1 dòng sửa) |
| Test files | Thêm test cho cache behavior, verify output consistency |

### Scope KHÔNG bao gồm

- Thay đổi `IcarusKeyDerivation` public API — chỉ thêm wordMap cache.
- Thay đổi `Ed25519Icarus` implementation — đây là Phase 2 (Tier 2) riêng.
- Cache ở tầng `CommonCoinsManager` hoặc `CoinsManager` — cache nằm trong `CardanoManager`.
- Persist cache ra disk — cache chỉ in-memory, per instance.

---

## B3. Kế hoạch triển khai

**Effort ước tính:** 1-2 ngày
**Files thay đổi:** 2 files chính + test files
**Risk:** Thấp — internal refactor, không thay đổi public API

### Tasks

- [ ] 1. Thêm cache infrastructure vào CardanoManager
  - [ ] 1.1 Thêm cache fields và AccountCacheKey data class
    - Mở file `crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/wallets/cardano/CardanoManager.kt`
    - Thêm `private data class AccountCacheKey(val purpose: Int, val account: Int)`
    - Thêm các cache fields ngay sau `private val mnemonicWords`
    - Verify: `./gradlew :crypto-wallet-lib:compileAndroidMain`
    - _Yêu cầu: 1.1, 1.2, 1.4, 1.5_

  - [ ] 1.2 Thêm getMasterKey() synchronized getter
    - `@Synchronized` đảm bảo PBKDF2 chỉ chạy 1 lần nếu 2 threads gọi đồng thời
    - _Yêu cầu: 1.1, 1.3, 5.1_

- [ ] 2. Thêm account key helpers (Cache L2)
  - [ ] 2.1 Thêm getShelleyAccountKey()
    - Dùng `@Synchronized` thay vì `getOrPut` vì `getOrPut` trên `mutableMapOf()` không atomic
    - _Yêu cầu: 2.1, 2.2, 2.5, 5.2_

  - [ ] 2.2 Thêm getByronAccountKey()
    - _Yêu cầu: 2.1, 2.3, 5.2_

- [ ] 3. Refactor derive methods để dùng cache (Cache L3)
  - [ ] 3.1 Refactor deriveShelleyPaymentKey()
    - Verify derivation path: master → 1852' → 1815' → account' → 0 → index → pubkey (không đổi)
    - _Yêu cầu: 3.1, 3.4, 6.2_

  - [ ] 3.2 Refactor deriveShelleyStakingKey()
    - Account key **shared** với payment key derivation
    - _Yêu cầu: 3.2, 3.4, 6.2_

  - [ ] 3.3 Refactor deriveByronKey()
    - Inline logic từ `IcarusKeyDerivation.deriveByronAddressKey()` + cache
    - Verify path: master → 44' → 1815' → 0' → 0 → index → pubkey (không đổi)
    - _Yêu cầu: 3.3, 6.2_

- [ ] 4. Checkpoint — Verify compilation và output consistency
  - `./gradlew :crypto-wallet-lib:compileAndroidMain` — phải pass
  - Existing tests phải pass mà không sửa đổi
  - _Yêu cầu: 6.1, 6.4, 6.5_

- [ ] 5. Cache wordMap trong IcarusKeyDerivation
  - [ ] 5.1 Thêm `WORD_MAP` lazy property, sửa `mnemonicToEntropy()` dùng `WORD_MAP`
    - _Yêu cầu: 4.1, 4.2_

- [ ] 6. Thêm clearCachedKeys() — Security
  - [ ] 6.1 Implement `clearCachedKeys()` với zero-fill tất cả cached ByteArray
    - _Yêu cầu: 7.1, 7.2, 7.3_

- [ ] 7. Testing
  - [ ] 7.1 Test output consistency — Shelley address (bit-for-bit match với implementation cũ)
  - [ ] 7.2 Test output consistency — Byron address
  - [ ] 7.3 Test cache behavior — PBKDF2 chỉ chạy 1 lần qua nhiều operations
  - [ ] 7.4 Test clearCachedKeys() rồi re-derive (phải cho cùng kết quả)
  - [ ] 7.5 Test staking operation dùng cached keys

- [ ] 8. Checkpoint cuối — Full verification
  - Compile pass, existing tests pass, new tests pass
  - Review: không có public API change, không có behavior change
  - _Yêu cầu: 6.1, 6.4, 6.5_

### Lưu ý triển khai (Gotchas)

**#1: deriveChildKey mutates input?**
Không — `deriveChildKey()` trả về Pair mới, không mutate input. Nhưng `var k`, `var cc` bị reassign. Các refactored methods đã đúng vì dùng `val (k0, cc0)` local copy.

**#2: ByteArray trong Map key**
`shelleyPaymentKeyCache` dùng `Pair<Int, Int>` làm key — OK. **KHÔNG** dùng `ByteArray` làm Map key — `ByteArray.equals()` dùng reference equality.

**#3: @Synchronized trên Kotlin/Native**
`@Synchronized` chỉ work JVM/Android. Nếu cần iOS sau này, thay bằng `kotlinx.atomicfu` hoặc `stately-concurrency`.

**#4: Memory footprint**
Tổng cache ~672 bytes per instance (1 Shelley account + 1 Byron). Negligible — không cần LRU hoặc eviction.

---

## Kế hoạch thực hiện tổng thể

### Phase 1 (1-2 ngày) — Cache master key & account key ← ✅ ĐÃ HOÀN THÀNH (2026-04-08)
1. ✅ Cache 3 tầng (master → account → derived key) trong `CardanoManager`
2. ✅ Cache wordMap trong `IcarusKeyDerivation`
3. ✅ `clearCachedKeys()` với zero-fill security
4. **Kết quả: PBKDF2 chỉ chạy 1 lần, lần gọi sau <1ms**

### Phase 2 (2-3 ngày) — BouncyCastle Ed25519
1. Thay `Ed25519Icarus` Android bằng BouncyCastle `Ed25519.scalarMultBaseEncoded()`
2. Viết unit test so sánh output với implementation cũ
3. **Kỳ vọng: giảm thêm ~80% (2s → 0.4s)**

### Phase 3 (1-2 ngày) — BouncyCastle/javax PBKDF2
1. Thêm `expect/actual` cho `pbkdf2SHA512`
2. Android actual dùng `javax.crypto.SecretKeyFactory` hoặc BouncyCastle
3. **Kỳ vọng: giảm thêm ~50% (0.4s → 0.2s)**

### Phase 4 (0.5 ngày) — Minor optimizations
1. BouncyCastle Blake2b
2. CBOR canonical encoding cache

**Tổng effort: ~5-7 ngày cho 10-20x performance improvement.**
