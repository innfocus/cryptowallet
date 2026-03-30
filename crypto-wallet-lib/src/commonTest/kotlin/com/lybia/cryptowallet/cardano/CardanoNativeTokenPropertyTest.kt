package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import com.lybia.cryptowallet.wallets.cardano.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Property-based tests for Cardano Native Tokens, Min UTXO, and UTXO selection.
 */
class CardanoNativeTokenPropertyTest {

    // ---- Generators ----

    private val hexChars = ('0'..'9') + ('a'..'f')

    /** Generate a valid 56-char hex policy ID (28 bytes). */
    private fun arbPolicyId(): Arb<String> =
        Arb.list(Arb.element(hexChars), 56..56).map { it.joinToString("") }

    /** Generate a valid hex asset name (0-64 chars, even length). */
    private fun arbAssetName(): Arb<String> =
        Arb.int(0..32).flatMap { byteLen ->
            Arb.list(Arb.element(hexChars), (byteLen * 2)..(byteLen * 2)).map { it.joinToString("") }
        }

    /** Generate a positive token amount. */
    private fun arbTokenAmount(): Arb<Long> = Arb.long(1L..1_000_000_000L)

    /** Generate a valid CardanoNativeToken. */
    private fun arbNativeToken(): Arb<CardanoNativeToken> =
        Arb.bind(arbPolicyId(), arbAssetName(), arbTokenAmount()) { pid, name, amt ->
            CardanoNativeToken(pid, name, amt)
        }

    /** Generate a non-empty multi-asset map (1-3 policies, 1-3 assets each). */
    private fun arbMultiAssetMap(): Arb<Map<String, Map<String, Long>>> =
        Arb.int(1..3).flatMap { numPolicies ->
            Arb.list(
                Arb.bind(arbPolicyId(), Arb.int(1..3)) { pid, numAssets ->
                    pid to numAssets
                },
                numPolicies..numPolicies
            ).flatMap { policySpecs ->
                val generators = policySpecs.map { (pid, numAssets) ->
                    Arb.list(
                        Arb.bind(arbAssetName(), arbTokenAmount()) { name, amt -> name to amt },
                        numAssets..numAssets
                    ).map { assets -> pid to assets.toMap() }
                }
                Arb.bind(generators[0], if (generators.size > 1) generators[1] else Arb.constant(null),
                    if (generators.size > 2) generators[2] else Arb.constant(null)) { a, b, c ->
                    listOfNotNull(a, b, c).toMap()
                }
            }
        }

    /** Generate a random address byte array. */
    private fun arbAddressBytes(): Arb<ByteArray> =
        Arb.byteArray(Arb.int(29..57), Arb.byte())

    /** Generate a valid lovelace amount. */
    private fun arbLovelace(): Arb<Long> = Arb.long(1_000_000L..10_000_000_000L)

    /** Generate a valid coinsPerUtxoByte parameter. */
    private fun arbCoinsPerUtxoByte(): Arb<Long> = Arb.long(1000L..10000L)

    // ---- Property Tests ----

    // Feature: cardano-midnight-support, Property 12: Multi-Asset Output with Valid Token Identification
    // **Validates: Requirements 5.1, 5.2**
    @Test
    fun multiAssetOutputWithValidTokenIdentification() = runTest {
        checkAll(100, arbMultiAssetMap()) { assetsMap ->
            val multiAsset = CardanoMultiAsset(assetsMap)
            val cbor = multiAsset.toCbor()

            // Must be a CBOR map
            assertTrue(cbor is CborValue.CborMap, "Multi-asset CBOR should be a map")
            val outerMap = cbor as CborValue.CborMap

            // Number of outer entries should match number of policies
            assertEquals(assetsMap.size, outerMap.entries.size,
                "Outer map should have one entry per policy ID")

            // Verify nested structure
            for ((outerKey, outerVal) in outerMap.entries) {
                // Outer key should be a byte string (policy ID = 28 bytes)
                assertTrue(outerKey is CborValue.CborByteString,
                    "Policy ID should be a CBOR byte string")
                val policyBytes = (outerKey as CborValue.CborByteString).bytes
                assertEquals(28, policyBytes.size,
                    "Policy ID should be 28 bytes, got ${policyBytes.size}")

                // Outer value should be a map (asset name -> amount)
                assertTrue(outerVal is CborValue.CborMap,
                    "Inner value should be a CBOR map")
                val innerMap = outerVal as CborValue.CborMap

                for ((innerKey, innerVal) in innerMap.entries) {
                    // Inner key should be a byte string (asset name, max 32 bytes)
                    assertTrue(innerKey is CborValue.CborByteString,
                        "Asset name should be a CBOR byte string")
                    val assetBytes = (innerKey as CborValue.CborByteString).bytes
                    assertTrue(assetBytes.size <= 32,
                        "Asset name should be at most 32 bytes, got ${assetBytes.size}")

                    // Inner value should be a uint (amount)
                    assertTrue(innerVal is CborValue.CborUInt,
                        "Amount should be a CBOR unsigned integer")
                }
            }

            // Verify round-trip: encode to bytes and decode back
            val encoder = CborEncoder()
            val decoder = CborDecoder()
            val encoded = encoder.encode(cbor)
            assertTrue(encoded.isNotEmpty(), "Encoded CBOR should not be empty")
            val decoded = decoder.decode(encoded)
            assertTrue(decoded is CborValue.CborMap, "Decoded value should be a CBOR map")
        }
    }

    // Feature: cardano-midnight-support, Property 13: Minimum ADA Calculation for Multi-Asset Outputs
    // **Validates: Requirements 5.3**
    @Test
    fun minimumAdaCalculationForMultiAssetOutputs() = runTest {
        checkAll(100, arbAddressBytes(), arbLovelace(), arbCoinsPerUtxoByte()) { addr, lovelace, coinsPerUtxoByte ->
            // ADA-only output
            val adaOnlyOutput = CardanoTransactionOutput(addr, lovelace)
            val minAdaOnly = CardanoMinUtxo.calculateMinAda(adaOnlyOutput, coinsPerUtxoByte)

            // Must be at least 1 ADA (1_000_000 lovelace)
            assertTrue(minAdaOnly >= 1_000_000L,
                "Min ADA should be at least 1 ADA, got $minAdaOnly")

            // Create outputs with increasing number of tokens and verify monotonicity
            val policyId = ByteArray(28) { 0x01 }
            var prevMinAda = minAdaOnly

            for (numTokens in 1..3) {
                val multiAssets = mutableMapOf<ByteArray, Map<ByteArray, Long>>()
                for (i in 0 until numTokens) {
                    val assetName = ByteArray(4) { (i + 1).toByte() }
                    multiAssets[policyId] = (multiAssets[policyId] ?: emptyMap()) +
                        (assetName to 1_000_000L)
                }
                val multiAssetOutput = CardanoTransactionOutput(addr, lovelace, multiAssets)
                val minAdaMulti = CardanoMinUtxo.calculateMinAda(multiAssetOutput, coinsPerUtxoByte)

                // Min ADA should be at least 1 ADA
                assertTrue(minAdaMulti >= 1_000_000L,
                    "Min ADA for multi-asset should be at least 1 ADA, got $minAdaMulti")

                // Min ADA should increase monotonically with more tokens
                assertTrue(minAdaMulti >= prevMinAda,
                    "Min ADA should increase monotonically: $minAdaMulti < $prevMinAda for $numTokens tokens")

                prevMinAda = minAdaMulti
            }
        }
    }

    // Feature: cardano-midnight-support, Property 14: UTXO Selection Covers Required Amounts
    // **Validates: Requirements 5.6**
    @Test
    fun utxoSelectionCoversRequiredAmounts() = runTest {
        checkAll(100, arbPolicyId(), arbAssetName()) { policyId, assetName ->
            // Create UTXOs with known token amounts
            val utxos = (1..5).map { i ->
                CardanoUtxo(
                    txHash = "a".repeat(64),
                    index = i,
                    lovelace = 5_000_000L * i,
                    nativeTokens = listOf(
                        CardanoNativeToken(policyId, assetName, 100L * i)
                    )
                )
            }

            val totalTokens = utxos.sumOf { it.tokenAmount(policyId, assetName) }
            val totalAda = utxos.sumOf { it.lovelace }

            // Request half the available tokens and half the ADA
            val requiredTokens = totalTokens / 2
            val requiredAda = totalAda / 2

            val selected = CardanoUtxoSelector.selectUtxos(
                utxos, policyId, assetName, requiredTokens, requiredAda
            )

            // Selected UTXOs should cover required token amount
            val selectedTokens = selected.sumOf { it.tokenAmount(policyId, assetName) }
            assertTrue(selectedTokens >= requiredTokens,
                "Selected tokens ($selectedTokens) should cover required ($requiredTokens)")

            // Selected UTXOs should cover required ADA
            val selectedAda = selected.sumOf { it.lovelace }
            assertTrue(selectedAda >= requiredAda,
                "Selected ADA ($selectedAda) should cover required ($requiredAda)")

            // Selected should be a subset of input UTXOs
            assertTrue(selected.all { it in utxos },
                "Selected UTXOs should be a subset of input UTXOs")
        }
    }

    // Feature: cardano-midnight-support, Property 15: Insufficient Token Error with Deficit Info
    // **Validates: Requirements 5.7**
    @Test
    fun insufficientTokenErrorWithDeficitInfo() = runTest {
        checkAll(100, arbPolicyId(), arbAssetName(), arbTokenAmount()) { policyId, assetName, requiredAmount ->
            // Create UTXOs with less than required tokens
            val availableAmount = requiredAmount / 2  // Always less than required
            val utxos = if (availableAmount > 0) {
                listOf(
                    CardanoUtxo(
                        txHash = "b".repeat(64),
                        index = 0,
                        lovelace = 10_000_000L,
                        nativeTokens = listOf(
                            CardanoNativeToken(policyId, assetName, availableAmount)
                        )
                    )
                )
            } else {
                listOf(
                    CardanoUtxo(
                        txHash = "b".repeat(64),
                        index = 0,
                        lovelace = 10_000_000L,
                        nativeTokens = emptyList()
                    )
                )
            }

            val error = assertFailsWith<CardanoError.InsufficientTokens> {
                CardanoUtxoSelector.selectUtxos(
                    utxos, policyId, assetName, requiredAmount, 1_000_000L
                )
            }

            // Error should contain the available and required amounts
            assertEquals(policyId, error.policyId, "Error should contain policy ID")
            assertEquals(assetName, error.assetName, "Error should contain asset name")
            assertEquals(availableAmount, error.available, "Error should contain available amount")
            assertEquals(requiredAmount, error.required, "Error should contain required amount")
            assertTrue(error.available < error.required,
                "Available (${error.available}) should be less than required (${error.required})")
            assertTrue(error.message.isNotEmpty(), "Error message should not be empty")
        }
    }
}
