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

## 4. Troubleshooting

- **Dependency issues**: If `ton-kotlin` is not found, ensure you have refreshed Gradle and that `mavenCentral()` is available in your root `settings.gradle.kts` or `build.gradle.kts`.
- **Address Mismatch**: Different wallet versions (V3R1, V3R2, V4R2) produce different addresses for the same mnemonic. V4R2 is the default in this library.
