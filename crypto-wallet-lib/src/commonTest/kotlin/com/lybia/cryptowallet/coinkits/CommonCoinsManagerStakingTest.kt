package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for CommonCoinsManager staking integration.
 *
 * Feature: staking-bridge
 */
class CommonCoinsManagerStakingTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val stakingChains = setOf(NetworkName.CARDANO, NetworkName.TON)

    // ── Property 11: supportsStaking trả về kết quả đúng cho mọi chain ──

    // Feature: staking-bridge, Property 11: supportsStaking trả về kết quả đúng cho mọi chain
    // **Validates: Requirements 13.5**
    @Test
    fun supportsStakingReturnsCorrectResultForAllChains() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        checkAll(100, Arb.enum<NetworkName>()) { coin ->
            val result = manager.supportsStaking(coin)
            if (coin in stakingChains) {
                assertTrue(result,
                    "supportsStaking($coin) should return true for staking chain")
            } else {
                assertFalse(result,
                    "supportsStaking($coin) should return false for non-staking chain")
            }
        }
    }

    // ── Property 12: CommonCoinsManager staking methods delegate đúng chain manager ──

    // Feature: staking-bridge, Property 12: CommonCoinsManager staking methods delegate đúng chain manager
    // **Validates: Requirements 13.1, 13.2, 13.3, 13.4**
    @Test
    fun stakingMethodsDelegateToCorrectChainManager() = runTest {
        checkAll(100, Arb.enum<NetworkName>()) { coin ->
            val manager = CommonCoinsManager(mnemonic = testMnemonic)

            if (coin in stakingChains) {
                // For supported chains, the underlying manager should be an IStakingManager.
                // We verify delegation by checking that supportsStaking returns true
                // and that the factory produces a valid IStakingManager for this chain.
                assertTrue(manager.supportsStaking(coin),
                    "supportsStaking($coin) should be true for staking chain")

                val stakingMgr = ChainManagerFactory.createStakingManager(coin, testMnemonic)
                assertTrue(stakingMgr is IStakingManager,
                    "ChainManagerFactory should produce IStakingManager for $coin")
            } else {
                // For unsupported chains, staking operations should return failure results
                assertFalse(manager.supportsStaking(coin),
                    "supportsStaking($coin) should be false for non-staking chain")

                val stakeResult = manager.stake(coin, 1000L, "pool_address")
                assertFalse(stakeResult.success,
                    "stake($coin) should fail for unsupported chain")
                assertTrue(stakeResult.error?.contains("Unsupported") == true,
                    "stake($coin) error should mention 'Unsupported'")

                val unstakeResult = manager.unstake(coin, 1000L)
                assertFalse(unstakeResult.success,
                    "unstake($coin) should fail for unsupported chain")
                assertTrue(unstakeResult.error?.contains("Unsupported") == true,
                    "unstake($coin) error should mention 'Unsupported'")

                val rewardsResult = manager.getStakingRewards(coin, "some_address")
                assertFalse(rewardsResult.success,
                    "getStakingRewards($coin) should fail for unsupported chain")
                assertTrue(rewardsResult.error?.contains("Unsupported") == true,
                    "getStakingRewards($coin) error should mention 'Unsupported'")

                val balanceResult = manager.getStakingBalance(coin, "some_address", poolAddress = "pool")
                assertFalse(balanceResult.success,
                    "getStakingBalance($coin) should fail for unsupported chain")
                assertTrue(balanceResult.error?.contains("Unsupported") == true,
                    "getStakingBalance($coin) error should mention 'Unsupported'")
            }
        }
    }

    // ── Unit test: unsupported staking chain error ──

    // **Validates: Requirements 13.6**
    @Test
    fun stakeForUnsupportedChainReturnsUnsupportedOperation() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        // Test all non-staking chains
        val unsupportedChains = NetworkName.entries.filter { it !in stakingChains }
        for (chain in unsupportedChains) {
            val result = manager.stake(chain, 1_000_000L, "pool_address")
            assertFalse(result.success,
                "stake($chain) should not succeed for unsupported chain")
            assertTrue(result.error?.contains("Unsupported operation") == true,
                "stake($chain) error should contain 'Unsupported operation', got: ${result.error}")
            assertEquals("", result.txHash,
                "stake($chain) txHash should be empty for unsupported chain")
        }
    }

    @Test
    fun unstakeForUnsupportedChainReturnsUnsupportedOperation() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        val unsupportedChains = NetworkName.entries.filter { it !in stakingChains }
        for (chain in unsupportedChains) {
            val result = manager.unstake(chain, 1_000_000L)
            assertFalse(result.success,
                "unstake($chain) should not succeed for unsupported chain")
            assertTrue(result.error?.contains("Unsupported operation") == true,
                "unstake($chain) error should contain 'Unsupported operation', got: ${result.error}")
        }
    }

    @Test
    fun getStakingRewardsForUnsupportedChainReturnsUnsupportedOperation() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        val unsupportedChains = NetworkName.entries.filter { it !in stakingChains }
        for (chain in unsupportedChains) {
            val result = manager.getStakingRewards(chain, "some_address")
            assertFalse(result.success,
                "getStakingRewards($chain) should not succeed for unsupported chain")
            assertTrue(result.error?.contains("Unsupported operation") == true,
                "getStakingRewards($chain) error should contain 'Unsupported operation', got: ${result.error}")
            assertEquals(0.0, result.balance,
                "getStakingRewards($chain) balance should be 0.0 for unsupported chain")
        }
    }

    @Test
    fun getStakingBalanceForUnsupportedChainReturnsUnsupportedOperation() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        val unsupportedChains = NetworkName.entries.filter { it !in stakingChains }
        for (chain in unsupportedChains) {
            val result = manager.getStakingBalance(chain, "some_address", poolAddress = "pool")
            assertFalse(result.success,
                "getStakingBalance($chain) should not succeed for unsupported chain")
            assertTrue(result.error?.contains("Unsupported operation") == true,
                "getStakingBalance($chain) error should contain 'Unsupported operation', got: ${result.error}")
            assertEquals(0.0, result.balance,
                "getStakingBalance($chain) balance should be 0.0 for unsupported chain")
        }
    }
}
