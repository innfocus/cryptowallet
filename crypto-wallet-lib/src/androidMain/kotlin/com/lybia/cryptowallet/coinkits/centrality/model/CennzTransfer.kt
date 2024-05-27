package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class CennzTransfer : Serializable {
    val from: String = ""
    val to: String = ""

    @SerializedName("extrinsic_index")
    val extrinsicIndex: String = ""

    val hash: String = ""

    @SerializedName("block_num")
    val blockNum: Long = 0

    @SerializedName("block_timestamp")
    val blockTimestamp: Long = 0

    val module: String = ""
    val amount: Long = 0

    @SerializedName("asset_id")
    val assetID: Int = 0

    val success: Boolean = true

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}