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

    // ── Key derivation — Icarus V2 (ed25519-bip32) for both Shelley and Byron ──

    // Shelley payment key: m/1852'/1815'/account'/0/index
    // Per CIP-1852: purpose, coin_type, account = hardened; role, index = SOFT
    // Returns Triple(pubKey32, chainCode32, extKey64)
    private fun deriveShelleyPaymentKey(account: Int, index: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        var k = masterExt; var cc = masterCC
        IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
        return Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)
    }

    // Shelley staking key: m/1852'/1815'/account'/2/0
    // Returns Triple(pubKey32, chainCode32, extKey64)
    private fun deriveShelleyStakingKey(account: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        var k = masterExt; var cc = masterCC
        IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let  { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 2, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        return Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)
    }

    // Byron payment key: m/44'/1815'/0'/0/index
    // See docs/CARDANO_BYRON_SPEC.md for the full algorithm explanation.
    private fun deriveByronKey(index: Int): Triple<ByteArray, ByteArray, ByteArray> {
        return IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, index)
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
        val (paymentPub, _, _) = deriveShelleyPaymentKey(account, index)
        val paymentKeyHash = CardanoAddress.hashKey(paymentPub)

        val (stakingPub, _, _) = deriveShelleyStakingKey(account)
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
        // IcarusKeyDerivation.deriveByronAddressKey returns (pubKey, chainCode, extKey).
        // The public key is derived via Ed25519Icarus (pre-clamped scalar, no SHA-512).
        val (pubKey, chainCode, _) = deriveByronKey(index)
        return CardanoAddress.createByronAddress(pubKey, chainCode)
    }

    /**
     * Generate a staking (reward) address.
     *
     * @param account HD account index (default 0)
     * @return Bech32-encoded staking address
     */
    fun getStakingAddress(account: Int = 0): String {
        val (stakingPub, _, _) = deriveShelleyStakingKey(account)
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
     * Get transaction history with pagination support.
     * @param address Cardano address (defaults to wallet address)
     * @param count Number of transactions per page (max 100)
     * @param page 1-based page number
     * @param order Sort order: "desc" (newest first) or "asc"
     * @return Pair of (transactions, hasMore)
     */
    suspend fun getTransactionHistoryPaginated(
        address: String? = null,
        count: Int = 20,
        page: Int = 1,
        order: String = "desc"
    ): Pair<List<com.lybia.cryptowallet.models.cardano.CardanoTransactionInfo>, Boolean> {
        val addr = address ?: getAddress()
        logger.d { "getTransactionHistoryPaginated for $addr, count=$count, page=$page" }
        return apiService.getTransactionHistoryPaginated(listOf(addr), count, page, order)
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
     * Get transaction history for a specific native token with pagination.
     *
     * @param policyId 56-char hex policy ID
     * @param assetName Hex-encoded asset name
     * @param count Number of transactions per page (max 100)
     * @param page 1-based page number
     * @param order Sort order: "desc" (newest first) or "asc"
     * @return Pair of (transactions, hasMore)
     */
    suspend fun getTokenTransactionHistoryPaginated(
        policyId: String,
        assetName: String,
        count: Int = 20,
        page: Int = 1,
        order: String = "desc"
    ): Pair<List<com.lybia.cryptowallet.models.cardano.CardanoTransactionInfo>, Boolean> {
        logger.d { "getTokenTransactionHistoryPaginated: $policyId.$assetName, count=$count, page=$page" }
        return apiService.getAssetTransactionsPaginated(policyId, assetName, count, page, order)
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

        // Calculate minimum ADA for the native token output using protocol parameters.
        // CardanoMinUtxo.calculateMinAda() uses: max(1 ADA, (160 + outputSize) * coinsPerUtxoByte)
        val policyIdBytes = hexToBytes(policyId)
        val assetNameBytes = hexToBytes(assetName)
        val toAddressBytes = addressToBytes(toAddress)
        val dummyTokenOutput = CardanoTransactionOutput(
            addressBytes = toAddressBytes,
            lovelace = 2_000_000L, // Use realistic lovelace value for accurate CBOR size estimation
            multiAssets = mapOf(policyIdBytes to mapOf(assetNameBytes to amount))
        )
        val minAda = CardanoMinUtxo.calculateMinAda(dummyTokenOutput, COINS_PER_UTXO_BYTE)
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
            // Multi-asset change: validate ADA meets minimum for the change output
            val changeOutput = CardanoTransactionOutput(
                addressBytes = fromAddressBytes,
                lovelace = changeLovelace,
                multiAssets = mapOf(policyIdBytes to mapOf(assetNameBytes to changeTokens))
            )
            val minAdaForChange = CardanoMinUtxo.calculateMinAda(changeOutput, COINS_PER_UTXO_BYTE)
            if (changeLovelace < minAdaForChange) {
                throw CardanoError.ApiError(
                    statusCode = null,
                    message = "Insufficient ADA for token change output: ${changeLovelace} lovelace available, ${minAdaForChange} required. Select UTXOs with more ADA."
                )
            }
            builder.addMultiAssetOutput(
                fromAddressBytes,
                changeLovelace,
                mapOf(policyIdBytes to mapOf(assetNameBytes to changeTokens))
            )
        } else if (changeLovelace >= CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
            // ADA-only change: only add output if above minimum UTXO (absorb dust into effective fee)
            builder.addOutput(fromAddressBytes, changeLovelace)
        }

        builder.setFee(fee)
        builder.setTtl(ttl)

        val body = builder.build()

        // Sign with Icarus ed25519-bip32
        val (paymentPub, _, paymentExtKey) = deriveShelleyPaymentKey(0, 0)
        val txHash = body.getHash()
        val signature = Ed25519Icarus.sign(paymentExtKey, txHash)

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

        // Change output: dust (<1 ADA) is absorbed into fee to keep output above MIN_UTXO
        val rawChange = collected - amount - fee - serviceFeeTotal
        val effectiveFee: Long
        val actualChange: Long
        if (rawChange > 0L && rawChange < CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
            effectiveFee = fee + rawChange  // absorb dust into fee
            actualChange = 0L
        } else {
            effectiveFee = fee
            actualChange = rawChange
        }
        if (actualChange > 0L) {
            val fromAddressBytes = addressToBytes(fromAddress)
            builder.addOutput(fromAddressBytes, actualChange)
        }

        builder.setFee(effectiveFee)
        builder.setTtl(ttl)

        val body = builder.build()

        // Sign with Icarus ed25519-bip32 — kL as scalar directly (NOT RFC 8032)
        val (paymentPub, _, paymentExtKey) = deriveShelleyPaymentKey(0, 0)
        val txHash = body.getHash()
        val signature = Ed25519Icarus.sign(paymentExtKey, txHash)

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(paymentPub, signature)
            .build()

        val signedTx = CardanoSignedTransaction(body, witnessSet)
        val txBytes = signedTx.serialize()
        if (txBytes.size > CardanoTransactionBuilder.MAX_TX_SIZE_BYTES) {
            throw CardanoError.ApiError(
                statusCode = null,
                message = "Transaction too large: ${txBytes.size} bytes (max ${CardanoTransactionBuilder.MAX_TX_SIZE_BYTES})"
            )
        }
        return signedTx
    }

    // ── Byron Transaction (Bug #5-#8 fix) ──────────────────────────────────

    /**
     * Build and sign a Byron ADA transfer transaction.
     *
     * Byron UTXOs (from addresses starting with "Ae2") require:
     * - Icarus ed25519-bip32 signing (NOT RFC 8032 / ton-kotlin Ed25519)
     * - BootstrapWitness (CBOR key 2) with pubKey + signature + chainCode + attributes
     *
     * Use this when the sender has funds in an old Byron address.
     * For Shelley addresses use [buildAndSignTransaction].
     *
     * @param toAddress   Destination address (Byron or Shelley)
     * @param amount      Amount to send in lovelace
     * @param fee         Network fee in lovelace
     * @param fromIndex   Byron address derivation index (default 0)
     * @param serviceAddress Optional service fee address
     * @param serviceFeeLovelace Optional service fee amount in lovelace
     */
    suspend fun buildAndSignByronTransaction(
        toAddress: String,
        amount: Long,
        fee: Long,
        fromIndex: Int = 0,
        serviceAddress: String? = null,
        serviceFeeLovelace: Long = 0
    ): CardanoSignedTransaction {
        val hasServiceFee = !serviceAddress.isNullOrBlank() && serviceFeeLovelace > 0
        logger.d { "buildAndSignByronTransaction: $amount lovelace to $toAddress, fee=$fee, index=$fromIndex" }

        // Derive Byron key at m/44'/1815'/0'/0/fromIndex
        val (pubKey32, chainCode32, extKey64) = deriveByronKey(fromIndex)
        val fromAddress = CardanoAddress.createByronAddress(pubKey32, chainCode32)

        // Fetch UTXOs for the Byron address
        val apiUtxos = apiService.getUtxos(listOf(fromAddress))
        val utxos = apiUtxos.map { apiUtxo ->
            val lovelace = apiUtxo.amount
                .filter { it.unit == "lovelace" }
                .sumOf { it.quantity.toLongOrNull() ?: 0L }
            CardanoUtxo(txHash = apiUtxo.txHash, index = apiUtxo.txIndex, lovelace = lovelace)
        }

        // UTXO selection
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
                message = "Insufficient ADA: available=$collected, required=$requiredTotal"
            )
        }

        // Get TTL
        val currentBlock = apiService.getCurrentBlock()
        val ttl = currentBlock.slot + 7200

        // Build transaction body
        val builder = CardanoTransactionBuilder()
        for (utxo in selected) {
            builder.addInput(utxo.txHash, utxo.index)
        }

        val toAddressBytes = addressToBytes(toAddress)
        builder.addOutput(toAddressBytes, amount)

        if (hasServiceFee) {
            val serviceAddressBytes = addressToBytes(serviceAddress!!)
            builder.addOutput(serviceAddressBytes, serviceFeeLovelace)
        }

        // Change output: dust (<1 ADA) is absorbed into fee to keep output above MIN_UTXO
        val rawChange = collected - amount - fee - serviceFeeTotal
        val effectiveFee: Long
        val actualChange: Long
        if (rawChange > 0L && rawChange < CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
            effectiveFee = fee + rawChange  // absorb dust into fee
            actualChange = 0L
        } else {
            effectiveFee = fee
            actualChange = rawChange
        }
        if (actualChange > 0L) {
            val fromAddressBytes = addressToBytes(fromAddress)
            builder.addOutput(fromAddressBytes, actualChange)
        }

        builder.setFee(effectiveFee)
        builder.setTtl(ttl)

        val body = builder.build()

        // Sign using Icarus ed25519-bip32 — NOT standard RFC 8032
        val txHash = body.getHash()
        val signature = Ed25519Icarus.sign(extKey64, txHash)

        // Bootstrap witness: [pubKey32, sig64, chainCode32, 0xa0 (CBOR empty map)]
        val attributes = byteArrayOf(0xa0.toByte())
        val witnessSet = CardanoWitnessBuilder()
            .addBootstrapWitness(pubKey32, signature, chainCode32, attributes)
            .build()

        val signedTx = CardanoSignedTransaction(body, witnessSet)
        val txBytes = signedTx.serialize()
        if (txBytes.size > CardanoTransactionBuilder.MAX_TX_SIZE_BYTES) {
            throw CardanoError.ApiError(
                statusCode = null,
                message = "Transaction too large: ${txBytes.size} bytes (max ${CardanoTransactionBuilder.MAX_TX_SIZE_BYTES})"
            )
        }
        return signedTx
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
            val (stakingPub, _, stakingExtKey) = deriveShelleyStakingKey(0)
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

            // Check if staking key is already registered to avoid double registration.
            // getAccountInfo returns 404 (throws) when the key has never been registered.
            val isAlreadyRegistered = try {
                apiService.getAccountInfo(stakingAddress)
                true
            } catch (e: Exception) {
                false
            }

            val fee = STAKING_TX_FEE_LOVELACE
            val deposit = if (isAlreadyRegistered) 0L else STAKE_KEY_DEPOSIT_LOVELACE
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

            // Change output: inputs = outputs + fee + deposit (ledger balance rule)
            val change = collected - fee - deposit
            if (change > 0) {
                val fromAddressBytes = addressToBytes(fromAddress)
                builder.addOutput(fromAddressBytes, change)
            }

            builder.setFee(fee)
            builder.setTtl(ttl)

            // Only register stake key if not already registered (avoids double deposit)
            if (!isAlreadyRegistered) {
                builder.addCertificate(CardanoCertificate.StakeRegistration(stakingKeyHash))
            }
            builder.addCertificate(CardanoCertificate.Delegation(stakingKeyHash, poolKeyHash))

            val body = builder.build()

            // Sign with both payment key and staking key (Icarus ed25519-bip32)
            val (paymentPub, _, paymentExtKey) = deriveShelleyPaymentKey(0, 0)
            val txHash = body.getHash()
            val paymentSig = Ed25519Icarus.sign(paymentExtKey, txHash)
            val stakingSig = Ed25519Icarus.sign(stakingExtKey, txHash)

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
            val (stakingPub, _, stakingExtKey) = deriveShelleyStakingKey(0)
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

            val fee = STAKING_TX_FEE_LOVELACE
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
            val depositRefund = STAKE_KEY_DEPOSIT_LOVELACE
            val change = collected - fee + depositRefund
            if (change > 0) {
                val fromAddressBytes = addressToBytes(fromAddress)
                builder.addOutput(fromAddressBytes, change)
            }

            builder.setFee(fee)
            builder.setTtl(ttl)
            builder.addCertificate(CardanoCertificate.StakeDeregistration(stakingKeyHash))

            val body = builder.build()

            // Sign with both payment key and staking key (Icarus ed25519-bip32)
            val (paymentPub, _, paymentExtKey) = deriveShelleyPaymentKey(0, 0)
            val txHash = body.getHash()
            val paymentSig = Ed25519Icarus.sign(paymentExtKey, txHash)
            val stakingSig = Ed25519Icarus.sign(stakingExtKey, txHash)

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
         * Protocol parameter: lovelace per UTXO byte (Babbage era mainnet/testnet = 4310).
         * Used to calculate minimum ADA for multi-asset outputs.
         * Formula: max(1 ADA, (160 + outputSize) * COINS_PER_UTXO_BYTE)
         */
        internal const val COINS_PER_UTXO_BYTE = 4310L

        /**
         * Estimated fee for staking transactions (~400-byte tx).
         * Formula: minFeeA(44) * 400 + minFeeB(155381) = 172,981 → rounded up to 200,000.
         */
        internal const val STAKING_TX_FEE_LOVELACE = 200_000L

        /**
         * Stake key registration deposit — current Cardano protocol constant (2 ADA).
         * Refunded upon stake key deregistration.
         */
        internal const val STAKE_KEY_DEPOSIT_LOVELACE = 2_000_000L

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
