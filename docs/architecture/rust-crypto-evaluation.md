# Đánh giá: Sử dụng Rust Crypto Libraries trong KMP

> **Ngày phân tích:** 2026-04-08
> **Trạng thái:** Draft — chờ review
> **Kết luận:** Không khuyến nghị chuyển sang Rust ở thời điểm hiện tại

---

## 1. Thư viện mã hóa hiện tại

### 1.1 Thư viện bên ngoài

| Thư viện | Version | Crypto primitives | Source set |
|---|---|---|---|
| **bitcoin-kmp** (ACINQ) | 0.30.0 | secp256k1, ECDSA, SHA256, RIPEMD160, BIP32/39/44, Hash160, Bech32 | Common |
| **secp256k1-kmp** (ACINQ) | 0.23.0 | secp256k1 elliptic curve, JNI native bindings (Android/JVM) | Common + JNI |
| **krypto** (Korlibs) | 4.0.10 | HMAC-SHA512, SHA256/512, SecureRandom, PBKDF2 | Common |
| **ton-kotlin** | 0.5.0 | Ed25519, TON mnemonic, Cell/BoC encoding | Common |
| **web3j-core-android** | 5.0.2 | BIP44, Credentials, RawTransaction, Keccak-256, EIP-155 signing | Android only |
| **bouncycastle** | 1.83 | Full crypto provider (dependency của web3j) | Android only |
| **bignum** (Ionspin) | 0.3.8 | Arbitrary precision integer (cho Ed25519 field math) | Common |

### 1.2 Pure-Kotlin implementations (tự viết)

| Module | Thuật toán | File |
|---|---|---|
| **Blake2b** | RFC 7693, output 1-64 bytes | `utils/Blake2b.kt` |
| **Keccak / SHA3** | Keccak-256 + SHA3-256 (FIPS 202) | `utils/SHA3.kt` |
| **BIP32** | HD key derivation | `wallets/hdwallet/bip32/` |
| **BIP39** | Mnemonic generation/validation | `wallets/hdwallet/bip39/ACTBIP39.kt` |
| **Bech32** | BIP-173 encoding | `utils/Bech32.kt` |
| **Base58Check** | Bitcoin/XRP address encoding | `utils/Base58Ext.kt` |
| **EIP-55** | Ethereum checksum address | `utils/ACTEIP55.kt` |
| **EthTransactionSigner** | EIP-155 / EIP-1559 signing | `wallets/ethereum/EthTransactionSigner.kt` |
| **XrpTransactionSigner** | XRP signing + SHA512Half | `wallets/ripple/XrpTransactionSigner.kt` |
| **Ed25519 (Cardano)** | Icarus key derivation | `wallets/cardano/Ed25519.kt` |

### 1.3 Signature algorithms theo blockchain

| Blockchain | Signature | Hash | Encoding |
|---|---|---|---|
| Bitcoin | ECDSA (secp256k1) | SHA256, RIPEMD160 | Base58Check, Bech32 |
| Ethereum/Arbitrum | ECDSA (secp256k1) | Keccak-256 | Hex + EIP-55 |
| TON | EdDSA (Ed25519) | SHA256/512 | Base64, TL-B |
| Cardano | EdDSA (Ed25519) | Blake2b, SHA512 | Bech32, CBOR |
| XRP | ECDSA (secp256k1) | SHA512Half | Base58Check |
| Centrality | Sr25519 | — | SS58 |

---

## 2. Rust Crypto Libraries tiềm năng

### 2.1 Mapping thay thế

| Rust crate | Thay thế cho | Hiệu suất ước tính (vs pure Kotlin) |
|---|---|---|
| **ring** / **rustcrypto** | krypto (SHA, HMAC, PBKDF2) | ~5-20x nhanh hơn |
| **k256** (RustCrypto) | secp256k1-kmp | ~2-5x (Android gap nhỏ vì secp256k1-kmp đã dùng JNI) |
| **ed25519-dalek** | ton-kotlin Ed25519, Cardano Ed25519 | ~3-10x nhanh hơn pure Kotlin |
| **blake2** (RustCrypto) | Blake2b.kt (pure Kotlin) | ~10-30x nhanh hơn |
| **sha3** (RustCrypto) | SHA3.kt / Keccak (pure Kotlin) | ~10-30x nhanh hơn |
| **bip32** / **bip39** | Custom BIP32/39 | ~5-15x nhanh hơn |

### 2.2 Phân tích hiệu suất

**Lợi ích lớn nhất:** Các module pure-Kotlin tự viết (Blake2b, Keccak, BIP32/39, Ed25519 Cardano) — Rust cải thiện 10-30x vì Kotlin/JVM không tối ưu cho bit manipulation.

**Lợi ích nhỏ hơn:** secp256k1 trên Android đã dùng JNI native — thay bằng Rust chỉ nhanh hơn ~20-50%. Trên iOS, secp256k1-kmp dùng pure Kotlin nên Rust cải thiện đáng kể hơn (5-10x).

**Ít tác động thực tế:** Crypto operations trong wallet app chạy infrequently (sign transaction, derive key) — user không cảm nhận được sự khác biệt giữa 1ms vs 10ms. Hiệu suất chỉ thực sự quan trọng khi batch operations (scan nhiều address, derive nhiều key).

---

## 3. Phương pháp tích hợp Rust vào KMP

### 3.1 UniFFI (Mozilla) — Recommended

- Rust → tự động generate Kotlin bindings (Android) + Swift bindings (iOS)
- Đã mature, dùng trong Firefox, Mozilla products
- KMP integration: viết `expect/actual`, `actual` Android gọi Kotlin binding, `actual` iOS gọi Swift binding

### 3.2 JNI + C interop — Manual

- Compile Rust → `.so` (Android) / `.a` (iOS)
- Viết JNI wrapper cho Android, cinterop cho Kotlin/Native
- Phức tạp hơn UniFFI nhưng kiểm soát tốt hơn

### 3.3 Cross-compilation targets

- **Android:** `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (via `cargo-ndk`)
- **iOS:** `aarch64-apple-ios`, `aarch64-apple-ios-sim`, `x86_64-apple-ios` (via `cargo-lipo`)

---

## 4. Đánh giá rủi ro

| Rủi ro | Mức độ | Chi tiết |
|---|---|---|
| **Build complexity** | **Cao** | Thêm Rust toolchain vào CI/CD, cross-compile cho 6+ target architectures, tăng build time đáng kể |
| **Binary size** | **Trung bình** | Mỗi Rust library thêm ~500KB-2MB per architecture. Với 4 Android + 3 iOS arch = +10-30MB tổng |
| **Debug difficulty** | **Cao** | Stack traces qua FFI boundary khó đọc, memory issues khó trace |
| **Team skill gap** | **Cao** | Team hiện tại dùng Kotlin — cần học Rust hoặc hire Rust developer để maintain |
| **Memory safety boundary** | **Trung bình** | Rust memory-safe bên trong, nhưng FFI boundary (JNI/cinterop) là unsafe — bugs gây crash không recoverable |
| **Maintenance burden** | **Cao** | 2 build systems (Gradle + Cargo), version sync, OS ABI updates |
| **KMP compatibility** | **Trung bình** | Không có first-class KMP support — phải viết `expect/actual` cho mỗi platform, mất lợi thế code-sharing |

---

## 5. Kết luận

### Không khuyến nghị chuyển sang Rust crypto ở thời điểm hiện tại

**Lý do chính:**

1. **Hiệu suất không phải bottleneck** — Wallet app sign/derive infrequently, user không cảm nhận sự khác biệt. Network latency (API calls) mới là bottleneck thực sự.
2. **secp256k1-kmp đã dùng native** — Library chính đã có JNI bindings, gap hiệu suất với Rust rất nhỏ.
3. **Chi phí vượt quá lợi ích** — Build complexity, team skill, maintenance burden quá lớn cho một wallet library.

### Hướng đi thay thế được khuyến nghị

| Giải pháp | Lợi ích | Effort |
|---|---|---|
| Thay pure-Kotlin Blake2b/Keccak bằng **krypto** hoặc **BouncyCastle KMP** | Tăng hiệu suất, không thêm build complexity | Thấp |
| Dùng **platform-native crypto** qua `expect/actual`: Android → `java.security`/BouncyCastle, iOS → `CommonCrypto`/`CryptoKit` | Performance gần Rust, không thêm toolchain | Trung bình |
| iOS Ed25519: dùng `CryptoKit.Curve25519` qua cinterop | Apple đã tối ưu hardware-level | Thấp |

### Khi nào nên xem xét lại Rust

- Project mở rộng thành SDK/platform xử lý hàng nghìn transactions/giây
- Cần formal verification hoặc audit security ở mức cao nhất
- Team có Rust expertise sẵn có

---

## Tài liệu liên quan

- [Phân tích hiệu suất Cardano](cardano-crypto-performance.md) — Đánh giá chi tiết bottleneck Cardano trên Android và giải pháp cụ thể dùng BouncyCastle thay Rust
