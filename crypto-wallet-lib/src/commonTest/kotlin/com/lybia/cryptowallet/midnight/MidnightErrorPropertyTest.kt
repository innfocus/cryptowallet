package com.lybia.cryptowallet.midnight

import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.midnight.MidnightAddress
import com.lybia.cryptowallet.wallets.midnight.MidnightError
import com.lybia.cryptowallet.wallets.midnight.MidnightManager
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Property-based tests for Midnight insufficient tDUST error handling.
 */
class MidnightErrorPropertyTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val baseUrl = "https://mock.midnight.network/api/v0"

    /**
     * Create a mock client that returns a specific balance for any address query,
     * and accepts transaction submissions.
     */
    private fun mockClientWithBalance(balance: Long): HttpClient {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val url = request.url.toString()
                    when {
                        url.contains("/balance") -> respond(
                            """{"balance":$balance,"address":"$address"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> respond(
                            "Not Found",
                            HttpStatusCode.NotFound,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    /** Generate a balance that is strictly less than the required amount. */
    private fun arbBalanceAndRequired(): Arb<Pair<Long, Long>> =
        Arb.long(0L..999_999_999L).flatMap { balance ->
            Arb.long((balance + 1)..1_000_000_000L).map { required ->
                balance to required
            }
        }

    // Feature: cardano-midnight-support, Property 18: Insufficient tDUST Error with Balance Info
    // **Validates: Requirements 9.4**
    @Test
    fun insufficientTDustErrorContainsBalanceAndRequiredAmount() = runTest {
        checkAll(100, arbBalanceAndRequired()) { (balance, required) ->
            val client = mockClientWithBalance(balance)
            val apiService = MidnightApiService(baseUrl, "test-key", client)
            val manager = MidnightManager(testMnemonic, apiService)

            // Generate a valid destination address from a different mnemonic
            val toAddress = MidnightAddress.fromMnemonic(
                "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
            )

            val error = assertFailsWith<MidnightError.InsufficientTDust> {
                manager.sendTDust(toAddress, required)
            }

            // Error should contain both the current balance and the requested amount
            assertEquals(balance, error.balance, "Error should contain current balance")
            assertEquals(required, error.required, "Error should contain required amount")

            // Error message should be non-empty and descriptive
            assert(error.message.isNotEmpty()) { "Error message should not be empty" }
            assert(error.message.contains(balance.toString())) { "Error message should contain balance" }
            assert(error.message.contains(required.toString())) { "Error message should contain required amount" }

            client.close()
        }
    }
}
