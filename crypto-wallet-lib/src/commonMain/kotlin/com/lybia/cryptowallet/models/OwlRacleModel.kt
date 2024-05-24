package com.lybia.cryptowallet.models

import kotlinx.serialization.Serializable


@Serializable
data class OwlRacleModel<T> (
    val timestamp: String,
    val lastBlock: Long,
    val avgTime: Double,
    val avgTx: Double,
    val avgGas: Double,
    val speeds: List<T>
)
data class TransactionGas(
    val acceptance: Int,
    val gasPrice: Double,
    val estimatedFee: Double
)