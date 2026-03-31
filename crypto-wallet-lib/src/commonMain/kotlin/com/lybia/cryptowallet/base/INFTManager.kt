package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TransferResponseModel

/** NFT operations (TEP-62, ERC-721). */
interface INFTManager {
    suspend fun getNFTs(address: String, coinNetwork: CoinNetwork): List<NFTItem>?
    suspend fun transferNFT(
        nftAddress: String,
        toAddress: String,
        memo: String? = null,
        coinNetwork: CoinNetwork
    ): TransferResponseModel
}
