package com.lybia.cryptowallet.wallets.centrality.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanAccount(
    val address: String = "",
    val nonce: Long = 0,
    val balances: List<CennzScanAsset> = emptyList()
)

@Serializable
data class ScanTransfer(
    val transfers: List<CennzTransfer> = emptyList(),
    val count: Long = 0
)

@Serializable
data class ScanAccountResponse(
    val code: Long = 0,
    val message: String = "",
    val ttl: Long = 0,
    val data: ScanAccount? = null
)

@Serializable
data class ScanTransferResponse(
    val code: Long = 0,
    val message: String = "",
    val ttl: Long = 0,
    val data: ScanTransfer? = null
)
