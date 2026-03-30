package com.lybia.cryptowallet.services

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.models.cardano.MidnightBalanceResponse
import com.lybia.cryptowallet.models.cardano.MidnightTransaction
import com.lybia.cryptowallet.wallets.midnight.MidnightError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Ktor-based Midnight Network API service.
 *
 * @param baseUrl Base URL for the Midnight API endpoint
 * @param apiKey Optional API key for authentication
 * @param client Optional HttpClient for dependency injection (testing)
 */
class MidnightApiService(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val client: HttpClient = HttpClientService.INSTANCE.client
) {
    private val logger = Logger.withTag("MidnightApiService")

    private val json = Json { ignoreUnknownKeys = true }

    private fun HttpRequestBuilder.applyAuth() {
        apiKey?.let { header("Authorization", "Bearer $it") }
    }

    /**
     * Execute an HTTP request and handle errors uniformly.
     */
    private suspend inline fun <reified T> safeRequest(
        endpoint: String,
        crossinline block: suspend HttpClient.() -> HttpResponse
    ): T {
        val response: HttpResponse
        try {
            response = client.block()
        } catch (e: Exception) {
            logger.e(e) { "Connection error to $endpoint" }
            throw MidnightError.ConnectionError(endpoint, e)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw MidnightError.TransactionRejected(
                body.ifEmpty { "HTTP ${response.status.value}: ${response.status.description}" }
            )
        }
        return response.body<T>()
    }

    /**
     * Get tDUST balance for an address.
     * @return tDUST balance in smallest unit
     */
    suspend fun getBalance(address: String): Long {
        logger.d { "getBalance: $address" }
        val endpoint = "$baseUrl/addresses/$address/balance"
        val balanceResponse = safeRequest<MidnightBalanceResponse>(endpoint) {
            get(endpoint) { applyAuth() }
        }
        return balanceResponse.balance
    }

    /**
     * Get transaction history for an address.
     */
    suspend fun getTransactionHistory(address: String): List<MidnightTransaction> {
        logger.d { "getTransactionHistory: $address" }
        val endpoint = "$baseUrl/addresses/$address/transactions"
        return safeRequest<List<MidnightTransaction>>(endpoint) {
            get(endpoint) { applyAuth() }
        }
    }

    /**
     * Submit a signed transaction to the Midnight network.
     * @return Transaction hash
     */
    suspend fun submitTransaction(signedTx: ByteArray): String {
        logger.d { "submitTransaction: ${signedTx.size} bytes" }
        val endpoint = "$baseUrl/tx/submit"
        val response: HttpResponse
        try {
            response = client.post(endpoint) {
                applyAuth()
                contentType(ContentType("application", "cbor"))
                setBody(signedTx)
            }
        } catch (e: Exception) {
            logger.e(e) { "Connection error to $endpoint" }
            throw MidnightError.ConnectionError(endpoint, e)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw MidnightError.TransactionRejected(
                body.ifEmpty { "HTTP ${response.status.value}: ${response.status.description}" }
            )
        }
        return response.bodyAsText().trim().removeSurrounding("\"")
    }
}
