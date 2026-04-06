# Cardano Shelley Address Management — Technical Specification

**Version:** 1.0  
**Status:** 🔴 Chưa fix — địa chỉ sinh ra SAI so với Yoroi/Daedalus  
**Scope:** `commonMain` — Shelley base address, enterprise address, reward/staking address  
**Liên quan:** Đọc `docs/CARDANO_BYRON_SPEC.md` trước — Shelley mắc cùng 4 bug pattern với Byron

---

## 1. Overview

Shelley là era hiện tại của Cardano (ra mắt 2020). Toàn bộ ví Cardano hiện đại (Yoroi, Daedalus, Eternl, Nami, Lace) dùng chuẩn Shelley. Địa chỉ Shelley được encode bằng **Bech32** với prefix `addr1` (mainnet) hoặc `addr_test1` (testnet).

Cardano Shelley có 3 loại địa chỉ:

| Loại | Prefix mainnet | Cấu trúc | Dùng để |
|---|---|---|---|
| **Base** | `addr1q...` | payment key hash + staking key hash | Nhận ADA, có delegation |
| **Enterprise** | `addr1v...` | payment key hash only | Nhận ADA, không delegation |
| **Reward/Staking** | `stake1u...` | staking key hash only | Nhận staking rewards |

`getAddress()` trong `CardanoManager` trả về **Base address** (account=0, index=0, mainnet).

---

## 2. Thuật toán đúng — Shelley Icarus (CIP-1852 + CIP-0003)

### 2.1 Tổng quan flow

```
Mnemonic (BIP-39)
    ↓  BIP-39 entropy extraction  (KHÔNG dùng BIP-39 seed)
Entropy (bytes)
    ↓  PBKDF2-HMAC-SHA512(password="", salt=entropy, iter=4096, dkLen=96)
96-byte raw  →  clamp  →  Master Extended Key (64B) + Master Chain Code (32B)
    ↓  Icarus V2 child derivation (ed25519-bip32)
    │
    ├─ Payment key path: m/1852'/1815'/account'/0/index
    │       ↓  publicKeyFromExtended(extKey)  (kL scalar trực tiếp, NO SHA-512)
    │   Payment Verification Key (32B)
    │       ↓  Blake2b-224
    │   paymentKeyHash (28B)
    │
    └─ Staking key path: m/1852'/1815'/account'/2/0
            ↓  publicKeyFromExtended(extKey)
        Staking Verification Key (32B)
            ↓  Blake2b-224
        stakingKeyHash (28B)
    ↓
    createBaseAddress(paymentKeyHash, stakingKeyHash, isTestnet)
    ↓  Bech32(header + paymentKeyHash + stakingKeyHash)
Base Address (Bech32)
```

### 2.2 Master Key

**Giống hệt Byron** — cùng dùng Icarus master key từ `IcarusKeyDerivation.masterKeyFromMnemonic()`.

```
entropy  = BIP-39 decode(mnemonic)
raw      = PBKDF2-HMAC-SHA512(password="", salt=entropy, iterations=4096, dkLen=96)
raw[0]  &= 0xF8   // clamp
raw[31] &= 0x1F
raw[31] |= 0x40
extKey    = raw[0..63]   // 64 bytes: kL || kR
chainCode = raw[64..95]  // 32 bytes
```

### 2.3 Derivation Path (CIP-1852)

```
m / 1852' / 1815' / account' / role / index
     ↑        ↑        ↑        ↑      ↑
  Shelley   ADA    account  chain   address
  purpose  coin   (hard)   index   index
           type           (SOFT)  (SOFT)
```

| Key | Path | Hardened? |
|---|---|---|
| Payment (external) | `m/1852'/1815'/account'/0/index` | 1852', 1815', account' = hardened; **0, index = soft** |
| Change (internal)  | `m/1852'/1815'/account'/1/index` | 1852', 1815', account' = hardened; **1, index = soft** |
| Staking            | `m/1852'/1815'/account'/2/0`    | 1852', 1815', account' = hardened; **2, 0 = soft** |

**⚠️ Quan trọng:** role (0, 1, 2) và index đều là **NON-HARDENED** (soft derivation).  
Đây là điểm khác biệt cốt lõi so với SLIP-0010 (chỉ hỗ trợ hardened).

### 2.4 Child Key Derivation

**Giống hệt Byron** — dùng cùng `IcarusKeyDerivation.deriveChildKey()` (ed25519-bip32 V2).

Khác biệt duy nhất so với Byron là **path prefix** (`1852'` thay vì `44'`) và role/index values.

### 2.5 Public Key và Key Hash

```
// Public key từ extended key (NO SHA-512 — giống Byron)
pubKey = IcarusKeyDerivation.publicKeyFromExtended(extKey)  // 32 bytes

// Key hash cho address encoding
keyHash = Blake2b-224(pubKey)  // 28 bytes
```

### 2.6 Address Encoding

`CardanoAddress.createBaseAddress()` **đã đúng** — không cần thay đổi.

```
// Base address (type 0x00)
header  = 0x00 | networkId   // mainnet = 0x01, testnet = 0x00
payload = header (1B) + paymentKeyHash (28B) + stakingKeyHash (28B)  // 57 bytes total
address = Bech32("addr", payload)   // mainnet
        = Bech32("addr_test", payload)  // testnet

// Reward/Staking address (type 0xE0)
header  = 0xE0 | networkId
payload = header (1B) + stakingKeyHash (28B)  // 29 bytes
address = Bech32("stake", payload)
```

---

## 3. Code hiện tại — Phân tích lỗi

### 3.1 Toàn cảnh `CardanoManager.kt` (vùng Shelley)

```kotlin
// ❌ BUG #1: Dùng BIP-39 seed thay vì entropy
private val seed: ByteArray = MnemonicCode.toSeed(mnemonicWords, "")  // line 37

// ❌ BUG #2 + #3: SLIP-0010 + tất cả index hardened
private fun slip10DeriveEd25519(path: IntArray): Pair<ByteArray, ByteArray> {
    var iBytes = Crypto.hmac512("ed25519 seed".encodeToByteArray(), seed)  // SLIP-0010 master
    // ...child derivation hoàn toàn khác Icarus...
}

private fun derivePaymentKey(account: Int, index: Int): Pair<ByteArray, ByteArray> {
    return slip10DeriveEd25519(intArrayOf(
        hardenedIndex(1852),
        hardenedIndex(1815),
        hardenedIndex(account),
        hardenedIndex(0),      // ❌ role phải là SOFT (0, không phải 0')
        hardenedIndex(index)   // ❌ index phải là SOFT
    ))
}

private fun deriveStakingKey(account: Int): Pair<ByteArray, ByteArray> {
    return slip10DeriveEd25519(intArrayOf(
        hardenedIndex(1852),
        hardenedIndex(1815),
        hardenedIndex(account),
        hardenedIndex(2),    // ❌ role=2 phải là SOFT
        hardenedIndex(0)     // ❌ index=0 phải là SOFT
    ))
}

// ❌ BUG #4: SHA-512 hashes the key trước khi multiply
private fun ed25519PublicKey(privateKey: ByteArray): ByteArray {
    return computeEd25519PublicKey(privateKey)  // gọi Ed25519.publicKey() — SHA-512 bên trong
}

// ✅ ĐÃ ĐÚNG: Address encoding
fun getShelleyAddress(account: Int = 0, index: Int = 0): String {
    val (paymentPriv, _) = derivePaymentKey(account, index)
    val paymentPub = ed25519PublicKey(paymentPriv)          // ❌ sai ở bước này
    val paymentKeyHash = CardanoAddress.hashKey(paymentPub) // ✅ Blake2b-224 đúng

    val (stakingPriv, _) = deriveStakingKey(account)
    val stakingPub = ed25519PublicKey(stakingPriv)          // ❌ sai ở bước này
    val stakingKeyHash = CardanoAddress.hashKey(stakingPub) // ✅ đúng

    return CardanoAddress.createBaseAddress(paymentKeyHash, stakingKeyHash, isTestnet) // ✅ đúng
}
```

### 3.2 Bảng lỗi chi tiết

| # | Vị trí | Code sai | Code đúng | Ảnh hưởng |
|---|---|---|---|---|
| **Bug 1** | `CardanoManager.kt:37` | `MnemonicCode.toSeed(words, "")` | `IcarusKeyDerivation.masterKeyFromMnemonic(words)` | Master key hoàn toàn khác |
| **Bug 2** | `CardanoManager.kt:43-61` | `slip10DeriveEd25519(path)` — HMAC replace | `IcarusKeyDerivation.deriveChildKey()` — HMAC add | Child keys khác |
| **Bug 3a** | `CardanoManager.kt:89` | `hardenedIndex(0)` — role hardened | `0` (soft, non-hardened) | Payment key sai |
| **Bug 3b** | `CardanoManager.kt:90` | `hardenedIndex(index)` — hardened | `index` (soft) | Payment key sai |
| **Bug 3c** | `CardanoManager.kt:99` | `hardenedIndex(2)` — role hardened | `2` (soft) | Staking key sai |
| **Bug 3d** | `CardanoManager.kt:100` | `hardenedIndex(0)` — hardened | `0` (soft) | Staking key sai |
| **Bug 4** | `CardanoManager.kt:67-76` | `Ed25519.publicKey(seed)` — SHA-512 hashes | `IcarusKeyDerivation.publicKeyFromExtended(ext)` | Public key sai |

> `CardanoAddress.createBaseAddress/createRewardAddress` — **✅ không có lỗi**, không cần sửa.

---

## 4. Nguyên nhân gốc

### Bug 1 — MnemonicCode.toSeed vs entropy

`MnemonicCode.toSeed(words, "")` implement BIP-39:
```
seed = PBKDF2-HMAC-SHA512(password = "mnemonic", salt = mnemonic_string, iter=2048, dkLen=64)
```

Trong khi Icarus dùng:
```
seed = PBKDF2-HMAC-SHA512(password = "", salt = entropy_bytes, iter=4096, dkLen=96)
```

Hai thuật toán này có **4 điểm khác nhau**: password, salt, iterations, và output length.

**Tại sao bị nhầm?** Developer dùng BIP-39 seed vì đó là convention của Bitcoin/Ethereum. Cardano Icarus là ngoại lệ — dùng entropy trực tiếp, bỏ qua BIP-39 PBKDF2 step, thay bằng PBKDF2 riêng của mình.

### Bug 2 — SLIP-0010 vs Icarus V2

SLIP-0010 (dùng cho Solana, TON, Polkadot):
```
child_kL = HMAC-SHA512(parent_kR, [0x00 || parent_kL || index_BE])[0..31]
child_kR = HMAC-SHA512(parent_kR, [0x00 || parent_kL || index_BE])[32..63]
```
→ kL được **thay thế** hoàn toàn.

Icarus V2 (dùng cho Cardano):
```
Z       = HMAC-SHA512(cc, [tag || key_material || index_LE])
child_kL = Z[0..27]*8 + parent_kL   // kL được CỘNG VÀO
child_kR = Z[32..63]  + parent_kR
```
→ kL được **cộng dồn**, giữ lại tính chất của group.

**Tại sao bị nhầm?** Cả hai đều dùng HMAC-SHA512 và path notation `m/x'/y'/z'`. Tên gọi "SLIP-0010 Ed25519" hay "ed25519-bip32" dễ nhầm nếu không đọc kỹ spec.

### Bug 3 — Hardened role và index

SLIP-0010 **chỉ hỗ trợ hardened** derivation (index >= 0x80000000) vì với Ed25519 tiêu chuẩn, soft derivation không an toàn. Developer hardcode `hardenedIndex()` cho toàn bộ path.

Icarus **hỗ trợ cả soft** vì thuật toán additive (zL*8 + kL) đảm bảo an toàn cho soft derivation. CIP-1852 yêu cầu role và index là soft để xpub có thể derive public child keys (watch-only wallets).

### Bug 4 — SHA-512 hash

`Ed25519.publicKey(seed)` từ ton-kotlin implement RFC 8032:
```
scalar = SHA-512(seed)[0..31], clamped
A = scalar * B
```

Với Icarus extended key, `kL` **ĐÃ là scalar** (đã được clamp ở bước master key). Nếu gọi `Ed25519.publicKey(kL)` thì nó sẽ SHA-512 hash `kL` rồi mới multiply — cho ra public key hoàn toàn sai.

---

## 5. Cách fix

### 5.1 Thay thế cần thiết trong `CardanoManager.kt`

**Xóa/không dùng:**
- `private val seed` (BIP-39 seed field)
- `slip10DeriveEd25519()` (toàn bộ hàm)
- `ed25519PublicKey()` dùng cho Shelley
- `hardenedIndex()` cho role và index trong Shelley paths

**Thêm mới:**
- `private fun deriveShelleyPaymentKey(account: Int, index: Int)` → dùng `IcarusKeyDerivation.deriveByronPath`-style với path `m/1852'/1815'/account'/0/index`
- `private fun deriveShelleyStakingKey(account: Int)` → path `m/1852'/1815'/account'/2/0`

**Hàm fix mẫu:**
```kotlin
// ✅ Đúng
private fun deriveShelleyPaymentKey(account: Int, index: Int): Triple<ByteArray, ByteArray, ByteArray> {
    val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
    var (k, cc) = masterExt to masterCC
    IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }  // role=0, SOFT
    IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc } // index, SOFT
    return Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)
}

private fun deriveShelleyStakingKey(account: Int): Triple<ByteArray, ByteArray, ByteArray> {
    val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
    var (k, cc) = masterExt to masterCC
    IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
    IcarusKeyDerivation.deriveChildKey(k, cc, 2, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }  // role=2, SOFT
    IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }  // index=0, SOFT
    return Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)
}
```

### 5.2 Không cần thay đổi

- `CardanoAddress.createBaseAddress()` ✅
- `CardanoAddress.createEnterpriseAddress()` ✅
- `CardanoAddress.createRewardAddress()` ✅
- `CardanoAddress.hashKey()` (Blake2b-224) ✅
- `IcarusKeyDerivation` — dùng lại toàn bộ, không thêm code mới

### 5.3 Performance note

Mỗi call `getShelleyAddress()` sẽ traverse toàn bộ 5-level path Icarus, mỗi level có 2 HMAC-SHA512 calls. Tổng ~10 HMAC calls và 1 Ed25519 scalar multiplication. Nên cache kết quả nếu gọi nhiều lần.

---

## 6. Known test vector

```
Mnemonic  : left arena awkward spin damp pipe liar ribbon few husband execute whisper

Expected Shelley base address (account=0, index=0, mainnet):
  addr1qxhj6eqf65yt283f4vwuasfjag7v485g0szrce84hhldd8jrmw23wageh85y8qgjrgxd70k8s44j2wuex329wk5xqfpqu3zkwl

Code hiện tại sinh ra (SAI):
  addr1qymvqhg06hxwhf427ndg5xkkv64k9295y0za2g0a39cz4656mksn358rpx05m5lesajc5qqthc9zqapgqvkwch6g2zwssdc42h
```

Test case đã có trong `CardanoManagerTest.kt::knownVector_shelleyAddress_account0_index0` — đang `@Ignore`.  
Sau khi fix, bỏ `@Ignore` để test pass.

---

## 7. Lệnh chạy test (sau khi fix)

```bash
# Chạy known-vector Shelley
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.CardanoManagerTest.knownVector_shelleyAddress_account0_index0"

# Chạy toàn bộ CardanoManager tests
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.CardanoManagerTest"

# Chạy toàn bộ Cardano tests
./gradlew :crypto-wallet-lib:cleanJvmTest :crypto-wallet-lib:jvmTest \
  --tests "com.lybia.cryptowallet.cardano.*"
```

---

## 8. Quy tắc phòng tránh lỗi

| ❌ Sai | ✅ Đúng |
|---|---|
| `MnemonicCode.toSeed()` cho Cardano Shelley | `IcarusKeyDerivation.masterKeyFromMnemonic()` |
| `slip10DeriveEd25519` cho path Shelley | `IcarusKeyDerivation.deriveChildKey()` |
| `hardenedIndex(0)` cho role | `0` (soft) |
| `hardenedIndex(index)` cho address index | `index` (soft) |
| `Ed25519.publicKey(kL)` với Icarus key | `IcarusKeyDerivation.publicKeyFromExtended(extKey)` |
| Nhầm path Shelley với Byron | Byron=`m/44'/1815'`, Shelley=`m/1852'/1815'` |

---

## 9. Tài liệu tham khảo

| Tài liệu | Link | Nội dung |
|---|---|---|
| **CIP-1852** — HD Wallets for Cardano | https://github.com/cardano-foundation/CIPs/blob/master/CIP-1852/CIP-1852.md | Chuẩn derivation path Shelley, phân biệt role 0/1/2 |
| **CIP-0003** — Icarus master key | https://github.com/cardano-foundation/CIPs/blob/master/CIP-0003/Icarus.md | PBKDF2 từ entropy, clamp algorithm |
| **CIP-0019** — Cardano address format | https://github.com/cardano-foundation/CIPs/blob/master/CIP-0019/README.md | Shelley address structure, header bytes, Bech32 encoding |
| **ed25519-bip32** | https://input-output-hk.github.io/adrestia/cardano-wallet/concepts/master-key-generation | ed25519-bip32 V2 child derivation (zL\*8+kL) |
| **SLIP-0010** | https://github.com/satoshilabs/slips/blob/master/slip-0010.md | Chuẩn SLIP-0010 (KHÔNG dùng cho Cardano) |
| **BIP-44** | https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki | HD wallet path convention (coin type 1815 = Cardano) |
| **cardano-addresses CLI** | https://github.com/IntersectMBO/cardano-addresses | Tool để verify địa chỉ sinh ra |
