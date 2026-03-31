package com.lybia.cryptowallet.wallets.centrality.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CennzTransfer(
    val from: String = "",
    val to: String = "",
    @SerialName("extrinsic_index") val extrinsicIndex: String = "",
    val hash: String = "",
    @SerialName("block_num") val blockNum: Long = 0,
    @SerialName("block_timestamp") val blockTimestamp: Long = 0,
    val module: String = "",
    val amount: Long = 0,
    @SerialName("asset_id") val assetId: Int = 0,
    val success: Boolean = true
)
