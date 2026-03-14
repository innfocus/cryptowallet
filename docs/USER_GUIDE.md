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
4. Add the `crypto-wallet-lib` library to your target.

### Manual XCFramework Integration
If you prefer not to use SPM, you can generate the XCFramework manually:
```bash
./gradlew :crypto-wallet-lib:assembleCrypto-wallet-libReleaseXCFramework
```
1. Drag the output `.xcframework` from `crypto-wallet-lib/build/XCFramework/release/` into your Xcode project.
2. Under **General -> Frameworks, Libraries, and Embedded Content**, ensure it is set to "Embed & Sign".

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

// Set to Testnet for development
Config.shared.setNetwork(network: .testnet)
```

---

## 3. Working with Managers in Swift (iOS)

The library's shared logic is accessible via platform-specific managers. Since many Kotlin functions are `suspend`, they are bridged to Swift as `async` functions or completion handlers.

### Initializing Managers
```swift
import crypto_wallet_lib

let mnemonic = "your twenty four word mnemonic phrase..."
let btcManager = BitcoinManager(mnemonics: mnemonic)
let ethManager = EthereumManager.shared
let tonManager = TonManager(mnemonics: mnemonic)
```

### Fetching Balances (Async/Await)
For modern Swift projects, use `Task` and `await` to call library functions:

```swift
func fetchBalances() {
    Task {
        do {
            // 1. Bitcoin Balance
            let btcAddress = btcManager.getNativeSegWitAddress(numberAccount: 0)
            let btcBalance = try await btcManager.getBalance(address: btcAddress, coinNetwork: nil)
            print("BTC Balance: \(btcBalance)")

            // 2. TON Balance
            let tonNetwork = CoinNetwork(name: .ton, apiKeyExplorer: "", apiKeyInfura: "YOUR_KEY")
            let tonBalance = try await tonManager.getBalance(address: nil, coinNetwork: tonNetwork)
            print("TON Balance: \(tonBalance)")

            // 3. Ethereum/Arbitrum Balance
            let arbNetwork = CoinNetwork(name: .arbitrum, apiKeyExplorer: "", apiKeyInfura: "YOUR_KEY")
            let ethBalance = try await ethManager.getBalance(address: "0x...", coinNetwork: arbNetwork)
            print("Arbitrum Balance: \(ethBalance)")

        } catch {
            print("Error fetching balances: \(error.localizedDescription)")
        }
    }
}
```

### Transactions and History
```swift
Task {
    let tonNetwork = CoinNetwork(name: .ton, apiKeyExplorer: "", apiKeyInfura: "YOUR_KEY")
    let history = try await tonManager.getTransactionHistory(address: nil, coinNetwork: tonNetwork)

    for tx in history {
        print("Transaction Hash: \(tx)")
    }
}
```

### Error Handling
Kotlin exceptions are mapped to `NSError` in Swift. Always wrap calls in `do-catch` blocks when using `try await`.

---

## 4. Working with Coins via CoinsManager (Android)

`CoinsManager` is the recommended way to interact with all supported coins on Android. It provides a unified interface for core operations.

### Setup and Recovery
```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager

// Recover wallet using mnemonic
CoinsManager.shared.mnemonic = "your mnemonic words..."
```

### Supported Coins
The library supports: `Bitcoin`, `Ethereum`, `Cardano`, `Ripple`, `Centrality`, `TON`.

### Get Balance
```kotlin
import com.lybia.cryptowallet.coinkits.BalanceHandle
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin

CoinsManager.shared.getBalance(ACTCoin.Bitcoin, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        if (success) println("Balance: $balance")
    }
})
```

### Send Coins
```kotlin
import com.lybia.cryptowallet.coinkits.SendCoinHandle
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress

val fromAddress = CoinsManager.shared.firstAddress(ACTCoin.Bitcoin)!!
CoinsManager.shared.sendCoin(
    fromAddress = fromAddress,
    toAddressStr = "recipient_address",
    serAddressStr = "", // Service fee address if applicable
    amount = 0.001,
    networkFee = 0.0001,
    serviceFee = 0.0,
    completionHandler = object : SendCoinHandle {
        override fun completionHandler(transID: String, success: Boolean, errStr: String) {
            if (success) println("Tx ID: $transID")
        }
    }
)

```

## 4. Manual Manager Usage (Legacy/Advanced)

While `CoinsManager` is preferred for Android, individual managers are still available and used for KMP/iOS.

### Working with Bitcoin
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
