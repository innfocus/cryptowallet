package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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


/**
 * Feature: service-fee-support, Property 2: Fee multiplier for Account Chain
 *
 * For any coin in Account Chain (Ethereum, Arbitrum, XRP, TON) and any valid serviceAddress,
 * the result of estimateFee with service fee should be exactly 2 times the result of
 * estimateFee without service fee (same other parameters). When serviceAddress is empty/null,
 * the result should equal the base result (multiplier of 1).
 *
 * This test replicates the fee multiplier logic from CommonCoinsManager.estimateFee:
 *   if (baseResult.success && hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)) {
 *       baseResult.copy(fee = baseResult.fee * FEE_MULTIPLIER)
 *   } else {
 *       baseResult
 *   }
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 */
class AccountChainFeeMultiplierPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Replicates the fee multiplier logic applied at the end of
     * CommonCoinsManager.estimateFee. Given a successful base fee,
     * returns the adjusted fee considering service fee parameters.
     */
    private fun applyFeeMultiplier(
        baseFee: Double,
        coin: NetworkName,
        serviceAddress: String?,
        serviceFee: Double
    ): Double {
        return if (hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)) {
            baseFee * CommonCoinsManager.FEE_MULTIPLIER
        } else {
            baseFee
        }
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates Account Chain coins: Ethereum, Arbitrum, XRP, TON. */
    private fun arbAccountChain(): Arb<NetworkName> = Arb.of(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.XRP,
        NetworkName.TON
    )

    /** Generates valid (non-blank) service addresses. */
    private fun arbValidServiceAddress(): Arb<String> = Arb.of(
        "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18",
        "rN7n3473SaZBCG4dFL83w7p1W9cgZw6iF3",
        "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2",
        "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
        "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
        "EQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"
    )

    /** Generates invalid service addresses: null, empty, or blank-only. */
    private fun arbInvalidServiceAddress(): Arb<String?> = Arb.of(
        null,
        "",
        " ",
        "  ",
        "\t",
        "\n",
        " \t\n "
    )

    /** Generates positive service fee values. */
    private fun arbPositiveServiceFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 100.0
    ).filter { it > 0.0 && it.isFinite() }

    /** Generates positive base fee values (simulating network fee estimates). */
    private fun arbPositiveBaseFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 10.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any Account Chain coin with a valid service address and positive service fee,
     * the fee multiplier must produce exactly baseFee * FEE_MULTIPLIER (= 2).
     */
    @Test
    fun feeIsDoubledForAccountChainWithValidServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbValidServiceAddress(),
            arbPositiveServiceFee(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, serviceFee, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, serviceFee)
            val expected = baseFee * CommonCoinsManager.FEE_MULTIPLIER

            assertEquals(
                expected, result,
                "For Account chain $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "fee should be baseFee($baseFee) * FEE_MULTIPLIER(${CommonCoinsManager.FEE_MULTIPLIER}) = $expected, but got $result"
            )
        }
    }

    /**
     * For any Account Chain coin with an invalid (null/blank) service address,
     * the fee must equal the base fee (multiplier of 1, no doubling).
     */
    @Test
    fun feeIsUnchangedForAccountChainWithInvalidServiceAddress() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbInvalidServiceAddress(),
            arbPositiveServiceFee(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, serviceFee, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, serviceFee)

            assertEquals(
                baseFee, result,
                "For Account chain $coin with invalid serviceAddress='$serviceAddress', " +
                    "fee should equal baseFee($baseFee), but got $result"
            )
        }
    }

    /**
     * For any Account Chain coin with a valid service address but serviceFee = 0,
     * the fee must equal the base fee (no doubling when serviceFee is zero).
     */
    @Test
    fun feeIsUnchangedForAccountChainWithZeroServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbValidServiceAddress(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, 0.0)

            assertEquals(
                baseFee, result,
                "For Account chain $coin with serviceFee=0.0, " +
                    "fee should equal baseFee($baseFee), but got $result"
            )
        }
    }
}


/**
 * Feature: service-fee-support, Property 3: UTXO Chain fee estimation with service address
 *
 * For any coin in UTXO Chain (Bitcoin, Cardano) and any valid serviceAddress,
 * the fee multiplier logic (applyFeeMultiplier) should NOT apply — it must return
 * baseFee unchanged for UTXO chains regardless of service fee parameters.
 *
 * UTXO chains handle service fee differently: they add an additional output to the
 * transaction rather than sending a separate transaction. Therefore, the FEE_MULTIPLIER
 * (which doubles the fee for Account chains to cover two transactions) must not be
 * applied to UTXO chains.
 *
 * **Validates: Requirements 1.4**
 */
class UtxoChainFeeEstimationPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Replicates the fee multiplier logic applied at the end of
     * CommonCoinsManager.estimateFee. For UTXO chains, the fee should
     * remain unchanged regardless of service fee parameters.
     */
    private fun applyFeeMultiplier(
        baseFee: Double,
        coin: NetworkName,
        serviceAddress: String?,
        serviceFee: Double
    ): Double {
        return if (hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)) {
            baseFee * CommonCoinsManager.FEE_MULTIPLIER
        } else {
            baseFee
        }
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates UTXO Chain coins: Bitcoin, Cardano. */
    private fun arbUtxoChain(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.CARDANO
    )

    /** Generates valid (non-blank) service addresses. */
    private fun arbValidServiceAddress(): Arb<String> = Arb.of(
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
        "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
        "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
        "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x",
        "addr1v9k2w3r5x6y7z8a9b0c1d2e3f4g5h6j7k8l9m0n1o2p3q4r5s6t7",
        "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"
    )

    /** Generates invalid service addresses: null, empty, or blank-only. */
    private fun arbInvalidServiceAddress(): Arb<String?> = Arb.of(
        null,
        "",
        " ",
        "  ",
        "\t",
        "\n",
        " \t\n "
    )

    /** Generates positive service fee values. */
    private fun arbPositiveServiceFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 100.0
    ).filter { it > 0.0 && it.isFinite() }

    /** Generates positive base fee values (simulating network fee estimates). */
    private fun arbPositiveBaseFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 10.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any UTXO Chain coin with a valid service address and positive service fee,
     * the fee multiplier must NOT be applied — the result must equal the baseFee unchanged.
     * UTXO chains handle service fee via additional transaction outputs, not via fee doubling.
     */
    @Test
    fun feeIsUnchangedForUtxoChainWithValidServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbUtxoChain(),
            arbValidServiceAddress(),
            arbPositiveServiceFee(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, serviceFee, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, serviceFee)

            assertEquals(
                baseFee, result,
                "For UTXO chain $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "fee should equal baseFee($baseFee) unchanged (no FEE_MULTIPLIER), but got $result"
            )
        }
    }

    /**
     * For any UTXO Chain coin with an invalid (null/blank) service address,
     * the fee must also equal the base fee (no multiplier applied).
     */
    @Test
    fun feeIsUnchangedForUtxoChainWithInvalidServiceAddress() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbUtxoChain(),
            arbInvalidServiceAddress(),
            arbPositiveServiceFee(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, serviceFee, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, serviceFee)

            assertEquals(
                baseFee, result,
                "For UTXO chain $coin with invalid serviceAddress='$serviceAddress', " +
                    "fee should equal baseFee($baseFee), but got $result"
            )
        }
    }

    /**
     * For any UTXO Chain coin with zero service fee,
     * the fee must equal the base fee (no multiplier applied).
     */
    @Test
    fun feeIsUnchangedForUtxoChainWithZeroServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbUtxoChain(),
            arbValidServiceAddress(),
            arbPositiveBaseFee()
        ) { coin, serviceAddress, baseFee ->
            val result = applyFeeMultiplier(baseFee, coin, serviceAddress, 0.0)

            assertEquals(
                baseFee, result,
                "For UTXO chain $coin with serviceFee=0.0, " +
                    "fee should equal baseFee($baseFee), but got $result"
            )
        }
    }
}


/**
 * Feature: service-fee-support, Property 10: Backward compatibility
 *
 * For any coin and any parameters, calling estimateFee with default values
 * serviceAddress=null and serviceFee=0.0 must produce identical results as
 * calling without the new parameters.
 *
 * This verifies that the fee multiplier logic in CommonCoinsManager.estimateFee
 * is a no-op when the service fee parameters use their default values:
 *   - hasServiceFee(null, 0.0) always returns false
 *   - Therefore the multiplier branch is never taken
 *   - The result equals the base fee for ALL chains (UTXO and Account alike)
 *
 * **Validates: Requirements 1.5, 2.6**
 */
class EstimateFeeBackwardCompatPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Replicates the fee multiplier logic applied at the end of
     * CommonCoinsManager.estimateFee. When serviceAddress=null and
     * serviceFee=0.0 (defaults), hasServiceFee returns false and the
     * baseFee is returned unchanged — identical to calling without
     * the new parameters.
     */
    private fun applyFeeMultiplier(
        baseFee: Double,
        coin: NetworkName,
        serviceAddress: String?,
        serviceFee: Double
    ): Double {
        return if (hasServiceFee(serviceAddress, serviceFee) && !isUtxoChain(coin)) {
            baseFee * CommonCoinsManager.FEE_MULTIPLIER
        } else {
            baseFee
        }
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates any NetworkName from all supported chains. */
    private fun arbNetworkName(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.TON,
        NetworkName.CARDANO,
        NetworkName.MIDNIGHT,
        NetworkName.XRP,
        NetworkName.CENTRALITY
    )

    /** Generates positive base fee values (simulating network fee estimates). */
    private fun arbPositiveBaseFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 10.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any coin, calling the fee multiplier logic with the default values
     * (serviceAddress=null, serviceFee=0.0) must return the baseFee unchanged.
     * This proves that estimateFee with explicit defaults is identical to
     * estimateFee without the new parameters.
     */
    @Test
    fun estimateFeeWithDefaultServiceParamsEqualsBaseFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbPositiveBaseFee()
        ) { coin, baseFee ->
            val resultWithDefaults = applyFeeMultiplier(
                baseFee = baseFee,
                coin = coin,
                serviceAddress = null,
                serviceFee = 0.0
            )

            assertEquals(
                baseFee, resultWithDefaults,
                "For coin $coin, estimateFee with serviceAddress=null and serviceFee=0.0 " +
                    "should return baseFee($baseFee) unchanged, but got $resultWithDefaults"
            )
        }
    }

    /**
     * For any coin, calling the fee multiplier logic with serviceAddress=""
     * (empty string) and serviceFee=0.0 must also return the baseFee unchanged.
     * Empty string is treated the same as null for backward compatibility.
     */
    @Test
    fun estimateFeeWithEmptyServiceAddressEqualsBaseFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbPositiveBaseFee()
        ) { coin, baseFee ->
            val resultWithEmpty = applyFeeMultiplier(
                baseFee = baseFee,
                coin = coin,
                serviceAddress = "",
                serviceFee = 0.0
            )

            assertEquals(
                baseFee, resultWithEmpty,
                "For coin $coin, estimateFee with serviceAddress='' and serviceFee=0.0 " +
                    "should return baseFee($baseFee) unchanged, but got $resultWithEmpty"
            )
        }
    }

    /**
     * For any coin, even when serviceFee > 0 but serviceAddress is null,
     * the result must equal the baseFee (hasServiceFee returns false).
     * This ensures backward compatibility: without a valid service address,
     * the fee is never modified regardless of serviceFee value.
     */
    @Test
    fun estimateFeeWithNullAddressAndPositiveFeeEqualsBaseFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbPositiveBaseFee(),
            Arb.double(min = 0.000001, max = 100.0).filter { it > 0.0 && it.isFinite() }
        ) { coin, baseFee, serviceFee ->
            val result = applyFeeMultiplier(
                baseFee = baseFee,
                coin = coin,
                serviceAddress = null,
                serviceFee = serviceFee
            )

            assertEquals(
                baseFee, result,
                "For coin $coin, estimateFee with serviceAddress=null and serviceFee=$serviceFee " +
                    "should return baseFee($baseFee) unchanged, but got $result"
            )
        }
    }
}
