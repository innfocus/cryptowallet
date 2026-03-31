package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.bridge.BridgeManagerFactory
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Property 11: Capability matrix consistency**
 *
 * supportsTokens/NFTs/FeeEstimation/Staking/Bridge trả về đúng cho mọi NetworkName.
 * ChainManagerFactory.createStakingManager trả về null cho chain không hỗ trợ.
 * ChainManagerFactory.createWalletManager trả về non-null cho mọi NetworkName.
 *
 * **Validates: Requirements 5.7, 6.1, 6.7, 6.8, 7.7, 7.8, 34.1-34.8**
 */
// Feature: crypto-wallet-module, Property 11: Capability matrix consistency
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

    private val stakingCapableNetworks = setOf(
        NetworkName.CARDANO,
        NetworkName.TON
    )

    private val supportedBridgePairs = setOf(
        NetworkName.CARDANO to NetworkName.MIDNIGHT,
        NetworkName.MIDNIGHT to NetworkName.CARDANO,
        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
        NetworkName.ARBITRUM to NetworkName.ETHEREUM
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

    // ── Staking capability ──────────────────────────────────────────────

    @Test
    fun supportsStakingMatchesExpectedCapabilities() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(allNetworks)
        ) { network ->
            val expected = network in stakingCapableNetworks
            assertEquals(
                expected,
                manager.supportsStaking(network),
                "supportsStaking($network) should be $expected"
            )
        }
    }

    // ── Bridge capability ───────────────────────────────────────────────

    @Test
    fun supportsBridgeMatchesExpectedCapabilities() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.enum<NetworkName>(),
            Arb.enum<NetworkName>()
        ) { from, to ->
            val expected = (from to to) in supportedBridgePairs
            assertEquals(
                expected,
                manager.supportsBridge(from, to),
                "supportsBridge($from, $to) should be $expected"
            )
        }
    }

    // ── ChainManagerFactory: createWalletManager non-null for all ────────

    @Test
    fun createWalletManagerReturnsNonNullForAllNetworks() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.enum<NetworkName>()
        ) { coin ->
            val manager = ChainManagerFactory.createWalletManager(coin, testMnemonic)
            assertNotNull(
                manager,
                "createWalletManager($coin) should return non-null"
            )
        }
    }

    // ── ChainManagerFactory: createStakingManager null for unsupported ───

    @Test
    fun createStakingManagerReturnsNullForUnsupportedChains() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.enum<NetworkName>()
        ) { coin ->
            val manager = ChainManagerFactory.createStakingManager(coin, testMnemonic)
            if (coin in stakingCapableNetworks) {
                assertNotNull(
                    manager,
                    "createStakingManager($coin) should return non-null for staking chain"
                )
                assertTrue(
                    manager is IStakingManager,
                    "createStakingManager($coin) should return IStakingManager"
                )
            } else {
                assertNull(
                    manager,
                    "createStakingManager($coin) should return null for non-staking chain"
                )
            }
        }
    }
}
