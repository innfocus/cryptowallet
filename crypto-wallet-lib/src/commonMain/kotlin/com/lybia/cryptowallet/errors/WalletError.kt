package com.lybia.cryptowallet.errors

import com.lybia.cryptowallet.enums.NetworkName

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

    data class NetworkError(val reason: String) :
        WalletError("Network error: $reason")
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

/**
 * Staking-specific errors.
 */
sealed class StakingError(override val message: String) : Exception(message) {
    data class PoolNotFound(val poolAddress: String) :
        StakingError("Stake pool not found: $poolAddress")

    data class InsufficientStakingBalance(val available: Double, val required: Double) :
        StakingError("Insufficient staking balance: available=$available, required=$required")

    data class DelegationAlreadyActive(val currentPool: String) :
        StakingError("Delegation already active to pool: $currentPool")

    data class NoDelegationActive(val stakingAddress: String) :
        StakingError("No active delegation for: $stakingAddress")
}

/**
 * Bridge-specific errors.
 */
sealed class BridgeError(override val message: String) : Exception(message) {
    data class UnsupportedBridgePair(val fromChain: NetworkName, val toChain: NetworkName) :
        BridgeError("Unsupported bridge pair: $fromChain → $toChain")

    data class BridgeServiceUnavailable(val service: String) :
        BridgeError("Bridge service unavailable: $service")

    data class BridgeTransactionFailed(val txHash: String, val reason: String) :
        BridgeError("Bridge transaction failed: $txHash — $reason")

    data class InsufficientBridgeBalance(val available: Double, val required: Double, val fee: Double) :
        BridgeError("Insufficient balance for bridge: available=$available, required=$required (including fee=$fee)")
}
