package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork

/** Token operations (ERC-20, Jetton, Cardano native token). */
interface ITokenManager {
    suspend fun getTokenBalance(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double
    suspend fun getTokenTransactionHistory(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any?
    suspend fun transferToken(dataSigned: String, coinNetwork: CoinNetwork): String?
}
