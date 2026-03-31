package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin

/**
 * KMP-compatible transaction data model.
 * Replaces androidMain version: removed java.io.Serializable, replaced java.util.Date with Long (epoch millis).
 */
data class TransationData(
    var amount: Float = 0.0f,
    var fee: Float = 0.0f,
    var iD: String = "",
    var fromAddress: String = "",
    var toAddress: String = "",
    var dateMillis: Long = 0L,
    var coin: ACTCoin = ACTCoin.Bitcoin,
    var isSend: Boolean = false,
    var memoNetwork: MemoData? = null
)
