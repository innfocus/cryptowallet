package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.services.CardanoApiProvider
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.wallets.cardano.CardanoError
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class CardanoApiServiceUnitTest {

    private val baseUrl = "https://cardano-mainnet.blockfrost.io/api/v0"
    private val apiKey = "test-project-id"

    private fun service(client: HttpClient, provider: CardanoApiProvider = CardanoApiProvider.BLOCKFROST) =
        CardanoApiService(baseUrl, apiKey, provider, client)

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

    // ---- getUtxos ----

    @Test
    fun getUtxosReturnsUtxoList() = runTest {
        val body = """
            [
              {"tx_hash":"abc123","tx_index":0,"amount":[{"unit":"lovelace","quantity":"5000000"}]},
              {"tx_hash":"def456","tx_index":1,"amount":[{"unit":"lovelace","quantity":"3000000"},{"unit":"abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd123448454c4c4f","quantity":"100"}]}
            ]
        """.trimIndent()
        val client = jsonMockClient(body)
        val result = service(client).getUtxos(listOf("addr1_test"))
        assertEquals(2, result.size)
        assertEquals("abc123", result[0].txHash)
        assertEquals(0, result[0].txIndex)
        assertEquals("lovelace", result[0].amount[0].unit)
        assertEquals("5000000", result[0].amount[0].quantity)
        assertEquals(2, result[1].amount.size)
        client.close()
    }

    // ---- getTransactionHistory ----

    @Test
    fun getTransactionHistoryReturnsTxList() = runTest {
        val body = """
            [
              {
                "tx_hash":"tx001",
                "block_height":100,
                "block_time":1700000000,
                "fees":"200000",
                "inputs":[{"address":"addr_in","amount":[{"unit":"lovelace","quantity":"10000000"}]}],
                "outputs":[{"address":"addr_out","amount":[{"unit":"lovelace","quantity":"9800000"}]}]
              }
            ]
        """.trimIndent()
        val client = jsonMockClient(body)
        val result = service(client).getTransactionHistory(listOf("addr_test"))
        assertEquals(1, result.size)
        assertEquals("tx001", result[0].txHash)
        assertEquals(100L, result[0].blockHeight)
        assertEquals("200000", result[0].fees)
        assertEquals(1, result[0].inputs.size)
        assertEquals(1, result[0].outputs.size)
        client.close()
    }

    // ---- submitTransaction ----

    @Test
    fun submitTransactionReturnsTxHash() = runTest {
        val client = jsonMockClient("\"tx_hash_abc123\"")
        val result = service(client).submitTransaction(byteArrayOf(0x01, 0x02))
        assertEquals("tx_hash_abc123", result)
        client.close()
    }

    // ---- getCurrentBlock ----

    @Test
    fun getCurrentBlockReturnsBlockInfo() = runTest {
        val body = """{"epoch":400,"slot":100000,"hash":"blockhash123","height":9000000}"""
        val client = jsonMockClient(body)
        val result = service(client).getCurrentBlock()
        assertEquals(400, result.epoch)
        assertEquals(100000L, result.slot)
        assertEquals("blockhash123", result.hash)
        assertEquals(9000000L, result.height)
        client.close()
    }

    // ---- getProtocolParameters ----

    @Test
    fun getProtocolParametersReturnsParams() = runTest {
        val body = """{"min_fee_a":44,"min_fee_b":155381,"coins_per_utxo_size":"4310"}"""
        val client = jsonMockClient(body)
        val result = service(client).getProtocolParameters()
        assertEquals(44, result.minFeeA)
        assertEquals(155381, result.minFeeB)
        assertEquals("4310", result.coinsPerUtxoSize)
        client.close()
    }

    // ---- getAssetInfo ----

    @Test
    fun getAssetInfoReturnsAssetData() = runTest {
        val body = """
            {
              "asset":"abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd123448454c4c4f",
              "policy_id":"abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
              "asset_name":"48454c4c4f",
              "metadata":{"name":"HELLO","description":"A test token","ticker":"HLO","decimals":6}
            }
        """.trimIndent()
        val client = jsonMockClient(body)
        val result = service(client).getAssetInfo(
            "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            "48454c4c4f"
        )
        assertNotNull(result)
        assertEquals("abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234", result.policyId)
        assertEquals("48454c4c4f", result.assetName)
        assertNotNull(result.metadata)
        assertEquals("HELLO", result.metadata!!.name)
        assertEquals(6, result.metadata!!.decimals)
        client.close()
    }

    @Test
    fun getAssetInfoReturnsNullFor404() = runTest {
        val client = jsonMockClient("Not Found", HttpStatusCode.NotFound)
        val result = service(client).getAssetInfo("a".repeat(56), "00")
        assertNull(result)
        client.close()
    }

    // ---- getAddressAssets ----

    @Test
    fun getAddressAssetsAggregatesTokens() = runTest {
        val policyId = "a".repeat(56)
        val assetHex = "48454c4c4f"
        val body = """
            [
              {"tx_hash":"tx1","tx_index":0,"amount":[
                {"unit":"lovelace","quantity":"5000000"},
                {"unit":"${policyId}${assetHex}","quantity":"100"}
              ]},
              {"tx_hash":"tx2","tx_index":0,"amount":[
                {"unit":"lovelace","quantity":"3000000"},
                {"unit":"${policyId}${assetHex}","quantity":"50"}
              ]}
            ]
        """.trimIndent()
        val client = jsonMockClient(body)
        val result = service(client).getAddressAssets("addr_test")
        assertEquals(1, result.size)
        assertEquals(policyId, result[0].policyId)
        assertEquals(assetHex, result[0].assetName)
        assertEquals(150L, result[0].amount)
        client.close()
    }

    // ---- Error handling ----

    @Test
    fun httpErrorThrowsApiError() = runTest {
        val client = jsonMockClient("Bad Request", HttpStatusCode.BadRequest)
        val error = assertFailsWith<CardanoError.ApiError> {
            service(client).getCurrentBlock()
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.message.isNotEmpty())
        client.close()
    }

    @Test
    fun serverErrorThrowsApiError() = runTest {
        val client = jsonMockClient("Internal Server Error", HttpStatusCode.InternalServerError)
        val error = assertFailsWith<CardanoError.ApiError> {
            service(client).getProtocolParameters()
        }
        assertEquals(500, error.statusCode)
        client.close()
    }

    // ---- Blockfrost auth header ----

    @Test
    fun blockfrostSendsProjectIdHeader() = runTest {
        var capturedProjectId: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedProjectId = request.headers["project_id"]
                    respond(
                        """{"epoch":1,"slot":1,"hash":"h","height":1}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        service(client, CardanoApiProvider.BLOCKFROST).getCurrentBlock()
        assertEquals(apiKey, capturedProjectId)
        client.close()
    }

    // ---- Koios auth header ----

    @Test
    fun koiosSendsAuthorizationHeader() = runTest {
        var capturedAuth: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedAuth = request.headers["Authorization"]
                    respond(
                        """{"epoch":1,"slot":1,"hash":"h","height":1}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        service(client, CardanoApiProvider.KOIOS).getCurrentBlock()
        assertEquals("Bearer $apiKey", capturedAuth)
        client.close()
    }
}
