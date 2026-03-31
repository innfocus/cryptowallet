package com.lybia.cryptowallet.wallets.centrality

/**
 * Centrality (CennzNet)-specific errors.
 */
sealed class CentralityError(override val message: String) : Exception(message) {
    data class RpcError(val method: String, val code: Int, override val message: String) :
        CentralityError("RPC error in $method (code $code): $message")

    data class InvalidSS58Address(val address: String) :
        CentralityError("Invalid SS58 address: $address")

    data class SigningFailed(val reason: String) :
        CentralityError("Signing failed: $reason")

    data class ExtrinsicSubmitFailed(val hash: String, val reason: String) :
        CentralityError("Extrinsic submit failed: $hash — $reason")

    data class InvalidScaleEncoding(val value: String, val reason: String) :
        CentralityError("Invalid SCALE encoding for $value: $reason")
}
