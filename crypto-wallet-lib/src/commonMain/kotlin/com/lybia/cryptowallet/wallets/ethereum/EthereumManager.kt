package com.lybia.cryptowallet.wallets.ethereum

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.IFeeEstimator
import com.lybia.cryptowallet.base.INFTManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.base.ITokenManager
import com.lybia.cryptowallet.base.ITransactionFee
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.ExplorerModel
import com.lybia.cryptowallet.models.FeeEstimate
import com.lybia.cryptowallet.models.FeeEstimateParams
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.Transaction
import com.lybia.cryptowallet.models.TransactionToken
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.TransferTokenModel
import com.lybia.cryptowallet.services.ExplorerRpcService
import com.lybia.cryptowallet.services.InfuraRpcService
import com.lybia.cryptowallet.services.OwlRacleService
import com.lybia.cryptowallet.utils.Utils


class EthereumManager : BaseCoinManager(), ITokenManager, INFTManager, IFeeEstimator, ITransactionFee, ITokenAndNFT {
    private val decimal = 18

    companion object {
        val shared: EthereumManager = EthereumManager()
    }

    override fun getAddress(): String {
        return "Arbitrum Address"
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        require(!address.isNullOrEmpty()) { "Address is null" }
        require(coinNetwork != null) { "CoinNetwork is null" }
        val balance = InfuraRpcService.shared.getBalance(coinNetwork, address)
        return Utils.convertHexStringToDouble(balance, 18)
    }

    override suspend fun getTransactionHistory(
        address: String?, coinNetwork: CoinNetwork?
    ): ExplorerModel<List<Transaction>>? {
        require(!address.isNullOrEmpty()) { "Address is null" }
        require(coinNetwork != null) { "CoinNetwork is null" }
        val response = ExplorerRpcService.INSTANCE.getTransactionHistory(coinNetwork, address)
        if (response?.status == "1") {
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

    // ── ITransactionFee (existing) ──────────────────────────────────

    override suspend fun getEstGas(model: TransferTokenModel, coinNetwork: CoinNetwork): Double {
        val hexValue = Utils.convertDoubleToHexString(model.value.toDoubleOrNull() ?: 0.0, decimal)
        model.value = hexValue
        val gasLimit = InfuraRpcService.shared.estimateLimitGas(coinNetwork, model)
        return Utils.convertHexStringToDouble(gasLimit, 1)
    }

    override suspend fun getAllGasPrice(coinNetwork: CoinNetwork): GasPrice? {
        return when (coinNetwork.name) {
            NetworkName.ARBITRUM -> OwlRacleService.shared.getAllGasPrice(coinNetwork)
            else -> ExplorerRpcService.INSTANCE.getAllGasPrice(coinNetwork)
        }
    }

    // ── ITokenAndNFT (existing) ─────────────────────────────────────

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

    // ── ITokenManager ───────────────────────────────────────────────

    override suspend fun getTokenBalance(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double {
        return getBalanceToken(address, contractAddress, coinNetwork)
    }

    override suspend fun getTokenTransactionHistory(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any? {
        return getTransactionHistoryToken(address, contractAddress, coinNetwork)
    }

    override suspend fun transferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        return TransferToken(dataSigned, coinNetwork)
    }

    // ── INFTManager ─────────────────────────────────────────────────

    override suspend fun getNFTs(address: String, coinNetwork: CoinNetwork): List<NFTItem>? {
        return try {
            ExplorerRpcService.INSTANCE.getNFTTransactions(coinNetwork, address)
                ?.result
                ?.distinctBy { it.contractAddress + it.tokenID }
                ?.map { nft ->
                    NFTItem(
                        coin = ACTCoin.Ethereum,
                        address = nft.contractAddress,
                        collectionAddress = nft.contractAddress,
                        index = nft.tokenID.toLongOrNull() ?: 0L,
                        name = nft.tokenName,
                        description = null,
                        imageUrl = null
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun transferNFT(
        nftAddress: String,
        toAddress: String,
        memo: String?,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        return try {
            val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, nftAddress)
            TransferResponseModel(
                success = true,
                error = null,
                txHash = txHash
            )
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResponseModel(
                success = false,
                error = e.message,
                txHash = null
            )
        }
    }

    // ── IFeeEstimator ───────────────────────────────────────────────

    override suspend fun estimateFee(params: FeeEstimateParams, coinNetwork: CoinNetwork): FeeEstimate {
        val model = TransferTokenModel(
            nonce = "",
            addressFrom = params.fromAddress,
            contractAddress = params.toAddress,
            dataEncodeABI = params.data ?: "",
            value = params.amount.toString()
        )
        val gasLimit = getEstGas(model, coinNetwork)
        val gasPrice = getAllGasPrice(coinNetwork)
        val gasPriceValue = gasPrice?.ProposeGasPrice?.toLongOrNull() ?: 0L
        val fee = gasLimit * gasPriceValue.toDouble() / 1_000_000_000.0 // gwei to ETH
        return FeeEstimate(
            fee = fee,
            gasLimit = gasLimit.toLong(),
            gasPrice = gasPriceValue,
            unit = "gwei"
        )
    }

    override suspend fun getGasPrice(coinNetwork: CoinNetwork): GasPrice? {
        return getAllGasPrice(coinNetwork)
    }
}
