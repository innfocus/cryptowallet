package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.services.CardanoApiProvider
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.wallets.cardano.CardanoError
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Property-based tests for CardanoApiService HTTP error handling.
 */
class CardanoApiServicePropertyTest {

    /**
     * Create a mock HttpClient that always responds with the given status code and body.
     */
    private fun mockClientWithStatus(statusCode: Int, body: String = "error"): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = body,
                        status = HttpStatusCode.fromValue(statusCode),
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    /** Generate HTTP error status codes (400-599). */
    private fun arbHttpErrorCode(): Arb<Int> = Arb.int(400..599)

    /** Generate non-empty error message strings. */
    private fun arbErrorMessage(): Arb<String> =
        Arb.string(1..100)

    // Feature: cardano-midnight-support, Property 16: HTTP Error Returns Error Object with Status Code
    // **Validates: Requirements 6.7**
    @Test
    fun httpErrorReturnsErrorObjectWithStatusCode() = runTest {
        checkAll(100, arbHttpErrorCode(), arbErrorMessage()) { statusCode, errorBody ->
            val client = mockClientWithStatus(statusCode, errorBody)
            val service = CardanoApiService(
                baseUrl = "https://mock.blockfrost.io/api/v0",
                apiKey = "test-key",
                provider = CardanoApiProvider.BLOCKFROST,
                client = client
            )

            // Test with getCurrentBlock (simplest endpoint — single GET)
            val error = assertFailsWith<CardanoError.ApiError> {
                service.getCurrentBlock()
            }

            // Error should contain the HTTP status code
            assertNotNull(error.statusCode, "ApiError should contain a status code")
            assertEquals(statusCode, error.statusCode, "Status code should match HTTP response")

            // Error message should be non-empty
            assertTrue(error.message.isNotEmpty(), "Error message should not be empty")

            client.close()
        }
    }
}
