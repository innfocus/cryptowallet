# CryptoWallet

A Kotlin multiplatform library support web3j and endpoint related with crypto wallet

## Requirements

iOS: Requires a min iOS version of 13
Android: Requires a min SDK version of 26

## Setup on Android

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib-android:1.0.2'
}
```

## Setup on iOS

### Swift Package Manager

Use Xcode to add the project (**File -> Add Package Dependencies**)

## Usage

### CoinsManager (Android Only)

`CoinsManager` is the central entry point for managing multiple coins on Android. It handles HD Wallet derivation, network configuration, and core operations like balance fetching and transactions.

#### Initialize and Recover Wallet

```kotlin
import com.lybia.cryptowallet.coinkits.CoinsManager

// Set mnemonic to recover wallet
CoinsManager.shared.updateMnemonic("your twenty four word mnemonic phrase...")

// Get HD Wallet instance
val wallet = CoinsManager.shared.getHDWallet()
```

#### Get Balance

```kotlin
import com.lybia.cryptowallet.coinkits.BalanceHandle
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin

CoinsManager.shared.getBalance(ACTCoin.Bitcoin, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) {
        if (success) {
            println("Bitcoin Balance: $balance")
        }
    }
})
```

#### Get Transactions

```kotlin
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.TransationData

CoinsManager.shared.getTransactions(ACTCoin.Bitcoin, null, object : TransactionsHandle {
    override fun completionHandler(transactions: Array<TransationData>?, moreParam: JsonObject?, errStr: String) {
        transactions?.forEach { 
            println("Transaction: ${it.hash}, Amount: ${it.amount}")
        }
    }
})
```

### Define Coin Network (Common)


#### Ios

```swift
        import crypto_wallet_lib

        static let arbitrumCoinNetwork = CoinNetwork(name: NetworkName,
                                                 apiKeyExplorer: "",
                                                 apiKeyInfura: "",
                                                 apiKeyOwlRacle: ""
                                                 )
```

## Build Ios XCFramework

### Export XCFramework to binary file

```
    ./gradlew :crypto-wallet-lib:assembleCrypto-wallet-libReleaseXCFramework
```

After export XCFramework, zip the folder exported from **crypto-wallet-lib -> build -> XCFramework -> release -> crypto_wallet_lib.xcframework** and storage (Github Release)

### Calculate Checksum and run cmd

Calculate checksum the zip file and run the command below

```
    swift package compute-checksum crypto_wallet_lib.xcframework.zip
```

Update link binary XCFramework and checksum to Package.swift
