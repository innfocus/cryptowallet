package com.lybia.cryptowallet.wallets.cardano

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.errors.StakingError
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.CardanoApiService
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MnemonicCode

/**
 * Main Cardano wallet manager.
 *
 * Extends [BaseCoinManager] and provides Shelley/Byron address generation (CIP-1852),
 * balance queries, transaction history, ADA transfers, and native token operations.
 *
 * @param mnemonic BIP-39 mnemonic phrase (space-separated words)
 * @param apiService Optional [CardanoApiService] for dependency injection / testing
 */
class CardanoManager(
    private val mnemonic: String,
    private val apiService: CardanoApiService = CardanoApiService(
        baseUrl = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
    )
) : BaseCoinManager(), IStakingManager {

    private val logger = Logger.withTag("CardanoManager")
    private val mnemonicWords = mnemonic.split(" ").filter { it.isNotEmpty() }

    // ── BIP-39 seed & SLIP-0010 Ed25519 master key ──────────────────────────

    private val seed: ByteArray = MnemonicCode.toSeed(mnemonicWords, "")

    /**
     * SLIP-0010 Ed25519 key derivation (hardened only).
     * Returns (privateKey 32 bytes, chainCode 32 bytes).
     */
    private fun slip10DeriveEd25519(path: IntArray): Pair<ByteArray, ByteArray> {
        var iBytes = Crypto.hmac512("ed25519 seed".encodeToByteArray(), seed)
        var kL = iBytes.sliceArray(0 until 32)
        var kR = iBytes.sliceArray(32 until 64)

        for (index in path) {
            val data = ByteArray(37)
            data[0] = 0x00
            kL.copyInto(data, 1)
            data[33] = (index ushr 24).toByte()
            data[34] = (index ushr 16).toByte()
            data[35] = (index ushr 8).toByte()
            data[36] = index.toByte()
            iBytes = Crypto.hmac512(kR, data)
            kL = iBytes.sliceArray(0 until 32)
            kR = iBytes.sliceArray(32 until 64)
        }
        return kL to kR
    }

    /**
     * Derive Ed25519 public key from private key using Crypto.
     * Returns 32-byte compressed Ed25519 public key.
     */
    private fun ed25519PublicKey(privateKey: ByteArray): ByteArray {
        // fr.acinq.bitcoin.Crypto provides Ed25519 via libsecp256k1 or platform impl
        // However, bitcoin-kmp doesn't expose Ed25519 directly.
        // We use the same approach as TonManager: SLIP-0010 derives the private key,
        // then we compute the public key via the available crypto primitives.
        //
        // For Cardano CIP-1852, we need Ed25519 key pairs.
        // We'll use a minimal Ed25519 implementation via the available libraries.
        return computeEd25519PublicKey(privateKey)
    }

    // ── CIP-1852 derivation paths ───────────────────────────────────────────
    // m / 1852' / 1815' / account' / role / index
    // role: 0 = external (payment), 1 = internal (change), 2 = staking

    private fun hardenedIndex(i: Int): Int = 0x80000000.toInt() or i

    private fun derivePaymentKey(account: Int, index: Int): Pair<ByteArray, ByteArray> {
        return slip10DeriveEd25519(intArrayOf(
            hardenedIndex(1852),
            hardenedIndex(1815),
            hardenedIndex(account),
            hardenedIndex(0), // external/payment role (hardened for SLIP-0010)
            hardenedIndex(index)
        ))
    }

    private fun deriveStakingKey(account: Int): Pair<ByteArray, ByteArray> {
        return slip10DeriveEd25519(intArrayOf(
            hardenedIndex(1852),
            hardenedIndex(1815),
            hardenedIndex(account),
            hardenedIndex(2), // staking role
            hardenedIndex(0)
        ))
    }

    private fun deriveByronKey(index: Int): Pair<ByteArray, ByteArray> {
        return slip10DeriveEd25519(intArrayOf(
            hardenedIndex(44),
            hardenedIndex(1815),
            hardenedIndex(0),
            hardenedIndex(0),
            hardenedIndex(index)
        ))
    }

    // ── Address generation (Task 6.2) ───────────────────────────────────────

    /**
     * Generate a Shelley base address (payment + staking key) using CIP-1852 derivation.
     *
     * @param account HD account index (default 0)
     * @param index Address index (default 0)
     * @return Bech32-encoded Shelley base address
     */
    fun getShelleyAddress(account: Int = 0, index: Int = 0): String {
        val (paymentPriv, _) = derivePaymentKey(account, index)
        val paymentPub = ed25519PublicKey(paymentPriv)
        val paymentKeyHash = CardanoAddress.hashKey(paymentPub)

        val (stakingPriv, _) = deriveStakingKey(account)
        val stakingPub = ed25519PublicKey(stakingPriv)
        val stakingKeyHash = CardanoAddress.hashKey(stakingPub)

        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        return CardanoAddress.createBaseAddress(paymentKeyHash, stakingKeyHash, isTestnet)
    }

    /**
     * Generate a Byron-era address.
     *
     * @param index Address index (default 0)
     * @return Base58-encoded Byron address
     */
    fun getByronAddress(index: Int = 0): String {
        val (privKey, chainCode) = deriveByronKey(index)
        val pubKey = ed25519PublicKey(privKey)
        return CardanoAddress.createByronAddress(pubKey, chainCode)
    }

    /**
     * Generate a staking (reward) address.
     *
     * @param account HD account index (default 0)
     * @return Bech32-encoded staking address
     */
    fun getStakingAddress(account: Int = 0): String {
        val (stakingPriv, _) = deriveStakingKey(account)
        val stakingPub = ed25519PublicKey(stakingPriv)
        val stakingKeyHash = CardanoAddress.hashKey(stakingPub)

        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        return CardanoAddress.createRewardAddress(stakingKeyHash, isTestnet)
    }

    // ── BaseCoinManager overrides (Task 6.1) ────────────────────────────────

    /**
     * Returns the first Shelley base address (account=0, index=0).
     */
    override fun getAddress(): String {
        return getShelleyAddress(account = 0, index = 0)
    }

    /**
     * Query ADA balance for the given address via the API service.
     * Returns balance in ADA (not lovelace).
     */
    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        val addr = address ?: getAddress()
        logger.d { "getBalance for $addr" }
        val utxos = apiService.getUtxos(listOf(addr))
        val totalLovelace = utxos.sumOf { utxo ->
            utxo.amount
                .filter { it.unit == "lovelace" }
                .sumOf { it.quantity.toLongOrNull() ?: 0L }
        }
        return totalLovelace.toDouble() / 1_000_000.0
    }

    /**
     * Query transaction history for the given address.
     */
    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        val addr = address ?: getAddress()
        logger.d { "getTransactionHistory for $addr" }
        return apiService.getTransactionHistory(listOf(addr))
    }

    /**
     * Submit a signed transaction (Base64-encoded CBOR) to the network.
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
        return if (Config.shared.getNetwork() == Network.MAINNET) "mainnet" else "testnet"
    }

    // ── Native token operations (Task 6.3) ──────────────────────────────────

    /**
     * Get the balance of a specific native token for an address.
     *
     * @param address Cardano address
     * @param policyId 56-char hex policy ID
     * @param assetName Hex-encoded asset name
     * @return Token amount (raw, not adjusted for decimals)
     */
    suspend fun getTokenBalance(address: String, policyId: String, assetName: String): Long {
        logger.d { "getTokenBalance: $policyId.$assetName for $address" }
        val tokens = apiService.getAddressAssets(address)
        return tokens
            .filter { it.policyId == policyId && it.assetName == assetName }
            .sumOf { it.amount }
    }

    /**
     * Send native tokens to an address.
     * Builds a transaction that sends the specified token amount along with minimum ADA.
     *
     * @param toAddress Destination address
     * @param policyId 56-char hex policy ID
     * @param assetName Hex-encoded asset name
     * @param amount Token amount to send
     * @param fee Transaction fee in lovelace
     * @return Transaction hash
     */
    suspend fun sendToken(
        toAddress: String,
        policyId: String,
        assetName: String,
        amount: Long,
        fee: Long
    ): String {
        logger.d { "sendToken: $amount of $policyId.$assetName to $toAddress" }
        val fromAddress = getAddress()

        // Get UTXOs and select ones with enough tokens + ADA
        val apiUtxos = apiService.getUtxos(listOf(fromAddress))
        val utxos = apiUtxos.map { apiUtxo ->
            val lovelace = apiUtxo.amount
                .filter { it.unit == "lovelace" }
                .sumOf { it.quantity.toLongOrNull() ?: 0L }
            val nativeTokens = apiUtxo.amount
                .filter { it.unit != "lovelace" }
                .map { amt ->
                    CardanoNativeToken(
                        policyId = amt.unit.take(56),
                        assetName = amt.unit.drop(56),
                        amount = amt.quantity.toLongOrNull() ?: 0L
                    )
                }
            CardanoUtxo(
                txHash = apiUtxo.txHash,
                index = apiUtxo.txIndex,
                lovelace = lovelace,
                nativeTokens = nativeTokens
            )
        }

        // Min ADA for token output (estimate)
        val minAda = 2_000_000L
        val requiredAda = fee + minAda

        val selectedUtxos = CardanoUtxoSelector.selectUtxos(
            utxos, policyId, assetName, amount, requiredAda
        )

        // Build transaction
        val currentBlock = apiService.getCurrentBlock()
        val ttl = currentBlock.slot + 7200 // ~2 hours

        val builder = CardanoTransactionBuilder()
        for (utxo in selectedUtxos) {
            builder.addInput(utxo.txHash, utxo.index)
        }

        // Token output to recipient
        val policyIdBytes = hexToBytes(policyId)
        val assetNameBytes = hexToBytes(assetName)
        val toAddressBytes = addressToBytes(toAddress)
        builder.addMultiAssetOutput(
            toAddressBytes,
            minAda,
            mapOf(policyIdBytes to mapOf(assetNameBytes to amount))
        )

        // Change output
        val totalInputLovelace = selectedUtxos.sumOf { it.lovelace }
        val totalInputTokens = selectedUtxos.sumOf { it.tokenAmount(policyId, assetName) }
        val changeLovelace = totalInputLovelace - fee - minAda
        val changeTokens = totalInputTokens - amount

        val fromAddressBytes = addressToBytes(fromAddress)
        if (changeTokens > 0) {
            builder.addMultiAssetOutput(
                fromAddressBytes,
                changeLovelace,
                mapOf(policyIdBytes to mapOf(assetNameBytes to changeTokens))
            )
        } else if (changeLovelace > 0) {
            builder.addOutput(fromAddressBytes, changeLovelace)
        }

        builder.setFee(fee)
        builder.setTtl(ttl)

        val body = builder.build()

        // Sign
        val (paymentPriv, _) = derivePaymentKey(0, 0)
        val paymentPub = ed25519PublicKey(paymentPriv)
        val txHash = body.getHash()
        val signature = ed25519Sign(paymentPriv, txHash)

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(paymentPub, signature)
            .build()

        val signedTx = CardanoSignedTransaction(body, witnessSet)
        return apiService.submitTransaction(signedTx.serialize())
    }

    // ── Transaction building & signing (Task 6.4) ───────────────────────────

    /**
     * Build and sign an ADA transfer transaction.
     *
     * @param toAddress Destination address
     * @param amount Amount in lovelace
     * @param fee Fee in lovelace
     * @return Signed transaction ready for submission
     */
    suspend fun buildAndSignTransaction(
        toAddress: String,
        amount: Long,
        fee: Long,
        serviceAddress: String? = null,
        serviceFeeLovelace: Long = 0
    ): CardanoSignedTransaction {
        val hasServiceFee = !serviceAddress.isNullOrBlank() && serviceFeeLovelace > 0
        logger.d { "buildAndSignTransaction: $amount lovelace to $toAddress, fee=$fee, serviceFee=$serviceFeeLovelace, hasServiceFee=$hasServiceFee" }
        val fromAddress = getAddress()

        // Fetch UTXOs
        val apiUtxos = apiService.getUtxos(listOf(fromAddress))
        val utxos = apiUtxos.map { apiUtxo ->
            val lovelace = apiUtxo.amount
                .filter { it.unit == "lovelace" }
                .sumOf { it.quantity.toLongOrNull() ?: 0L }
            CardanoUtxo(
                txHash = apiUtxo.txHash,
                index = apiUtxo.txIndex,
                lovelace = lovelace
            )
        }

        // Simple UTXO selection: sort by lovelace descending, pick until enough
        val serviceFeeTotal = if (hasServiceFee) serviceFeeLovelace else 0L
        val requiredTotal = amount + fee + serviceFeeTotal
        val sorted = utxos.sortedByDescending { it.lovelace }
        val selected = mutableListOf<CardanoUtxo>()
        var collected = 0L
        for (utxo in sorted) {
            if (collected >= requiredTotal) break
            selected.add(utxo)
            collected += utxo.lovelace
        }

        if (collected < requiredTotal) {
            throw CardanoError.ApiError(
                statusCode = null,
                message = "Insufficient ADA: available=${collected}, required=${requiredTotal}"
            )
        }

        // Get current slot for TTL
        val currentBlock = apiService.getCurrentBlock()
        val ttl = currentBlock.slot + 7200

        // Build transaction body
        val builder = CardanoTransactionBuilder()
        for (utxo in selected) {
            builder.addInput(utxo.txHash, utxo.index)
        }

        val toAddressBytes = addressToBytes(toAddress)
        builder.addOutput(toAddressBytes, amount)

        // Service fee output
        if (hasServiceFee) {
            val serviceAddressBytes = addressToBytes(serviceAddress!!)
            builder.addOutput(serviceAddressBytes, serviceFeeLovelace)
        }

        // Change output
        val change = collected - amount - fee - serviceFeeTotal
        if (change > 0) {
            val fromAddressBytes = addressToBytes(fromAddress)
            builder.addOutput(fromAddressBytes, change)
        }

        builder.setFee(fee)
        builder.setTtl(ttl)

        val body = builder.build()

        // Sign with payment key
        val (paymentPriv, _) = derivePaymentKey(0, 0)
        val paymentPub = ed25519PublicKey(paymentPriv)
        val txHash = body.getHash()
        val signature = ed25519Sign(paymentPriv, txHash)

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(paymentPub, signature)
            .build()

        return CardanoSignedTransaction(body, witnessSet)
    }

    // ── IStakingManager implementation ──────────────────────────────────

    /**
     * Delegate ADA to a stake pool.
     * Builds a transaction with a delegation certificate, signed with both payment and staking keys.
     *
     * @param amount Amount in lovelace (used for balance check; delegation delegates entire stake)
     * @param poolAddress Bech32 pool ID (pool1...)
     * @param coinNetwork Network configuration
     * @return TransferResponseModel with transaction hash on success
     */
    override suspend fun stake(amount: Long, poolAddress: String, coinNetwork: CoinNetwork): TransferResponseModel {
        logger.d { "stake: amount=$amount, pool=$poolAddress" }
        return try {
            val fromAddress = getAddress()
            val stakingAddress = getStakingAddress()

            // Decode pool address to get pool key hash (28 bytes)
            val poolKeyHash = try {
                val (_, data5bit) = com.lybia.cryptowallet.utils.Bech32.decode(poolAddress)
                com.lybia.cryptowallet.utils.Bech32.convertBits(data5bit, 5, 8, false)
            } catch (e: Exception) {
                throw StakingError.PoolNotFound(poolAddress)
            }
            if (poolKeyHash.size != 28) {
                throw StakingError.PoolNotFound(poolAddress)
            }

            // Get staking key hash
            val (stakingPriv, _) = deriveStakingKey(0)
            val stakingPub = ed25519PublicKey(stakingPriv)
            val stakingKeyHash = CardanoAddress.hashKey(stakingPub)

            // Check balance
            val apiUtxos = apiService.getUtxos(listOf(fromAddress))
            val utxos = apiUtxos.map { apiUtxo ->
                val lovelace = apiUtxo.amount
                    .filter { it.unit == "lovelace" }
                    .sumOf { it.quantity.toLongOrNull() ?: 0L }
                CardanoUtxo(
                    txHash = apiUtxo.txHash,
                    index = apiUtxo.txIndex,
                    lovelace = lovelace
                )
            }

            val fee = 200_000L // estimated fee
            val deposit = 2_000_000L // stake key registration deposit
            val requiredTotal = fee + deposit
            val totalAvailable = utxos.sumOf { it.lovelace }

            if (totalAvailable < requiredTotal) {
                throw StakingError.InsufficientStakingBalance(
                    available = totalAvailable.toDouble() / 1_000_000.0,
                    required = requiredTotal.toDouble() / 1_000_000.0
                )
            }

            // Select UTXOs
            val sorted = utxos.sortedByDescending { it.lovelace }
            val selected = mutableListOf<CardanoUtxo>()
            var collected = 0L
            for (utxo in sorted) {
                if (collected >= requiredTotal) break
                selected.add(utxo)
                collected += utxo.lovelace
            }

            // Get current slot for TTL
            val currentBlock = apiService.getCurrentBlock()
            val ttl = currentBlock.slot + 7200

            // Build transaction with delegation certificate
            val builder = CardanoTransactionBuilder()
            for (utxo in selected) {
                builder.addInput(utxo.txHash, utxo.index)
            }

            // Change output
            val change = collected - fee
            if (change > 0) {
                val fromAddressBytes = addressToBytes(fromAddress)
                builder.addOutput(fromAddressBytes, change)
            }

            builder.setFee(fee)
            builder.setTtl(ttl)

            // Add stake registration + delegation certificates
            builder.addCertificate(CardanoCertificate.StakeRegistration(stakingKeyHash))
            builder.addCertificate(CardanoCertificate.Delegation(stakingKeyHash, poolKeyHash))

            val body = builder.build()

            // Sign with both payment key and staking key
            val (paymentPriv, _) = derivePaymentKey(0, 0)
            val paymentPub = ed25519PublicKey(paymentPriv)
            val txHash = body.getHash()
            val paymentSig = ed25519Sign(paymentPriv, txHash)
            val stakingSig = ed25519Sign(stakingPriv, txHash)

            val witnessSet = CardanoWitnessBuilder()
                .addVKeyWitness(paymentPub, paymentSig)
                .addVKeyWitness(stakingPub, stakingSig)
                .build()

            val signedTx = CardanoSignedTransaction(body, witnessSet)
            val resultHash = apiService.submitTransaction(signedTx.serialize())
            TransferResponseModel(success = true, error = null, txHash = resultHash)
        } catch (e: StakingError) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "stake failed" }
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Undelegate (deregister staking key).
     * Builds a transaction with a deregistration certificate.
     *
     * @param amount Not used for Cardano undelegation
     * @param coinNetwork Network configuration
     * @return TransferResponseModel with transaction hash on success
     */
    override suspend fun unstake(amount: Long, coinNetwork: CoinNetwork): TransferResponseModel {
        logger.d { "unstake" }
        return try {
            val fromAddress = getAddress()
            val stakingAddress = getStakingAddress()

            // Check if delegation is active
            val accountInfo = try {
                apiService.getAccountInfo(stakingAddress)
            } catch (e: Exception) {
                throw StakingError.NoDelegationActive(stakingAddress)
            }

            if (!accountInfo.active) {
                throw StakingError.NoDelegationActive(stakingAddress)
            }

            // Get staking key hash
            val (stakingPriv, _) = deriveStakingKey(0)
            val stakingPub = ed25519PublicKey(stakingPriv)
            val stakingKeyHash = CardanoAddress.hashKey(stakingPub)

            // Get UTXOs for fee
            val apiUtxos = apiService.getUtxos(listOf(fromAddress))
            val utxos = apiUtxos.map { apiUtxo ->
                val lovelace = apiUtxo.amount
                    .filter { it.unit == "lovelace" }
                    .sumOf { it.quantity.toLongOrNull() ?: 0L }
                CardanoUtxo(
                    txHash = apiUtxo.txHash,
                    index = apiUtxo.txIndex,
                    lovelace = lovelace
                )
            }

            val fee = 200_000L
            val sorted = utxos.sortedByDescending { it.lovelace }
            val selected = mutableListOf<CardanoUtxo>()
            var collected = 0L
            for (utxo in sorted) {
                if (collected >= fee) break
                selected.add(utxo)
                collected += utxo.lovelace
            }

            // Get current slot for TTL
            val currentBlock = apiService.getCurrentBlock()
            val ttl = currentBlock.slot + 7200

            // Build transaction with deregistration certificate
            val builder = CardanoTransactionBuilder()
            for (utxo in selected) {
                builder.addInput(utxo.txHash, utxo.index)
            }

            // Change output (collected - fee + 2 ADA deposit refund)
            val depositRefund = 2_000_000L
            val change = collected - fee + depositRefund
            if (change > 0) {
                val fromAddressBytes = addressToBytes(fromAddress)
                builder.addOutput(fromAddressBytes, change)
            }

            builder.setFee(fee)
            builder.setTtl(ttl)
            builder.addCertificate(CardanoCertificate.StakeDeregistration(stakingKeyHash))

            val body = builder.build()

            // Sign with both payment key and staking key
            val (paymentPriv, _) = derivePaymentKey(0, 0)
            val paymentPub = ed25519PublicKey(paymentPriv)
            val txHash = body.getHash()
            val paymentSig = ed25519Sign(paymentPriv, txHash)
            val stakingSig = ed25519Sign(stakingPriv, txHash)

            val witnessSet = CardanoWitnessBuilder()
                .addVKeyWitness(paymentPub, paymentSig)
                .addVKeyWitness(stakingPub, stakingSig)
                .build()

            val signedTx = CardanoSignedTransaction(body, witnessSet)
            val resultHash = apiService.submitTransaction(signedTx.serialize())
            TransferResponseModel(success = true, error = null, txHash = resultHash)
        } catch (e: StakingError) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "unstake failed" }
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Query staking rewards for the given staking address.
     *
     * @param address Staking address (if null, derives from mnemonic)
     * @param coinNetwork Network configuration
     * @return Rewards in ADA (lovelace / 1,000,000)
     */
    override suspend fun getStakingRewards(address: String, coinNetwork: CoinNetwork): Double {
        val stakingAddress = address.ifEmpty { getStakingAddress() }
        logger.d { "getStakingRewards: $stakingAddress" }
        return try {
            val accountInfo = apiService.getAccountInfo(stakingAddress)
            val withdrawableLovelace = accountInfo.withdrawableAmount.toLongOrNull() ?: 0L
            withdrawableLovelace.toDouble() / 1_000_000.0
        } catch (e: Exception) {
            logger.e(e) { "getStakingRewards failed" }
            0.0
        }
    }

    /**
     * Query delegation status and staked balance.
     *
     * @param address Staking address (if null, derives from mnemonic)
     * @param poolAddress Not used for Cardano balance query
     * @param coinNetwork Network configuration
     * @return Staked amount in ADA (lovelace / 1,000,000), or 0.0 if no active delegation
     */
    override suspend fun getStakingBalance(address: String, poolAddress: String, coinNetwork: CoinNetwork): Double {
        val stakingAddress = address.ifEmpty { getStakingAddress() }
        logger.d { "getStakingBalance: $stakingAddress" }
        return try {
            val accountInfo = apiService.getAccountInfo(stakingAddress)
            if (!accountInfo.active) return 0.0
            val controlledLovelace = accountInfo.controlledAmount.toLongOrNull() ?: 0L
            controlledLovelace.toDouble() / 1_000_000.0
        } catch (e: Exception) {
            logger.e(e) { "getStakingBalance failed" }
            0.0
        }
    }

    // ── Ed25519 helpers ─────────────────────────────────────────────────────

    companion object {
        /**
         * Compute Ed25519 public key from a 32-byte private key.
         * Uses a minimal pure-Kotlin Ed25519 implementation.
         */
        internal fun computeEd25519PublicKey(privateKey: ByteArray): ByteArray {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }
            return Ed25519.publicKey(privateKey)
        }

        /**
         * Sign a message with Ed25519.
         */
        internal fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }
            return Ed25519.sign(privateKey, message)
        }

        /**
         * Convert a Cardano address string to raw bytes for transaction outputs.
         * Handles both Shelley (Bech32) and Byron (Base58) addresses.
         */
        internal fun addressToBytes(address: String): ByteArray {
            return when (CardanoAddress.getAddressType(address)) {
                CardanoAddressType.BYRON -> {
                    fr.acinq.bitcoin.Base58.decode(address)
                }
                CardanoAddressType.SHELLEY_BASE,
                CardanoAddressType.SHELLEY_ENTERPRISE,
                CardanoAddressType.SHELLEY_REWARD -> {
                    val (_, data5bit) = com.lybia.cryptowallet.utils.Bech32.decode(address)
                    com.lybia.cryptowallet.utils.Bech32.convertBits(data5bit, 5, 8, false)
                }
                CardanoAddressType.UNKNOWN -> {
                    throw CardanoError.ApiError(
                        statusCode = null,
                        message = "Unknown address format: $address"
                    )
                }
            }
        }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length / 2
            val result = ByteArray(len)
            for (i in 0 until len) {
                result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }
    }
}
