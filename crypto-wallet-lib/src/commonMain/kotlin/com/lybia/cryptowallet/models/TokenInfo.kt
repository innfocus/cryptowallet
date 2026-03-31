package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin

/**
 * Holds metadata and balance for a specific token (e.g. Jetton on TON, ERC-20 on Ethereum).
 */
data class TokenInfo(
    val coin: ACTCoin,
    val contractAddress: String,
    val name: String?,
    val symbol: String?,
    val decimals: Int = 9,
    val balance: Double = 0.0,
    val imageUrl: String? = null
)
