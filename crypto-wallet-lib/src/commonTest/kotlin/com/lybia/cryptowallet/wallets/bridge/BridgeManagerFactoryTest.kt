package com.lybia.cryptowallet.wallets.bridge

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Property-based tests for BridgeManagerFactory.
 *
 * Feature: staking-bridge
 */
class BridgeManagerFactoryTest {

    private val supportedPairs: Set<Pair<NetworkName, NetworkName>> = setOf(
        NetworkName.CARDANO to NetworkName.MIDNIGHT,
        NetworkName.MIDNIGHT to NetworkName.CARDANO,
        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
        NetworkName.ARBITRUM to NetworkName.ETHEREUM
    )

    // ── Property 9: supportsBridge trả về kết quả đúng cho mọi cặp chain ──

    // Feature: staking-bridge, Property 9: supportsBridge trả về kết quả đúng cho mọi cặp chain
    // **Validates: Requirements 12.2, 12.3, 14.3**
    @Test
    fun supportsBridgeReturnsCorrectResultForAllChainPairs() = runTest {
        checkAll(100, Arb.enum<NetworkName>(), Arb.enum<NetworkName>()) { from, to ->
            val expected = (from to to) in supportedPairs
            val actual = BridgeManagerFactory.supportsBridge(from, to)
            assertEquals(
                expected, actual,
                "supportsBridge($from, $to) should be $expected but was $actual"
            )
        }
    }

    // Feature: staking-bridge, Property 9: supportsBridge trả về kết quả đúng cho mọi cặp chain
    // **Validates: Requirements 12.2, 12.3, 14.3**
    @Test
    fun supportsBridgeReturnsTrueForAllSupportedPairs() {
        supportedPairs.forEach { (from, to) ->
            assertTrue(
                BridgeManagerFactory.supportsBridge(from, to),
                "supportsBridge($from, $to) should be true"
            )
        }
    }

    // Feature: staking-bridge, Property 9: supportsBridge trả về kết quả đúng cho mọi cặp chain
    // **Validates: Requirements 12.2, 12.3, 14.3**
    @Test
    fun supportsBridgeReturnsFalseForUnsupportedPairs() {
        val unsupportedPairs = NetworkName.entries.flatMap { from ->
            NetworkName.entries.map { to -> from to to }
        }.filter { it !in supportedPairs }

        unsupportedPairs.forEach { (from, to) ->
            assertFalse(
                BridgeManagerFactory.supportsBridge(from, to),
                "supportsBridge($from, $to) should be false"
            )
        }
    }

    // ── Property 10: BridgeManagerFactory trả về đúng implementation type ──

    // Feature: staking-bridge, Property 10: BridgeManagerFactory trả về đúng implementation type
    // **Validates: Requirements 12.4**
    @Test
    fun createBridgeManagerReturnsCorrectTypeForAllPairs() = runTest {
        val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        checkAll(100, Arb.enum<NetworkName>(), Arb.enum<NetworkName>()) { from, to ->
            val manager = BridgeManagerFactory.createBridgeManager(from, to, testMnemonic)

            when {
                (from to to) in setOf(
                    NetworkName.CARDANO to NetworkName.MIDNIGHT,
                    NetworkName.MIDNIGHT to NetworkName.CARDANO
                ) -> assertIs<CardanoMidnightBridge>(manager,
                    "createBridgeManager($from, $to) should return CardanoMidnightBridge")

                (from to to) in setOf(
                    NetworkName.ETHEREUM to NetworkName.ARBITRUM,
                    NetworkName.ARBITRUM to NetworkName.ETHEREUM
                ) -> assertIs<EthereumArbitrumBridge>(manager,
                    "createBridgeManager($from, $to) should return EthereumArbitrumBridge")

                else -> assertNull(manager,
                    "createBridgeManager($from, $to) should return null for unsupported pair")
            }
        }
    }

    // Feature: staking-bridge, Property 10: BridgeManagerFactory trả về đúng implementation type
    // **Validates: Requirements 12.4**
    @Test
    fun createBridgeManagerReturnsNullForUnsupportedPairs() {
        val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        val unsupportedPairs = NetworkName.entries.flatMap { from ->
            NetworkName.entries.map { to -> from to to }
        }.filter { it !in supportedPairs }

        unsupportedPairs.forEach { (from, to) ->
            val manager = BridgeManagerFactory.createBridgeManager(from, to, testMnemonic)
            assertNull(manager, "createBridgeManager($from, $to) should return null")
        }
    }
}
