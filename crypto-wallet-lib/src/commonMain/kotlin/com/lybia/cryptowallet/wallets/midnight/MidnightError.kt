package com.lybia.cryptowallet.wallets.midnight

/**
 * Sealed class for Midnight Network errors.
 */
sealed class MidnightError(override val message: String) : Exception(message) {
    data class InsufficientTDust(val balance: Long, val required: Long) :
        MidnightError("Insufficient tDUST: balance=$balance, required=$required")

    data class ConnectionError(val endpoint: String, override val cause: Throwable) :
        MidnightError("Connection error to $endpoint: ${cause.message}")

    data class TransactionRejected(val reason: String) :
        MidnightError("Transaction rejected: $reason")

    data class InvalidAddress(val address: String) :
        MidnightError("Invalid Midnight address: $address")
}
