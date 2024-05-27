package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import java.io.Serializable

class CentralityAppResponse<T> : Serializable {
    val code: Long = 0
    val message: String = ""
    val ttl: Long = 0
    var data: T? = null

    override fun toString(): String {
        val gson = Gson()
        return gson.toJson(this)
    }
}

class ScanAccount : Serializable {
    val address: String = ""
    val nonce: Long = 0
    lateinit var balances: List<CennzScanAsset>
}

class ScanTransfer : Serializable {
    lateinit var transfers: List<CennzTransfer>
    val count: Long = 0
}