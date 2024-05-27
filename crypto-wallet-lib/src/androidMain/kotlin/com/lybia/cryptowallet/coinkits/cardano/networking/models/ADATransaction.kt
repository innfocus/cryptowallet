package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.annotations.SerializedName
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toDate
import java.io.Serializable
import java.util.*

class ADATransaction : Serializable {
    @SerializedName("hash")
    val transactionID: String = ""

    @SerializedName("time")
    private val _time: String = ""

    @SerializedName("last_update")
    private val _lastUpdate: String = ""

    @SerializedName("tx_state")
    val state: String = ""

    @SerializedName("block_num")
    val blockNum: String = ""

    @SerializedName("block_hash")
    val blockHash: String = ""

    @SerializedName("inputs")
    lateinit var inputs: Array<ADATransactionInOut>

    @SerializedName("outputs")
    lateinit var outputs: Array<ADATransactionInOut>

    var fee: Double = 0.0
    var amount: Double = 0.0

    fun time(): Date {
        return _time.toDate()
    }

    fun lastUpdate(): Date {
        return _lastUpdate.toDate()
    }
}
