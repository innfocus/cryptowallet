package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.FeeEstimate
import com.lybia.cryptowallet.models.FeeEstimateParams
import com.lybia.cryptowallet.models.GasPrice

/** Fee estimation — chain-specific. */
interface IFeeEstimator {
    suspend fun estimateFee(params: FeeEstimateParams, coinNetwork: CoinNetwork): FeeEstimate
    suspend fun getGasPrice(coinNetwork: CoinNetwork): GasPrice?
}
