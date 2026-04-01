package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Feature: service-fee-support, Property 8: Balance sufficiency check
 *
 * For any combination of amount, networkFee, serviceFee, and balance (all >= 0),
 * validateSufficientBalance must return:
 * - sufficient=true if and only if totalRequired <= balance
 * - deficit = max(0, totalRequired - balance) exactly
 * - totalRequired = amount + networkFee + serviceFee
 *
 * **Validates: Requirements 4.2, 4.3, 4.4**
 */
class BalanceValidationTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val manager = CommonCoinsManager(testMnemonic)

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates non-negative doubles suitable for monetary amounts. */
    private fun arbPositiveDouble(): Arb<Double> = Arb.double(
        min = 0.0,
        max = 1_000_000.0
    ).filter { it >= 0.0 && it.isFinite() }

    /** Generates a random NetworkName from all supported chains. */
    private fun arbNetworkName(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.TON,
        NetworkName.CARDANO,
        NetworkName.XRP
    )

    /** Generates a UTXO chain name. */
    private fun arbUtxoChain(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.CARDANO
    )

    /** Generates an Account chain name. */
    private fun arbAccountChain(): Arb<NetworkName> = Arb.of(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.XRP,
        NetworkName.TON
    )

    // ── Floating-point comparison helper ────────────────────────────────

    private val EPSILON = 1e-9

    private fun doubleEquals(a: Double, b: Double): Boolean =
        abs(a - b) < EPSILON

    // ── Property test ───────────────────────────────────────────────────

    @Test
    fun balanceSufficiencyCheckForAllChains() = runTest {
        checkAll(
            PropTestConfig(iterations = 200),
            arbNetworkName(),
            arbPositiveDouble(),
            arbPositiveDouble(),
            arbPositiveDouble(),
            arbPositiveDouble()
        ) { coin, amount, networkFee, serviceFee, balance ->
            val result = manager.validateSufficientBalance(
                coin = coin,
                amount = amount,
                networkFee = networkFee,
                serviceFee = serviceFee,
                balance = balance
            )

            val expectedTotal = amount + networkFee + serviceFee
            val expectedDeficit = maxOf(0.0, expectedTotal - balance)
            val expectedSufficient = expectedTotal <= balance

            // totalRequired = amount + networkFee + serviceFee
            assertTrue(
                doubleEquals(result.totalRequired, expectedTotal),
                "totalRequired should be amount($amount) + networkFee($networkFee) + serviceFee($serviceFee) = $expectedTotal, got ${result.totalRequired}"
            )

            // deficit = max(0, totalRequired - balance)
            assertTrue(
                doubleEquals(result.deficit, expectedDeficit),
                "deficit should be max(0, $expectedTotal - $balance) = $expectedDeficit, got ${result.deficit}"
            )

            // sufficient = true iff totalRequired <= balance
            assertEquals(
                expectedSufficient,
                result.sufficient,
                "sufficient should be $expectedSufficient for totalRequired=$expectedTotal, balance=$balance"
            )
        }
    }

    // ── Unit tests ──────────────────────────────────────────────────────

    /**
     * Balance exactly equals totalRequired → sufficient=true, deficit=0
     * Validates: Requirements 4.2, 4.3
     */
    @Test
    fun balanceExactlyEqualsTotalRequired() {
        val amount = 1.5
        val networkFee = 0.001
        val serviceFee = 0.05
        val balance = amount + networkFee + serviceFee // exactly sufficient

        val result = manager.validateSufficientBalance(
            coin = NetworkName.ETHEREUM,
            amount = amount,
            networkFee = networkFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient, "Should be sufficient when balance == totalRequired")
        assertTrue(
            doubleEquals(result.totalRequired, balance),
            "totalRequired should equal balance"
        )
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0 when balance == totalRequired"
        )
    }

    /**
     * Balance short by smallest unit (0.000001) → sufficient=false, deficit=0.000001
     * Validates: Requirements 4.2, 4.3
     */
    @Test
    fun balanceShortBySmallestUnit() {
        val amount = 1.0
        val networkFee = 0.001
        val serviceFee = 0.05
        val totalRequired = amount + networkFee + serviceFee
        val shortfall = 0.000001
        val balance = totalRequired - shortfall

        val result = manager.validateSufficientBalance(
            coin = NetworkName.BTC,
            amount = amount,
            networkFee = networkFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertFalse(result.sufficient, "Should be insufficient when balance < totalRequired")
        assertTrue(
            doubleEquals(result.totalRequired, totalRequired),
            "totalRequired should be amount + networkFee + serviceFee"
        )
        assertTrue(
            result.deficit > 0.0,
            "deficit should be positive when balance < totalRequired"
        )
        assertTrue(
            doubleEquals(result.deficit, shortfall),
            "deficit should equal the shortfall ($shortfall), got ${result.deficit}"
        )
    }

    /**
     * serviceFee = 0 → totalRequired = amount + networkFee only
     * Validates: Requirements 4.1, 4.4
     */
    @Test
    fun zeroServiceFeeOnlyAmountAndNetworkFee() {
        val amount = 2.0
        val networkFee = 0.005
        val serviceFee = 0.0
        val balance = 3.0

        val result = manager.validateSufficientBalance(
            coin = NetworkName.ETHEREUM,
            amount = amount,
            networkFee = networkFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(
            doubleEquals(result.totalRequired, amount + networkFee),
            "totalRequired should be amount + networkFee when serviceFee=0, got ${result.totalRequired}"
        )
        assertTrue(result.sufficient, "Should be sufficient with balance=3.0 > totalRequired=2.005")
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )
    }

    /**
     * All values = 0 → sufficient=true, totalRequired=0, deficit=0
     * Validates: Requirements 4.2, 4.3
     */
    @Test
    fun allValuesZero() {
        val result = manager.validateSufficientBalance(
            coin = NetworkName.BTC,
            amount = 0.0,
            networkFee = 0.0,
            serviceFee = 0.0,
            balance = 0.0
        )

        assertTrue(result.sufficient, "Should be sufficient when all values are 0")
        assertTrue(
            doubleEquals(result.totalRequired, 0.0),
            "totalRequired should be 0"
        )
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )
    }

    /**
     * UTXO chain (BTC) specific example:
     * totalRequired = amount + networkFee + serviceFee
     * Validates: Requirements 4.4
     */
    @Test
    fun utxoChainBtcExample() {
        val amount = 0.5        // 0.5 BTC
        val networkFee = 0.0002 // miner fee
        val serviceFee = 0.001  // service fee
        val balance = 0.6

        val result = manager.validateSufficientBalance(
            coin = NetworkName.BTC,
            amount = amount,
            networkFee = networkFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + networkFee + serviceFee // 0.5012
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "BTC totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )
        assertTrue(result.sufficient, "Should be sufficient: balance(0.6) >= totalRequired($expectedTotal)")
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )
    }

    /**
     * Account chain (ETH) specific example:
     * totalRequired = amount + networkFee + serviceFee
     * (networkFee already doubled by estimateFee for Account chains)
     * Validates: Requirements 4.4
     */
    @Test
    fun accountChainEthExample() {
        val amount = 1.0          // 1 ETH
        val networkFee = 0.006    // already doubled by estimateFee (2 × 0.003)
        val serviceFee = 0.01     // service fee
        val balance = 1.0         // exactly 1 ETH — not enough

        val result = manager.validateSufficientBalance(
            coin = NetworkName.ETHEREUM,
            amount = amount,
            networkFee = networkFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + networkFee + serviceFee // 1.016
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "ETH totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )
        assertFalse(result.sufficient, "Should be insufficient: balance(1.0) < totalRequired($expectedTotal)")
        val expectedDeficit = expectedTotal - balance // 0.016
        assertTrue(
            doubleEquals(result.deficit, expectedDeficit),
            "deficit should be $expectedDeficit, got ${result.deficit}"
        )
    }
}
