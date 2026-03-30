package com.lybia.cryptowallet.coinkits

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.cardano.*
import com.lybia.cryptowallet.wallets.midnight.MidnightError
import com.lybia.cryptowallet.wallets.midnight.MidnightManager

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
 * Common CoinsManager for Cardano and Midnight in commonMain.
 *
 * Delegates to [CardanoManager] and [MidnightManager] based on coin type.
 * This manager is platform-independent and uses Ktor-based API services.
 */
class CommonCoinsManager(
    private val mnemonic: String,
    cardanoApiService: CardanoApiService? = null,
    midnightApiService: MidnightApiService? = null
) {
    private val logger = Logger.withTag("CommonCoinsManager")

    private val cardanoManager: CardanoManager = CardanoManager(
        mnemonic = mnemonic,
        apiService = cardanoApiService ?: CardanoApiService(
            baseUrl = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        )
    )

    private val midnightManager: MidnightManager = MidnightManager(
        mnemonic = mnemonic,
        apiService = midnightApiService ?: MidnightApiService(
            baseUrl = CoinNetwork(NetworkName.MIDNIGHT).getMidnightApiUrl()
        )
    )

    // ── Address generation ──────────────────────────────────────────────────

    /**
     * Get the primary address for a given network.
     */
    fun getAddress(network: NetworkName): String {
        return when (network) {
            NetworkName.CARDANO -> cardanoManager.getAddress()
            NetworkName.MIDNIGHT -> midnightManager.getAddress()
            else -> throw IllegalArgumentException("Unsupported network: $network")
        }
    }

    // ── Balance queries (Task 8.5, 8.6) ─────────────────────────────────────

    /**
     * Get balance for Cardano (ADA) using CardanoApiService.
     */
    suspend fun getCardanoBalance(address: String? = null): BalanceResult {
        return try {
            val balance = cardanoManager.getBalance(address, CoinNetwork(NetworkName.CARDANO))
            BalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get Cardano balance" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    /**
     * Get balance for Midnight (tDUST) using MidnightApiService.
     */
    suspend fun getMidnightBalance(address: String? = null): BalanceResult {
        return try {
            val balance = midnightManager.getBalance(address, CoinNetwork(NetworkName.MIDNIGHT))
            BalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get Midnight balance" }
            BalanceResult(balance = 0.0, success = false, error = e.message)
        }
    }

    /**
     * Get balance for a given network.
     */
    suspend fun getBalance(network: NetworkName, address: String? = null): BalanceResult {
        return when (network) {
            NetworkName.CARDANO -> getCardanoBalance(address)
            NetworkName.MIDNIGHT -> getMidnightBalance(address)
            else -> BalanceResult(balance = 0.0, success = false, error = "Unsupported network: $network")
        }
    }

    // ── Transaction history (Task 8.5, 8.6) ─────────────────────────────────

    /**
     * Get transaction history for Cardano.
     */
    suspend fun getCardanoTransactions(address: String? = null): Any? {
        return cardanoManager.getTransactionHistory(address, CoinNetwork(NetworkName.CARDANO))
    }

    /**
     * Get transaction history for Midnight.
     */
    suspend fun getMidnightTransactions(address: String? = null): Any? {
        return midnightManager.getTransactionHistory(address, CoinNetwork(NetworkName.MIDNIGHT))
    }

    /**
     * Get transaction history for a given network.
     */
    suspend fun getTransactions(network: NetworkName, address: String? = null): Any? {
        return when (network) {
            NetworkName.CARDANO -> getCardanoTransactions(address)
            NetworkName.MIDNIGHT -> getMidnightTransactions(address)
            else -> null
        }
    }

    // ── Send coin (Task 8.5, 8.6) ───────────────────────────────────────────

    /**
     * Send ADA to a Cardano address.
     *
     * Dispatches based on address type:
     * - Byron address → uses Bootstrap witness
     * - Shelley address → uses VKey witness
     *
     * @param toAddress Destination Cardano address (Byron or Shelley)
     * @param amountLovelace Amount in lovelace
     * @param fee Fee in lovelace
     * @return SendResult with transaction hash
     */
    suspend fun sendCardano(toAddress: String, amountLovelace: Long, fee: Long): SendResult {
        return try {
            val signedTx = cardanoManager.buildAndSignTransaction(toAddress, amountLovelace, fee)
            val coinNetwork = CoinNetwork(NetworkName.CARDANO)
            val result = cardanoManager.transfer(
                signedTx.toBase64(),
                coinNetwork
            )
            SendResult(txHash = result.txHash ?: "", success = result.success, error = result.error)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send Cardano" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    /**
     * Send tDUST on Midnight network.
     *
     * @param toAddress Destination Midnight address
     * @param amount Amount in smallest tDUST unit
     * @return SendResult with transaction hash
     */
    suspend fun sendMidnight(toAddress: String, amount: Long): SendResult {
        return try {
            val txHash = midnightManager.sendTDust(toAddress, amount)
            SendResult(txHash = txHash, success = true)
        } catch (e: MidnightError.InsufficientTDust) {
            SendResult(txHash = "", success = false, error = e.message)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send Midnight" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── Token operations for Cardano (Task 8.7) ─────────────────────────────

    /**
     * Get native token balance for a Cardano address.
     *
     * @param address Cardano address
     * @param policyId 56-char hex policy ID
     * @param assetName Hex-encoded asset name
     * @return TokenBalanceResult with raw token amount
     */
    suspend fun getTokenBalance(
        address: String,
        policyId: String,
        assetName: String
    ): TokenBalanceResult {
        return try {
            val balance = cardanoManager.getTokenBalance(address, policyId, assetName)
            TokenBalanceResult(balance = balance, success = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to get token balance" }
            TokenBalanceResult(balance = 0L, success = false, error = e.message)
        }
    }

    /**
     * Send a Cardano native token.
     *
     * @param toAddress Destination address
     * @param policyId 56-char hex policy ID
     * @param assetName Hex-encoded asset name
     * @param amount Token amount to send
     * @param fee Transaction fee in lovelace
     * @return SendResult with transaction hash
     */
    suspend fun sendToken(
        toAddress: String,
        policyId: String,
        assetName: String,
        amount: Long,
        fee: Long
    ): SendResult {
        return try {
            val txHash = cardanoManager.sendToken(toAddress, policyId, assetName, amount, fee)
            SendResult(txHash = txHash, success = true)
        } catch (e: CardanoError.InsufficientTokens) {
            SendResult(txHash = "", success = false, error = e.message)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send token" }
            SendResult(txHash = "", success = false, error = e.message)
        }
    }

    // ── Address type detection (used by Property 19) ────────────────────────

    /**
     * Determine the witness type that would be used for a given Cardano address.
     *
     * @param address Cardano address string
     * @return "bootstrap" for Byron addresses, "vkey" for Shelley addresses
     * @throws IllegalArgumentException for unknown address types
     */
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
