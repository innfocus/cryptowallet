package com.lybia.cryptowallet.coinkits.bitcoin.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName


class BTCTransactionOutput(
    @SerializedName("tx_hash")
    val hash                        : String   = "",
    @SerializedName("tx_hash_big_endian")
    val hashBigEndian               : String   = "",
    @SerializedName("tx_index")
    val idx                         : Int   = 0,
    @SerializedName("tx_output_n")
    val outputNumber                : Int   = 0,
    @SerializedName("script")
    val script                      : String   = "",
    @SerializedName("value")
    val amount                      : Int   = 0,
    @SerializedName("value_hex")
    val valueHex                    : String   = "",
    @SerializedName("confirmations")
    val confirmations               : Int   = 0
){
    companion object {
        fun parser(json: JsonElement): Array<BTCTransactionOutput> {
            return try {
                Gson().fromJson(json, Array<BTCTransactionOutput>::class.java) ?: emptyArray()
            }catch (e: JsonSyntaxException) {
                emptyArray()
            }
        }
    }
}