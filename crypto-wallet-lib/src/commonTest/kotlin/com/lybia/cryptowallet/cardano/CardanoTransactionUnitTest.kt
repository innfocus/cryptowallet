package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import com.lybia.cryptowallet.wallets.cardano.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Cardano Shelley transaction building with known test vectors.
 */
class CardanoTransactionUnitTest {

    private val encoder = CborEncoder()
    private val decoder = CborDecoder()

    // ---- Helpers ----

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    // ---- Transaction Builder Tests ----

    @Test
    fun buildSimpleTransaction() {
        val txHash = ByteArray(32) { it.toByte() }
        val addressBytes = ByteArray(29) { (it + 0x60).toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addressBytes, 2_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        assertEquals(1, body.inputs.size)
        assertEquals(1, body.outputs.size)
        assertEquals(170_000L, body.fee)
        assertEquals(50_000_000L, body.ttl)
        assertNull(body.metadataHash)
    }

    @Test
    fun buildTransactionWithMultipleInputsOutputs() {
        val txHash1 = ByteArray(32) { 0x01.toByte() }
        val txHash2 = ByteArray(32) { 0x02.toByte() }
        val addr1 = ByteArray(29) { 0x61.toByte() }
        val addr2 = ByteArray(57) { 0x00.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash1, 0)
            .addInput(txHash2, 1)
            .addOutput(addr1, 1_500_000L)
            .addOutput(addr2, 3_000_000L)
            .setFee(200_000L)
            .setTtl(60_000_000L)
            .build()

        assertEquals(2, body.inputs.size)
        assertEquals(2, body.outputs.size)
    }

    @Test
    fun buildTransactionWithMetadataHash() {
        val txHash = ByteArray(32) { 0xAA.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }
        val metaHash = ByteArray(32) { 0xBB.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 5_000_000L)
            .setFee(180_000L)
            .setTtl(70_000_000L)
            .setMetadataHash(metaHash)
            .build()

        assertNotNull(body.metadataHash)
        assertTrue(metaHash.contentEquals(body.metadataHash!!))
    }

    @Test
    fun buildTransactionWithHexInput() {
        val txHashHex = "0000000000000000000000000000000000000000000000000000000000000001"
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHashHex, 0)
            .addOutput(addr, 1_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        assertEquals(1, body.inputs.size)
        assertEquals(1.toByte(), body.inputs[0].txHash[31])
    }

    // ---- Validation Tests ----

    @Test
    fun buildFailsWithNoInputs() {
        val addr = ByteArray(29) { 0x61.toByte() }
        assertFailsWith<IllegalArgumentException> {
            CardanoTransactionBuilder()
                .addOutput(addr, 1_000_000L)
                .setFee(170_000L)
                .setTtl(50_000_000L)
                .build()
        }
    }

    @Test
    fun buildFailsWithNoOutputs() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        assertFailsWith<IllegalArgumentException> {
            CardanoTransactionBuilder()
                .addInput(txHash, 0)
                .setFee(170_000L)
                .setTtl(50_000_000L)
                .build()
        }
    }

    @Test
    fun buildFailsWithZeroTtl() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }
        assertFailsWith<IllegalArgumentException> {
            CardanoTransactionBuilder()
                .addInput(txHash, 0)
                .addOutput(addr, 1_000_000L)
                .setFee(170_000L)
                .setTtl(0L)
                .build()
        }
    }

    @Test
    fun inputRejectsInvalidTxHash() {
        assertFailsWith<IllegalArgumentException> {
            CardanoTransactionInput(ByteArray(16), 0)  // too short
        }
    }

    @Test
    fun inputRejectsNegativeIndex() {
        assertFailsWith<IllegalArgumentException> {
            CardanoTransactionInput(ByteArray(32), -1)
        }
    }


    // ---- Serialization Tests ----

    @Test
    fun serializeSimpleTransactionBody() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 2_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        val serialized = body.serialize()
        assertTrue(serialized.isNotEmpty())

        // Decode and verify CBOR structure
        val cbor = decoder.decode(serialized)
        assertTrue(cbor is CborValue.CborMap, "Transaction body should be a CBOR map")
        val map = cbor as CborValue.CborMap

        // Should have keys 0, 1, 2, 3
        val keys = map.entries.map { (it.first as CborValue.CborUInt).value.toInt() }
        assertTrue(0 in keys, "Should have key 0 (inputs)")
        assertTrue(1 in keys, "Should have key 1 (outputs)")
        assertTrue(2 in keys, "Should have key 2 (fee)")
        assertTrue(3 in keys, "Should have key 3 (ttl)")

        // Verify inputs structure
        val inputsEntry = map.entries.first { (it.first as CborValue.CborUInt).value.toInt() == 0 }
        val inputsArr = inputsEntry.second as CborValue.CborArray
        assertEquals(1, inputsArr.items.size)
        val input0 = inputsArr.items[0] as CborValue.CborArray
        assertEquals(2, input0.items.size)
        assertTrue(input0.items[0] is CborValue.CborByteString)
        assertTrue(input0.items[1] is CborValue.CborUInt)

        // Verify fee
        val feeEntry = map.entries.first { (it.first as CborValue.CborUInt).value.toInt() == 2 }
        assertEquals(170_000uL, (feeEntry.second as CborValue.CborUInt).value)

        // Verify ttl
        val ttlEntry = map.entries.first { (it.first as CborValue.CborUInt).value.toInt() == 3 }
        assertEquals(50_000_000uL, (ttlEntry.second as CborValue.CborUInt).value)
    }

    @Test
    fun transactionBodyRoundTrip() {
        val txHash = ByteArray(32) { (it * 3).toByte() }
        val addr = ByteArray(57) { (it + 10).toByte() }

        val original = CardanoTransactionBuilder()
            .addInput(txHash, 5)
            .addOutput(addr, 10_000_000L)
            .setFee(200_000L)
            .setTtl(99_000_000L)
            .build()

        val serialized = original.serialize()
        val deserialized = CardanoTransactionBody.deserialize(serialized)

        assertEquals(original.inputs.size, deserialized.inputs.size)
        assertTrue(original.inputs[0].txHash.contentEquals(deserialized.inputs[0].txHash))
        assertEquals(original.inputs[0].index, deserialized.inputs[0].index)
        assertEquals(original.outputs.size, deserialized.outputs.size)
        assertTrue(original.outputs[0].addressBytes.contentEquals(deserialized.outputs[0].addressBytes))
        assertEquals(original.outputs[0].lovelace, deserialized.outputs[0].lovelace)
        assertEquals(original.fee, deserialized.fee)
        assertEquals(original.ttl, deserialized.ttl)
    }

    @Test
    fun transactionHashIsDeterministic() {
        val txHash = ByteArray(32) { 0xAB.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 5_000_000L)
            .setFee(180_000L)
            .setTtl(40_000_000L)
            .build()

        val hash1 = body.getHash()
        val hash2 = body.getHash()

        assertEquals(32, hash1.size, "Hash should be 32 bytes")
        assertTrue(hash1.contentEquals(hash2), "Hash should be deterministic")
    }

    @Test
    fun transactionHashMatchesBlake2b256() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 2_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        val serialized = body.serialize()
        val expectedHash = Blake2b.hash(serialized, 32)
        val actualHash = body.getHash()

        assertTrue(expectedHash.contentEquals(actualHash),
            "getHash() should return Blake2b-256 of serialized body")
    }

    // ---- Witness Tests ----

    @Test
    fun vkeyWitnessSerializationRoundTrip() {
        val pk = ByteArray(32) { 0x11.toByte() }
        val sig = ByteArray(64) { 0x22.toByte() }

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(pk, sig)
            .build()

        val serialized = witnessSet.serialize()
        val deserialized = CardanoWitnessSet.deserialize(serialized)

        assertEquals(1, deserialized.vkeyWitnesses.size)
        assertEquals(0, deserialized.bootstrapWitnesses.size)
        assertTrue(pk.contentEquals(deserialized.vkeyWitnesses[0].publicKey))
        assertTrue(sig.contentEquals(deserialized.vkeyWitnesses[0].signature))
    }

    @Test
    fun bootstrapWitnessSerializationRoundTrip() {
        val pk = ByteArray(32) { 0x33.toByte() }
        val sig = ByteArray(64) { 0x44.toByte() }
        val cc = ByteArray(32) { 0x55.toByte() }
        val attr = byteArrayOf(0xa0.toByte())

        val witnessSet = CardanoWitnessBuilder()
            .addBootstrapWitness(pk, sig, cc, attr)
            .build()

        val serialized = witnessSet.serialize()
        val deserialized = CardanoWitnessSet.deserialize(serialized)

        assertEquals(0, deserialized.vkeyWitnesses.size)
        assertEquals(1, deserialized.bootstrapWitnesses.size)
        val w = deserialized.bootstrapWitnesses[0]
        assertTrue(pk.contentEquals(w.publicKey))
        assertTrue(sig.contentEquals(w.signature))
        assertTrue(cc.contentEquals(w.chainCode))
        assertTrue(attr.contentEquals(w.attributes))
    }

    @Test
    fun mixedWitnessSetSerialization() {
        val vkPk = ByteArray(32) { 0xAA.toByte() }
        val vkSig = ByteArray(64) { 0xBB.toByte() }
        val bsPk = ByteArray(32) { 0xCC.toByte() }
        val bsSig = ByteArray(64) { 0xDD.toByte() }
        val bsCc = ByteArray(32) { 0xEE.toByte() }
        val bsAttr = byteArrayOf(0xa0.toByte())

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(vkPk, vkSig)
            .addBootstrapWitness(bsPk, bsSig, bsCc, bsAttr)
            .build()

        val serialized = witnessSet.serialize()
        val cbor = decoder.decode(serialized)
        assertTrue(cbor is CborValue.CborMap)
        val map = cbor as CborValue.CborMap
        val keys = map.entries.map { (it.first as CborValue.CborUInt).value.toInt() }
        assertTrue(0 in keys, "Should have VKey witnesses (key 0)")
        assertTrue(2 in keys, "Should have Bootstrap witnesses (key 2)")

        val deserialized = CardanoWitnessSet.deserialize(serialized)
        assertEquals(1, deserialized.vkeyWitnesses.size)
        assertEquals(1, deserialized.bootstrapWitnesses.size)
    }

    @Test
    fun emptyWitnessSet() {
        val witnessSet = CardanoWitnessBuilder().build()
        val serialized = witnessSet.serialize()
        val cbor = decoder.decode(serialized)
        assertTrue(cbor is CborValue.CborMap)
        val map = cbor as CborValue.CborMap
        assertEquals(0, map.entries.size, "Empty witness set should have no entries")
    }

    // ---- Signed Transaction Tests ----

    @Test
    fun signedTransactionSerialization() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 2_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        val witnessSet = CardanoWitnessBuilder()
            .addVKeyWitness(ByteArray(32) { 0x11.toByte() }, ByteArray(64) { 0x22.toByte() })
            .build()

        val signedTx = CardanoSignedTransaction(body, witnessSet)
        val serialized = signedTx.serialize()
        assertTrue(serialized.isNotEmpty())

        // Verify it's a CBOR array of 3 elements
        val cbor = decoder.decode(serialized)
        assertTrue(cbor is CborValue.CborArray)
        val arr = cbor as CborValue.CborArray
        assertEquals(3, arr.items.size, "Signed transaction should be array of 3 elements")

        // Element 0: body (map)
        assertTrue(arr.items[0] is CborValue.CborMap, "First element should be body map")
        // Element 1: witness set (map)
        assertTrue(arr.items[1] is CborValue.CborMap, "Second element should be witness set map")
        // Element 2: metadata (null)
        assertTrue(arr.items[2] is CborValue.CborSimple, "Third element should be null (simple)")
    }

    @Test
    fun signedTransactionId() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addOutput(addr, 2_000_000L)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        val witnessSet = CardanoWitnessBuilder().build()
        val signedTx = CardanoSignedTransaction(body, witnessSet)

        val txId = signedTx.getTransactionId()
        assertEquals(64, txId.length, "Transaction ID should be 64 hex chars (32 bytes)")
        assertEquals(body.getHash().toHex(), txId, "Transaction ID should match body hash")
    }

    // ---- Multi-Asset Output Tests ----

    @Test
    fun multiAssetOutputSerialization() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }
        val policyId = ByteArray(28) { 0xAA.toByte() }
        val assetName = "TestToken".encodeToByteArray()

        val assets = mapOf(policyId to mapOf(assetName to 1000L))

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addMultiAssetOutput(addr, 2_000_000L, assets)
            .setFee(170_000L)
            .setTtl(50_000_000L)
            .build()

        val serialized = body.serialize()
        val cbor = decoder.decode(serialized)
        val map = cbor as CborValue.CborMap

        // Get outputs
        val outputsEntry = map.entries.first { (it.first as CborValue.CborUInt).value.toInt() == 1 }
        val outputsArr = outputsEntry.second as CborValue.CborArray
        assertEquals(1, outputsArr.items.size)

        val output0 = outputsArr.items[0] as CborValue.CborArray
        assertEquals(2, output0.items.size)

        // Amount should be [lovelace, multi_asset_map]
        val amountValue = output0.items[1] as CborValue.CborArray
        assertEquals(2, amountValue.items.size)
        assertEquals(2_000_000uL, (amountValue.items[0] as CborValue.CborUInt).value)

        val multiAssetMap = amountValue.items[1] as CborValue.CborMap
        assertEquals(1, multiAssetMap.entries.size)
    }

    @Test
    fun multiAssetOutputRoundTrip() {
        val txHash = ByteArray(32) { 0x01.toByte() }
        val addr = ByteArray(29) { 0x61.toByte() }
        val policyId = ByteArray(28) { 0xBB.toByte() }
        val assetName1 = "Token1".encodeToByteArray()
        val assetName2 = "Token2".encodeToByteArray()

        val assets = mapOf(
            policyId to mapOf(
                assetName1 to 500L,
                assetName2 to 1000L
            )
        )

        val body = CardanoTransactionBuilder()
            .addInput(txHash, 0)
            .addMultiAssetOutput(addr, 3_000_000L, assets)
            .setFee(200_000L)
            .setTtl(60_000_000L)
            .build()

        val serialized = body.serialize()
        val deserialized = CardanoTransactionBody.deserialize(serialized)

        assertEquals(1, deserialized.outputs.size)
        val output = deserialized.outputs[0]
        assertEquals(3_000_000L, output.lovelace)
        assertNotNull(output.multiAssets)
        assertTrue(output.multiAssets!!.isNotEmpty())
    }

    // ---- VKey/Bootstrap Witness Validation Tests ----

    @Test
    fun vkeyWitnessRejectsInvalidKeySize() {
        assertFailsWith<IllegalArgumentException> {
            VKeyWitness(ByteArray(16), ByteArray(64))  // key too short
        }
    }

    @Test
    fun vkeyWitnessRejectsInvalidSignatureSize() {
        assertFailsWith<IllegalArgumentException> {
            VKeyWitness(ByteArray(32), ByteArray(32))  // sig too short
        }
    }

    @Test
    fun bootstrapWitnessRejectsInvalidChainCodeSize() {
        assertFailsWith<IllegalArgumentException> {
            BootstrapWitness(ByteArray(32), ByteArray(64), ByteArray(16), byteArrayOf(0xa0.toByte()))
        }
    }
}
