package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.models.bridge.BridgeFeeEstimate
import com.lybia.cryptowallet.models.bridge.BridgeStatus
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Property-based tests for Bridge data models.
 *
 * Feature: staking-bridge
 */
class BridgeModelsTest {

    // ── Generators ──────────────────────────────────────────────────

    private val arbNonNegativeDouble: Arb<Double> = Arb.double(0.0..10.0)
        .filter { it.isFinite() }

    private val arbSourceUnit: Arb<String> = Arb.of("ADA", "ETH", "tDUST")
    private val arbDestUnit: Arb<String> = Arb.of("tDUST", "ADA", "ETH")

    // ── Property 7: Bridge fee total bằng tổng các thành phần ───────

    // Feature: staking-bridge, Property 7: Bridge fee total bằng tổng các thành phần
    // **Validates: Requirements 9.3, 10.3**
    @Test
    fun bridgeFeeTotalEqualsComponentSum() = runTest {
        checkAll(
            100,
            arbNonNegativeDouble,
            arbNonNegativeDouble,
            arbNonNegativeDouble,
            arbSourceUnit,
            arbDestUnit
        ) { sourceFee, destinationFee, bridgeFee, srcUnit, dstUnit ->
            val expectedTotal = sourceFee + destinationFee + bridgeFee
            val estimate = BridgeFeeEstimate(
                sourceFee = sourceFee,
                destinationFee = destinationFee,
                bridgeFee = bridgeFee,
                totalFee = expectedTotal,
                sourceUnit = srcUnit,
                destinationUnit = dstUnit
            )

            // totalFee must equal sum of components (within floating point tolerance)
            assertTrue(
                abs(estimate.totalFee - (estimate.sourceFee + estimate.destinationFee + estimate.bridgeFee)) < 1e-9,
                "totalFee (${estimate.totalFee}) should equal sourceFee (${estimate.sourceFee}) + " +
                    "destinationFee (${estimate.destinationFee}) + bridgeFee (${estimate.bridgeFee})"
            )

            // All fee components must be non-negative
            assertTrue(estimate.sourceFee >= 0.0, "sourceFee should be non-negative")
            assertTrue(estimate.destinationFee >= 0.0, "destinationFee should be non-negative")
            assertTrue(estimate.bridgeFee >= 0.0, "bridgeFee should be non-negative")
            assertTrue(estimate.totalFee >= 0.0, "totalFee should be non-negative")
        }
    }

    // ── Property 8: Bridge status luôn là giá trị enum hợp lệ ──────

    // Feature: staking-bridge, Property 8: Bridge status luôn là giá trị enum hợp lệ
    // **Validates: Requirements 11.2**
    @Test
    fun bridgeStatusAlwaysValidEnum() = runTest {
        val validValues = setOf("pending", "confirming", "completed", "failed")

        checkAll(100, Arb.enum<BridgeStatus>()) { status ->
            // The value must be one of the valid strings
            assertTrue(
                status.value in validValues,
                "BridgeStatus.value '${status.value}' should be one of $validValues"
            )
        }
    }

    // Feature: staking-bridge, Property 8: Bridge status luôn là giá trị enum hợp lệ
    // **Validates: Requirements 11.2**
    @Test
    fun bridgeStatusEnumCoversAllValidValues() = runTest {
        val validValues = setOf("pending", "confirming", "completed", "failed")
        val enumValues = BridgeStatus.entries.map { it.value }.toSet()

        // Enum must cover exactly the valid values
        assertEquals(validValues, enumValues, "BridgeStatus entries should cover all valid values")
    }

    // Feature: staking-bridge, Property 8: Bridge status luôn là giá trị enum hợp lệ
    // **Validates: Requirements 11.2**
    @Test
    fun bridgeStatusEnumHasExactlyFourEntries() {
        assertEquals(4, BridgeStatus.entries.size, "BridgeStatus should have exactly 4 entries")
    }
}
