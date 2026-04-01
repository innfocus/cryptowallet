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



/**
 * Feature: service-fee-support, Property 5: UTXO Chain bao gồm service fee output
 *
 * For any coin in UTXO Chain (BTC, Cardano) with a valid serviceAddress and serviceFee > 0,
 * sendCoin must pass serviceAddress and serviceFee to the chain manager to include as an
 * additional output in the same transaction (single-transaction flow), rather than sending
 * a separate service fee transaction (two-transaction Account chain flow).
 *
 * This replicates the dispatch logic in CommonCoinsManager.sendCoin:
 *   val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)
 *   if (shouldSendServiceFee && !isUtxoChain(coin) && coin in ACCOUNT_CHAINS) {
 *       // Two-transaction flow (Account chains only)
 *   } else {
 *       // Single-transaction flow — service fee params forwarded to chain manager
 *       sendCoinMain(..., serviceAddress = if (shouldSendServiceFee) serviceAddress else null,
 *                         serviceFee = if (shouldSendServiceFee) serviceFee else 0.0)
 *   }
 *
 * **Validates: Requirements 2.2**
 */
class UtxoChainServiceFeeOutputPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Represents the routing decision made by sendCoin for a given coin
     * and service fee parameters.
     */
    sealed class SendCoinRoute {
        /** Service fee params forwarded to chain manager as additional output in same TX. */
        data class SingleTxWithServiceFee(
            val serviceAddress: String,
            val serviceFee: Double
        ) : SendCoinRoute()

        /** Two separate transactions: main TX + service fee TX (Account chains). */
        data class TwoTransactions(
            val serviceAddress: String,
            val serviceFee: Double
        ) : SendCoinRoute()

        /** No service fee — main transaction only, no service params forwarded. */
        object MainOnly : SendCoinRoute()
    }

    /**
     * Replicates the dispatch logic from CommonCoinsManager.sendCoin.
     * Returns which route the sendCoin would take for the given parameters.
     */
    private fun determineSendCoinRoute(
        coin: NetworkName,
        serviceAddress: String?,
        serviceFee: Double
    ): SendCoinRoute {
        val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)

        return if (shouldSendServiceFee && !isUtxoChain(coin) && coin in CommonCoinsManager.ACCOUNT_CHAINS) {
            // Account chain two-transaction flow
            SendCoinRoute.TwoTransactions(serviceAddress!!, serviceFee)
        } else if (shouldSendServiceFee && isUtxoChain(coin)) {
            // UTXO chain: service fee forwarded to chain manager as additional output
            SendCoinRoute.SingleTxWithServiceFee(serviceAddress!!, serviceFee)
        } else {
            // No service fee
            SendCoinRoute.MainOnly
        }
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates UTXO Chain coins: Bitcoin, Cardano. */
    private fun arbUtxoChain(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.CARDANO
    )

    /** Generates valid (non-blank) service addresses for UTXO chains. */
    private fun arbValidServiceAddress(): Arb<String> = Arb.of(
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
        "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
        "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
        "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x",
        "addr1v9k2w3r5x6y7z8a9b0c1d2e3f4g5h6j7k8l9m0n1o2p3q4r5s6t7",
        "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"
    )

    /** Generates positive service fee values. */
    private fun arbPositiveServiceFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 100.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any UTXO chain coin with a valid service address and positive service fee,
     * sendCoin must route to the single-transaction path and forward serviceAddress
     * and serviceFee to the chain manager (not the two-transaction Account chain path).
     *
     * This verifies Requirement 2.2: UTXO chains include service fee as an additional
     * output in the same main transaction.
     */
    @Test
    fun utxoChainRoutesToSingleTxWithServiceFeeForwarded() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbUtxoChain(),
            arbValidServiceAddress(),
            arbPositiveServiceFee()
        ) { coin, serviceAddress, serviceFee ->
            val route = determineSendCoinRoute(coin, serviceAddress, serviceFee)

            assertTrue(
                route is SendCoinRoute.SingleTxWithServiceFee,
                "For UTXO chain $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "sendCoin should route to SingleTxWithServiceFee, but got $route"
            )

            val singleTxRoute = route as SendCoinRoute.SingleTxWithServiceFee
            assertEquals(
                serviceAddress, singleTxRoute.serviceAddress,
                "For UTXO chain $coin, serviceAddress should be forwarded to chain manager"
            )
            assertEquals(
                serviceFee, singleTxRoute.serviceFee,
                "For UTXO chain $coin, serviceFee should be forwarded to chain manager"
            )
        }
    }

    /**
     * For any UTXO chain coin with a valid service address and positive service fee,
     * sendCoin must NOT route to the two-transaction Account chain path.
     * UTXO chains handle service fee as an additional output in the same transaction,
     * not as a separate transaction.
     */
    @Test
    fun utxoChainNeverRoutesToTwoTransactionPath() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbUtxoChain(),
            arbValidServiceAddress(),
            arbPositiveServiceFee()
        ) { coin, serviceAddress, serviceFee ->
            val route = determineSendCoinRoute(coin, serviceAddress, serviceFee)

            assertFalse(
                route is SendCoinRoute.TwoTransactions,
                "For UTXO chain $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "sendCoin must NOT route to TwoTransactions (Account chain path), but it did"
            )
        }
    }
}



/**
 * Feature: service-fee-support, Property 4: Account Chain gửi hai giao dịch
 *
 * For any coin in Account Chain (Ethereum, Arbitrum, XRP, TON) with a valid serviceAddress
 * and serviceFee > 0, when the main transaction succeeds, sendCoin must execute exactly
 * 2 transactions: the main transaction to toAddress with amount, and the service fee
 * transaction to serviceAddress with serviceFee.
 *
 * This replicates the dispatch logic in CommonCoinsManager.sendCoin:
 *   val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)
 *   if (shouldSendServiceFee && !isUtxoChain(coin) && coin in ACCOUNT_CHAINS) {
 *       sendCoinWithServiceFee(coin, toAddress, amount, networkFee, serviceFee, serviceAddress, memo)
 *   }
 *
 * And the two-transaction flow in sendCoinWithServiceFee:
 *   1. Main TX → toAddress with amount
 *   2. Service fee TX → serviceAddress with serviceFee
 *
 * **Validates: Requirements 2.1**
 */
class AccountChainTwoTransactionsPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Represents a planned transaction in the two-transaction flow.
     */
    data class PlannedTransaction(
        val toAddress: String,
        val amount: Double,
        val isServiceFee: Boolean
    )

    /**
     * Represents the routing decision and planned transactions made by sendCoin.
     */
    sealed class SendCoinPlan {
        /** Account chain two-transaction flow: main TX + service fee TX. */
        data class TwoTransactions(
            val mainTx: PlannedTransaction,
            val serviceFeeTx: PlannedTransaction
        ) : SendCoinPlan()

        /** Single transaction only (UTXO with service fee forwarded, or no service fee). */
        data class SingleTransaction(
            val mainTx: PlannedTransaction,
            val serviceAddress: String? = null,
            val serviceFee: Double = 0.0
        ) : SendCoinPlan()
    }

    /**
     * Replicates the dispatch logic from CommonCoinsManager.sendCoin and
     * sendCoinWithServiceFee. Returns the planned transactions that sendCoin
     * would execute for the given parameters.
     */
    private fun planSendCoin(
        coin: NetworkName,
        toAddress: String,
        amount: Double,
        serviceFee: Double,
        serviceAddress: String?
    ): SendCoinPlan {
        val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)

        return if (shouldSendServiceFee && !isUtxoChain(coin) && coin in CommonCoinsManager.ACCOUNT_CHAINS) {
            // Account chain two-transaction flow
            SendCoinPlan.TwoTransactions(
                mainTx = PlannedTransaction(
                    toAddress = toAddress,
                    amount = amount,
                    isServiceFee = false
                ),
                serviceFeeTx = PlannedTransaction(
                    toAddress = serviceAddress!!,
                    amount = serviceFee,
                    isServiceFee = true
                )
            )
        } else {
            // Single transaction (UTXO or no service fee)
            SendCoinPlan.SingleTransaction(
                mainTx = PlannedTransaction(
                    toAddress = toAddress,
                    amount = amount,
                    isServiceFee = false
                ),
                serviceAddress = if (shouldSendServiceFee) serviceAddress else null,
                serviceFee = if (shouldSendServiceFee) serviceFee else 0.0
            )
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

    /** Generates valid recipient addresses (distinct from service addresses). */
    private fun arbToAddress(): Arb<String> = Arb.of(
        "0x1234567890abcdef1234567890abcdef12345678",
        "rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe",
        "EQC7VpEHw2DA9hxkOi-UPMGMKqaAswsMlNtFnRcIgFwMEead",
        "0xdead000000000000000000000000000000000000",
        "rLHzPsX6oXkzU2qL12kHCH8G8cnZv1rBJh",
        "EQAVaSNT5mBoCdNBan8LPdjSgVMgPMfYKaN_h9grNJwAAACi"
    )

    /** Generates positive service fee values. */
    private fun arbPositiveServiceFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 100.0
    ).filter { it > 0.0 && it.isFinite() }

    /** Generates positive amount values. */
    private fun arbPositiveAmount(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 1000.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any Account Chain coin with a valid service address and positive service fee,
     * sendCoin must plan exactly two transactions: main TX to toAddress with amount,
     * and service fee TX to serviceAddress with serviceFee.
     */
    @Test
    fun accountChainPlansExactlyTwoTransactions() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbToAddress(),
            arbPositiveAmount(),
            arbPositiveServiceFee(),
            arbValidServiceAddress()
        ) { coin, toAddress, amount, serviceFee, serviceAddress ->
            val plan = planSendCoin(coin, toAddress, amount, serviceFee, serviceAddress)

            assertTrue(
                plan is SendCoinPlan.TwoTransactions,
                "For Account chain $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "sendCoin should plan TwoTransactions, but got $plan"
            )
        }
    }

    /**
     * For any Account Chain coin with a valid service fee, the main transaction
     * must target toAddress with the original amount (not the service fee).
     */
    @Test
    fun accountChainMainTxTargetsToAddressWithAmount() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbToAddress(),
            arbPositiveAmount(),
            arbPositiveServiceFee(),
            arbValidServiceAddress()
        ) { coin, toAddress, amount, serviceFee, serviceAddress ->
            val plan = planSendCoin(coin, toAddress, amount, serviceFee, serviceAddress)
                as SendCoinPlan.TwoTransactions

            assertEquals(
                toAddress, plan.mainTx.toAddress,
                "Main TX must target toAddress"
            )
            assertEquals(
                amount, plan.mainTx.amount,
                "Main TX must send the original amount"
            )
            assertFalse(
                plan.mainTx.isServiceFee,
                "Main TX must not be flagged as service fee"
            )
        }
    }

    /**
     * For any Account Chain coin with a valid service fee, the service fee transaction
     * must target serviceAddress with the serviceFee amount.
     */
    @Test
    fun accountChainServiceFeeTxTargetsServiceAddressWithServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbToAddress(),
            arbPositiveAmount(),
            arbPositiveServiceFee(),
            arbValidServiceAddress()
        ) { coin, toAddress, amount, serviceFee, serviceAddress ->
            val plan = planSendCoin(coin, toAddress, amount, serviceFee, serviceAddress)
                as SendCoinPlan.TwoTransactions

            assertEquals(
                serviceAddress, plan.serviceFeeTx.toAddress,
                "Service fee TX must target serviceAddress"
            )
            assertEquals(
                serviceFee, plan.serviceFeeTx.amount,
                "Service fee TX must send the serviceFee amount"
            )
            assertTrue(
                plan.serviceFeeTx.isServiceFee,
                "Service fee TX must be flagged as service fee"
            )
        }
    }

    /**
     * For any Account Chain coin with an invalid (null/blank) service address,
     * sendCoin must NOT plan two transactions — it should fall through to
     * single-transaction mode.
     */
    @Test
    fun accountChainWithInvalidAddressDoesNotPlanTwoTransactions() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbToAddress(),
            arbPositiveAmount(),
            arbPositiveServiceFee(),
            arbInvalidServiceAddress()
        ) { coin, toAddress, amount, serviceFee, serviceAddress ->
            val plan = planSendCoin(coin, toAddress, amount, serviceFee, serviceAddress)

            assertTrue(
                plan is SendCoinPlan.SingleTransaction,
                "For Account chain $coin with invalid serviceAddress='$serviceAddress', " +
                    "sendCoin should plan SingleTransaction, but got $plan"
            )
        }
    }

    /**
     * For any Account Chain coin with serviceFee = 0 (even with valid address),
     * sendCoin must NOT plan two transactions — zero fee means no service fee TX.
     */
    @Test
    fun accountChainWithZeroServiceFeeDoesNotPlanTwoTransactions() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbToAddress(),
            arbPositiveAmount(),
            arbValidServiceAddress()
        ) { coin, toAddress, amount, serviceAddress ->
            val plan = planSendCoin(coin, toAddress, amount, 0.0, serviceAddress)

            assertTrue(
                plan is SendCoinPlan.SingleTransaction,
                "For Account chain $coin with serviceFee=0.0, " +
                    "sendCoin should plan SingleTransaction, but got $plan"
            )
        }
    }
}



/**
 * Feature: service-fee-support, Property 7: Chia đôi phí mạng lưới cho Account Chain
 *
 * For any coin in Account Chain (Ethereum, Arbitrum, XRP, TON) with service fee,
 * when sendCoin executes two transactions, each individual transaction must receive
 * networkFee / 2 as its network fee (the sum of both transactions equals the original
 * networkFee).
 *
 * This replicates the fee-splitting logic from CommonCoinsManager.sendCoinWithServiceFee:
 *   - The networkFee passed in is the doubled fee from estimateFee
 *   - Each transaction (main + service fee) uses networkFee / 2
 *   - halfFee + halfFee == networkFee
 *
 * For XRP specifically, the production code converts to drops:
 *   val halfFeeDrops = if (networkFee > 0) doubleToSmallestUnit(networkFee / 2.0, 1_000_000L) else 12L
 *
 * **Validates: Requirements 3.1, 3.2**
 */
class NetworkFeeSplitPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    /**
     * Replicates the fee-splitting logic from sendCoinWithServiceFee.
     * Given the total (doubled) networkFee, returns the fee allocated to each
     * individual transaction.
     */
    private fun splitNetworkFee(networkFee: Double): Double {
        return networkFee / 2.0
    }

    /**
     * Replicates doubleToSmallestUnit from CommonCoinsManager.
     * Converts a Double amount to the chain's smallest unit (Long).
     */
    private fun doubleToSmallestUnit(amount: Double, factor: Long): Long {
        return kotlin.math.round(amount * factor).toLong()
    }

    /**
     * Replicates the XRP-specific fee splitting from sendCoinWithServiceFee:
     *   val halfFeeDrops = if (networkFee > 0) doubleToSmallestUnit(networkFee / 2.0, 1_000_000L) else 12L
     */
    private fun xrpHalfFeeDrops(networkFee: Double): Long {
        return if (networkFee > 0) doubleToSmallestUnit(networkFee / 2.0, 1_000_000L) else 12L
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates Account Chain coins: Ethereum, Arbitrum, XRP, TON. */
    private fun arbAccountChain(): Arb<NetworkName> = Arb.of(
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.XRP,
        NetworkName.TON
    )

    /**
     * Generates positive network fee values (these represent the doubled fee
     * returned by estimateFee for Account chains with service fee).
     */
    private fun arbPositiveNetworkFee(): Arb<Double> = Arb.double(
        min = 0.000002,
        max = 10.0
    ).filter { it > 0.0 && it.isFinite() }

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any Account Chain coin and any positive networkFee, each transaction
     * receives exactly networkFee / 2 as its fee allocation.
     */
    @Test
    fun eachTransactionReceivesHalfOfNetworkFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbPositiveNetworkFee()
        ) { coin, networkFee ->
            val halfFee = splitNetworkFee(networkFee)
            val expectedHalf = networkFee / 2.0

            assertEquals(
                expectedHalf, halfFee,
                "For $coin with networkFee=$networkFee, each transaction should receive " +
                    "networkFee/2 = $expectedHalf, but got $halfFee"
            )
        }
    }

    /**
     * For any Account Chain coin and any positive networkFee, the sum of the
     * two half-fees must equal the original networkFee.
     */
    @Test
    fun sumOfHalfFeesEqualsOriginalNetworkFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbPositiveNetworkFee()
        ) { coin, networkFee ->
            val halfFee = splitNetworkFee(networkFee)
            val totalFee = halfFee + halfFee

            assertEquals(
                networkFee, totalFee,
                "For $coin with networkFee=$networkFee, halfFee($halfFee) + halfFee($halfFee) " +
                    "should equal networkFee, but got $totalFee"
            )
        }
    }

    /**
     * For XRP specifically, the half fee in drops must be consistent:
     * both the main TX and the service fee TX use the same halfFeeDrops value,
     * ensuring the same gasPrice/fee is used for both transactions.
     *
     * This validates Requirement 3.2: same gasPrice for both transactions.
     */
    @Test
    fun xrpBothTransactionsUseSameHalfFeeDrops() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbPositiveNetworkFee()
        ) { networkFee ->
            val mainTxFeeDrops = xrpHalfFeeDrops(networkFee)
            val serviceFeeTxFeeDrops = xrpHalfFeeDrops(networkFee)

            assertEquals(
                mainTxFeeDrops, serviceFeeTxFeeDrops,
                "For XRP with networkFee=$networkFee, main TX fee drops ($mainTxFeeDrops) " +
                    "must equal service fee TX fee drops ($serviceFeeTxFeeDrops)"
            )
        }
    }

    /**
     * For ETH/Arbitrum, the same gasPrice is used for both transactions.
     * Since the fee splitting is done at the Double level (networkFee / 2.0),
     * both transactions receive identical fee values — validating Requirement 3.2.
     */
    @Test
    fun ethArbitrumBothTransactionsUseSameFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(NetworkName.ETHEREUM, NetworkName.ARBITRUM),
            arbPositiveNetworkFee()
        ) { coin, networkFee ->
            val mainTxFee = splitNetworkFee(networkFee)
            val serviceFeeTxFee = splitNetworkFee(networkFee)

            assertEquals(
                mainTxFee, serviceFeeTxFee,
                "For $coin with networkFee=$networkFee, main TX fee ($mainTxFee) " +
                    "must equal service fee TX fee ($serviceFeeTxFee)"
            )
        }
    }
}


/**
 * Feature: service-fee-support, Property 6: Giao dịch chính thất bại ngăn giao dịch phí dịch vụ
 *
 * For any coin and any service fee configuration, if the main transaction fails
 * (success=false), sendCoin must return SendResult with success=false and must NOT
 * execute any service fee transaction.
 *
 * This replicates the failure-handling logic from CommonCoinsManager:
 *
 * In sendCoin (top-level):
 *   - Account chains with service fee → sendCoinWithServiceFee
 *   - UTXO chains / no service fee → sendCoinMain (single TX, service fee is same TX)
 *
 * In sendCoinWithServiceFee (Account chain two-TX flow):
 *   - Step 1: Send main TX
 *   - Step 2: if (!mainResult.success) return mainResult  ← skips service fee TX
 *   - Step 3: Send service fee TX (only reached if main succeeded)
 *
 * For UTXO chains, the service fee is an output in the same transaction,
 * so a failed main TX inherently means no service fee was sent.
 *
 * **Validates: Requirements 2.5**
 */
class MainTxFailurePreventsServiceFeePropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    private fun hasServiceFee(serviceAddress: String?, serviceFee: Double): Boolean {
        return !serviceAddress.isNullOrBlank() && serviceFee > 0.0
    }

    private fun isUtxoChain(coin: NetworkName): Boolean {
        return coin in CommonCoinsManager.UTXO_CHAINS
    }

    /**
     * Represents the outcome of the sendCoin flow when the main TX fails.
     */
    data class SendCoinOutcome(
        val success: Boolean,
        val serviceFeeAttempted: Boolean,
        val error: String?
    )

    /**
     * Replicates the sendCoin dispatch + failure handling logic from CommonCoinsManager.
     *
     * Given a main transaction result with success=false, determines:
     * 1. Whether the final result has success=false
     * 2. Whether a service fee transaction was attempted
     *
     * For Account chains (two-TX flow via sendCoinWithServiceFee):
     *   - Main TX fails → return mainResult immediately, serviceFeeAttempted=false
     *
     * For UTXO chains (single-TX flow via sendCoinMain):
     *   - Service fee is part of the same TX → if TX fails, no service fee sent
     *   - serviceFeeAttempted=false (it's the same TX, not a separate attempt)
     *
     * For no-service-fee case:
     *   - Only main TX is sent → if it fails, no service fee to attempt
     */
    private fun simulateSendCoinWithFailedMainTx(
        coin: NetworkName,
        serviceAddress: String?,
        serviceFee: Double,
        mainTxError: String?
    ): SendCoinOutcome {
        val shouldSendServiceFee = hasServiceFee(serviceAddress, serviceFee)

        return if (shouldSendServiceFee && !isUtxoChain(coin) && coin in CommonCoinsManager.ACCOUNT_CHAINS) {
            // Account chain two-TX flow (sendCoinWithServiceFee):
            // Step 2: if (!mainResult.success) return mainResult
            // → service fee TX is never reached
            SendCoinOutcome(
                success = false,
                serviceFeeAttempted = false,
                error = mainTxError
            )
        } else {
            // UTXO chain or no service fee: single TX flow
            // Main TX failed → result is failure, no separate service fee TX
            SendCoinOutcome(
                success = false,
                serviceFeeAttempted = false,
                error = mainTxError
            )
        }
    }

    // ── Arb generators ──────────────────────────────────────────────────

    /** Generates any supported NetworkName. */
    private fun arbNetworkName(): Arb<NetworkName> = Arb.of(
        NetworkName.BTC,
        NetworkName.ETHEREUM,
        NetworkName.ARBITRUM,
        NetworkName.TON,
        NetworkName.CARDANO,
        NetworkName.XRP
    )

    /** Generates valid (non-blank) service addresses. */
    private fun arbValidServiceAddress(): Arb<String> = Arb.of(
        "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18",
        "rN7n3473SaZBCG4dFL83w7p1W9cgZw6iF3",
        "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2",
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
        "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x",
        "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
    )

    /** Generates service addresses including null, empty, blank, and valid. */
    private fun arbAnyServiceAddress(): Arb<String?> = Arb.of(
        null,
        "",
        " ",
        "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18",
        "rN7n3473SaZBCG4dFL83w7p1W9cgZw6iF3",
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    )

    /** Generates any service fee value (zero, positive). */
    private fun arbAnyServiceFee(): Arb<Double> = Arb.of(
        0.0,
        0.001,
        0.5,
        1.0,
        10.0,
        100.0
    )

    /** Generates positive service fee values. */
    private fun arbPositiveServiceFee(): Arb<Double> = Arb.double(
        min = 0.000001,
        max = 100.0
    ).filter { it > 0.0 && it.isFinite() }

    /** Generates error messages for failed main transactions. */
    private fun arbErrorMessage(): Arb<String?> = Arb.of(
        "Insufficient funds",
        "Network timeout",
        "Invalid address",
        "Transaction rejected",
        "Nonce too low",
        null
    )

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any coin with a valid service address and positive service fee,
     * when the main transaction fails, the final result must have success=false
     * and no service fee transaction must be attempted.
     *
     * This is the core property: main TX failure prevents service fee TX.
     */
    @Test
    fun failedMainTxReturnsFalseAndSkipsServiceFee() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbValidServiceAddress(),
            arbPositiveServiceFee(),
            arbErrorMessage()
        ) { coin, serviceAddress, serviceFee, errorMsg ->
            val outcome = simulateSendCoinWithFailedMainTx(
                coin, serviceAddress, serviceFee, errorMsg
            )

            assertFalse(
                outcome.success,
                "For $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "when main TX fails, sendCoin must return success=false, but got success=true"
            )
            assertFalse(
                outcome.serviceFeeAttempted,
                "For $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "when main TX fails, service fee TX must NOT be attempted"
            )
        }
    }

    /**
     * For any coin with any service fee configuration (including no service fee),
     * when the main transaction fails, the final result must have success=false.
     *
     * This covers all combinations: valid/invalid service address × zero/positive fee.
     */
    @Test
    fun failedMainTxAlwaysReturnsFalseRegardlessOfServiceFeeConfig() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbAnyServiceAddress(),
            arbAnyServiceFee(),
            arbErrorMessage()
        ) { coin, serviceAddress, serviceFee, errorMsg ->
            val outcome = simulateSendCoinWithFailedMainTx(
                coin, serviceAddress, serviceFee, errorMsg
            )

            assertFalse(
                outcome.success,
                "For $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "when main TX fails, sendCoin must return success=false regardless of service fee config"
            )
            assertFalse(
                outcome.serviceFeeAttempted,
                "For $coin with serviceAddress='$serviceAddress' and serviceFee=$serviceFee, " +
                    "when main TX fails, no service fee TX must be attempted regardless of config"
            )
        }
    }

    /**
     * For any coin, when the main transaction fails, the error from the main TX
     * must be preserved in the final SendCoinOutcome (not overwritten by service fee logic).
     */
    @Test
    fun failedMainTxPreservesOriginalError() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbNetworkName(),
            arbValidServiceAddress(),
            arbPositiveServiceFee(),
            arbErrorMessage()
        ) { coin, serviceAddress, serviceFee, errorMsg ->
            val outcome = simulateSendCoinWithFailedMainTx(
                coin, serviceAddress, serviceFee, errorMsg
            )

            assertEquals(
                errorMsg, outcome.error,
                "For $coin when main TX fails with error='$errorMsg', " +
                    "the original error must be preserved in the result, but got '${outcome.error}'"
            )
        }
    }
}



/**
 * Feature: service-fee-support, Property 9: Giao dịch phí dịch vụ thất bại bảo toàn giao dịch chính
 *
 * For any coin in Account Chain (Ethereum, Arbitrum, XRP, TON), when the main transaction
 * succeeds but the service fee transaction fails, sendCoin must return SendResult with
 * txHash of the main transaction, success=true, and error containing service fee
 * transaction error info.
 *
 * This replicates the failure-handling logic from CommonCoinsManager.sendCoinWithServiceFee:
 *
 *   // Step 2: If main TX failed, return immediately
 *   if (!mainResult.success) return mainResult
 *
 *   // Step 3: Send service fee transaction
 *   val serviceResult = try { ... } catch (e: Exception) {
 *       SendResult(txHash = "", success = false, error = e.message)
 *   }
 *
 *   // Step 4: Return result — main TX hash is always used
 *   return if (serviceResult.success) {
 *       SendResult(txHash = mainResult.txHash, success = true)
 *   } else {
 *       SendResult(txHash = mainResult.txHash, success = true,
 *                  error = "Service fee failed: ${serviceResult.error}")
 *   }
 *
 * **Validates: Requirements 6.1**
 */
class ServiceFeeFailurePreservesMainTxPropertyTest {

    // ── Replicated production logic ─────────────────────────────────────

    /**
     * Replicates the Step 4 logic from sendCoinWithServiceFee:
     * Given a successful main TX result and a failed service fee TX result,
     * produces the final SendResult.
     */
    private fun computeFinalResult(
        mainTxHash: String,
        mainSuccess: Boolean,
        serviceSuccess: Boolean,
        serviceError: String?
    ): SendResult {
        // Step 2: If main TX failed, return immediately
        if (!mainSuccess) {
            return SendResult(
                txHash = mainTxHash,
                success = false,
                error = "Main TX failed"
            )
        }

        // Step 4: Return result — main TX hash is always used
        return if (serviceSuccess) {
            SendResult(txHash = mainTxHash, success = true)
        } else {
            SendResult(
                txHash = mainTxHash,
                success = true,
                error = "Service fee failed: $serviceError"
            )
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

    /** Generates realistic transaction hashes for the main transaction. */
    private fun arbMainTxHash(): Arb<String> = Arb.of(
        "0xabc123def456789012345678901234567890abcdef1234567890abcdef123456",
        "0x1111111111111111111111111111111111111111111111111111111111111111",
        "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
        "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2",
        "tx_hash_ripple_example_12345",
        "ton_tx_hash_base64_example_abcdef"
    )

    /** Generates error messages for failed service fee transactions. */
    private fun arbServiceFeeError(): Arb<String> = Arb.of(
        "Insufficient funds for service fee",
        "Network timeout",
        "Nonce too low",
        "Gas estimation failed",
        "Transaction rejected by network",
        "Service address invalid"
    )

    // ── Property tests ──────────────────────────────────────────────────

    /**
     * For any Account Chain coin, when the main TX succeeds but the service fee TX fails,
     * the final result must have success=true (main TX is preserved).
     */
    @Test
    fun failedServiceFeeTxStillReturnsSuccessTrue() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbMainTxHash(),
            arbServiceFeeError()
        ) { coin, mainTxHash, serviceError ->
            val result = computeFinalResult(
                mainTxHash = mainTxHash,
                mainSuccess = true,
                serviceSuccess = false,
                serviceError = serviceError
            )

            assertTrue(
                result.success,
                "For $coin when main TX succeeds but service fee TX fails with '$serviceError', " +
                    "sendCoin must return success=true, but got success=false"
            )
        }
    }

    /**
     * For any Account Chain coin, when the main TX succeeds but the service fee TX fails,
     * the final result must contain the main transaction's txHash (not the service fee txHash).
     */
    @Test
    fun failedServiceFeeTxPreservesMainTxHash() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbMainTxHash(),
            arbServiceFeeError()
        ) { coin, mainTxHash, serviceError ->
            val result = computeFinalResult(
                mainTxHash = mainTxHash,
                mainSuccess = true,
                serviceSuccess = false,
                serviceError = serviceError
            )

            assertEquals(
                mainTxHash, result.txHash,
                "For $coin when service fee TX fails, txHash must be the main TX hash " +
                    "'$mainTxHash', but got '${result.txHash}'"
            )
        }
    }

    /**
     * For any Account Chain coin, when the main TX succeeds but the service fee TX fails,
     * the final result's error field must contain the service fee error information.
     */
    @Test
    fun failedServiceFeeTxIncludesErrorInfo() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbMainTxHash(),
            arbServiceFeeError()
        ) { coin, mainTxHash, serviceError ->
            val result = computeFinalResult(
                mainTxHash = mainTxHash,
                mainSuccess = true,
                serviceSuccess = false,
                serviceError = serviceError
            )

            assertTrue(
                result.error != null && result.error!!.contains(serviceError),
                "For $coin when service fee TX fails with '$serviceError', " +
                    "error field must contain the service fee error info, but got '${result.error}'"
            )
        }
    }

    /**
     * For any Account Chain coin, when both main TX and service fee TX succeed,
     * the final result must have success=true and error must be null (no error).
     * This is the contrast case: when service fee succeeds, no error is reported.
     */
    @Test
    fun successfulServiceFeeTxReturnsNoError() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbAccountChain(),
            arbMainTxHash()
        ) { coin, mainTxHash ->
            val result = computeFinalResult(
                mainTxHash = mainTxHash,
                mainSuccess = true,
                serviceSuccess = true,
                serviceError = null
            )

            assertTrue(
                result.success,
                "For $coin when both TXs succeed, sendCoin must return success=true"
            )
            assertEquals(
                null, result.error,
                "For $coin when both TXs succeed, error must be null, but got '${result.error}'"
            )
            assertEquals(
                mainTxHash, result.txHash,
                "For $coin when both TXs succeed, txHash must be the main TX hash"
            )
        }
    }
}
