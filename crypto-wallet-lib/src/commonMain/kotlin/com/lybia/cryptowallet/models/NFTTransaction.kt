package com.lybia.cryptowallet.models

import kotlinx.serialization.Serializable

@Serializable
data class NFTTransaction(
    val contractAddress: String,
    val tokenID: String,
    val tokenName: String,
    val tokenSymbol: String,
    val from: String,
    val to: String
)
