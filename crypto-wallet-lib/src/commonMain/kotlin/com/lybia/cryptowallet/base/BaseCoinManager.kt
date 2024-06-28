package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.TransferResponseModel

abstract class BaseCoinManager{
    abstract fun getAddress(): String
    abstract suspend fun getBalance(address: String? = null, coinNetwork: CoinNetwork? = null): Double
    abstract suspend fun getTransactionHistory(address: String? = null, coinNetwork: CoinNetwork?= null): Any?
    abstract suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel

    abstract suspend fun getChainId(coinNetwork: CoinNetwork): String
}