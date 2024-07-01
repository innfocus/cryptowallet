package com.lybia.walletapp.responses

import kotlinx.serialization.Serializable

@Serializable
data class CheckEmailVerificationResponse (
    val success: Boolean,
    val message: String,
    val authToken: String,
    val user: UserResponse
)