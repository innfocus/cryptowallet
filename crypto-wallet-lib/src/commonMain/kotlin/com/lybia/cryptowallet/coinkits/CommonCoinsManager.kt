package com.lybia.cryptowallet.coinkits

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.IBridgeManager
import com.lybia.cryptowallet.base.IFeeEstimator
import com.lybia.cryptowallet.base.INFTManager
import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.base.ITokenManager
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.errors.WalletError
import com.lybia.cryptowallet.wallets.bridge.BridgeManagerFactory
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import com.lybia.cryptowallet.wallets.cardano.CardanoError
import com.lybia.cryptowallet.wallets.centrality.CentralityManager
import com.lybia.cryptowallet.wallets.ton.TonManager
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.FeeEstimate
import com.lybia.cryptowallet.models.FeeEstimateParams
import com.lybia.cryptowallet.models.MemoData

/**
 * Result wrapper for balance queries.
 */
data class BalanceResult(
    val balance: Double,
    val success: Boolean,
    val error: String? = null
)

/**
 * Result wrapper for send operations.
 */
data class SendResult(
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * Result wrapper for token balance queries.
 */
data class TokenBalanceResult(
    val balance: Long,
    val success: Boolean,
    val error: String? = null
)

/**
 * Unified API facade for all coin operations in commonMain.
 *
 * Delegates to chain-specific managers created via [ChainManagerFactory].
 * Supports lazy manager creation — managers are only instantiated when first accessed.
 */
class CommonCoinsManager(
    private val mnemonic: String,
    private val configs: Map<NetworkName, ChainConfig> = emptyMap()
) {
    private val logger = Logger.withTag("CommonCoinsManager")
    private val managers = mutableMapOf<NetworkName, IWalletManager>()

    /**
     * Secondary constructor for backward compatibility with existing tests.
     * Accepts optional CardanoApiService and MidnightApiService for DI/testing.
     */
    constructor(
        mnemonic: String,
        cardanoApiService: com.lybia.cryptowallet.services.CardanoApiService?,
        midnightApiService: com.lybia.cryptowallet.services.MidnightApiService? = null
    ) : this(mnemonic) {
        // Pre-populate managers with injected services
        if (cardanoApiService != null) {
            managers[NetworkName.CARDANO] = com.lybia.cryptowallet.wallets.cardano.CardanoManager(
                mnemonic = mnemonic,
                apiService = cardanoApiService
            )
        }
        if (midnightApiService != null) {
            managers[NetworkName.MIDNIGHT] = com.lybia.cryptowallet.wallets.midnight.MidnightManager(
                mnemonic = mnemonic,
                apiService = midnightApiService
            )
        }
    }

    private fun getOrCreateManager(coin: NetworkName): IWalletManager {
        return managers.getOrPut(coin) {
            ChainManagerFactory.createWalletManager(
                coin, mnemonic, configs[coin] ?: ChainConfig.default(coin)
            )
        }
    }

    // ── Address generation ──────────────────────────────────────────────────

    fun getAddress(coin: NetworkName): String {
        return getOrCreateManager(coin).getAddress()
    }

    /**
     * Get address with async initialization support.
     * For chains like Centrality that require an API call to derive the address,
     * this method ensures the address is initialized before returning.
     */
    suspend fun getAddressAsync(coin: NetworkName): String {
        val manager = getOrCreateManager(coin)
        if (coin == NetworkName.CENTRALITY && manager is CentralityManager) {
            return manager.getAddressAsync()
        }
        return manager.getAddress()
    }

    /**
     * Get multiple addresses for a chain (HD wallet derivation).
     *
     * - Cardano: returns Shelley base addresses derived from account=0, index 0..count-1
     * - Bitcoin: returns Native SegWit addresses derived from account 0..count-1
     * - Centrality: returns single address (resolved from API)
     * - Other chains: returns single address (most chains use 1 address)
     *
     * @param coin Target chain
     * @param count Number of addresses to derive (default 1)
     * @return List of address strings
     */
    suspend fun addresses(coin: NetworkName, count: Int = 1): List<String> {
        val manager = getOrCreateManager(coin)
        return when (coin) {
            NetworkName.CARDANO -> {
                val cardanoManager = manager as com.lybia.cryptowallet.wallets.cardano.CardanoManager
                (0 until count).map { index ->
                    cardanoManager.getShelleyAddress(account = 0, index = index)
                }
            }

            NetworkName.BTC -> {
                val btcManager = manager as BitcoinManager
                (0 until count).map { account ->
                    btcManager.getNativeSegWitAddress(account) ?: ""
                }.filter { it.isNotEmpty() }
            }

            NetworkName.CENTRALITY -> {
                val addr = if (manager is CentralityManager) {
                    manager.getAddressAsync()
                } else {
                    manager.getAddress()
                }
                if (addr.isNotEmpty()) listOf(addr) else emptyList()
            }

            else -> {
                val addr = manager.getAddress()
                if (addr.isNotEmpty()) listOf(addr) else emptyList()
            }
        }
    }

    /**
     * Get the first address for a chain.
     * Equivalent to CoinsManager.firstAddress(coin).
     *
     * @param coin Target chain
     * @return Address string, or empty string if not available
     */
    fun firstAddress(coin: NetworkName): String {
        return getAddress(coin)
    }

    // ── Balance queries ─────────────────────────────────────────────────────

    suspend fun getBalance(coin: NetworkName, address: String? = null): BalanceResult {
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val balance = manager.getBalance(address, coinNetwork)
            BalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get balance for $coin" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    // ── Transaction history ─────────────────────────────────────────────────

    suspend fun getTransactionHistory(coin: NetworkName, address: String? = null): Any? {
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            manager.getTransactionHistory(address, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get transaction history for $coin" }
            null
        }
    }

    /**
     * Result wrapper for paginated transaction history queries.
     *
     * @param transactions Raw transaction list (type depends on chain)
     * @param hasMore Whether more pages are available
     * @param nextPageParam Opaque pagination token to pass to the next call
     * @param success Whether the query succeeded
     * @param error Error message if failed
     */
    data class TransactionHistoryResult(
        val transactions: Any? = null,
        val hasMore: Boolean = false,
        val nextPageParam: Map<String, Any?>? = null,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Get transaction history with pagination support.
     *
     * Pagination behavior per chain:
     * - XRP: uses `marker` (ledger + seq) from Ripple JSON-RPC `account_tx`
     * - Centrality: uses `row` + `page` params
     * - Cardano: Blockfrost returns all txs per address (no pagination in current impl)
     * - Other chains: returns all available transactions (no pagination)
     *
     * @param coin Target chain
     * @param address Optional address override
     * @param limit Max number of transactions per page
     * @param pageParam Pagination token from previous [TransactionHistoryResult.nextPageParam]
     * @return TransactionHistoryResult with transactions and pagination info
     */
    suspend fun getTransactionHistoryPaginated(
        coin: NetworkName,
        address: String? = null,
        limit: Int = 100,
        pageParam: Map<String, Any?>? = null
    ): TransactionHistoryResult {
        return try {
            when (coin) {
                NetworkName.XRP -> {
                    val manager = getOrCreateManager(coin)
                    val rippleManager = manager as com.lybia.cryptowallet.wallets.ripple.RippleManager
                    val addr = address ?: manager.getAddress()
                    val marker = pageParam?.let {
                        com.lybia.cryptowallet.models.ripple.RippleMarker(
                            ledger = (it["ledger"] as? Number)?.toLong(),
                            seq = (it["seq"] as? Number)?.toLong()
                        )
                    }
                    val response = rippleManager.getTransactionHistoryPaginated(addr, limit, marker)
                    TransactionHistoryResult(
                        transactions = response.first,
                        hasMore = response.second != null,
                        nextPageParam = response.second?.let {
                            mapOf("ledger" to it.ledger, "seq" to it.seq)
                        },
                        success = true
                    )
                }

                NetworkName.CENTRALITY -> {
                    val manager = getOrCreateManager(coin) as CentralityManager
                    val addr = address ?: manager.getAddressAsync()
                    val page = (pageParam?.get("page") as? Number)?.toInt() ?: 0
                    val result = manager.getTransactionHistoryPaginated(addr, limit, page)
                    val nextPage = if (result.isNotEmpty() && result.size >= limit) page + 1 else null
                    TransactionHistoryResult(
                        transactions = result,
                        hasMore = nextPage != null,
                        nextPageParam = nextPage?.let { mapOf("page" to it) },
                        success = true
                    )
                }

                else -> {
                    // Chains without pagination — delegate to standard method
                    val txs = getTransactionHistory(coin, address)
                    TransactionHistoryResult(
                        transactions = txs,
                        hasMore = false,
                        nextPageParam = null,
                        success = true
                    )
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to get paginated transaction history for $coin" }
            TransactionHistoryResult(success = false, error = e.message)
        }
    }

    // ── Transfer ────────────────────────────────────────────────────────────

    suspend fun transfer(coin: NetworkName, dataSigned: String): SendResult {
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val result = manager.transfer(dataSigned, coinNetwork)
            SendResult(
                txHash = result.txHash ?: "",
                success = result.success,
                error = result.error
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to transfer for $coin" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── Token operations ────────────────────────────────────────────────────

    suspend fun getTokenBalance(
        coin: NetworkName,
        address: String,
        contractAddress: String
    ): BalanceResult {
        if (!supportsTokens(coin)) {
            return BalanceResult(0.0, false, "Token not supported for $coin")
        }
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val tokenManager = manager as? ITokenManager
            if (tokenManager != null) {
                val balance = tokenManager.getTokenBalance(address, contractAddress, coinNetwork)
                BalanceResult(balance = balance, success = true)
            } else {
                BalanceResult(0.0, false, "Token manager not available for $coin")
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to get token balance for $coin" }
            BalanceResult(0.0, false, e.message)
        }
    }

    suspend fun sendToken(coin: NetworkName, dataSigned: String): SendResult {
        if (!supportsTokens(coin)) {
            return SendResult("", false, "Token not supported for $coin")
        }
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val tokenManager = manager as? ITokenManager
            if (tokenManager != null) {
                val txHash = tokenManager.transferToken(dataSigned, coinNetwork)
                SendResult(txHash = txHash ?: "", success = txHash != null)
            } else {
                SendResult("", false, "Token manager not available for $coin")
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to send token for $coin" }
            SendResult("", false, e.message)
        }
    }

    // ── NFT operations ──────────────────────────────────────────────────────

    suspend fun getNFTs(coin: NetworkName, address: String): List<NFTItem>? {
        if (!supportsNFTs(coin)) return null
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val nftManager = manager as? INFTManager
            nftManager?.getNFTs(address, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get NFTs for $coin" }
            null
        }
    }

    suspend fun transferNFT(
        coin: NetworkName,
        nftAddress: String,
        toAddress: String,
        memo: String? = null
    ): SendResult {
        if (!supportsNFTs(coin)) {
            return SendResult("", false, "NFT not supported for $coin")
        }
        return try {
            val manager = getOrCreateManager(coin)
            val coinNetwork = CoinNetwork(coin)
            val nftManager = manager as? INFTManager
            if (nftManager != null) {
                val result = nftManager.transferNFT(nftAddress, toAddress, memo, coinNetwork)
                SendResult(
                    txHash = result.txHash ?: "",
                    success = result.success,
                    error = result.error
                )
            } else {
                SendResult("", false, "NFT manager not available for $coin")
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to transfer NFT for $coin" }
            SendResult("", false, e.message)
        }
    }

    // ── Capability checking ─────────────────────────────────────────────────

    /**
     * Check if a coin supports token operations.
     * Ethereum, Cardano, and TON support tokens.
     */
    fun supportsTokens(coin: NetworkName): Boolean {
        return coin in setOf(
            NetworkName.ETHEREUM,
            NetworkName.ARBITRUM,
            NetworkName.CARDANO,
            NetworkName.TON
        )
    }

    /**
     * Check if a coin supports NFT operations.
     * Ethereum and TON support NFTs.
     */
    fun supportsNFTs(coin: NetworkName): Boolean {
        return coin in setOf(
            NetworkName.ETHEREUM,
            NetworkName.ARBITRUM,
            NetworkName.TON
        )
    }

    /**
     * Check if a coin supports fee estimation.
     * Only Ethereum/Arbitrum supports fee estimation via IFeeEstimator.
     */
    fun supportsFeeEstimation(coin: NetworkName): Boolean {
        return coin in setOf(
            NetworkName.ETHEREUM,
            NetworkName.ARBITRUM
        )
    }

    // ── Staking operations ──────────────────────────────────────────────

    private val stakingChains = setOf(NetworkName.CARDANO, NetworkName.TON)

    /**
     * Check if a coin supports staking operations.
     * Only Cardano and TON support staking.
     */
    fun supportsStaking(coin: NetworkName): Boolean {
        return coin in stakingChains
    }

    /**
     * Delegate stake to a pool.
     * Delegates to the [IStakingManager] of the corresponding chain.
     */
    suspend fun stake(coin: NetworkName, amount: Long, poolAddress: String): SendResult {
        if (!supportsStaking(coin)) {
            return SendResult(
                txHash = "",
                success = false,
                error = WalletError.UnsupportedOperation("stake", coin.name).message
            )
        }
        return try {
            val manager = getOrCreateManager(coin)
            val stakingManager = manager as IStakingManager
            val coinNetwork = CoinNetwork(coin)
            val result = stakingManager.stake(amount, poolAddress, coinNetwork)
            SendResult(
                txHash = result.txHash ?: "",
                success = result.success,
                error = result.error
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to stake for $coin" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Undelegate / unstake.
     * Delegates to the [IStakingManager] of the corresponding chain.
     */
    suspend fun unstake(coin: NetworkName, amount: Long): SendResult {
        if (!supportsStaking(coin)) {
            return SendResult(
                txHash = "",
                success = false,
                error = WalletError.UnsupportedOperation("unstake", coin.name).message
            )
        }
        return try {
            val manager = getOrCreateManager(coin)
            val stakingManager = manager as IStakingManager
            val coinNetwork = CoinNetwork(coin)
            val result = stakingManager.unstake(amount, coinNetwork)
            SendResult(
                txHash = result.txHash ?: "",
                success = result.success,
                error = result.error
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to unstake for $coin" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Query staking rewards for a chain.
     * Delegates to the [IStakingManager] of the corresponding chain.
     */
    suspend fun getStakingRewards(coin: NetworkName, address: String? = null): BalanceResult {
        if (!supportsStaking(coin)) {
            return BalanceResult(
                balance = 0.0,
                success = false,
                error = WalletError.UnsupportedOperation("getStakingRewards", coin.name).message
            )
        }
        return try {
            val manager = getOrCreateManager(coin)
            val stakingManager = manager as IStakingManager
            val coinNetwork = CoinNetwork(coin)
            val addr = address ?: manager.getAddress()
            val rewards = stakingManager.getStakingRewards(addr, coinNetwork)
            BalanceResult(balance = rewards, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get staking rewards for $coin" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    /**
     * Query staking balance for a chain and pool.
     * Delegates to the [IStakingManager] of the corresponding chain.
     */
    suspend fun getStakingBalance(
        coin: NetworkName,
        address: String? = null,
        poolAddress: String
    ): BalanceResult {
        if (!supportsStaking(coin)) {
            return BalanceResult(
                balance = 0.0,
                success = false,
                error = WalletError.UnsupportedOperation("getStakingBalance", coin.name).message
            )
        }
        return try {
            val manager = getOrCreateManager(coin)
            val stakingManager = manager as IStakingManager
            val coinNetwork = CoinNetwork(coin)
            val addr = address ?: manager.getAddress()
            val balance = stakingManager.getStakingBalance(addr, poolAddress, coinNetwork)
            BalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get staking balance for $coin" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    // ── Bridge operations ──────────────────────────────────────────────

    /**
     * Check if bridging between two chains is supported.
     * Delegates to [BridgeManagerFactory.supportsBridge].
     */
    fun supportsBridge(fromChain: NetworkName, toChain: NetworkName): Boolean {
        return BridgeManagerFactory.supportsBridge(fromChain, toChain)
    }

    /**
     * Bridge asset between two chains.
     * Delegates to the appropriate [IBridgeManager] implementation.
     */
    suspend fun bridgeAsset(fromChain: NetworkName, toChain: NetworkName, amount: Long): SendResult {
        if (!supportsBridge(fromChain, toChain)) {
            return SendResult(
                txHash = "",
                success = false,
                error = WalletError.UnsupportedOperation(
                    "bridgeAsset", "$fromChain → $toChain"
                ).message
            )
        }
        return try {
            val bridgeManager = BridgeManagerFactory.createBridgeManager(
                fromChain, toChain, mnemonic, configs
            ) ?: return SendResult(
                txHash = "",
                success = false,
                error = WalletError.UnsupportedOperation(
                    "bridgeAsset", "$fromChain → $toChain"
                ).message
            )
            val result = bridgeManager.bridgeAsset(fromChain, toChain, amount)
            SendResult(
                txHash = result.txHash ?: "",
                success = result.success,
                error = result.error
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to bridge asset from $fromChain to $toChain" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Query bridge transaction status.
     * Delegates to the appropriate [IBridgeManager] implementation.
     *
     * Note: Since getBridgeStatus only takes a txHash, we need to try all
     * bridge managers. In practice, the caller should know which bridge pair
     * the txHash belongs to. For simplicity, we try Cardano↔Midnight first,
     * then Ethereum↔Arbitrum.
     */
    suspend fun getBridgeStatus(txHash: String): String {
        return try {
            // Try each bridge manager until one succeeds
            val bridgePairs = listOf(
                NetworkName.CARDANO to NetworkName.MIDNIGHT,
                NetworkName.ETHEREUM to NetworkName.ARBITRUM
            )
            for ((from, to) in bridgePairs) {
                val bridgeManager = BridgeManagerFactory.createBridgeManager(
                    from, to, mnemonic, configs
                )
                if (bridgeManager != null) {
                    return bridgeManager.getBridgeStatus(txHash)
                }
            }
            "unknown"
        } catch (e: Exception) {
            logger.e(e) { "Failed to get bridge status for $txHash" }
            "unknown"
        }
    }

    // ── Unified sendCoin — dispatches per-chain ───────────────────────────

    /**
     * Unified send coin operation that dispatches to the appropriate chain manager.
     * Mirrors the legacy CoinsManager.sendCoin() but as a suspend function.
     *
     * @param coin Target chain
     * @param toAddress Destination address
     * @param amount Amount in the chain's major unit (e.g. ADA, not lovelace)
     * @param networkFee Network fee in the chain's major unit
     * @param serviceFee Optional service fee (for service-address transactions)
     * @param serviceAddress Optional service address
     * @param memo Optional memo / destination tag
     * @return SendResult with txHash on success
     */
    suspend fun sendCoin(
        coin: NetworkName,
        toAddress: String,
        amount: Double,
        networkFee: Double = 0.0,
        serviceFee: Double = 0.0,
        serviceAddress: String? = null,
        memo: MemoData? = null
    ): SendResult {
        return try {
            when (coin) {
                NetworkName.CARDANO -> {
                    val amountLovelace = (amount * 1_000_000).toLong()
                    val feeLovelace = (networkFee * 1_000_000).toLong()
                    sendCardano(toAddress, amountLovelace, feeLovelace)
                }

                NetworkName.MIDNIGHT -> {
                    val amountUnits = (amount * 1_000_000).toLong()
                    sendMidnight(toAddress, amountUnits)
                }

                NetworkName.CENTRALITY -> {
                    val fromAddress = getAddress(NetworkName.CENTRALITY)
                    sendCentrality(fromAddress, toAddress, amount)
                }

                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
                    val coinNetwork = CoinNetwork(NetworkName.TON)
                    val seqno = tonManager.getSeqno(coinNetwork)
                    val amountNano = (amount * 1_000_000_000).toLong()
                    val memoStr = memo?.memo?.takeIf { it.isNotEmpty() }
                    val bocBase64 = tonManager.signTransaction(toAddress, amountNano, seqno, memoStr)
                    val result = tonManager.transfer(bocBase64, coinNetwork)
                    SendResult(
                        txHash = result.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )
                }

                NetworkName.BTC -> {
                    val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
                    val amountSatoshi = (amount * 100_000_000).toLong()
                    val result = btcManager.sendBtc(toAddress, amountSatoshi)
                    SendResult(
                        txHash = result.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )
                }

                NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                    val ethManager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.ethereum.EthereumManager
                    val coinNetwork = CoinNetwork(coin)
                    val amountWei = ethManager.ethToWei(amount)
                    val result = ethManager.sendEthBigInt(toAddress, amountWei, coinNetwork)
                    SendResult(
                        txHash = result.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )
                }

                NetworkName.XRP -> {
                    val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
                    val amountDrops = (amount * 1_000_000).toLong()
                    val feeDrops = if (networkFee > 0) (networkFee * 1_000_000).toLong() else 12L
                    val destTag = memo?.destinationTag?.toLong()
                    val result = rippleManager.sendXrp(toAddress, amountDrops, feeDrops, destTag)
                    SendResult(
                        txHash = result.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )
                }

                else -> {
                    SendResult(txHash = "", success = false, error = "sendCoin not supported for $coin")
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to sendCoin for $coin" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── Fee estimation ──────────────────────────────────────────────────────

    /**
     * Result wrapper for fee estimation.
     */
    data class FeeEstimateResult(
        val fee: Double,
        val gasLimit: Long? = null,
        val gasPrice: Long? = null,
        val unit: String = "native",
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Estimate transaction fee for a given chain.
     *
     * - Ethereum/Arbitrum: delegates to [IFeeEstimator.estimateFee] (gas-based)
     * - TON: estimates via TonApiService using a dummy BOC
     * - Cardano, Centrality, Ripple, Midnight: returns static default fee
     * - BTC: returns 0 (fee calculated during UTXO selection)
     *
     * @param coin Target chain
     * @param amount Transaction amount in the chain's major unit
     * @param fromAddress Sender address (required for ETH/Arbitrum gas estimation)
     * @param toAddress Recipient address (required for ETH/Arbitrum gas estimation)
     * @return FeeEstimateResult with fee in the chain's major unit
     */
    suspend fun estimateFee(
        coin: NetworkName,
        amount: Double = 0.0,
        fromAddress: String? = null,
        toAddress: String? = null
    ): FeeEstimateResult {
        return try {
            when (coin) {
                NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                    val manager = getOrCreateManager(coin)
                    val feeEstimator = manager as? IFeeEstimator
                    if (feeEstimator != null) {
                        val from = fromAddress ?: getAddress(coin)
                        val to = toAddress ?: from
                        val coinNetwork = CoinNetwork(coin)
                        val estimate = feeEstimator.estimateFee(
                            FeeEstimateParams(from, to, amount), coinNetwork
                        )
                        FeeEstimateResult(
                            fee = estimate.fee,
                            gasLimit = estimate.gasLimit,
                            gasPrice = estimate.gasPrice,
                            unit = estimate.unit,
                            success = true
                        )
                    } else {
                        FeeEstimateResult(fee = 0.0, success = false, error = "IFeeEstimator not available for $coin")
                    }
                }

                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
                    val coinNetwork = CoinNetwork(NetworkName.TON)
                    val seqno = tonManager.getSeqno(coinNetwork)
                    val amountNano = (amount * 1_000_000_000).toLong()
                    // Sign a dummy self-transfer to get a valid BOC for estimation
                    val bocBase64 = tonManager.signTransaction(
                        toAddress = tonManager.getAddress(),
                        amountNano = amountNano,
                        seqno = seqno
                    )
                    val fee = tonManager.estimateFee(coinNetwork, tonManager.getAddress(), bocBase64)
                    FeeEstimateResult(fee = fee, unit = "TON", success = true)
                }

                NetworkName.CARDANO -> {
                    FeeEstimateResult(
                        fee = ACTCoin.Cardano.feeDefault(),
                        unit = "ADA",
                        success = true
                    )
                }

                NetworkName.MIDNIGHT -> {
                    FeeEstimateResult(
                        fee = ACTCoin.Midnight.feeDefault(),
                        unit = "tDUST",
                        success = true
                    )
                }

                NetworkName.XRP -> {
                    val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
                    val feeXrp = rippleManager.estimateFeeDynamicXrp()
                    FeeEstimateResult(
                        fee = feeXrp,
                        unit = "XRP",
                        success = true
                    )
                }

                NetworkName.CENTRALITY -> {
                    FeeEstimateResult(
                        fee = ACTCoin.Centrality.feeDefault(),
                        unit = "CENNZ",
                        success = true
                    )
                }

                NetworkName.BTC -> {
                    val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
                    val to = toAddress ?: btcManager.getAddress()
                    val amountSatoshi = (amount * 100_000_000).toLong()
                    val feeSatoshi = btcManager.estimateFee(to, amountSatoshi)
                    if (feeSatoshi != null) {
                        FeeEstimateResult(
                            fee = feeSatoshi.toDouble() / 100_000_000.0,
                            unit = "BTC",
                            success = true
                        )
                    } else {
                        FeeEstimateResult(
                            fee = ACTCoin.Bitcoin.feeDefault(),
                            unit = "BTC",
                            success = true,
                            error = "Estimation failed, using default"
                        )
                    }
                }

                else -> {
                    FeeEstimateResult(fee = 0.0, success = false, error = "Fee estimation not supported for $coin")
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to estimate fee for $coin" }
            FeeEstimateResult(fee = 0.0, success = false, error = e.message)
        }
    }

    // ── Cardano-specific operations (backward compat) ───────────────────────

    suspend fun getCardanoBalance(address: String? = null): BalanceResult {
        return getBalance(NetworkName.CARDANO, address)
    }

    suspend fun getMidnightBalance(address: String? = null): BalanceResult {
        return getBalance(NetworkName.MIDNIGHT, address)
    }

    suspend fun getCardanoTransactions(address: String? = null): Any? {
        return getTransactionHistory(NetworkName.CARDANO, address)
    }

    suspend fun getMidnightTransactions(address: String? = null): Any? {
        return getTransactionHistory(NetworkName.MIDNIGHT, address)
    }

    suspend fun getTransactions(network: NetworkName, address: String? = null): Any? {
        return getTransactionHistory(network, address)
    }

    suspend fun sendCardano(toAddress: String, amountLovelace: Long, fee: Long): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CARDANO)
            val cardanoManager = manager as com.lybia.cryptowallet.wallets.cardano.CardanoManager
            val signedTx = cardanoManager.buildAndSignTransaction(toAddress, amountLovelace, fee)
            val coinNetwork = CoinNetwork(NetworkName.CARDANO)
            val result = cardanoManager.transfer(signedTx.toBase64(), coinNetwork)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send Cardano" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    suspend fun sendMidnight(toAddress: String, amount: Long): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.MIDNIGHT)
            val midnightManager = manager as com.lybia.cryptowallet.wallets.midnight.MidnightManager
            val txHash = midnightManager.sendTDust(toAddress, amount)
            SendResult(txHash = txHash, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send Midnight" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── Centrality-specific operations ──────────────────────────────────────

    suspend fun getCentralityBalance(address: String? = null): BalanceResult {
        return getBalance(NetworkName.CENTRALITY, address)
    }

    suspend fun getCentralityTransactions(address: String? = null): Any? {
        return getTransactionHistory(NetworkName.CENTRALITY, address)
    }

    suspend fun sendCentrality(
        fromAddress: String,
        toAddress: String,
        amount: Double,
        assetId: Int = 1
    ): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CENTRALITY)
            val centralityManager = manager as CentralityManager
            val result = centralityManager.sendCoin(fromAddress, toAddress, amount, assetId)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    suspend fun getTokenBalance(
        address: String,
        policyId: String,
        assetName: String
    ): TokenBalanceResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CARDANO)
            val cardanoManager = manager as com.lybia.cryptowallet.wallets.cardano.CardanoManager
            val balance = cardanoManager.getTokenBalance(address, policyId, assetName)
            TokenBalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get token balance" }
            TokenBalanceResult(balance = 0L, success = false, error = e.message)
        }
    }

    suspend fun sendToken(
        toAddress: String,
        policyId: String,
        assetName: String,
        amount: Long,
        fee: Long
    ): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CARDANO)
            val cardanoManager = manager as com.lybia.cryptowallet.wallets.cardano.CardanoManager
            val txHash = cardanoManager.sendToken(toAddress, policyId, assetName, amount, fee)
            SendResult(txHash = txHash, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send token" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    fun getWitnessTypeForAddress(address: String): String {
        return when (CardanoAddress.getAddressType(address)) {
            CardanoAddressType.BYRON -> "bootstrap"
            CardanoAddressType.SHELLEY_BASE,
            CardanoAddressType.SHELLEY_ENTERPRISE,
            CardanoAddressType.SHELLEY_REWARD -> "vkey"
            CardanoAddressType.UNKNOWN -> throw IllegalArgumentException("Unknown address type: $address")
        }
    }

    // ── Bitcoin-specific operations ─────────────────────────────────────────

    /**
     * Send BTC directly (build + sign + submit via BlockCypher).
     * Supports all address types: Legacy (1...), Nested SegWit (3...), Native SegWit (bc1q...).
     *
     * @param toAddress Destination Bitcoin address (any format)
     * @param amountBtc Amount in BTC
     * @param addressType Sender address type (default: NATIVE_SEGWIT)
     * @param accountIndex HD wallet account index (default 0)
     */
    suspend fun sendBtc(
        toAddress: String,
        amountBtc: Double,
        addressType: com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType =
            com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): SendResult {
        return try {
            val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
            val amountSatoshi = (amountBtc * 100_000_000).toLong()
            val result = btcManager.sendBtc(toAddress, amountSatoshi, addressType, accountIndex)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send BTC" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Get a Bitcoin address by type.
     * @param addressType NATIVE_SEGWIT (bc1q...), NESTED_SEGWIT (3...), or LEGACY (1...)
     * @param accountIndex HD wallet account index (default 0)
     */
    fun getBtcAddress(
        addressType: com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType =
            com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): String {
        val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
        return btcManager.getAddressByType(addressType, accountIndex)
    }

    // ── Ethereum-specific operations ────────────────────────────────────────

    /**
     * Send ETH directly (build + sign + submit).
     * Uses EIP-1559 (type 2) when supported, falls back to legacy (type 0).
     * Amount is converted to wei using BigInteger (safe for any ETH amount).
     *
     * @param toAddress Destination address (0x-prefixed)
     * @param amountEth Amount in ETH (no overflow — uses BigInteger internally)
     * @param coin NetworkName.ETHEREUM or NetworkName.ARBITRUM
     * @param gasLimit Optional gas limit override
     * @param gasPriceGwei Optional gas price override in gwei (legacy mode)
     */
    suspend fun sendEth(
        toAddress: String,
        amountEth: Double,
        coin: NetworkName = NetworkName.ETHEREUM,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): SendResult {
        return try {
            val ethManager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.ethereum.EthereumManager
            val coinNetwork = CoinNetwork(coin)
            val amountWei = ethManager.ethToWei(amountEth)
            val result = ethManager.sendEthBigInt(toAddress, amountWei, coinNetwork, gasLimit)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send ETH" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Send ERC-20 token directly (build + sign + submit).
     * Uses EIP-1559 when supported. Token amount uses BigInteger (safe for high-decimal tokens).
     *
     * @param contractAddress ERC-20 token contract address
     * @param toAddress Recipient address
     * @param amount Token amount in smallest unit (e.g. wei for 18-decimal tokens)
     * @param coin NetworkName.ETHEREUM or NetworkName.ARBITRUM
     * @param gasLimit Optional gas limit override
     * @param gasPriceGwei Optional gas price override in gwei
     */
    suspend fun sendErc20Token(
        contractAddress: String,
        toAddress: String,
        amount: Long,
        coin: NetworkName = NetworkName.ETHEREUM,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): SendResult {
        return try {
            val ethManager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.ethereum.EthereumManager
            val coinNetwork = CoinNetwork(coin)
            val result = ethManager.sendErc20Token(contractAddress, toAddress, amount, coinNetwork, gasLimit, gasPriceGwei)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send ERC-20 token" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── XRP-specific operations ─────────────────────────────────────────────

    /**
     * Send XRP directly (build + sign + submit).
     * @param toAddress Destination r-address
     * @param amountXrp Amount in XRP (not drops)
     * @param feeXrp Fee in XRP (default: 0.000012 XRP = 12 drops)
     * @param destinationTag Optional destination tag
     */
    suspend fun sendXrp(
        toAddress: String,
        amountXrp: Double,
        feeXrp: Double = 0.000012,
        destinationTag: Long? = null
    ): SendResult {
        return try {
            val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
            val amountDrops = (amountXrp * 1_000_000).toLong()
            val feeDrops = (feeXrp * 1_000_000).toLong()
            val result = rippleManager.sendXrp(toAddress, amountDrops, feeDrops, destinationTag)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send XRP" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }
}
