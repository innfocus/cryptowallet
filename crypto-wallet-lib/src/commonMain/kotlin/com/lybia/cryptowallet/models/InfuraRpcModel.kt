package com.lybia.cryptowallet.models

import kotlinx.serialization.Serializable


@Serializable
data class InfuraRpcRequest(
    val jsonrpc: String,
    val method: String,
    val params: List<String>,
    val id: Int
)
@Serializable
data class InfuraRpcBalanceResponse(
    val jsonrpc: String?,
    val id: Int?,
    val result: String? = null,
    val error: InfuraRpcModelError? = null
)
@Serializable
data class InfuraRpcModelError(
    val code: Float,
    val message: String
)