package com.lybia.walletapp.requests

import kotlinx.serialization.Serializable

@Serializable
data class CheckEmailVerificationRequest(
    val email: String,
    val verificationCode: String,
)