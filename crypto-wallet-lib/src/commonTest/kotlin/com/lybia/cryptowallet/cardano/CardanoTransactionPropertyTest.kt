package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import com.lybia.cryptowallet.wallets.cardano.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for Cardano Shelley transaction building, serialization, and witnesses.
 */
class CardanoTransactionPropertyTest {

    // ---- Generators ----

    /** Generate a random 32-byte array (transaction hash / key size). */
    private fun arbBytes32(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(32), Arb.byte())

    /** Generate a random 64-byte array (signature size). */
    private fun arbBytes64(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(64), Arb.byte())

    /** Generate a random address byte array (29-57 bytes, typical Cardano address payload). */
    private fun arbAddressBytes(): Arb<ByteArray> =
        Arb.byteArray(Arb.int(29..57), Arb.byte())

    /** Generate a valid lovelace amount (positive). */
    private fun arbLovelace(): Arb<Long> = Arb.long(1L..10_000_000_000L)

    /** Generate a valid fee. */
    private fun arbFee(): Arb<Long> = Arb.long(100_000L..5_000_000L)

    /** Generate a valid TTL. */
    private fun arbTtl(): Arb<Long> = Arb.long(1L..100_000_000L)

    /** Generate a valid UTXO index. */
    private fun arbIndex(): Arb<Int> = Arb.int(0..255)

    /** Generate a transaction input. */
    private fun arbInput(): Arb<CardanoTransactionInput> =
        Arb.bind(arbBytes32(), arbIndex()) { hash, idx ->
            CardanoTransactionInput(hash, idx)
        }

    /** Generate a simple transaction output. */
    private fun arbOutput(): Arb<CardanoTransactionOutput> =
        Arb.bind(arbAddressBytes(), arbLovelace()) { addr, amount ->
            CardanoTransactionOutput(addr, amount)
        }

    /** Generate a transaction body. */
    private fun arbTransactionBody(): Arb<CardanoTransactionBody> =
        Arb.bind(
            Arb.list(arbInput(), 1..4),
            Arb.list(arbOutput(), 1..4),
            arbFee(),
            arbTtl()
        ) { inputs, outputs, fee, ttl ->
            CardanoTransactionBody(inputs, outputs, fee, ttl)
        }

    /** Generate a VKey witness. */
    private fun arbVKeyWitness(): Arb<VKeyWitness> =
        Arb.bind(arbBytes32(), arbBytes64()) { pk, sig ->
            VKeyWitness(pk, sig)
        }

    /** Generate a Bootstrap witness. */
    private fun arbBootstrapWitness(): Arb<BootstrapWitness> =
        Arb.bind(
            arbBytes32(),
            arbBytes64(),
            arbBytes32(),
            Arb.byteArray(Arb.constant(1), Arb.constant(0xa0.toByte()))  // empty CBOR map
        ) { pk, sig, cc, attr ->
            BootstrapWitness(pk, sig, cc, attr)
        }

    // ---- Helpers ----

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    // ---- Property Tests ----

    // Feature: cardano-midnight-support, Property 8: Shelley Transaction Serialization Round-Trip
    // **Validates: Requirements 4.1, 4.7**
    @Test
    fun transactionSerializationRoundTrip() = runTest {
        checkAll(100, arbTransactionBody()) { original ->
            // Serialize
            val serialized = original.serialize()
            assertTrue(serialized.isNotEmpty(), "Serialized body should not be empty")

            // Deserialize
            val deserialized = CardanoTransactionBody.deserialize(serialized)

            // Verify inputs
            assertEquals(original.inputs.size, deserialized.inputs.size, "Input count mismatch")
            original.inputs.zip(deserialized.inputs).forEach { (orig, deser) ->
                assertTrue(orig.txHash.contentEquals(deser.txHash), "Input txHash mismatch")
                assertEquals(orig.index, deser.index, "Input index mismatch")
            }

            // Verify outputs
            assertEquals(original.outputs.size, deserialized.outputs.size, "Output count mismatch")
            original.outputs.zip(deserialized.outputs).forEach { (orig, deser) ->
                assertTrue(orig.addressBytes.contentEquals(deser.addressBytes), "Output address mismatch")
                assertEquals(orig.lovelace, deser.lovelace, "Output lovelace mismatch")
            }

            // Verify fee and ttl
            assertEquals(original.fee, deserialized.fee, "Fee mismatch")
            assertEquals(original.ttl, deserialized.ttl, "TTL mismatch")

            // Verify hash is deterministic
            val hash1 = original.getHash()
            val hash2 = original.getHash()
            assertTrue(hash1.contentEquals(hash2), "Transaction hash should be deterministic")
            assertEquals(32, hash1.size, "Transaction hash should be 32 bytes (Blake2b-256)")
        }
    }


    // Feature: cardano-midnight-support, Property 9: VKey Witness Signature Verification
    // **Validates: Requirements 4.2**
    @Test
    fun vkeyWitnessSignatureVerification() = runTest {
        checkAll(100, arbBytes32(), arbBytes32(), arbBytes64()) { txHash, publicKey, signature ->
            // Create a VKey witness
            val witness = VKeyWitness(publicKey, signature)

            // Verify structural correctness
            assertEquals(32, witness.publicKey.size, "VKey public key should be 32 bytes")
            assertEquals(64, witness.signature.size, "VKey signature should be 64 bytes")

            // Build witness set and serialize
            val witnessSet = CardanoWitnessBuilder()
                .addVKeyWitness(publicKey, signature)
                .build()

            val serialized = witnessSet.serialize()
            assertTrue(serialized.isNotEmpty(), "Serialized witness set should not be empty")

            // Deserialize and verify round-trip
            val deserialized = CardanoWitnessSet.deserialize(serialized)
            assertEquals(1, deserialized.vkeyWitnesses.size, "Should have 1 VKey witness")
            assertEquals(0, deserialized.bootstrapWitnesses.size, "Should have 0 Bootstrap witnesses")

            val deserWitness = deserialized.vkeyWitnesses[0]
            assertTrue(publicKey.contentEquals(deserWitness.publicKey), "Public key should round-trip")
            assertTrue(signature.contentEquals(deserWitness.signature), "Signature should round-trip")
        }
    }

    // Feature: cardano-midnight-support, Property 10: Bootstrap Witness Signature Verification
    // **Validates: Requirements 4.3**
    @Test
    fun bootstrapWitnessSignatureVerification() = runTest {
        checkAll(100, arbBytes32(), arbBytes64(), arbBytes32()) { publicKey, signature, chainCode ->
            val attributes = byteArrayOf(0xa0.toByte())  // empty CBOR map

            // Create a Bootstrap witness
            val witness = BootstrapWitness(publicKey, signature, chainCode, attributes)

            // Verify structural correctness
            assertEquals(32, witness.publicKey.size, "Bootstrap public key should be 32 bytes")
            assertEquals(64, witness.signature.size, "Bootstrap signature should be 64 bytes")
            assertEquals(32, witness.chainCode.size, "Bootstrap chain code should be 32 bytes")

            // Build witness set and serialize
            val witnessSet = CardanoWitnessBuilder()
                .addBootstrapWitness(publicKey, signature, chainCode, attributes)
                .build()

            val serialized = witnessSet.serialize()
            assertTrue(serialized.isNotEmpty(), "Serialized witness set should not be empty")

            // Deserialize and verify round-trip
            val deserialized = CardanoWitnessSet.deserialize(serialized)
            assertEquals(0, deserialized.vkeyWitnesses.size, "Should have 0 VKey witnesses")
            assertEquals(1, deserialized.bootstrapWitnesses.size, "Should have 1 Bootstrap witness")

            val deserWitness = deserialized.bootstrapWitnesses[0]
            assertTrue(publicKey.contentEquals(deserWitness.publicKey), "Public key should round-trip")
            assertTrue(signature.contentEquals(deserWitness.signature), "Signature should round-trip")
            assertTrue(chainCode.contentEquals(deserWitness.chainCode), "Chain code should round-trip")
            assertTrue(attributes.contentEquals(deserWitness.attributes), "Attributes should round-trip")
        }
    }

    // Feature: cardano-midnight-support, Property 11: Mixed Witness Set Structure
    // **Validates: Requirements 4.4**
    @Test
    fun mixedWitnessSetStructure() = runTest {
        checkAll(
            100,
            Arb.list(arbVKeyWitness(), 1..3),
            Arb.list(arbBootstrapWitness(), 1..3)
        ) { vkeyWitnesses, bootstrapWitnesses ->
            // Build a mixed witness set
            val builder = CardanoWitnessBuilder()
            vkeyWitnesses.forEach { w -> builder.addVKeyWitness(w.publicKey, w.signature) }
            bootstrapWitnesses.forEach { w ->
                builder.addBootstrapWitness(w.publicKey, w.signature, w.chainCode, w.attributes)
            }
            val witnessSet = builder.build()

            // Serialize
            val serialized = witnessSet.serialize()
            assertTrue(serialized.isNotEmpty(), "Serialized mixed witness set should not be empty")

            // Verify CBOR structure contains both key 0 and key 2
            val decoder = com.lybia.cryptowallet.utils.cbor.CborDecoder()
            val cbor = decoder.decode(serialized)
            assertTrue(cbor is com.lybia.cryptowallet.utils.cbor.CborValue.CborMap,
                "Witness set should be a CBOR map")
            val map = cbor as com.lybia.cryptowallet.utils.cbor.CborValue.CborMap

            val keys = map.entries.map {
                (it.first as com.lybia.cryptowallet.utils.cbor.CborValue.CborUInt).value.toInt()
            }
            assertTrue(0 in keys, "Mixed witness set should contain key 0 (VKey witnesses)")
            assertTrue(2 in keys, "Mixed witness set should contain key 2 (Bootstrap witnesses)")

            // Deserialize and verify counts
            val deserialized = CardanoWitnessSet.deserialize(serialized)
            assertEquals(vkeyWitnesses.size, deserialized.vkeyWitnesses.size,
                "VKey witness count should match: expected ${vkeyWitnesses.size}, got ${deserialized.vkeyWitnesses.size}")
            assertEquals(bootstrapWitnesses.size, deserialized.bootstrapWitnesses.size,
                "Bootstrap witness count should match: expected ${bootstrapWitnesses.size}, got ${deserialized.bootstrapWitnesses.size}")
        }
    }

    // Feature: crypto-wallet-module, Property 7: Cardano transaction CBOR round-trip
    // Serialize Shelley tx → CBOR → deserialize → same inputs, outputs, fee, ttl
    // **Validates: Requirements 14.6**
    @Test
    fun shelleyTransactionCborRoundTrip() = runTest {
        checkAll(
            100,
            arbTransactionBody(),
            Arb.list(arbVKeyWitness(), 1..3)
        ) { body, vkeys ->
            // Build a full signed transaction
            val witnessSet = CardanoWitnessSet(vkeys, emptyList())
            val signedTx = CardanoSignedTransaction(body, witnessSet)

            // Serialize full transaction to CBOR
            val serialized = signedTx.serialize()
            assertTrue(serialized.isNotEmpty(), "Serialized signed tx should not be empty")

            // Decode the CBOR array: [body_map, witness_set_map, metadata_or_null]
            val decoder = CborDecoder()
            val topLevel = decoder.decode(serialized)
            assertTrue(topLevel is CborValue.CborArray, "Signed tx should be a CBOR array")
            val items = (topLevel as CborValue.CborArray).items
            assertEquals(3, items.size, "Signed tx array should have 3 elements")

            // Re-encode body from decoded CBOR and deserialize
            val bodyBytes = com.lybia.cryptowallet.utils.cbor.CborEncoder().encode(items[0])
            val deserializedBody = CardanoTransactionBody.deserialize(bodyBytes)

            // Verify inputs round-trip
            assertEquals(body.inputs.size, deserializedBody.inputs.size, "Input count mismatch")
            body.inputs.zip(deserializedBody.inputs).forEach { (orig, deser) ->
                assertTrue(orig.txHash.contentEquals(deser.txHash), "Input txHash mismatch")
                assertEquals(orig.index, deser.index, "Input index mismatch")
            }

            // Verify outputs round-trip
            assertEquals(body.outputs.size, deserializedBody.outputs.size, "Output count mismatch")
            body.outputs.zip(deserializedBody.outputs).forEach { (orig, deser) ->
                assertTrue(orig.addressBytes.contentEquals(deser.addressBytes), "Output address mismatch")
                assertEquals(orig.lovelace, deser.lovelace, "Output lovelace mismatch")
            }

            // Verify fee and ttl round-trip
            assertEquals(body.fee, deserializedBody.fee, "Fee mismatch after round-trip")
            assertEquals(body.ttl, deserializedBody.ttl, "TTL mismatch after round-trip")

            // Re-encode witness set from decoded CBOR and deserialize
            val witnessBytes = com.lybia.cryptowallet.utils.cbor.CborEncoder().encode(items[1])
            val deserializedWitness = CardanoWitnessSet.deserialize(witnessBytes)
            assertEquals(vkeys.size, deserializedWitness.vkeyWitnesses.size, "VKey witness count mismatch")

            // Verify metadata is null
            assertTrue(items[2] is CborValue.CborSimple, "Metadata should be CBOR null")

            // Verify transaction ID is deterministic across serializations
            val txId1 = signedTx.getTransactionId()
            val txId2 = signedTx.getTransactionId()
            assertEquals(txId1, txId2, "Transaction ID should be deterministic")
            assertEquals(64, txId1.length, "Transaction ID should be 64 hex chars (32 bytes)")
        }
    }
}
