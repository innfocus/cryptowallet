package com.lybia.cryptowallet.coinkits.bitcoin.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

class BTCBalanceSummary(
    @SerializedName("final_balance")
    var finalBalance: Double    = 0.0,
    @SerializedName("n_tx")
    var txNumber: Int           = 0,
    @SerializedName("total_received")
    var totalReceived: Double   = 0.0
) {
    var address: String = ""

    companion object {
        fun parser(json: JsonElement): BTCBalanceSummary? {
            return try {
                Gson().fromJson(json, BTCBalanceSummary::class.java)
            }catch (e: JsonSyntaxException) {
                null
            }
        }
    }
}