package com.lybia.cryptowallet.wallets.cardano

/**
 * Cardano address types.
 */
enum class CardanoAddressType {
    BYRON,
    SHELLEY_BASE,
    SHELLEY_ENTERPRISE,
    SHELLEY_REWARD,
    UNKNOWN
}

/**
 * Sealed class for Cardano address errors.
 */
sealed class CardanoError(override val message: String) : Exception(message) {
    data class InvalidByronAddress(val address: String, val reason: String) :
        CardanoError("Invalid Byron address '$address': $reason")

    data class InvalidShelleyAddress(val address: String, val reason: String) :
        CardanoError("Invalid Shelley address '$address': $reason")

    data class InsufficientTokens(
        val policyId: String,
        val assetName: String,
        val available: Long,
        val required: Long
    ) : CardanoError("Insufficient tokens (policy=$policyId, asset=$assetName): available=$available, required=$required")

    data class ApiError(
        val statusCode: Int?,
        override val message: String
    ) : CardanoError(message)
}
