package com.lybia.cryptowallet.wallets.bridge

import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.errors.BridgeError
import com.lybia.cryptowallet.models.bridge.BridgeStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for EthereumArbitrumBridge.
 *
 * Feature: staking-bridge
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 11.1, 11.2, 12.5
 */
class EthereumArbitrumBridgeTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private fun createBridge(): EthereumArbitrumBridge =
        EthereumArbitrumBridge(testMnemonic)

    // ── Bridge ETH → Arbitrum flow ──────────────────────────────────

    // Validates: Requirement 10.1
    @Test
    fun bridgeEthToArbitrumReturnsSuccessfulResponse() = runTest {
        val bridge = createBridge()
        val amount = 1_000_000L // above minimum

        val result = bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.ARBITRUM, amount)

        assertTrue(result.success, "Bridge ETH → Arbitrum should succeed")
        assertNotNull(result.txHash, "Bridge tx hash should not be null")
        assertTrue(result.txHash!!.contains("bridge_deposit"), "Tx hash should indicate deposit operation")
        assertEquals(null, result.error, "Error should be null on success")
    }

    // Validates: Requirement 10.4
    @Test
    fun bridgeEthToArbitrumReturnsTxIdForTracking() = runTest {
        val bridge = createBridge()
        val amount = 500_000L

        val result = bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.ARBITRUM, amount)

        assertTrue(result.success)
        assertNotNull(result.txHash, "Should return transaction ID for status tracking")
        assertTrue(result.txHash!!.isNotBlank(), "Transaction ID should not be blank")
    }

    // ── Bridge Arbitrum → ETH flow ──────────────────────────────────

    // Validates: Requirement 10.2
    @Test
    fun bridgeArbitrumToEthReturnsSuccessfulResponse() = runTest {
        val bridge = createBridge()
        val amount = 1_000_000L

        val result = bridge.bridgeAsset(NetworkName.ARBITRUM, NetworkName.ETHEREUM, amount)

        assertTrue(result.success, "Bridge Arbitrum → ETH should succeed")
        assertNotNull(result.txHash, "Bridge tx hash should not be null")
        assertTrue(result.txHash!!.contains("bridge_withdrawal"), "Tx hash should indicate withdrawal operation")
        assertEquals(null, result.error, "Error should be null on success")
    }

    // Validates: Requirement 10.4
    @Test
    fun bridgeArbitrumToEthReturnsTxIdForTracking() = runTest {
        val bridge = createBridge()
        val amount = 200_000L

        val result = bridge.bridgeAsset(NetworkName.ARBITRUM, NetworkName.ETHEREUM, amount)

        assertTrue(result.success)
        assertNotNull(result.txHash, "Should return transaction ID for status tracking")
        assertTrue(result.txHash!!.isNotBlank(), "Transaction ID should not be blank")
    }

    // ── Insufficient balance error ──────────────────────────────────

    // Validates: Requirement 10.5
    @Test
    fun bridgeWithAmountBelowMinimumThrowsInsufficientBalance() = runTest {
        val bridge = createBridge()
        val tooSmallAmount = 5_000L // below MIN_BRIDGE_AMOUNT of 10_000

        val error = assertFailsWith<BridgeError.InsufficientBridgeBalance> {
            bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.ARBITRUM, tooSmallAmount)
        }

        assertTrue(error.message.contains("Insufficient"), "Error message should mention insufficient balance")
    }

    // Validates: Requirement 10.5
    @Test
    fun bridgeArbitrumWithAmountBelowMinimumThrowsInsufficientBalance() = runTest {
        val bridge = createBridge()
        val tooSmallAmount = 100L // way below minimum

        assertFailsWith<BridgeError.InsufficientBridgeBalance> {
            bridge.bridgeAsset(NetworkName.ARBITRUM, NetworkName.ETHEREUM, tooSmallAmount)
        }
    }

    // ── Unsupported bridge pair error ────────────────────────────────

    // Validates: Requirement 12.5
    @Test
    fun bridgeWithUnsupportedPairThrowsUnsupportedBridgePair() = runTest {
        val bridge = createBridge()

        val error = assertFailsWith<BridgeError.UnsupportedBridgePair> {
            bridge.bridgeAsset(NetworkName.CARDANO, NetworkName.ARBITRUM, 1_000_000L)
        }

        assertEquals(NetworkName.CARDANO, error.fromChain)
        assertEquals(NetworkName.ARBITRUM, error.toChain)
    }

    @Test
    fun bridgeWithSameChainThrowsUnsupportedBridgePair() = runTest {
        val bridge = createBridge()

        assertFailsWith<BridgeError.UnsupportedBridgePair> {
            bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.ETHEREUM, 1_000_000L)
        }
    }

    // ── getBridgeStatus ─────────────────────────────────────────────

    // Validates: Requirement 11.1, 11.2
    @Test
    fun getBridgeStatusReturnsValidStatus() = runTest {
        val bridge = createBridge()
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()

        val status = bridge.getBridgeStatus("bridge_deposit_ethereum_1000000_sim_1")

        assertTrue(
            status in validStatuses,
            "Status '$status' should be one of $validStatuses"
        )
    }

    @Test
    fun getBridgeStatusWithBlankHashThrowsError() = runTest {
        val bridge = createBridge()

        assertFailsWith<BridgeError.BridgeTransactionFailed> {
            bridge.getBridgeStatus("")
        }
    }

    @Test
    fun getBridgeStatusWithBlankSpacesThrowsError() = runTest {
        val bridge = createBridge()

        assertFailsWith<BridgeError.BridgeTransactionFailed> {
            bridge.getBridgeStatus("   ")
        }
    }

    // ── estimateBridgeFee ───────────────────────────────────────────

    // Validates: Requirement 10.3
    @Test
    fun estimateBridgeFeeReturnsEthUnits() = runTest {
        val bridge = createBridge()
        val amount = 1_000_000_000_000_000_000L // 1 ETH in wei

        val fee = bridge.estimateBridgeFee(NetworkName.ETHEREUM, NetworkName.ARBITRUM, amount)

        assertEquals("ETH", fee.sourceUnit, "Source unit should be ETH")
        assertEquals("ETH", fee.destinationUnit, "Destination unit should be ETH")
        assertTrue(fee.sourceFee > 0, "Source fee should be positive")
        assertTrue(fee.destinationFee > 0, "Destination fee should be positive")
        assertTrue(fee.bridgeFee > 0, "Bridge fee should be positive")
        assertTrue(fee.totalFee > 0, "Total fee should be positive")
    }

    // Validates: Requirement 10.3
    @Test
    fun estimateBridgeFeeTotalEqualsSumOfComponents() = runTest {
        val bridge = createBridge()
        val amount = 2_000_000_000_000_000_000L // 2 ETH in wei

        val fee = bridge.estimateBridgeFee(NetworkName.ETHEREUM, NetworkName.ARBITRUM, amount)

        val expectedTotal = fee.sourceFee + fee.destinationFee + fee.bridgeFee
        assertTrue(
            kotlin.math.abs(fee.totalFee - expectedTotal) < 1e-9,
            "Total fee (${fee.totalFee}) should equal sum of components ($expectedTotal)"
        )
    }

    @Test
    fun estimateBridgeFeeForUnsupportedPairThrowsError() = runTest {
        val bridge = createBridge()

        assertFailsWith<BridgeError.UnsupportedBridgePair> {
            bridge.estimateBridgeFee(NetworkName.BTC, NetworkName.ARBITRUM, 1_000_000L)
        }
    }

    // ── End-to-end flow verification ────────────────────────────────

    @Test
    fun fullBridgeFlowEthToArbitrumThenCheckStatus() = runTest {
        val bridge = createBridge()
        val amount = 50_000L

        // Bridge
        val result = bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.ARBITRUM, amount)
        assertTrue(result.success)
        assertNotNull(result.txHash)

        // Check status
        val status = bridge.getBridgeStatus(result.txHash!!)
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()
        assertTrue(status in validStatuses)
    }

    @Test
    fun fullBridgeFlowArbitrumToEthThenCheckStatus() = runTest {
        val bridge = createBridge()
        val amount = 100_000L

        // Bridge
        val result = bridge.bridgeAsset(NetworkName.ARBITRUM, NetworkName.ETHEREUM, amount)
        assertTrue(result.success)
        assertNotNull(result.txHash)

        // Check status
        val status = bridge.getBridgeStatus(result.txHash!!)
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()
        assertTrue(status in validStatuses)
    }
}
