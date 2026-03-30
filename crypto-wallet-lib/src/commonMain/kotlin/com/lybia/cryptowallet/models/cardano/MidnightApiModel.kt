package com.lybia.cryptowallet.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidnightTransaction(
    @SerialName("tx_hash") val txHash: String,
    val amount: Long,
    val timestamp: Long,
    @SerialName("from_address") val fromAddress: String,
    @SerialName("to_address") val toAddress: String
)

@Serializable
data class MidnightBalanceResponse(
    val balance: Long,
    val address: String
)
