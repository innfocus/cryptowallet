package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class CennzScanAsset : Serializable {
    @SerializedName("assetId")
    var assetID: Int = 0

    var free: Long = 0
    var lock: Long = 0

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}