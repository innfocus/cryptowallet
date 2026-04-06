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
CoinsManager.shared.updateMnemonic("your mnemonic words...")
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

## 5. Token (Jetton / ERC-20) Operations — Android

Token operations are exposed through `ITokenManager`, which `CoinsManager` implements. The current implementation supports **TON Jettons**; other chains follow the same interface.

### Get Token Balance

Fetches the Jetton balance **and** metadata (name, symbol, decimals, image) in one call.

```kotlin
import com.lybia.cryptowallet.coinkits.models.TokenInfo
import com.lybia.cryptowallet.coinkits.services.TokenBalanceHandle

val walletAddress  = CoinsManager.shared.firstAddress(ACTCoin.TON)?.rawAddressString() ?: return
val jettonMaster   = "EQBlqsm144Dq6SjbPI4jjZvA1hqTIP3CvHovbIfW_t-SCALE" // e.g. SCALE token

CoinsManager.shared.getTokenBalance(
    coin              = ACTCoin.TON,
    address           = walletAddress,
    contractAddress   = jettonMaster,
    completionHandler = object : TokenBalanceHandle {
        override fun completionHandler(tokenInfo: TokenInfo?, success: Boolean, errStr: String) {
            if (success && tokenInfo != null) {
                println("Name:    ${tokenInfo.name}")
                println("Symbol:  ${tokenInfo.symbol}")
                println("Balance: ${tokenInfo.balance}")
                println("Image:   ${tokenInfo.imageUrl}")
            }
        }
    }
)
```

### Get Token Transaction History

Returns the same `TransationData` format used by native coin history.

```kotlin
import com.lybia.cryptowallet.coinkits.services.TokenTransactionsHandle

CoinsManager.shared.getTokenTransactions(
    coin              = ACTCoin.TON,
    address           = walletAddress,
    contractAddress   = jettonMaster,
    completionHandler = object : TokenTransactionsHandle {
        override fun completionHandler(transactions: Array<TransationData>?, errStr: String) {
            transactions?.forEach { tx ->
                println("${if (tx.isSend) "Sent" else "Received"} ${tx.amount} — ${tx.iD}")
            }
        }
    }
)
```

### Send Token

```kotlin
import com.lybia.cryptowallet.coinkits.services.SendTokenHandle

CoinsManager.shared.sendToken(
    coin              = ACTCoin.TON,
    toAddress         = "UQ...",
    contractAddress   = jettonMaster,
    amount            = 10.0,        // human-readable amount
    decimals          = 9,           // 9 for most Jettons, 6 for USDT
    memo              = "payment",   // optional comment
    completionHandler = object : SendTokenHandle {
        override fun completionHandler(txHash: String, success: Boolean, errStr: String) {
            if (success) println("Sent! txHash: $txHash")
            else         println("Error: $errStr")
        }
    }
)
```

---

## 6. NFT Operations — Android

NFT operations are exposed through `INFTManager`, which `CoinsManager` implements. The current implementation supports **TON NFTs** (TEP-62) fetched via the Toncenter v3 REST API.

### Get NFTs Owned by Address

```kotlin
import com.lybia.cryptowallet.coinkits.models.NFTItem
import com.lybia.cryptowallet.coinkits.services.NFTListHandle

val walletAddress = CoinsManager.shared.firstAddress(ACTCoin.TON)?.rawAddressString() ?: return

CoinsManager.shared.getNFTs(
    coin              = ACTCoin.TON,
    address           = walletAddress,
    completionHandler = object : NFTListHandle {
        override fun completionHandler(nfts: Array<NFTItem>?, errStr: String) {
            nfts?.forEach { nft ->
                println("NFT: ${nft.name}")
                println("  Address:    ${nft.address}")
                println("  Collection: ${nft.collectionAddress}")
                println("  Index:      ${nft.index}")
                println("  Image:      ${nft.imageUrl}")
                nft.attributes?.forEach { (trait, value) ->
                    println("  $trait: $value")
                }
            }
        }
    }
)
```

### Transfer NFT

Sends a TEP-62 NFT transfer message. The wallet must hold enough TON to cover gas (~0.1 TON).

```kotlin
import com.lybia.cryptowallet.coinkits.services.NFTTransferHandle

CoinsManager.shared.transferNFT(
    coin              = ACTCoin.TON,
    nftAddress        = "EQ...",    // address of the NFT item contract
    toAddress         = "UQ...",    // recipient
    memo              = null,       // optional forward comment
    completionHandler = object : NFTTransferHandle {
        override fun completionHandler(txHash: String, success: Boolean, errStr: String) {
            if (success) println("NFT transferred! txHash: $txHash")
            else         println("Error: $errStr")
        }
    }
)
```

---

## 7. Architecture Overview — Token & NFT

The feature is structured in three layers to keep concerns separate and allow new chains to be added with minimal changes.

```
ITokenManager / INFTManager          ← interfaces in CoinsManager
        │
        │  dispatch by ACTCoin
        ▼
    TonService                        ← androidMain, bridges coroutines → callbacks
        │
        │  suspend calls
        ▼
    TonManager (commonMain / KMP)     ← sign + broadcast logic
        │
        ▼
    TonApiService                     ← Ktor HTTP: Toncenter v2 JSON-RPC + v3 REST
```

**Adding a new chain (e.g. Ethereum ERC-20):**
1. Create `EthService : TokenService, NFTService` in `coinkits/eth/`.
2. Add `ACTCoin.Ethereum -> ethService.xxx(...)` inside `CoinsManager`.

No changes to interfaces or models are required.

---

## 8. Manual Manager Usage (Legacy / Advanced)

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

## 8b. Working with Ethereum & Arbitrum

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

## 9. API Reference Summary

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

## 10. Building XCFramework for iOS
If you are contributing to the library and need to update the iOS framework:

```bash
./gradlew :crypto-wallet-lib:assembleCrypto-wallet-libReleaseXCFramework
```
The output will be located in:
`crypto-wallet-lib/build/XCFramework/release/crypto_wallet_lib.xcframework`
