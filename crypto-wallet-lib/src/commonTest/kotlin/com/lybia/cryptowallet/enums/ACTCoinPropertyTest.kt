package com.lybia.cryptowallet.enums

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 2: ACTCoin enum equivalence
 *
 * For any ACTCoin value, all methods return the same values as the
 * androidMain original (with unitValue = unit().toDouble()).
 *
 * **Validates: Requirements 2.1, 2.2, 2.8**
 */
class ACTCoinPropertyTest {

    // Expected values hardcoded from androidMain ACTCoin enum
    private data class ExpectedCoinData(
        val nameCoin: String,
        val symbolName: String,
        val minimumValue: Double,
        val unitValue: Double,
        val regex: String,
        val algorithm: Algorithm,
        val baseApiUrl: String,
        val feeDefault: Double,
        val minimumAmount: Double,
        val supportMemo: Boolean,
        val allowNewAddress: Boolean
    )

    private val expectedValues = mapOf(
        ACTCoin.Bitcoin to ExpectedCoinData("Bitcoin", "BTC", 0.00001, 100_000_000.0, "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))", Algorithm.Secp256k1, "https://blockchain.info", 0.0, 0.0, false, true),
        ACTCoin.Ethereum to ExpectedCoinData("Ethereum", "ETH", 0.0001, 1_000_000_000_000_000_000.0, "(?:((0x|0X|)[a-fA-F0-9]{40,}))", Algorithm.Secp256k1, "", 0.0, 0.0, false, false),
        ACTCoin.Cardano to ExpectedCoinData("Cardano", "ADA", 1.0, 1_000_000.0, "(?:([a-km-zA-HJ-NP-Z1-9]{25,}))", Algorithm.Ed25519, "", 0.0, 0.0, false, true),
        ACTCoin.XCoin to ExpectedCoinData("X-Coin", "XCOIN", 0.0001, 1_000_000_000_000_000_000.0, "(?:((0x|0X|)[a-fA-F0-9]{40,}))", Algorithm.Secp256k1, "", 0.0, 0.0, false, false),
        ACTCoin.Ripple to ExpectedCoinData("Ripple", "XRP", 0.00001, 1_000_000.0, "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))", Algorithm.Secp256k1, "", 0.000012, 1.0, true, false),
        ACTCoin.Centrality to ExpectedCoinData("Centrality", "CENNZ", 0.01, 10_000.0, "(?:(5|[a-km-zA-HJ-NP-Z1-9]{47,}))", Algorithm.Sr25519, "", 15287.0, 0.0, false, false),
        ACTCoin.TON to ExpectedCoinData("TON", "TON", 0.01, 1_000_000_000.0, "(?:([a-km-zA-HJ-NP-Z1-9]{48,}))", Algorithm.Ed25519, "", 0.01, 0.0, true, false),
        ACTCoin.Midnight to ExpectedCoinData("Midnight", "tDUST", 0.01, 1_000_000.0, "(?:(midnight1[a-z0-9]{38,}))", Algorithm.Ed25519, "", 0.01, 0.0, false, false)
    )

    @Test
    fun actCoinEnumEquivalence() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.enum<ACTCoin>()) { coin ->
            val expected = expectedValues[coin]!!
            assertEquals(expected.nameCoin, coin.nameCoin(), "nameCoin mismatch for $coin")
            assertEquals(expected.symbolName, coin.symbolName(), "symbolName mismatch for $coin")
            assertEquals(expected.minimumValue, coin.minimumValue(), "minimumValue mismatch for $coin")
            assertEquals(expected.unitValue, coin.unitValue(), "unitValue mismatch for $coin")
            assertEquals(expected.regex, coin.regex(), "regex mismatch for $coin")
            assertEquals(expected.algorithm, coin.algorithm(), "algorithm mismatch for $coin")
            assertEquals(expected.baseApiUrl, coin.baseApiUrl(), "baseApiUrl mismatch for $coin")
            assertEquals(expected.feeDefault, coin.feeDefault(), "feeDefault mismatch for $coin")
            assertEquals(expected.minimumAmount, coin.minimumAmount(), "minimumAmount mismatch for $coin")
            assertEquals(expected.supportMemo, coin.supportMemo(), "supportMemo mismatch for $coin")
            assertEquals(expected.allowNewAddress, coin.allowNewAddress(), "allowNewAddress mismatch for $coin")
        }
    }
}
