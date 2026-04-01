package com.lybia.cryptowallet.property

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.cardano.CardanoManager
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager
import com.lybia.cryptowallet.wallets.ripple.RippleManager
import com.lybia.cryptowallet.wallets.ton.TonManager
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Feature: crypto-wallet-module, Property 14: Testnet address và URL correctness
 *
 * For any valid mnemonic, when TESTNET:
 * - Bitcoin address prefix `tb1` (Native SegWit)
 * - Cardano address prefix `addr_test`
 * - CoinNetwork returns non-empty URL for 5 main chains
 * - CardanoManager uses Blockfrost preprod URL
 * - RippleManager uses testnet endpoint
 *
 * **Validates: Requirements 35.1, 35.2, 35.3, 35.4, 35.5, 35.6, 35.7, 35.8**
 */
class TestnetPropertyTest {

    private val testMnemonics = listOf(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "letter advice cage absurd amount doctor acoustic avoid letter advice cage above"
    )

    /** Chains that have meaningful testnet URL endpoints. */
    private val mainChains = listOf(
        NetworkName.BTC,
        NetworkName.ETHEREUM,
        NetworkName.CARDANO,
        NetworkName.XRP,
        NetworkName.TON
    )

    private lateinit var savedNetwork: Network

    @BeforeTest
    fun setup() {
        savedNetwork = Config.shared.getNetwork()
    }

    @AfterTest
    fun tearDown() {
        Config.shared.setNetwork(savedNetwork)
    }


    // ── Property 14a: Bitcoin testnet address has prefix tb1 ────────────

    @Test
    fun bitcoinTestnetAddressHasTb1Prefix() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(testMnemonics),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { mnemonic, network ->
            Config.shared.setNetwork(network)
            try {
                val manager = BitcoinManager(mnemonic)
                val address = manager.getAddress()

                assertTrue(address.isNotEmpty(), "Bitcoin address must not be empty ($network)")
                when (network) {
                    Network.TESTNET -> assertTrue(
                        address.startsWith("tb1"),
                        "Bitcoin TESTNET address should start with 'tb1', got: $address"
                    )
                    Network.MAINNET -> assertTrue(
                        address.startsWith("bc1"),
                        "Bitcoin MAINNET address should start with 'bc1', got: $address"
                    )
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 14b: Cardano testnet address has prefix addr_test ──────

    @Test
    fun cardanoTestnetAddressHasAddrTestPrefix() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(testMnemonics),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { mnemonic, network ->
            Config.shared.setNetwork(network)
            try {
                val manager = CardanoManager(mnemonic)
                val address = manager.getAddress()

                assertTrue(address.isNotEmpty(), "Cardano address must not be empty ($network)")
                when (network) {
                    Network.TESTNET -> assertTrue(
                        address.startsWith("addr_test"),
                        "Cardano TESTNET address should start with 'addr_test', got: $address"
                    )
                    Network.MAINNET -> assertTrue(
                        address.startsWith("addr1"),
                        "Cardano MAINNET address should start with 'addr1', got: $address"
                    )
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 14c: CoinNetwork returns non-empty URL for 5 chains on TESTNET ──

    @Test
    fun coinNetworkTestnetUrlsAreNonEmpty() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(mainChains),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { networkName, network ->
            Config.shared.setNetwork(network)
            Config.shared.apiKeyInfura = "test-key"
            Config.shared.apiKeyToncenter = "test-key"
            try {
                val coinNetwork = CoinNetwork(networkName)
                // Each chain should have at least one non-empty URL
                val url = when (networkName) {
                    NetworkName.BTC -> "" // Bitcoin uses BitcoinApiService directly, not CoinNetwork URL
                    NetworkName.ETHEREUM -> coinNetwork.getInfuraRpcUrl()
                    NetworkName.CARDANO -> coinNetwork.getBlockfrostUrl()
                    NetworkName.XRP -> coinNetwork.getRippleRpcUrl()
                    NetworkName.TON -> coinNetwork.getInfuraRpcUrl() // TON uses toncenter via getInfuraRpcUrl
                    else -> ""
                }
                if (networkName != NetworkName.BTC) {
                    assertTrue(
                        url.isNotEmpty(),
                        "CoinNetwork URL for $networkName/$network must not be empty"
                    )
                    assertTrue(
                        url.startsWith("https://"),
                        "CoinNetwork URL for $networkName/$network must start with https://, got: $url"
                    )
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 14d: CardanoManager uses Blockfrost preprod URL on TESTNET ──

    @Test
    fun cardanoManagerUsesBlockfrostPreprodOnTestnet() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { network ->
            Config.shared.setNetwork(network)
            try {
                val url = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
                when (network) {
                    Network.TESTNET -> {
                        assertTrue(
                            url.contains("cardano-preprod"),
                            "Cardano TESTNET should use preprod Blockfrost URL, got: $url"
                        )
                    }
                    Network.MAINNET -> {
                        assertTrue(
                            url.contains("cardano-mainnet"),
                            "Cardano MAINNET should use mainnet Blockfrost URL, got: $url"
                        )
                    }
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 14e: RippleManager uses testnet endpoint on TESTNET ────

    @Test
    fun rippleManagerUsesTestnetEndpointOnTestnet() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { network ->
            Config.shared.setNetwork(network)
            try {
                val url = CoinNetwork(NetworkName.XRP).getRippleRpcUrl()
                when (network) {
                    Network.TESTNET -> {
                        assertTrue(
                            url.contains("altnet.rippletest.net"),
                            "Ripple TESTNET should use altnet endpoint, got: $url"
                        )
                    }
                    Network.MAINNET -> {
                        assertTrue(
                            url.contains("s1.ripple.com"),
                            "Ripple MAINNET should use mainnet endpoint, got: $url"
                        )
                    }
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 14f: Ripple address valid on both networks ─────────────

    @Test
    fun rippleAddressValidOnBothNetworks() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(testMnemonics),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { mnemonic, network ->
            Config.shared.setNetwork(network)
            try {
                val manager = RippleManager(mnemonic)
                val address = manager.getAddress()

                assertTrue(address.isNotEmpty(), "Ripple address must not be empty ($network)")
                assertTrue(
                    address.startsWith("r"),
                    "Ripple address should start with 'r' on $network, got: $address"
                )
                assertTrue(
                    address.length in 25..35,
                    "Ripple address length should be 25-35 on $network, got ${address.length}"
                )
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }
}
