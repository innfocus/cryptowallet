package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.TransferResponseModel

abstract class BaseCoinManager : IWalletManager {
    abstract override fun getAddress(): String
    abstract override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double
    abstract override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any?
    abstract override suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel

    abstract override suspend fun getChainId(coinNetwork: CoinNetwork): String
}