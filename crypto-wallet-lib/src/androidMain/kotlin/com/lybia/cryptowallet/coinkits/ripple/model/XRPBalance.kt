package com.lybia.cryptowallet.coinkits.ripple.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

class XRPBalance(

    @SerializedName("currency")
    val currency   : String   = "",

    @SerializedName("value")
    val value   : Double = 0.0 ) {

    companion object {
        fun parser(json: JsonElement): Array<XRPBalance> {
            return try {
                Gson().fromJson(json, Array<XRPBalance>::class.java) ?: emptyArray()
            }catch (e: JsonSyntaxException) {
                emptyArray()
            }
        }
    }
}