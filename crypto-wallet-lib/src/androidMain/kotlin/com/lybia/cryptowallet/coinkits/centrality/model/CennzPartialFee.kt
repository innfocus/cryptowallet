package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class CennzPartialFee : Serializable {

    @SerializedName("class")
    var classFee: String = ""


    @SerializedName("partialFee")
    var partialFee: Int = 0

    var weight: Int = 0

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}