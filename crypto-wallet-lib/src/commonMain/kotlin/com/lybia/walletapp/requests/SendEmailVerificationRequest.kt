package com.lybia.walletapp.requests

import kotlinx.serialization.Serializable

@Serializable
data class SendEmailVerificationRequest(
    val email: String,
)