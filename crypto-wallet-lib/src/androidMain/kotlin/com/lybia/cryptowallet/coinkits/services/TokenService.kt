package com.lybia.cryptowallet.coinkits.services

import com.lybia.cryptowallet.coinkits.TransationData
import com.lybia.cryptowallet.coinkits.models.TokenInfo

// ─── Callbacks ───────────────────────────────────────────────────────────────

interface TokenBalanceHandle {
    fun completionHandler(tokenInfo: TokenInfo?, success: Boolean, errStr: String)
}

interface TokenTransactionsHandle {
    fun completionHandler(transactions: Array<TransationData>?, errStr: String)
}

interface SendTokenHandle {
    fun completionHandler(txHash: String, success: Boolean, errStr: String)
}

// ─── Service interface ────────────────────────────────────────────────────────

/**
 * Implemented per-chain (e.g. TonService). CoinsManager delegates to these.
 */
interface TokenService {
    fun getTokenBalance(
        address: String,
        contractAddress: String,
        completionHandler: TokenBalanceHandle
    )

    fun getTokenTransactions(
        address: String,
        contractAddress: String,
        completionHandler: TokenTransactionsHandle
    )

    /**
     * @param decimals token decimals (e.g. 9 for most Jettons, 6 for USDT)
     */
    fun sendToken(
        toAddress: String,
        contractAddress: String,
        amount: Double,
        decimals: Int,
        memo: String? = null,
        completionHandler: SendTokenHandle
    )
}
