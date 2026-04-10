# Developer Guide: CryptoWallet

Hướng dẫn kỹ thuật cho developer đóng góp vào CryptoWallet KMP library.

> **Tài liệu quan trọng khác:**
> - [Contributing Guide](CONTRIBUTING.md) — Quy tắc code quality, review checklist, design patterns
> - [CommonCoinsManager API](api/common-coins-manager.md) — API reference + Design Patterns (Section 11)
> - [PR Template](../.github/PULL_REQUEST_TEMPLATE.md) — Checklist khi tạo Pull Request

---

## 1. Project Structure

```
crypto-wallet-lib/src/
├── commonMain/    # Shared code cho tất cả platforms (KMP)
│   ├── base/      # Interfaces: IWalletManager, ITokenManager, INFTManager, IStakingManager
│   ├── coinkits/  # CommonCoinsManager (unified facade), ChainManagerFactory
│   ├── wallets/   # Chain managers: TonManager, BitcoinManager, EthereumManager, ...
│   ├── services/  # API clients: TonApiService, InfuraRpcService, ...
│   ├── models/    # Data models, response types
│   └── errors/    # WalletError hierarchy
├── androidMain/   # Android-specific: CoinsManager, TonService (callback bridge)
├── iosMain/       # iOS: Ktor Darwin client config, XCFramework export
├── jvmMain/       # JVM actuals (incomplete — do not target)
└── commonTest/    # Unit tests
```

## 2. Build & Test

### Build Commands (Active targets: Android only)

```bash
# Primary: compile & verify (nhanh nhất)
./gradlew :crypto-wallet-lib:compileAndroidMain

# Full Android build (AAR)
./gradlew :crypto-wallet-lib:assembleAndroidMain

# Publish locally
./gradlew publishToMavenLocal
```

> **KHÔNG chạy:** `jvmTest`, `allTests`, `build` — JVM/iOS missing actual declarations, sẽ fail.

### Test Commands

```bash
# Specific test class
./gradlew :crypto-wallet-lib:jvmTest --tests "com.lybia.cryptowallet.wallets.ton.TonManagerTest"

# iOS simulator test
./gradlew :crypto-wallet-lib:iosSimulatorArm64Test
```

### Test Guidelines
- Mock HTTP calls với Ktor `MockEngine` — **KHÔNG** gọi live API trong unit test
- TON tests dùng 24-word mnemonic (TON format, không phải BIP-39)
- Mỗi chain mới **phải** có test cho address derivation

## 3. Thêm Chain mới — Checklist

Khi thêm blockchain mới, phải hoàn thành **tất cả** bước sau:

### 3.1 Code Implementation

- [ ] **Chain Manager**: Tạo `{Chain}Manager` trong `commonMain/wallets/{chain}/`
  - Extend `BaseCoinManager()`
  - Implement `ITokenManager` nếu hỗ trợ token
  - Implement `INFTManager` nếu hỗ trợ NFT
  - Implement `IStakingManager` nếu hỗ trợ staking
  - Implement `IFeeEstimator` nếu hỗ trợ dynamic fee
- [ ] **API Service**: Tạo `{Chain}ApiService` trong `commonMain/services/`
- [ ] **Models**: Tạo response models trong `commonMain/models/{chain}/`
- [ ] **Errors**: Thêm chain-specific errors nếu cần (`errors/WalletError.kt`)
- [ ] **Enums**: Thêm `NetworkName.{CHAIN}` + `ACTCoin.{Chain}`
- [ ] **CoinNetwork**: Cấu hình endpoints trong `CoinNetwork.kt`
- [ ] **ChainManagerFactory**: Đăng ký trong `createWalletManager()`

### 3.2 CommonCoinsManager Integration

- [ ] **Routing**: Thêm `when` branch cho `sendCoin()`, `sendCoinExact()`
- [ ] **Pagination**: Thêm `when` branch cho `getTransactionHistoryPaginated()` nếu hỗ trợ
- [ ] **Fee**: Thêm `when` branch cho `estimateFee()`
- [ ] **Capability**: Cập nhật `supportsTokens()`, `supportsNFTs()`, `supportsStaking()`
- [ ] **ACCOUNT_CHAINS** hoặc **UTXO_CHAINS**: Thêm vào set tương ứng

### 3.3 Android Integration (nếu cần callback API)

- [ ] **CoinsManager**: Thêm routing trong `addresses()`, `getBalance()`, `sendCoin()`, etc.
- [ ] **Service Bridge**: Tạo `{Chain}Service` nếu cần callback pattern (giống `TonService`)

### 3.4 Testing

- [ ] Unit test cho address derivation
- [ ] Integration test cho API calls (MockEngine)
- [ ] Build passes: `./gradlew :crypto-wallet-lib:compileAndroidMain`

### 3.5 Documentation

- [ ] `docs/chains/{chain}.md` — Kỹ thuật chi tiết
- [ ] `docs/android/{chain}-integration.md` — Hướng dẫn Android
- [ ] `docs/ios/{chain}-integration.md` — Hướng dẫn iOS
- [ ] `docs/api/common-coins-manager.md` — Cập nhật bảng pagination, capability
- [ ] `docs/README.md` — Thêm links

## 4. Design Patterns (Phải tuân thủ)

### 4.1 Result Wrapper
CommonCoinsManager **KHÔNG** throw exception ra ngoài:
```kotlin
suspend fun operation(): SomeResult {
    return try {
        SomeResult(data = ..., success = true)
    } catch (e: Exception) {
        logger.e(e) { "Failed" }
        SomeResult(success = false, error = e.message)
    }
}
```

### 4.2 Config Singleton
```kotlin
// ĐÚNG: đọc network at runtime
val isTestnet = Config.shared.getNetwork() == Network.TESTNET

// SAI: truyền network ở constructor
class BadManager(val network: Network)  // KHÔNG làm như này
```

### 4.3 Coroutine Convention
```kotlin
// CommonCoinsManager: suspend fun
suspend fun getBalance(coin: NetworkName): BalanceResult

// Android bridge: launch + callback trên Main
scope.launch {
    val result = manager.getBalance(addr, coinNetwork)
    withContext(Dispatchers.Main) { callback(result) }
}

// KHÔNG dùng GlobalScope
```

### 4.4 Mnemonic Normalization (NFKD)

**Tất cả mnemonic truyền vào library phải được NFKD normalize trước khi dùng.** Đây là yêu cầu bắt buộc của BIP-39 spec và là nguyên nhân gây bug address lệch giữa iOS và Android với seed tiếng Nhật (dakuten như げ, べ, で có 2 dạng Unicode: composed NFC `U+3052` vs decomposed NFD `U+3051 U+3099`).

- Entry point chính — `CommonCoinsManager` constructor — đã tự động normalize. Team mobile **không cần** tự NFKD trước khi gọi.
- Khi viết chain manager mới nhận `mnemonic: String` trực tiếp, **phải** normalize ngay field declaration:
  ```kotlin
  class FooManager(mnemonic: String) {
      private val mnemonic: String = mnemonic.nfkd()  // hoặc
      private val words = Bip39Language.splitMnemonic(mnemonic.nfkd())
  }
  ```
- Khi gọi `MnemonicCode.toSeed()` (bitcoin-kmp) hoặc `ACTBIP39.deterministicSeedString()` trực tiếp, mnemonic **và passphrase** đều phải NFKD:
  ```kotlin
  MnemonicCode.toSeed(Bip39Language.splitMnemonic(mnemonic.nfkd()), passphrase.nfkd())
  ```
- API: `com.lybia.cryptowallet.utils.nfkd` — expect/actual dùng `java.text.Normalizer` (Android/JVM) và `NSString.decomposedStringWithCompatibilityMapping` (iOS). NFKD là idempotent nên gọi nhiều lần an toàn.
- Regression test: `JapaneseMnemonicNfkdTest` — kiểm tra NFC và NFD cùng 1 mnemonic Nhật phải ra cùng seed và cùng Bitcoin address. Nếu thêm chain mới, thêm test tương tự.

**Sai lầm cần tránh:**
```kotlin
// SAI: MnemonicCode.toSeed trực tiếp mà không NFKD
val seed = MnemonicCode.toSeed(words, "")  // hỏng với mnemonic CJK qua iOS

// SAI: giữ mnemonic raw trong field rồi dùng về sau
class FooManager(private val mnemonic: String)  // NFC/NFD lệch sẽ bug
```

### 4.5 Transaction Signing
```kotlin
// ĐÚNG: validUntil có expiry
validUntil = if (seqno == 0) Int.MAX_VALUE else defaultValidUntil()

// ĐÚNG: seqno error propagated
val seqno = apiService.getSeqno(coin, address)
    ?: throw WalletError.NetworkError("Failed to retrieve seqno")

// SAI: seqno default 0 khi API fail
val seqno = apiService.getSeqno(coin, address) ?: 0  // NGUY HIỂM
```

## 5. Dependency Management

- **Tất cả** versions trong `gradle/libs.versions.toml`
- **KHÔNG** hardcode version trong `build.gradle.kts`
- Dùng `version.ref` trong `[libraries]`
- Tên: `camelCase` cho versions, `kebab-case` cho library aliases

## 6. Troubleshooting

| Vấn đề | Nguyên nhân | Cách fix |
|--------|-------------|----------|
| `ton-kotlin` not found | Gradle chưa refresh | Refresh Gradle, kiểm tra `mavenCentral()` |
| Address khác nhau | Wallet version khác | V4R2 (legacy) vs V5R1 (default) tạo address khác nhau |
| JVM test fail | Missing actual declarations | Không chạy `jvmTest` — dùng `compileAndroidMain` |
| ClassCastException token | Thiếu `ITokenManager` | Chain manager phải implement cả `ITokenManager` lẫn `ITokenAndNFT` |
| Unstake TON fail | Thiếu poolAddress | TON unstake **bắt buộc** truyền `poolAddress` |
