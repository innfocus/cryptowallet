package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.errors.StakingError
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Property-based and unit tests for Cardano staking operations.
 */
class CardanoStakingManagerTest {

    // ── Property Tests ──────────────────────────────────────────────────

    // Feature: staking-bridge, Property 3: Unit conversion round-trip (lovelace ↔ ADA)
    // **Validates: Requirements 3.3, 6.4**
    @Test
    fun unitConversionRoundTripLovelaceAda() = runTest {
        checkAll(100, Arb.long(0L..1_000_000_000_000L)) { lovelace ->
            // Convert lovelace → ADA
            val ada = lovelace.toDouble() / 1_000_000.0
            // Convert ADA → lovelace
            val backToLovelace = (ada * 1_000_000.0).roundToLong()

            // Round-trip should yield original value within floating point tolerance
            // For values up to 1 trillion lovelace, the error should be at most 1 lovelace
            val diff = abs(lovelace - backToLovelace)
            assertTrue(
                diff <= 1L,
                "Round-trip failed for lovelace=$lovelace: ada=$ada, back=$backToLovelace, diff=$diff"
            )
        }
    }

    // ── Unit Tests ──────────────────────────────────────────────────────

    // Test insufficient balance error (StakingError.InsufficientStakingBalance)
    // Validates: Requirements 1.5
    @Test
    fun insufficientStakingBalanceErrorContainsDetails() {
        val error = StakingError.InsufficientStakingBalance(available = 1.5, required = 4.2)

        assertTrue(error.message.contains("1.5"), "Error should contain available amount")
        assertTrue(error.message.contains("4.2"), "Error should contain required amount")
        assertTrue(error.message.isNotEmpty(), "Error message should not be empty")
        assertEquals(1.5, error.available)
        assertEquals(4.2, error.required)
    }

    // Test no active delegation error (StakingError.NoDelegationActive)
    // Validates: Requirements 2.5
    @Test
    fun noDelegationActiveErrorContainsDetails() {
        val stakingAddr = "stake1ux3g2c9dx2nhhehyrezyxpkstartcqmu9hk63ud0mxj8g8q5fhxn0"
        val error = StakingError.NoDelegationActive(stakingAddress = stakingAddr)

        assertTrue(error.message.contains(stakingAddr), "Error should contain staking address")
        assertTrue(error.message.isNotEmpty(), "Error message should not be empty")
        assertEquals(stakingAddr, error.stakingAddress)
    }

    // Test that InsufficientStakingBalance is a StakingError
    @Test
    fun insufficientStakingBalanceIsStakingError() {
        val error: StakingError = StakingError.InsufficientStakingBalance(available = 0.5, required = 2.0)
        assertTrue(error is Exception, "StakingError should be an Exception")
        assertTrue(error is StakingError.InsufficientStakingBalance)
    }

    // Test that NoDelegationActive is a StakingError
    @Test
    fun noDelegationActiveIsStakingError() {
        val error: StakingError = StakingError.NoDelegationActive(stakingAddress = "stake1test")
        assertTrue(error is Exception, "StakingError should be an Exception")
        assertTrue(error is StakingError.NoDelegationActive)
    }
}
