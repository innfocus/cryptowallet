package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import com.lybia.cryptowallet.wallets.cardano.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for Cardano Native Token edge cases.
 */
class CardanoNativeTokenUnitTest {

    // ---- Edge case: empty token list ----

    @Test
    fun emptyMultiAssetMapProducesEmptyCborMap() {
        val multiAsset = CardanoMultiAsset(emptyMap())
        val cbor = multiAsset.toCbor()
        assertTrue(cbor is CborValue.CborMap, "Should be a CBOR map")
        val map = cbor as CborValue.CborMap
        assertTrue(map.entries.isEmpty(), "Empty multi-asset should produce empty CBOR map")
        assertEquals(0, multiAsset.tokenCount(), "Token count should be 0")
    }

    @Test
    fun emptyTokenListInUtxoSelection() {
        val policyId = "a".repeat(56)
        val assetName = "bb"
        val utxos = listOf(
            CardanoUtxo(
                txHash = "c".repeat(64),
                index = 0,
                lovelace = 5_000_000L,
                nativeTokens = emptyList()
            )
        )

        val error = assertFailsWith<CardanoError.InsufficientTokens> {
            CardanoUtxoSelector.selectUtxos(utxos, policyId, assetName, 100L, 1_000_000L)
        }
        assertEquals(0L, error.available)
        assertEquals(100L, error.required)
    }

    @Test
    fun utxoWithNoTokensReportsZeroTokenAmount() {
        val utxo = CardanoUtxo(
            txHash = "d".repeat(64),
            index = 0,
            lovelace = 2_000_000L,
            nativeTokens = emptyList()
        )
        assertEquals(0L, utxo.tokenAmount("a".repeat(56), "bb"))
    }

    // ---- Edge case: max policy ID (all f's) ----

    @Test
    fun maxPolicyIdIsValid() {
        val maxPolicyId = "f".repeat(56)
        val token = CardanoNativeToken(maxPolicyId, "00", 1L)
        assertEquals(maxPolicyId, token.policyId)
        assertTrue(token.fingerprint().startsWith("asset"), "Fingerprint should start with 'asset'")
    }

    @Test
    fun maxPolicyIdInMultiAsset() {
        val maxPolicyId = "f".repeat(56)
        val multiAsset = CardanoMultiAsset(
            mapOf(maxPolicyId to mapOf("00" to 1_000L))
        )
        val cbor = multiAsset.toCbor()
        assertTrue(cbor is CborValue.CborMap)
        val map = cbor as CborValue.CborMap
        assertEquals(1, map.entries.size)

        val policyBytes = (map.entries[0].first as CborValue.CborByteString).bytes
        assertTrue(policyBytes.all { it == 0xFF.toByte() },
            "Max policy ID bytes should all be 0xFF")
    }

    // ---- Edge case: max asset name (32 bytes = 64 hex chars) ----

    @Test
    fun maxAssetNameIsValid() {
        val policyId = "a".repeat(56)
        val maxAssetName = "f".repeat(64) // 32 bytes
        val token = CardanoNativeToken(policyId, maxAssetName, 1L)
        assertEquals(maxAssetName, token.assetName)
        assertTrue(token.fingerprint().startsWith("asset"))
    }

    @Test
    fun assetNameExceedingMaxIsRejected() {
        val policyId = "a".repeat(56)
        val tooLongAssetName = "a".repeat(66) // 33 bytes
        assertFailsWith<IllegalArgumentException> {
            CardanoNativeToken(policyId, tooLongAssetName, 1L)
        }
    }

    @Test
    fun emptyAssetNameIsValid() {
        val policyId = "a".repeat(56)
        val token = CardanoNativeToken(policyId, "", 1L)
        assertEquals("", token.assetName)
        assertTrue(token.fingerprint().startsWith("asset"))
    }

    // ---- Edge case: policy ID validation ----

    @Test
    fun invalidPolicyIdLengthIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            CardanoNativeToken("aa", "00", 1L)
        }
    }

    @Test
    fun invalidPolicyIdHexIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            CardanoNativeToken("g".repeat(56), "00", 1L)
        }
    }

    // ---- Min UTXO calculation ----

    @Test
    fun minAdaForAdaOnlyOutputIsAtLeastOneAda() {
        val output = CardanoTransactionOutput(ByteArray(29) { 0x01 }, 2_000_000L)
        val minAda = CardanoMinUtxo.calculateMinAda(output, 4310L)
        assertTrue(minAda >= 1_000_000L, "Min ADA should be at least 1 ADA, got $minAda")
    }

    @Test
    fun minAdaIncreasesWithMoreTokens() {
        val addr = ByteArray(29) { 0x01 }
        val policyId = ByteArray(28) { 0xaa.toByte() }

        // ADA-only
        val adaOnly = CardanoTransactionOutput(addr, 2_000_000L)
        val minAdaOnly = CardanoMinUtxo.calculateMinAda(adaOnly, 4310L)

        // 1 token
        val oneToken = CardanoTransactionOutput(
            addr, 2_000_000L,
            mapOf(policyId to mapOf(ByteArray(4) { 0x01 } to 1000L))
        )
        val minAda1 = CardanoMinUtxo.calculateMinAda(oneToken, 4310L)

        // 3 tokens
        val threeTokens = CardanoTransactionOutput(
            addr, 2_000_000L,
            mapOf(
                policyId to mapOf(
                    ByteArray(4) { 0x01 } to 1000L,
                    ByteArray(4) { 0x02 } to 2000L,
                    ByteArray(4) { 0x03 } to 3000L
                )
            )
        )
        val minAda3 = CardanoMinUtxo.calculateMinAda(threeTokens, 4310L)

        assertTrue(minAda1 >= minAdaOnly, "1 token min ADA ($minAda1) >= ADA-only ($minAdaOnly)")
        assertTrue(minAda3 >= minAda1, "3 tokens min ADA ($minAda3) >= 1 token ($minAda1)")
    }

    // ---- UTXO selection ----

    @Test
    fun selectUtxosPicksMinimalSet() {
        val policyId = "a".repeat(56)
        val assetName = "bb"
        val utxos = listOf(
            CardanoUtxo("c".repeat(64), 0, 5_000_000L,
                listOf(CardanoNativeToken(policyId, assetName, 500L))),
            CardanoUtxo("d".repeat(64), 0, 3_000_000L,
                listOf(CardanoNativeToken(policyId, assetName, 300L))),
            CardanoUtxo("e".repeat(64), 0, 2_000_000L,
                listOf(CardanoNativeToken(policyId, assetName, 200L)))
        )

        // Need 400 tokens — the first UTXO (500) should suffice
        val selected = CardanoUtxoSelector.selectUtxos(utxos, policyId, assetName, 400L, 1_000_000L)
        assertTrue(selected.isNotEmpty())
        val totalTokens = selected.sumOf { it.tokenAmount(policyId, assetName) }
        assertTrue(totalTokens >= 400L)
    }

    @Test
    fun selectUtxosAddsMoreForAdaCoverage() {
        val policyId = "a".repeat(56)
        val assetName = "bb"
        val utxos = listOf(
            CardanoUtxo("c".repeat(64), 0, 1_000_000L,
                listOf(CardanoNativeToken(policyId, assetName, 1000L))),
            CardanoUtxo("d".repeat(64), 0, 10_000_000L,
                listOf(CardanoNativeToken(policyId, assetName, 10L)))
        )

        // Need 500 tokens (first UTXO covers) but 5_000_000 ADA (need second UTXO too)
        val selected = CardanoUtxoSelector.selectUtxos(utxos, policyId, assetName, 500L, 5_000_000L)
        assertEquals(2, selected.size, "Should select both UTXOs to cover ADA requirement")
    }

    @Test
    fun multiAssetTokenCount() {
        val multiAsset = CardanoMultiAsset(
            mapOf(
                "a".repeat(56) to mapOf("00" to 100L, "01" to 200L),
                "b".repeat(56) to mapOf("02" to 300L)
            )
        )
        assertEquals(3, multiAsset.tokenCount())
    }
}
