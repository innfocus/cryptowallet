package com.lybia.cryptowallet.enums

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 3: ACTNetwork method equivalence
 *
 * For any ACTCoin x isTestNet combination, ACTNetwork methods return
 * the same values as the androidMain original.
 *
 * **Validates: Requirements 2.6**
 */
class ACTNetworkPropertyTest {

    // Expected coinType values from androidMain
    private fun expectedCoinType(coin: ACTCoin, isTestNet: Boolean): Int = when (coin) {
        ACTCoin.Bitcoin -> if (isTestNet) 1 else 0
        ACTCoin.Ethereum -> 60
        ACTCoin.Cardano -> 1815
        ACTCoin.Ripple -> 144
        ACTCoin.Centrality -> 392
        ACTCoin.XCoin -> 868
        ACTCoin.TON -> 607
        ACTCoin.Midnight -> 1815
    }

    private fun expectedDerivationPath(coin: ACTCoin, isTestNet: Boolean): String = when {
        coin == ACTCoin.Bitcoin && isTestNet -> "${expectedCoinType(coin, isTestNet)}'"
        else -> "44'/${expectedCoinType(coin, isTestNet)}'/0'"
    }

    private fun expectedPrivateKeyPrefix(isTestNet: Boolean): Int = 0x0488ADE4.toInt()

    private fun expectedPublicKeyPrefix(isTestNet: Boolean): Int =
        if (isTestNet) 0x043587cf else 0x0488b21e

    private fun expectedPubkeyhash(coin: ACTCoin, isTestNet: Boolean): Byte = when {
        !isTestNet -> 0x00
        coin == ACTCoin.Bitcoin -> 0x6f
        else -> 0x00
    }

    private fun expectedAddressPrefix(coin: ACTCoin): String =
        if (coin == ACTCoin.Ethereum) "0x" else ""

    private fun expectedExplorer(coin: ACTCoin, isTestNet: Boolean): String = when (isTestNet) {
        false -> when (coin) {
            ACTCoin.Bitcoin -> "https://www.blockchain.com/btc"
            ACTCoin.Ethereum -> "https://etherscan.io"
            ACTCoin.Cardano -> "https://cardanoexplorer.com"
            ACTCoin.Ripple -> "https://bithomp.com"
            ACTCoin.Centrality -> "https://uncoverexplorer.com"
            ACTCoin.XCoin -> "Explorer XCoin"
            ACTCoin.TON -> "https://tonscan.org"
            ACTCoin.Midnight -> "https://explorer.midnight.network"
        }
        true -> when (coin) {
            ACTCoin.Bitcoin -> "https://testnet.blockchain.info"
            ACTCoin.Ethereum -> "https://goerli.etherscan.io"
            ACTCoin.Cardano -> "https://cardanoexplorer.com"
            ACTCoin.Ripple -> "https://test.bithomp.com"
            ACTCoin.Centrality -> "https://uncoverexplorer.com"
            ACTCoin.XCoin -> "Explorer XCoin"
            ACTCoin.TON -> "https://testnet.tonscan.org"
            ACTCoin.Midnight -> "https://explorer.testnet.midnight.network"
        }
    }

    private fun expectedExplorerForTX(coin: ACTCoin, isTestNet: Boolean): String = when (coin) {
        ACTCoin.Centrality -> "https://uncoverexplorer.com/extrinsic/"
        else -> expectedExplorer(coin, isTestNet) + if (coin == ACTCoin.Ripple) "/explorer/" else "/tx/"
    }

    private fun expectedDerivateIdxMax(coin: ACTCoin, chain: Change): Int = when (coin) {
        ACTCoin.Bitcoin -> if (chain == Change.Internal) 10 else 100
        ACTCoin.Cardano -> if (chain == Change.Internal) 0 else 50
        else -> if (chain == Change.Internal) 0 else 1
    }

    @Test
    fun actNetworkMethodEquivalence() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.enum<ACTCoin>(),
            Arb.boolean()
        ) { coin, isTestNet ->
            val network = ACTNetwork(coin, isTestNet)

            assertEquals(expectedCoinType(coin, isTestNet), network.coinType(), "coinType for $coin/$isTestNet")
            assertEquals(expectedDerivationPath(coin, isTestNet), network.derivationPath(), "derivationPath for $coin/$isTestNet")
            assertEquals(expectedPrivateKeyPrefix(isTestNet), network.privateKeyPrefix(), "privateKeyPrefix for $coin/$isTestNet")
            assertEquals(expectedPublicKeyPrefix(isTestNet), network.publicKeyPrefix(), "publicKeyPrefix for $coin/$isTestNet")
            assertEquals(expectedPubkeyhash(coin, isTestNet), network.pubkeyhash(), "pubkeyhash for $coin/$isTestNet")
            assertEquals(expectedAddressPrefix(coin), network.addressPrefix(), "addressPrefix for $coin/$isTestNet")
            assertEquals(expectedExplorer(coin, isTestNet), network.explorer(), "explorer for $coin/$isTestNet")
            assertEquals(expectedExplorerForTX(coin, isTestNet), network.explorerForTX(), "explorerForTX for $coin/$isTestNet")

            // Test derivateIdxMax for both chain types
            for (chain in Change.entries) {
                assertEquals(
                    expectedDerivateIdxMax(coin, chain),
                    network.derivateIdxMax(chain),
                    "derivateIdxMax for $coin/$chain"
                )
            }
        }
    }
}
