package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue

/**
 * Represents a Cardano native token identified by policy ID and asset name.
 *
 * @property policyId 28-byte hex-encoded policy ID (56 hex chars)
 * @property assetName Hex-encoded asset name (max 32 bytes = 64 hex chars)
 * @property amount Token amount
 */
data class CardanoNativeToken(
    val policyId: String,
    val assetName: String,
    val amount: Long
) {
    init {
        require(policyId.length == 56) { "Policy ID must be 56 hex characters (28 bytes), got ${policyId.length}" }
        require(policyId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Policy ID must be valid hex"
        }
        require(assetName.length <= 64) { "Asset name must be at most 64 hex characters (32 bytes), got ${assetName.length}" }
        require(assetName.length % 2 == 0) { "Asset name hex must have even length" }
        require(assetName.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Asset name must be valid hex"
        }
    }

    /**
     * Compute CIP-14 asset fingerprint (asset1...).
     * Blake2b-160 hash of (policyId bytes || assetName bytes), then Bech32 with "asset" prefix.
     */
    fun fingerprint(): String {
        val policyBytes = hexToBytes(policyId)
        val assetBytes = hexToBytes(assetName)
        val payload = policyBytes + assetBytes
        val hash = Blake2b.hash(payload, 20) // Blake2b-160
        return bech32Encode("asset", hash)
    }
}


/**
 * Represents a multi-asset bundle: a map of policyId -> (assetName -> amount).
 * Both policyId and assetName are hex-encoded strings.
 */
data class CardanoMultiAsset(
    val assets: Map<String, Map<String, Long>>
) {
    /**
     * Serialize to CBOR nested map: {policyId_bytes -> {assetName_bytes -> amount}}.
     */
    fun toCbor(): CborValue {
        val outerEntries = assets.map { (policyId, assetMap) ->
            val innerEntries = assetMap.map { (assetName, amount) ->
                CborValue.CborByteString(hexToBytes(assetName)) to CborValue.CborUInt(amount.toULong())
            }
            CborValue.CborByteString(hexToBytes(policyId)) to CborValue.CborMap(innerEntries)
        }
        return CborValue.CborMap(outerEntries)
    }

    /**
     * Total number of distinct tokens across all policies.
     */
    fun tokenCount(): Int = assets.values.sumOf { it.size }
}

/**
 * Calculates minimum ADA required for a transaction output.
 */
object CardanoMinUtxo {
    private const val MIN_ADA_LOVELACE = 1_000_000L // 1 ADA

    /**
     * Calculate minimum ADA for a transaction output.
     *
     * Formula: max(minAda, (160 + outputSize) * coinsPerUtxoByte)
     * where outputSize is the CBOR-serialized size of the output.
     *
     * @param output The transaction output
     * @param coinsPerUtxoByte Protocol parameter (typically 4310 on mainnet)
     * @return Minimum lovelace required
     */
    fun calculateMinAda(
        output: CardanoTransactionOutput,
        coinsPerUtxoByte: Long
    ): Long {
        val outputSize = estimateOutputSize(output)
        val calculated = (160L + outputSize) * coinsPerUtxoByte
        return maxOf(MIN_ADA_LOVELACE, calculated)
    }

    /**
     * Estimate the CBOR-serialized size of a transaction output.
     */
    private fun estimateOutputSize(output: CardanoTransactionOutput): Long {
        val encoder = CborEncoder()
        val amountCbor = if (output.multiAssets != null && output.multiAssets.isNotEmpty()) {
            val multiAssetEntries = output.multiAssets.map { (policyId, assets) ->
                val assetEntries = assets.map { (assetName, amount) ->
                    CborValue.CborByteString(assetName) to CborValue.CborUInt(amount.toULong())
                }
                CborValue.CborByteString(policyId) to CborValue.CborMap(assetEntries)
            }
            CborValue.CborArray(
                listOf(
                    CborValue.CborUInt(output.lovelace.toULong()),
                    CborValue.CborMap(multiAssetEntries)
                )
            )
        } else {
            CborValue.CborUInt(output.lovelace.toULong())
        }
        val outputCbor = CborValue.CborArray(
            listOf(
                CborValue.CborByteString(output.addressBytes),
                amountCbor
            )
        )
        return encoder.encodeCanonical(outputCbor).size.toLong()
    }
}

/**
 * Represents a UTXO (Unspent Transaction Output) on Cardano.
 */
data class CardanoUtxo(
    val txHash: String,
    val index: Int,
    val lovelace: Long,
    val nativeTokens: List<CardanoNativeToken> = emptyList()
) {
    init {
        require(txHash.length == 64) { "Transaction hash must be 64 hex characters (32 bytes)" }
        require(index >= 0) { "Index must be non-negative" }
        require(lovelace >= 0) { "Lovelace must be non-negative" }
    }

    /**
     * Get the amount of a specific token in this UTXO.
     */
    fun tokenAmount(policyId: String, assetName: String): Long =
        nativeTokens
            .filter { it.policyId == policyId && it.assetName == assetName }
            .sumOf { it.amount }
}

/**
 * UTXO selection algorithm for native token transactions.
 *
 * Uses a greedy approach: sort UTXOs by target token amount descending,
 * then select until both token and ADA requirements are met.
 */
object CardanoUtxoSelector {

    /**
     * Select UTXOs that cover the required token amount and ADA for fees.
     *
     * @param utxos Available UTXOs
     * @param policyId Target token policy ID
     * @param assetName Target token asset name
     * @param requiredTokenAmount Required token amount
     * @param requiredAda Required ADA (lovelace) for fee + min UTXO
     * @return List of selected UTXOs
     * @throws CardanoError.InsufficientTokens if not enough tokens available
     */
    fun selectUtxos(
        utxos: List<CardanoUtxo>,
        policyId: String,
        assetName: String,
        requiredTokenAmount: Long,
        requiredAda: Long
    ): List<CardanoUtxo> {
        // Sort by token amount descending (greedy)
        val sorted = utxos.sortedByDescending { it.tokenAmount(policyId, assetName) }

        val selected = mutableListOf<CardanoUtxo>()
        var collectedTokens = 0L
        var collectedAda = 0L

        for (utxo in sorted) {
            if (collectedTokens >= requiredTokenAmount && collectedAda >= requiredAda) break
            selected.add(utxo)
            collectedTokens += utxo.tokenAmount(policyId, assetName)
            collectedAda += utxo.lovelace
        }

        // Check if we have enough tokens
        val totalAvailableTokens = utxos.sumOf { it.tokenAmount(policyId, assetName) }
        if (collectedTokens < requiredTokenAmount) {
            throw CardanoError.InsufficientTokens(
                policyId = policyId,
                assetName = assetName,
                available = totalAvailableTokens,
                required = requiredTokenAmount
            )
        }

        // If we have enough tokens but not enough ADA, add more UTXOs for ADA
        if (collectedAda < requiredAda) {
            val remaining = sorted.filter { it !in selected }
                .sortedByDescending { it.lovelace }
            for (utxo in remaining) {
                if (collectedAda >= requiredAda) break
                selected.add(utxo)
                collectedAda += utxo.lovelace
                collectedTokens += utxo.tokenAmount(policyId, assetName)
            }
        }

        if (collectedAda < requiredAda) {
            throw CardanoError.InsufficientAda(
                available = collectedAda,
                required = requiredAda
            )
        }

        return selected
    }
}

// ---- Utility functions ----

private fun hexToBytes(hex: String): ByteArray {
    val len = hex.length / 2
    val result = ByteArray(len)
    for (i in 0 until len) {
        result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return result
}

/**
 * Minimal Bech32 encoder for CIP-14 fingerprints.
 */
private fun bech32Encode(hrp: String, data: ByteArray): String {
    val values = convertBits(data, 8, 5, true)
    val checksum = bech32Checksum(hrp, values)
    val combined = values + checksum
    val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    val encoded = combined.map { charset[it.toInt() and 0x1f] }.joinToString("")
    return "$hrp${"1"}$encoded"
}

private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
    var acc = 0
    var bits = 0
    val result = mutableListOf<Byte>()
    val maxv = (1 shl toBits) - 1
    for (b in data) {
        acc = (acc shl fromBits) or (b.toInt() and 0xff)
        bits += fromBits
        while (bits >= toBits) {
            bits -= toBits
            result.add(((acc shr bits) and maxv).toByte())
        }
    }
    if (pad && bits > 0) {
        result.add(((acc shl (toBits - bits)) and maxv).toByte())
    }
    return result.toByteArray()
}

private fun bech32Checksum(hrp: String, data: ByteArray): ByteArray {
    val values = bech32HrpExpand(hrp) + data + byteArrayOf(0, 0, 0, 0, 0, 0)
    val polymod = bech32Polymod(values) xor 1
    return ByteArray(6) { ((polymod shr (5 * (5 - it))) and 31).toByte() }
}

private fun bech32HrpExpand(hrp: String): ByteArray {
    val result = ByteArray(hrp.length * 2 + 1)
    for (i in hrp.indices) {
        result[i] = (hrp[i].code shr 5).toByte()
        result[i + hrp.length + 1] = (hrp[i].code and 31).toByte()
    }
    result[hrp.length] = 0
    return result
}

private fun bech32Polymod(values: ByteArray): Int {
    val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    var chk = 1
    for (v in values) {
        val b = chk shr 25
        chk = ((chk and 0x1ffffff) shl 5) xor (v.toInt() and 0xff)
        for (i in 0..4) {
            if ((b shr i) and 1 == 1) chk = chk xor gen[i]
        }
    }
    return chk
}
