package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.TransferResponseModel

/** Bridge operations (future). */
interface IBridgeManager {
    suspend fun bridgeAsset(fromChain: NetworkName, toChain: NetworkName, amount: Long): TransferResponseModel
    suspend fun getBridgeStatus(txHash: String): String
}
