package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.CardanoApiProvider
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.wallets.cardano.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for CardanoManager with mock API service.
 */
class CardanoManagerTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val baseUrl = "https://cardano-mainnet.blockfrost.io/api/v0"

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

    private fun mockApiService(client: HttpClient): CardanoApiService {
        return CardanoApiService(baseUrl, "test-key", CardanoApiProvider.BLOCKFROST, client)
    }

    private fun createManager(client: HttpClient): CardanoManager {
        return CardanoManager(testMnemonic, mockApiService(client))
    }

    // ── Task 6.1: BaseCoinManager overrides ─────────────────────────────────

    @Test
    fun getAddressReturnsShelleyBaseAddress() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val address = manager.getAddress()
        assertTrue(address.startsWith("addr1"), "Shelley mainnet address should start with addr1, got: $address")
        assertTrue(CardanoAddress.isValidShelleyAddress(address))
        client.close()
    }

    @Test
    fun getAddressReturnsTestnetAddressWhenConfigured() {
        Config.shared.setNetwork(Network.TESTNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val address = manager.getAddress()
        assertTrue(address.startsWith("addr_test1"), "Shelley testnet address should start with addr_test1, got: $address")
        Config.shared.setNetwork(Network.MAINNET)
        client.close()
    }

    @Test
    fun getBalanceReturnsAdaFromUtxos() = runTest {
        val utxoResponse = """[
            {"tx_hash":"${"a".repeat(64)}","tx_index":0,"amount":[{"unit":"lovelace","quantity":"5000000"}]},
            {"tx_hash":"${"b".repeat(64)}","tx_index":1,"amount":[{"unit":"lovelace","quantity":"3000000"}]}
        ]""".trimIndent()
        val client = jsonMockClient(utxoResponse)
        val manager = createManager(client)
        val balance = manager.getBalance("addr_test1_dummy")
        assertEquals(8.0, balance, 0.001)
        client.close()
    }

    @Test
    fun getBalanceReturnsZeroForEmptyUtxos() = runTest {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val balance = manager.getBalance("addr_test1_dummy")
        assertEquals(0.0, balance, 0.001)
        client.close()
    }

    @Test
    fun getTransactionHistoryReturnsTxList() = runTest {
        val txResponse = """[{"tx_hash":"tx001","block_height":100,"block_time":1700000000,"fees":"200000",
            "inputs":[{"address":"addr_in","amount":[{"unit":"lovelace","quantity":"10000000"}]}],
            "outputs":[{"address":"addr_out","amount":[{"unit":"lovelace","quantity":"9800000"}]}]}]""".trimIndent()
        val client = jsonMockClient(txResponse)
        val manager = createManager(client)
        val history = manager.getTransactionHistory("addr_test1_dummy")
        assertNotNull(history)
        client.close()
    }

    @Test
    fun transferSubmitsSignedTransaction() = runTest {
        val client = jsonMockClient("\"tx_hash_result\"")
        val manager = createManager(client)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val base64Data = kotlin.io.encoding.Base64.encode(byteArrayOf(0x83.toByte(), 0xa0.toByte(), 0xa0.toByte(), 0xf6.toByte()))
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

    // ── Task 6.2: Address generation ────────────────────────────────────────

    @Test
    fun getShelleyAddressIsDeterministic() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val addr1 = manager.getShelleyAddress(0, 0)
        val addr2 = manager.getShelleyAddress(0, 0)
        assertEquals(addr1, addr2, "Same mnemonic + account + index should produce same address")
        client.close()
    }

    @Test
    fun getShelleyAddressDifferentIndexesDiffer() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val addr0 = manager.getShelleyAddress(0, 0)
        val addr1 = manager.getShelleyAddress(0, 1)
        assertNotEquals(addr0, addr1)
        client.close()
    }

    @Test
    fun getShelleyAddressDifferentAccountsDiffer() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val addr0 = manager.getShelleyAddress(0, 0)
        val addr1 = manager.getShelleyAddress(1, 0)
        assertNotEquals(addr0, addr1)
        client.close()
    }

    @Test
    fun getByronAddressIsValid() {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val byronAddr = manager.getByronAddress(0)
        assertTrue(CardanoAddress.isValidByronAddress(byronAddr), "Byron address should be valid: $byronAddr")
        client.close()
    }

    @Test
    fun getByronAddressIsDeterministic() {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val addr1 = manager.getByronAddress(0)
        val addr2 = manager.getByronAddress(0)
        assertEquals(addr1, addr2)
        client.close()
    }

    @Test
    fun getStakingAddressIsValid() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val stakingAddr = manager.getStakingAddress(0)
        assertTrue(stakingAddr.startsWith("stake1"), "Staking address should start with stake1, got: $stakingAddr")
        assertTrue(CardanoAddress.isValidShelleyAddress(stakingAddr))
        client.close()
    }

    @Test
    fun getStakingAddressIsDeterministic() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val addr1 = manager.getStakingAddress(0)
        val addr2 = manager.getStakingAddress(0)
        assertEquals(addr1, addr2)
        client.close()
    }

    @Test
    fun differentMnemonicsProduceDifferentAddresses() {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager1 = createManager(client)
        val mnemonic2 = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        val manager2 = CardanoManager(mnemonic2, mockApiService(client))
        assertNotEquals(manager1.getAddress(), manager2.getAddress())
        client.close()
    }

    // ── Task 6.3: Native token operations ───────────────────────────────────

    @Test
    fun getTokenBalanceReturnsCorrectAmount() = runTest {
        val policyId = "a".repeat(56)
        val assetHex = "48454c4c4f"
        val utxoResponse = """[
            {"tx_hash":"${"a".repeat(64)}","tx_index":0,"amount":[
                {"unit":"lovelace","quantity":"5000000"},
                {"unit":"${policyId}${assetHex}","quantity":"100"}
            ]},
            {"tx_hash":"${"b".repeat(64)}","tx_index":1,"amount":[
                {"unit":"lovelace","quantity":"3000000"},
                {"unit":"${policyId}${assetHex}","quantity":"50"}
            ]}
        ]""".trimIndent()
        val client = jsonMockClient(utxoResponse)
        val manager = createManager(client)
        val balance = manager.getTokenBalance("addr_test1_dummy", policyId, assetHex)
        assertEquals(150L, balance)
        client.close()
    }

    @Test
    fun getTokenBalanceReturnsZeroWhenNoTokens() = runTest {
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val balance = manager.getTokenBalance("addr_test1_dummy", "a".repeat(56), "48454c4c4f")
        assertEquals(0L, balance)
        client.close()
    }

    // ── Task 6.4: buildAndSignTransaction ───────────────────────────────────

    @Test
    fun buildAndSignTransactionProducesValidSignedTx() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val txHash = "a".repeat(64)
        val client = routingMockClient { url ->
            when {
                url.contains("/utxos") -> """[{"tx_hash":"$txHash","tx_index":0,"amount":[{"unit":"lovelace","quantity":"10000000"}]}]""" to HttpStatusCode.OK
                url.contains("/blocks/latest") -> """{"epoch":400,"slot":100000,"hash":"blockhash","height":9000000}""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val signedTx = manager.buildAndSignTransaction(
            toAddress = manager.getAddress(),
            amount = 2_000_000L,
            fee = 200_000L
        )

        assertNotNull(signedTx.body)
        assertNotNull(signedTx.witnessSet)
        assertTrue(signedTx.body.inputs.isNotEmpty())
        assertTrue(signedTx.body.outputs.isNotEmpty())
        assertEquals(200_000L, signedTx.body.fee)
        assertTrue(signedTx.body.ttl > 100000)

        val serialized = signedTx.serialize()
        assertTrue(serialized.isNotEmpty())

        val txId = signedTx.getTransactionId()
        assertEquals(64, txId.length)
        assertTrue(txId.all { it in '0'..'9' || it in 'a'..'f' })
        client.close()
    }

    @Test
    fun buildAndSignTransactionIncludesChangeOutput() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val txHash = "a".repeat(64)
        val client = routingMockClient { url ->
            when {
                url.contains("/utxos") -> """[{"tx_hash":"$txHash","tx_index":0,"amount":[{"unit":"lovelace","quantity":"10000000"}]}]""" to HttpStatusCode.OK
                url.contains("/blocks/latest") -> """{"epoch":400,"slot":100000,"hash":"blockhash","height":9000000}""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        val signedTx = manager.buildAndSignTransaction(
            toAddress = manager.getAddress(),
            amount = 2_000_000L,
            fee = 200_000L
        )

        assertEquals(2, signedTx.body.outputs.size, "Should have recipient + change outputs")
        assertEquals(2_000_000L, signedTx.body.outputs[0].lovelace)
        assertEquals(7_800_000L, signedTx.body.outputs[1].lovelace)
        client.close()
    }

    @Test
    fun buildAndSignTransactionThrowsOnInsufficientFunds() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val client = routingMockClient { url ->
            when {
                url.contains("/utxos") -> """[{"tx_hash":"${"a".repeat(64)}","tx_index":0,"amount":[{"unit":"lovelace","quantity":"1000000"}]}]""" to HttpStatusCode.OK
                else -> "Not Found" to HttpStatusCode.NotFound
            }
        }
        val manager = createManager(client)
        assertFailsWith<CardanoError.ApiError> {
            manager.buildAndSignTransaction(
                toAddress = manager.getAddress(),
                amount = 5_000_000L,
                fee = 200_000L
            )
        }
        client.close()
    }

    @Test
    fun getChainIdReturnsCorrectNetwork() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val client = jsonMockClient("[]")
        val manager = createManager(client)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        assertEquals("mainnet", manager.getChainId(coinNetwork))
        Config.shared.setNetwork(Network.TESTNET)
        assertEquals("testnet", manager.getChainId(coinNetwork))
        Config.shared.setNetwork(Network.MAINNET)
        client.close()
    }
}
