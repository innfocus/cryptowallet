package com.lybia.cryptowallet.coinkits.cardano.model.transaction

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import java.io.ByteArrayOutputStream

class TransactionWitnessSet(val bootstraps: Array<TxWitness>) {

    fun serializer(): List<DataItem> {
        val rs = CborBuilder().addMap()

        val inputArray = rs.putArray(2)
        val ls = mutableListOf<DataItem>()
        bootstraps.map { it.serializer() }.forEach {
            ls.addAll(it)
        }
        ls.forEach {
            inputArray.add(it)
        }

        return rs.end().build()
    }

    fun encode(): ByteArray {
        val baos = ByteArrayOutputStream()
        CborEncoder(baos).nonCanonical().encode(this.serializer())
        return baos.toByteArray()
    }
}