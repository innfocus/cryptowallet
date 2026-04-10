package com.lybia.cryptowallet.wallets.midnight

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import com.lybia.cryptowallet.wallets.cardano.Ed25519
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MnemonicCode

/**
 * Main Midnight wallet manager.
 *
 * Extends [BaseCoinManager] and provides Midnight address generation,
 * tDUST balance queries, transaction history, and tDUST transfers.
 *
 * @param mnemonic BIP-39 mnemonic phrase (space-separated words)
 * @param apiService Optional [MidnightApiService] for dependency injection / testing
 */
class MidnightManager(
    private val mnemonic: String,
    private val apiService: MidnightApiService = MidnightApiService(
        baseUrl = "https://midnight-testnet.api.midnight.network/api/v0"
    )
) : BaseCoinManager() {

    private val logger = Logger.withTag("MidnightManager")

    // ── BaseCoinManager overrides ───────────────────────────────────────────

    /**
     * Returns the Midnight address derived from the mnemonic.
     */
    override fun getAddress(): String {
        return MidnightAddress.fromMnemonic(mnemonic)
    }

    /**
     * Query tDUST balance for the given address.
     * Returns balance in whole tDUST units (balance / 1_000_000).
     */
    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        val addr = address ?: getAddress()
        logger.d { "getBalance for $addr" }
        val balanceRaw = apiService.getBalance(addr)
        return balanceRaw.toDouble() / 1_000_000.0
    }

    /**
     * Query transaction history for the given address.
     */
    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        val addr = address ?: getAddress()
        logger.d { "getTransactionHistory for $addr" }
        return apiService.getTransactionHistory(addr)
    }

    /**
     * Submit a signed transaction (Base64-encoded) to the Midnight network.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel {
        logger.d { "transfer: submitting signed transaction" }
        return try {
            val txBytes = kotlin.io.encoding.Base64.decode(dataSigned)
            val txHash = apiService.submitTransaction(txBytes)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            logger.e(e) { "transfer failed" }
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return "midnight-testnet"
    }

    // ── Midnight-specific operations ────────────────────────────────────────

    /**
     * Send tDUST to another Midnight address.
     *
     * @param toAddress Destination Midnight address
     * @param amount Amount in smallest tDUST unit
     * @return Transaction hash
     * @throws MidnightError.InsufficientTDust if balance is insufficient
     * @throws MidnightError.InvalidAddress if toAddress is invalid
     */
    suspend fun sendTDust(toAddress: String, amount: Long): String {
        logger.d { "sendTDust: $amount to $toAddress" }

        if (!MidnightAddress.isValid(toAddress)) {
            throw MidnightError.InvalidAddress(toAddress)
        }

        val fromAddress = getAddress()
        val balance = apiService.getBalance(fromAddress)

        if (balance < amount) {
            throw MidnightError.InsufficientTDust(balance = balance, required = amount)
        }

        // Derive signing key and sign the transaction
        val words = Bip39Language.splitMnemonic(mnemonic)
        val seed = MnemonicCode.toSeed(words, "")
        val (privateKey, _) = MidnightAddress.slip10DeriveEd25519(seed, intArrayOf(
            hardenedIndex(1852),
            hardenedIndex(1815),
            hardenedIndex(0),
            hardenedIndex(0),
            hardenedIndex(0)
        ))

        // Build a simple transaction payload: [from, to, amount, signature]
        val txPayload = buildTransactionPayload(fromAddress, toAddress, amount)
        val signature = Ed25519.sign(privateKey, txPayload)
        val signedTx = txPayload + signature

        return apiService.submitTransaction(signedTx)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun hardenedIndex(i: Int): Int = 0x80000000.toInt() or i

    private fun buildTransactionPayload(from: String, to: String, amount: Long): ByteArray {
        val fromBytes = from.encodeToByteArray()
        val toBytes = to.encodeToByteArray()
        val amountBytes = ByteArray(8)
        for (i in 0..7) {
            amountBytes[i] = (amount ushr (56 - i * 8)).toByte()
        }
        return fromBytes + toBytes + amountBytes
    }
}
