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
import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

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
    private val mnemonicWords = Bip39Language.splitMnemonic(mnemonic)

    // ── Key cache — eliminates redundant PBKDF2 and derivation across operations ──
    // See docs/architecture/cardano-crypto-performance.md (Phần B) for design rationale.

    private data class AccountCacheKey(val purpose: Int, val account: Int)

    private val lock = SynchronizedObject()

    // Cache L1: Master key (PBKDF2-4096 result) — lazily computed, at most once per instance
    @kotlin.concurrent.Volatile private var masterKeyCache: Pair<ByteArray, ByteArray>? = null

    // Cache L2: Account-level keys at m/purpose'/1815'/account'
    private val accountKeyCache = mutableMapOf<AccountCacheKey, Pair<ByteArray, ByteArray>>()

    // Cache L3: Final derived keys — Triple(pubKey32, chainCode32, extKey64)
    private val shelleyPaymentKeyCache = mutableMapOf<Pair<Int, Int>, Triple<ByteArray, ByteArray, ByteArray>>()
    private val shelleyStakingKeyCache = mutableMapOf<Int, Triple<ByteArray, ByteArray, ByteArray>>()
    private val byronKeyCache = mutableMapOf<Int, Triple<ByteArray, ByteArray, ByteArray>>()

    private fun getMasterKey(): Pair<ByteArray, ByteArray> = synchronized(lock) {
        masterKeyCache ?: IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
            .also { masterKeyCache = it }
    }

    private fun getShelleyAccountKey(account: Int): Pair<ByteArray, ByteArray> = synchronized(lock) {
        val cacheKey = AccountCacheKey(purpose = 1852, account = account)
        accountKeyCache[cacheKey]?.let { return@synchronized it }

        val (masterExt, masterCC) = getMasterKey()
        var k = masterExt; var cc = masterCC
        IcarusKeyDerivation.deriveChildKey(k, cc, 1852, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, account, hardened = true).let { (nk, nc) -> k = nk; cc = nc }

        (k to cc).also { accountKeyCache[cacheKey] = it }
    }

    private fun getByronAccountKey(): Pair<ByteArray, ByteArray> = synchronized(lock) {
        val cacheKey = AccountCacheKey(purpose = 44, account = 0)
        accountKeyCache[cacheKey]?.let { return@synchronized it }

        val (masterExt, masterCC) = getMasterKey()
        var k = masterExt; var cc = masterCC
        IcarusKeyDerivation.deriveChildKey(k, cc, 44, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = true).let { (nk, nc) -> k = nk; cc = nc }

        (k to cc).also { accountKeyCache[cacheKey] = it }
    }

    /**
     * Zero-fill and release all cached key material.
     * Call when the wallet is locked or the manager is no longer needed.
     * After calling, subsequent key derivations will recompute from scratch.
     */
    fun clearCachedKeys() = synchronized(lock) {
        masterKeyCache?.let { (ext, cc) -> ext.fill(0); cc.fill(0) }
        masterKeyCache = null

        accountKeyCache.values.forEach { (ext, cc) -> ext.fill(0); cc.fill(0) }
        accountKeyCache.clear()

        shelleyPaymentKeyCache.values.forEach { (pub, cc, ext) -> pub.fill(0); cc.fill(0); ext.fill(0) }
        shelleyPaymentKeyCache.clear()

        shelleyStakingKeyCache.values.forEach { (pub, cc, ext) -> pub.fill(0); cc.fill(0); ext.fill(0) }
        shelleyStakingKeyCache.clear()

        byronKeyCache.values.forEach { (pub, cc, ext) -> pub.fill(0); cc.fill(0); ext.fill(0) }
        byronKeyCache.clear()
    }

    // ── Key derivation — Icarus V2 (ed25519-bip32) for both Shelley and Byron ──

    // Shelley payment key: m/1852'/1815'/account'/0/index
    // Per CIP-1852: purpose, coin_type, account = hardened; role, index = SOFT
    // Returns Triple(pubKey32, chainCode32, extKey64)
    private fun deriveShelleyPaymentKey(account: Int, index: Int): Triple<ByteArray, ByteArray, ByteArray> {
        val cacheKey = account to index
        synchronized(lock) {
            shelleyPaymentKeyCache[cacheKey]?.let { return@deriveShelleyPaymentKey it }
        }

        val (k0, cc0) = getShelleyAccountKey(account)
        var k = k0; var cc = cc0
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
        val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

        synchronized(lock) {
            shelleyPaymentKeyCache[cacheKey] = result
        }
        return result
    }

    // Shelley staking key: m/1852'/1815'/account'/2/0
    // Returns Triple(pubKey32, chainCode32, extKey64)
    private fun deriveShelleyStakingKey(account: Int): Triple<ByteArray, ByteArray, ByteArray> {
        synchronized(lock) {
            shelleyStakingKeyCache[account]?.let { return@deriveShelleyStakingKey it }
        }

        val (k0, cc0) = getShelleyAccountKey(account)
        var k = k0; var cc = cc0
        IcarusKeyDerivation.deriveChildKey(k, cc, 2, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

        synchronized(lock) {
            shelleyStakingKeyCache[account] = result
        }
        return result
    }

    // Byron payment key: m/44'/1815'/0'/0/index
    private fun deriveByronKey(index: Int): Triple<ByteArray, ByteArray, ByteArray> {
        synchronized(lock) {
            byronKeyCache[index]?.let { return@deriveByronKey it }
        }

        val (k0, cc0) = getByronAccountKey()
        var k = k0; var cc = cc0
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }
        val result = Triple(IcarusKeyDerivation.publicKeyFromExtended(k), cc, k)

        synchronized(lock) {
            byronKeyCache[index] = result
        }
        return result
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
        // Formula: max(1 ADA, (160 + outputSize) * coinsPerUtxoByte)
        val policyIdBytes = hexToBytes(policyId)
        val assetNameBytes = hexToBytes(assetName)
        val toAddressBytes = addressToBytes(toAddress)
        val fromAddressBytes = addressToBytes(fromAddress)
        val dummyTokenOutput = CardanoTransactionOutput(
            addressBytes = toAddressBytes,
            lovelace = 2_000_000L, // Use realistic lovelace value for accurate CBOR size estimation
            multiAssets = mapOf(policyIdBytes to mapOf(assetNameBytes to amount))
        )
        val minAda = CardanoMinUtxo.calculateMinAda(dummyTokenOutput, COINS_PER_UTXO_BYTE)

        // Phase 1: Initial UTXO selection with conservative estimate (target token change only)
        val dummyChangeOutput = CardanoTransactionOutput(
            addressBytes = fromAddressBytes,
            lovelace = 2_000_000L,
            multiAssets = mapOf(policyIdBytes to mapOf(assetNameBytes to 1L))
        )
        val initialEstMinAdaForChange = CardanoMinUtxo.calculateMinAda(dummyChangeOutput, COINS_PER_UTXO_BYTE)
        var requiredAda = fee + minAda + initialEstMinAdaForChange

        var selectedUtxos = CardanoUtxoSelector.selectUtxos(
            utxos, policyId, assetName, amount, requiredAda
        )

        // Phase 2: Compute actual change tokens from selected UTXOs, recalculate if needed.
        // Uses String keys to avoid ByteArray reference equality issues.
        fun buildChangeTokensMap(selected: List<CardanoUtxo>): MutableMap<String, MutableMap<String, Long>> {
            val map = mutableMapOf<String, MutableMap<String, Long>>()
            for (utxo in selected) {
                for (token in utxo.nativeTokens) {
                    val assetMap = map.getOrPut(token.policyId) { mutableMapOf() }
                    assetMap[token.assetName] = (assetMap[token.assetName] ?: 0L) + token.amount
                }
            }
            // Subtract the tokens being sent to the recipient
            val targetAssetMap = map[policyId]
            if (targetAssetMap != null) {
                val remaining = (targetAssetMap[assetName] ?: 0L) - amount
                if (remaining > 0) {
                    targetAssetMap[assetName] = remaining
                } else {
                    targetAssetMap.remove(assetName)
                    if (targetAssetMap.isEmpty()) map.remove(policyId)
                }
            }
            return map
        }

        fun changeMapToBytes(map: Map<String, Map<String, Long>>): Map<ByteArray, Map<ByteArray, Long>> =
            map.map { (pid, assets) ->
                hexToBytes(pid) to assets.map { (name, amt) -> hexToBytes(name) to amt }.toMap()
            }.toMap()

        var changeTokensMap = buildChangeTokensMap(selectedUtxos)

        // If the actual change has more tokens than estimated, the change output is larger
        // and needs more minAda. Re-select UTXOs with corrected requirement.
        if (changeTokensMap.isNotEmpty()) {
            val actualChangeAssets = changeMapToBytes(changeTokensMap)
            val actualChangeOutput = CardanoTransactionOutput(
                addressBytes = fromAddressBytes,
                lovelace = 2_000_000L,
                multiAssets = actualChangeAssets
            )
            val actualMinAdaForChange = CardanoMinUtxo.calculateMinAda(actualChangeOutput, COINS_PER_UTXO_BYTE)
            val correctedRequiredAda = fee + minAda + actualMinAdaForChange

            if (correctedRequiredAda > requiredAda) {
                // Need more ADA — re-select UTXOs
                requiredAda = correctedRequiredAda
                selectedUtxos = CardanoUtxoSelector.selectUtxos(
                    utxos, policyId, assetName, amount, requiredAda
                )
                changeTokensMap = buildChangeTokensMap(selectedUtxos)
            }
        }

        // Phase 3: Build transaction
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
        val changeLovelace = totalInputLovelace - fee - minAda
        val hasChangeTokens = changeTokensMap.isNotEmpty()

        if (hasChangeTokens) {
            val changeAssets = changeMapToBytes(changeTokensMap)
            val changeOutput = CardanoTransactionOutput(
                addressBytes = fromAddressBytes,
                lovelace = changeLovelace,
                multiAssets = changeAssets
            )
            val minAdaForChange = CardanoMinUtxo.calculateMinAda(changeOutput, COINS_PER_UTXO_BYTE)
            if (changeLovelace < minAdaForChange) {
                throw CardanoError.InsufficientAda(
                    available = changeLovelace,
                    required = minAdaForChange
                )
            }
            builder.addMultiAssetOutput(fromAddressBytes, changeLovelace, changeAssets)
        } else if (changeLovelace >= CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
            // ADA-only change: only add output if above minimum UTXO (absorb dust into effective fee)
            builder.addOutput(fromAddressBytes, changeLovelace)
        }

        builder.setFee(fee)
        builder.setTtl(ttl)

        // Cardano ledger rule: sum(inputs.lovelace) = sum(outputs.lovelace) + fee
        val totalOutputLovelace = minAda +
            (if (hasChangeTokens || changeLovelace >= CardanoTransactionBuilder.MIN_UTXO_LOVELACE) changeLovelace else 0L)
        val consumed = totalOutputLovelace + fee
        check(totalInputLovelace >= consumed) {
            "Value not conserved: inputs=$totalInputLovelace, outputs+fee=$consumed"
        }

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

        // Fetch UTXOs — parse native tokens to preserve them in change output
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

        // UTXO selection: prefer UTXOs without native tokens to avoid multi-asset change.
        // Sort: token-free UTXOs first (by lovelace desc), then token-bearing (by lovelace desc).
        val serviceFeeTotal = if (hasServiceFee) serviceFeeLovelace else 0L
        val requiredTotal = amount + fee + serviceFeeTotal
        val sorted = utxos.sortedWith(
            compareBy<CardanoUtxo> { it.nativeTokens.isNotEmpty() }
                .thenByDescending { it.lovelace }
        )
        val selected = mutableListOf<CardanoUtxo>()
        var collected = 0L
        for (utxo in sorted) {
            if (collected >= requiredTotal) break
            selected.add(utxo)
            collected += utxo.lovelace
        }

        // Gather native tokens from selected UTXOs that must be returned via change
        val changeTokensMap = mutableMapOf<String, MutableMap<String, Long>>()
        for (utxo in selected) {
            for (token in utxo.nativeTokens) {
                val assetMap = changeTokensMap.getOrPut(token.policyId) { mutableMapOf() }
                assetMap[token.assetName] = (assetMap[token.assetName] ?: 0L) + token.amount
            }
        }
        val hasChangeTokens = changeTokensMap.isNotEmpty()

        // If change carries native tokens, we need extra ADA for the multi-asset change output minUTxO
        val fromAddressBytes = addressToBytes(fromAddress)
        var minAdaForChange = 0L
        if (hasChangeTokens) {
            val changeAssets = changeTokensMap.map { (pid, assets) ->
                hexToBytes(pid) to assets.map { (name, amt) -> hexToBytes(name) to amt }.toMap()
            }.toMap()
            val dummyChangeOutput = CardanoTransactionOutput(
                addressBytes = fromAddressBytes,
                lovelace = 2_000_000L,
                multiAssets = changeAssets
            )
            minAdaForChange = CardanoMinUtxo.calculateMinAda(dummyChangeOutput, COINS_PER_UTXO_BYTE)
        }

        // Ensure we have enough ADA including minAda for multi-asset change
        val requiredWithChange = requiredTotal + minAdaForChange
        if (collected < requiredWithChange) {
            // Try to add more UTXOs
            val remaining = sorted.filter { it !in selected }.sortedByDescending { it.lovelace }
            for (utxo in remaining) {
                if (collected >= requiredWithChange) break
                selected.add(utxo)
                collected += utxo.lovelace
                // Re-gather tokens from newly added UTXOs
                for (token in utxo.nativeTokens) {
                    val assetMap = changeTokensMap.getOrPut(token.policyId) { mutableMapOf() }
                    assetMap[token.assetName] = (assetMap[token.assetName] ?: 0L) + token.amount
                }
            }
        }

        if (collected < requiredTotal) {
            throw CardanoError.InsufficientAda(
                available = collected,
                required = requiredTotal
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
        val rawChange = collected - amount - fee - serviceFeeTotal
        val hasTokenChange = changeTokensMap.isNotEmpty()

        if (hasTokenChange) {
            // Multi-asset change: must include all native tokens from selected UTXOs
            val changeAssets = changeTokensMap.map { (pid, assets) ->
                hexToBytes(pid) to assets.map { (name, amt) -> hexToBytes(name) to amt }.toMap()
            }.toMap()
            val changeOutput = CardanoTransactionOutput(
                addressBytes = fromAddressBytes,
                lovelace = rawChange,
                multiAssets = changeAssets
            )
            val actualMinAdaForChange = CardanoMinUtxo.calculateMinAda(changeOutput, COINS_PER_UTXO_BYTE)
            if (rawChange < actualMinAdaForChange) {
                throw CardanoError.InsufficientAda(
                    available = rawChange,
                    required = actualMinAdaForChange
                )
            }
            builder.addMultiAssetOutput(fromAddressBytes, rawChange, changeAssets)
            builder.setFee(fee)
        } else {
            // ADA-only change: dust (<1 ADA) is absorbed into fee
            val effectiveFee: Long
            val actualChange: Long
            if (rawChange > 0L && rawChange < CardanoTransactionBuilder.MIN_UTXO_LOVELACE) {
                effectiveFee = fee + rawChange
                actualChange = 0L
            } else {
                effectiveFee = fee
                actualChange = rawChange
            }
            if (actualChange > 0L) {
                builder.addOutput(fromAddressBytes, actualChange)
            }
            builder.setFee(effectiveFee)
        }

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
