package com.lybia.cryptowallet.coinkits

import kotlin.math.pow
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
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.wallets.ton.TonManager
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.FeeEstimate
import com.lybia.cryptowallet.models.FeeEstimateParams
import com.lybia.cryptowallet.models.MemoData
import kotlin.concurrent.Volatile

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
 * Result of balance validation against total required amount (including fees).
 */
data class BalanceValidationResult(
    val sufficient: Boolean,
    val totalRequired: Double,
    val deficit: Double = 0.0
)

/**
 * Safely convert a Double amount in major unit to Long in smallest unit.
 *
 * Uses [kotlin.math.roundToLong] instead of [toLong] to avoid floating-point
 * truncation errors. For example:
 * - `0.1 * 100_000_000` = `9999999.999999998` → `toLong()` = `9999999` ❌
 * - `0.1 * 100_000_000` = `9999999.999999998` → `roundToLong()` = `10000000` ✅
 *
 * For absolute precision, callers should use Long (smallest unit) directly
 * via [CommonCoinsManager.sendCoinExact] or chain-specific methods.
 */
internal fun doubleToSmallestUnit(amount: Double, factor: Long): Long {
    return kotlin.math.round(amount * factor).toLong()
}

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

    companion object {
        /** Hệ số nhân phí mạng lưới cho Account chains khi có service fee */
        const val FEE_MULTIPLIER = 2

        /** Các chain UTXO-based */
        val UTXO_CHAINS = setOf(NetworkName.BTC, NetworkName.CARDANO)

        /** Các chain Account-based hỗ trợ service fee */
        val ACCOUNT_CHAINS = setOf(
            NetworkName.ETHEREUM, NetworkName.ARBITRUM,
            NetworkName.XRP, NetworkName.TON
        )

        /**
         * Shared singleton instance — mirrors CoinsManager.shared pattern.
         *
         * Must call [initialize] before using. Thread-safe via @Volatile.
         *
         * Usage:
         * ```kotlin
         * // App startup
         * CommonCoinsManager.initialize("your mnemonic here")
         *
         * // Anywhere in the app
         * val address = CommonCoinsManager.shared.getAddress(NetworkName.BTC)
         * ```
         */
        @kotlin.concurrent.Volatile
        private var _shared: CommonCoinsManager? = null

        val shared: CommonCoinsManager
            get() = _shared ?: throw IllegalStateException(
                "CommonCoinsManager not initialized. Call CommonCoinsManager.initialize(mnemonic) first."
            )

        /**
         * Initialize the shared singleton with a mnemonic.
         * Call this once at app startup (e.g. Application.onCreate or after user login).
         *
         * @param mnemonic BIP-39 mnemonic phrase
         * @param configs Optional per-chain configuration (API keys, endpoints)
         */
        fun initialize(
            mnemonic: String,
            configs: Map<NetworkName, ChainConfig> = emptyMap()
        ) {
            _shared = CommonCoinsManager(mnemonic, configs)
        }

        /**
         * Check if the shared instance has been initialized.
         */
        val isInitialized: Boolean get() = _shared != null

        /**
         * Reset the shared instance (e.g. on logout or wallet switch).
         */
        fun reset() {
            _shared = null
        }
    }

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

    /**
     * Xác định chain có phải UTXO-based không.
     * UTXO chains (Bitcoin, Cardano) xử lý service fee bằng cách thêm output bổ sung
     * trong cùng giao dịch, khác với Account chains gửi giao dịch riêng biệt.
     */
    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in UTXO_CHAINS
    }

    /**
     * Xác định có phí dịch vụ hay không.
     * Trả về true khi serviceAddress không null/blank VÀ serviceFee > 0.
     * Khi false, toàn bộ logic phí dịch vụ được bỏ qua.
     */
    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    /**
     * Validate whether the wallet balance is sufficient for a transaction
     * including amount, network fee, and service fee.
     *
     * The formula is the same for both UTXO and Account chains:
     *   totalRequired = amount + networkFee + serviceFee
     *
     * For Account chains, [estimateFee] already multiplies the network fee
     * by [FEE_MULTIPLIER] to cover both the main and service-fee transactions,
     * so no additional adjustment is needed here.
     *
     * @param coin Target chain (used for future chain-specific logic if needed)
     * @param amount Transaction amount in the chain's major unit
     * @param networkFee Network fee (already doubled by estimateFee for Account chains)
     * @param serviceFee Service fee amount
     * @param balance Current wallet balance
     * @return [BalanceValidationResult] indicating sufficiency, total required, and deficit
     */
    fun validateSufficientBalance(
        coin: NetworkName,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        balance: Double
    ): BalanceValidationResult {
        val totalRequired = amount + networkFee + serviceFee
        val deficit = maxOf(0.0, totalRequired - balance)
        val sufficient = totalRequired <= balance
        return BalanceValidationResult(
            sufficient = sufficient,
            totalRequired = totalRequired,
            deficit = deficit
        )
    }

    /**
     * Get the underlying chain manager for advanced operations.
     *
     * Use this when you need access to chain-specific methods not exposed
     * by the unified CommonCoinsManager API. Cast the result to the
     * concrete manager type (e.g. BitcoinManager, EthereumManager).
     *
     * Example:
     * ```kotlin
     * val btcManager = manager.getChainManager(NetworkName.BTC) as BitcoinManager
     * val result = btcManager.sendBtcLocal(toAddress, amountSat)
     * ```
     *
     * @param coin Target chain
     * @return IWalletManager instance (cast to concrete type as needed)
     */
    fun getChainManager(coin: NetworkName): IWalletManager {
        return getOrCreateManager(coin)
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
     * - Cardano: uses `page` (1-based) + `count` params via Blockfrost API
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

                NetworkName.CARDANO -> {
                    val manager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.cardano.CardanoManager
                    val addr = address ?: manager.getAddress()
                    val page = (pageParam?.get("page") as? Number)?.toInt() ?: 1
                    val count = limit.coerceAtMost(100)
                    val response = manager.getTransactionHistoryPaginated(addr, count, page)
                    TransactionHistoryResult(
                        transactions = response.first,
                        hasMore = response.second,
                        nextPageParam = if (response.second) mapOf("page" to (page + 1)) else null,
                        success = true
                    )
                }

                NetworkName.CENTRALITY -> {
                    val manager = getOrCreateManager(coin) as CentralityManager
                    val addr = address ?: manager.getAddressAsync()
                    val page = (pageParam?.get("page") as? Number)?.toInt() ?: 0
                    val assetId = (pageParam?.get("assetId") as? Number)?.toInt() ?: CentralityManager.ASSET_CENNZ
                    val result = manager.getTransactionHistoryPaginated(addr, assetId, limit, page)
                    val nextPage = if (result.isNotEmpty() && result.size >= limit) page + 1 else null
                    TransactionHistoryResult(
                        transactions = result,
                        hasMore = nextPage != null,
                        nextPageParam = nextPage?.let { mapOf("page" to it, "assetId" to assetId) },
                        success = true
                    )
                }

                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(coin) as TonManager
                    val addr = address ?: tonManager.getAddress()
                    val coinNetwork = CoinNetwork(coin)
                    val lt = pageParam?.get("lt") as? String
                    val hash = pageParam?.get("hash") as? String
                    val count = limit.coerceAtMost(100)
                    val txs = tonManager.getTransactionHistory(addr, coinNetwork, count, lt, hash)
                    val lastTx = txs?.lastOrNull()
                    val hasMore = txs != null && txs.size >= count
                    TransactionHistoryResult(
                        transactions = txs,
                        hasMore = hasMore,
                        nextPageParam = if (hasMore && lastTx != null)
                            mapOf("lt" to lastTx.transactionId.lt, "hash" to lastTx.transactionId.hash)
                        else null,
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

    /**
     * Get token transaction history with pagination for Cardano native tokens.
     *
     * @param coin Target chain (currently only CARDANO supported)
     * @param policyId Policy ID of the native token (56-char hex)
     * @param assetName Hex-encoded asset name
     * @param limit Max number of transactions per page (max 100)
     * @param pageParam Pagination token from previous result's nextPageParam
     * @return TransactionHistoryResult with transactions and pagination info
     */
    suspend fun getTokenTransactionHistoryPaginated(
        coin: NetworkName,
        policyId: String,
        assetName: String,
        limit: Int = 20,
        pageParam: Map<String, Any?>? = null
    ): TransactionHistoryResult {
        return try {
            when (coin) {
                NetworkName.CARDANO -> {
                    val manager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.cardano.CardanoManager
                    val page = (pageParam?.get("page") as? Number)?.toInt() ?: 1
                    val count = limit.coerceAtMost(100)
                    val response = manager.getTokenTransactionHistoryPaginated(policyId, assetName, count, page)
                    TransactionHistoryResult(
                        transactions = response.first,
                        hasMore = response.second,
                        nextPageParam = if (response.second) mapOf("page" to (page + 1)) else null,
                        success = true
                    )
                }
                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(coin) as TonManager
                    val addr = tonManager.getAddress()
                    val coinNetwork = CoinNetwork(coin)
                    val lt = pageParam?.get("lt") as? String
                    val hash = pageParam?.get("hash") as? String
                    val count = limit.coerceAtMost(100)
                    // policyId is used as contractAddress for TON Jetton
                    val txs = tonManager.getJettonTransactionsParsed(
                        addr, policyId, coinNetwork, count, lt, hash
                    )
                    val lastTx = txs?.lastOrNull()
                    val hasMore = txs != null && txs.size >= count
                    TransactionHistoryResult(
                        transactions = txs,
                        hasMore = hasMore,
                        nextPageParam = if (hasMore && lastTx != null)
                            mapOf("lt" to lastTx.transactionId.lt, "hash" to lastTx.transactionId.hash)
                        else null,
                        success = true
                    )
                }

                else -> {
                    TransactionHistoryResult(
                        success = false,
                        error = "Token transaction history not supported for $coin"
                    )
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to get token transaction history for $coin" }
            TransactionHistoryResult(success = false, error = e.message)
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
     *
     * @param poolAddress Required for TON liquid staking (Tonstakers, Bemo).
     *   TON Nominator pools handle withdrawal automatically.
     *   For Cardano, poolAddress is ignored (pass null or empty).
     */
    suspend fun unstake(coin: NetworkName, amount: Long, poolAddress: String? = null): SendResult {
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
            val result = if (!poolAddress.isNullOrBlank()) {
                stakingManager.unstake(amount, poolAddress, coinNetwork)
            } else {
                stakingManager.unstake(amount, coinNetwork)
            }
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

    // ── TON-specific operations ───────────────────────────────────────────

    /**
     * Send Jetton (TON token) directly — handles signing + broadcast internally.
     *
     * @param toAddress Recipient address
     * @param jettonMasterAddress Jetton Master contract address
     * @param amount Amount in major unit (e.g. 10.5 USDT)
     * @param decimals Token decimals (USDT=6, default=9). Must match token metadata.
     * @param memo Optional comment
     */
    suspend fun sendJetton(
        toAddress: String,
        jettonMasterAddress: String,
        amount: Double,
        decimals: Int = 9,
        memo: String? = null
    ): SendResult {
        return try {
            val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
            val coinNetwork = CoinNetwork(NetworkName.TON)
            val seqno = tonManager.getSeqno(coinNetwork)
            val amountNano = doubleToSmallestUnit(amount, 10.0.pow(decimals.toDouble()).toLong())
            val boc = tonManager.signJettonTransaction(
                jettonMasterAddress = jettonMasterAddress,
                toAddress = toAddress,
                jettonAmountNano = amountNano,
                seqno = seqno,
                coinNetwork = coinNetwork,
                memo = memo?.takeIf { it.isNotEmpty() }
            )
            val txHash = tonManager.TransferToken(boc, coinNetwork)
            SendResult(txHash = txHash ?: "", success = txHash != null)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send Jetton" }
            SendResult("", false, e.message)
        }
    }

    /**
     * Get Jetton (TON token) metadata: name, symbol, decimals, image.
     */
    suspend fun getJettonMetadata(contractAddress: String): com.lybia.cryptowallet.models.ton.JettonMetadata? {
        return try {
            val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
            val coinNetwork = CoinNetwork(NetworkName.TON)
            tonManager.getJettonMetadata(contractAddress, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get Jetton metadata" }
            null
        }
    }

    /**
     * Resolve TON DNS domain to address (e.g. "alice.ton" → "UQ...").
     */
    suspend fun resolveTonDns(domain: String): String? {
        return try {
            val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
            val coinNetwork = CoinNetwork(NetworkName.TON)
            tonManager.resolveDns(domain, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to resolve TON DNS for $domain" }
            null
        }
    }

    /**
     * Reverse resolve TON address to domain (e.g. "UQ..." → "alice.ton").
     */
    suspend fun reverseResolveTonDns(address: String): String? {
        return try {
            val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
            val coinNetwork = CoinNetwork(NetworkName.TON)
            tonManager.reverseResolveDns(address, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to reverse resolve TON DNS for $address" }
            null
        }
    }

    /**
     * Detect TON staking pool type (Nominator, Tonstakers, Bemo, Unknown).
     */
    suspend fun detectTonPoolType(poolAddress: String): com.lybia.cryptowallet.wallets.ton.TonPoolType {
        return try {
            val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
            val coinNetwork = CoinNetwork(NetworkName.TON)
            tonManager.detectPoolType(poolAddress, coinNetwork)
        } catch (e: Exception) {
            logger.e(e) { "Failed to detect TON pool type for $poolAddress" }
            com.lybia.cryptowallet.wallets.ton.TonPoolType.UNKNOWN
        }
    }

    // ── Unified sendCoin — dispatches per-chain ───────────────────────────

    /**
     * Unified send coin operation that dispatches to the appropriate chain manager.
     * Mirrors the legacy CoinsManager.sendCoin() but as a suspend function.
     *
     * When [serviceAddress] is valid and [serviceFee] > 0:
     * - **Account chains** (ETH, Arbitrum, XRP, TON): sends a separate service-fee
     *   transaction after the main transaction succeeds. The [networkFee] is split
     *   in half for each transaction.
     * - **UTXO chains** (BTC, Cardano): service fee is included as an extra output
     *   in the same transaction (handled by the chain manager).
     *
     * If the service-fee transaction fails, the main transaction result is still
     * returned with `success=true` and the error is reported in [SendResult.error].
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
            val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)

            // Account chains with service fee: two-transaction flow
            if (shouldSendServiceFee && !isUtxoChain(coin) && coin in ACCOUNT_CHAINS) {
                sendCoinWithServiceFee(coin, toAddress, amount, networkFee, serviceFee, serviceAddress!!, memo)
            } else {
                // No service fee OR UTXO chain OR unsupported chain
                // For UTXO chains, pass service fee params so chain managers can include
                // the service fee as an additional output in the same transaction.
                sendCoinMain(
                    coin, toAddress, amount, networkFee, memo,
                    serviceAddress = if (shouldSendServiceFee) serviceAddress else null,
                    serviceFee = if (shouldSendServiceFee) serviceFee else 0.0
                )
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to sendCoin for $coin" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Execute the main transaction only (no service fee for Account chains).
     * For UTXO chains, service fee params are passed through to the chain manager
     * so it can include the service fee as an additional output in the same transaction.
     */
    private suspend fun sendCoinMain(
        coin: NetworkName,
        toAddress: String,
        amount: Double,
        networkFee: Double,
        memo: MemoData? = null,
        serviceAddress: String? = null,
        serviceFee: Double = 0.0
    ): SendResult {
        return when (coin) {
            NetworkName.CARDANO -> {
                val amountLovelace = doubleToSmallestUnit(amount, 1_000_000L)
                val feeLovelace = doubleToSmallestUnit(networkFee, 1_000_000L)
                val serviceFeeLovelace = doubleToSmallestUnit(serviceFee, 1_000_000L)
                sendCardano(toAddress, amountLovelace, feeLovelace, serviceAddress, serviceFeeLovelace)
            }

            NetworkName.MIDNIGHT -> {
                val amountUnits = doubleToSmallestUnit(amount, 1_000_000L)
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
                val amountNano = doubleToSmallestUnit(amount, 1_000_000_000L)
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
                val amountSatoshi = doubleToSmallestUnit(amount, 100_000_000L)
                val serviceFeeSatoshi = doubleToSmallestUnit(serviceFee, 100_000_000L)
                val result = btcManager.sendBtc(
                    toAddress, amountSatoshi,
                    serviceAddress = serviceAddress,
                    serviceFeeAmount = serviceFeeSatoshi
                )
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
                val amountDrops = doubleToSmallestUnit(amount, 1_000_000L)
                val feeDrops = if (networkFee > 0) doubleToSmallestUnit(networkFee, 1_000_000L) else 12L
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
    }

    /**
     * Account chain two-transaction flow: main TX + service fee TX.
     *
     * The [networkFee] is split in half — each transaction uses networkFee/2.
     * If the main TX fails, the service fee TX is skipped.
     * If the service fee TX fails, the main TX result is still returned as success
     * with the service fee error in [SendResult.error].
     */
    private suspend fun sendCoinWithServiceFee(
        coin: NetworkName,
        toAddress: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        serviceAddress: String,
        memo: MemoData? = null
    ): SendResult {
        // ── Step 1: Send main transaction ──
        val mainResult = when (coin) {
            NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                val ethManager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.ethereum.EthereumManager
                val coinNetwork = CoinNetwork(coin)
                val amountWei = ethManager.ethToWei(amount)
                val result = ethManager.sendEthBigInt(toAddress, amountWei, coinNetwork)
                SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
            }

            NetworkName.XRP -> {
                val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
                val amountDrops = doubleToSmallestUnit(amount, 1_000_000L)
                val halfFeeDrops = if (networkFee > 0) doubleToSmallestUnit(networkFee / 2.0, 1_000_000L) else 12L
                val destTag = memo?.destinationTag?.toLong()
                val result = rippleManager.sendXrp(toAddress, amountDrops, halfFeeDrops, destTag)
                SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
            }

            NetworkName.TON -> {
                val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
                val coinNetwork = CoinNetwork(NetworkName.TON)
                val seqno = tonManager.getSeqno(coinNetwork)
                val amountNano = doubleToSmallestUnit(amount, 1_000_000_000L)
                val memoStr = memo?.memo?.takeIf { it.isNotEmpty() }
                val bocBase64 = tonManager.signTransaction(toAddress, amountNano, seqno, memoStr)
                val result = tonManager.transfer(bocBase64, coinNetwork)
                SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
            }

            else -> {
                SendResult(txHash = "", success = false, error = "Service fee not supported for $coin")
            }
        }

        // ── Step 2: If main TX failed, return immediately — do NOT send service fee ──
        if (!mainResult.success) {
            return mainResult
        }

        // ── Step 3: Send service fee transaction ──
        val serviceResult = try {
            when (coin) {
                NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                    val ethManager = getOrCreateManager(coin) as com.lybia.cryptowallet.wallets.ethereum.EthereumManager
                    val coinNetwork = CoinNetwork(coin)
                    val serviceFeeWei = ethManager.ethToWei(serviceFee)
                    val result = ethManager.sendEthBigInt(serviceAddress, serviceFeeWei, coinNetwork)
                    SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
                }

                NetworkName.XRP -> {
                    val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
                    val serviceFeeDrops = doubleToSmallestUnit(serviceFee, 1_000_000L)
                    val halfFeeDrops = if (networkFee > 0) doubleToSmallestUnit(networkFee / 2.0, 1_000_000L) else 12L
                    val result = rippleManager.sendXrp(serviceAddress, serviceFeeDrops, halfFeeDrops, null)
                    SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
                }

                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
                    val coinNetwork = CoinNetwork(NetworkName.TON)
                    val newSeqno = tonManager.getSeqno(coinNetwork)
                    val serviceFeeNano = doubleToSmallestUnit(serviceFee, 1_000_000_000L)
                    val bocBase64 = tonManager.signTransaction(serviceAddress, serviceFeeNano, newSeqno, null)
                    val result = tonManager.transfer(bocBase64, coinNetwork)
                    SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
                }

                else -> {
                    SendResult(txHash = "", success = false, error = "Service fee not supported for $coin")
                }
            }
        } catch (e: Exception) {
            logger.w { "Service fee transaction failed for $coin: ${e.message}" }
            SendResult(txHash = "", success = false, error = e.message)
        }

        // ── Step 4: Return result — main TX hash is always used ──
        return if (serviceResult.success) {
            SendResult(txHash = mainResult.txHash, success = true)
        } else {
            logger.w { "Service fee failed for $coin (mainTx=${mainResult.txHash}): ${serviceResult.error}" }
            SendResult(
                txHash = mainResult.txHash,
                success = true,
                error = "Service fee failed: ${serviceResult.error}"
            )
        }
    }

    /**
     * Send coin with amount in the chain's smallest unit (Long) — no floating-point conversion.
     *
     * Use this when you need absolute precision (e.g. amount comes from user input
     * already parsed to smallest unit, or from another API returning Long).
     *
     * | Chain | Smallest unit | Example |
     * |---|---|---|
     * | BTC | satoshi | 10_000_000 = 0.1 BTC |
     * | ADA | lovelace | 2_000_000 = 2 ADA |
     * | TON | nanoton | 500_000_000 = 0.5 TON |
     * | XRP | drops | 10_000_000 = 10 XRP |
     * | Midnight | units | 1_000_000 = 1 tDUST |
     * | ETH | wei (via BigInteger) | Use [sendEth] instead |
     * | Centrality | CENNZ×10000 | Use [sendCentrality] instead |
     */
    suspend fun sendCoinExact(
        coin: NetworkName,
        toAddress: String,
        amountSmallestUnit: Long,
        feeSmallestUnit: Long = 0L,
        memo: MemoData? = null
    ): SendResult {
        return try {
            when (coin) {
                NetworkName.CARDANO -> sendCardano(toAddress, amountSmallestUnit, feeSmallestUnit)
                NetworkName.MIDNIGHT -> sendMidnight(toAddress, amountSmallestUnit)
                NetworkName.BTC -> {
                    val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
                    val result = btcManager.sendBtc(toAddress, amountSmallestUnit)
                    SendResult(result.txHash ?: "", result.success, result.error)
                }
                NetworkName.TON -> {
                    val tonManager = getOrCreateManager(NetworkName.TON) as TonManager
                    val coinNetwork = CoinNetwork(NetworkName.TON)
                    val seqno = tonManager.getSeqno(coinNetwork)
                    val memoStr = memo?.memo?.takeIf { it.isNotEmpty() }
                    val boc = tonManager.signTransaction(toAddress, amountSmallestUnit, seqno, memoStr)
                    val result = tonManager.transfer(boc, coinNetwork)
                    SendResult(result.txHash ?: "", result.success, result.error)
                }
                NetworkName.XRP -> {
                    val rippleManager = getOrCreateManager(NetworkName.XRP) as com.lybia.cryptowallet.wallets.ripple.RippleManager
                    val fee = if (feeSmallestUnit > 0) feeSmallestUnit else 12L
                    val destTag = memo?.destinationTag?.toLong()
                    val result = rippleManager.sendXrp(toAddress, amountSmallestUnit, fee, destTag)
                    SendResult(result.txHash ?: "", result.success, result.error)
                }
                NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                    SendResult("", false, "ETH uses BigInteger — use sendEth() instead")
                }
                NetworkName.CENTRALITY -> {
                    SendResult("", false, "Centrality uses Double amount — use sendCentrality() instead")
                }
                else -> SendResult("", false, "sendCoinExact not supported for $coin")
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to sendCoinExact for $coin" }
            SendResult("", false, e.message)
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
     * @param serviceAddress Service fee recipient address (null = no service fee)
     * @param serviceFee Service fee amount in the chain's major unit (0.0 = no service fee)
     * @return FeeEstimateResult with fee in the chain's major unit
     */
    suspend fun estimateFee(
        coin: NetworkName,
        amount: Double = 0.0,
        fromAddress: String? = null,
        toAddress: String? = null,
        serviceAddress: String? = null,
        serviceFee: Double = 0.0
    ): FeeEstimateResult {
        return try {
            val baseResult = when (coin) {
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
                    val amountNano = doubleToSmallestUnit(amount, 1_000_000_000L)
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
                    // Cardano uses a static default fee; the actual fee (including any
                    // service-fee output) is computed inside buildAndSignTransaction at
                    // send time, so no dynamic estimation is needed here.
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
                    val amountSatoshi = doubleToSmallestUnit(amount, 100_000_000L)
                    val btcServiceAddr = if (hasServiceFee(serviceAddress, serviceFee)) serviceAddress else null
                    val feeSatoshi = btcManager.estimateFee(to, amountSatoshi, serviceAddress = btcServiceAddr)
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

            // Apply service fee multiplier for Account chains:
            // Account chains need two separate transactions (main + service fee),
            // so the network fee is doubled to cover both.
            if (baseResult.success && hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)) {
                baseResult.copy(fee = baseResult.fee * FEE_MULTIPLIER)
            } else {
                baseResult
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

    suspend fun sendCardano(
        toAddress: String,
        amountLovelace: Long,
        fee: Long,
        serviceAddress: String? = null,
        serviceFeeLovelace: Long = 0
    ): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CARDANO)
            val cardanoManager = manager as com.lybia.cryptowallet.wallets.cardano.CardanoManager
            val signedTx = cardanoManager.buildAndSignTransaction(
                toAddress, amountLovelace, fee,
                serviceAddress = serviceAddress,
                serviceFeeLovelace = serviceFeeLovelace
            )
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

    /**
     * Get balance for a Centrality asset (CENNZ or CPAY).
     * @param assetId 1 = CENNZ, 2 = CPAY (see [CentralityManager.ASSET_CENNZ], [CentralityManager.ASSET_CPAY])
     */
    suspend fun getCentralityBalance(address: String? = null, assetId: Int = CentralityManager.ASSET_CENNZ): BalanceResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CENTRALITY) as CentralityManager
            val balance = manager.getBalance(address, assetId)
            BalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get Centrality balance (assetId=$assetId)" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    /**
     * Get transaction history for a Centrality asset (CENNZ or CPAY).
     * @param assetId 1 = CENNZ, 2 = CPAY
     */
    suspend fun getCentralityTransactions(address: String? = null, assetId: Int = CentralityManager.ASSET_CENNZ): Any? {
        return try {
            val manager = getOrCreateManager(NetworkName.CENTRALITY) as CentralityManager
            manager.getTransactionHistory(address, assetId)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get Centrality transactions (assetId=$assetId)" }
            null
        }
    }

    suspend fun sendCentrality(
        fromAddress: String,
        toAddress: String,
        amount: Double,
        assetId: Int = CentralityManager.ASSET_CENNZ
    ): SendResult {
        return try {
            val manager = getOrCreateManager(NetworkName.CENTRALITY) as CentralityManager
            val result = manager.sendCoin(fromAddress, toAddress, amount, assetId)
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
            val amountSatoshi = doubleToSmallestUnit(amountBtc, 100_000_000L)
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

    /**
     * Send BTC using the local transaction builder (no BlockCypher dependency).
     * Builds and signs the transaction entirely client-side using bitcoin-kmp.
     * Uses Esplora (Blockstream) API for UTXO fetching and broadcasting only.
     *
     * Supports all address types: Legacy (1...), Nested SegWit (3...), Native SegWit (bc1q...).
     *
     * @param toAddress Destination Bitcoin address (any format)
     * @param amountBtc Amount in BTC
     * @param addressType Sender address type (default: NATIVE_SEGWIT)
     * @param accountIndex HD wallet account index (default 0)
     * @param feeRateSatPerVbyte Fee rate in sat/vB (null = auto from mempool.space)
     */
    suspend fun sendBtcLocal(
        toAddress: String,
        amountBtc: Double,
        addressType: com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType =
            com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0,
        feeRateSatPerVbyte: Long? = null
    ): SendResult {
        return try {
            val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
            val amountSatoshi = doubleToSmallestUnit(amountBtc, 100_000_000L)
            val result = btcManager.sendBtcLocal(
                toAddress, amountSatoshi, addressType, accountIndex, feeRateSatPerVbyte
            )
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send BTC (local)" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Estimate BTC transaction fee using the local builder.
     *
     * @param toAddress Destination address
     * @param amountBtc Amount in BTC
     * @param addressType Sender address type
     * @param accountIndex HD wallet account index
     * @param feeRateSatPerVbyte Fee rate in sat/vB (null = auto)
     * @return FeeEstimateResult with fee in BTC
     */
    suspend fun estimateBtcFeeLocal(
        toAddress: String,
        amountBtc: Double,
        addressType: com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType =
            com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0,
        feeRateSatPerVbyte: Long? = null
    ): FeeEstimateResult {
        return try {
            val btcManager = getOrCreateManager(NetworkName.BTC) as BitcoinManager
            val amountSatoshi = doubleToSmallestUnit(amountBtc, 100_000_000L)
            val feeSat = btcManager.estimateFeeLocal(
                toAddress, amountSatoshi, addressType, accountIndex, feeRateSatPerVbyte
            )
            if (feeSat != null) {
                FeeEstimateResult(
                    fee = feeSat.toDouble() / 100_000_000.0,
                    unit = "BTC",
                    success = true
                )
            } else {
                FeeEstimateResult(fee = 0.0, unit = "BTC", success = false, error = "Estimation failed")
            }
        } catch (e: Exception) {
            FeeEstimateResult(fee = 0.0, unit = "BTC", success = false, error = e.message)
        }
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
            val amountDrops = doubleToSmallestUnit(amountXrp, 1_000_000L)
            val feeDrops = doubleToSmallestUnit(feeXrp, 1_000_000L)
            val result = rippleManager.sendXrp(toAddress, amountDrops, feeDrops, destinationTag)
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send XRP" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }
}
