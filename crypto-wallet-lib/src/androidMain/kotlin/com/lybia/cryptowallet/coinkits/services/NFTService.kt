package com.lybia.cryptowallet.coinkits.services

import com.lybia.cryptowallet.coinkits.models.NFTItem

// ─── Callbacks ───────────────────────────────────────────────────────────────

interface NFTListHandle {
    fun completionHandler(nfts: Array<NFTItem>?, errStr: String)
}

interface NFTTransferHandle {
    fun completionHandler(txHash: String, success: Boolean, errStr: String)
}

// ─── Service interface ────────────────────────────────────────────────────────

/**
 * Implemented per-chain (e.g. TonService). CoinsManager delegates to these.
 */
interface NFTService {
    fun getNFTs(address: String, completionHandler: NFTListHandle)

    fun transferNFT(
        nftAddress: String,
        toAddress: String,
        memo: String? = null,
        completionHandler: NFTTransferHandle
    )
}
