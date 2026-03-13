package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.ton.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class TonApiService {
    companion object {
        val INSTANCE = TonApiService()
    }

    suspend fun getBalance(coin: CoinNetwork, address: String): String? {
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            contentType(ContentType.Application.Json)
            setBody(
                TonRpcRequest(
                    method = "getAddressInformation",
                    params = mapOf("address" to address)
                )
            )
        }

        if (response.status.value in 200..299) {
            val body = response.body<TonAddressInformationResponse>()
            if (body.ok) {
                return body.result?.balance
            }
        }
        return null
    }

    suspend fun getTransactions(coin: CoinNetwork, address: String, limit: Int = 10): List<TonTransaction>? {
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            contentType(ContentType.Application.Json)
            setBody(
                TonRpcRequest(
                    method = "getTransactions",
                    params = mapOf("address" to address, "limit" to limit)
                )
            )
        }

        if (response.status.value in 200..299) {
            val body = response.body<TonTransactionsResponse>()
            if (body.ok) {
                return body.result
            }
        }
        return null
    }

    suspend fun estimateFee(coin: CoinNetwork, address: String, body: String): Long? {
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            contentType(ContentType.Application.Json)
            setBody(
                TonRpcRequest(
                    method = "estimateFee",
                    params = mapOf(
                        "address" to address,
                        "body" to body,
                        "ignore_chksig" to true
                    )
                )
            )
        }

        if (response.status.value in 200..299) {
            val res = response.body<TonFeeResponse>()
            if (res.ok && res.result != null) {
                val fees = res.result.sourceFees
                return fees.inFwdFee + fees.storageFee + fees.gasFee + fees.fwdFee
            }
        }
        return null
    }

    suspend fun runGetMethod(coin: CoinNetwork, address: String, method: String, stack: List<List<String>> = emptyList()): TonRunGetMethodResponse? {
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            contentType(ContentType.Application.Json)
            setBody(
                TonRpcRequest(
                    method = "runGetMethod",
                    params = mapOf(
                        "address" to address,
                        "method" to method,
                        "stack" to stack
                    )
                )
            )
        }

        return if (response.status.value in 200..299) {
            response.body<TonRunGetMethodResponse>()
        } else null
    }

    suspend fun getJettonMetadataFromUrl(url: String): JettonMetadata? {
        return try {
            val response = HttpClientService.INSTANCE.client.get(url)
            if (response.status.value in 200..299) {
                response.body<JettonMetadata>()
            } else null
        } catch (e: Exception) {
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
                    if (value.startsWith("0x")) value.substring(2).toInt(16) else value.toInt()
                } catch (e: Exception) { 0 }
            }
        }
        return 0
    }

    suspend fun sendBoc(coin: CoinNetwork, bocBase64: String): String? {
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            contentType(ContentType.Application.Json)
            setBody(
                TonRpcRequest(
                    method = "sendBoc",
                    params = mapOf("boc" to bocBase64)
                )
            )
        }

        if (response.status.value in 200..299) {
            val body = response.body<TonAddressInformationResponse>()
            if (body.ok) {
                return "success"
            }
        }
        return null
    }
}
