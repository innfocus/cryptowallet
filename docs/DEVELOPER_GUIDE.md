# Developer Guide: CryptoWallet

This guide provides technical instructions for developers contributing to the CryptoWallet library.

## 1. Project Structure

- `commonMain`: Shared code for all platforms (KMP).
- `androidMain`: Android-specific implementations.
- `iosMain`: iOS-specific implementations.
- `commonTest`: Unit tests for shared logic.

## 2. Running Tests

Testing is the primary way to ensure correctness across platforms.

### Running All Common Tests
Run this command to execute tests in the `commonTest` source set using the JVM.

```bash
./gradlew :crypto-wallet-lib:allTests
```

### Running Specific Module Tests
- **Android Unit Tests**:
  ```bash
  ./gradlew :crypto-wallet-lib:testDebugUnitTest
  ```
- **iOS Unit Tests (Simulator)**:
  ```bash
  ./gradlew :crypto-wallet-lib:iosX64Test
  ```
- **JVM Tests (Fastest)**:
  ```bash
  ./gradlew :crypto-wallet-lib:jvmTest
  ```

### Running a Specific Test Class
You can target a specific test file by using the `--tests` flag (for JVM/Android):

```bash
./gradlew :crypto-wallet-lib:jvmTest --tests "com.lybia.cryptowallet.wallets.ton.TonManagerTest"
```

## 3. Best Practices for TON Integration

- **Mnemonic**: Standard TON mnemonics are 24 words. Ensure the phrase is split correctly.
- **Address Formats**:
    - **User-friendly**: Non-bounceable is generally preferred for wallet displays to prevent accidental coins bounce.
    - **Testnet**: Always check `Config.shared.getNetwork()` before formatting the address string.
- **SDK**: We use `ton-kotlin:0.5.0`. Refer to `gradle/libs.versions.toml` for version management.

## 4. Integrating New Coins with CoinsManager

When adding a new blockchain, you must integrate it into `CoinsManager` to maintain the unified API for Android.

### Steps to Integrate:
1. **Define the Coin**: Add the new coin to the `ACTCoin` enum (usually in `hdwallet/bip32/ACTCoin.kt`).
2. **Update Supported Coins**: In `CoinsManager.kt`, add the new coin to the `coinsSupported` list and `networkManager` map.
3. **Implement Core Logic**: Extend `CoinsManager` methods for the new coin:
    - `addresses()`: Define how to derive addresses.
    - `getBalance()`: Add a case for the new coin and call its networking helper.
    - `getTransactions()`: Add a case to fetch history.
    - `sendCoin()`: Handle signing and broadcasting.
    - `estimateFee()`: Calculate transaction fees.

### Example: Adding a new coin logic
```kotlin
// In CoinsManager.kt -> getBalance()
when (coin) {
    ACTCoin.NewCoin -> {
        // Call NewCoin specific logic
        getNewCoinBalance(adds.first(), completionHandler)
    }
}
```

## 5. Dependency Management

All project dependencies and their versions MUST be managed using **Gradle Version Catalogs** (`gradle/libs.versions.toml`).

- **No Hardcoded Versions**: Versions must not be hardcoded in `build.gradle.kts` files.
- **Centralized Versions**: All version numbers must be defined in the `[versions]` section of `libs.versions.toml`.
- **Reference by ref**: In the `[libraries]` section, always use `version.ref` to point to the version defined in `[versions]`.
- **Module Names**: Use camelCase for version names and kebab-case for library aliases.

## 5. Troubleshooting

- **Dependency issues**: If `ton-kotlin` is not found, ensure you have refreshed Gradle and that `mavenCentral()` is available in your root `settings.gradle.kts` or `build.gradle.kts`.
- **Address Mismatch**: Different wallet versions (V3R1, V3R2, V4R2) produce different addresses for the same mnemonic. V4R2 is the default in this library.
