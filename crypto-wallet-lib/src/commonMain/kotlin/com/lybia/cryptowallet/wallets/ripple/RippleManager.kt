package com.lybia.cryptowallet.wallets.ripple

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ripple.RippleTransactionEntry
import com.lybia.cryptowallet.services.RippleApiService
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet

/**
 * Ripple/XRP wallet manager for commonMain.
 * Implements IWalletManager using ACTHDWallet for key derivation (Secp256k1, coinType=144)
 * and RippleApiService for networking.
 */
class RippleManager(
    mnemonic: String,
    private val apiService: RippleApiService = RippleApiService.INSTANCE
) : BaseCoinManager(), IWalletManager {

    private val network = ACTNetwork(ACTCoin.Ripple, Config.shared.getNetwork() == Network.TESTNET)
    private val hdWallet = ACTHDWallet(mnemonic)
    private var walletAddress: String? = null

    /** Raw 32-byte secp256k1 private key for signing */
    private val privateKeyBytes: ByteArray by lazy {
        hdWallet.generateExternalPrivateKey(0, network).raw!!
    }

    /** 33-byte compressed secp256k1 public key */
    private val publicKeyBytes: ByteArray by lazy {
        hdWallet.generateExternalPublicKey(0, network).raw!!
    }

    companion object {
        /** 1 XRP = 1,000,000 drops */
        const val XRP_DROPS_PER_UNIT = 1_000_000.0
        /** Default fee in drops (12 drops = 0.000012 XRP) */
        const val DEFAULT_FEE_DROPS = 12L
    }

    init {
        // Derive the first external address (m/44'/144'/0'/0/0)
        val publicKey = hdWallet.generateExternalPublicKey(0, network)
        val actAddress = ACTAddress(publicKey)
        walletAddress = actAddress.rawAddressString()
    }

    override fun getAddress(): String {
        return walletAddress ?: ""
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        val addr = address ?: walletAddress ?: return 0.0
        val balanceDrops = apiService.getBalance(addr)
        return (balanceDrops?.toLongOrNull() ?: 0L) / XRP_DROPS_PER_UNIT
    }

    override suspend fun getTransactionHistory(
        address: String?,
        coinNetwork: CoinNetwork?
    ): List<RippleTransactionEntry>? {
        val addr = address ?: walletAddress ?: return null
        val response = apiService.getTransactionHistory(addr)
        return response?.result?.transactions
    }

    /**
     * Get transaction history with pagination support via Ripple marker.
     * @param address Ripple address
     * @param limit Max transactions per page
     * @param marker Pagination marker from previous response (null for first page)
     * @return Pair of (transactions, nextMarker). nextMarker is null if no more pages.
     */
    suspend fun getTransactionHistoryPaginated(
        address: String,
        limit: Int = 100,
        marker: com.lybia.cryptowallet.models.ripple.RippleMarker? = null
    ): Pair<List<RippleTransactionEntry>?, com.lybia.cryptowallet.models.ripple.RippleMarker?> {
        val response = apiService.getTransactionHistoryWithMarker(address, limit, marker)
        return Pair(response?.result?.transactions, response?.result?.marker)
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        return try {
            val response = apiService.submitTransaction(dataSigned)
            val result = response?.result
            if (result?.engineResult == "tesSUCCESS" || result?.engineResult == "terQUEUED") {
                TransferResponseModel(
                    success = true,
                    error = null,
                    txHash = result.tx_json?.hash
                )
            } else {
                TransferResponseModel(
                    success = false,
                    error = result?.engineResultMessage ?: "Transaction failed",
                    txHash = null
                )
            }
        } catch (e: Exception) {
            TransferResponseModel(
                success = false,
                error = e.message,
                txHash = null
            )
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        // Ripple doesn't have a chain ID concept like Ethereum
        return "ripple"
    }

    // ── Direct send (build + sign + submit) ─────────────────────────

    /**
     * Get the current account sequence number for the wallet address.
     */
    suspend fun getSequence(): Long {
        val addr = walletAddress ?: throw IllegalStateException("No wallet address")
        val info = apiService.getAccountInfo(addr)
            ?: throw Exception("Failed to get account info")
        return info.result.accountData?.sequence
            ?: throw Exception("Account not found or no sequence")
    }

    /**
     * Get the current validated ledger index for LastLedgerSequence calculation.
     */
    suspend fun getCurrentLedgerIndex(): Long {
        val addr = walletAddress ?: throw IllegalStateException("No wallet address")
        val info = apiService.getAccountInfo(addr)
        return info?.result?.ledgerCurrentIndex ?: 0L
    }

    /**
     * Build, sign, and submit an XRP Payment transaction.
     *
     * @param toAddress Destination r-address
     * @param amountDrops Amount in drops (1 XRP = 1,000,000 drops)
     * @param feeDrops Fee in drops (default: 12 drops)
     * @param destinationTag Optional destination tag (UInt32)
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendXrp(
        toAddress: String,
        amountDrops: Long,
        feeDrops: Long = DEFAULT_FEE_DROPS,
        destinationTag: Long? = null
    ): TransferResponseModel {
        return try {
            val fromAddr = walletAddress
                ?: return TransferResponseModel(false, "No wallet address", null)

            val sequence = getSequence()
            val ledgerIndex = getCurrentLedgerIndex()
            // LastLedgerSequence = current + 20 (gives ~60-80 seconds to confirm)
            val lastLedgerSeq = if (ledgerIndex > 0) ledgerIndex + 20 else null

            val txBlob = XrpTransactionSigner.signPayment(
                privateKey = privateKeyBytes,
                publicKey = publicKeyBytes,
                account = fromAddr,
                destination = toAddress,
                amountDrops = amountDrops,
                feeDrops = feeDrops,
                sequence = sequence,
                destinationTag = destinationTag,
                lastLedgerSequence = lastLedgerSeq
            )

            val response = apiService.submitTransaction(txBlob)
            val result = response?.result
            if (result?.engineResult == "tesSUCCESS" || result?.engineResult == "terQUEUED") {
                TransferResponseModel(
                    success = true,
                    error = null,
                    txHash = result.tx_json?.hash
                )
            } else {
                TransferResponseModel(
                    success = false,
                    error = result?.engineResultMessage ?: "Transaction failed: ${result?.engineResult}",
                    txHash = null
                )
            }
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Estimate XRP transaction fee.
     * XRP fees are typically fixed at 12 drops (0.000012 XRP).
     * Returns fee in XRP (not drops).
     */
    fun estimateFee(): Double {
        return DEFAULT_FEE_DROPS / XRP_DROPS_PER_UNIT
    }
}
