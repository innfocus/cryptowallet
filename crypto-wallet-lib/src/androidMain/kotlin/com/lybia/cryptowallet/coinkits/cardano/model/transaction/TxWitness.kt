package com.lybia.cryptowallet.coinkits.cardano.model.transaction

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import java.io.ByteArrayOutputStream

class TxWitness(
    private val extendedPublicKey: ByteArray,
    private val signature: ByteArray,
    private val chainCode: ByteArray,
    private val attributes: ByteArray
) {

    fun serializer(): List<DataItem> {
        val witness = CborBuilder().addArray()
        witness.add(extendedPublicKey)
        witness.add(signature)
        witness.add(chainCode)
        witness.add(attributes)
        return witness.end().build()
    }

    fun encode(): ByteArray {
        val baos = ByteArrayOutputStream()
        CborEncoder(baos).nonCanonical().encode(this.serializer())
        return baos.toByteArray()
    }

}