package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Deprecated(
    message = "Use com.lybia.cryptowallet.models.cardano API models in commonMain instead",
    level = DeprecationLevel.WARNING
)
class ADATransactionInOut : Serializable {
    @SerializedName("address")
    var address: String = ""

    @SerializedName("amount")
    var value: Double = 0.0
}