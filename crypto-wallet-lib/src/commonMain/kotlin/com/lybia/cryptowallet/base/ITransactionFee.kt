package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.TransferTokenModel

interface ITransactionFee {
    //for eth,arb and token on chain etherium
    suspend fun getEstGas(model: TransferTokenModel, coinNetwork: CoinNetwork): Double
    suspend fun getAllGasPrice(coinNetwork: CoinNetwork): GasPrice?
}