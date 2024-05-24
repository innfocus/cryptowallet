package com.lybia.cryptowallet.models

import kotlinx.serialization.Serializable

@Serializable
data class TransferCoinModel(
    val nonce: String,
    val amount: Double,
    val addressTo: String,
    val addressFrom: String,
)

@Serializable
data class TransferTokenModel(
    val nonce: String,
    val addressFrom: String,
    val contractAddress: String,
    val dataEncodeABI: String,
    var value: String
)


@Serializable
data class EstimateGasRequestModel(
    val from: String?,
    val to: String,
    val value: String?,
    val data: String?,
) {
    fun toJsonString(): String {
        return "{ \"from\": \"$from\", \"to\": \"$to\", \"value\": \"$value\", \"data\": \"$data\" }"
    }
}

@Serializable
data class TransferResponseModel(
    val success: Boolean,
    val error: String?,
    val txHash: String?
)