package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

data class ADAUnspentTransaction(
    @SerializedName("utxo_id")
    val utoxID          : String    = "",
    @SerializedName("tx_hash")
    val transationHash  : String    = "",
    @SerializedName("tx_index")
    val transactionIdx  : Int       = 0,
    @SerializedName("receiver")
    val receiver        : String    = "",
    @SerializedName("amount")
    private val _amount : String    = ""
){
    val amount: Long get() { return _amount.toLongOrNull() ?: 0 }
    companion object {
        fun parser(json: JsonElement): Array<ADAUnspentTransaction> {
            return try {
                Gson().fromJson(json, Array<ADAUnspentTransaction>::class.java) ?: emptyArray()
            }catch (e: JsonSyntaxException) {
                emptyArray()
            }
        }
    }
}