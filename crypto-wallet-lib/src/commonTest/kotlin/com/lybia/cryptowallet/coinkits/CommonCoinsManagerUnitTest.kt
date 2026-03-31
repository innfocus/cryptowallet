package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
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
 * Unit tests for CommonCoinsManager.
 *
 * Tests delegation, capability checking, and error handling.
 */
class CommonCoinsManagerUnitTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

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
                            "[]",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        url.contains("/blocks/latest") -> respond(
                            """{"epoch":400,"slot":100000,"hash":"abc","height":9000000}""",
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

    private fun createManagerWithMocks(): CommonCoinsManager {
        val cardanoClient = mockCardanoClient()
        return CommonCoinsManager(
            mnemonic = testMnemonic,
            cardanoApiService = CardanoApiService(
                "https://mock.blockfrost.io/api/v0", "test-key", client = cardanoClient
            )
        )
    }

    // ── Delegation tests ────────────────────────────────────────────────────

    @Test
    fun getAddressCardanoDelegatesToCardanoManager() {
        val manager = createManagerWithMocks()
        val address = manager.getAddress(NetworkName.CARDANO)
        assertTrue(address.isNotEmpty())
        val addrType = CardanoAddress.getAddressType(address)
        assertEquals(CardanoAddressType.SHELLEY_BASE, addrType)
    }

    @Test
    fun getAddressMidnightDelegatesToMidnightManager() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        val address = manager.getAddress(NetworkName.MIDNIGHT)
        assertTrue(address.isNotEmpty())
        assertTrue(address.startsWith("midnight"))
    }

    @Test
    fun getAddressTonDelegatesToTonManager() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        val address = manager.getAddress(NetworkName.TON)
        assertTrue(address.isNotEmpty())
    }

    @Test
    fun getAddressXrpDelegatesToRippleManager() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        val address = manager.getAddress(NetworkName.XRP)
        assertTrue(address.isNotEmpty())
    }

    // ── Balance tests with mock ─────────────────────────────────────────────

    @Test
    fun getBalanceCardanoReturnsCorrectValue() = runTest {
        val manager = createManagerWithMocks()
        val result = manager.getBalance(NetworkName.CARDANO)
        assertTrue(result.success)
        assertEquals(5.0, result.balance, 0.001)
    }

    // ── Capability checking unit tests ──────────────────────────────────────

    @Test
    fun supportsTokensReturnsCorrectValues() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertTrue(manager.supportsTokens(NetworkName.ETHEREUM))
        assertTrue(manager.supportsTokens(NetworkName.ARBITRUM))
        assertTrue(manager.supportsTokens(NetworkName.CARDANO))
        assertTrue(manager.supportsTokens(NetworkName.TON))
        assertFalse(manager.supportsTokens(NetworkName.BTC))
        assertFalse(manager.supportsTokens(NetworkName.XRP))
        assertFalse(manager.supportsTokens(NetworkName.MIDNIGHT))
    }

    @Test
    fun supportsNFTsReturnsCorrectValues() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertTrue(manager.supportsNFTs(NetworkName.ETHEREUM))
        assertTrue(manager.supportsNFTs(NetworkName.ARBITRUM))
        assertTrue(manager.supportsNFTs(NetworkName.TON))
        assertFalse(manager.supportsNFTs(NetworkName.BTC))
        assertFalse(manager.supportsNFTs(NetworkName.XRP))
        assertFalse(manager.supportsNFTs(NetworkName.CARDANO))
        assertFalse(manager.supportsNFTs(NetworkName.MIDNIGHT))
    }

    @Test
    fun supportsFeeEstimationReturnsCorrectValues() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertTrue(manager.supportsFeeEstimation(NetworkName.ETHEREUM))
        assertTrue(manager.supportsFeeEstimation(NetworkName.ARBITRUM))
        assertFalse(manager.supportsFeeEstimation(NetworkName.BTC))
        assertFalse(manager.supportsFeeEstimation(NetworkName.XRP))
        assertFalse(manager.supportsFeeEstimation(NetworkName.CARDANO))
        assertFalse(manager.supportsFeeEstimation(NetworkName.TON))
        assertFalse(manager.supportsFeeEstimation(NetworkName.MIDNIGHT))
    }
}
