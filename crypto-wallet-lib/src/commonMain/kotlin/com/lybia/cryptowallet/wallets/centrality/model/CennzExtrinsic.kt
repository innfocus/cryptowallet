package com.lybia.cryptowallet.wallets.centrality.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CennzExtrinsic(
    @SerialName("account_id") val accountId: String = "",
    @SerialName("block_num") val blockNum: Long = 0,
    @SerialName("block_timestamp") val blockTimestamp: Long = 0,
    @SerialName("extrinsic_index") val extrinsicIndex: String = "",
    @SerialName("extrinsic_hash") val extrinsicHash: String = "",
    @SerialName("call_module") val callModule: String = "",
    @SerialName("call_module_function") val callModuleFunction: String = "",
    val params: String = "",
    val fee: Long = 0,
    val success: Boolean = true,
    val nonce: Long = 0
)
