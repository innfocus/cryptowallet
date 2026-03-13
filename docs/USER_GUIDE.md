# CryptoWallet Library User Guide

A Kotlin Multiplatform (KMP) library for managing crypto wallets, supporting Bitcoin, Ethereum, Arbitrum, and soon TON.

## 1. Installation

### Android
Add the dependency to your `build.gradle` (Module level):

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib:1.1.6'
}
```

### iOS (Swift Package Manager)
1. In Xcode, go to **File -> Add Packages...**
2. Enter the repository URL: `https://github.com/innfocus/cryptowallet`
3. Select the version or branch you wish to use.

---

## 2. Global Configuration

Before using any wallet manager, you must configure the global network environment (Mainnet or Testnet).

### Kotlin (Android/KMP)
```kotlin
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

// Set to Testnet for development
Config.shared.setNetwork(Network.TESTNET)
```

### Swift (iOS)
```swift
import crypto_wallet_lib

Config.shared.setNetwork(network: .testnet)
```

---

## 3. Working with Bitcoin

The `BitcoinManager` handles address derivation (Legacy, SegWit, Native SegWit) and balance fetching.

### Kotlin
```kotlin
val mnemonic = "your twenty four word mnemonic phrase..."
val btcManager = BitcoinManager(mnemonic)

// Get addresses
val nativeSegWitAddr = btcManager.getNativeSegWitAddress()
val legacyAddr = btcManager.getLegacyAddress()

// Fetch balance (Async)
val balance = btcManager.getBalance(nativeSegWitAddr, null)
println("BTC Balance: $balance")
```

### Swift
```swift
let mnemonic = "..."
let btcManager = BitcoinManager(mnemonics: mnemonic)

let address = btcManager.getNativeSegWitAddress(numberAccount: 0)
// Balance fetching requires handling Kotlin Coroutines (Suspend functions)
```

---

## 4. Working with Ethereum & Arbitrum

`EthereumManager` is a singleton that handles EVM-compatible chains. You need to provide a `CoinNetwork` with your API keys (Infura, Etherscan, etc.).

### Kotlin
```kotlin
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager

val arbitrumNetwork = CoinNetwork(
    name = NetworkName.ARBITRUM,
    apiKeyExplorer = "YOUR_ARBISCAN_API_KEY",
    apiKeyInfura = "YOUR_INFURA_PROJECT_ID"
)

val balance = EthereumManager.shared.getBalance("0xYourAddress...", arbitrumNetwork)
```

---

## 5. API Reference Summary

### `CoinNetwork`
| Parameter | Description |
| :--- | :--- |
| `name` | `NetworkName.BTC`, `.ETHEREUM`, or `.ARBITRUM` |
| `apiKeyExplorer` | API Key for Etherscan/Arbiscan |
| `apiKeyInfura` | Project ID for Infura RPC |
| `apiKeyOwlRacle` | Optional: For gas price estimation |

### `BaseCoinManager` Methods
All managers inherit these common methods:
- `getAddress()`: Returns the derived address.
- `getBalance(address, coinNetwork)`: Returns balance as `Double`.
- `getTransactionHistory(address, coinNetwork)`: Returns a list of transactions.
- `transfer(dataSigned, coinNetwork)`: Broadcasts a signed transaction.

---

## 6. Building XCFramework for iOS
If you are contributing to the library and need to update the iOS framework:

```bash
./gradlew :crypto-wallet-lib:assembleCrypto-wallet-libReleaseXCFramework
```
The output will be located in:
`crypto-wallet-lib/build/XCFramework/release/crypto_wallet_lib.xcframework`
