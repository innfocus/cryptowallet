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
import kotlinx.serialization.json.Json

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
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw CardanoError.ApiError(
                statusCode = response.status.value,
                message = body.ifEmpty { response.status.description }
            )
        }
        return response.body<T>()
    }

    /**
     * Get UTXOs for a list of addresses.
     */
    suspend fun getUtxos(addresses: List<String>): List<CardanoApiUtxo> {
        logger.d { "getUtxos: ${addresses.size} addresses" }
        val allUtxos = mutableListOf<CardanoApiUtxo>()
        for (address in addresses) {
            val utxos = safeRequest<List<CardanoApiUtxo>> {
                get("$baseUrl/addresses/$address/utxos") { applyAuth() }
            }
            allUtxos.addAll(utxos)
        }
        return allUtxos
    }

    /**
     * Get transaction history for a list of addresses.
     */
    suspend fun getTransactionHistory(addresses: List<String>): List<CardanoTransactionInfo> {
        logger.d { "getTransactionHistory: ${addresses.size} addresses" }
        val allTxs = mutableListOf<CardanoTransactionInfo>()
        for (address in addresses) {
            val txs = safeRequest<List<CardanoTransactionInfo>> {
                get("$baseUrl/addresses/$address/transactions") { applyAuth() }
            }
            allTxs.addAll(txs)
        }
        return allTxs
    }

    /**
     * Submit a signed transaction (CBOR bytes) to the network.
     * @return Transaction hash
     */
    suspend fun submitTransaction(signedTxCbor: ByteArray): String {
        logger.d { "submitTransaction: ${signedTxCbor.size} bytes" }
        val response: io.ktor.client.statement.HttpResponse
        try {
            response = client.post("$baseUrl/tx/submit") {
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
     */
    suspend fun getCurrentBlock(): CardanoBlockInfo {
        logger.d { "getCurrentBlock" }
        return safeRequest<CardanoBlockInfo> {
            get("$baseUrl/blocks/latest") { applyAuth() }
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
