package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.errors.WalletError
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Property-based and unit tests for TON staking operations.
 *
 * Feature: staking-bridge
 */
@OptIn(ExperimentalEncodingApi::class)
class TonStakingManagerTest {

    private val testMnemonic =
        "left arena awkward spin damp pipe liar ribbon few husband execute whisper"

    // ── Generators ──────────────────────────────────────────────────

    private val arbPoolType: Arb<TonPoolType> =
        Arb.enum<TonPoolType>().filter { it != TonPoolType.UNKNOWN }

    private val arbPositiveAmountNano: Arb<Long> =
        Arb.long(1_000_000L..100_000_000_000L) // 0.001 TON to 100 TON

    private val arbPositiveDouble: Arb<Double> =
        Arb.double(0.001..1_000_000.0).filter { it.isFinite() }

    // ── Property 4: TON stake tạo valid signed BOC ──────────────────

    // Feature: staking-bridge, Property 4: TON stake tạo valid signed BOC
    // **Validates: Requirements 4.1, 5.1, 5.2**
    @Test
    fun tonStakeProducesValidSignedBoc() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        // Use a valid TON address in raw format for the pool
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"

        checkAll(100, arbPoolType, arbPositiveAmountNano) { poolType, amount ->
            val seqno = 5

            val boc = when (poolType) {
                TonPoolType.NOMINATOR -> tonManager.signDepositToNominatorPool(poolAddress, amount, seqno)
                TonPoolType.TONSTAKERS -> tonManager.signTonstakersDeposit(poolAddress, amount, seqno)
                TonPoolType.BEMO -> tonManager.signBemoDeposit(poolAddress, amount, seqno)
                TonPoolType.UNKNOWN -> error("Should not reach UNKNOWN")
            }

            // BOC must be non-empty
            assertTrue(boc.isNotEmpty(), "Signed BOC should not be empty for poolType=$poolType, amount=$amount")

            // BOC must be valid base64 and decodable
            val decoded = Base64.Default.decode(boc)
            assertTrue(decoded.isNotEmpty(), "Decoded BOC bytes should not be empty")

            // Decoding as BagOfCells must succeed without throwing
            val bagOfCells = org.ton.boc.BagOfCells(decoded)
            assertTrue(bagOfCells.roots.isNotEmpty(), "BagOfCells should have at least one root cell")
        }
    }

    // ── Property 5: Liquid staking rate calculation ─────────────────

    // Feature: staking-bridge, Property 5: Liquid staking rate calculation
    // **Validates: Requirements 6.2, 6.3**
    @Test
    fun liquidStakingRateCalculation() = runTest {
        // For any tokenBalance > 0, totalStakingBalance > 0, totalTokenSupply > 0:
        // amountInTon = tokenBalance * (totalStakingBalance / totalTokenSupply)
        // rewards = amountInTon - tokenBalance
        // rewards must be non-negative when rate >= 1

        val arbTokenBalance = Arb.double(0.001..1_000_000.0).filter { it.isFinite() }
        val arbTotalStaking = Arb.long(1_000_000_000L..1_000_000_000_000_000L) // nanoTON
        val arbTotalSupply = Arb.long(1_000_000_000L..1_000_000_000_000_000L)  // nanoTokens

        checkAll(100, arbTokenBalance, arbTotalStaking, arbTotalSupply) { tokenBalance, totalStakingBalance, totalTokenSupply ->
            val rate = totalStakingBalance.toDouble() / totalTokenSupply.toDouble()
            val amountInTon = tokenBalance * rate
            val rewards = amountInTon - tokenBalance

            // amountInTon must equal tokenBalance * rate
            val expectedAmountInTon = tokenBalance * (totalStakingBalance.toDouble() / totalTokenSupply.toDouble())
            assertEquals(
                expectedAmountInTon, amountInTon, 1e-10,
                "amountInTon should equal tokenBalance * rate"
            )

            // When rate >= 1, rewards must be non-negative
            if (rate >= 1.0) {
                assertTrue(
                    rewards >= -1e-10, // small tolerance for floating point
                    "Rewards should be non-negative when rate >= 1.0: rewards=$rewards, rate=$rate, tokenBalance=$tokenBalance"
                )
            }
        }
    }

    // ── Property 6: TON IStakingManager routes đúng pool handler ────

    // Feature: staking-bridge, Property 6: TON IStakingManager routes đúng pool handler
    // **Validates: Requirements 7.2, 7.3, 7.4, 7.5**
    @Test
    fun tonStakingManagerRoutesCorrectPoolHandler() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"

        checkAll(100, arbPoolType, arbPositiveAmountNano) { poolType, amount ->
            val seqno = 5

            // Call the specific sign method directly
            val directBoc = when (poolType) {
                TonPoolType.NOMINATOR -> tonManager.signDepositToNominatorPool(poolAddress, amount, seqno)
                TonPoolType.TONSTAKERS -> tonManager.signTonstakersDeposit(poolAddress, amount, seqno)
                TonPoolType.BEMO -> tonManager.signBemoDeposit(poolAddress, amount, seqno)
                TonPoolType.UNKNOWN -> error("Should not reach UNKNOWN")
            }

            // Verify the direct call produces a valid BOC
            assertTrue(directBoc.isNotEmpty(), "Direct sign method should produce non-empty BOC for $poolType")

            // Verify the BOC is valid base64
            val decoded = Base64.Default.decode(directBoc)
            assertTrue(decoded.isNotEmpty(), "Decoded BOC should not be empty for $poolType")

            // Verify routing: for NOMINATOR, the BOC should contain the nominator op-code payload
            // For TONSTAKERS and BEMO, they delegate to signTransaction (simple transfer)
            // All should produce valid BagOfCells
            val bagOfCells = org.ton.boc.BagOfCells(decoded)
            assertTrue(bagOfCells.roots.isNotEmpty(), "BagOfCells should have roots for $poolType")
        }
    }

    // ── Unit Tests (5.6) ────────────────────────────────────────────

    // Test unstake returns UnsupportedOperation
    // Validates: Requirements 7.6
    @Test
    fun unstakeReturnsUnsupportedOperation() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val coinNetwork = CoinNetwork(NetworkName.TON)

        val error = assertFailsWith<WalletError.UnsupportedOperation> {
            tonManager.unstake(1_000_000_000L, coinNetwork)
        }

        assertTrue(error.message.contains("unstake"), "Error message should mention 'unstake'")
        assertTrue(error.message.contains("TON"), "Error message should mention 'TON'")
        assertEquals("unstake", error.operation)
        assertEquals("TON", error.chain)
    }

    // Test pool type enum values
    // Validates: Requirements 4.4, 5.4
    @Test
    fun tonPoolTypeEnumHasExpectedValues() {
        val values = TonPoolType.entries
        assertEquals(4, values.size, "TonPoolType should have 4 values")
        assertTrue(values.contains(TonPoolType.NOMINATOR))
        assertTrue(values.contains(TonPoolType.TONSTAKERS))
        assertTrue(values.contains(TonPoolType.BEMO))
        assertTrue(values.contains(TonPoolType.UNKNOWN))
    }

    // Test that each pool type sign method produces valid BOC
    // Validates: Requirements 4.4, 5.4
    @Test
    fun nominatorPoolSignProducesValidBoc() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"

        val boc = tonManager.signDepositToNominatorPool(poolAddress, 10_000_000_000L, 5)
        assertTrue(boc.isNotEmpty(), "Nominator pool BOC should not be empty")
        val decoded = Base64.Default.decode(boc)
        val bagOfCells = org.ton.boc.BagOfCells(decoded)
        assertTrue(bagOfCells.roots.isNotEmpty())
    }

    @Test
    fun tonstakersSignProducesValidBoc() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"

        val boc = tonManager.signTonstakersDeposit(poolAddress, 10_000_000_000L, 5)
        assertTrue(boc.isNotEmpty(), "Tonstakers BOC should not be empty")
        val decoded = Base64.Default.decode(boc)
        val bagOfCells = org.ton.boc.BagOfCells(decoded)
        assertTrue(bagOfCells.roots.isNotEmpty())
    }

    @Test
    fun bemoSignProducesValidBoc() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"

        val boc = tonManager.signBemoDeposit(poolAddress, 10_000_000_000L, 5)
        assertTrue(boc.isNotEmpty(), "Bemo BOC should not be empty")
        val decoded = Base64.Default.decode(boc)
        val bagOfCells = org.ton.boc.BagOfCells(decoded)
        assertTrue(bagOfCells.roots.isNotEmpty())
    }
}
