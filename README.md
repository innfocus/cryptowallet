# CryptoWallet

A Kotlin multiplatform library support web3j and endpoint related with crypto wallet

## Requirements

iOS: Requires a min iOS version of 13
Android: Requires a min SDK version of 24

## Setup on Android

```groovy
dependencies {
    implementation 'io.github.innfocus:crypto-wallet-lib-android:1.0.1'
}
```

## Setup on iOS

### Swift Package Manager

Use Xcode to add the project (**File -> Add Package Dependencies**)

## Usage

### Define Coin Network

#### Android

```kotlin

    import com.lybia.cryptowallet.CoinNetwork
    import com.lybia.cryptowallet.enums.NetworkName

    private var coinNetwork = CoinNetwork(
                                name = NetworkName,
                                apiKeyExplorer = "explorerAPIKey",
                                apiKeyInfura = "infuraAPIKey",

    )
```

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
