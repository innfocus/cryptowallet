package com.lybia.cryptowallet.base

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.TransferResponseModel

/** Staking operations (future — Cardano, TON). */
interface IStakingManager {
    suspend fun stake(amount: Long, poolAddress: String, coinNetwork: CoinNetwork): TransferResponseModel
    suspend fun unstake(amount: Long, coinNetwork: CoinNetwork): TransferResponseModel
    suspend fun getStakingRewards(address: String, coinNetwork: CoinNetwork): Double
    suspend fun getStakingBalance(address: String, poolAddress: String, coinNetwork: CoinNetwork): Double
}
