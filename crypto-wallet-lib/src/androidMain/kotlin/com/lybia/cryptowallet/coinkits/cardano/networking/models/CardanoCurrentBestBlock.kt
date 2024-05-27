package com.lybia.cryptowallet.coinkits.cardano.networking.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

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