package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.ton.*
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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
            val body = response.body<TonTransactionsResponse>() // Reusing model as structure is similar
            if (body.ok) {
                // In TON Center, sendBoc usually returns success and tx hash might be available later
                // but let's return a success indicator for now
                return "success"
            }
        }
        return null
    }
}
