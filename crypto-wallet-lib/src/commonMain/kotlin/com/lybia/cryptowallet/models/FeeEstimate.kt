package com.lybia.cryptowallet.models

data class FeeEstimateParams(
    val fromAddress: String,
    val toAddress: String,
    val amount: Double,
    val data: String? = null
)

data class FeeEstimate(
    val fee: Double,
    val gasLimit: Long? = null,
    val gasPrice: Long? = null,
    val unit: String = "native"
)
