package com.lybia.cryptowallet.coinkits.cardano.model.transaction

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray

class TxoPointer(private val txId: String, val index: Long) {
    fun serializer(): List<DataItem> {
        val utxoCbor = CborBuilder().addArray().add(ByteString(txId.fromHexToByteArray()))
            .add(UnsignedInteger(index)).end().build()
        return utxoCbor
    }
}