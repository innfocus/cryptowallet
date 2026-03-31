package com.lybia.cryptowallet.models.bridge

import com.lybia.cryptowallet.enums.NetworkName

/**
 * Bridge status enum representing the lifecycle of a bridge transaction.
 */
enum class BridgeStatus(val value: String) {
    PENDING("pending"),
    CONFIRMING("confirming"),
    COMPLETED("completed"),
    FAILED("failed")
}

/**
 * Fee estimate for a bridge transaction, broken down by component.
 */
data class BridgeFeeEstimate(
    val sourceFee: Double,
    val destinationFee: Double,
    val bridgeFee: Double,
    val totalFee: Double,
    val sourceUnit: String,
    val destinationUnit: String
)

/**
 * Information about a bridge transaction.
 */
data class BridgeTransactionInfo(
    val bridgeTxId: String,
    val sourceTxHash: String?,
    val destinationTxHash: String?,
    val fromChain: NetworkName,
    val toChain: NetworkName,
    val amount: Long,
    val status: BridgeStatus,
    val createdAt: Long
)
