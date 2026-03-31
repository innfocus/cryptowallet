package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin

/**
 * Represents a single NFT item owned by a wallet address.
 */
data class NFTItem(
    val coin: ACTCoin,
    val address: String,
    val collectionAddress: String?,
    val index: Long = 0L,
    val name: String?,
    val description: String?,
    val imageUrl: String?,
    val attributes: Map<String, String>? = null
)
