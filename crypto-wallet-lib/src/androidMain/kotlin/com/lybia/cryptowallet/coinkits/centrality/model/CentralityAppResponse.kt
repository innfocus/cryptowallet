package com.lybia.cryptowallet.coinkits.centrality.model

import com.google.gson.Gson
import java.io.Serializable

@Deprecated("Centrality is Android-only legacy code. Will be migrated to commonMain in a future phase.", level = DeprecationLevel.WARNING)
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

@Deprecated("Centrality is Android-only legacy code. Will be migrated to commonMain in a future phase.", level = DeprecationLevel.WARNING)
class ScanAccount : Serializable {
    val address: String = ""
    val nonce: Long = 0
    lateinit var balances: List<CennzScanAsset>
}

@Deprecated("Centrality is Android-only legacy code. Will be migrated to commonMain in a future phase.", level = DeprecationLevel.WARNING)
class ScanTransfer : Serializable {
    lateinit var transfers: List<CennzTransfer>
    val count: Long = 0
}