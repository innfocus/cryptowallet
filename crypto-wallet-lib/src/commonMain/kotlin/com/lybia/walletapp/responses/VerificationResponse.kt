package com.lybia.walletapp.responses

import kotlinx.serialization.Serializable

@Serializable
data class VerificationResponse(
    val success: Boolean,
    val message: String,
    val code: Int
)