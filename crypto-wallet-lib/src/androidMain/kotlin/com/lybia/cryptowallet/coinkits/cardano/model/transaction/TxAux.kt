package com.lybia.cryptowallet.coinkits.cardano.model.transaction

import android.util.Base64
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.SimpleValue
import java.io.ByteArrayOutputStream

class TxAux(val tx: Tx, private val witnessSet: TransactionWitnessSet) {

//    body: &TransactionBody
//    witness_set: &TransactionWitnessSet
//    metadata: Option<TransactionMetadata>

    fun serializer(): List<DataItem> {
        val rs = CborBuilder().addArray()
        val txCbor = tx.serializer()
        txCbor.forEach {
            rs.add(it)
        }

        val witnessCbor = witnessSet.serializer()
        witnessCbor.forEach {
            rs.add(it)
        }
        rs.add(SimpleValue.NULL)
        return rs.end().build()
    }

    fun base64(): String {
        return Base64.encodeToString(encode(), 2)
    }

    fun encode(): ByteArray {
        val baos = ByteArrayOutputStream()
        CborEncoder(baos).nonCanonical().encode(this.serializer())
        return baos.toByteArray()
    }
}