package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Property 13: Network URL correctness**
 *
 * For any NetworkName × Network (MAINNET/TESTNET): CoinNetwork returns URL non-empty,
 * starts with "https://" for all endpoints used by that chain.
 *
 * **Validates: Requirements 7.6, 9.6**
 */
class NetworkUrlPropertyTest {

    // Networks that have Infura RPC URLs
    private val infuraNetworks = listOf(NetworkName.ETHEREUM, NetworkName.ARBITRUM, NetworkName.TON)

    // Networks that have Explorer endpoints
    private val explorerNetworks = listOf(NetworkName.ETHEREUM, NetworkName.ARBITRUM, NetworkName.CARDANO, NetworkName.MIDNIGHT, NetworkName.TON)

    @Test
    fun infuraRpcUrlsAreValidHttps() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(infuraNetworks),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { networkName, network ->
            // Set network for this test
            val savedNetwork = Config.shared.getNetwork()
            Config.shared.setNetwork(network)
            Config.shared.apiKeyInfura = "test-api-key"

            try {
                val coinNetwork = CoinNetwork(networkName)
                val url = coinNetwork.getInfuraRpcUrl()
                assertTrue(url.isNotEmpty(), "Infura RPC URL for $networkName/$network must not be empty")
                assertTrue(url.startsWith("https://"), "Infura RPC URL for $networkName/$network must start with https://, got: $url")
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    @Test
    fun explorerEndpointsAreValidHttps() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(explorerNetworks),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { networkName, network ->
            val savedNetwork = Config.shared.getNetwork()
            Config.shared.setNetwork(network)

            try {
                val coinNetwork = CoinNetwork(networkName)
                val url = coinNetwork.getExplorerEndpoint()
                assertTrue(url.isNotEmpty(), "Explorer URL for $networkName/$network must not be empty")
                assertTrue(url.startsWith("https://"), "Explorer URL for $networkName/$network must start with https://, got: $url")
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    @Test
    fun blockfrostUrlIsValidHttps() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { network ->
            val savedNetwork = Config.shared.getNetwork()
            Config.shared.setNetwork(network)

            try {
                val coinNetwork = CoinNetwork(NetworkName.CARDANO)
                val url = coinNetwork.getBlockfrostUrl()
                assertTrue(url.isNotEmpty(), "Blockfrost URL for $network must not be empty")
                assertTrue(url.startsWith("https://"), "Blockfrost URL for $network must start with https://, got: $url")
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    @Test
    fun midnightApiUrlIsValidHttps() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(Network.MAINNET, Network.TESTNET)
        ) { network ->
            val savedNetwork = Config.shared.getNetwork()
            Config.shared.setNetwork(network)

            try {
                val coinNetwork = CoinNetwork(NetworkName.MIDNIGHT)
                val url = coinNetwork.getMidnightApiUrl()
                assertTrue(url.isNotEmpty(), "Midnight API URL for $network must not be empty")
                assertTrue(url.startsWith("https://"), "Midnight API URL for $network must start with https://, got: $url")
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }
}
