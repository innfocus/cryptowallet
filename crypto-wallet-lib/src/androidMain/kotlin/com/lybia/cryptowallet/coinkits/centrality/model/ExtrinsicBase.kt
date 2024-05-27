package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray
import com.lybia.cryptowallet.coinkits.centrality.networking.toHexWithPrefix
import com.lybia.cryptowallet.coinkits.centrality.utils.U8a

class ExtrinsicBase {
    var version: Int = 132
    var signature: Signature = Signature()
    var method: Method = Method()
    var specVersion: Int = 39
    var transactionVersion: Int = 5
    var genesisHash: String = ""
    var blockHash: String = ""

    fun toU8a(): ByteArray {
        var encoded = ByteArray(1)
        encoded[0] = 132.toByte()
        encoded += this.signature.toU8a()
        encoded += this.method.toU8a()
        return U8a.compactAddLength(encoded)
    }

    fun toHex(): String {
        return toU8a().toHexWithPrefix()
    }

    fun paramsMethod(
        toAddress: String,
        amount: Long,
        assetId: Int = 1
    ) {
        this.method.args.to = toAddress
        this.method.args.amount = amount
        this.method.args.assetId = assetId
    }

    fun sign(signature: String) {
        this.signature.signature = signature.removePrefix("0x").fromHexToByteArray()
    }

    fun paramsSignature(
        signer: String,
        nonce: Int
    ) {
        this.signature.signer = signer
        this.signature.nonce = nonce
    }

    fun signOptions(
        specVersion: Int,
        transactionVersion: Int,
        genesisHash: String,
        blockHash: String,
        era: ByteArray
    ) {
        this.specVersion = specVersion
        this.transactionVersion = transactionVersion
        this.genesisHash = genesisHash
        this.blockHash = blockHash
        this.signature.era = era
    }

    fun createPayload(): ByteArray {
        var encoded = this.method.toU8a()
        encoded += this.signature.era
        encoded += U8a.compactToU8a(this.signature.nonce.toBigInteger())
        encoded += this.signature.transactionPayment

        encoded += U8a.toArrayLikeLE(this.specVersion.toBigInteger(), 4)
        encoded += U8a.toArrayLikeLE(this.transactionVersion.toBigInteger(), 4)

        // method chain_getBlockHash
        encoded += this.genesisHash.removePrefix("0x").fromHexToByteArray()

        // chain_getFinalizedHead
        encoded += this.blockHash.removePrefix("0x").fromHexToByteArray()
        return encoded
    }

    class Signature {
        var signer: String = ""
        var signature: ByteArray = ByteArray(64)
        var era: ByteArray = ByteArray(2)
        var nonce: Int = 0
        var transactionPayment: ByteArray = ByteArray(2)

        fun toU8a(): ByteArray {
            transactionPayment[0] = 0.toByte()
            transactionPayment[1] = 0.toByte()

            val address = CennzAddress(this.signer)
            var encoded = ByteArray(0)
            if (address.checkValid()) {
                encoded += address.publicKey!!
            }
//            encoded += 1.toByte()
            encoded += this.signature
            encoded += era
            encoded += U8a.compactToU8a(this.nonce.toBigInteger())
            encoded += transactionPayment
            return encoded
        }

        fun toHex(): String {
            return toU8a().toHexWithPrefix()
        }

        override fun toString(): String {
            val gson = Gson()
            return gson.toJson(this)
        }
    }

    class Method {
        var callIndex: String = "0x0401"
        var args: MethodArgs = MethodArgs()

        fun toU8a(): ByteArray {
            var encoded: ByteArray = callIndex.removePrefix("0x").fromHexToByteArray()
            encoded += this.args.toU8a()
            return encoded
        }

        fun toHex(): String {
            return toU8a().toHexWithPrefix()
        }

        override fun toString(): String {
            val gson = Gson()
            return gson.toJson(this)
        }
    }

    class MethodArgs {
        var to: String = ""
        var assetId: Int = 1
        var amount: Long = 10000

        fun toU8a(): ByteArray {
            val address = CennzAddress(this.to)
            var encoded = U8a.compactToU8a(assetId.toBigInteger())
            if (address.checkValid()) {
                encoded += address.publicKey!!
            }
            encoded += U8a.compactToU8a(amount.toBigInteger())
            return encoded
        }

        fun toHex(): String {
            return toU8a().toHexWithPrefix()
        }

        override fun toString(): String {
            val gson = Gson()
            return gson.toJson(this)
        }
    }

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }

}