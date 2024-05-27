package com.lybia.cryptowallet.coinkits.cardano.model.transaction

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Bech32
import java.lang.Exception

class TxOut(val address: String, val value: Long) {
    fun serializer(): List<DataItem>? {
        try {
            val dataDecode = Bech32.bech32Decode(address)
            val u5Data = dataDecode.data
            val u8Data = Bech32.convertBits(dataDecode.data, 0, u5Data.size, 5, 8, false)
            val rs = CborBuilder().addArray()
            rs.add(u8Data)
            rs.add(value)
            return rs.end().build()
        } catch (e: Exception) {

        }
        return try {
            val addData = Base58.decode(address)
            val rs = CborBuilder().addArray()
            rs.add(addData)
            rs.add(value)
            rs.end().build()
        } catch (e: CborException) {
            null
        }
    }
}