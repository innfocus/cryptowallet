package com.lybia.cryptowallet.errors

/**
 * Base sealed class for wallet errors across all chains.
 */
sealed class WalletError(override val message: String) : Exception(message) {
    data class ConnectionError(val endpoint: String, override val cause: Throwable) :
        WalletError("Connection error to $endpoint: ${cause.message}")

    data class InsufficientFunds(val available: Double, val required: Double) :
        WalletError("Insufficient funds: available=$available, required=$required")

    data class InvalidAddress(val address: String, val reason: String) :
        WalletError("Invalid address '$address': $reason")

    data class TransactionRejected(val reason: String) :
        WalletError("Transaction rejected: $reason")

    data class UnsupportedOperation(val operation: String, val chain: String) :
        WalletError("Unsupported operation '$operation' for chain '$chain'")
}

/**
 * Bitcoin-specific errors.
 */
sealed class BitcoinError(override val message: String) : Exception(message) {
    data class InsufficientUtxos(val available: Long, val required: Long) :
        BitcoinError("Insufficient UTXOs: available=$available satoshi, required=$required satoshi")

    data class InvalidTransaction(val reason: String) :
        BitcoinError("Invalid Bitcoin transaction: $reason")
}

/**
 * Ripple-specific errors.
 */
sealed class RippleError(override val message: String) : Exception(message) {
    data class AccountNotFound(val address: String) :
        RippleError("Ripple account not found: $address")

    data class TransactionFailed(val engineResult: String, val engineResultMessage: String) :
        RippleError("Ripple transaction failed: $engineResult — $engineResultMessage")
}
