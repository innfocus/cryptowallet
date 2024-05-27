package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class ADATransactionInOut : Serializable {
    @SerializedName("address")
    var address: String = ""

    @SerializedName("amount")
    var value: Double = 0.0
}