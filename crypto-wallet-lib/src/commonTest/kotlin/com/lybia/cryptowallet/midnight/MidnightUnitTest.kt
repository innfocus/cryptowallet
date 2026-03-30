package com.lybia.cryptowallet.midnight

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.midnight.MidnightAddress
import com.lybia.cryptowallet.wallets.midnight.MidnightError
import com.lybia.cryptowallet.wallets.midnight.MidnightManager
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for MidnightManager and MidnightApiService with mock client.
 */
class MidnightUnitTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val baseUrl = "https://mock.midnight.network/api/v0"

    private fun jsonMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(responseBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun routingMockClient(handler: (String) -> Pair<String, HttpStatusCode>): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val (body, status) = handler(request.url.toString())
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun mockApiService(client: HttpClient): MidnightApiService {
        return MidnightApiService(baseUrl, "test-key", client)
    }

    private fun createManager(client: HttpClient): MidnightManager {
        return MidnightManager(testMnemonic, mockApiService(client))
    }

    // ── MidnightAddress tests ───────────────────────────────────────────────

    @Test
    fun addressFromMnemonicIsNonEmpty() {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        assertTrue(address.isNotEmpty())
    }

    @Test
    fun addressStartsWithMidnightPrefix() {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        assertTrue(address.startsWith("midnight1"), "Expected midnight1 prefix, got: $address")
    }

    @Test
    fun addressIsDeterministic() {
        val addr1 = MidnightAddress.fromMnemonic(testMnemonic)
        val addr2 = MidnightAddress.fromMnemonic(testMnemonic)
        assertEquals(addr1, addr2)
    }

    @Test
    fun differentMnemonicsProduceDifferentAddresses() {
        val addr1 = MidnightAddress.fromMnemonic(testMnemonic)
        val addr2 = MidnightAddress.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
        assertNotEquals(addr1, addr2)
    }

    @Test
    fun generatedAddressIsValid() {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        assertTrue(MidnightAddress.isValid(address))
    }

    @Test
    fun invalidAddressIsRejected() {
        assertFalse(MidnightAddress.isValid("not_a_valid_address"))
        assertFalse(MidnightAddress.isValid(""))
        assertFalse(MidnightAddress.isValid("addr1qxy2"))
    }

    // ── MidnightApiService tests ────────────────────────────────────────────

    @Test
    fun getBalanceReturnsBalance() = runTest {
        val client = jsonMockClient("""{"balance":5000000,"address":"midnight1test"}""")
        val service = mockApiService(client)
        val balance = service.getBalance("midnight1test")
        assertEquals(5_000_000L, balance)
        client.close()
    }

    @Test
    fun getTransactionHistoryReturnsList() = runTest {
        val body = """[
            {"tx_hash":"txhash1","amount":1000000,"timestamp":1700000000,"from_address":"midnight1from","to_address":"midnight1to"}
        ]""".trimIndent()
        val client = jsonMockClient(body)
        val service = mockApiService(client)
        val txs = service.getTransactionHistory("midnight1test")
        assertEquals(1, txs.size)
        assertEquals("txhash1", txs[0].txHash)
        assertEquals(1_000_000L, txs[0].amount)
        assertEquals("midnight1from", txs[0].fromAddress)
        assertEquals("midnight1to", txs[0].toAddress)
        client.close()
    }

    @Test
    fun submitTransactionReturnsTxHash() = runTest {
        val client = jsonMockClient("\"tx_hash_result\"")
        val service = mockApiService(client)
        val result = service.submitTransaction(byteArrayOf(0x01, 0x02))
        assertEquals("tx_hash_result", result)
        client.close()
    }

    @Test
    fun getBalanceThrowsConnectionErrorOnFailure() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw java.io.IOException("Network unreachable") }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val service = mockApiService(client)
        assertFailsWith<MidnightError.ConnectionError> {
            service.getBalance("midnight1test")
        }
        client.close()
    }

    @Test
    fun getBalanceThrowsOnHttpError() = runTest {
        val client = jsonMockClient("Server Error", HttpStatusCode.InternalServerError)
        val service = mockApiService(client)
        assertFailsWith<MidnightError.TransactionRejected> {
            service.getBalance("midnight1test")
        }
        client.close()
    }

    @Test
    fun apiServiceSendsAuthHeader() = runTest {
        var capturedAuth: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedAuth = request.headers["Authorization"]
                    respond(
                        """{"balance":0,"address":"midnight1test"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val service = MidnightApiService(baseUrl, "my-api-key", client)
        service.getBalance("midnight1test")
        assertEquals("Bearer my-api-key", capturedAuth)
        client.close()
    }

    // ── MidnightManager tests ───────────────────────────────────────────────

    @Test
    fun getAddressReturnsMidnightAddress() {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val address = manager.getAddress()
        assertTrue(address.startsWith("midnight1"))
        assertTrue(MidnightAddress.isValid(address))
        client.close()
    }

    @Test
    fun getBalanceReturnsTDustInWholeUnits() = runTest {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        val client = routingMockClient { url ->
            when {
                url.contains("/balance") -> """{"balance":5000000,"address":"$address"}""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val balance = manager.getBalance(address)
        assertEquals(5.0, balance, 0.001)
        client.close()
    }

    @Test
    fun getBalanceReturnsZeroForEmptyBalance() = runTest {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        val client = routingMockClient { url ->
            when {
                url.contains("/balance") -> """{"balance":0,"address":"$address"}""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val balance = manager.getBalance(address)
        assertEquals(0.0, balance, 0.001)
        client.close()
    }

    @Test
    fun getTransactionHistoryReturnsTxList() = runTest {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        val txResponse = """[{"tx_hash":"tx001","amount":1000000,"timestamp":1700000000,"from_address":"midnight1from","to_address":"midnight1to"}]"""
        val client = routingMockClient { url ->
            when {
                url.contains("/transactions") -> txResponse to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val history = manager.getTransactionHistory(address)
        assertNotNull(history)
        client.close()
    }

    @Test
    fun transferSubmitsSignedTransaction() = runTest {
        val client = jsonMockClient("\"tx_hash_result\"")
        val manager = createManager(client)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val base64Data = kotlin.io.encoding.Base64.encode(byteArrayOf(0x01, 0x02, 0x03))
        val result = manager.transfer(base64Data, coinNetwork)
        assertTrue(result.success)
        assertEquals("tx_hash_result", result.txHash)
        client.close()
    }

    @Test
    fun transferReturnsErrorOnFailure() = runTest {
        val client = jsonMockClient("Bad Request", HttpStatusCode.BadRequest)
        val manager = createManager(client)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val base64Data = kotlin.io.encoding.Base64.encode(byteArrayOf(0x01))
        val result = manager.transfer(base64Data, coinNetwork)
        assertFalse(result.success)
        assertNotNull(result.error)
        client.close()
    }

    @Test
    fun sendTDustThrowsInsufficientTDustWhenBalanceTooLow() = runTest {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        val toAddress = MidnightAddress.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
        val client = routingMockClient { url ->
            when {
                url.contains("/balance") -> """{"balance":100,"address":"$address"}""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val error = assertFailsWith<MidnightError.InsufficientTDust> {
            manager.sendTDust(toAddress, 1000L)
        }
        assertEquals(100L, error.balance)
        assertEquals(1000L, error.required)
        client.close()
    }

    @Test
    fun sendTDustThrowsInvalidAddressForBadAddress() = runTest {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        assertFailsWith<MidnightError.InvalidAddress> {
            manager.sendTDust("not_a_valid_address", 1000L)
        }
        client.close()
    }

    @Test
    fun sendTDustSucceedsWithSufficientBalance() = runTest {
        val address = MidnightAddress.fromMnemonic(testMnemonic)
        val toAddress = MidnightAddress.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
        val client = routingMockClient { url ->
            when {
                url.contains("/balance") -> """{"balance":5000000,"address":"$address"}""" to HttpStatusCode.OK
                url.contains("/submit") -> "\"tx_hash_success\"" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val txHash = manager.sendTDust(toAddress, 1_000_000L)
        assertEquals("tx_hash_success", txHash)
        client.close()
    }

    @Test
    fun getChainIdReturnsMidnightTestnet() = runTest {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        assertEquals("midnight-testnet", manager.getChainId(coinNetwork))
        client.close()
    }

    // ── MidnightError tests ─────────────────────────────────────────────────

    @Test
    fun insufficientTDustErrorContainsBalanceInfo() {
        val error = MidnightError.InsufficientTDust(balance = 100, required = 500)
        assertEquals(100L, error.balance)
        assertEquals(500L, error.required)
        assertTrue(error.message.contains("100"))
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun connectionErrorContainsEndpoint() {
        val cause = RuntimeException("timeout")
        val error = MidnightError.ConnectionError(endpoint = "https://api.midnight.network", cause = cause)
        assertTrue(error.message.contains("https://api.midnight.network"))
        assertEquals(cause, error.cause)
    }

    @Test
    fun transactionRejectedContainsReason() {
        val error = MidnightError.TransactionRejected(reason = "invalid signature")
        assertTrue(error.message.contains("invalid signature"))
    }

    @Test
    fun invalidAddressContainsAddress() {
        val error = MidnightError.InvalidAddress(address = "bad_addr")
        assertTrue(error.message.contains("bad_addr"))
    }
}
