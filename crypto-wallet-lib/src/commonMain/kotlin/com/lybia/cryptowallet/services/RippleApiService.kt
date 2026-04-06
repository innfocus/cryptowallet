package com.lybia.cryptowallet.services

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResponse
import com.lybia.cryptowallet.models.ripple.RippleAccountTxResponse
import com.lybia.cryptowallet.models.ripple.RippleRpcParam
import com.lybia.cryptowallet.models.ripple.RippleMarker
import com.lybia.cryptowallet.models.ripple.RippleRpcRequest
import com.lybia.cryptowallet.models.ripple.RippleSubmitResponse
import com.lybia.cryptowallet.models.ripple.RippleFeeResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
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
    private val logger = Logger.withTag("RippleApiService")

    companion object {
        val INSTANCE: RippleApiService = RippleApiService()

        private const val MAINNET_URL = "https://s1.ripple.com:51234"
        private const val TESTNET_URL = "https://s.altnet.rippletest.net:51234"

        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 30_000L
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
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleAccountInfoResponse>()
            } else null
        } catch (e: Exception) {
            logger.e(e) { "account_info RPC failed for $address" }
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
        return getTransactionHistoryWithMarker(address, limit, marker = null)
    }

    /**
     * Get transaction history with pagination marker support.
     * @param marker Pagination marker from previous response (null for first page)
     * @return Response containing transactions and optional next marker
     */
    suspend fun getTransactionHistoryWithMarker(
        address: String,
        limit: Int = 100,
        marker: RippleMarker? = null
    ): RippleAccountTxResponse? {
        return try {
            val request = RippleRpcRequest(
                method = "account_tx",
                params = listOf(
                    RippleRpcParam(
                        account = address,
                        ledgerIndex = "validated",
                        limit = limit,
                        forward = false,
                        marker = marker
                    )
                )
            )
            val response = client.post(getRpcUrl()) {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleAccountTxResponse>()
            } else null
        } catch (e: Exception) {
            logger.e(e) { "account_tx RPC failed" }
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
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleSubmitResponse>()
            } else null
        } catch (e: Exception) {
            logger.e(e) { "submit RPC failed" }
            null
        }
    }

    /**
     * Get current network fee via `fee` JSON-RPC method.
     * Returns fee drops info including base_fee, median_fee, minimum_fee, open_ledger_fee.
     */
    suspend fun getFee(): RippleFeeResponse? {
        return try {
            val request = RippleRpcRequest(
                method = "fee",
                params = emptyList()
            )
            val response = client.post(getRpcUrl()) {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.value in 200..299) {
                response.body<RippleFeeResponse>()
            } else null
        } catch (e: Exception) {
            logger.e(e) { "fee RPC failed" }
            null
        }
    }
}
