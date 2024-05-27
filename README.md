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

    private var coinNetwork = CoinNetwork(NetworkName,
                                'infuraURL',
                                'infuraTestNetURL',
                                'explorerNetworkUrl',
                                'explorerTestNetUrl',
                                'owlRacleUrl',
                                'explorerAPIKEY',
                                'infuraAPIKey',
                                'owlAPIKey'
    )
```

#### Ios

```swift
        import crypto_wallet_lib

        static let arbitrumCoinNetwork = CoinNetwork(name: NetworkName,
                                                 infuraApiUrl: "",
                                                 infuraTestNetUrl: "",
                                                 explorerUrl: "",
                                                 explorerTestNetUrl: "",
                                                 owlRacleUrl: "",
                                                 apiKeyExplorer: "",
                                                 apiKeyInfura: "",
                                                 apiKeyOwlRacle: ""
                                                 )
```
