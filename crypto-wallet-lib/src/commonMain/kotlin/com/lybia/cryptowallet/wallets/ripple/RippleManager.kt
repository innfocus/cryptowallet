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
        /** Base reserve required for an XRP account (10 XRP) */
        const val BASE_RESERVE_DROPS = 10_000_000L
        /**
         * LastLedgerSequence offset from current ledger index.
         * 75 ledgers ≈ 5 minutes — matches code cũ, safe during network congestion.
         */
        const val LAST_LEDGER_OFFSET = 75
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
     * @param feeDrops Fee in drops (0 = auto estimate)
     * @param destinationTag Optional destination tag (UInt32)
     * @param memoText Optional memo text
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendXrp(
        toAddress: String,
        amountDrops: Long,
        feeDrops: Long = 0L,
        destinationTag: Long? = null,
        memoText: String? = null
    ): TransferResponseModel {
        return try {
            val fromAddr = walletAddress
                ?: return TransferResponseModel(false, "No wallet address", null)

            // Use dynamic fee if not explicitly provided (0 = auto)
            val actualFee = if (feeDrops > 0) feeDrops else estimateFeeDynamic()

            // Single RPC call for both sequence and ledger index (BUG-7 fix)
            val accountInfo = apiService.getAccountInfo(fromAddr)
                ?: return TransferResponseModel(false, "Failed to get account info", null)
            val accountData = accountInfo.result.accountData
                ?: return TransferResponseModel(false, "Account not found on ledger", null)
            val sequence = accountData.sequence
            val ledgerIndex = accountInfo.result.ledgerCurrentIndex ?: 0L
            val lastLedgerSeq = if (ledgerIndex > 0) ledgerIndex + LAST_LEDGER_OFFSET else null

            // Balance validation: must cover amount + fee + 10 XRP reserve (BUG-3 fix)
            val balanceDrops = accountData.balance.toLongOrNull() ?: 0L
            val requiredDrops = amountDrops + actualFee + BASE_RESERVE_DROPS
            if (balanceDrops < requiredDrops) {
                return TransferResponseModel(
                    false,
                    "Insufficient funds: balance=${balanceDrops / XRP_DROPS_PER_UNIT} XRP, " +
                            "required=${requiredDrops / XRP_DROPS_PER_UNIT} XRP " +
                            "(amount + fee + 10 XRP reserve)",
                    null
                )
            }

            val signResult = XrpTransactionSigner.signPayment(
                privateKey = privateKeyBytes,
                publicKey = publicKeyBytes,
                account = fromAddr,
                destination = toAddress,
                amountDrops = amountDrops,
                feeDrops = actualFee,
                sequence = sequence,
                destinationTag = destinationTag,
                lastLedgerSequence = lastLedgerSeq,
                memoText = memoText
            )

            val response = apiService.submitTransaction(signResult.txBlob)
            val result = response?.result
            if (result?.engineResult == "tesSUCCESS" || result?.engineResult == "terQUEUED") {
                TransferResponseModel(
                    success = true,
                    error = null,
                    // Prefer server hash, fallback to locally computed TX ID
                    txHash = result.tx_json?.hash ?: signResult.transactionId
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
     * Estimate XRP transaction fee dynamically via the `fee` RPC method.
     * Returns fee in drops. Falls back to DEFAULT_FEE_DROPS if RPC fails.
     *
     * The `open_ledger_fee` is the fee required to get into the current open ledger,
     * which is the most practical value for immediate confirmation.
     */
    suspend fun estimateFeeDynamic(): Long {
        return try {
            val feeResponse = apiService.getFee()
            val drops = feeResponse?.result?.drops
            if (drops != null) {
                // Use open_ledger_fee for timely inclusion, fallback to median, then base
                drops.openLedgerFee.toLongOrNull()
                    ?: drops.medianFee.toLongOrNull()
                    ?: drops.baseFee.toLongOrNull()
                    ?: DEFAULT_FEE_DROPS
            } else {
                DEFAULT_FEE_DROPS
            }
        } catch (_: Exception) {
            DEFAULT_FEE_DROPS
        }
    }

    /**
     * Estimate XRP transaction fee in XRP (not drops).
     * Uses dynamic fee from the network, falls back to default.
     */
    suspend fun estimateFeeDynamicXrp(): Double {
        return estimateFeeDynamic().toDouble() / XRP_DROPS_PER_UNIT
    }

    /**
     * Static fee estimate (synchronous). Returns default fee in XRP.
     */
    fun estimateFee(): Double {
        return DEFAULT_FEE_DROPS / XRP_DROPS_PER_UNIT
    }
}
