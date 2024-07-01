package com.lybia.walletapp.requests

import kotlinx.serialization.Serializable

@Serializable
data class SendConsultationRequest(
    val inquiry: String,
    val email: String? = null,
    val name: String? = null,
)