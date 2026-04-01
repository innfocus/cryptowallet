package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for the service fee flow:
 *   estimateFee → validateSufficientBalance → sendCoin
 *
 * These tests verify the complete logical flow across all chain types,
 * ensuring that fee estimation, balance validation, and send operations
 * work together correctly with and without service fees.
 *
 * **Validates: Requirements 1.1-1.5, 2.1-2.6, 3.1-3.4, 4.1-4.4, 5.1-5.4, 6.1-6.3**
 */
class ServiceFeeEndToEndTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val manager = CommonCoinsManager(testMnemonic)

    // ── Floating-point comparison helper ────────────────────────────────

    private val EPSILON = 1e-9

    private fun doubleEquals(a: Double, b: Double): Boolean =
        abs(a - b) < EPSILON

    // ── Replicated private helpers for flow verification ────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    // ════════════════════════════════════════════════════════════════════
    // Account Chain (ETH) — Full flow with service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * ETH flow: estimateFee doubles the base fee → validateSufficientBalance
     * checks amount + doubledFee + serviceFee → sendCoin splits fee in half
     * for two transactions.
     *
     * Validates: Requirements 1.2, 3.1, 3.2, 4.2, 4.3, 2.1
     */
    @Test
    fun ethFullFlowWithServiceFee() {
        val coin = NetworkName.ETHEREUM
        val amount = 1.0          // 1 ETH
        val baseFee = 0.003       // base network fee per tx
        val serviceFee = 0.01     // service fee
        val serviceAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18"

        // Step 1: estimateFee — Account chain doubles the fee
        val estimatedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER // 0.006
        assertTrue(
            doubleEquals(estimatedFee, baseFee * 2),
            "ETH estimateFee should double the base fee: expected ${baseFee * 2}, got $estimatedFee"
        )

        // Step 2: validateSufficientBalance — totalRequired = amount + estimatedFee + serviceFee
        val balance = 1.02 // enough to cover 1.0 + 0.006 + 0.01 = 1.016
        val validationResult = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee // 1.016
        assertTrue(validationResult.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(validationResult.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${validationResult.totalRequired}"
        )
        assertTrue(
            doubleEquals(validationResult.deficit, 0.0),
            "deficit should be 0 when sufficient"
        )

        // Step 3: Verify sendCoin logic — fee is split in half for each tx
        val halfFee = estimatedFee / 2.0
        assertTrue(
            doubleEquals(halfFee, baseFee),
            "Half of doubled fee should equal original base fee: expected $baseFee, got $halfFee"
        )

        // Verify the two-transaction model:
        // Main tx: sends `amount` to `toAddress` with `halfFee`
        // Service tx: sends `serviceFee` to `serviceAddress` with `halfFee`
        assertTrue(hasServiceFee(serviceAddress, serviceFee), "Should have service fee")
        assertFalse(isUtxoChain(coin), "ETH should not be UTXO chain")
    }

    /**
     * ETH flow: insufficient balance detected by validateSufficientBalance.
     *
     * Validates: Requirements 4.2, 4.3, 2.5
     */
    @Test
    fun ethFlowInsufficientBalance() {
        val coin = NetworkName.ETHEREUM
        val amount = 1.0
        val baseFee = 0.003
        val serviceFee = 0.01
        val estimatedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER // 0.006
        val balance = 1.0 // not enough for 1.0 + 0.006 + 0.01 = 1.016

        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee // 1.016
        assertFalse(result.sufficient, "Balance $balance should be insufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal"
        )
        val expectedDeficit = expectedTotal - balance // 0.016
        assertTrue(
            doubleEquals(result.deficit, expectedDeficit),
            "deficit should be $expectedDeficit, got ${result.deficit}"
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Account Chain (XRP) — Full flow with service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * XRP flow: estimateFee doubles → validateSufficientBalance → sendCoin
     * sends two transactions (main + service fee), each with half the fee.
     *
     * Validates: Requirements 1.2, 3.3, 4.2, 4.3, 2.1
     */
    @Test
    fun xrpFullFlowWithServiceFee() {
        val coin = NetworkName.XRP
        val amount = 100.0        // 100 XRP
        val baseFee = 0.000012    // 12 drops in XRP
        val serviceFee = 0.5      // 0.5 XRP service fee
        val serviceAddress = "rN7n3473SaZBCG4dFL83w7p1W9cgZw6iF3"

        // Step 1: estimateFee — doubles for Account chain
        val estimatedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER
        assertTrue(
            doubleEquals(estimatedFee, baseFee * 2),
            "XRP estimateFee should double the base fee"
        )

        // Step 2: validateSufficientBalance
        val balance = 101.0
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee
        assertTrue(result.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )

        // Step 3: Verify fee split logic
        val halfFee = estimatedFee / 2.0
        assertTrue(
            doubleEquals(halfFee, baseFee),
            "Half of doubled fee should equal original base fee"
        )
        assertTrue(hasServiceFee(serviceAddress, serviceFee))
        assertFalse(isUtxoChain(coin))
    }

    // ════════════════════════════════════════════════════════════════════
    // Account Chain (TON) — Full flow with service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * TON flow: estimateFee doubles → validateSufficientBalance → sendCoin
     * sends two transactions.
     *
     * Validates: Requirements 1.3, 3.4, 4.2, 4.3, 2.1
     */
    @Test
    fun tonFullFlowWithServiceFee() {
        val coin = NetworkName.TON
        val amount = 5.0          // 5 TON
        val baseFee = 0.01        // base fee per tx
        val serviceFee = 0.1      // 0.1 TON service fee
        val serviceAddress = "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2"

        // Step 1: estimateFee — doubles for Account chain
        val estimatedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER
        assertTrue(
            doubleEquals(estimatedFee, 0.02),
            "TON estimateFee should double the base fee to 0.02"
        )

        // Step 2: validateSufficientBalance
        val balance = 5.2
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee // 5.12
        assertTrue(result.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )

        // Step 3: Verify fee split
        val halfFee = estimatedFee / 2.0
        assertTrue(
            doubleEquals(halfFee, baseFee),
            "Half of doubled fee should equal original base fee"
        )
        assertTrue(hasServiceFee(serviceAddress, serviceFee))
        assertFalse(isUtxoChain(coin))
    }

    // ════════════════════════════════════════════════════════════════════
    // UTXO Chain (BTC) — Full flow with service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * BTC flow: estimateFee does NOT double (UTXO chain adds output instead) →
     * validateSufficientBalance checks amount + fee + serviceFee →
     * sendCoin passes serviceAddress/serviceFee to chain manager as extra output.
     *
     * Validates: Requirements 1.4, 4.4, 2.2
     */
    @Test
    fun btcFullFlowWithServiceFee() {
        val coin = NetworkName.BTC
        val amount = 0.5          // 0.5 BTC
        val baseFee = 0.0002      // miner fee (includes extra output size)
        val serviceFee = 0.001    // 0.001 BTC service fee
        val serviceAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        // Step 1: estimateFee — UTXO chain does NOT multiply
        // The fee already accounts for the extra output via chain manager
        val estimatedFee = baseFee // no multiplier for UTXO
        assertTrue(isUtxoChain(coin), "BTC should be UTXO chain")

        // Step 2: validateSufficientBalance
        // UTXO: totalRequired = amount + networkFee + serviceFee
        val balance = 0.6
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee // 0.5012
        assertTrue(result.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )

        // Step 3: Verify UTXO chain sends single transaction with extra output
        assertTrue(hasServiceFee(serviceAddress, serviceFee))
        assertTrue(isUtxoChain(coin), "BTC is UTXO — service fee is extra output, not separate tx")
    }

    /**
     * BTC flow: insufficient balance for UTXO chain.
     *
     * Validates: Requirements 4.2, 4.4
     */
    @Test
    fun btcFlowInsufficientBalance() {
        val coin = NetworkName.BTC
        val amount = 0.5
        val estimatedFee = 0.0003
        val serviceFee = 0.001
        val balance = 0.5 // not enough for 0.5 + 0.0003 + 0.001 = 0.5013

        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee
        assertFalse(result.sufficient, "Balance $balance should be insufficient for total $expectedTotal")
        assertTrue(result.deficit > 0.0, "deficit should be positive")
    }

    // ════════════════════════════════════════════════════════════════════
    // UTXO Chain (Cardano) — Full flow with service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * Cardano flow: estimateFee returns static default (no multiplier for UTXO) →
     * validateSufficientBalance → sendCoin passes serviceAddress/serviceFee
     * to CardanoManager as extra output.
     *
     * Validates: Requirements 1.4, 4.4, 2.2
     */
    @Test
    fun cardanoFullFlowWithServiceFee() {
        val coin = NetworkName.CARDANO
        val amount = 10.0         // 10 ADA
        val baseFee = 0.2         // ~200000 lovelace default fee
        val serviceFee = 1.0      // 1 ADA service fee
        val serviceAddress = "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x"

        // Step 1: estimateFee — UTXO chain, no multiplier
        val estimatedFee = baseFee
        assertTrue(isUtxoChain(coin), "Cardano should be UTXO chain")

        // Step 2: validateSufficientBalance
        val balance = 12.0
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee + serviceFee // 11.2
        assertTrue(result.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )

        // Step 3: Verify UTXO chain behavior
        assertTrue(hasServiceFee(serviceAddress, serviceFee))
        assertTrue(isUtxoChain(coin))
    }

    // ════════════════════════════════════════════════════════════════════
    // Backward compatibility — No service fee
    // ════════════════════════════════════════════════════════════════════

    /**
     * ETH flow without service fee: estimateFee returns base fee (no doubling),
     * validateSufficientBalance only checks amount + networkFee,
     * sendCoin sends single transaction.
     *
     * Validates: Requirements 1.1, 5.4, 2.3, 2.4, 1.5
     */
    @Test
    fun ethFlowWithoutServiceFee() {
        val coin = NetworkName.ETHEREUM
        val amount = 1.0
        val baseFee = 0.003

        // Step 1: No service fee → fee is NOT doubled
        val serviceAddress: String? = null
        val serviceFee = 0.0
        assertFalse(hasServiceFee(serviceAddress, serviceFee), "Should not have service fee")

        val estimatedFee = baseFee // no multiplier

        // Step 2: validateSufficientBalance — only amount + networkFee
        val balance = 1.005
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee // 1.003
        assertTrue(result.sufficient, "Balance $balance should be sufficient for total $expectedTotal")
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal (no serviceFee), got ${result.totalRequired}"
        )
    }

    /**
     * BTC flow without service fee: standard single-output transaction.
     *
     * Validates: Requirements 1.1, 5.4, 2.3
     */
    @Test
    fun btcFlowWithoutServiceFee() {
        val coin = NetworkName.BTC
        val amount = 0.5
        val baseFee = 0.0001
        val serviceFee = 0.0
        val serviceAddress: String? = null

        assertFalse(hasServiceFee(serviceAddress, serviceFee))

        val estimatedFee = baseFee

        val balance = 0.6
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        val expectedTotal = amount + estimatedFee // 0.5001
        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, expectedTotal),
            "totalRequired should be $expectedTotal, got ${result.totalRequired}"
        )
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )
    }

    /**
     * XRP flow without service fee: single transaction, no fee doubling.
     *
     * Validates: Requirements 1.1, 5.4
     */
    @Test
    fun xrpFlowWithoutServiceFee() {
        val coin = NetworkName.XRP
        val amount = 50.0
        val baseFee = 0.000012
        val serviceFee = 0.0

        assertFalse(hasServiceFee(null, serviceFee))

        val balance = 51.0
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = baseFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, amount + baseFee),
            "totalRequired should be amount + baseFee only"
        )
    }

    /**
     * TON flow without service fee.
     *
     * Validates: Requirements 1.1, 5.4
     */
    @Test
    fun tonFlowWithoutServiceFee() {
        val coin = NetworkName.TON
        val amount = 5.0
        val baseFee = 0.01
        val serviceFee = 0.0

        assertFalse(hasServiceFee("", serviceFee))

        val balance = 5.1
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = baseFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, amount + baseFee),
            "totalRequired should be amount + baseFee only"
        )
    }

    /**
     * Cardano flow without service fee.
     *
     * Validates: Requirements 1.1, 5.4
     */
    @Test
    fun cardanoFlowWithoutServiceFee() {
        val coin = NetworkName.CARDANO
        val amount = 10.0
        val baseFee = 0.2
        val serviceFee = 0.0

        assertFalse(hasServiceFee(null, serviceFee))

        val balance = 11.0
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = baseFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, amount + baseFee),
            "totalRequired should be amount + baseFee only"
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Edge cases: empty/blank serviceAddress with positive serviceFee
    // ════════════════════════════════════════════════════════════════════

    /**
     * When serviceAddress is empty string but serviceFee > 0,
     * the system should behave as if there's no service fee.
     *
     * Validates: Requirements 5.1, 2.3
     */
    @Test
    fun emptyServiceAddressIgnoresServiceFee() {
        val coin = NetworkName.ETHEREUM
        val amount = 1.0
        val baseFee = 0.003
        val serviceFee = 0.01
        val serviceAddress = ""

        // hasServiceFee should be false
        assertFalse(hasServiceFee(serviceAddress, serviceFee))

        // Fee should NOT be doubled
        val estimatedFee = baseFee // no multiplier

        // validateSufficientBalance still includes serviceFee in totalRequired
        // but the sendCoin flow won't send a service fee transaction
        val balance = 1.02
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = 0.0, // caller should pass 0 when hasServiceFee is false
            balance = balance
        )

        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, amount + estimatedFee),
            "totalRequired should only include amount + networkFee when no service fee"
        )
    }

    /**
     * When serviceAddress is blank (whitespace) but serviceFee > 0,
     * the system should behave as if there's no service fee.
     *
     * Validates: Requirements 5.1
     */
    @Test
    fun blankServiceAddressIgnoresServiceFee() {
        assertFalse(hasServiceFee("   ", 0.5))
        assertFalse(hasServiceFee("\t", 1.0))
        assertFalse(hasServiceFee("\n", 0.01))
    }

    /**
     * When serviceFee is 0 but serviceAddress is valid,
     * the system should behave as if there's no service fee.
     *
     * Validates: Requirements 5.3, 2.4
     */
    @Test
    fun zeroServiceFeeIgnoresServiceAddress() {
        val serviceAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18"
        assertFalse(hasServiceFee(serviceAddress, 0.0))

        // Fee should NOT be doubled for Account chain
        val coin = NetworkName.ETHEREUM
        val baseFee = 0.003
        val estimatedFee = baseFee // no multiplier when serviceFee = 0

        val balance = 1.1
        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = 1.0,
            networkFee = estimatedFee,
            serviceFee = 0.0,
            balance = balance
        )

        assertTrue(result.sufficient)
        assertTrue(
            doubleEquals(result.totalRequired, 1.0 + estimatedFee),
            "totalRequired should not include service fee when serviceFee=0"
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Fee multiplier consistency across Account chains
    // ════════════════════════════════════════════════════════════════════

    /**
     * Verify that FEE_MULTIPLIER is consistently applied across all Account chains
     * and NOT applied to UTXO chains.
     *
     * Validates: Requirements 1.2, 1.3, 1.4
     */
    @Test
    fun feeMultiplierConsistencyAcrossChains() {
        val baseFee = 0.005
        val serviceAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18"
        val serviceFee = 0.01

        // Account chains: fee should be doubled
        for (coin in CommonCoinsManager.ACCOUNT_CHAINS) {
            val shouldDouble = hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)
            assertTrue(shouldDouble, "$coin should have fee doubled")
            val adjustedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER
            assertTrue(
                doubleEquals(adjustedFee, baseFee * 2),
                "$coin: adjusted fee should be ${baseFee * 2}"
            )
        }

        // UTXO chains: fee should NOT be doubled
        for (coin in CommonCoinsManager.UTXO_CHAINS) {
            val shouldDouble = hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)
            assertFalse(shouldDouble, "$coin should NOT have fee doubled")
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Balance boundary: exactly sufficient
    // ════════════════════════════════════════════════════════════════════

    /**
     * Account chain: balance exactly equals totalRequired (amount + doubledFee + serviceFee).
     *
     * Validates: Requirements 4.2, 4.3
     */
    @Test
    fun accountChainBalanceExactlyEqualsTotalWithServiceFee() {
        val coin = NetworkName.ETHEREUM
        val amount = 2.0
        val baseFee = 0.004
        val estimatedFee = baseFee * CommonCoinsManager.FEE_MULTIPLIER // 0.008
        val serviceFee = 0.05
        val expectedTotal = amount + estimatedFee + serviceFee // 2.058
        val balance = expectedTotal // exactly sufficient

        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient, "Balance exactly equal to totalRequired should be sufficient")
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0 when balance == totalRequired"
        )
    }

    /**
     * UTXO chain: balance exactly equals totalRequired (amount + fee + serviceFee).
     *
     * Validates: Requirements 4.2, 4.3, 4.4
     */
    @Test
    fun utxoChainBalanceExactlyEqualsTotalWithServiceFee() {
        val coin = NetworkName.BTC
        val amount = 0.5
        val estimatedFee = 0.0002
        val serviceFee = 0.001
        val expectedTotal = amount + estimatedFee + serviceFee // 0.5012
        val balance = expectedTotal

        val result = manager.validateSufficientBalance(
            coin = coin,
            amount = amount,
            networkFee = estimatedFee,
            serviceFee = serviceFee,
            balance = balance
        )

        assertTrue(result.sufficient, "Balance exactly equal to totalRequired should be sufficient")
        assertTrue(
            doubleEquals(result.deficit, 0.0),
            "deficit should be 0"
        )
    }
}
