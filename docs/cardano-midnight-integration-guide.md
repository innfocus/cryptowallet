# Hướng dẫn tích hợp CommonCoinsManager — Multi-Chain Wallet

Tài liệu này hướng dẫn cách sử dụng `CommonCoinsManager` trong `commonMain` để tích hợp tất cả các coin được hỗ trợ vào ứng dụng ví Android và iOS. Bao gồm hướng dẫn migration cho app cũ đang dùng `androidMain`.

## Tổng quan

`CommonCoinsManager` là API thống nhất cho tất cả các coin, nằm hoàn toàn trong `commonMain`, hoạt động trên Android, iOS và JVM. Tất cả chain managers đã được migrate từ `androidMain` sang `commonMain`.

### Coins được hỗ trợ

| Coin | NetworkName | transfer | Token | NFT | Fee est. | Staking | Bridge |
|---|---|---|---|---|---|---|---|
| Bitcoin (BTC) | `BTC` | ✅ | — | — | — | — | — |
| Ethereum (ETH) | `ETHEREUM` | ✅ | ✅ ERC-20 | ✅ ERC-721 | ✅ | — | ✅ ↔ Arbitrum |
| Arbitrum | `ARBITRUM` | ✅ | ✅ ERC-20 | ✅ ERC-721 | ✅ | — | ✅ ↔ Ethereum |
| Ripple (XRP) | `XRP` | ✅ | — | — | — | — | — |
| TON | `TON` | ✅ | ✅ Jetton | ✅ TEP-62 | — | ✅ | — |
| Cardano (ADA) | `CARDANO` | ✅ | ✅ Native | — | — | ✅ | ✅ ↔ Midnight |
| Midnight (tDUST) | `MIDNIGHT` | ✅ | — | — | — | — | ✅ ↔ Cardano |
| Centrality (CENNZ) | `CENTRALITY` | ✅ | — | — | — | — | — |

Tất cả coin đều hỗ trợ: `getAddress`, `getBalance`, `getTransactionHistory`.

---

## Cấu hình ban đầu

### 1. Chọn network (Mainnet / Testnet)

```kotlin
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network

Config.shared.setNetwork(Network.MAINNET) // hoặc Network.TESTNET
```

### 2. Khởi tạo CommonCoinsManager

```kotlin
import com.lybia.cryptowallet.coinkits.CommonCoinsManager

val mnemonic = "your twelve word mnemonic phrase here ..."
val manager = CommonCoinsManager(mnemonic = mnemonic)
```

### 3. Khởi tạo với custom API service (tuỳ chọn)

```kotlin
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.CardanoApiProvider
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.NetworkName

val cardanoApi = CardanoApiService(
    baseUrl = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl(),
    apiKey = "your-blockfrost-project-id",
    provider = CardanoApiProvider.BLOCKFROST
)

val manager = CommonCoinsManager(
    mnemonic = mnemonic,
    cardanoApiService = cardanoApi
)
```

---

## Unified API — Tất cả coin

### Lấy địa chỉ

```kotlin
val btcAddress = manager.getAddress(NetworkName.BTC)           // "bc1q..."
val ethAddress = manager.getAddress(NetworkName.ETHEREUM)       // "0x..."
val arbAddress = manager.getAddress(NetworkName.ARBITRUM)       // "0x..." (same as ETH)
val xrpAddress = manager.getAddress(NetworkName.XRP)            // "r..."
val tonAddress = manager.getAddress(NetworkName.TON)             // "EQ..." / "UQ..."
val adaAddress = manager.getAddress(NetworkName.CARDANO)         // "addr1q..."
val midAddress = manager.getAddress(NetworkName.MIDNIGHT)        // "midnight1q..."
val cenAddress = manager.getAddress(NetworkName.CENTRALITY)      // "cX..." (SS58)
```

### Lấy số dư

```kotlin
launch {
    val result = manager.getBalance(NetworkName.BTC)
    // BalanceResult(balance: Double, success: Boolean, error: String?)
    if (result.success) println("BTC: ${result.balance}")
}
```

### Lấy lịch sử giao dịch

```kotlin
launch {
    val txs = manager.getTransactionHistory(NetworkName.ETHEREUM)
}
```

### Transfer (generic — gửi signed transaction)

```kotlin
launch {
    val result = manager.transfer(NetworkName.ETHEREUM, signedTxHex)
    // SendResult(txHash: String, success: Boolean, error: String?)
    if (result.success) println("TX: ${result.txHash}")
}
```

---

## Capability Checking

```kotlin
// Token operations (ERC-20, Jetton, Cardano native token)
manager.supportsTokens(NetworkName.ETHEREUM)    // true
manager.supportsTokens(NetworkName.TON)         // true
manager.supportsTokens(NetworkName.CARDANO)     // true
manager.supportsTokens(NetworkName.BTC)         // false

// NFT operations (ERC-721, TEP-62)
manager.supportsNFTs(NetworkName.ETHEREUM)      // true
manager.supportsNFTs(NetworkName.TON)           // true
manager.supportsNFTs(NetworkName.CARDANO)       // false

// Fee estimation (gas price, gas limit)
manager.supportsFeeEstimation(NetworkName.ETHEREUM)  // true
manager.supportsFeeEstimation(NetworkName.ARBITRUM)   // true
manager.supportsFeeEstimation(NetworkName.BTC)        // false

// Staking
manager.supportsStaking(NetworkName.CARDANO)    // true
manager.supportsStaking(NetworkName.TON)        // true
manager.supportsStaking(NetworkName.ETHEREUM)   // false

// Bridge
manager.supportsBridge(NetworkName.CARDANO, NetworkName.MIDNIGHT)   // true
manager.supportsBridge(NetworkName.ETHEREUM, NetworkName.ARBITRUM)   // true
manager.supportsBridge(NetworkName.BTC, NetworkName.ETHEREUM)        // false
```

---

## Token Operations

Hỗ trợ: Ethereum (ERC-20), Arbitrum (ERC-20), TON (Jetton), Cardano (Native Token).

```kotlin
launch {
    // Generic token balance
    val balance = manager.getTokenBalance(
        NetworkName.ETHEREUM, "0xWalletAddress", "0xContractAddress"
    )

    // Generic token send
    val result = manager.sendToken(NetworkName.ETHEREUM, signedTokenTxHex)

    // Cardano native token (specific overload)
    val adaTokenBalance = manager.getTokenBalance(
        address = "addr1q...",
        policyId = "a".repeat(56),
        assetName = "48454c4c4f"
    )
    val adaTokenSend = manager.sendToken(
        toAddress = "addr1q...",
        policyId = "a".repeat(56),
        assetName = "48454c4c4f",
        amount = 100L,
        fee = 200_000L
    )
}
```

---

## NFT Operations

Hỗ trợ: Ethereum (ERC-721), Arbitrum (ERC-721), TON (TEP-62).

```kotlin
launch {
    // List NFTs
    val nfts = manager.getNFTs(NetworkName.ETHEREUM, "0xWalletAddress")
    // List<NFTItem>?

    // Transfer NFT
    val result = manager.transferNFT(
        NetworkName.TON, "EQNFTAddress", "EQToAddress", "optional memo"
    )
}
```

---

## Staking Operations

Hỗ trợ: Cardano (delegation), TON (Nominator Pool, Tonstakers, Bemo).

```kotlin
launch {
    // Stake
    val stakeResult = manager.stake(
        NetworkName.CARDANO, amount = 5_000_000L, poolAddress = "pool1..."
    )

    // Unstake
    val unstakeResult = manager.unstake(NetworkName.CARDANO, amount = 5_000_000L)

    // Query rewards
    val rewards = manager.getStakingRewards(NetworkName.CARDANO)
    if (rewards.success) println("Rewards: ${rewards.balance} ADA")

    // Query staking balance
    val stakingBal = manager.getStakingBalance(
        NetworkName.TON, poolAddress = "EQPoolAddress"
    )
}
```

> TON staking: `unstake()` trả về `UnsupportedOperation` vì TON staking protocols không hỗ trợ unstake trực tiếp.

---

## Bridge Operations

Hỗ trợ: Cardano ↔ Midnight, Ethereum ↔ Arbitrum. Hiện dùng simulated responses — chờ API thật sẵn sàng.

```kotlin
launch {
    // Bridge ADA → tDUST
    val bridgeResult = manager.bridgeAsset(
        fromChain = NetworkName.CARDANO,
        toChain = NetworkName.MIDNIGHT,
        amount = 5_000_000L
    )

    // Query bridge status
    val status = manager.getBridgeStatus(bridgeResult.txHash)
    // "pending" | "confirming" | "completed" | "failed"
}
```

---

## Chi tiết từng Coin

### Bitcoin (BTC)

```kotlin
val address = manager.getAddress(NetworkName.BTC)
// Native SegWit: "bc1q..." (mainnet) / "tb1q..." (testnet)

launch {
    val balance = manager.getBalance(NetworkName.BTC)
    val txs = manager.getTransactionHistory(NetworkName.BTC)
    val send = manager.transfer(NetworkName.BTC, signedTxHex)
}
```

### Ethereum / Arbitrum

```kotlin
launch {
    val balance = manager.getBalance(NetworkName.ETHEREUM)
    val send = manager.transfer(NetworkName.ETHEREUM, signedTxHex)

    // ERC-20 token
    val tokenBal = manager.getTokenBalance(NetworkName.ETHEREUM, wallet, contract)
    val tokenSend = manager.sendToken(NetworkName.ETHEREUM, signedTokenTx)

    // ERC-721 NFT
    val nfts = manager.getNFTs(NetworkName.ETHEREUM, wallet)
    val nftSend = manager.transferNFT(NetworkName.ETHEREUM, nftAddr, toAddr)
}
```

### Ripple (XRP)

```kotlin
launch {
    val balance = manager.getBalance(NetworkName.XRP)
    // Balance đã chia 1,000,000 drops → XRP
    val send = manager.transfer(NetworkName.XRP, signedTxBlob)
}
```

### TON

```kotlin
launch {
    val balance = manager.getBalance(NetworkName.TON)
    val send = manager.transfer(NetworkName.TON, signedBocBase64)

    // Jetton token (TEP-74)
    val jettonBal = manager.getTokenBalance(NetworkName.TON, wallet, jettonMaster)

    // NFT (TEP-62)
    val nfts = manager.getNFTs(NetworkName.TON, wallet)
    val nftSend = manager.transferNFT(NetworkName.TON, nftAddr, toAddr, "memo")

    // Staking
    val stake = manager.stake(NetworkName.TON, amount, poolAddr)
    val stakingBal = manager.getStakingBalance(NetworkName.TON, poolAddress = poolAddr)
}
```

### Cardano (ADA)

```kotlin
// Addresses
val shelley = manager.getAddress(NetworkName.CARDANO)  // "addr1q..." / "addr_test1q..."

// Cardano-specific helpers
launch {
    val balance = manager.getCardanoBalance()
    val txs = manager.getCardanoTransactions()
    val send = manager.sendCardano(
        toAddress = "addr1q...",
        amountLovelace = 2_000_000L,  // 2 ADA
        fee = 200_000L
    )

    // Native token
    val tokenBal = manager.getTokenBalance(
        address = "addr1q...", policyId = "abc...", assetName = "48454c4c4f"
    )
    val tokenSend = manager.sendToken(
        toAddress = "addr1q...", policyId = "abc...",
        assetName = "48454c4c4f", amount = 100L, fee = 200_000L
    )

    // Staking
    val stake = manager.stake(NetworkName.CARDANO, 5_000_000L, "pool1...")
    val rewards = manager.getStakingRewards(NetworkName.CARDANO)
}
```

Cardano-specific utilities:

```kotlin
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType

// Validate address
val isValid = CardanoAddress.isValidAddress("addr1q...")
val isByron = CardanoAddress.isValidByronAddress("Ae2tdPwUPEZ...")
val isShelley = CardanoAddress.isValidShelleyAddress("addr1q...")

// Detect address type
val type = CardanoAddress.getAddressType("addr1q...")
// BYRON, SHELLEY_BASE, SHELLEY_ENTERPRISE, SHELLEY_REWARD, UNKNOWN
```

### Midnight Network (tDUST)

```kotlin
launch {
    val balance = manager.getMidnightBalance()
    val txs = manager.getMidnightTransactions()
    val send = manager.sendMidnight(toAddress = "midnight1q...", amount = 1_000_000L)
}
```

### Centrality / CennzNet (CENNZ)

```kotlin
launch {
    val balance = manager.getCentralityBalance()
    val txs = manager.getCentralityTransactions()

    // sendCoin flow (full orchestration: getRuntimeVersion → build extrinsic → sign → submit)
    val send = manager.sendCentrality(
        fromAddress = "cX...",
        toAddress = "cX...",
        amount = 1.5,       // đơn vị CENNZ (tự chia BASE_UNIT=10000)
        assetId = 1
    )
}
```

---

## Đơn vị tiền tệ

| Coin | Đơn vị nhỏ nhất | Hệ số | `getBalance()` trả về |
|---|---|---|---|
| BTC | satoshi | 1 BTC = 100,000,000 sat | BTC |
| ETH | wei | 1 ETH = 10^18 wei | ETH |
| XRP | drops | 1 XRP = 1,000,000 drops | XRP |
| TON | nanoton | 1 TON = 10^9 nanoton | TON |
| ADA | lovelace | 1 ADA = 1,000,000 lovelace | ADA |
| tDUST | — | 1 tDUST = 1,000,000 đơn vị | tDUST |
| CENNZ | — | 1 CENNZ = 10,000 đơn vị | CENNZ |

> `getBalance()` trả về đơn vị lớn (human-readable). Các helper methods (`sendCardano`, `sendMidnight`) nhận đơn vị nhỏ nhất.

---

## Result Types

```kotlin
data class BalanceResult(val balance: Double, val success: Boolean, val error: String? = null)
data class SendResult(val txHash: String, val success: Boolean, val error: String? = null)
data class TokenBalanceResult(val balance: Long, val success: Boolean, val error: String? = null)
```

---

## Xử lý lỗi

### WalletError (chung cho tất cả chain)

| Error | Mô tả |
|---|---|
| `ConnectionError` | Lỗi kết nối network (endpoint + cause) |
| `InsufficientFunds` | Không đủ số dư |
| `InvalidAddress` | Địa chỉ không hợp lệ |
| `TransactionRejected` | Network từ chối giao dịch |
| `UnsupportedOperation` | Operation không hỗ trợ cho chain này |

### Chain-specific errors

| Chain | Error Class | Subclasses |
|---|---|---|
| Bitcoin | `BitcoinError` | `InsufficientUtxos`, `InvalidTransaction` |
| Cardano | `CardanoError` | `InvalidByronAddress`, `InvalidShelleyAddress`, `InsufficientTokens`, `ApiError` |
| Midnight | `MidnightError` | `InsufficientTDust`, `ConnectionError`, `TransactionRejected`, `InvalidAddress` |
| Ripple | `RippleError` | `AccountNotFound`, `TransactionFailed` |
| Centrality | `CentralityError` | `RpcError`, `InvalidSS58Address`, `SigningFailed`, `ExtrinsicSubmitFailed` |
| Staking | `StakingError` | `PoolNotFound`, `InsufficientStakingBalance`, `DelegationAlreadyActive`, `NoDelegationActive` |
| Bridge | `BridgeError` | `UnsupportedBridgePair`, `BridgeServiceUnavailable`, `BridgeTransactionFailed` |

> `CommonCoinsManager` luôn catch exceptions và wrap thành `BalanceResult` / `SendResult` với `success=false`. Exceptions không leak ra caller.

---

## Tích hợp Android — CoinsManager Delegation

`CoinsManager` (androidMain) đã delegate tất cả coin operations sang `CommonCoinsManager` qua `commonManager`. Ethereum web3j code giữ nguyên trong androidMain.

### Coins đã delegate hoàn toàn sang commonMain

| Coin | Operations delegated |
|---|---|
| Bitcoin | `getAddress`, `getBalance`, `getTransactionHistory`, `transfer` |
| Ethereum | `getAddress`, `getBalance`, `getTransactionHistory`, `transfer`, token, NFT, fee |
| Ripple | `getAddress`, `getBalance`, `getTransactionHistory`, `transfer` |
| TON | `getAddress`, `getBalance`, `getTransactionHistory`, `transfer`, token, NFT, staking |
| Cardano | `getAddress`, `getBalance`, `getTransactionHistory`, `sendCardano`, staking, native token |
| Midnight | `getAddress`, `getBalance`, `getTransactionHistory`, `sendMidnight` |
| Centrality | `getAddress`, `getBalance`, `getTransactionHistory`, `sendCentrality` |

### Pattern trong CoinsManager

```kotlin
// CoinsManager.kt (androidMain) — delegation pattern
class CoinsManager private constructor() {
    private val commonManager: CommonCoinsManager by lazy {
        CommonCoinsManager(mnemonic = mnemonicString)
    }

    // Ví dụ: getBalance cho Cardano
    fun getBalance(coin: ACTCoin, ..., completionHandler: ...) {
        when (coin) {
            ACTCoin.Cardano -> {
                launch {
                    val result = commonManager.getBalance(NetworkName.CARDANO, address)
                    withContext(Dispatchers.Main) {
                        completionHandler.completionHandler(result.balance, result.success)
                    }
                }
            }
            // ... tương tự cho các coin khác
        }
    }
}
```

---

## Tích hợp iOS (Swift)

Module nằm trong `commonMain`, export qua XCFramework:

```swift
import crypto_wallet_lib

let mnemonic = "your twelve word mnemonic ..."
let manager = CommonCoinsManager(mnemonic: mnemonic)

// Lấy địa chỉ (synchronous)
let btcAddress = manager.getAddress(coin: .btc)
let ethAddress = manager.getAddress(coin: .ethereum)
let adaAddress = manager.getAddress(coin: .cardano)
let tonAddress = manager.getAddress(coin: .ton)
let cenAddress = manager.getAddress(coin: .centrality)

// Lấy số dư (async)
Task {
    let result = try await manager.getBalance(coin: .cardano, address: nil)
    if result.success { print("ADA: \(result.balance)") }
}

// Transfer
Task {
    let result = try await manager.transfer(coin: .ethereum, dataSigned: signedTxHex)
    if result.success { print("TX: \(result.txHash)") }
}

// Staking
Task {
    let stakeResult = try await manager.stake(coin: .cardano, amount: 5_000_000, poolAddress: "pool1...")
    let rewards = try await manager.getStakingRewards(coin: .cardano, address: nil)
}

// NFT
Task {
    let nfts = try await manager.getNFTs(coin: .ton, address: tonAddress)
    let transferResult = try await manager.transferNFT(
        coin: .ton, nftAddress: "EQ...", toAddress: "EQ...", memo: nil
    )
}

// Bridge
Task {
    let bridgeResult = try await manager.bridgeAsset(
        fromChain: .cardano, toChain: .midnight, amount: 5_000_000
    )
    let status = try await manager.getBridgeStatus(txHash: bridgeResult.txHash)
}

// Capability checking
let hasTokens = manager.supportsTokens(coin: .ethereum)   // true
let hasNFTs = manager.supportsNFTs(coin: .ton)             // true
let hasStaking = manager.supportsStaking(coin: .cardano)   // true
```

---

## Migration Guide — Từ androidMain sang commonMain

Hướng dẫn cho app cũ đang dùng code trực tiếp từ `androidMain` chuyển sang sử dụng `CommonCoinsManager` trong `commonMain`.

### Tổng quan thay đổi

| Trước (androidMain) | Sau (commonMain) |
|---|---|
| `CoinsManager.shared.addresses(ACTCoin.Bitcoin)` | `manager.getAddress(NetworkName.BTC)` |
| `CoinsManager.shared.getBalance(ACTCoin.Cardano, ...)` | `manager.getBalance(NetworkName.CARDANO)` |
| `CentralityNetwork.shared.sendCoin(...)` | `manager.sendCentrality(from, to, amount)` |
| `CentralityNetwork.shared.scanAccount(...)` | `manager.getCentralityBalance()` |
| Callback-based (`completionHandler`) | Suspend functions (coroutines) |
| Gson `@SerializedName` | kotlinx `@SerialName` |
| `java.math.BigInteger` | `com.ionspin.kotlin.bignum.integer.BigInteger` |
| `java.io.Serializable` | Removed (dùng `@Serializable` nếu cần) |
| `android.util.Log` | `co.touchlab.kermit.Logger` |
| Retrofit/OkHttp | Ktor HttpClient |

### Import mapping

```kotlin
// ─── Trước ──────────────────────────────────────────────────────────
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.Algorithm
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.Change
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.coinkits.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTHDWallet
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.coinkits.TransationData
import com.lybia.cryptowallet.coinkits.MemoData
import com.lybia.cryptowallet.coinkits.models.TokenInfo
import com.lybia.cryptowallet.coinkits.models.NFTItem
import com.lybia.cryptowallet.coinkits.centrality.*

// ─── Sau ────────────────────────────────────────────────────────────
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.Algorithm
import com.lybia.cryptowallet.enums.Change
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.models.TransationData
import com.lybia.cryptowallet.models.MemoData
import com.lybia.cryptowallet.models.TokenInfo
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.wallets.centrality.*
import com.lybia.cryptowallet.wallets.centrality.codec.ScaleCodec
import com.lybia.cryptowallet.wallets.centrality.model.*
```

### Centrality migration (chi tiết)

Centrality đã được migrate hoàn toàn từ `androidMain` sang `commonMain`. Legacy code đã bị xóa.

```kotlin
// ─── Trước (androidMain) ────────────────────────────────────────────
// Callback-based, Retrofit/OkHttp, Gson
CentralityNetwork.shared.getPublicAddress(seed) { address, publicKey ->
    // handle address
}
CentralityNetwork.shared.scanAccount(address, assetId) { balance, success ->
    // handle balance
}
CentralityNetwork.shared.sendCoin(from, to, amount, assetId) { txHash, success, error ->
    // handle result
}

// ─── Sau (commonMain) ──────────────────────────────────────────────
// Suspend functions, Ktor, kotlinx-serialization
launch {
    val address = manager.getAddress(NetworkName.CENTRALITY)
    val balance = manager.getCentralityBalance()
    val send = manager.sendCentrality(from, to, amount, assetId)
}
```

### SCALE Codec migration

```kotlin
// ─── Trước ──────────────────────────────────────────────────────────
import com.lybia.cryptowallet.coinkits.centrality.U8a
import java.math.BigInteger
U8a.compactToU8a(BigInteger("1000"))

// ─── Sau ────────────────────────────────────────────────────────────
import com.lybia.cryptowallet.wallets.centrality.codec.ScaleCodec
import com.ionspin.kotlin.bignum.integer.BigInteger
ScaleCodec.compactToU8a(BigInteger(1000))
```

### SS58 Address migration

```kotlin
// ─── Trước ──────────────────────────────────────────────────────────
import com.lybia.cryptowallet.coinkits.centrality.CennzAddress
val addr = CennzAddress(addressString)
val pubKey = addr.publicKey

// ─── Sau ────────────────────────────────────────────────────────────
import com.lybia.cryptowallet.wallets.centrality.model.CentralityAddress
val addr = CentralityAddress(addressString)
val pubKey = addr.publicKey  // ByteArray? — null nếu invalid
```

### ExtrinsicBuilder migration

```kotlin
// ─── Trước ──────────────────────────────────────────────────────────
import com.lybia.cryptowallet.coinkits.centrality.ExtrinsicBase
val extrinsic = ExtrinsicBase()
// ... set fields directly ...
val hex = extrinsic.toString()  // Gson-based

// ─── Sau ────────────────────────────────────────────────────────────
import com.lybia.cryptowallet.wallets.centrality.model.ExtrinsicBuilder
val extrinsic = ExtrinsicBuilder()
    .paramsMethod(to, amount, assetId)
    .paramsSignature(signer, nonce)
    .signOptions(specVersion, txVersion, genesisHash, blockHash, era)
val payload = extrinsic.createPayload()
extrinsic.sign(signatureHex)
val hex = extrinsic.toHex()  // "0x..."
```

### Data Models migration

```kotlin
// ─── Trước (Gson + java.io.Serializable) ────────────────────────────
@SerializedName("block_num") var blockNum: Long = 0

// ─── Sau (kotlinx.serialization) ────────────────────────────────────
@SerialName("block_num") val blockNum: Long = 0
```

### Callback → Coroutine migration pattern

```kotlin
// ─── Trước (callback hell) ──────────────────────────────────────────
CoinsManager.shared.getBalance(ACTCoin.Cardano, addresses) { balance, success ->
    runOnUiThread {
        if (success) updateUI(balance)
    }
}

// ─── Sau (coroutines) ───────────────────────────────────────────────
lifecycleScope.launch {
    val result = manager.getBalance(NetworkName.CARDANO)
    if (result.success) updateUI(result.balance)
    // Đã trên Main thread nếu dùng lifecycleScope
}
```

---

## Lưu ý quan trọng

1. Tất cả hàm network là `suspend fun` — cần gọi trong coroutine scope
2. `CommonCoinsManager` tự tạo API services nếu không truyền vào
3. Địa chỉ Cardano derive theo CIP-1852 (`m/1852'/1815'/account'/role/index`)
4. Module tự phát hiện Byron vs Shelley address khi gửi ADA
5. Code legacy trong `androidMain` đã bị xóa cho: Cardano, Bitcoin, Ripple, Centrality, HD Wallet
6. Ethereum web3j code trong `androidMain` giữ nguyên (không xóa)
7. `CoinsManager` (androidMain) vẫn tồn tại — delegate sang `CommonCoinsManager`
8. Bridge operations hiện dùng simulated responses — chờ API thật sẵn sàng
9. TON `unstake()` trả về `UnsupportedOperation` — TON staking protocols không hỗ trợ unstake trực tiếp
