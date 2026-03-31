package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResponse
import com.lybia.cryptowallet.models.ripple.RippleAccountTxResponse
import com.lybia.cryptowallet.models.ripple.RippleRpcParam
import com.lybia.cryptowallet.models.ripple.RippleRpcRequest
import com.lybia.cryptowallet.models.ripple.RippleSubmitResponse
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers

/**
 * Ripple JSON-RPC API service using Ktor.
 * Replaces androidMain Gxrp (Retrofit + Gson).
 */
class RippleApiService(
    private val client: io.ktor.client.HttpClient = HttpClientService.INSTANCE.client
) {
    companion object {
        val INSTANCE: RippleApiService = RippleApiService()

        private const val MAINNET_URL = "https://s1.ripple.com:51234"
        private const val TESTNET_URL = "https://s.altnet.rippletest.net:51234"
    }

    private fun getRpcUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> MAINNET_URL
            Network.TESTNET -> TESTNET_URL
        }
    }

    /**
     * Get account info (balance, sequence) via account_info JSON-RPC.
     * Balance is returned in drops (1 XRP = 1,000,000 drops).
     */
    suspend fun getAccountInfo(address: String): RippleAccountInfoResponse? {
        return try {
            val request = RippleRpcRequest(
                method = "account_info",
                params = listOf(
                    RippleRpcParam(
                        account = address,
                        ledgerIndex = "current"
                    )
                )
            )
            val response = client.post(getRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleAccountInfoResponse>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get XRP balance for an address in drops (as String).
     */
    suspend fun getBalance(address: String): String? {
        val info = getAccountInfo(address)
        return info?.result?.accountData?.balance
    }

    /**
     * Get transaction history via account_tx JSON-RPC.
     */
    suspend fun getTransactionHistory(
        address: String,
        limit: Int = 100
    ): RippleAccountTxResponse? {
        return try {
            val request = RippleRpcRequest(
                method = "account_tx",
                params = listOf(
                    RippleRpcParam(
                        account = address,
                        ledgerIndex = "validated",
                        limit = limit,
                        forward = false
                    )
                )
            )
            val response = client.post(getRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleAccountTxResponse>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Submit a signed transaction via submit JSON-RPC.
     */
    suspend fun submitTransaction(txBlob: String): RippleSubmitResponse? {
        return try {
            val request = RippleRpcRequest(
                method = "submit",
                params = listOf(
                    RippleRpcParam(txBlob = txBlob)
                )
            )
            val response = client.post(getRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleSubmitResponse>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
