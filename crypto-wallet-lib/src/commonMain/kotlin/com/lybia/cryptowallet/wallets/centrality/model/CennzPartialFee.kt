package com.lybia.cryptowallet.wallets.centrality.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CennzPartialFee(
    @SerialName("class") val classFee: String = "",
    @SerialName("partialFee") val partialFee: Int = 0,
    val weight: Int = 0
)
