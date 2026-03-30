package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Deprecated(
    message = "Use com.lybia.cryptowallet.models.cardano API models in commonMain instead",
    level = DeprecationLevel.WARNING
)
class CardanoCurrentBestBlock : Serializable {
    @SerializedName("epoch")
    val epoch: Int = 0

    @SerializedName("hash")
    val blockHash: String = ""

    @SerializedName("height")
    val height: Int = 0

    @SerializedName("slot")
    val slot: Int = 0

}