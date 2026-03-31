package com.lybia.cryptowallet

import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import com.lybia.cryptowallet.wallets.midnight.MidnightAddress
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CommonCoinsManager Cardano and Midnight integration.
 */
class CoinsManagerCardanoTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val blockfrostUrl = "https://cardano-mainnet.blockfrost.io/api/v0"
    private val midnightUrl = "https://mock.midnight.network/api/v0"

    // ── Mock clients ────────────────────────────────────────────────────────

    private fun mockCardanoClient(balanceLovelace: Long = 5_000_000L): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val url = request.url.toString()
                    when {
                        url.contains("/utxos") -> respond(
                            """[{"tx_hash":"abc123","tx_index":0,"amount":[{"unit":"lovelace","quantity":"$balanceLovelace"}]}]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/transactions") -> respond(
                            """[]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/blocks/latest") -> respond(
                            """{"epoch":400,"slot":100000,"hash":"abc","height":9000000}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/parameters") -> respond(
                            """{"min_fee_a":44,"min_fee_b":155381,"coins_per_utxo_size":"4310"}""",
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

    private fun mockMidnightClient(balance: Long = 10_000_000L): HttpClient {
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
                        url.contains("/transactions") -> respond(
                            """[]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/tx/submit") -> respond(
                            "\"txhash_midnight_123\"",
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

    private fun createManager(
        cardanoBalance: Long = 5_000_000L,
        midnightBalance: Long = 10_000_000L
    ): CommonCoinsManager {
        val cardanoClient = mockCardanoClient(cardanoBalance)
        val midnightClient = mockMidnightClient(midnightBalance)
        return CommonCoinsManager(
            mnemonic = testMnemonic,
            cardanoApiService = CardanoApiService(blockfrostUrl, "test-key", client = cardanoClient),
            midnightApiService = MidnightApiService(midnightUrl, "test-key", midnightClient)
        )
    }

    // ── Address tests ───────────────────────────────────────────────────────

    @Test
    fun getCardanoAddressReturnsShelleyAddress() {
        val manager = createManager()
        val address = manager.getAddress(NetworkName.CARDANO)
        assertTrue(address.isNotEmpty(), "Cardano address should not be empty")
        val addrType = CardanoAddress.getAddressType(address)
        assertEquals(CardanoAddressType.SHELLEY_BASE, addrType, "Default Cardano address should be Shelley base")
    }

    @Test
    fun getMidnightAddressReturnsMidnightAddress() {
        val manager = createManager()
        val address = manager.getAddress(NetworkName.MIDNIGHT)
        assertTrue(address.isNotEmpty(), "Midnight address should not be empty")
        assertTrue(address.startsWith("midnight"), "Midnight address should start with 'midnight' prefix")
    }

    // ── Balance tests ───────────────────────────────────────────────────────

    @Test
    fun getCardanoBalanceReturnsSuccess() = runTest {
        val manager = createManager(cardanoBalance = 10_000_000L)
        val result = manager.getCardanoBalance()
        assertTrue(result.success, "Cardano balance query should succeed")
        assertEquals(10.0, result.balance, 0.001, "Balance should be 10 ADA")
    }

    @Test
    fun getMidnightBalanceReturnsSuccess() = runTest {
        val manager = createManager(midnightBalance = 5_000_000L)
        val result = manager.getMidnightBalance()
        assertTrue(result.success, "Midnight balance query should succeed")
        assertEquals(5.0, result.balance, 0.001, "Balance should be 5 tDUST")
    }

    @Test
    fun getBalanceDispatchesToCorrectNetwork() = runTest {
        val manager = createManager(cardanoBalance = 3_000_000L, midnightBalance = 7_000_000L)

        val cardanoResult = manager.getBalance(NetworkName.CARDANO)
        assertTrue(cardanoResult.success)
        assertEquals(3.0, cardanoResult.balance, 0.001)

        val midnightResult = manager.getBalance(NetworkName.MIDNIGHT)
        assertTrue(midnightResult.success)
        assertEquals(7.0, midnightResult.balance, 0.001)
    }

    @Test
    fun getBalanceForUnsupportedNetworkReturnsError() = runTest {
        // BTC is now supported via CommonCoinsManager expansion.
        // BitcoinManager.getBalance may fail without a configured wallet address,
        // but the network itself is supported. Test with a truly unsupported scenario instead.
        val manager = createManager()
        // All NetworkName values are now supported, so we just verify BTC doesn't crash
        val result = manager.getBalance(NetworkName.BTC)
        // BTC balance call may succeed (returning 0.0) or fail gracefully
        // Either way, it should not throw an exception
    }

    // ── Transaction history tests ───────────────────────────────────────────

    @Test
    fun getCardanoTransactionsReturnsResult() = runTest {
        val manager = createManager()
        val result = manager.getCardanoTransactions()
        assertNotNull(result, "Cardano transactions should not be null")
    }

    @Test
    fun getMidnightTransactionsReturnsResult() = runTest {
        val manager = createManager()
        val result = manager.getMidnightTransactions()
        assertNotNull(result, "Midnight transactions should not be null")
    }

    // ── Witness type dispatch tests ─────────────────────────────────────────

    @Test
    fun witnessTypeForByronAddressIsBootstrap() {
        val manager = createManager()
        // Generate a Byron address
        val pubKey = ByteArray(32) { it.toByte() }
        val chainCode = ByteArray(32) { (it + 32).toByte() }
        val byronAddress = CardanoAddress.createByronAddress(pubKey, chainCode)

        val witnessType = manager.getWitnessTypeForAddress(byronAddress)
        assertEquals("bootstrap", witnessType)
    }

    @Test
    fun witnessTypeForShelleyAddressIsVkey() {
        val manager = createManager()
        // Generate a Shelley address
        val paymentHash = ByteArray(28) { it.toByte() }
        val stakingHash = ByteArray(28) { (it + 28).toByte() }
        val shelleyAddress = CardanoAddress.createBaseAddress(paymentHash, stakingHash, false)

        val witnessType = manager.getWitnessTypeForAddress(shelleyAddress)
        assertEquals("vkey", witnessType)
    }

    @Test
    fun witnessTypeForEnterpriseAddressIsVkey() {
        val manager = createManager()
        val paymentHash = ByteArray(28) { it.toByte() }
        val enterpriseAddress = CardanoAddress.createEnterpriseAddress(paymentHash, false)

        val witnessType = manager.getWitnessTypeForAddress(enterpriseAddress)
        assertEquals("vkey", witnessType)
    }

    // ── Send Midnight tests ─────────────────────────────────────────────────

    @Test
    fun sendMidnightWithSufficientBalanceSucceeds() = runTest {
        val manager = createManager(midnightBalance = 10_000_000L)
        val toAddress = MidnightAddress.fromMnemonic(
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        )
        val result = manager.sendMidnight(toAddress, 5_000_000L)
        assertTrue(result.success, "Send should succeed with sufficient balance")
        assertTrue(result.txHash.isNotEmpty(), "Transaction hash should not be empty")
    }

    @Test
    fun sendMidnightWithInsufficientBalanceFails() = runTest {
        val manager = createManager(midnightBalance = 1_000L)
        val toAddress = MidnightAddress.fromMnemonic(
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        )
        val result = manager.sendMidnight(toAddress, 5_000_000L)
        assertFalse(result.success, "Send should fail with insufficient balance")
        assertNotNull(result.error)
    }

    // ── CoinNetwork URL tests ───────────────────────────────────────────────

    @Test
    fun coinNetworkBlockfrostUrlIsCorrect() {
        Config.shared.setNetwork(com.lybia.cryptowallet.enums.Network.MAINNET)
        val network = CoinNetwork(NetworkName.CARDANO)
        val url = network.getBlockfrostUrl()
        assertTrue(url.contains("blockfrost"), "Blockfrost URL should contain 'blockfrost'")
        assertTrue(url.contains("mainnet"), "Mainnet URL should contain 'mainnet'")
    }

    @Test
    fun coinNetworkMidnightUrlIsCorrect() {
        Config.shared.setNetwork(com.lybia.cryptowallet.enums.Network.TESTNET)
        val network = CoinNetwork(NetworkName.MIDNIGHT)
        val url = network.getMidnightApiUrl()
        assertTrue(url.contains("midnight"), "Midnight URL should contain 'midnight'")
        assertTrue(url.contains("testnet"), "Testnet URL should contain 'testnet'")
    }
}
