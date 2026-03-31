package com.lybia.cryptowallet.errors

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-based tests for Staking and Bridge error models.
 *
 * Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
 * **Validates: Requirements 16.3, 16.4**
 */
class ErrorModelsPropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    private val arbNonEmptyString: Arb<String> = Arb.string(1..100, Codepoint.alphanumeric())

    private val arbPositiveDouble: Arb<Double> = Arb.double(0.001..1_000_000.0)
        .filter { it.isFinite() }

    private val arbNetworkName: Arb<NetworkName> = Arb.enum<NetworkName>()

    // ── StakingError property tests ─────────────────────────────────

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.3**
    @Test
    fun poolNotFoundErrorContainsPoolAddress() = runTest {
        checkAll(100, arbNonEmptyString) { poolAddress ->
            val error = StakingError.PoolNotFound(poolAddress)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(poolAddress),
                "Message should contain pool address '$poolAddress', got: '${error.message}'"
            )
            assertTrue(error is Exception, "StakingError should be an Exception")
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.3**
    @Test
    fun insufficientStakingBalanceErrorContainsAmounts() = runTest {
        checkAll(100, arbPositiveDouble, arbPositiveDouble) { available, required ->
            val error = StakingError.InsufficientStakingBalance(available, required)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(available.toString()),
                "Message should contain available amount '$available', got: '${error.message}'"
            )
            assertTrue(
                error.message.contains(required.toString()),
                "Message should contain required amount '$required', got: '${error.message}'"
            )
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.3**
    @Test
    fun delegationAlreadyActiveErrorContainsPoolInfo() = runTest {
        checkAll(100, arbNonEmptyString) { currentPool ->
            val error = StakingError.DelegationAlreadyActive(currentPool)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(currentPool),
                "Message should contain current pool '$currentPool', got: '${error.message}'"
            )
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.3**
    @Test
    fun noDelegationActiveErrorContainsStakingAddress() = runTest {
        checkAll(100, arbNonEmptyString) { stakingAddress ->
            val error = StakingError.NoDelegationActive(stakingAddress)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(stakingAddress),
                "Message should contain staking address '$stakingAddress', got: '${error.message}'"
            )
        }
    }

    // ── BridgeError property tests ──────────────────────────────────

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.4**
    @Test
    fun unsupportedBridgePairErrorContainsChainNames() = runTest {
        checkAll(100, arbNetworkName, arbNetworkName) { fromChain, toChain ->
            val error = BridgeError.UnsupportedBridgePair(fromChain, toChain)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(fromChain.toString()),
                "Message should contain fromChain '$fromChain', got: '${error.message}'"
            )
            assertTrue(
                error.message.contains(toChain.toString()),
                "Message should contain toChain '$toChain', got: '${error.message}'"
            )
            assertTrue(error is Exception, "BridgeError should be an Exception")
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.4**
    @Test
    fun bridgeServiceUnavailableErrorContainsServiceName() = runTest {
        checkAll(100, arbNonEmptyString) { service ->
            val error = BridgeError.BridgeServiceUnavailable(service)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(service),
                "Message should contain service name '$service', got: '${error.message}'"
            )
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.4**
    @Test
    fun bridgeTransactionFailedErrorContainsTxHashAndReason() = runTest {
        checkAll(100, arbNonEmptyString, arbNonEmptyString) { txHash, reason ->
            val error = BridgeError.BridgeTransactionFailed(txHash, reason)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(txHash),
                "Message should contain txHash '$txHash', got: '${error.message}'"
            )
            assertTrue(
                error.message.contains(reason),
                "Message should contain reason '$reason', got: '${error.message}'"
            )
        }
    }

    // Feature: staking-bridge, Property 16: Error objects chứa thông tin mô tả đầy đủ
    // **Validates: Requirements 16.4**
    @Test
    fun insufficientBridgeBalanceErrorContainsAllAmounts() = runTest {
        checkAll(100, arbPositiveDouble, arbPositiveDouble, arbPositiveDouble) { available, required, fee ->
            val error = BridgeError.InsufficientBridgeBalance(available, required, fee)
            assertTrue(error.message.isNotEmpty(), "Message should not be empty")
            assertTrue(
                error.message.contains(available.toString()),
                "Message should contain available '$available', got: '${error.message}'"
            )
            assertTrue(
                error.message.contains(required.toString()),
                "Message should contain required '$required', got: '${error.message}'"
            )
            assertTrue(
                error.message.contains(fee.toString()),
                "Message should contain fee '$fee', got: '${error.message}'"
            )
        }
    }
}
