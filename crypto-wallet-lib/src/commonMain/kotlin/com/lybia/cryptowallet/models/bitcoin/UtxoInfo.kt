package com.lybia.cryptowallet.models.bitcoin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an Unspent Transaction Output (UTXO) from the Esplora API.
 */
@Serializable
data class UtxoInfo(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: UtxoStatus
) {
    @Serializable
    data class UtxoStatus(
        val confirmed: Boolean,
        @SerialName("block_height") val blockHeight: Long? = null,
        @SerialName("block_hash") val blockHash: String? = null,
        @SerialName("block_time") val blockTime: Long? = null
    )
}

/**
 * Fee rate recommendations from Mempool.space / Esplora.
 */
@Serializable
data class FeeRateRecommendation(
    @SerialName("fastestFee") val fastestFee: Long,
    @SerialName("halfHourFee") val halfHourFee: Long,
    @SerialName("hourFee") val hourFee: Long,
    @SerialName("economyFee") val economyFee: Long,
    @SerialName("minimumFee") val minimumFee: Long
)
