package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork

interface ITokenAndNFT {
    suspend fun getBalanceToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double
    suspend fun getTransactionHistoryToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any?
    suspend fun getNFT()
    suspend fun TransferToken(dataSigned: String, coinNetwork: CoinNetwork): String?
}