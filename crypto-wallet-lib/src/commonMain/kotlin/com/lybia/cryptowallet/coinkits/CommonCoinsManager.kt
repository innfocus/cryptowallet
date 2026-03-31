package com.lybia.cryptowallet.coinkits

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.IFeeEstimator
import com.lybia.cryptowallet.base.INFTManager
import com.lybia.cryptowallet.base.ITokenManager
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.errors.WalletError
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import com.lybia.cryptowallet.wallets.cardano.CardanoError

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
}
