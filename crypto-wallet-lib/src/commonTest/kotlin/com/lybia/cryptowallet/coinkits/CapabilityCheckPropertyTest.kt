package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Property 15: CommonCoinsManager capability checking**
 *
 * supportsTokens: Cardano=true, Ethereum=true, TON=true, Bitcoin=false, Ripple=false, Midnight=false
 * supportsNFTs: Ethereum=true, TON=true, others=false
 * supportsFeeEstimation: Ethereum=true, others=false
 *
 * **Validates: Requirements 10.5**
 */
class CapabilityCheckPropertyTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val tokenCapableNetworks = setOf(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.CARDANO,
        NetworkName.TON
    )

    private val nftCapableNetworks = setOf(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.TON
    )

    private val feeEstimationCapableNetworks = setOf(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM
    )

    private val allNetworks = NetworkName.entries.toList()

    @Test
    fun supportsTokensMatchesExpectedCapabilities() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(allNetworks)
        ) { network ->
            val expected = network in tokenCapableNetworks
            assertEquals(
                expected,
                manager.supportsTokens(network),
                "supportsTokens($network) should be $expected"
            )
        }
    }

    @Test
    fun supportsNFTsMatchesExpectedCapabilities() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(allNetworks)
        ) { network ->
            val expected = network in nftCapableNetworks
            assertEquals(
                expected,
                manager.supportsNFTs(network),
                "supportsNFTs($network) should be $expected"
            )
        }
    }

    @Test
    fun supportsFeeEstimationMatchesExpectedCapabilities() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(allNetworks)
        ) { network ->
            val expected = network in feeEstimationCapableNetworks
            assertEquals(
                expected,
                manager.supportsFeeEstimation(network),
                "supportsFeeEstimation($network) should be $expected"
            )
        }
    }

    @Test
    fun bitcoinDoesNotSupportTokensOrNFTs() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertFalse(manager.supportsTokens(NetworkName.BTC))
        assertFalse(manager.supportsNFTs(NetworkName.BTC))
        assertFalse(manager.supportsFeeEstimation(NetworkName.BTC))
    }

    @Test
    fun rippleDoesNotSupportTokensOrNFTs() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertFalse(manager.supportsTokens(NetworkName.XRP))
        assertFalse(manager.supportsNFTs(NetworkName.XRP))
        assertFalse(manager.supportsFeeEstimation(NetworkName.XRP))
    }

    @Test
    fun midnightDoesNotSupportTokensOrNFTs() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertFalse(manager.supportsTokens(NetworkName.MIDNIGHT))
        assertFalse(manager.supportsNFTs(NetworkName.MIDNIGHT))
        assertFalse(manager.supportsFeeEstimation(NetworkName.MIDNIGHT))
    }

    @Test
    fun ethereumSupportsAllCapabilities() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)
        assertTrue(manager.supportsTokens(NetworkName.ETHEREUM))
        assertTrue(manager.supportsNFTs(NetworkName.ETHEREUM))
        assertTrue(manager.supportsFeeEstimation(NetworkName.ETHEREUM))
    }
}
