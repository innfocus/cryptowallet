package com.lybia.cryptowallet.services

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.models.cardano.*
import com.lybia.cryptowallet.wallets.cardano.CardanoError
import com.lybia.cryptowallet.wallets.cardano.CardanoNativeToken
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

/**
 * Supported Cardano API backend providers.
 */
enum class CardanoApiProvider {
    BLOCKFROST,
    KOIOS,
    CUSTOM
}

/**
 * Ktor-based Cardano API service.
 *
 * @param baseUrl Base URL for the API (e.g. "https://cardano-mainnet.blockfrost.io/api/v0")
 * @param apiKey Optional API key (used as "project_id" header for Blockfrost)
 * @param provider The API backend provider (default: BLOCKFROST)
 * @param client Optional HttpClient for dependency injection (testing)
 */
class CardanoApiService(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val provider: CardanoApiProvider = CardanoApiProvider.BLOCKFROST,
    private val client: HttpClient = HttpClientService.INSTANCE.client
) {
    private val logger = Logger.withTag("CardanoApiService")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a CardanoApiService with automatic provider detection.
     * If apiKey is provided, uses Blockfrost. Otherwise, falls back to Koios (free, no key).
     */
    companion object {
        fun createWithFallback(
            blockfrostUrl: String,
            koiosUrl: String,
            apiKey: String? = null,
            client: HttpClient = HttpClientService.INSTANCE.client
        ): CardanoApiService {
            return if (!apiKey.isNullOrEmpty()) {
                CardanoApiService(
                    baseUrl = blockfrostUrl,
                    apiKey = apiKey,
                    provider = CardanoApiProvider.BLOCKFROST,
                    client = client
                )
            } else {
                CardanoApiService(
                    baseUrl = koiosUrl,
                    apiKey = null,
                    provider = CardanoApiProvider.KOIOS,
                    client = client
                )
            }
        }
    }

    /**
     * Apply common headers (e.g. Blockfrost project_id).
     */
    private fun HttpRequestBuilder.applyAuth() {
        when (provider) {
            CardanoApiProvider.BLOCKFROST -> apiKey?.let { header("project_id", it) }
            CardanoApiProvider.KOIOS -> apiKey?.let { header("Authorization", "Bearer $it") }
            CardanoApiProvider.CUSTOM -> apiKey?.let { header("Authorization", "Bearer $it") }
        }
    }

    /**
     * Execute an HTTP request and handle errors uniformly.
     */
    private suspend inline fun <reified T> safeRequest(
        crossinline block: suspend HttpClient.() -> HttpResponse
    ): T {
        val response: HttpResponse
        try {
            response = client.block()
        } catch (e: Exception) {
            logger.e(e) { "Connection error" }
            throw CardanoError.ApiError(statusCode = null, message = "Connection error: ${e.message ?: "unknown"}")
        }
        logger.d { "Request URL: ${response.call.request.url}" }
        logger.d { "Response status: ${response.status}" }
        logger.d { "Response Content-Type: ${response.headers["Content-Type"]}" }
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw CardanoError.ApiError(
                statusCode = response.status.value,
                message = body.ifEmpty { response.status.description }
            )
        }
        val rawJson = response.bodyAsText()
        logger.d { "Response body length: ${rawJson.length}, first 200 chars: ${rawJson.take(200)}" }
        return json.decodeFromString<T>(rawJson)
    }

    /**
     * Get UTXOs for a list of addresses.
     * Blockfrost: GET /addresses/{address}/utxos
     * Koios: POST /address_utxos with body {"_addresses": [...]}
     */
    suspend fun getUtxos(addresses: List<String>): List<CardanoApiUtxo> {
        logger.d { "getUtxos: ${addresses.size} addresses (provider=$provider)" }
        return when (provider) {
            CardanoApiProvider.KOIOS -> {
                val body = buildJsonObject {
                    putJsonArray("_addresses") { addresses.forEach { add(it) } }
                }
                val koiosUtxos = safeRequest<List<KoiosUtxo>> {
                    post("$baseUrl/address_utxos") {
                        applyAuth()
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }
                }
                // Convert Koios format to Blockfrost-compatible format
                koiosUtxos.map { ku ->
                    CardanoApiUtxo(
                        txHash = ku.txHash,
                        txIndex = ku.txIndex,
                        amount = ku.value.let { value ->
                            val amounts = mutableListOf(CardanoAmount("lovelace", value))
                            ku.assetList?.forEach { asset ->
                                amounts.add(CardanoAmount(
                                    unit = asset.policyId + asset.assetName,
                                    quantity = asset.quantity
                                ))
                            }
                            amounts
                        }
                    )
                }
            }
            else -> {
                val allUtxos = mutableListOf<CardanoApiUtxo>()
                for (address in addresses) {
                    val utxos = safeRequest<List<CardanoApiUtxo>> {
                        get("$baseUrl/addresses/$address/utxos") { applyAuth() }
                    }
                    allUtxos.addAll(utxos)
                }
                allUtxos
            }
        }
    }

    /**
     * Get transaction history for a list of addresses.
     * Blockfrost: GET /addresses/{address}/transactions
     * Koios: POST /address_txs with body {"_addresses": [...]}
     */
    suspend fun getTransactionHistory(addresses: List<String>): List<CardanoTransactionInfo> {
        logger.d { "getTransactionHistory: ${addresses.size} addresses (provider=$provider)" }
        return when (provider) {
            CardanoApiProvider.KOIOS -> {
                val body = buildJsonObject {
                    putJsonArray("_addresses") { addresses.forEach { add(it) } }
                }
                val koiosTxs = safeRequest<List<KoiosTxInfo>> {
                    post("$baseUrl/address_txs") {
                        applyAuth()
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }
                }
                koiosTxs.map { kt ->
                    CardanoTransactionInfo(
                        txHash = kt.txHash,
                        blockHeight = kt.blockHeight,
                        blockTime = kt.blockTime,
                        fees = kt.fee ?: "0",
                        inputs = emptyList(),
                        outputs = emptyList()
                    )
                }
            }
            else -> {
                val allTxs = mutableListOf<CardanoTransactionInfo>()
                for (address in addresses) {
                    val txHashes = safeRequest<List<CardanoTransactionHash>> {
                        get("$baseUrl/addresses/$address/transactions") {
                            applyAuth()
                        }
                    }

                    val txs = fetchTransactionsSafely(txHashes)
                    allTxs.addAll(txs)
                }
                allTxs
            }
        }
    }

    suspend fun fetchTransactionsSafely(txHashes: List<CardanoTransactionHash>): List<CardanoTransactionInfo> {
        val allTxs = mutableListOf<CardanoTransactionInfo>()

        val chunks = txHashes.chunked(5)

        for (chunk in chunks) {
            val chunkResult = coroutineScope {
                chunk.map { txRef ->
                    async {
                        val detailDeferred = async {
                            safeRequest<BlockfrostTxDetail> { get("$baseUrl/txs/${txRef.txHash}") { applyAuth() } }
                        }
                        val utxosDeferred = async {
                            safeRequest<BlockfrostTxUtxos> { get("$baseUrl/txs/${txRef.txHash}/utxos") { applyAuth() } }
                        }

                        val txDetail = detailDeferred.await()
                        val txUtxos = utxosDeferred.await()

                        CardanoTransactionInfo(
                            txHash = txDetail.hash,
                            blockHeight = txDetail.blockHeight,
                            blockTime = txDetail.blockTime,
                            fees = txDetail.fees,
                            inputs = txUtxos.inputs,
                            outputs = txUtxos.outputs
                        )
                    }
                }.awaitAll()
            }
            allTxs.addAll(chunkResult)
        }

        return allTxs
    }

    /**
     * Submit a signed transaction (CBOR bytes) to the network.
     * Blockfrost: POST /tx/submit (application/cbor)
     * Koios: POST /submittx (application/cbor)
     * @return Transaction hash
     */
    suspend fun submitTransaction(signedTxCbor: ByteArray): String {
        logger.d { "submitTransaction: ${signedTxCbor.size} bytes (provider=$provider)" }
        val submitPath = when (provider) {
            CardanoApiProvider.KOIOS -> "$baseUrl/submittx"
            else -> "$baseUrl/tx/submit"
        }
        val response: HttpResponse
        try {
            response = client.post(submitPath) {
                applyAuth()
                contentType(ContentType("application", "cbor"))
                setBody(signedTxCbor)
            }
        } catch (e: Exception) {
            logger.e(e) { "Connection error" }
            throw CardanoError.ApiError(statusCode = null, message = "Connection error: ${e.message ?: "unknown"}")
        }
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw CardanoError.ApiError(
                statusCode = response.status.value,
                message = body.ifEmpty { response.status.description }
            )
        }
        return response.bodyAsText().trim().removeSurrounding("\"")
    }

    /**
     * Get the current (latest) block info.
     * Blockfrost: GET /blocks/latest
     * Koios: GET /tip
     */
    suspend fun getCurrentBlock(): CardanoBlockInfo {
        logger.d { "getCurrentBlock (provider=$provider)" }
        return when (provider) {
            CardanoApiProvider.KOIOS -> {
                val tips = safeRequest<List<KoiosTip>> {
                    get("$baseUrl/tip") { applyAuth() }
                }
                val tip = tips.firstOrNull() ?: throw CardanoError.ApiError(null, "No tip data")
                CardanoBlockInfo(
                    epoch = tip.epochNo,
                    slot = tip.absSlot,
                    hash = tip.hash,
                    height = tip.blockNo
                )
            }
            else -> {
                safeRequest<CardanoBlockInfo> {
                    get("$baseUrl/blocks/latest") { applyAuth() }
                }
            }
        }
    }

    /**
     * Get current protocol parameters.
     */
    suspend fun getProtocolParameters(): CardanoProtocolParams {
        logger.d { "getProtocolParameters" }
        return safeRequest<CardanoProtocolParams> {
            get("$baseUrl/epochs/latest/parameters") { applyAuth() }
        }
    }

    /**
     * Get metadata/info for a specific native asset.
     */
    suspend fun getAssetInfo(policyId: String, assetName: String): CardanoAssetInfo? {
        logger.d { "getAssetInfo: $policyId$assetName" }
        return try {
            safeRequest<CardanoAssetInfo> {
                get("$baseUrl/assets/$policyId$assetName") { applyAuth() }
            }
        } catch (e: CardanoError.ApiError) {
            if (e.statusCode == 404) null else throw e
        }
    }

    /**
     * Get account info for a staking address (delegation status, rewards, etc.).
     * Uses Blockfrost `/accounts/{staking_address}` endpoint.
     *
     * @param stakingAddress Bech32-encoded staking (reward) address
     * @return Account info including delegation status and rewards
     */
    suspend fun getAccountInfo(stakingAddress: String): CardanoAccountInfo {
        logger.d { "getAccountInfo: $stakingAddress" }
        return safeRequest<CardanoAccountInfo> {
            get("$baseUrl/accounts/$stakingAddress") { applyAuth() }
        }
    }

    /**
     * Get all native tokens held by an address.
     */
    suspend fun getAddressAssets(address: String): List<CardanoNativeToken> {
        logger.d { "getAddressAssets: $address" }
        val utxos = getUtxos(listOf(address))
        val tokenMap = mutableMapOf<Pair<String, String>, Long>()
        for (utxo in utxos) {
            for (amount in utxo.amount) {
                if (amount.unit != "lovelace") {
                    // Blockfrost unit = policyId (56 chars) + assetName hex
                    val policyId = amount.unit.take(56)
                    val assetHex = amount.unit.drop(56)
                    val key = policyId to assetHex
                    tokenMap[key] = (tokenMap[key] ?: 0L) + amount.quantity.toLongOrNull().let { it ?: 0L }
                }
            }
        }
        return tokenMap.map { (key, qty) ->
            CardanoNativeToken(policyId = key.first, assetName = key.second, amount = qty)
        }
    }
}

// ── Koios response models ───────────────────────────────────────────────

@Serializable
internal data class KoiosUtxo(
    @SerialName("tx_hash") val txHash: String,
    @SerialName("tx_index") val txIndex: Int,
    val value: String,
    @SerialName("asset_list") val assetList: List<KoiosAsset>? = null
)

@Serializable
internal data class KoiosAsset(
    @SerialName("policy_id") val policyId: String,
    @SerialName("asset_name") val assetName: String,
    val quantity: String
)

@Serializable
internal data class KoiosTxInfo(
    @SerialName("tx_hash") val txHash: String,
    @SerialName("block_height") val blockHeight: Long,
    @SerialName("block_time") val blockTime: Long,
    val fee: String? = null
)

@Serializable
internal data class KoiosTip(
    @SerialName("epoch_no") val epochNo: Int,
    @SerialName("abs_slot") val absSlot: Long,
    val hash: String,
    @SerialName("block_no") val blockNo: Long
)
