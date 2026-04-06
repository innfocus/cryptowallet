# Phân tích Migration: androidMain → commonMain

> Cập nhật lần cuối: Tháng 3/2026. Migration hoàn tất 100% — tất cả chain managers đã nằm trong commonMain.

## 1. Trạng thái hiện tại

### Tổng quan

Toàn bộ business logic đã được migrate sang `commonMain`. `androidMain` chỉ còn:
- `CoinsManager.kt` — delegate sang `CommonCoinsManager` (backward compat)
- `Platform.android.kt` — platform name
- `HttpClientService.kt` — Ktor OkHttp engine
- Ethereum web3j code — giữ nguyên (không xóa, Android app vẫn dùng song song)

### Đã migrate sang commonMain (hoàn tất)

| androidMain (cũ, @Deprecated) | commonMain (mới) | Ghi chú |
|---|---|---|
| `cardano/model/CarAddress.kt` | `wallets/cardano/CardanoAddress.kt` | Byron + Shelley |
| `cardano/model/CarKeyPair.kt` | `wallets/cardano/CardanoManager.kt` | SLIP-0010 derivation |
| `cardano/model/CarPublicKey.kt` | `wallets/cardano/Ed25519.kt` | ton-kotlin Ed25519 |
| `cardano/model/CarPrivateKey.kt` | `wallets/cardano/CardanoManager.kt` | Tích hợp trong manager |
| `cardano/model/CarDerivation.kt` | `wallets/cardano/CardanoManager.kt` | CIP-1852 paths |
| `cardano/model/CarEnums.kt` | `wallets/cardano/CardanoAddressType.kt` | Enum + Error classes |
| `cardano/model/transaction/Tx*.kt` (7 files) | `wallets/cardano/CardanoTransaction.kt` | Builder pattern |
| `cardano/model/transaction/TxWitnessBuilder.kt` | `wallets/cardano/CardanoWitnessBuilder.kt` | VKey + Bootstrap |
| `cardano/networking/Gada.kt` | `services/CardanoApiService.kt` | Ktor thay Retrofit |
| `cardano/networking/models/*.kt` (4 files) | `models/cardano/CardanoApiModel.kt` | kotlinx-serialization |
| `cardano/helpers/ADAConverter.kt` | `wallets/cardano/CardanoNativeToken.kt` | Native token support |
| _(không có)_ | `wallets/midnight/*` | Midnight hoàn toàn mới |
| _(không có)_ | `utils/cbor/*` | Custom CBOR thay co.nstant.in |
| _(không có)_ | `utils/Bech32.kt, Blake2b.kt, CRC32.kt, SHA3.kt` | Pure Kotlin crypto |
| `hdwallet/bip32/ACTCoin.kt` | `enums/ACTCoin.kt` | Thay BigDecimal → Double |
| `hdwallet/bip32/ACTNetwork.kt` | `enums/ACTNetwork.kt` | Bỏ Java API |
| `hdwallet/bip39/ACTBIP39.kt` | `wallets/hdwallet/bip39/ACTBIP39.kt` | bitcoin-kmp thay Spongy Castle |
| `hdwallet/bip32/ACTPrivateKey.kt` | `wallets/hdwallet/bip32/ACTPrivateKey.kt` | bitcoin-kmp thay Spongy Castle |
| `hdwallet/bip44/ACTHDWallet.kt` | `wallets/hdwallet/bip44/ACTHDWallet.kt` | bitcoin-kmp DeterministicWallet |
| `hdwallet/bip44/ACTAddress.kt` | `wallets/hdwallet/bip44/ACTAddress.kt` | KMP crypto primitives |
| `hdwallet/core/helpers/*` | `utils/*` | Pure Kotlin extensions |
| `bitcoin/networking/Gbtc.kt` | `services/BitcoinApiService.kt` | Ktor thay Retrofit |
| `ripple/networking/Gxrp.kt` | `services/RippleApiService.kt` | Ktor thay Retrofit |
| `ethereum/networking/Geth.kt` | `wallets/ethereum/EthereumManager.kt` | JSON-RPC qua Ktor (InfuraRpcService) |
| `centrality/CentralityNetwork.kt` | `services/CentralityApiService.kt` | Ktor thay Retrofit, suspend thay callbacks |
| `centrality/U8a.kt` | `wallets/centrality/codec/ScaleCodec.kt` | bignum thay java.math.BigInteger |
| `centrality/CennzAddress.kt` | `wallets/centrality/model/CentralityAddress.kt` | fr.acinq.bitcoin.Base58 + Blake2b |
| `centrality/ExtrinsicBase.kt` | `wallets/centrality/model/ExtrinsicBuilder.kt` | Builder pattern, ScaleCodec |
| `centrality/models/*.kt` | `wallets/centrality/model/*.kt` | kotlinx-serialization thay Gson |
| `TransationData.kt` | `models/TransationData.kt` | Long epoch millis thay java.util.Date |
| `MemoData.kt` | `models/MemoData.kt` | Pure Kotlin |
| `models/TokenInfo.kt` | `models/TokenInfo.kt` | Pure Kotlin |
| `models/NFTItem.kt` | `models/NFTItem.kt` | Pure Kotlin |
| _(không có)_ | `wallets/bridge/*` | Bridge mới (simulated) |
| _(không có)_ | `coinkits/ChainManagerFactory.kt` | Factory pattern mới |
| _(không có)_ | `coinkits/CommonCoinsManager.kt` | Unified facade mới |
| _(không có)_ | `errors/WalletError.kt` | Error hierarchy mới |

### Còn lại trong androidMain (không migrate)

| File | Lý do giữ lại |
|---|---|
| `CoinsManager.kt` | Backward compat — delegate sang CommonCoinsManager |
| `ethereum/Geth.kt` + web3j code | Android app vẫn dùng web3j song song |
| `Platform.android.kt` | Platform-specific (expect/actual) |
| `HttpClientService.kt` | Ktor OkHttp engine (expect/actual) |

## 2. Kiến trúc commonMain hiện tại

```
crypto-wallet-lib/src/commonMain/kotlin/com/lybia/cryptowallet/
├── base/                           # Interfaces: IWalletManager, ITokenManager, INFTManager,
│                                   #   IFeeEstimator, IStakingManager, IBridgeManager
├── enums/                          # ACTCoin, Algorithm, Change, NetworkName, ACTNetwork
├── models/                         # TransationData, MemoData, TokenInfo, NFTItem, FeeEstimate
│   ├── bitcoin/                    # Bitcoin-specific models
│   ├── cardano/                    # Cardano API models
│   ├── ripple/                     # Ripple JSON-RPC models
│   └── ton/                        # TON NFT models
├── errors/                         # WalletError, BitcoinError, RippleError, StakingError, BridgeError
├── utils/                          # Blake2b, SHA3, CRC32, Bech32, CBOR, ACTCrypto
├── wallets/
│   ├── hdwallet/bip39/             # ACTBIP39, ACTLanguages (bitcoin-kmp)
│   ├── hdwallet/bip32/             # ACTPrivateKey, ACTPublicKey, ACTKey (bitcoin-kmp)
│   ├── hdwallet/bip44/             # ACTHDWallet, ACTAddress
│   ├── bitcoin/BitcoinManager.kt   # IWalletManager
│   ├── ethereum/EthereumManager.kt # IWalletManager, ITokenManager, INFTManager, IFeeEstimator
│   ├── cardano/                    # CardanoManager (IWalletManager, IStakingManager), CardanoAddress, etc.
│   ├── ton/TonManager.kt          # IWalletManager, ITokenManager, INFTManager, IStakingManager
│   ├── ripple/RippleManager.kt    # IWalletManager
│   ├── midnight/MidnightManager.kt # IWalletManager
│   ├── centrality/                 # CentralityManager (IWalletManager), ScaleCodec, ExtrinsicBuilder
│   └── bridge/                     # CardanoMidnightBridge, EthereumArbitrumBridge, BridgeManagerFactory
├── services/                       # BitcoinApiService, CardanoApiService, RippleApiService,
│                                   #   TonApiService, InfuraRpcService, ExplorerRpcService,
│                                   #   MidnightApiService, CentralityApiService, HttpClientService
├── coinkits/
│   ├── CommonCoinsManager.kt      # Unified facade
│   └── ChainManagerFactory.kt     # Factory pattern
├── CoinNetwork.kt
└── Config.kt
```

## 3. Capability Matrix (hoàn chỉnh)

| Chain | IWalletManager | ITokenManager | INFTManager | IFeeEstimator | IStakingManager | IBridgeManager |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Bitcoin | ✅ | — | — | — | — | — |
| Ethereum | ✅ | ✅ | ✅ | ✅ | — | — |
| Arbitrum | ✅ | ✅ | ✅ | ✅ | — | — |
| Cardano | ✅ | ✅ | — | — | ✅ | — |
| TON | ✅ | ✅ | ✅ | — | ✅ | — |
| Ripple | ✅ | — | — | — | — | — |
| Midnight | ✅ | — | — | — | — | — |
| Centrality | ✅ | — | — | — | — | — |

Bridge pairs: Cardano ↔ Midnight, Ethereum ↔ Arbitrum (simulated responses, chờ API thật).

---

## 4. Hoạt động trên iOS (qua commonMain)

Tất cả tính năng sau đã hoạt động trên iOS qua XCFramework:

- HD Wallet (BIP32/39/44) — tạo mnemonic, derive keys, generate addresses cho tất cả coins
- Bitcoin: address, balance, transactions, transfer
- Ethereum/Arbitrum: address, balance, transactions, transfer, ERC-20 tokens, ERC-721 NFTs, fee estimation
- Cardano: address (Shelley + Byron), balance, transactions, send ADA, native tokens, staking (delegate/undelegate/rewards)
- TON: address, balance, transactions, transfer, Jetton tokens, TEP-62 NFTs, staking (Nominator/Tonstakers/Bemo)
- Ripple: address, balance, transactions, transfer
- Midnight: address, balance, transactions, send tDUST
- Centrality: address (SS58), balance, transactions, sendCoin (full extrinsic flow)
- Bridge: Cardano ↔ Midnight, Ethereum ↔ Arbitrum (simulated)
- HTTP: Ktor Darwin client
- Crypto: Blake2b, SHA3, CRC32, Bech32, CBOR, SCALE — pure Kotlin

### iosMain chỉ có platform-specific files:
```
Platform.ios.kt              — Platform name
services/HttpClientService.ios.kt — Ktor Darwin engine
utils/Utils.ios.kt           — Hex conversion helpers
```

---

## 5. Lịch sử migration theo phase

### Phase 1 — ✅ DONE: Pure Kotlin models + interfaces
1. ✅ `MemoData.kt` → commonMain
2. ✅ `models/TokenInfo.kt` → commonMain
3. ✅ `models/NFTItem.kt` → commonMain
4. ✅ `services/TokenService.kt` → commonMain
5. ✅ `services/NFTService.kt` → commonMain
6. ✅ `ACTCoin` enum → commonMain (thay BigDecimal → Double)
7. ✅ `ACTNetwork`, `Algorithm`, `Change` → commonMain
8. ✅ Core interfaces: `IWalletManager`, `ITokenManager`, `INFTManager`, `IFeeEstimator`, `IStakingManager`, `IBridgeManager`

### Phase 2 — ✅ DONE: HD Wallet (BIP39/32/44)
1. ✅ ACTBIP39 → commonMain (bitcoin-kmp MnemonicCode thay Spongy Castle)
2. ✅ ACTPrivateKey, ACTPublicKey, ACTKey → commonMain (bitcoin-kmp Crypto)
3. ✅ ACTHDWallet, ACTAddress → commonMain
4. ✅ Crypto helpers → commonMain (Base58, Bech32, EIP55, extensions)

### Phase 3 — ✅ DONE: Networking per-coin (Ktor thay Retrofit)
1. ✅ Bitcoin: BitcoinManager + BitcoinApiService (Ktor)
2. ✅ Ripple: RippleManager + RippleApiService (Ktor)
3. ✅ Ethereum: EthereumManager + InfuraRpcService + ExplorerRpcService (Ktor)
4. ✅ TransationData → commonMain (Long epoch millis thay java.util.Date)

### Phase 4 — ✅ DONE: Unified CoinsManager + Cleanup
1. ✅ ChainManagerFactory — factory cho tất cả coins
2. ✅ CommonCoinsManager — unified facade (BTC, ETH, XRP, TON, ADA, Midnight, Arbitrum)
3. ✅ WalletError hierarchy — ConnectionError, InsufficientFunds, InvalidAddress, etc.
4. ✅ androidMain CoinsManager delegate sang CommonCoinsManager
5. ✅ Xóa legacy code: HD Wallet, Bitcoin networking, Ripple networking, Cardano, models

### Phase 5 — ✅ DONE: Centrality migration
1. ✅ ScaleCodec (SCALE encoding) → commonMain (bignum thay java.math.BigInteger)
2. ✅ CentralityAddress (SS58) → commonMain (fr.acinq.bitcoin.Base58 + Blake2b)
3. ✅ Data models → commonMain (kotlinx-serialization thay Gson)
4. ✅ ExtrinsicBuilder → commonMain (builder pattern)
5. ✅ CentralityApiService → commonMain (Ktor thay Retrofit)
6. ✅ CentralityManager (IWalletManager) → commonMain
7. ✅ NetworkName.CENTRALITY + ChainManagerFactory + CommonCoinsManager integration
8. ✅ CoinsManager delegation cho Centrality
9. ✅ Xóa legacy `androidMain/centrality/` directory

### Phase 6 — ✅ DONE: Crypto Wallet Module gaps
1. ✅ BitcoinManager.transfer() — broadcast transaction
2. ✅ EthereumManager NFT operations — getNFTs() + transferNFT() (ERC-721)
3. ✅ TonManager NFT + INFTManager — getNFTs() + transferNFT() (TEP-62)
4. ✅ Staking operations — Cardano delegation + TON (Nominator/Tonstakers/Bemo)
5. ✅ Bridge operations — CardanoMidnightBridge + EthereumArbitrumBridge (simulated)
6. ✅ Property-based tests P1-P13 (Kotest Property)
7. ✅ ChainManagerFactory + CommonCoinsManager wiring cho NFT/Staking/Bridge

---

## 6. Dependencies đã loại bỏ khỏi androidMain sau migration

| Dependency | Trạng thái | Lý do |
|---|---|---|
| `co.nstant.in:cbor` | ✅ Đã xoá | Custom CBOR trong commonMain |
| `com.madgag.spongycastle:*` | ✅ Đã xoá | bitcoin-kmp thay thế |
| `com.google.guava:guava` | ✅ Đã xoá | Pure Kotlin thay thế |
| `io.reactivex.rxjava2:*` | ✅ Đã xoá | Coroutines thay thế |
| `org.whispersystems:curve25519-android` | ✅ Đã xoá | ton-kotlin Ed25519 thay thế |
| `com.squareup.retrofit2:adapter-rxjava2` | ✅ Đã xoá | RxJava đã bị xoá |
| `com.squareup.retrofit2:retrofit` | ✅ Đã xoá | Centrality đã migrate sang Ktor |
| `com.squareup.retrofit2:converter-gson` | ✅ Đã xoá | kotlinx-serialization thay thế |
| `com.google.code.gson:gson` | ✅ Đã xoá | kotlinx-serialization thay thế |
| `com.squareup.okhttp3:okhttp` | ✅ Đã xoá | Ktor thay thế |

### Dependencies vẫn giữ (chỉ cho Ethereum web3j trong androidMain)

| Dependency | Lý do |
|---|---|
| `org.web3j:core` | Ethereum web3j code path trong androidMain (giữ nguyên) |

---

## 7. Testing

Toàn bộ test suite nằm trong `commonTest`, chạy trên Android, iOS, JVM:

- 13 property-based tests (P1-P13) — Kotest Property, 100+ iterations mỗi test
- Unit tests cho tất cả chain managers (mock Ktor client)
- BIP39/BIP32/BIP44 test vectors (TREZOR reference)
- CBOR round-trip, SCALE round-trip, SS58 round-trip
- Cardano address validation, capability matrix consistency
- Bridge status validation, ACTCoin metadata consistency
