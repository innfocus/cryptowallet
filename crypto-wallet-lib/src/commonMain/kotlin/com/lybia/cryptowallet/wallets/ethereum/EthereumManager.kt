package com.lybia.cryptowallet.wallets.ethereum

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.base.ITransactionFee
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.ExplorerModel
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.Transaction
import com.lybia.cryptowallet.models.TransactionToken
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.TransferTokenModel
import com.lybia.cryptowallet.services.ExplorerRpcService
import com.lybia.cryptowallet.services.InfuraRpcService
import com.lybia.cryptowallet.services.OwlRacleService
import com.lybia.cryptowallet.utils.Utils


class EthereumManager : BaseCoinManager(), ITransactionFee, ITokenAndNFT {
    private val decimal = 18

    companion object {
        val shared: EthereumManager = EthereumManager()

    }


    override fun getAddress(): String {
        return "Arbitrum Address"
    }

    override suspend fun getBalance(address: String, coinNetwork: CoinNetwork): Double {
        val balance = InfuraRpcService.shared.getBalance(coinNetwork, address)
        return Utils.convertHexStringToDouble(balance, 18)
    }

    override suspend fun getTransactionHistory(
        address: String, coinNetwork: CoinNetwork
    ): ExplorerModel<List<Transaction>>? {
        val response = ExplorerRpcService.INSTANCE.getTransactionHistory(coinNetwork, address)
        if(response?.status =="1"){
            response.result = response.result.filter { it.value != "0" }
        }
        return response
    }

    override suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel {
        try {
            val result = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, dataSigned)

            return TransferResponseModel(
                success = true,
                error = null,
                txHash = result
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return TransferResponseModel(
                success = false,
                error = e.message,
                txHash = null
            )
        }

    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        val result = InfuraRpcService.shared.getChainId(coinNetwork)

        return Utils.convertHexStringToDouble(result, 0).toInt().toString()
    }

    override suspend fun getEstGas(model: TransferTokenModel, coinNetwork: CoinNetwork): Double {
        val hexValue = Utils.convertDoubleToHexString(model.value.toDoubleOrNull() ?: 0.0, decimal)
        model.value = hexValue;
        val gasLimit = InfuraRpcService.shared.estimateLimitGas(coinNetwork,model)
        return Utils.convertHexStringToDouble(gasLimit,1)
    }

    override suspend fun getAllGasPrice(coinNetwork: CoinNetwork): GasPrice? {
        when(coinNetwork.name){
            NetworkName.ARBITRUM->{
                val price = OwlRacleService.shared.getAllGasPrice(coinNetwork)
                return price
            }
            else->{
                val price = ExplorerRpcService.INSTANCE.getAllGasPrice(coinNetwork)
                return price
            }
        }
    }


    override suspend fun getBalanceToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double {
        val balance = ExplorerRpcService.INSTANCE.getBalanceToken(coinNetwork, address, contractAddress)
        return balance?.toDoubleOrNull() ?: 0.0
    }

    override suspend fun getTransactionHistoryToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): ExplorerModel<List<TransactionToken>>? {
        return ExplorerRpcService.INSTANCE.getTransactionHistoryToken(coinNetwork, address, contractAddress)
    }

    override suspend fun getNFT() {
        TODO("Not yet implemented")
    }

    override suspend fun TransferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, dataSigned)
        return txHash
    }

}