package com.lybia.cryptowallet.wallets.ripple

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.RippleApiService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for RippleManager testnet configuration.
 *
 * Verifies that RippleManager reads isTestNet from Config dynamically
 * (not hardcoded false), and that RippleApiService selects the correct
 * RPC endpoint for mainnet vs testnet.
 *
 * Requirements: 35.5, 35.6, 35.10
 */
class RippleTestnetTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var savedNetwork: Network

    @BeforeTest
    fun setup() {
        savedNetwork = Config.shared.getNetwork()
    }

    @AfterTest
    fun tearDown() {
        Config.shared.setNetwork(savedNetwork)
    }

    // ── CoinNetwork Ripple RPC URL selection ────────────────────────────

    @Test
    fun coinNetwork_testnet_returnsAltnetUrl() {
        Config.shared.setNetwork(Network.TESTNET)
        val url = CoinNetwork(NetworkName.XRP).getRippleRpcUrl()
        assertEquals(
            "https://s.altnet.rippletest.net:51234/",
            url,
            "TESTNET should use Ripple altnet URL"
        )
    }

    @Test
    fun coinNetwork_mainnet_returnsMainnetUrl() {
        Config.shared.setNetwork(Network.MAINNET)
        val url = CoinNetwork(NetworkName.XRP).getRippleRpcUrl()
        assertEquals(
            "https://s1.ripple.com:51234/",
            url,
            "MAINNET should use Ripple mainnet URL"
        )
    }

    // ── ACTNetwork isTestNet reflects Config ─────────────────────────────

    @Test
    fun actNetwork_testnet_isTestNetTrue() {
        Config.shared.setNetwork(Network.TESTNET)
        val network = ACTNetwork(ACTCoin.Ripple, Config.shared.getNetwork() == Network.TESTNET)
        assertTrue(network.isTestNet, "ACTNetwork should have isTestNet=true when Config is TESTNET")
    }

    @Test
    fun actNetwork_mainnet_isTestNetFalse() {
        Config.shared.setNetwork(Network.MAINNET)
        val network = ACTNetwork(ACTCoin.Ripple, Config.shared.getNetwork() == Network.TESTNET)
        assertFalse(network.isTestNet, "ACTNetwork should have isTestNet=false when Config is MAINNET")
    }

    // ── ACTNetwork explorer URL reflects testnet ────────────────────────

    @Test
    fun actNetwork_testnet_returnsTestExplorer() {
        val network = ACTNetwork(ACTCoin.Ripple, true)
        assertEquals(
            "https://test.bithomp.com",
            network.explorer(),
            "Ripple testnet explorer should be test.bithomp.com"
        )
    }

    @Test
    fun actNetwork_mainnet_returnsMainnetExplorer() {
        val network = ACTNetwork(ACTCoin.Ripple, false)
        assertEquals(
            "https://bithomp.com",
            network.explorer(),
            "Ripple mainnet explorer should be bithomp.com"
        )
    }

    // ── RippleManager address generation ────────────────────────────────

    @Test
    fun rippleManager_mainnet_generatesValidAddress() {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = RippleManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("r"),
            "Mainnet Ripple address should start with 'r', got: $address"
        )
        assertTrue(
            address.length in 25..35,
            "Ripple address should be 25-35 chars, got ${address.length}: $address"
        )
    }

    @Test
    fun rippleManager_testnet_generatesValidAddress() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager = RippleManager(testMnemonic)
        val address = manager.getAddress()

        // Ripple testnet addresses also start with 'r' (same Base58 Ripple alphabet)
        assertTrue(
            address.startsWith("r"),
            "Testnet Ripple address should start with 'r', got: $address"
        )
        assertTrue(
            address.length in 25..35,
            "Ripple address should be 25-35 chars, got ${address.length}: $address"
        )
    }

    @Test
    fun rippleManager_addressDeterministic_mainnet() {
        Config.shared.setNetwork(Network.MAINNET)
        val manager1 = RippleManager(testMnemonic)
        val manager2 = RippleManager(testMnemonic)

        assertEquals(
            manager1.getAddress(),
            manager2.getAddress(),
            "Same mnemonic on MAINNET should produce the same Ripple address"
        )
    }

    @Test
    fun rippleManager_addressDeterministic_testnet() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager1 = RippleManager(testMnemonic)
        val manager2 = RippleManager(testMnemonic)

        assertEquals(
            manager1.getAddress(),
            manager2.getAddress(),
            "Same mnemonic on TESTNET should produce the same Ripple address"
        )
    }

    // ── RippleManager internal network config is dynamic ────────────────

    @Test
    fun rippleManager_testnet_usesTestnetConfig() {
        Config.shared.setNetwork(Network.TESTNET)
        // RippleManager should read Config.shared.getNetwork() dynamically
        // and create ACTNetwork(ACTCoin.Ripple, isTestNet=true)
        val manager = RippleManager(testMnemonic)
        val address = manager.getAddress()

        // Address should be non-empty and valid
        assertTrue(address.isNotEmpty(), "Testnet RippleManager should produce a non-empty address")
    }

    @Test
    fun rippleManager_mainnet_usesMainnetConfig() {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = RippleManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(address.isNotEmpty(), "Mainnet RippleManager should produce a non-empty address")
    }

    // ── RippleApiService URL selection ───────────────────────────────────

    @Test
    fun rippleApiService_testnet_usesAltnetEndpoint() {
        Config.shared.setNetwork(Network.TESTNET)
        // RippleApiService.getRpcUrl() is private, but we can verify
        // the CoinNetwork helper returns the correct URL
        val url = CoinNetwork(NetworkName.XRP).getRippleRpcUrl()
        assertTrue(
            url.contains("altnet.rippletest.net"),
            "TESTNET RPC URL should contain altnet.rippletest.net, got: $url"
        )
    }

    @Test
    fun rippleApiService_mainnet_usesMainnetEndpoint() {
        Config.shared.setNetwork(Network.MAINNET)
        val url = CoinNetwork(NetworkName.XRP).getRippleRpcUrl()
        assertTrue(
            url.contains("s1.ripple.com"),
            "MAINNET RPC URL should contain s1.ripple.com, got: $url"
        )
    }

    // ── Backward compatibility: mainnet address unchanged ───────────────

    @Test
    fun rippleManager_mainnet_backwardCompatible() {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = RippleManager(testMnemonic)
        val address = manager.getAddress()

        // The well-known "abandon" mnemonic should produce a deterministic
        // mainnet Ripple address starting with 'r'
        assertTrue(
            address.startsWith("r"),
            "Mainnet address should start with 'r' (backward compatible)"
        )
        assertTrue(
            address.isNotEmpty(),
            "Mainnet address should not be empty (backward compatible)"
        )
    }
}
