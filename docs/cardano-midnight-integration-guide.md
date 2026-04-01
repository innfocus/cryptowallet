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
| TON | `TON` | ✅ | ✅ Jetton | ✅ TEP-62 | ✅ | ✅ | — |
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

Có 2 cách cài đặt apiKey cho Cardano (Blockfrost):

```kotlin
// ─── Cách 1: Inject CardanoApiService trực tiếp ─────────────────────
// Linh hoạt — control hoàn toàn service instance (provider, custom client)
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

// ─── Cách 2: Dùng configs map (gọn hơn) ────────────────────────────
// ChainManagerFactory tự tạo CardanoApiService bên trong
import com.lybia.cryptowallet.coinkits.ChainConfig

val manager = CommonCoinsManager(
    mnemonic = mnemonic,
    configs = mapOf(
        NetworkName.CARDANO to ChainConfig(
            apiBaseUrl = "https://cardano-mainnet.blockfrost.io/api/v0",
            apiKey = "your-blockfrost-project-id"
        ),
        // Thêm config cho chain khác nếu cần
        NetworkName.MIDNIGHT to ChainConfig(
            apiBaseUrl = "https://midnight-api.example.com",
            apiKey = "your-midnight-api-key"
        )
    )
)
```

> Nếu không truyền apiKey, `ChainConfig.default(CARDANO)` tự động fallback sang Koios API (free, không cần key). Nếu có apiKey, dùng Blockfrost.

```kotlin
// ─── Cách 3: Koios fallback (không cần API key) ────────────────────
// Khi không truyền apiKey, ChainManagerFactory tự dùng Koios thay Blockfrost
val manager = CommonCoinsManager(mnemonic = mnemonic)
// → CardanoApiService.createWithFallback() chọn Koios vì apiKey = null

// ─── Cách 4: Chỉ định Koios trực tiếp ──────────────────────────────
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.CardanoApiProvider

val koiosApi = CardanoApiService(
    baseUrl = CoinNetwork(NetworkName.CARDANO).getKoiosUrl(),
    provider = CardanoApiProvider.KOIOS
)
val manager = CommonCoinsManager(mnemonic = mnemonic, cardanoApiService = koiosApi)
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

### sendCoin (unified — tự build + sign + submit)

`sendCoin` là API thống nhất thay thế `CoinsManager.shared.sendCoin()`. Tự động dispatch đến chain manager phù hợp, build transaction, sign và submit. Nhận amount ở đơn vị lớn (ADA, TON, CENNZ...).

```kotlin
import com.lybia.cryptowallet.models.MemoData

launch {
    // ─── Cardano ────────────────────────────────────────────────────
    val adaResult = manager.sendCoin(
        coin = NetworkName.CARDANO,
        toAddress = "addr1q...",
        amount = 2.0,           // 2 ADA (tự convert sang lovelace bên trong)
        networkFee = 0.2         // 0.2 ADA fee
    )

    // ─── Ethereum ───────────────────────────────────────────────────
    val ethResult = manager.sendCoin(
        coin = NetworkName.ETHEREUM,
        toAddress = "0x...",
        amount = 0.1             // 0.1 ETH (tự build EIP-155 tx + sign)
    )
    // Hoặc dùng method riêng:
    val ethResult2 = manager.sendEth("0x...", amountEth = 0.1)

    // ─── ERC-20 Token (method riêng) ────────────────────────────────
    val tokenResult = manager.sendErc20Token(
        contractAddress = "0xTokenContract...",
        toAddress = "0xRecipient...",
        amount = 1_000_000L      // amount in smallest unit (e.g. 1 USDT = 1_000_000)
    )

    // ─── XRP (có destinationTag) ────────────────────────────────────
    val xrpResult = manager.sendCoin(
        coin = NetworkName.XRP,
        toAddress = "r...",
        amount = 10.0,           // 10 XRP (tự build Payment tx + sign)
        memo = MemoData(null, 12345u)  // destinationTag
    )
    // Hoặc dùng method riêng:
    val xrpResult2 = manager.sendXrp("r...", amountXrp = 10.0, destinationTag = 12345L)

    // ─── Midnight ───────────────────────────────────────────────────
    val midResult = manager.sendCoin(
        coin = NetworkName.MIDNIGHT,
        toAddress = "midnight1q...",
        amount = 1.0             // 1 tDUST
    )

    // ─── TON (có memo) ─────────────────────────────────────────────
    val tonResult = manager.sendCoin(
        coin = NetworkName.TON,
        toAddress = "EQ...",
        amount = 0.5,            // 0.5 TON
        memo = MemoData("Hello", null)
    )

    // ─── Centrality ─────────────────────────────────────────────────
    val cenResult = manager.sendCoin(
        coin = NetworkName.CENTRALITY,
        toAddress = "cX...",
        amount = 1.5             // 1.5 CENNZ
    )

    // Kết quả chung
    if (adaResult.success) println("ADA TX: ${adaResult.txHash}")
}
```

> Tất cả chain đều hỗ trợ `sendCoin` trực tiếp (build + sign + submit).

| Chain | sendCoin | Ghi chú |
|---|---|---|
| Cardano | ✅ | Tự build UTXO + sign + submit |
| Midnight | ✅ | Tự build + submit |
| TON | ✅ | Tự sign BOC + submit, hỗ trợ memo |
| Centrality | ✅ | Tự orchestrate extrinsic + submit |
| ETH / Arbitrum | ✅ | Tự build EIP-155 tx + secp256k1 sign + submit |
| XRP | ✅ | Tự build Payment tx + secp256k1 sign + submit, hỗ trợ destinationTag |
| BTC | ✅ | BlockCypher hoặc Local builder (Esplora), hỗ trợ Legacy/SegWit/Native SegWit |

### estimateFee (unified)

`estimateFee` trả về phí giao dịch ước tính cho mỗi chain. ETH/Arbitrum dùng gas estimation thực tế, các chain khác trả về default fee.

```kotlin
launch {
    // ─── Ethereum (gas-based, cần fromAddress + toAddress) ──────────
    val ethFee = manager.estimateFee(
        coin = NetworkName.ETHEREUM,
        amount = 0.1,
        fromAddress = "0xFrom...",
        toAddress = "0xTo..."
    )
    // FeeEstimateResult(fee, gasLimit, gasPrice, unit, success, error)
    if (ethFee.success) {
        println("ETH fee: ${ethFee.fee} ${ethFee.unit}")
        println("Gas limit: ${ethFee.gasLimit}, Gas price: ${ethFee.gasPrice}")
    }

    // ─── TON (estimate qua dummy BOC) ───────────────────────────────
    val tonFee = manager.estimateFee(
        coin = NetworkName.TON,
        amount = 0.5
    )
    if (tonFee.success) println("TON fee: ${tonFee.fee} ${tonFee.unit}")

    // ─── Cardano, Centrality, XRP, Midnight (static default) ────────
    val adaFee = manager.estimateFee(coin = NetworkName.CARDANO)
    val cenFee = manager.estimateFee(coin = NetworkName.CENTRALITY)
    val xrpFee = manager.estimateFee(coin = NetworkName.XRP)
    val midFee = manager.estimateFee(coin = NetworkName.MIDNIGHT)

    println("ADA fee: ${adaFee.fee}")   // ACTCoin.Cardano.feeDefault()
    println("CENNZ fee: ${cenFee.fee}") // ACTCoin.Centrality.feeDefault()
    println("XRP fee: ${xrpFee.fee}")   // ACTCoin.Ripple.feeDefault()
}
```

| Chain | Estimation | Ghi chú |
|---|---|---|
| ETH / Arbitrum | ✅ Dynamic | Gas estimation qua IFeeEstimator |
| TON | ✅ Dynamic | Estimate qua TonApiService (dummy BOC) |
| Cardano | Static | `ACTCoin.Cardano.feeDefault()` |
| Midnight | Static | `ACTCoin.Midnight.feeDefault()` |
| XRP | ✅ Dynamic | `fee` RPC method (open_ledger_fee, fallback 12 drops) |
| Centrality | Static | `ACTCoin.Centrality.feeDefault()` |
| BTC | ✅ Dynamic | BlockCypher `/txs/new` hoặc Local builder (Esplora + mempool.space) |

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
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager

// ─── Addresses (3 loại) ─────────────────────────────────────────────
val nativeSegwit = manager.getBtcAddress(BitcoinAddressType.NATIVE_SEGWIT)  // "bc1q..."
val nestedSegwit = manager.getBtcAddress(BitcoinAddressType.NESTED_SEGWIT)  // "3..."
val legacy = manager.getBtcAddress(BitcoinAddressType.LEGACY)               // "1..."

// Default (Native SegWit):
val address = manager.getAddress(NetworkName.BTC)  // "bc1q..."

launch {
    val balance = manager.getBalance(NetworkName.BTC)
    val txs = manager.getTransactionHistory(NetworkName.BTC)

    // ─── Option 1: Local tx builder (recommended — không phụ thuộc BlockCypher)
    // Build + sign transaction hoàn toàn client-side bằng bitcoin-kmp.
    // Chỉ gọi API để fetch UTXOs (Esplora/Blockstream) và broadcast.
    val sendLocal = manager.sendBtcLocal(
        toAddress = "bc1q...",
        amountBtc = 0.001,
        addressType = BitcoinAddressType.NATIVE_SEGWIT,  // hoặc NESTED_SEGWIT, LEGACY
        accountIndex = 0,
        feeRateSatPerVbyte = null  // null = auto từ mempool.space
    )

    // Fee estimation (local)
    val feeLocal = manager.estimateBtcFeeLocal(
        toAddress = "bc1q...",
        amountBtc = 0.001,
        addressType = BitcoinAddressType.NATIVE_SEGWIT,
        feeRateSatPerVbyte = 15  // 15 sat/vB, hoặc null = auto
    )
    // FeeEstimateResult(fee=0.0000xxxx BTC, unit="BTC", success=true)

    // ─── Option 2: BlockCypher (legacy — vẫn hoạt động) ────────────
    // BlockCypher xử lý UTXO selection server-side, client chỉ sign.
    val sendBlockCypher = manager.sendBtc(
        toAddress = "1...",
        amountBtc = 0.001,
        addressType = BitcoinAddressType.LEGACY,
        accountIndex = 0
    )

    // Fee estimation (BlockCypher)
    val feeBlockCypher = manager.estimateFee(
        coin = NetworkName.BTC,
        amount = 0.001,
        toAddress = "bc1q..."
    )

    // ─── sendCoin (unified — dùng BlockCypher bên trong) ────────────
    val send = manager.sendCoin(
        coin = NetworkName.BTC,
        toAddress = "bc1q...",
        amount = 0.001
    )

    // ─── Pre-signed (legacy API) ────────────────────────────────────
    val sendSigned = manager.transfer(NetworkName.BTC, signedTxJson)
}
```

#### So sánh Local Builder vs BlockCypher

| | Local Builder (`sendBtcLocal`) | BlockCypher (`sendBtc`) |
|---|---|---|
| UTXO selection | Client-side (largest-first) | Server-side (BlockCypher) |
| Transaction building | Client-side (bitcoin-kmp) | Server-side (BlockCypher `/txs/new`) |
| Signing | Client-side ✅ | Client-side ✅ |
| Script type handling | Client explicit (P2PKH/P2SH-P2WPKH/P2WPKH) | BlockCypher auto-detect |
| API dependency | Esplora (miễn phí, self-hostable) | BlockCypher (rate limited) |
| Offline signing | Có (nếu có UTXO cache) | Không |
| Verify trước broadcast | Có (`correctlySpends()`) | Không |
| Fee rate control | Chọn sat/vB cụ thể | BlockCypher tự chọn |

#### Lấy BitcoinManager trực tiếp

```kotlin
// Khi cần truy cập các method riêng của BitcoinManager
val btcManager = manager.getChainManager(NetworkName.BTC) as BitcoinManager

// Local builder trực tiếp (amount in satoshis)
launch {
    val result = btcManager.sendBtcLocal(
        toAddress = "bc1q...",
        amountSatoshi = 100_000,
        addressType = BitcoinAddressType.NATIVE_SEGWIT,
        feeRateSatPerVbyte = 20
    )
}

// Estimate fee local (trả về satoshis)
launch {
    val feeSat = btcManager.estimateFeeLocal(
        toAddress = "bc1q...",
        amountSatoshi = 100_000,
        addressType = BitcoinAddressType.NATIVE_SEGWIT
    )
    println("Fee: $feeSat satoshis")
}
```

### Ethereum / Arbitrum

```kotlin
launch {
    val balance = manager.getBalance(NetworkName.ETHEREUM)

    // ─── Direct send ETH (build + sign + submit) ───────────────────
    val send = manager.sendCoin(
        coin = NetworkName.ETHEREUM,
        toAddress = "0x...",
        amount = 0.1             // 0.1 ETH
    )
    // Hoặc dùng method riêng (cho custom gas):
    val send2 = manager.sendEth(
        toAddress = "0x...",
        amountEth = 0.1,
        gasLimit = 21000L,       // optional
        gasPriceGwei = 30L       // optional
    )

    // ─── Direct send ERC-20 token ──────────────────────────────────
    val tokenSend = manager.sendErc20Token(
        contractAddress = "0xTokenContract...",
        toAddress = "0xRecipient...",
        amount = 1_000_000L      // smallest unit
    )

    // ─── Pre-signed (legacy) ───────────────────────────────────────
    val sendSigned = manager.transfer(NetworkName.ETHEREUM, signedTxHex)

    // ERC-20 token balance
    val tokenBal = manager.getTokenBalance(NetworkName.ETHEREUM, wallet, contract)

    // ERC-721 NFT
    val nfts = manager.getNFTs(NetworkName.ETHEREUM, wallet)
    val nftSend = manager.transferNFT(NetworkName.ETHEREUM, nftAddr, toAddr)

    // Fee estimation
    val fee = manager.estimateFee(
        coin = NetworkName.ETHEREUM,
        amount = 0.1,
        fromAddress = "0x...",
        toAddress = "0x..."
    )
    // FeeEstimateResult(fee, gasLimit, gasPrice, unit="gwei", success)
}
```

### Ripple (XRP)

```kotlin
launch {
    val balance = manager.getBalance(NetworkName.XRP)
    // Balance đã chia 1,000,000 drops → XRP

    // ─── Direct send XRP (build + sign + submit) ───────────────────
    val send = manager.sendCoin(
        coin = NetworkName.XRP,
        toAddress = "r...",
        amount = 10.0,           // 10 XRP
        memo = MemoData(null, 12345u)  // destinationTag (optional)
    )
    // Hoặc dùng method riêng:
    val send2 = manager.sendXrp(
        toAddress = "r...",
        amountXrp = 10.0,
        feeXrp = 0.000012,       // 12 drops (default)
        destinationTag = 12345L   // optional
    )

    // ─── Pre-signed (legacy) ───────────────────────────────────────
    val sendSigned = manager.transfer(NetworkName.XRP, signedTxBlob)

    // Fee: XRP fee cố định 12 drops (0.000012 XRP)
    val fee = manager.estimateFee(coin = NetworkName.XRP)
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

### Sử dụng Byron và Shelley song song qua CommonCoinsManager

`CardanoManager` (bên trong `CommonCoinsManager`) derive cả Byron và Shelley address từ cùng một mnemonic. Mặc định `getAddress(NetworkName.CARDANO)` trả về Shelley base address. Để truy cập Byron address hoặc dùng cả hai song song, cần cast sang `CardanoManager`.

#### Derivation paths

| Loại | Derivation Path | Ví dụ address |
|---|---|---|
| Shelley (CIP-1852) | `m/1852'/1815'/account'/0'/index'` | `addr1q...` / `addr_test1q...` |
| Byron (BIP-44) | `m/44'/1815'/0'/0'/index'` | `Ae2tdPwUPEZ...` (Base58) |
| Staking (CIP-1852) | `m/1852'/1815'/account'/2'/0'` | `stake1u...` / `stake_test1u...` |

#### Lấy cả Byron và Shelley address

```kotlin
val manager = CommonCoinsManager(mnemonic = mnemonic)

// ─── Cách 1: Qua CommonCoinsManager (chỉ Shelley) ──────────────────
val shelleyAddress = manager.getAddress(NetworkName.CARDANO)  // "addr1q..."

// ─── Cách 2: Cast sang CardanoManager để lấy cả hai ────────────────
import com.lybia.cryptowallet.wallets.cardano.CardanoManager
import com.lybia.cryptowallet.coinkits.ChainManagerFactory
import com.lybia.cryptowallet.coinkits.ChainConfig
import com.lybia.cryptowallet.enums.NetworkName

val cardanoManager = ChainManagerFactory.createWalletManager(
    NetworkName.CARDANO, mnemonic, ChainConfig.default(NetworkName.CARDANO)
) as CardanoManager

// Shelley base address (payment + staking key)
val shelley = cardanoManager.getShelleyAddress(account = 0, index = 0)  // "addr1q..."

// Byron address (legacy)
val byron = cardanoManager.getByronAddress(index = 0)  // "Ae2tdPwUPEZ..."

// Staking (reward) address
val staking = cardanoManager.getStakingAddress(account = 0)  // "stake1u..."

// Derive nhiều address (HD wallet)
val shelleyAddresses = (0..4).map { cardanoManager.getShelleyAddress(account = 0, index = it) }
val byronAddresses = (0..4).map { cardanoManager.getByronAddress(index = it) }
```

#### Query balance cho cả Byron và Shelley

```kotlin
launch {
    // Balance cho Shelley address (mặc định)
    val shelleyBalance = manager.getCardanoBalance()

    // Balance cho Byron address cụ thể
    val byronAddr = cardanoManager.getByronAddress(index = 0)
    val byronBalance = manager.getCardanoBalance(address = byronAddr)

    // Tổng balance cả hai
    if (shelleyBalance.success && byronBalance.success) {
        val totalAda = shelleyBalance.balance + byronBalance.balance
        println("Shelley: ${shelleyBalance.balance} ADA")
        println("Byron:   ${byronBalance.balance} ADA")
        println("Total:   $totalAda ADA")
    }
}
```

#### Gửi ADA — tự động detect Byron/Shelley

`sendCardano()` và `buildAndSignTransaction()` tự detect loại address đích (Byron hay Shelley) qua `CardanoAddress.getAddressType()` và convert sang bytes phù hợp. Caller không cần xử lý gì thêm.

```kotlin
launch {
    // Gửi đến Shelley address — tự detect
    val result1 = manager.sendCardano(
        toAddress = "addr1q...",       // Shelley base address
        amountLovelace = 2_000_000L,
        fee = 200_000L
    )

    // Gửi đến Byron address — cũng tự detect
    val result2 = manager.sendCardano(
        toAddress = "Ae2tdPwUPEZ...",  // Byron address
        amountLovelace = 2_000_000L,
        fee = 200_000L
    )
    // Cả hai đều dùng cùng API, CardanoManager tự xử lý addressToBytes()
}
```

#### Validate và detect loại address

```kotlin
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType

fun handleCardanoAddress(address: String) {
    // Validate chung (Byron hoặc Shelley)
    if (!CardanoAddress.isValidAddress(address)) {
        println("Invalid Cardano address")
        return
    }

    // Detect loại cụ thể
    when (CardanoAddress.getAddressType(address)) {
        CardanoAddressType.BYRON -> {
            println("Byron (legacy) address")
            // Byron address không hỗ trợ staking, native token
        }
        CardanoAddressType.SHELLEY_BASE -> {
            println("Shelley base address (payment + staking)")
            // Hỗ trợ đầy đủ: transfer, staking, native token
        }
        CardanoAddressType.SHELLEY_ENTERPRISE -> {
            println("Shelley enterprise address (payment only)")
            // Không có staking key
        }
        CardanoAddressType.SHELLEY_REWARD -> {
            println("Shelley reward/staking address")
            // Chỉ dùng cho staking rewards
        }
        CardanoAddressType.UNKNOWN -> {
            println("Unknown address type")
        }
    }
}
```

#### Lịch sử giao dịch cho cả hai loại address

```kotlin
launch {
    // Lịch sử cho Shelley (mặc định)
    val shelleyTxs = manager.getCardanoTransactions()

    // Lịch sử cho Byron address cụ thể
    val byronAddr = cardanoManager.getByronAddress(index = 0)
    val byronTxs = manager.getCardanoTransactions(address = byronAddr)

    // Gộp lịch sử cả hai (nếu cần hiển thị chung)
    // Cast về List<*> tuỳ theo response type từ Blockfrost API
}
```

#### So sánh Byron vs Shelley

| Tính năng | Byron | Shelley |
|---|---|---|
| Address format | Base58 (`Ae2tdPwUPEZ...`) | Bech32 (`addr1q...`) |
| Derivation | BIP-44 (`m/44'/1815'/...`) | CIP-1852 (`m/1852'/1815'/...`) |
| Staking | ❌ | ✅ (delegation) |
| Native token | ❌ | ✅ |
| Bridge (Midnight) | ❌ | ✅ |
| `getAddress()` mặc định | ❌ | ✅ |
| Transfer (nhận) | ✅ | ✅ |
| Transfer (gửi từ) | ✅ (qua `buildAndSignTransaction`) | ✅ |

> Byron address chủ yếu dùng cho backward compatibility với ví cũ. Shelley là tiêu chuẩn hiện tại, hỗ trợ đầy đủ staking, native token, và bridge.

#### Pattern trong Android app (WalletCardanoManager + WalletCardanoShelleyManager)

Android app hiện tại dùng 2 wallet manager song song:

| Manager | ISO | Loại | Mô tả |
|---|---|---|---|
| `WalletCardanoManager` | `"ADA"` | Byron | Legacy Cardano wallet, dùng `CoinsManager.shared` |
| `WalletCardanoShelleyManager` | `"ADAS"` | Shelley | Shelley wallet, lấy address từ StakingApi server |

```kotlin
// WalletsMaster.kt — khởi tạo cả hai song song
val cardano = WalletCardanoManager.getInstance(app)        // Byron
val cardanoShelley = WalletCardanoShelleyManager.getInstance(app)  // Shelley

if (cardano != null) mWallets.add(cardano)
if (cardanoShelley != null) mWallets.add(cardanoShelley)

// Lookup theo ISO
val byronWallet = WalletsMaster.getWalletByIso(app, "ADA")    // WalletCardanoManager
val shelleyWallet = WalletsMaster.getWalletByIso(app, "ADAS")  // WalletCardanoShelleyManager
```

Khi migrate sang `CommonCoinsManager`, cả hai wallet có thể thay bằng một `CardanoManager` duy nhất:

```kotlin
// ─── Trước (2 manager riêng biệt) ──────────────────────────────────
val byronAddr = WalletCardanoManager.getInstance(app)?.getAddress()
val shelleyAddr = WalletCardanoShelleyManager.getInstance(app)?.getAddress()

// ─── Sau (1 CardanoManager, derive cả hai) ──────────────────────────
val cardanoManager = ChainManagerFactory.createWalletManager(
    NetworkName.CARDANO, mnemonic, ChainConfig.default(NetworkName.CARDANO)
) as CardanoManager

val byronAddr = cardanoManager.getByronAddress(index = 0)
val shelleyAddr = cardanoManager.getShelleyAddress(account = 0, index = 0)
val stakingAddr = cardanoManager.getStakingAddress(account = 0)

// Hoặc qua CommonCoinsManager (chỉ Shelley mặc định)
val manager = CommonCoinsManager(mnemonic = mnemonic)
val defaultAddr = manager.getAddress(NetworkName.CARDANO)  // = shelleyAddr
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
| `CoinsManager.shared.addresses(ACTCoin.Bitcoin)` | `manager.addresses(NetworkName.BTC, count)` |
| `CoinsManager.shared.firstAddress(coin)` | `manager.firstAddress(NetworkName.XXX)` hoặc `manager.getAddress(NetworkName.XXX)` |
| `CoinsManager.shared.getBalance(ACTCoin.Cardano, ...)` | `manager.getBalance(NetworkName.CARDANO)` |
| `CoinsManager.shared.getTransactions(coin, moreParam, handler)` | `manager.getTransactionHistoryPaginated(coin, limit, pageParam)` |
| `com.lybia.cryptowallet.coinkits.cardano.helpers.ADACoin` | `ACTCoin.Cardano.unitValue()` (= 1,000,000.0) |
| `Gada.shared.addressUsed(addresses, handler)` | `CardanoApiService.getTransactionHistory()` + filter |
| `ADAAddressUsedHandle` | Định nghĩa callback cục bộ trong app |
| `CentralityNetwork.shared.sendCoin(...)` | `manager.sendCoin(NetworkName.CENTRALITY, ...)` hoặc `manager.sendCentrality(from, to, amount)` |
| `CentralityNetwork.BASE_UNIT` | `CentralityManager.BASE_UNIT` (= 10000) |
| `CentralityNetwork.shared.scanAccount(...)` | `manager.getCentralityBalance()` |
| `CentralityNetwork.shared.transactions(...)` | `manager.getCentralityTransactions()` |
| `CentralityNetwork.shared.getPublicAddress(seed, handler)` | `manager.getAddress(NetworkName.CENTRALITY)` hoặc `CentralityApiService.getPublicAddress(seed)` |
| `CoinsManager.shared.setNetworks(networks)` | `Config.shared.setNetwork(Network.MAINNET)` + tạo mới `CommonCoinsManager` |
| `CoinsManager.shared.cleanAll()` | Tạo mới instance `CommonCoinsManager(mnemonic)` |
| `CoinsManager.shared.sendCoin(from, to, ...)` | `manager.sendCoin(coin, toAddress, amount, fee)` |
| `CoinsManager.shared.estimateFee(...)` | `manager.estimateFee(coin)` hoặc `ACTCoin.feeDefault()` |
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

### ADACoin constant migration

Hằng số `ADACoin` (giá trị `1000000`) trước đây nằm ở `com.lybia.cryptowallet.coinkits.cardano.helpers.ADACoin`, dùng để chuyển đổi giữa ADA và lovelace (1 ADA = 1,000,000 lovelace). Hằng số này đã bị xóa khỏi thư viện khi migrate.

Cách thay thế (chọn 1 trong 2):

```kotlin
// ─── Cách 1 (recommended): Dùng ACTCoin.unitValue() ────────────────
import com.lybia.cryptowallet.enums.ACTCoin

// Trước:
// import com.lybia.cryptowallet.coinkits.cardano.helpers.ADACoin
// val ada = lovelace / ADACoin

// Sau:
val ada = lovelace / ACTCoin.Cardano.unitValue()  // unitValue() = 1_000_000.0
val lovelace = (ada * ACTCoin.Cardano.unitValue()).toLong()

// ─── Cách 2: Định nghĩa hằng số cục bộ trong app ──────────────────
companion object {
    const val ADA_COIN = 1_000_000L  // 1 ADA = 1,000,000 lovelace
}
val ada = lovelace.toDouble() / ADA_COIN
```

Các file trong app cần cập nhật: `StakedCoinsFragment`, `StakeDetailsFragment`, `WalletCardanoManager`, `WalletCardanoShelleyManager` — thay tất cả `ADACoin` bằng `ACTCoin.Cardano.unitValue()` hoặc hằng số cục bộ.

### Gada.shared.addressUsed() migration

`Gada.shared.addressUsed(addresses, handler)` trước đây gọi Cardano API để kiểm tra danh sách address nào đã có giao dịch (đã sử dụng). Class `Gada` và callback interface `ADAAddressUsedHandle` đã bị xóa khỏi thư viện.

Trong commonMain, `CardanoApiService` không có method `addressUsed()` riêng. Thay vào đó, dùng `getTransactionHistory()` hoặc `getUtxos()` để kiểm tra:

```kotlin
// ─── Trước (androidMain, callback-based) ────────────────────────────
import com.lybia.cryptowallet.coinkits.cardano.networking.Gada
import com.lybia.cryptowallet.coinkits.cardano.networking.ADAAddressUsedHandle

Gada.shared.addressUsed(addresses) { usedAddresses, error ->
    // usedAddresses: Array<String> — danh sách address đã có giao dịch
}

// ─── Sau (commonMain, coroutine) ────────────────────────────────────
import com.lybia.cryptowallet.services.CardanoApiService

// Cách 1: Dùng CardanoApiService trực tiếp
val cardanoApi = CardanoApiService(baseUrl = "...", apiKey = "...")

suspend fun getUsedAddresses(addresses: List<String>): List<String> {
    return addresses.filter { address ->
        try {
            val txs = cardanoApi.getTransactionHistory(listOf(address))
            txs.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

// Cách 2: Coroutine bridge trong app (giữ callback pattern cũ)
fun checkAddressUsed(
    addresses: List<String>,
    callback: (usedAddresses: List<String>, error: Throwable?) -> Unit
) {
    lifecycleScope.launch {
        try {
            val used = getUsedAddresses(addresses)
            callback(used, null)
        } catch (e: Exception) {
            callback(emptyList(), e)
        }
    }
}
```

Nếu app cần callback interface tương tự `ADAAddressUsedHandle`, định nghĩa cục bộ:

```kotlin
// Định nghĩa trong app (không cần import từ thư viện)
interface ADAAddressUsedHandle {
    fun completionHandler(addressUsed: Array<String>, err: Throwable?)
}
```

Các file cần cập nhật: `WalletCardanoManager`, `WalletCardanoShelleyManager`, `CoinAddressAdaFragment`.

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

Hằng số `CentralityNetwork.BASE_UNIT` (= `10000`) giờ nằm ở:

```kotlin
// Trước:
// import com.lybia.cryptowallet.coinkits.centrality.CentralityNetwork
// val cennz = rawBalance / CentralityNetwork.BASE_UNIT

// Sau:
import com.lybia.cryptowallet.wallets.centrality.CentralityManager
val cennz = rawBalance.toDouble() / CentralityManager.BASE_UNIT  // 10000
```

`CentralityNetwork.shared.scanAccount(address, assetId, handler)` trước đây trả về balance qua callback. Giờ có 2 cách:

```kotlin
// ─── Trước (callback) ───────────────────────────────────────────────
CentralityNetwork.shared.scanAccount(address, assetId) { balance, success ->
    // balance: Double (đã chia BASE_UNIT)
}

// ─── Sau — Cách 1: Dùng CommonCoinsManager (recommended) ───────────
launch {
    val result = manager.getCentralityBalance(address)
    // result.balance: Double (đã chia BASE_UNIT), result.success, result.error
}

// ─── Sau — Cách 2: Dùng CentralityApiService trực tiếp ─────────────
import com.lybia.cryptowallet.services.CentralityApiService
import com.lybia.cryptowallet.wallets.centrality.CentralityManager

val api = CentralityApiService()
launch {
    val account = api.scanAccount(address)  // ScanAccount(address, nonce, balances)
    val asset = account.balances.find { it.assetId == assetId }
    val balance = (asset?.free?.toDouble() ?: 0.0) / CentralityManager.BASE_UNIT
}
```

`CentralityNetwork.shared.sendCoin(from, to, amount, assetId, handler)` trước đây là callback hell 7 cấp. Giờ là suspend function:

```kotlin
// ─── Trước (callback hell) ──────────────────────────────────────────
CentralityNetwork.shared.sendCoin(from, to, amount, assetId) { txHash, success, error ->
    runOnUiThread { /* handle result */ }
}

// ─── Sau — Cách 1: Dùng CommonCoinsManager (recommended) ───────────
launch {
    val result = manager.sendCentrality(
        fromAddress = "cX...",
        toAddress = "cX...",
        amount = 1.5,       // đơn vị CENNZ (tự nhân BASE_UNIT bên trong)
        assetId = 1
    )
    // SendResult(txHash, success, error)
    if (result.success) println("TX: ${result.txHash}")
}

// ─── Sau — Cách 2: Dùng CentralityManager trực tiếp ────────────────
import com.lybia.cryptowallet.wallets.centrality.CentralityManager

val centralityManager = CentralityManager(mnemonic = mnemonic)
launch {
    val result = centralityManager.sendCoin(from, to, amount, assetId)
    // TransferResponseModel(success, error, txHash)
    // Bên trong tự orchestrate: getRuntimeVersion → chainGetBlockHash →
    //   chainGetFinalizedHead → chainGetHeader → systemAccountNextIndex →
    //   build ExtrinsicBuilder → signMessage → submitExtrinsic
}
```

`CentralityNetwork.shared.transactions(address, assetId, row, page, handler)` trước đây trả về lịch sử giao dịch qua callback:

```kotlin
// ─── Trước (callback) ───────────────────────────────────────────────
CentralityNetwork.shared.transactions(address, assetId, 100, 0) { transfers, success ->
    // transfers: List<CennzTransfer>
}

// ─── Sau — Cách 1: Dùng CommonCoinsManager (recommended) ───────────
launch {
    val txs = manager.getCentralityTransactions(address)
    // Trả về List<CennzTransfer> (đã filter theo assetId và success=true)
}

// ─── Sau — Cách 2: Dùng CentralityApiService trực tiếp ─────────────
import com.lybia.cryptowallet.services.CentralityApiService

val api = CentralityApiService()
launch {
    val result = api.scanTransfers(address, row = 100, page = 0)
    // ScanTransfer(transfers: List<CennzTransfer>, count: Long)
    val filtered = result.transfers.filter { it.assetId == assetId && it.success }
}
```

`CentralityNetwork.shared.getPublicAddress(seed, handler)` trước đây trả về address + publicKey qua callback:

```kotlin
// ─── Trước (callback) ───────────────────────────────────────────────
CentralityNetwork.shared.getPublicAddress(seed) { address, publicKey ->
    // address: String, publicKey: String
}

// ─── Sau — Cách 1: Dùng CommonCoinsManager (recommended) ───────────
val address = manager.getAddress(NetworkName.CENTRALITY)  // synchronous (cached)

// Lần đầu gọi getBalance/getCentralityBalance sẽ tự resolve address
launch {
    val balance = manager.getCentralityBalance()
    // address đã được resolve và cache bên trong
}

// ─── Sau — Cách 2: Dùng CentralityApiService trực tiếp ─────────────
import com.lybia.cryptowallet.services.CentralityApiService

val api = CentralityApiService()
launch {
    val centralityAddress = api.getPublicAddress(seedHex)
    // CentralityAddress(address: String, publicKey: ByteArray?)
    val address = centralityAddress.address
    val pubKey = centralityAddress.publicKey
}
```

> Lưu ý: `manager.getAddress(NetworkName.CENTRALITY)` trả về `""` nếu chưa gọi suspend function nào (address chưa được resolve từ API). Gọi `getBalance()` hoặc `getCentralityBalance()` trước sẽ tự resolve và cache address.

### CoinsManager.shared.setNetworks / cleanAll / estimateFee migration

```kotlin
// ─── setNetworks ────────────────────────────────────────────────────
// Trước: CoinsManager.shared.setNetworks(arrayOf(network1, network2))
//   → reset wallet, set coin networks

// Sau: Config global + tạo mới CommonCoinsManager
Config.shared.setNetwork(Network.MAINNET)  // hoặc Network.TESTNET
val manager = CommonCoinsManager(mnemonic = mnemonic)
// CommonCoinsManager tự tạo managers lazy theo Config.shared network

// ─── cleanAll ───────────────────────────────────────────────────────
// Trước: CoinsManager.shared.cleanAll()
//   → xóa hdWallet, addresses, cached data

// Sau: Tạo mới instance (CommonCoinsManager không có mutable state cần clean)
val freshManager = CommonCoinsManager(mnemonic = newMnemonic)
// Hoặc nếu đổi mnemonic trong androidMain CoinsManager:
// CoinsManager.shared.updateMnemonic(newMnemonic)  // đã có method này

// ─── estimateFee ────────────────────────────────────────────────────
// Trước:
// CoinsManager.shared.estimateFee(amount, address, paramFee, serviceFee, network, handler)

// Sau — Cardano/Centrality: dùng feeDefault (không cần gọi network)
val cardanoFee = ACTCoin.Cardano.feeDefault()      // 0.2 ADA
val centralityFee = ACTCoin.Centrality.feeDefault() // fixed fee

// Sau — Ethereum/Arbitrum: dùng EthereumManager trực tiếp
launch {
    if (manager.supportsFeeEstimation(NetworkName.ETHEREUM)) {
        // Cast sang IFeeEstimator cho fee estimation chi tiết
        val ethManager = ChainManagerFactory.createWalletManager(
            NetworkName.ETHEREUM, mnemonic
        ) as IFeeEstimator
        val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)
        val fee = ethManager.estimateFee(
            FeeEstimateParams(fromAddress, toAddress, amount),
            coinNetwork
        )
        // FeeEstimate(fee, gasLimit, gasPrice, unit)
    }
}

// Sau — TON: dùng TonManager.estimateFee trực tiếp
launch {
    val tonManager = TonManager(mnemonic)
    val coinNetwork = CoinNetwork(NetworkName.TON)
    val fee = TonApiService.INSTANCE.estimateFee(coinNetwork, address, bocBase64)
    // fee: Long? (nanoton)
}
```

```kotlin
// ─── firstAddress ───────────────────────────────────────────────────
// Trước: CoinsManager.shared.firstAddress(ACTCoin.Cardano)
//   → ACTAddress? (object chứa address string + network info)

// Sau: CommonCoinsManager.getAddress trả về String trực tiếp
val address = manager.getAddress(NetworkName.CARDANO)   // "addr1q..."
val btcAddr = manager.getAddress(NetworkName.BTC)       // "bc1q..."
val ethAddr = manager.getAddress(NetworkName.ETHEREUM)   // "0x..."
// Không cần unwrap ACTAddress — trả về String luôn
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

## Xóa CoinsManager — Chuyển hoàn toàn sang CommonCoinsManager

Hướng dẫn thay thế từng method của `CoinsManager` (androidMain) bằng `CommonCoinsManager` (commonMain) để xóa `CoinsManager.kt` và thống nhất KMP cho Android + iOS.

### Khác biệt chính

| | CoinsManager (cũ) | CommonCoinsManager (mới) |
|---|---|---|
| Source set | androidMain | commonMain (Android + iOS + JVM) |
| Pattern | Singleton `CoinsManager.shared` | Instance `CommonCoinsManager(mnemonic)` |
| API style | Callback (`BalanceHandle`, `SendCoinHandle`) | Suspend functions + Result wrappers |
| Address type | `ACTAddress` (object) | `String` (trực tiếp) |
| Coin type | `ACTCoin` enum | `NetworkName` enum |
| Network config | `ACTNetwork` + `setNetworks()` | `Config.shared.setNetwork()` + `ChainConfig` |
| State | Mutable singleton (hdWallet, caches) | Stateless facade (lazy managers) |

### ICoinsManager methods

```kotlin
// ─── getHDWallet() ──────────────────────────────────────────────────
// Trước: CoinsManager.shared.getHDWallet()
// Sau: Không cần — CommonCoinsManager quản lý bên trong
//      Nếu cần trực tiếp: ACTHDWallet(mnemonic)

// ─── setNetworks(networks) ──────────────────────────────────────────
// Trước: CoinsManager.shared.setNetworks(arrayOf(network1, network2))
// Sau:
Config.shared.setNetwork(Network.MAINNET)
val manager = CommonCoinsManager(mnemonic = mnemonic)

// ─── currentNetwork(coin) ───────────────────────────────────────────
// Trước: CoinsManager.shared.currentNetwork(ACTCoin.Cardano) → ACTNetwork?
// Sau: Không cần — dùng Config.shared.getNetwork() nội bộ
//      Nếu cần: ACTNetwork(ACTCoin.Cardano, Config.shared.isTestnet())

// ─── cleanAll() ─────────────────────────────────────────────────────
// Trước: CoinsManager.shared.cleanAll()
// Sau: Tạo mới instance
val freshManager = CommonCoinsManager(mnemonic = newMnemonic)

// ─── firstAddress(coin) / addresses(coin) ───────────────────────────
// Trước: CoinsManager.shared.firstAddress(ACTCoin.Cardano) → ACTAddress?
// Sau:
val address: String = manager.firstAddress(NetworkName.CARDANO)  // = getAddress()
// Hoặc:
val address2: String = manager.getAddress(NetworkName.CARDANO)

// Trước: CoinsManager.shared.addresses(ACTCoin.Cardano) → Array<ACTAddress>?
// Sau:
launch {
    val addrs: List<String> = manager.addresses(NetworkName.CARDANO, count = 5)
    // Cardano: 5 Shelley base addresses (account=0, index 0..4)
    // BTC: 5 Native SegWit addresses (account 0..4)
    // Centrality: luôn trả về 1 address (resolve từ API)
    // Các chain khác: luôn trả về 1 address
}
// Centrality (cần resolve từ API):
launch { val addr = manager.getAddressAsync(NetworkName.CENTRALITY) }

// ─── getBalance(coin, handler) ──────────────────────────────────────
// Trước:
CoinsManager.shared.getBalance(ACTCoin.Cardano, object : BalanceHandle {
    override fun completionHandler(balance: Double, success: Boolean) { }
})
// Sau:
launch {
    val result = manager.getBalance(NetworkName.CARDANO)
    // BalanceResult(balance, success, error)
}

// ─── getTransactions(coin, moreParam, handler) ──────────────────────
// Trước:
CoinsManager.shared.getTransactions(ACTCoin.Ripple, null, object : TransactionsHandle {
    override fun completionHandler(txs: Array<TransationData>?, more: JsonObject?, err: String) { }
})
// Sau — Cách 1: Không cần pagination
launch {
    val txs = manager.getTransactionHistory(NetworkName.XRP)
    // Any? — cast theo chain
}
// Sau — Cách 2: Có pagination (recommended cho XRP, Centrality)
launch {
    // Trang đầu
    val page1 = manager.getTransactionHistoryPaginated(
        coin = NetworkName.XRP,
        limit = 50
    )
    // TransactionHistoryResult(transactions, hasMore, nextPageParam, success, error)

    // Trang tiếp theo (nếu có)
    if (page1.hasMore) {
        val page2 = manager.getTransactionHistoryPaginated(
            coin = NetworkName.XRP,
            limit = 50,
            pageParam = page1.nextPageParam  // chứa marker cho XRP
        )
    }

    // Centrality pagination (row + page)
    val cenPage = manager.getTransactionHistoryPaginated(
        coin = NetworkName.CENTRALITY,
        limit = 100,
        pageParam = mapOf("page" to 0)  // page 0-based
    )
}

// ─── sendCoin(from, to, ser, amount, fee, serviceFee, memo, handler)
// Trước:
CoinsManager.shared.sendCoin(fromAddr, toStr, serStr, amount, fee, sFee, memo,
    object : SendCoinHandle {
        override fun completionHandler(txID: String, success: Boolean, err: String) { }
    })
// Sau — Cách 1: Unified sendCoin (recommended cho tất cả chain trừ BTC)
launch {
    val result = manager.sendCoin(
        coin = NetworkName.CARDANO,
        toAddress = "addr1q...",
        amount = 2.0,        // ADA (tự convert lovelace bên trong)
        networkFee = 0.2      // ADA
    )
    // TON với memo:
    val tonResult = manager.sendCoin(
        coin = NetworkName.TON,
        toAddress = "EQ...",
        amount = 0.5,
        memo = MemoData("memo text", null)
    )
    // ETH (tự build EIP-155 tx + sign + submit):
    val ethResult = manager.sendCoin(
        coin = NetworkName.ETHEREUM,
        toAddress = "0x...",
        amount = 0.1          // 0.1 ETH
    )
    // XRP với destinationTag:
    val xrpResult = manager.sendCoin(
        coin = NetworkName.XRP,
        toAddress = "r...",
        amount = 10.0,         // 10 XRP
        memo = MemoData(null, 12345u)  // destinationTag
    )
}
// Sau — Cách 2: Per-chain methods (vẫn hoạt động)
launch {
    val ada = manager.sendCardano(toAddress, amountLovelace, feeLovelace)
    val cen = manager.sendCentrality(from, to, amount, assetId)
    val mid = manager.sendMidnight(toAddress, amount)
    val eth = manager.sendEth(toAddress, amountEth = 0.1)
    val erc20 = manager.sendErc20Token(contractAddress, toAddress, amount = 1000L)
    val xrp = manager.sendXrp(toAddress, amountXrp = 10.0, destinationTag = 12345L)
}
// Sau — Cách 3: Pre-signed (legacy API, vẫn hoạt động)
launch {
    val btc = manager.transfer(NetworkName.BTC, signedTxJson)
}

// ─── estimateFee(...) ───────────────────────────────────────────────
// Trước: CoinsManager.shared.estimateFee(amount, ser, fee, 0.0, 0.0, network, handler)
// Sau — Cách 1: Unified estimateFee (recommended)
launch {
    val fee = manager.estimateFee(coin = NetworkName.CARDANO)
    // FeeEstimateResult(fee, gasLimit?, gasPrice?, unit, success, error?)
    println("Fee: ${fee.fee} ${fee.unit}")

    // ETH/Arbitrum: dynamic gas estimation
    val ethFee = manager.estimateFee(
        coin = NetworkName.ETHEREUM,
        amount = 0.1,
        fromAddress = "0x...",
        toAddress = "0x..."
    )
    // TON: dynamic estimation via dummy BOC
    val tonFee = manager.estimateFee(coin = NetworkName.TON, amount = 0.5)
}
// Sau — Cách 2: Static default (vẫn hoạt động)
val fee = ACTCoin.Cardano.feeDefault()  // Cardano, Centrality, Ripple
```

### ITokenManager methods

```kotlin
// ─── getTokenBalance ────────────────────────────────────────────────
// Trước: CoinsManager.shared.getTokenBalance(ACTCoin.TON, addr, contract, handler)
// Sau:
launch { val result = manager.getTokenBalance(NetworkName.TON, addr, contract) }

// ─── getTokenTransactions ───────────────────────────────────────────
// Trước: CoinsManager.shared.getTokenTransactions(ACTCoin.TON, addr, contract, handler)
// Sau: Dùng chain manager trực tiếp
launch {
    val tonManager = TonManager(mnemonic)
    val txs = tonManager.getTokenTransactionHistory(addr, contract, coinNetwork)
}

// ─── sendToken ──────────────────────────────────────────────────────
// Trước: CoinsManager.shared.sendToken(ACTCoin.TON, to, contract, amount, decimals, memo, handler)
// Sau:
launch { val result = manager.sendToken(NetworkName.TON, signedTokenTxData) }
// Cardano native token:
launch { val result = manager.sendToken(to, policyId, assetName, amount, fee) }
```

### INFTManager methods

```kotlin
// ─── getNFTs ────────────────────────────────────────────────────────
// Trước: CoinsManager.shared.getNFTs(ACTCoin.TON, addr, handler)
// Sau:
launch { val nfts = manager.getNFTs(NetworkName.TON, addr) }

// ─── transferNFT ────────────────────────────────────────────────────
// Trước: CoinsManager.shared.transferNFT(ACTCoin.TON, nftAddr, toAddr, memo, handler)
// Sau:
launch { val result = manager.transferNFT(NetworkName.TON, nftAddr, toAddr, memo) }
```

### Properties & Config

```kotlin
// ─── mnemonic ───────────────────────────────────────────────────────
// Trước: CoinsManager.shared.mnemonic = "..."
// Sau:
val manager = CommonCoinsManager(mnemonic = "...")

// ─── API keys ───────────────────────────────────────────────────────
// Trước: CoinsManager.shared.apiKeyInfura = "..."
// Sau (Config global, đã có trong commonMain):
Config.shared.apiKeyInfura = "..."
Config.shared.apiKeyExplorer = "..."
Config.shared.apiKeyOwlRacle = "..."
Config.shared.apiKeyToncenter = "..."
```

### Checklist xóa CoinsManager

1. Thay tất cả `CoinsManager.shared.xxx` → `CommonCoinsManager` methods
2. Thay `ACTCoin.Xxx` → `NetworkName.XXX`
3. Thay callback interfaces → coroutine `launch { }`
4. Thay `ACTAddress` → `String`
5. Thay `CoinsManager.shared.mnemonic = "..."` → `CommonCoinsManager(mnemonic = "...")`
6. Thay `CoinsManager.shared.apiKeyXxx` → `Config.shared.apiKeyXxx`
7. Xóa `CoinsManager.kt` khỏi androidMain
8. Xóa `TonService.kt` khỏi androidMain (đã tích hợp trong CommonCoinsManager)
9. Xóa callback interfaces: `BalanceHandle`, `TransactionsHandle`, `SendCoinHandle`, `EstimateFeeHandle`
10. Verify compile trên Android, iOS, JVM

---

## Lấy Chain Manager riêng (`getChainManager`)

`CommonCoinsManager` cung cấp unified API cho tất cả chain. Khi cần truy cập các method riêng của từng chain manager (ví dụ: local BTC builder, Cardano Byron address, TON staking pool cụ thể), dùng `getChainManager()` rồi cast sang concrete type.

```kotlin
import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.enums.NetworkName

val manager = CommonCoinsManager(mnemonic = mnemonic)
```

### BitcoinManager

```kotlin
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType

val btcManager = manager.getChainManager(NetworkName.BTC) as BitcoinManager

// Address generation (3 loại)
val native = btcManager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, accountIndex = 0)
val nested = btcManager.getAddressByType(BitcoinAddressType.NESTED_SEGWIT, accountIndex = 0)
val legacy = btcManager.getAddressByType(BitcoinAddressType.LEGACY, accountIndex = 0)

// Multi-account (HD wallet)
val account0 = btcManager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, accountIndex = 0)
val account1 = btcManager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, accountIndex = 1)

launch {
    // Local builder — build + sign client-side, broadcast qua Esplora
    val result = btcManager.sendBtcLocal(
        toAddress = "bc1q...",
        amountSatoshi = 100_000,
        addressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex = 0,
        feeRateSatPerVbyte = 15  // null = auto từ mempool.space
    )

    // Local fee estimation
    val feeSat = btcManager.estimateFeeLocal(
        toAddress = "bc1q...",
        amountSatoshi = 100_000,
        addressType = BitcoinAddressType.NATIVE_SEGWIT
    )

    // BlockCypher builder (legacy option)
    val result2 = btcManager.sendBtc(
        toAddress = "bc1q...",
        amountSatoshi = 100_000,
        addressType = BitcoinAddressType.NATIVE_SEGWIT
    )

    // BlockCypher fee estimation
    val feeSat2 = btcManager.estimateFee(
        toAddress = "bc1q...",
        amountSatoshi = 100_000
    )
}
```

### EthereumManager

```kotlin
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager

val ethManager = manager.getChainManager(NetworkName.ETHEREUM) as EthereumManager
val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)

launch {
    // Send ETH (amount in wei via BigInteger)
    val amountWei = ethManager.ethToWei(0.1)
    val result = ethManager.sendEthBigInt("0x...", amountWei, coinNetwork)

    // Send ERC-20 token
    val tokenResult = ethManager.sendErc20Token(
        contractAddress = "0xToken...",
        toAddress = "0xRecipient...",
        amount = 1_000_000L,
        coinNetwork = coinNetwork
    )

    // Gas estimation
    val gasPrice = ethManager.getGasPrice(coinNetwork)
    val fee = ethManager.estimateFee(
        FeeEstimateParams("0xFrom...", "0xTo...", 0.1), coinNetwork
    )

    // NFT listing
    val nfts = ethManager.getNFTs("0xWallet...", coinNetwork)
}
```

### CardanoManager

```kotlin
import com.lybia.cryptowallet.wallets.cardano.CardanoManager

val cardanoManager = manager.getChainManager(NetworkName.CARDANO) as CardanoManager

// Shelley address (mặc định)
val shelley = cardanoManager.getShelleyAddress(account = 0, index = 0)

// Byron address (legacy)
val byron = cardanoManager.getByronAddress(index = 0)

// Staking address
val staking = cardanoManager.getStakingAddress(account = 0)

// Derive nhiều address
val addresses = (0..4).map { cardanoManager.getShelleyAddress(account = 0, index = it) }

launch {
    // Build + sign + submit transaction
    val signedTx = cardanoManager.buildAndSignTransaction(
        toAddress = "addr1q...",
        amount = 2_000_000L,  // lovelace
        fee = 200_000L
    )

    // Native token
    val tokenBalance = cardanoManager.getTokenBalance("addr1q...", "policyId", "assetName")
    val tokenTx = cardanoManager.sendToken("addr1q...", "policyId", "assetName", 100L, 200_000L)
}
```

### TonManager

```kotlin
import com.lybia.cryptowallet.wallets.ton.TonManager

val tonManager = manager.getChainManager(NetworkName.TON) as TonManager
val coinNetwork = CoinNetwork(NetworkName.TON)

launch {
    // Sign + broadcast TON transfer
    val seqno = tonManager.getSeqno(coinNetwork)
    val boc = tonManager.signTransaction("EQ...", amountNano = 500_000_000L, seqno = seqno, memo = "hello")
    val result = tonManager.transfer(boc, coinNetwork)

    // Jetton token balance
    val jettonBalance = tonManager.getTokenBalance("EQWallet...", "EQJettonMaster...", coinNetwork)

    // NFT listing
    val nfts = tonManager.getNFTs("EQWallet...", coinNetwork)

    // Staking (Nominator Pool)
    val stakeResult = tonManager.stake(1_000_000_000L, "EQPool...", coinNetwork)
    val stakingBalance = tonManager.getStakingBalance("EQWallet...", "EQPool...", coinNetwork)
}
```

### RippleManager

```kotlin
import com.lybia.cryptowallet.wallets.ripple.RippleManager

val xrpManager = manager.getChainManager(NetworkName.XRP) as RippleManager

launch {
    // Send XRP (amount in drops)
    val result = xrpManager.sendXrp(
        toAddress = "r...",
        amountDrops = 10_000_000L,  // 10 XRP
        feeDrops = 12L,
        destinationTag = 12345L
    )

    // Dynamic fee estimation
    val feeXrp = xrpManager.estimateFeeDynamicXrp()

    // Paginated transaction history
    val (txs, marker) = xrpManager.getTransactionHistoryPaginated("r...", limit = 50)
}
```

### MidnightManager

```kotlin
import com.lybia.cryptowallet.wallets.midnight.MidnightManager

val midManager = manager.getChainManager(NetworkName.MIDNIGHT) as MidnightManager

launch {
    val txHash = midManager.sendTDust("midnight1q...", amount = 1_000_000L)
}
```

### CentralityManager

```kotlin
import com.lybia.cryptowallet.wallets.centrality.CentralityManager

val cenManager = manager.getChainManager(NetworkName.CENTRALITY) as CentralityManager

launch {
    // Resolve address (cần API call)
    val address = cenManager.getAddressAsync()

    // Send CENNZ (full orchestration: getRuntimeVersion → build extrinsic → sign → submit)
    val result = cenManager.sendCoin(
        fromAddress = "cX...",
        toAddress = "cX...",
        amount = 1.5,
        assetId = 1
    )

    // Paginated transaction history
    val txs = cenManager.getTransactionHistoryPaginated("cX...", row = 100, page = 0)
}
```

### Pattern chung

```kotlin
// Tất cả chain manager đều implement IWalletManager
val anyManager = manager.getChainManager(NetworkName.XXX)
val address = anyManager.getAddress()

launch {
    val balance = anyManager.getBalance(null, CoinNetwork(NetworkName.XXX))
    val txs = anyManager.getTransactionHistory(null, CoinNetwork(NetworkName.XXX))
}

// Cast sang interface cụ thể khi cần
if (anyManager is ITokenManager) {
    val tokenBalance = anyManager.getTokenBalance(address, contract, coinNetwork)
}
if (anyManager is INFTManager) {
    val nfts = anyManager.getNFTs(address, coinNetwork)
}
if (anyManager is IStakingManager) {
    val rewards = anyManager.getStakingRewards(address, coinNetwork)
}
if (anyManager is IFeeEstimator) {
    val fee = anyManager.estimateFee(params, coinNetwork)
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
7. Sau khi xóa `CoinsManager`, Android app dùng `CommonCoinsManager` trực tiếp — cùng API với iOS
8. Bridge operations hiện dùng simulated responses — chờ API thật sẵn sàng
9. TON `unstake()` trả về `UnsupportedOperation`
10. Bitcoin có 2 option gửi: `sendBtcLocal()` (local builder, recommended) và `sendBtc()` (BlockCypher). Local builder build + sign hoàn toàn client-side, chỉ gọi Esplora API để fetch UTXOs và broadcast
11. `getChainManager(coin)` trả về `IWalletManager` — cast sang concrete type (BitcoinManager, EthereumManager, ...) để truy cập chain-specific methods
12. Chain managers được tạo lazy — chỉ khởi tạo khi lần đầu truy cập qua `getAddress()`, `getBalance()`, hoặc `getChainManager()`
