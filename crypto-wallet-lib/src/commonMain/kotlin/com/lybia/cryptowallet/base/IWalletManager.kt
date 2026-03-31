package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.TransferResponseModel

/** Basic wallet operations — every chain implements this. */
interface IWalletManager {
    fun getAddress(): String
    suspend fun getBalance(address: String? = null, coinNetwork: CoinNetwork? = null): Double
    suspend fun getTransactionHistory(address: String? = null, coinNetwork: CoinNetwork? = null): Any?
    suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel
    suspend fun getChainId(coinNetwork: CoinNetwork): String
}
