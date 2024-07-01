package com.lybia.walletapp.responses

import kotlinx.serialization.Serializable

@Serializable
data class BaseApiResponse(
    val success: Boolean,
    val message: String,
    val code: Int? = null
)