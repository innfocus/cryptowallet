package com.lybia.cryptowallet.property

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinAddressType
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Feature: crypto-wallet-module
 *
 * Property 16: Bitcoin address type prefix correctness
 * For any valid mnemonic, for any BitcoinAddressType, for any valid accountIndex (≥0):
 * - NATIVE_SEGWIT + MAINNET → prefix `bc1`
 * - NATIVE_SEGWIT + TESTNET → prefix `tb1`
 * - NESTED_SEGWIT + MAINNET → prefix `3`
 * - NESTED_SEGWIT + TESTNET → prefix `2`
 * - LEGACY + MAINNET → prefix `1`
 * - LEGACY + TESTNET → prefix `m` or `n`
 * **Validates: Requirements 36.1, 36.2, 36.3, 36.10**
 *
 * Property 17: Bitcoin multi-account uniqueness
 * For any valid mnemonic, for any BitcoinAddressType, for any two different account indices
 * (i ≠ j, i ≥ 0, j ≥ 0): getAddressByType(type, i) ≠ getAddressByType(type, j)
 * **Validates: Requirements 36.4, 36.5**
 */
class BitcoinAddressTypePropertyTest {

    private val testMnemonics = listOf(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "letter advice cage absurd amount doctor acoustic avoid letter advice cage above"
    )

    private val accountIndices = listOf(0, 1, 2)

    private lateinit var savedNetwork: Network

    @BeforeTest
    fun setup() {
        savedNetwork = Config.shared.getNetwork()
    }

    @AfterTest
    fun tearDown() {
        Config.shared.setNetwork(savedNetwork)
    }

    // ── Property 16: Bitcoin address type prefix correctness ─────────────

    @Test
    fun bitcoinAddressTypePrefixCorrectness() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(testMnemonics),
            Arb.of(
                BitcoinAddressType.NATIVE_SEGWIT,
                BitcoinAddressType.NESTED_SEGWIT,
                BitcoinAddressType.LEGACY
            ),
            Arb.of(Network.MAINNET, Network.TESTNET),
            Arb.element(accountIndices)
        ) { mnemonic, addressType, network, accountIndex ->
            Config.shared.setNetwork(network)
            try {
                val manager = BitcoinManager(mnemonic)
                val address = manager.getAddressByType(addressType, accountIndex)

                assertTrue(
                    address.isNotEmpty(),
                    "Address must not be empty for $addressType/$network/account=$accountIndex"
                )

                val (expectedPrefix, description) = when (addressType) {
                    BitcoinAddressType.NATIVE_SEGWIT -> when (network) {
                        Network.MAINNET -> "bc1" to "NATIVE_SEGWIT+MAINNET should start with 'bc1'"
                        Network.TESTNET -> "tb1" to "NATIVE_SEGWIT+TESTNET should start with 'tb1'"
                    }
                    BitcoinAddressType.NESTED_SEGWIT -> when (network) {
                        Network.MAINNET -> "3" to "NESTED_SEGWIT+MAINNET should start with '3'"
                        Network.TESTNET -> "2" to "NESTED_SEGWIT+TESTNET should start with '2'"
                    }
                    BitcoinAddressType.LEGACY -> when (network) {
                        Network.MAINNET -> "1" to "LEGACY+MAINNET should start with '1'"
                        Network.TESTNET -> "m_or_n" to "LEGACY+TESTNET should start with 'm' or 'n'"
                    }
                }

                if (expectedPrefix == "m_or_n") {
                    assertTrue(
                        address.startsWith("m") || address.startsWith("n"),
                        "$description, got: $address (account=$accountIndex)"
                    )
                } else {
                    assertTrue(
                        address.startsWith(expectedPrefix),
                        "$description, got: $address (account=$accountIndex)"
                    )
                }
            } finally {
                Config.shared.setNetwork(savedNetwork)
            }
        }
    }

    // ── Property 17: Bitcoin multi-account uniqueness ────────────────────

    @Test
    fun bitcoinMultiAccountUniqueness() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.element(testMnemonics),
            Arb.of(
                BitcoinAddressType.NATIVE_SEGWIT,
                BitcoinAddressType.NESTED_SEGWIT,
                BitcoinAddressType.LEGACY
            ),
            Arb.int(0..9),
            Arb.int(0..9)
        ) { mnemonic, addressType, i, j ->
            if (i != j) {
                Config.shared.setNetwork(Network.MAINNET)
                try {
                    val manager = BitcoinManager(mnemonic)
                    val addressI = manager.getAddressByType(addressType, i)
                    val addressJ = manager.getAddressByType(addressType, j)

                    assertNotEquals(
                        addressI,
                        addressJ,
                        "Addresses for $addressType account $i and $j must differ, both got: $addressI"
                    )
                } finally {
                    Config.shared.setNetwork(savedNetwork)
                }
            }
        }
    }
}
