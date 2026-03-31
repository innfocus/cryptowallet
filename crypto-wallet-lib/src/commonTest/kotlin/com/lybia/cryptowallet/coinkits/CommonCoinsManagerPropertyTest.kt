package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Property 14: CommonCoinsManager correct delegation**
 *
 * For any supported NetworkName: getAddress(coin) delegates to the correct
 * chain manager and returns a non-empty string.
 *
 * **Validates: Requirements 10.2, 10.3, 10.4**
 */
class CommonCoinsManagerPropertyTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    // Networks that can produce an address without external API calls
    private val supportedNetworks = listOf(
        NetworkName.CARDANO,
        NetworkName.MIDNIGHT,
        NetworkName.TON,
        NetworkName.XRP
    )

    @Test
    fun getAddressDelegatesToCorrectManagerAndReturnsNonEmpty() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(supportedNetworks)
        ) { network ->
            val address = manager.getAddress(network)
            assertTrue(
                address.isNotEmpty(),
                "getAddress($network) should return non-empty string, got empty"
            )
        }
    }

    @Test
    fun getAddressIsConsistentForSameNetwork() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(supportedNetworks)
        ) { network ->
            val address1 = manager.getAddress(network)
            val address2 = manager.getAddress(network)
            assertTrue(
                address1 == address2,
                "getAddress($network) should return same address on repeated calls"
            )
        }
    }

    @Test
    fun differentNetworksProduceDifferentAddresses() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        // Collect addresses for all supported networks
        val addresses = supportedNetworks.map { manager.getAddress(it) }

        // All addresses should be distinct
        val distinctAddresses = addresses.toSet()
        assertTrue(
            distinctAddresses.size == addresses.size,
            "Different networks should produce different addresses"
        )
    }
}
