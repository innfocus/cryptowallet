package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.base.IBridgeManager
import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.bridge.CardanoMidnightBridge
import com.lybia.cryptowallet.wallets.bridge.EthereumArbitrumBridge
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property-based tests for ChainManagerFactory staking and bridge methods.
 *
 * Feature: staking-bridge
 */
class ChainManagerFactoryTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val stakingChains: Set<NetworkName> = setOf(
        NetworkName.CARDANO,
        NetworkName.TON
    )

    private val supportedBridgePairs: Set<Pair<NetworkName, NetworkName>> = setOf(
        NetworkName.CARDANO to NetworkName.MIDNIGHT,
        NetworkName.MIDNIGHT to NetworkName.CARDANO,
        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
        NetworkName.ARBITRUM to NetworkName.ETHEREUM
    )

    // ── Property 14: Factory createStakingManager trả về đúng kết quả ──

    // Feature: staking-bridge, Property 14: Factory createStakingManager trả về đúng kết quả
    // **Validates: Requirements 15.1, 15.3**
    @Test
    fun createStakingManagerReturnsCorrectResultForAllChains() = runTest {
        checkAll(100, Arb.enum<NetworkName>()) { coin ->
            val manager = ChainManagerFactory.createStakingManager(coin, testMnemonic)
            if (coin in stakingChains) {
                assertNotNull(manager,
                    "createStakingManager($coin) should return non-null IStakingManager")
                assertIs<IStakingManager>(manager,
                    "createStakingManager($coin) should return IStakingManager")
            } else {
                assertNull(manager,
                    "createStakingManager($coin) should return null for unsupported chain")
            }
        }
    }

    // ── Property 15: Factory createBridgeManager trả về đúng kết quả ──

    // Feature: staking-bridge, Property 15: Factory createBridgeManager trả về đúng kết quả
    // **Validates: Requirements 15.2, 15.4**
    @Test
    fun createBridgeManagerReturnsCorrectResultForAllPairs() = runTest {
        checkAll(100, Arb.enum<NetworkName>(), Arb.enum<NetworkName>()) { from, to ->
            val manager = ChainManagerFactory.createBridgeManager(from, to, testMnemonic)
            val pair = from to to
            if (pair in supportedBridgePairs) {
                assertNotNull(manager,
                    "createBridgeManager($from, $to) should return non-null IBridgeManager")
                assertIs<IBridgeManager>(manager,
                    "createBridgeManager($from, $to) should return IBridgeManager")
                when {
                    pair in setOf(
                        NetworkName.CARDANO to NetworkName.MIDNIGHT,
                        NetworkName.MIDNIGHT to NetworkName.CARDANO
                    ) -> assertIs<CardanoMidnightBridge>(manager,
                        "createBridgeManager($from, $to) should return CardanoMidnightBridge")
                    pair in setOf(
                        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
                        NetworkName.ARBITRUM to NetworkName.ETHEREUM
                    ) -> assertIs<EthereumArbitrumBridge>(manager,
                        "createBridgeManager($from, $to) should return EthereumArbitrumBridge")
                }
            } else {
                assertNull(manager,
                    "createBridgeManager($from, $to) should return null for unsupported pair")
            }
        }
    }
}
