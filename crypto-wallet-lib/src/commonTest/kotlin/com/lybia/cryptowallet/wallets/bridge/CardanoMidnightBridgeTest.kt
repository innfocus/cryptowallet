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
 * Unit tests for CardanoMidnightBridge.
 *
 * Feature: staking-bridge
 * Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5, 11.1, 11.2, 11.3
 */
class CardanoMidnightBridgeTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private fun createBridge(): CardanoMidnightBridge =
        CardanoMidnightBridge(testMnemonic)

    // ── Bridge ADA → tDUST flow ─────────────────────────────────────

    // Validates: Requirement 9.1
    @Test
    fun bridgeAdaToTdustReturnsSuccessfulResponse() = runTest {
        val bridge = createBridge()
        val amount = 5_000_000L // 5 ADA

        val result = bridge.bridgeAsset(NetworkName.CARDANO, NetworkName.MIDNIGHT, amount)

        assertTrue(result.success, "Bridge ADA → tDUST should succeed")
        assertNotNull(result.txHash, "Bridge tx hash should not be null")
        assertTrue(result.txHash!!.contains("bridge_mint"), "Tx hash should indicate mint operation")
        assertEquals(null, result.error, "Error should be null on success")
    }

    // Validates: Requirement 9.4
    @Test
    fun bridgeAdaToTdustReturnsTxIdForTracking() = runTest {
        val bridge = createBridge()
        val amount = 10_000_000L // 10 ADA

        val result = bridge.bridgeAsset(NetworkName.CARDANO, NetworkName.MIDNIGHT, amount)

        assertTrue(result.success)
        assertNotNull(result.txHash, "Should return transaction ID for status tracking")
        assertTrue(result.txHash!!.isNotBlank(), "Transaction ID should not be blank")
    }

    // ── Bridge tDUST → ADA flow ─────────────────────────────────────

    // Validates: Requirement 9.2
    @Test
    fun bridgeTdustToAdaReturnsSuccessfulResponse() = runTest {
        val bridge = createBridge()
        val amount = 5_000_000L // 5 tDUST

        val result = bridge.bridgeAsset(NetworkName.MIDNIGHT, NetworkName.CARDANO, amount)

        assertTrue(result.success, "Bridge tDUST → ADA should succeed")
        assertNotNull(result.txHash, "Bridge tx hash should not be null")
        assertTrue(result.txHash!!.contains("bridge_unlock"), "Tx hash should indicate unlock operation")
        assertEquals(null, result.error, "Error should be null on success")
    }

    // Validates: Requirement 9.4
    @Test
    fun bridgeTdustToAdaReturnsTxIdForTracking() = runTest {
        val bridge = createBridge()
        val amount = 3_000_000L

        val result = bridge.bridgeAsset(NetworkName.MIDNIGHT, NetworkName.CARDANO, amount)

        assertTrue(result.success)
        assertNotNull(result.txHash, "Should return transaction ID for status tracking")
        assertTrue(result.txHash!!.isNotBlank(), "Transaction ID should not be blank")
    }

    // ── Insufficient balance error ──────────────────────────────────

    // Validates: Requirement 9.5
    @Test
    fun bridgeWithAmountBelowMinimumThrowsInsufficientBalance() = runTest {
        val bridge = createBridge()
        val tooSmallAmount = 500_000L // 0.5 ADA, below 1 ADA minimum

        val error = assertFailsWith<BridgeError.InsufficientBridgeBalance> {
            bridge.bridgeAsset(NetworkName.CARDANO, NetworkName.MIDNIGHT, tooSmallAmount)
        }

        assertTrue(error.message.contains("Insufficient"), "Error message should mention insufficient balance")
    }

    // Validates: Requirement 9.5
    @Test
    fun bridgeTdustWithAmountBelowMinimumThrowsInsufficientBalance() = runTest {
        val bridge = createBridge()
        val tooSmallAmount = 100L // way below minimum

        assertFailsWith<BridgeError.InsufficientBridgeBalance> {
            bridge.bridgeAsset(NetworkName.MIDNIGHT, NetworkName.CARDANO, tooSmallAmount)
        }
    }

    // ── Unsupported bridge pair ─────────────────────────────────────

    @Test
    fun bridgeWithUnsupportedPairThrowsUnsupportedBridgePair() = runTest {
        val bridge = createBridge()

        val error = assertFailsWith<BridgeError.UnsupportedBridgePair> {
            bridge.bridgeAsset(NetworkName.ETHEREUM, NetworkName.MIDNIGHT, 1_000_000L)
        }

        assertEquals(NetworkName.ETHEREUM, error.fromChain)
        assertEquals(NetworkName.MIDNIGHT, error.toChain)
    }

    // ── getBridgeStatus ─────────────────────────────────────────────

    // Validates: Requirement 11.1, 11.2
    @Test
    fun getBridgeStatusReturnsValidStatus() = runTest {
        val bridge = createBridge()
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()

        val status = bridge.getBridgeStatus("bridge_mint_5000000_sim_1")

        assertTrue(
            status in validStatuses,
            "Status '$status' should be one of $validStatuses"
        )
    }

    // Validates: Requirement 11.3
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

    // Validates: Requirement 9.3
    @Test
    fun estimateBridgeFeeForAdaToTdustReturnsCorrectUnits() = runTest {
        val bridge = createBridge()
        val amount = 10_000_000L // 10 ADA

        val fee = bridge.estimateBridgeFee(NetworkName.CARDANO, NetworkName.MIDNIGHT, amount)

        assertEquals("ADA", fee.sourceUnit, "Source unit should be ADA")
        assertEquals("tDUST", fee.destinationUnit, "Destination unit should be tDUST")
        assertTrue(fee.sourceFee > 0, "Source fee should be positive")
        assertTrue(fee.destinationFee > 0, "Destination fee should be positive")
        assertTrue(fee.bridgeFee > 0, "Bridge fee should be positive")
        assertTrue(fee.totalFee > 0, "Total fee should be positive")
    }

    // Validates: Requirement 9.3
    @Test
    fun estimateBridgeFeeForTdustToAdaReturnsCorrectUnits() = runTest {
        val bridge = createBridge()
        val amount = 10_000_000L

        val fee = bridge.estimateBridgeFee(NetworkName.MIDNIGHT, NetworkName.CARDANO, amount)

        assertEquals("tDUST", fee.sourceUnit, "Source unit should be tDUST")
        assertEquals("ADA", fee.destinationUnit, "Destination unit should be ADA")
    }

    // Validates: Requirement 9.3
    @Test
    fun estimateBridgeFeeTotalEqualsSumOfComponents() = runTest {
        val bridge = createBridge()
        val amount = 50_000_000L // 50 ADA

        val fee = bridge.estimateBridgeFee(NetworkName.CARDANO, NetworkName.MIDNIGHT, amount)

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
            bridge.estimateBridgeFee(NetworkName.BTC, NetworkName.MIDNIGHT, 1_000_000L)
        }
    }

    // ── End-to-end flow verification ────────────────────────────────

    @Test
    fun fullBridgeFlowAdaToTdustThenCheckStatus() = runTest {
        val bridge = createBridge()
        val amount = 2_000_000L // 2 ADA

        // Bridge
        val result = bridge.bridgeAsset(NetworkName.CARDANO, NetworkName.MIDNIGHT, amount)
        assertTrue(result.success)
        assertNotNull(result.txHash)

        // Check status
        val status = bridge.getBridgeStatus(result.txHash!!)
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()
        assertTrue(status in validStatuses)
    }

    @Test
    fun fullBridgeFlowTdustToAdaThenCheckStatus() = runTest {
        val bridge = createBridge()
        val amount = 3_000_000L

        // Bridge
        val result = bridge.bridgeAsset(NetworkName.MIDNIGHT, NetworkName.CARDANO, amount)
        assertTrue(result.success)
        assertNotNull(result.txHash)

        // Check status
        val status = bridge.getBridgeStatus(result.txHash!!)
        val validStatuses = BridgeStatus.entries.map { it.value }.toSet()
        assertTrue(status in validStatuses)
    }
}
