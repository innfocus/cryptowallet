package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class CennzExtrinsic : Serializable {
    @SerializedName("account_id")
    val accountID: String = ""

    @SerializedName("block_num")
    val blockNum: Long = 0

    @SerializedName("block_timestamp")
    val blockTimestamp: Long = 0

    @SerializedName("extrinsic_index")
    val extrinsicIndex: String = ""

    @SerializedName("extrinsic_hash")
    val extrinsicHash: String = ""

    @SerializedName("call_module")
    val callModule: String = ""

    @SerializedName("call_module_function")
    val callModuleFunction: String = ""

    val params: String = ""
    val fee: Long = 0
    val success: Boolean = true
    val nonce: Long = 0

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}