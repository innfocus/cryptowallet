# Gemini CLI Project Context: CryptoWallet

This project is a Kotlin Multiplatform (KMP) library designed to provide crypto wallet functionality for Android and iOS. It supports multiple blockchains including Bitcoin, Ethereum, Arbitrum, Cardano, Ripple, and Centrality.

## Project Overview

*   **Type:** Kotlin Multiplatform Library
*   **Target Platforms:** Android (min SDK 26, target SDK 36), iOS (min version 13)
*   **Main Modules:**
    *   `:crypto-wallet-lib`: The core library implementing blockchain logic.
*   **Key Technologies:**
    *   **Languages:** Kotlin (Multiplatform), Swift (for iOS consumption)
    *   **Networking:** Ktor (KMP), Retrofit (Android specific)
    *   **Serialization:** Kotlinx Serialization, Gson (Android specific)
    *   **Blockchain Libraries:** `fr.acinq.bitcoin` (KMP), `web3j` (Android), `spongycastle` (Android)
    *   **Concurrency:** Kotlinx Coroutines, RxJava (Android specific)

## Architecture

The project follows the standard KMP structure:
*   `crypto-wallet-lib/src/commonMain`: Shared logic for wallet management (`BitcoinManager`, `EthereumManager`), network configurations (`CoinNetwork`), and service interfaces (`BitcoinApiService`, `InfuraRpcService`).
*   `crypto-wallet-lib/src/androidMain`: Android-specific implementations, including a comprehensive `coinkits` package supporting Cardano, Ripple, and Centrality.
*   `crypto-wallet-lib/src/iosMain`: iOS-specific implementations, primarily utilizing the Darwin engine for Ktor.

## Building and Running

### Gradle Commands
*   **Build all:** `./gradlew build`
*   **Build Android:** `./gradlew :crypto-wallet-lib:assembleRelease`
*   **Build iOS XCFramework:** `./gradlew :crypto-wallet-lib:assembleCrypto-wallet-libReleaseXCFramework`
*   **Run Tests:** `./gradlew allTests` or specifically `./gradlew :crypto-wallet-lib:iosX64Test` / `./gradlew :crypto-wallet-lib:testDebugUnitTest`

### Publishing
The project uses the `com.vanniktech.maven.publish` plugin for publishing to Maven Central.
*   **Publish to Local Maven:** `./gradlew publishToMavenLocal`

## Development Conventions

*   **Concurrency:** Use Kotlinx Coroutines for shared logic. Android-specific legacy code may use RxJava.
*   **Networking:** Shared networking should use Ktor. Configuration is managed via the `Config` singleton and `CoinNetwork` class.
*   **Testing:** Common logic is tested in `commonTest` using `kotlin.test`. Mocking for Ktor is available via `ktor-client-mock`.
*   **Wallet Management:**
    *   `BitcoinManager`: Handles Bitcoin wallet creation (Legacy, SegWit, Native SegWit) and transactions.
    *   `EthereumManager`: Handles Ethereum and Arbitrum balances, transactions, and token transfers.
    *   `CoinsManager` (Android): Central manager for Android-specific coin implementations.

## Key Files

*   `crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/wallets/bitcoin/BitcoinManager.kt`: Core Bitcoin logic.
*   `crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/wallets/ethereum/EthereumManager.kt`: Core Ethereum/Arbitrum logic.
*   `crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/CoinNetwork.kt`: Network endpoint and API key management.
*   `crypto-wallet-lib/src/androidMain/kotlin/com/lybia/cryptowallet/coinkits/CoinsManager.kt`: Android-specific coin management.
*   `Package.swift`: Swift Package Manager configuration for iOS.
