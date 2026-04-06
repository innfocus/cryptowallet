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
import kotlinx.coroutines.delay

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

        /** Max polling attempts for Reliable TX Submission */
        const val VALIDATION_MAX_ATTEMPTS = 20
        /** Delay between polls (ms) — ~4 seconds per ledger close */
        const val VALIDATION_POLL_DELAY_MS = 4_000L
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
     * @param serviceAddress Optional service fee r-address. If provided with serviceFeeDrops > 0,
     *   a second transaction is sent to this address after the primary TX succeeds.
     * @param serviceFeeDrops Service fee amount in drops (0 = no service fee)
     * @param awaitValidated If true, polls `tx` RPC until the TX is validated on ledger
     *   or LastLedgerSequence expires (Reliable Transaction Submission pattern).
     *   If false (default), returns immediately after submit — faster but less certain.
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendXrp(
        toAddress: String,
        amountDrops: Long,
        feeDrops: Long = 0L,
        destinationTag: Long? = null,
        memoText: String? = null,
        serviceAddress: String? = null,
        serviceFeeDrops: Long = 0L,
        awaitValidated: Boolean = false
    ): TransferResponseModel {
        return try {
            val fromAddr = walletAddress
                ?: return TransferResponseModel(false, "No wallet address", null)

            val hasServiceFee = !serviceAddress.isNullOrBlank() && serviceFeeDrops > 0

            // Use dynamic fee if not explicitly provided (0 = auto)
            val actualFee = if (feeDrops > 0) feeDrops else estimateFeeDynamic()

            // Single RPC call for both sequence and ledger index
            val accountInfo = apiService.getAccountInfo(fromAddr)
                ?: return TransferResponseModel(false, "Failed to get account info", null)
            val accountData = accountInfo.result.accountData
                ?: return TransferResponseModel(false, "Account not found on ledger", null)
            val sequence = accountData.sequence
            val ledgerIndex = accountInfo.result.ledgerCurrentIndex ?: 0L
            val lastLedgerSeq = if (ledgerIndex > 0) ledgerIndex + LAST_LEDGER_OFFSET else null

            // Balance validation: amount + fee + reserve, plus service fee TX if applicable
            val balanceDrops = accountData.balance.toLongOrNull() ?: 0L
            val totalFees = if (hasServiceFee) actualFee * 2 else actualFee  // 2 TXs = 2 fees
            val totalAmount = if (hasServiceFee) amountDrops + serviceFeeDrops else amountDrops
            val requiredDrops = totalAmount + totalFees + BASE_RESERVE_DROPS
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
            val txHash = result?.tx_json?.hash ?: signResult.transactionId

            if (result?.engineResult == "tesSUCCESS" || result?.engineResult == "terQUEUED") {
                // Send service fee as second TX with sequence+1 (fire-and-forget)
                if (hasServiceFee) {
                    sendServiceFee(
                        fromAddr, serviceAddress!!, serviceFeeDrops, actualFee,
                        sequence + 1, lastLedgerSeq?.let { it + LAST_LEDGER_OFFSET }
                    )
                }

                // Reliable TX Submission: optionally poll until validated
                if (awaitValidated && lastLedgerSeq != null) {
                    val validation = awaitValidation(txHash, lastLedgerSeq)
                    TransferResponseModel(
                        success = validation.confirmed,
                        error = validation.error,
                        txHash = txHash
                    )
                } else {
                    TransferResponseModel(success = true, error = null, txHash = txHash)
                }
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
     * Send service fee as a separate transaction (fire-and-forget).
     * Uses sequence+1 from the primary transaction.
     * Errors are logged but do not affect the primary TX result.
     */
    private suspend fun sendServiceFee(
        fromAddr: String,
        serviceAddress: String,
        serviceFeeDrops: Long,
        feeDrops: Long,
        sequence: Long,
        lastLedgerSequence: Long?
    ) {
        try {
            val signResult = XrpTransactionSigner.signPayment(
                privateKey = privateKeyBytes,
                publicKey = publicKeyBytes,
                account = fromAddr,
                destination = serviceAddress,
                amountDrops = serviceFeeDrops,
                feeDrops = feeDrops,
                sequence = sequence,
                lastLedgerSequence = lastLedgerSequence
            )
            apiService.submitTransaction(signResult.txBlob)
        } catch (_: Exception) {
            // Service fee is best-effort — don't fail the primary transaction
        }
    }

    // ── Reliable Transaction Submission ────────────────────────────────

    /**
     * Result of awaiting transaction validation.
     * @param confirmed true if TX is validated on ledger with tesSUCCESS
     * @param txHash Transaction hash
     * @param engineResult XRP Ledger result code (e.g. "tesSUCCESS", "tecUNFUNDED_PAYMENT")
     * @param ledgerIndex Ledger index where TX was validated (null if not yet validated)
     * @param error Human-readable error message (null on success)
     */
    data class ValidationResult(
        val confirmed: Boolean,
        val txHash: String,
        val engineResult: String? = null,
        val ledgerIndex: Long? = null,
        val error: String? = null
    )

    /**
     * Poll the XRP Ledger until a submitted transaction is validated or definitively fails.
     *
     * Implements the "Reliable Transaction Submission" pattern from
     * https://xrpl.org/reliable-transaction-submission.html
     *
     * @param txHash Transaction hash to look up
     * @param lastLedgerSequence The LastLedgerSequence set on the TX (expiry boundary)
     * @param maxAttempts Maximum number of poll attempts (default: 20)
     * @param pollDelayMs Delay between polls in ms (default: 4000ms ≈ 1 ledger close)
     * @return ValidationResult with confirmation status and details
     */
    suspend fun awaitValidation(
        txHash: String,
        lastLedgerSequence: Long,
        maxAttempts: Int = VALIDATION_MAX_ATTEMPTS,
        pollDelayMs: Long = VALIDATION_POLL_DELAY_MS
    ): ValidationResult {
        for (attempt in 1..maxAttempts) {
            val txResponse = apiService.getTransaction(txHash)
            val txResult = txResponse?.result

            if (txResult != null) {
                // TX found and validated → definitive result
                if (txResult.isConfirmed) {
                    return ValidationResult(
                        confirmed = true,
                        txHash = txHash,
                        engineResult = txResult.meta?.transactionResult,
                        ledgerIndex = txResult.ledgerIndex
                    )
                }

                // TX found but failed permanently (tec/tem/tef on validated ledger)
                if (txResult.isDefinitiveFailure) {
                    return ValidationResult(
                        confirmed = false,
                        txHash = txHash,
                        engineResult = txResult.meta?.transactionResult,
                        ledgerIndex = txResult.ledgerIndex,
                        error = "Transaction failed: ${txResult.meta?.transactionResult}"
                    )
                }
            }

            // Check if LastLedgerSequence has passed → TX expired
            val currentLedger = getCurrentLedgerIndexSafe()
            if (currentLedger > 0 && currentLedger > lastLedgerSequence) {
                // If TX was not found after its expiry ledger, it's definitively failed
                if (txResult == null || txResult.error == "txnNotFound") {
                    return ValidationResult(
                        confirmed = false,
                        txHash = txHash,
                        error = "Transaction expired: LastLedgerSequence $lastLedgerSequence passed (current: $currentLedger)"
                    )
                }
            }

            // Wait before next poll
            if (attempt < maxAttempts) {
                delay(pollDelayMs)
            }
        }

        // Exhausted all attempts without a definitive result
        return ValidationResult(
            confirmed = false,
            txHash = txHash,
            error = "Validation timeout after $maxAttempts attempts"
        )
    }

    /**
     * Get current ledger index without throwing. Returns 0 on failure.
     */
    private suspend fun getCurrentLedgerIndexSafe(): Long {
        return try {
            val addr = walletAddress ?: return 0L
            val info = apiService.getAccountInfo(addr)
            info?.result?.ledgerCurrentIndex ?: 0L
        } catch (_: Exception) {
            0L
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
