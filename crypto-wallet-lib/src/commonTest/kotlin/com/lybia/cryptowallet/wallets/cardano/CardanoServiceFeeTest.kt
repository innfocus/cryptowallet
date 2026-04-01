package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.services.CardanoApiProvider
import com.lybia.cryptowallet.services.CardanoApiService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for CardanoManager service fee support.
 *
 * Tests cover:
 * - Transaction building with service fee output (Requirements 2.2)
 * - Backward compatibility when no service fee is provided
 * - UTXO selection accounting for service fee amount
 * - Change calculation with service fee
 */
class CardanoServiceFeeTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val baseUrl = "https://cardano-mainnet.blockfrost.io/api/v0"
    private val utxoTxHash = "a".repeat(64)

    @BeforeTest
    fun setup() {
        Config.shared.setNetwork(Network.MAINNET)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun routingMockClient(
        utxoLovelace: Long = 10_000_000L,
        slot: Long = 100_000L
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val url = request.url.toString()
                    when {
                        url.contains("/utxos") -> respond(
                            """[{"tx_hash":"$utxoTxHash","tx_index":0,"amount":[{"unit":"lovelace","quantity":"$utxoLovelace"}]}]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/blocks/latest") -> respond(
                            """{"epoch":400,"slot":$slot,"hash":"blockhash","height":9000000}""",
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

    private fun createManager(client: HttpClient): CardanoManager {
        val apiService = CardanoApiService(baseUrl, "test-key", CardanoApiProvider.BLOCKFROST, client)
        return CardanoManager(testMnemonic, apiService)
    }

    // ── Service fee output tests ────────────────────────────────────────

    @Test
    fun buildAndSignTransaction_withServiceFee_includesServiceFeeOutput() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = toAddress,
            serviceFeeLovelace = 500_000L
        )

        // 3 outputs: recipient + service fee + change
        assertEquals(3, signedTx.body.outputs.size, "Should have recipient + service fee + change outputs")
        assertEquals(2_000_000L, signedTx.body.outputs[0].lovelace, "Recipient output")
        assertEquals(500_000L, signedTx.body.outputs[1].lovelace, "Service fee output")
        // change = 10M - 2M - 200k - 500k = 7.3M
        assertEquals(7_300_000L, signedTx.body.outputs[2].lovelace, "Change output")
        client.close()
    }

    @Test
    fun buildAndSignTransaction_withServiceFee_serviceOutputAddressMatchesServiceAddress() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        // Use a different mnemonic to get a distinct service address
        val serviceManager = CardanoManager(
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
            CardanoApiService(baseUrl, "test-key", CardanoApiProvider.BLOCKFROST, client)
        )
        val serviceAddr = serviceManager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = serviceAddr,
            serviceFeeLovelace = 500_000L
        )

        // Verify service fee output address bytes match the service address
        val expectedBytes = CardanoManager.addressToBytes(serviceAddr)
        assertTrue(
            signedTx.body.outputs[1].addressBytes.contentEquals(expectedBytes),
            "Service fee output should go to the service address"
        )
        client.close()
    }

    @Test
    fun buildAndSignTransaction_withServiceFee_correctChangeCalculation() = runTest {
        val client = routingMockClient(utxoLovelace = 5_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 1_000_000L,
            fee = 180_000L,
            serviceAddress = toAddress,
            serviceFeeLovelace = 1_000_000L
        )

        // change = 5M - 1M - 180k - 1M = 2.82M
        val expectedChange = 5_000_000L - 1_000_000L - 180_000L - 1_000_000L
        assertEquals(3, signedTx.body.outputs.size)
        assertEquals(expectedChange, signedTx.body.outputs[2].lovelace, "Change should account for service fee")
        client.close()
    }

    // ── Backward compatibility tests ────────────────────────────────────

    @Test
    fun buildAndSignTransaction_withoutServiceFee_producesStandardTx() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L
        )

        // 2 outputs: recipient + change (no service fee)
        assertEquals(2, signedTx.body.outputs.size, "Should have recipient + change only")
        assertEquals(2_000_000L, signedTx.body.outputs[0].lovelace)
        // change = 10M - 2M - 200k = 7.8M
        assertEquals(7_800_000L, signedTx.body.outputs[1].lovelace)
        client.close()
    }

    @Test
    fun buildAndSignTransaction_nullServiceAddress_noServiceFeeOutput() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = null,
            serviceFeeLovelace = 500_000L
        )

        assertEquals(2, signedTx.body.outputs.size, "Null service address should skip service fee output")
        client.close()
    }

    @Test
    fun buildAndSignTransaction_emptyServiceAddress_noServiceFeeOutput() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = "",
            serviceFeeLovelace = 500_000L
        )

        assertEquals(2, signedTx.body.outputs.size, "Empty service address should skip service fee output")
        client.close()
    }

    @Test
    fun buildAndSignTransaction_zeroServiceFee_noServiceFeeOutput() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = toAddress,
            serviceFeeLovelace = 0L
        )

        assertEquals(2, signedTx.body.outputs.size, "Zero service fee should skip service fee output")
        client.close()
    }

    // ── UTXO selection with service fee ─────────────────────────────────

    @Test
    fun buildAndSignTransaction_withServiceFee_insufficientFundsThrows() = runTest {
        // 3M available, need 2M + 200k + 1.5M = 3.7M
        val client = routingMockClient(utxoLovelace = 3_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        assertFailsWith<CardanoError.ApiError> {
            manager.buildAndSignTransaction(
                toAddress = toAddress,
                amount = 2_000_000L,
                fee = 200_000L,
                serviceAddress = toAddress,
                serviceFeeLovelace = 1_500_000L
            )
        }
        client.close()
    }

    @Test
    fun buildAndSignTransaction_withServiceFee_exactBalance_noChangeOutput() = runTest {
        // Exact: 2M + 200k + 500k = 2.7M
        val client = routingMockClient(utxoLovelace = 2_700_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = toAddress,
            serviceFeeLovelace = 500_000L
        )

        // 2 outputs: recipient + service fee (no change since balance is exact)
        assertEquals(2, signedTx.body.outputs.size, "Exact balance should produce no change output")
        assertEquals(2_000_000L, signedTx.body.outputs[0].lovelace)
        assertEquals(500_000L, signedTx.body.outputs[1].lovelace)
        client.close()
    }

    // ── Transaction validity ────────────────────────────────────────────

    @Test
    fun buildAndSignTransaction_withServiceFee_producesValidSerializedTx() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = 200_000L,
            serviceAddress = toAddress,
            serviceFeeLovelace = 500_000L
        )

        // Verify the signed transaction can be serialized
        val serialized = signedTx.serialize()
        assertTrue(serialized.isNotEmpty(), "Serialized transaction should not be empty")

        // Verify transaction ID is valid hex
        val txId = signedTx.getTransactionId()
        assertEquals(64, txId.length, "Transaction ID should be 64 hex chars")
        assertTrue(txId.all { it in '0'..'9' || it in 'a'..'f' }, "Transaction ID should be valid hex")
        client.close()
    }

    @Test
    fun buildAndSignTransaction_withServiceFee_feeIsCorrect() = runTest {
        val client = routingMockClient(utxoLovelace = 10_000_000L)
        val manager = createManager(client)
        val toAddress = manager.getAddress()
        val fee = 200_000L

        val signedTx = manager.buildAndSignTransaction(
            toAddress = toAddress,
            amount = 2_000_000L,
            fee = fee,
            serviceAddress = toAddress,
            serviceFeeLovelace = 500_000L
        )

        assertEquals(fee, signedTx.body.fee, "Transaction fee should match the specified fee")
        client.close()
    }
}
