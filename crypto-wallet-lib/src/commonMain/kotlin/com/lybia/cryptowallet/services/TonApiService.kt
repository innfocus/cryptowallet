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
import io.ktor.client.statement.bodyAsText
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

    suspend fun getTransactions(
        coin: CoinNetwork,
        address: String,
        limit: Int = 10,
        lt: String? = null,
        hash: String? = null
    ): List<TonTransaction>? {
        logger.d { "getTransactions: address=$address, limit=$limit, lt=$lt, hash=$hash" }
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
                            if (lt != null) put("lt", lt)
                            if (hash != null) put("hash", hash)
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

    /**
     * Estimate fee with full breakdown (source + destination fees).
     * @return TonFeeResult with sourceFees and destinationFees, or null on error.
     */
    suspend fun estimateFeeDetailed(coin: CoinNetwork, address: String, body: String): TonFeeResult? {
        logger.d { "estimateFeeDetailed: address=$address" }
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
                    logger.v { "estimateFeeDetailed: source=${res.result.sourceFees.total}, destinations=${res.result.destinationFees.size}" }
                    return res.result
                } else {
                    logger.e { "estimateFeeDetailed failed: ${res.error ?: "Unknown error"}" }
                }
            } else {
                logger.e { "estimateFeeDetailed HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "Error in estimateFeeDetailed" }
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

    suspend fun getSeqno(coin: CoinNetwork, address: String): Int? {
        val body = runGetMethod(coin, address, "seqno")
        val exitCode = body?.result?.exitCode

        // exitCode must be 0 for a successful get-method execution.
        // exitCode -13/-14 = account not deployed (seqno is 0).
        // Any other exitCode or ok=false = error.
        if (exitCode == -13 || exitCode == -14) {
            logger.i { "Wallet not deployed (exitCode=$exitCode), seqno = 0" }
            return 0
        }

        if (body?.ok == true && exitCode == 0 && body.result?.stack?.isNotEmpty() == true) {
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
        }

        if (body != null) {
            logger.e { "getSeqno failed: ok=${body.ok}, exitCode=$exitCode" }
        } else {
            logger.e { "getSeqno failed: no response (network or API issue)" }
        }
        return null
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

    /**
     * Broadcast BOC and return the message hash (for confirmation polling).
     * @return base64 message hash on success, null on failure.
     */
    suspend fun sendBocReturnHash(coin: CoinNetwork, bocBase64: String): String? {
        val url = coin.getInfuraRpcUrl()
        logger.d { "sendBocReturnHash → POST $url" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(url) {
                contentType(ContentType.Application.Json)
                Config.shared.apiKeyToncenter?.let { header("X-API-Key", it) }
                setBody(
                    TonRpcRequest(
                        method = "sendBocReturnHash",
                        params = buildJsonObject {
                            put("boc", bocBase64)
                        }
                    )
                )
            }
            val rawBody = response.bodyAsText()
            logger.d { "sendBocReturnHash ← HTTP ${response.status.value} | body: $rawBody" }

            if (response.status.value in 200..299) {
                val body = response.body<TonSendBocReturnHashResponse>()
                if (body.ok && body.result != null) {
                    logger.i { "sendBocReturnHash ✓ hash=${body.result.hash}" }
                    return body.result.hash
                } else {
                    logger.e { "sendBocReturnHash ✗ error: ${body.error}" }
                }
            } else {
                logger.e { "sendBocReturnHash ✗ HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "sendBocReturnHash ✗ exception: ${e.message}" }
            null
        }
    }

    suspend fun sendBoc(coin: CoinNetwork, bocBase64: String): String? {
        val url = coin.getInfuraRpcUrl()
        logger.d { "sendBoc → POST $url" }
        logger.d { "sendBoc → BOC length: ${bocBase64.length}" }
        return try {
            val response = HttpClientService.INSTANCE.client.post(url) {
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

            val rawBody = response.bodyAsText()
            logger.d { "sendBoc ← HTTP ${response.status.value} | body: $rawBody" }

            if (response.status.value in 200..299) {
                val body = response.body<TonGenericResponse>()
                if (body.ok) {
                    logger.i { "sendBoc ✓ broadcast successful" }
                    return "success"
                } else {
                    logger.e { "sendBoc ✗ API error: code=${body.code}, error=${body.error}" }
                    return body.error
                }
            } else {
                logger.e { "sendBoc ✗ HTTP error: ${response.status}" }
            }
            null
        } catch (e: Exception) {
            logger.e(e) { "sendBoc ✗ exception: ${e.message}" }
            null
        }
    }
}

