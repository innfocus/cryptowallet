package com.lybia.cryptowallet.services

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.models.ton.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class TonApiService {
    private val logger = Logger.withTag("TonApiService")

    companion object {
        val INSTANCE = TonApiService()
    }

    suspend fun getBalance(coin: CoinNetwork, address: String): String? {
        logger.d { "getBalance: address=$address" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "getAddressInformation",
                        params = buildJsonObject {
                            put("address", address)
                        }
                    )
                )
            }

            if (response.status.value in 200..299) {
                val body = response.body<TonAddressInformationResponse>()
                if (body.ok) {
                    return body.result?.balance.also { logger.v { "Balance for $address: $it" } }
                } else {
                    logger.e { "getBalance failed: ${body.error ?: "Unknown error"}" }
                }
            } else {
                logger.e { "getBalance HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "Error in getBalance" }
            null
        }
    }

    suspend fun getTransactions(coin: CoinNetwork, address: String, limit: Int = 10): List<TonTransaction>? {
        logger.d { "getTransactions: address=$address, limit=$limit" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "getTransactions",
                        params = buildJsonObject {
                            put("address", address)
                            put("limit", limit)
                        }
                    )
                )
            }

            if (response.status.value in 200..299) {
                val body = response.body<TonTransactionsResponse>()
                if (body.ok) {
                    return body.result.also { logger.v { "Found ${it?.size ?: 0} transactions" } }
                } else {
                    logger.e { "getTransactions failed: ${body.error ?: "Unknown error"}" }
                }
            } else {
                logger.e { "getTransactions HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "Error in getTransactions" }
            null
        }
    }

    suspend fun estimateFee(coin: CoinNetwork, address: String, body: String): Long? {
        logger.d { "estimateFee: address=$address" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "estimateFee",
                        params = buildJsonObject {
                            put("address", address)
                            put("body", body)
                            put("ignore_chksig", true)
                        }
                    )
                )
            }

            if (response.status.value in 200..299) {
                val res = response.body<TonFeeResponse>()
                if (res.ok && res.result != null) {
                    val fees = res.result.sourceFees
                    val totalFee = fees.inFwdFee + fees.storageFee + fees.gasFee + fees.fwdFee
                    logger.v { "Estimated fee: $totalFee" }
                    return totalFee
                } else {
                    logger.e { "estimateFee failed: ${res.error ?: "Unknown error"}" }
                }
            } else {
                logger.e { "estimateFee HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "Error in estimateFee" }
            null
        }
    }

    suspend fun runGetMethod(coin: CoinNetwork, address: String, method: String, stack: List<List<String>> = emptyList()): TonRunGetMethodResponse? {
        logger.d { "runGetMethod: address=$address, method=$method" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "runGetMethod",
                        params = buildJsonObject {
                            put("address", address)
                            put("method", method)
                            put("stack", Json.encodeToJsonElement(stack))
                        }
                    )
                )
            }

            if (response.status.value in 200..299) {
                response.body<TonRunGetMethodResponse>()
            } else {
                logger.e { "runGetMethod HTTP error: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Error in runGetMethod" }
            null
        }
    }

    suspend fun getJettonMetadataFromUrl(url: String): JettonMetadata? {
        logger.d { "getJettonMetadataFromUrl: $url" }
        return try {
            val response = HttpClientService.INSTANCE.client.get(url)
            if (response.status.value in 200..299) {
                response.body<JettonMetadata>()
            } else {
                logger.e { "getJettonMetadataFromUrl HTTP error: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Error in getJettonMetadataFromUrl" }
            null
        }
    }

    suspend fun getSeqno(coin: CoinNetwork, address: String): Int {
        val body = runGetMethod(coin, address, "seqno")
        if (body?.ok == true && body.result?.stack?.isNotEmpty() == true) {
            val element = body.result.stack[0]
            if (element.size >= 2) {
                val value = element[1].toString().removeSurrounding("\"")
                return try {
                    val seqno = if (value.startsWith("0x")) value.substring(2).toInt(16) else value.toInt()
                    logger.v { "seqno for $address: $seqno" }
                    seqno
                } catch (e: Exception) {
                    logger.e(e) { "Error parsing seqno value: $value" }
                    0
                }
            }
        } else if (body != null && !body.ok) {
            logger.e { "getSeqno failed: $body" }
        } else {
            logger.e { "getSeqno failed: Unknown error" }
        }
        return 0
    }

    suspend fun getNFTItems(coin: CoinNetwork, ownerAddress: String, limit: Int = 50): List<TonNFTItem>? {
        logger.d { "getNFTItems: owner=$ownerAddress, limit=$limit" }
        return try {
            val response = HttpClientService.INSTANCE.client.get("${coin.getToncenterV3Url()}/nfts") {
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                url {
                    parameters.append("owner_address", ownerAddress)
                    parameters.append("limit", limit.toString())
                }
            }
            if (response.status.value in 200..299) {
                response.body<TonV3NFTResponse>().nftItems.also { logger.v { "Found ${it.size} NFTs" } }
            } else {
                logger.e { "getNFTItems HTTP error: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Error in getNFTItems" }
            null
        }
    }

    suspend fun sendBoc(coin: CoinNetwork, bocBase64: String): String? {
        logger.d { "sendBoc called" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "sendBoc",
                        params = buildJsonObject {
                            put("boc", bocBase64)
                        }
                    )
                )
            }

            if (response.status.value in 200..299) {
                val body = response.body<TonAddressInformationResponse>()
                if (body.ok) {
                    logger.i { "sendBoc success" }
                    return "success"
                } else {
                    logger.e { "sendBoc failed: ${body.error ?: "Unknown error"}" }
                }
            } else {
                logger.e { "sendBoc HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "Error in sendBoc" }
            null
        }
    }
}

