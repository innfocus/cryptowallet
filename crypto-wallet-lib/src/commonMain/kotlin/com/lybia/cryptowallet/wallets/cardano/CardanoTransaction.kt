package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborValue

/**
 * Represents a single transaction input (reference to a UTXO).
 */
data class CardanoTransactionInput(
    val txHash: ByteArray,  // 32-byte transaction hash
    val index: Int
) {
    init {
        require(txHash.size == 32) { "Transaction hash must be 32 bytes" }
        require(index >= 0) { "Index must be non-negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardanoTransactionInput) return false
        return txHash.contentEquals(other.txHash) && index == other.index
    }

    override fun hashCode(): Int = txHash.contentHashCode() * 31 + index
}

/**
 * Represents a simple transaction output (address + lovelace amount).
 */
data class CardanoTransactionOutput(
    val addressBytes: ByteArray,
    val lovelace: Long,
    val multiAssets: Map<ByteArray, Map<ByteArray, Long>>? = null  // policyId -> (assetName -> amount)
) {
    init {
        require(lovelace >= 0) { "Lovelace amount must be non-negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardanoTransactionOutput) return false
        return addressBytes.contentEquals(other.addressBytes) && lovelace == other.lovelace
    }

    override fun hashCode(): Int = addressBytes.contentHashCode() * 31 + lovelace.hashCode()
}


/**
 * Cardano Shelley-era transaction body.
 * Contains inputs, outputs, fee, ttl, and optional metadata hash.
 */
class CardanoTransactionBody(
    val inputs: List<CardanoTransactionInput>,
    val outputs: List<CardanoTransactionOutput>,
    val fee: Long,
    val ttl: Long,
    val metadataHash: ByteArray? = null,
    val certificates: List<CardanoCertificate> = emptyList()
) {
    private val encoder = CborEncoder()

    /**
     * Serialize the transaction body to CBOR bytes.
     * CBOR map with keys: 0=inputs, 1=outputs, 2=fee, 3=ttl, 7=metadata_hash (optional)
     */
    fun serialize(): ByteArray {
        val entries = mutableListOf<Pair<CborValue, CborValue>>()

        // Key 0: inputs — array of [txHash_bytes, index_uint]
        val inputsCbor = inputs.map { input ->
            CborValue.CborArray(listOf(
                CborValue.CborByteString(input.txHash),
                CborValue.CborUInt(input.index.toULong())
            ))
        }
        entries.add(CborValue.CborUInt(0u) to CborValue.CborArray(inputsCbor))

        // Key 1: outputs — array of [address_bytes, amount] or [address_bytes, [amount, multi_asset_map]]
        val outputsCbor = outputs.map { output ->
            val amountCbor = if (output.multiAssets != null && output.multiAssets.isNotEmpty()) {
                // Multi-asset output: [lovelace, {policyId -> {assetName -> amount}}]
                val multiAssetEntries = output.multiAssets.map { (policyId, assets) ->
                    val assetEntries = assets.map { (assetName, amount) ->
                        CborValue.CborByteString(assetName) to CborValue.CborUInt(amount.toULong())
                    }
                    CborValue.CborByteString(policyId) to CborValue.CborMap(assetEntries)
                }
                CborValue.CborArray(listOf(
                    CborValue.CborUInt(output.lovelace.toULong()),
                    CborValue.CborMap(multiAssetEntries)
                ))
            } else {
                CborValue.CborUInt(output.lovelace.toULong())
            }
            CborValue.CborArray(listOf(
                CborValue.CborByteString(output.addressBytes),
                amountCbor
            ))
        }
        entries.add(CborValue.CborUInt(1u) to CborValue.CborArray(outputsCbor))

        // Key 2: fee
        entries.add(CborValue.CborUInt(2u) to CborValue.CborUInt(fee.toULong()))

        // Key 3: ttl
        entries.add(CborValue.CborUInt(3u) to CborValue.CborUInt(ttl.toULong()))

        // Key 4: certificates (optional)
        if (certificates.isNotEmpty()) {
            val certsCbor = certificates.map { it.toCbor() }
            entries.add(CborValue.CborUInt(4u) to CborValue.CborArray(certsCbor))
        }

        // Key 7: metadata hash (optional)
        if (metadataHash != null) {
            entries.add(CborValue.CborUInt(7u) to CborValue.CborByteString(metadataHash))
        }

        return encoder.encode(CborValue.CborMap(entries))
    }

    /**
     * Get the transaction hash (Blake2b-256 of serialized body).
     */
    fun getHash(): ByteArray {
        return Blake2b.hash(serialize(), 32)
    }

    companion object {
        /**
         * Deserialize a CBOR-encoded transaction body.
         */
        fun deserialize(bytes: ByteArray): CardanoTransactionBody {
            val decoder = CborDecoder()
            val cbor = decoder.decode(bytes)
            require(cbor is CborValue.CborMap) { "Transaction body must be a CBOR map" }

            var inputs = emptyList<CardanoTransactionInput>()
            var outputs = emptyList<CardanoTransactionOutput>()
            var fee = 0L
            var ttl = 0L
            var metadataHash: ByteArray? = null
            var certificates = emptyList<CardanoCertificate>()

            for ((key, value) in cbor.entries) {
                val keyInt = (key as CborValue.CborUInt).value.toInt()
                when (keyInt) {
                    0 -> {
                        val arr = (value as CborValue.CborArray).items
                        inputs = arr.map { item ->
                            val inputArr = (item as CborValue.CborArray).items
                            CardanoTransactionInput(
                                txHash = (inputArr[0] as CborValue.CborByteString).bytes,
                                index = (inputArr[1] as CborValue.CborUInt).value.toInt()
                            )
                        }
                    }
                    1 -> {
                        val arr = (value as CborValue.CborArray).items
                        outputs = arr.map { item ->
                            val outputArr = (item as CborValue.CborArray).items
                            val addrBytes = (outputArr[0] as CborValue.CborByteString).bytes
                            val amountValue = outputArr[1]
                            if (amountValue is CborValue.CborUInt) {
                                CardanoTransactionOutput(addrBytes, amountValue.value.toLong())
                            } else {
                                // Multi-asset: [lovelace, {policyId -> {assetName -> amount}}]
                                val amountArr = (amountValue as CborValue.CborArray).items
                                val lovelace = (amountArr[0] as CborValue.CborUInt).value.toLong()
                                val multiAssetMap = (amountArr[1] as CborValue.CborMap).entries
                                val multiAssets = mutableMapOf<ByteArrayWrapper, Map<ByteArrayWrapper, Long>>()
                                val rawMultiAssets = mutableMapOf<ByteArray, Map<ByteArray, Long>>()
                                for ((policyKey, assetsVal) in multiAssetMap) {
                                    val policyId = (policyKey as CborValue.CborByteString).bytes
                                    val assetsMap = (assetsVal as CborValue.CborMap).entries
                                    val assets = mutableMapOf<ByteArray, Long>()
                                    for ((assetKey, amountVal) in assetsMap) {
                                        val assetName = (assetKey as CborValue.CborByteString).bytes
                                        val amount = (amountVal as CborValue.CborUInt).value.toLong()
                                        assets[assetName] = amount
                                    }
                                    rawMultiAssets[policyId] = assets
                                }
                                CardanoTransactionOutput(addrBytes, lovelace, rawMultiAssets)
                            }
                        }
                    }
                    2 -> fee = (value as CborValue.CborUInt).value.toLong()
                    3 -> ttl = (value as CborValue.CborUInt).value.toLong()
                    4 -> {
                        val arr = (value as CborValue.CborArray).items
                        certificates = arr.map { CardanoCertificate.fromCbor(it) }
                    }
                    7 -> metadataHash = (value as CborValue.CborByteString).bytes
                }
            }

            return CardanoTransactionBody(inputs, outputs, fee, ttl, metadataHash, certificates)
        }
    }
}

/** Helper wrapper for ByteArray keys in maps. */
private class ByteArrayWrapper(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayWrapper) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * Builder for constructing Cardano Shelley transactions.
 */
class CardanoTransactionBuilder {
    private val inputs = mutableListOf<CardanoTransactionInput>()
    private val outputs = mutableListOf<CardanoTransactionOutput>()
    private var fee: Long = 0
    private var ttl: Long = 0
    private var metadataHash: ByteArray? = null
    private val certificates = mutableListOf<CardanoCertificate>()

    fun addInput(txHash: ByteArray, index: Int): CardanoTransactionBuilder {
        val input = CardanoTransactionInput(txHash, index)
        if (!inputs.contains(input)) {
            inputs.add(input)
        }
        return this
    }

    fun addInput(txHashHex: String, index: Int): CardanoTransactionBuilder {
        return addInput(hexToBytes(txHashHex), index)
    }

    fun addOutput(addressBytes: ByteArray, lovelace: Long): CardanoTransactionBuilder {
        require(lovelace >= MIN_UTXO_LOVELACE) {
            "Output amount $lovelace lovelace is below minimum UTXO ($MIN_UTXO_LOVELACE lovelace)"
        }
        outputs.add(CardanoTransactionOutput(addressBytes, lovelace))
        return this
    }

    fun addMultiAssetOutput(
        addressBytes: ByteArray,
        lovelace: Long,
        assets: Map<ByteArray, Map<ByteArray, Long>>
    ): CardanoTransactionBuilder {
        require(lovelace >= MIN_UTXO_LOVELACE) {
            "Multi-asset output ADA $lovelace lovelace is below minimum UTXO ($MIN_UTXO_LOVELACE lovelace)"
        }
        outputs.add(CardanoTransactionOutput(addressBytes, lovelace, assets))
        return this
    }

    fun setFee(fee: Long): CardanoTransactionBuilder {
        this.fee = fee
        return this
    }

    fun setTtl(ttl: Long): CardanoTransactionBuilder {
        this.ttl = ttl
        return this
    }

    fun setMetadataHash(hash: ByteArray): CardanoTransactionBuilder {
        this.metadataHash = hash
        return this
    }

    fun addCertificate(certificate: CardanoCertificate): CardanoTransactionBuilder {
        certificates.add(certificate)
        return this
    }

    fun build(): CardanoTransactionBody {
        require(inputs.isNotEmpty()) { "Transaction must have at least one input" }
        require(outputs.isNotEmpty()) { "Transaction must have at least one output" }
        require(fee >= 0) { "Fee must be non-negative" }
        require(ttl > 0) { "TTL must be positive" }
        return CardanoTransactionBody(
            inputs = inputs.toList(),
            outputs = outputs.toList(),
            fee = fee,
            ttl = ttl,
            metadataHash = metadataHash,
            certificates = certificates.toList()
        )
    }

    companion object {
        /** Minimum lovelace per output (IOHK protocol parameter). */
        const val MIN_UTXO_LOVELACE = 1_000_000L

        /** Maximum serialised transaction size in bytes (IOHK protocol parameter). */
        const val MAX_TX_SIZE_BYTES = 16_384

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length / 2
            val result = ByteArray(len)
            for (i in 0 until len) {
                result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }
    }
}
