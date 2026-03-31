package com.lybia.cryptowallet.wallets.centrality.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CennzScanAsset(
    @SerialName("assetId") val assetId: Int = 0,
    val free: Long = 0,
    val lock: Long = 0
)
