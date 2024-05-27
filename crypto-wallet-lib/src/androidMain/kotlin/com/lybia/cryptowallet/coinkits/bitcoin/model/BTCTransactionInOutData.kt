package com.lybia.cryptowallet.coinkits.bitcoin.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName


class BTCTransactionInOutData(
    @SerializedName("spent")
    val spent                   : Boolean   = true,
    @SerializedName("tx_index")
    val index                   : Int       = 0,
    @SerializedName("type")
    val type                    : Int       = 0,
    @SerializedName("addr")
    val address                 : String    = "",
    @SerializedName("value")
    val value                   : Float     = 0.0f,
    @SerializedName("n")
    val n                       : Int       = 0,
    @SerializedName("script")
    val script                  : String    = "",
    @SerializedName("prev_out")
    private val prevOut         : BTCTransactionInOutData?
) {
    companion object {
        fun parser(json: JsonElement): Array<BTCTransactionInOutData> {
            return try {
                if (json.isJsonArray) {
                    val rs = Gson().fromJson(json.asJsonArray, Array<BTCTransactionInOutData>::class.java) ?: emptyArray()
                    rs.map { it.prevOut ?: it }.toTypedArray()
                }else{
                    emptyArray()
                }
            }catch (e: JsonSyntaxException) {
                emptyArray()
            }
        }
    }
}