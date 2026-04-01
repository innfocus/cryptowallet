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
import com.lybia.cryptowallet.utils.toHexString


class EthereumManager(
    private val mnemonic: String? = null
) : BaseCoinManager(), ITokenManager, INFTManager, IFeeEstimator, ITransactionFee, ITokenAndNFT {
    private val decimal = 18

    companion object {
        val shared: EthereumManager = EthereumManager()
    }

    // ── Key derivation (m/44'/60'/0'/0/0) ───────────────────────────

    private val hdWallet by lazy { mnemonic?.let { com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet(it) } }
    private val ethNetwork by lazy { com.lybia.cryptowallet.enums.ACTNetwork(ACTCoin.Ethereum, false) }

    private val privateKeyBytes: ByteArray? by lazy {
        hdWallet?.generateExternalPrivateKey(0, ethNetwork)?.raw
    }

    private val walletAddress: String? by lazy {
        hdWallet?.let {
            val pubKey = it.generateExternalPublicKey(0, ethNetwork)
            com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress(pubKey).rawAddressString()
        }
    }

    override fun getAddress(): String {
        return walletAddress ?: "Ethereum Address"
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

    // ── Direct send (build + sign + submit) ─────────────────────────

    /**
     * Get the current nonce for the wallet address.
     */
    suspend fun getNonce(coinNetwork: CoinNetwork): Long {
        val addr = walletAddress ?: throw IllegalStateException("No wallet address — mnemonic not provided")
        val hexNonce = InfuraRpcService.shared.getTransactionCount(coinNetwork, addr)
            ?: throw Exception("Failed to get nonce")
        return Utils.convertHexStringToDouble(hexNonce, 0).toLong()
    }

    /**
     * Build, sign, and submit an ETH transfer transaction.
     *
     * @param toAddress Destination address
     * @param amountWei Amount in wei
     * @param coinNetwork Network configuration
     * @param gasLimit Optional gas limit override (default: estimated)
     * @param gasPriceGwei Optional gas price override in gwei (default: from network)
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendEth(
        toAddress: String,
        amountWei: Long,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): TransferResponseModel {
        val privKey = privateKeyBytes
            ?: return TransferResponseModel(false, "No private key — mnemonic not provided", null)
        val fromAddr = walletAddress
            ?: return TransferResponseModel(false, "No wallet address", null)

        return try {
            val nonce = getNonce(coinNetwork)
            val chainId = getChainId(coinNetwork).toLong()

            // Estimate gas if not provided
            val estimatedGasLimit = gasLimit ?: run {
                val model = TransferTokenModel(
                    nonce = "", addressFrom = fromAddr,
                    contractAddress = toAddress, dataEncodeABI = "",
                    value = "0x" + amountWei.toString(16)
                )
                getEstGas(model, coinNetwork).toLong()
            }

            // Get gas price if not provided
            val gasPriceWei = if (gasPriceGwei != null) {
                gasPriceGwei * 1_000_000_000L
            } else {
                val hexPrice = InfuraRpcService.shared.getAllGasPrice(coinNetwork, chainId.toInt())
                if (hexPrice != null) Utils.convertHexStringToDouble(hexPrice, 0).toLong()
                else 20_000_000_000L // fallback 20 gwei
            }

            val signedTxHex = EthTransactionSigner.signTransaction(
                privateKey = privKey,
                nonce = nonce,
                gasPriceWei = gasPriceWei,
                gasLimit = estimatedGasLimit,
                toAddress = toAddress,
                valueWei = amountWei,
                data = byteArrayOf(),
                chainId = chainId
            )

            val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, signedTxHex)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Build, sign, and submit an ERC-20 token transfer transaction.
     *
     * @param contractAddress ERC-20 token contract address
     * @param toAddress Recipient address
     * @param amount Token amount (raw, in smallest unit)
     * @param coinNetwork Network configuration
     * @param gasLimit Optional gas limit override
     * @param gasPriceGwei Optional gas price override in gwei
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendErc20Token(
        contractAddress: String,
        toAddress: String,
        amount: Long,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): TransferResponseModel {
        val privKey = privateKeyBytes
            ?: return TransferResponseModel(false, "No private key — mnemonic not provided", null)
        val fromAddr = walletAddress
            ?: return TransferResponseModel(false, "No wallet address", null)

        return try {
            val nonce = getNonce(coinNetwork)
            val chainId = getChainId(coinNetwork).toLong()

            // Encode ERC-20 transfer(address,uint256) call data
            val transferData = EthTransactionSigner.encodeErc20Transfer(toAddress, amount)

            // Estimate gas if not provided
            val estimatedGasLimit = gasLimit ?: run {
                val model = TransferTokenModel(
                    nonce = "", addressFrom = fromAddr,
                    contractAddress = contractAddress,
                    dataEncodeABI = "0x" + transferData.toHexString(),
                    value = "0x0"
                )
                getEstGas(model, coinNetwork).toLong()
            }

            // Get gas price if not provided
            val gasPriceWei = if (gasPriceGwei != null) {
                gasPriceGwei * 1_000_000_000L
            } else {
                val hexPrice = InfuraRpcService.shared.getAllGasPrice(coinNetwork, chainId.toInt())
                if (hexPrice != null) Utils.convertHexStringToDouble(hexPrice, 0).toLong()
                else 20_000_000_000L
            }

            val signedTxHex = EthTransactionSigner.signTransaction(
                privateKey = privKey,
                nonce = nonce,
                gasPriceWei = gasPriceWei,
                gasLimit = estimatedGasLimit,
                toAddress = contractAddress,
                valueWei = 0L,
                data = transferData,
                chainId = chainId
            )

            val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, signedTxHex)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Estimate the total fee for a transaction in ETH.
     * fee = gasLimit * gasPrice
     */
    suspend fun estimateTransactionFee(
        toAddress: String,
        amountWei: Long,
        coinNetwork: CoinNetwork
    ): FeeEstimate {
        val fromAddr = walletAddress ?: getAddress()
        val model = TransferTokenModel(
            nonce = "", addressFrom = fromAddr,
            contractAddress = toAddress, dataEncodeABI = "",
            value = "0x" + amountWei.toString(16)
        )
        val gasLimit = getEstGas(model, coinNetwork)
        val gasPrice = getAllGasPrice(coinNetwork)
        val gasPriceValue = gasPrice?.ProposeGasPrice?.toLongOrNull() ?: 0L
        val fee = gasLimit * gasPriceValue.toDouble() / 1_000_000_000.0
        return FeeEstimate(
            fee = fee,
            gasLimit = gasLimit.toLong(),
            gasPrice = gasPriceValue,
            unit = "gwei"
        )
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
