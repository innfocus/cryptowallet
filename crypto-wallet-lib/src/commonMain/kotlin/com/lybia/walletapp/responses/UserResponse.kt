package com.lybia.walletapp.responses

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Long,
    val phoneNumber: String? = null, // Assuming phone number can be optional
    val countryCode: String? = null,
    val lang: String?,
    val notification: Boolean,
    val invitationCode: String? = null,
    val invitationLink: String? = null,
    val agencyLevel: String? = null,
    val referrerId: Long? = null,
    val referrer: ReferrerResponse? = null,
    val email: String? = null
)

@Serializable
data class ReferrerResponse(
    val id: Long,
    val phoneNumber: String? = null, // Assuming phone number can be optional
    val countryCode: String? = null,
    val email: String? = null
)