package com.lybia.cryptowallet.wallets.centrality.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.centrality.codec.ScaleCodec

/**
 * Builder for Substrate extrinsic (signed transaction).
 * Replaces ExtrinsicBase from androidMain.
 *
 * Extrinsic format:
 * [compact_length][version_byte(132)][signature_bytes][method_bytes]
 *
 * Payload format (for signing):
 * [method_bytes][era][compact_nonce][tx_payment][specVersion_LE4][txVersion_LE4][genesisHash][blockHash]
 */
class ExtrinsicBuilder {
    // Version byte: 132 = 0x84 (signed, version 4)
    private val version: Int = 132

    // Signature params
    private var signer: String = ""
    private var signatureBytes: ByteArray = ByteArray(64)
    private var era: ByteArray = ByteArray(2)
    private var nonce: Int = 0
    private var transactionPayment: ByteArray = byteArrayOf(0, 0)

    // Method params
    private var callIndex: String = "0x0401"
    private var toAddress: String = ""
    private var assetId: Int = 1
    private var amount: Long = 10000

    // Sign options
    private var specVersion: Int = 39
    private var transactionVersion: Int = 5
    private var genesisHash: String = ""
    private var blockHash: String = ""

    /** Set method parameters (destination, amount, assetId). */
    fun paramsMethod(to: String, amount: Long, assetId: Int = 1): ExtrinsicBuilder {
        this.toAddress = to
        this.amount = amount
        this.assetId = assetId
        return this
    }

    /** Set signature parameters (signer address, nonce). */
    fun paramsSignature(signer: String, nonce: Int): ExtrinsicBuilder {
        this.signer = signer
        this.nonce = nonce
        return this
    }

    /** Set sign options (chain state). */
    fun signOptions(
        specVersion: Int,
        transactionVersion: Int,
        genesisHash: String,
        blockHash: String,
        era: ByteArray
    ): ExtrinsicBuilder {
        this.specVersion = specVersion
        this.transactionVersion = transactionVersion
        this.genesisHash = genesisHash
        this.blockHash = blockHash
        this.era = era
        return this
    }

    /** Apply signature from hex string. */
    fun sign(signatureHex: String): ExtrinsicBuilder {
        this.signatureBytes = signatureHex.removePrefix("0x").fromHexToByteArray()
        return this
    }

    /**
     * Create unsigned payload for signing.
     * Format: method + era + compact(nonce) + txPayment
     *       + specVersion(LE4) + txVersion(LE4) + genesisHash + blockHash
     */
    fun createPayload(): ByteArray {
        var encoded = encodeMethod()
        encoded += era
        encoded += ScaleCodec.compactToU8a(BigInteger(nonce.toLong()))
        encoded += transactionPayment
        encoded += ScaleCodec.toArrayLikeLE(BigInteger(specVersion.toLong()), 4)
        encoded += ScaleCodec.toArrayLikeLE(BigInteger(transactionVersion.toLong()), 4)
        encoded += genesisHash.removePrefix("0x").fromHexToByteArray()
        encoded += blockHash.removePrefix("0x").fromHexToByteArray()
        return encoded
    }

    /**
     * Encode complete signed extrinsic.
     * Format: compact_length(version + signature + method)
     */
    fun toU8a(): ByteArray {
        var encoded = ByteArray(1)
        encoded[0] = version.toByte()
        encoded += encodeSignature()
        encoded += encodeMethod()
        return ScaleCodec.compactAddLength(encoded)
    }

    /** Encode to hex string with "0x" prefix. */
    fun toHex(): String = "0x" + toU8a().toHexString()

    // ─── Internal encoding ──────────────────────────────────────

    /** Encode signature section: publicKey + signatureBytes + era + compact(nonce) + txPayment */
    private fun encodeSignature(): ByteArray {
        val address = CentralityAddress(signer)
        var encoded = ByteArray(0)
        if (address.isValid()) {
            encoded += address.publicKey!!
        }
        encoded += signatureBytes
        encoded += era
        encoded += ScaleCodec.compactToU8a(BigInteger(nonce.toLong()))
        encoded += transactionPayment
        return encoded
    }

    /** Encode method section: callIndex + compact(assetId) + publicKey(to) + compact(amount) */
    private fun encodeMethod(): ByteArray {
        var encoded = callIndex.removePrefix("0x").fromHexToByteArray()
        encoded += ScaleCodec.compactToU8a(BigInteger(assetId.toLong()))
        val toAddr = CentralityAddress(toAddress)
        if (toAddr.isValid()) {
            encoded += toAddr.publicKey!!
        }
        encoded += ScaleCodec.compactToU8a(BigInteger(amount))
        return encoded
    }
}
