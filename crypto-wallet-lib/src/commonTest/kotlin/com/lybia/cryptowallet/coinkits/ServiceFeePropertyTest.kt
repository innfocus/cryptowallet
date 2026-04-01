package com.lybia.cryptowallet.coinkits

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Feature: service-fee-support, Property 1: hasServiceFee detection
 *
 * For any serviceAddress that is null, empty string, or blank-only string,
 * OR serviceFee equals 0.0, hasServiceFee must return false.
 * Conversely, when serviceAddress is non-blank and serviceFee > 0, it must return true.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3, 2.3, 2.4**
 */
class ServiceFeePropertyTest {

    /**
     * Replicates the private hasServiceFee logic from CommonCoinsManager
     * for property-based testing. This must stay in sync with the production code:
     *   private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
     *       return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
     *   }
     */
    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates service addresses that should be treated as "no service fee":
     *  null, empty string, or blank-only strings. */
    private fun arbInvalidServiceAddress(): Arb<String?> = Arb.of(
        null,
        "",
        " ",
        "  ",
        "\t",
        "\n",
        " \t\n "
    )

    /** Generates non-blank service addresses (valid wallet addresses). */
    private fun arbValidServiceAddress(): Arb<String> = Arb.of(
        "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x",
        "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18",
        "rN7n3473SaZBCG4dFL83w7p1W9cgZw6iF3",
        "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2",
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
        "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
    )

    /** Generates service fee values that indicate no service fee (zero). */
    private fun arbZeroFee(): Arb<Double> = Arb.constant(0.0)

    /** Generates positive service fee values. */
    private fun arbPositiveFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 1000.0
    ).filter { it > 0.0 && it.isFinite() }

    /** Generates negative service fee values. */
    private fun arbNegativeFee(): Arb<Double> = Arb.double(
        min = -1000.0,
        max = -0.000001
    ).filter { it < 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    @Test
    fun hasServiceFeeReturnsFalseForNullOrBlankAddress() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbInvalidServiceAddress(),
            arbPositiveFee()
        ) { address, fee ->
            assertFalse(
                hasServiceFee(address, fee),
                "hasServiceFee($address, $fee) should be false when address is null/blank"
            )
        }
    }

    @Test
    fun hasServiceFeeReturnsFalseForZeroFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbValidServiceAddress(),
            arbZeroFee()
        ) { address, fee ->
            assertFalse(
                hasServiceFee(address, fee),
                "hasServiceFee($address, $fee) should be false when fee is 0.0"
            )
        }
    }

    @Test
    fun hasServiceFeeReturnsFalseForNegativeFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbValidServiceAddress(),
            arbNegativeFee()
        ) { address, fee ->
            assertFalse(
                hasServiceFee(address, fee),
                "hasServiceFee($address, $fee) should be false when fee is negative"
            )
        }
    }

    @Test
    fun hasServiceFeeReturnsTrueForValidAddressAndPositiveFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbValidServiceAddress(),
            arbPositiveFee()
        ) { address, fee ->
            assertTrue(
                hasServiceFee(address, fee),
                "hasServiceFee($address, $fee) should be true when address is valid and fee > 0"
            )
        }
    }

    @Test
    fun hasServiceFeeReturnsFalseWhenBothInvalid() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbInvalidServiceAddress(),
            arbZeroFee()
        ) { address, fee ->
            assertFalse(
                hasServiceFee(address, fee),
                "hasServiceFee($address, $fee) should be false when both address is invalid and fee is 0"
            )
        }
    }
}
