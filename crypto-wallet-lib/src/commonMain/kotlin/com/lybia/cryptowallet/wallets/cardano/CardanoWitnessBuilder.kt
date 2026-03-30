package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborValue

/**
 * Represents a VKey (verification key) witness for Shelley transactions.
 */
data class VKeyWitness(
    val publicKey: ByteArray,   // 32-byte Ed25519 public key
    val signature: ByteArray    // 64-byte Ed25519 signature
) {
    init {
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        require(signature.size == 64) { "Signature must be 64 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VKeyWitness) return false
        return publicKey.contentEquals(other.publicKey) && signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + signature.contentHashCode()
}

/**
 * Represents a Bootstrap witness for Byron addresses in Shelley transactions.
 */
data class BootstrapWitness(
    val publicKey: ByteArray,   // 32-byte Ed25519 public key
    val signature: ByteArray,   // 64-byte Ed25519 signature
    val chainCode: ByteArray,   // 32-byte chain code
    val attributes: ByteArray   // CBOR-encoded attributes (usually empty map = 0xa0)
) {
    init {
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        require(signature.size == 64) { "Signature must be 64 bytes" }
        require(chainCode.size == 32) { "Chain code must be 32 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BootstrapWitness) return false
        return publicKey.contentEquals(other.publicKey) &&
            signature.contentEquals(other.signature) &&
            chainCode.contentEquals(other.chainCode) &&
            attributes.contentEquals(other.attributes)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + attributes.contentHashCode()
        return result
    }
}


/**
 * Cardano witness set containing VKey and Bootstrap witnesses.
 */
class CardanoWitnessSet(
    val vkeyWitnesses: List<VKeyWitness>,
    val bootstrapWitnesses: List<BootstrapWitness>
) {
    private val encoder = CborEncoder()

    /**
     * Serialize the witness set to CBOR bytes.
     * CBOR map with key 0 = VKey witnesses array, key 2 = Bootstrap witnesses array.
     */
    fun serialize(): ByteArray {
        val entries = mutableListOf<Pair<CborValue, CborValue>>()

        // Key 0: VKey witnesses
        if (vkeyWitnesses.isNotEmpty()) {
            val vkeysCbor = vkeyWitnesses.map { w ->
                CborValue.CborArray(listOf(
                    CborValue.CborByteString(w.publicKey),
                    CborValue.CborByteString(w.signature)
                ))
            }
            entries.add(CborValue.CborUInt(0u) to CborValue.CborArray(vkeysCbor))
        }

        // Key 2: Bootstrap witnesses
        if (bootstrapWitnesses.isNotEmpty()) {
            val bootstrapCbor = bootstrapWitnesses.map { w ->
                CborValue.CborArray(listOf(
                    CborValue.CborByteString(w.publicKey),
                    CborValue.CborByteString(w.signature),
                    CborValue.CborByteString(w.chainCode),
                    CborValue.CborByteString(w.attributes)
                ))
            }
            entries.add(CborValue.CborUInt(2u) to CborValue.CborArray(bootstrapCbor))
        }

        return encoder.encode(CborValue.CborMap(entries))
    }

    companion object {
        /**
         * Deserialize a CBOR-encoded witness set.
         */
        fun deserialize(bytes: ByteArray): CardanoWitnessSet {
            val decoder = CborDecoder()
            val cbor = decoder.decode(bytes)
            require(cbor is CborValue.CborMap) { "Witness set must be a CBOR map" }

            var vkeyWitnesses = emptyList<VKeyWitness>()
            var bootstrapWitnesses = emptyList<BootstrapWitness>()

            for ((key, value) in cbor.entries) {
                val keyInt = (key as CborValue.CborUInt).value.toInt()
                when (keyInt) {
                    0 -> {
                        val arr = (value as CborValue.CborArray).items
                        vkeyWitnesses = arr.map { item ->
                            val wArr = (item as CborValue.CborArray).items
                            VKeyWitness(
                                publicKey = (wArr[0] as CborValue.CborByteString).bytes,
                                signature = (wArr[1] as CborValue.CborByteString).bytes
                            )
                        }
                    }
                    2 -> {
                        val arr = (value as CborValue.CborArray).items
                        bootstrapWitnesses = arr.map { item ->
                            val wArr = (item as CborValue.CborArray).items
                            BootstrapWitness(
                                publicKey = (wArr[0] as CborValue.CborByteString).bytes,
                                signature = (wArr[1] as CborValue.CborByteString).bytes,
                                chainCode = (wArr[2] as CborValue.CborByteString).bytes,
                                attributes = (wArr[3] as CborValue.CborByteString).bytes
                            )
                        }
                    }
                }
            }

            return CardanoWitnessSet(vkeyWitnesses, bootstrapWitnesses)
        }
    }
}

/**
 * Builder for constructing Cardano witness sets.
 */
class CardanoWitnessBuilder {
    private val vkeyWitnesses = mutableListOf<VKeyWitness>()
    private val bootstrapWitnesses = mutableListOf<BootstrapWitness>()

    fun addVKeyWitness(publicKey: ByteArray, signature: ByteArray): CardanoWitnessBuilder {
        vkeyWitnesses.add(VKeyWitness(publicKey, signature))
        return this
    }

    fun addBootstrapWitness(
        publicKey: ByteArray,
        signature: ByteArray,
        chainCode: ByteArray,
        attributes: ByteArray
    ): CardanoWitnessBuilder {
        bootstrapWitnesses.add(BootstrapWitness(publicKey, signature, chainCode, attributes))
        return this
    }

    fun build(): CardanoWitnessSet {
        return CardanoWitnessSet(
            vkeyWitnesses = vkeyWitnesses.toList(),
            bootstrapWitnesses = bootstrapWitnesses.toList()
        )
    }
}

/**
 * A fully signed Cardano transaction ready for submission.
 */
class CardanoSignedTransaction(
    val body: CardanoTransactionBody,
    val witnessSet: CardanoWitnessSet,
    val metadata: ByteArray? = null
) {
    private val encoder = CborEncoder()

    /**
     * Serialize the full signed transaction to CBOR bytes.
     * CBOR array: [body_map, witness_set_map, metadata_or_null]
     */
    fun serialize(): ByteArray {
        val decoder = CborDecoder()

        // Decode body and witness set back to CborValue for embedding
        val bodyCbor = decoder.decode(body.serialize())
        val witnessCbor = decoder.decode(witnessSet.serialize())
        val metadataCbor: CborValue = if (metadata != null) {
            decoder.decode(metadata)
        } else {
            CborValue.CborSimple.NULL
        }

        return encoder.encode(CborValue.CborArray(listOf(bodyCbor, witnessCbor, metadataCbor)))
    }

    /**
     * Get the transaction ID (hex-encoded Blake2b-256 hash of the body).
     */
    fun getTransactionId(): String {
        return body.getHash().toHex()
    }

    /**
     * Encode the serialized transaction as Base64.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun toBase64(): String {
        return kotlin.io.encoding.Base64.encode(serialize())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
