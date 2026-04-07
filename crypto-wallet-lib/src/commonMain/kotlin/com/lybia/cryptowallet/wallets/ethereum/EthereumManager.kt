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
import com.ionspin.kotlin.bignum.integer.BigInteger


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

    /**
     * Paginated ETH transaction history.
     * @param page 1-based page number
     * @param offset Number of records per page (max 10000, recommended 20-100)
     * @return Pair(transactions, hasMore)
     */
    suspend fun getTransactionHistoryPaginated(
        address: String,
        coinNetwork: CoinNetwork,
        page: Int = 1,
        offset: Int = 20
    ): Pair<List<Transaction>, Boolean> {
        val response = ExplorerRpcService.INSTANCE.getTransactionHistory(
            coinNetwork, address, page, offset, sort = "desc"
        )
        val txs = if (response?.status == "1") {
            response.result.filter { it.value != "0" }
        } else {
            emptyList()
        }
        return Pair(txs, txs.size >= offset)
    }

    /**
     * Paginated ERC-20 token transaction history.
     * @param page 1-based page number
     * @param offset Number of records per page (max 10000, recommended 20-100)
     * @return Pair(transactions, hasMore)
     */
    suspend fun getTokenTransactionHistoryPaginated(
        address: String,
        contractAddress: String,
        coinNetwork: CoinNetwork,
        page: Int = 1,
        offset: Int = 20
    ): Pair<List<TransactionToken>, Boolean> {
        val response = ExplorerRpcService.INSTANCE.getTransactionHistoryToken(
            coinNetwork, address, contractAddress, page, offset, sort = "desc"
        )
        val txs = if (response?.status == "1") {
            response.result
        } else {
            emptyList()
        }
        return Pair(txs, txs.size >= offset)
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
     * Parse a hex string (0x-prefixed) to BigInteger.
     */
    private fun hexToBigInt(hex: String?): BigInteger {
        if (hex.isNullOrEmpty()) return BigInteger.ZERO
        val clean = hex.removePrefix("0x").removePrefix("0X")
        if (clean.isEmpty()) return BigInteger.ZERO
        return BigInteger.parseString(clean, 16)
    }

    /**
     * Convert ETH (Double) to wei (BigInteger) safely.
     * Avoids Long overflow: 1 ETH = 10^18 wei, Long.MAX ≈ 9.2 * 10^18.
     */
    fun ethToWei(ethAmount: Double): BigInteger {
        // Multiply in steps to preserve precision: amount * 10^9 * 10^9
        val gwei = (ethAmount * 1_000_000_000).toLong()
        return BigInteger.fromLong(gwei) * BigInteger.fromLong(1_000_000_000)
    }

    /**
     * Build, sign, and submit an ETH transfer transaction.
     * Automatically selects EIP-1559 (type 2) when the network supports it,
     * falling back to legacy (type 0) otherwise.
     *
     * @param toAddress Destination address
     * @param amountWei Amount in wei (BigInteger — no overflow risk)
     * @param coinNetwork Network configuration
     * @param gasLimit Optional gas limit override
     * @param maxPriorityFeeGwei Optional EIP-1559 tip override in gwei
     * @param maxFeeGwei Optional EIP-1559 max fee override in gwei
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendEthBigInt(
        toAddress: String,
        amountWei: BigInteger,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null,
        maxPriorityFeeGwei: Long? = null,
        maxFeeGwei: Long? = null
    ): TransferResponseModel {
        val privKey = privateKeyBytes
            ?: return TransferResponseModel(false, "No private key — mnemonic not provided", null)
        val fromAddr = walletAddress
            ?: return TransferResponseModel(false, "No wallet address", null)

        return try {
            val nonce = getNonce(coinNetwork)
            val chainId = getChainId(coinNetwork).toLong()

            // Estimate gas limit if not provided
            val estGasLimit = gasLimit ?: run {
                val hexValue = "0x" + amountWei.toString(16)
                val model = TransferTokenModel(
                    nonce = "", addressFrom = fromAddr,
                    contractAddress = toAddress, dataEncodeABI = "",
                    value = hexValue
                )
                getEstGas(model, coinNetwork).toLong()
            }

            // Try EIP-1559 first (check if network supports baseFee)
            val baseFeeHex = InfuraRpcService.shared.getBaseFee(coinNetwork)
            val signedTxHex: String

            if (baseFeeHex != null) {
                // ── EIP-1559 (type 2) ───────────────────────────────
                val baseFee = hexToBigInt(baseFeeHex)
                val GWEI = BigInteger.fromLong(1_000_000_000)

                val priorityFee = if (maxPriorityFeeGwei != null) {
                    BigInteger.fromLong(maxPriorityFeeGwei) * GWEI
                } else {
                    val rpcTip = InfuraRpcService.shared.getMaxPriorityFeePerGas(coinNetwork)
                    if (rpcTip != null) hexToBigInt(rpcTip)
                    else BigInteger.fromLong(1_500_000_000) // 1.5 gwei fallback
                }

                val maxFee = if (maxFeeGwei != null) {
                    BigInteger.fromLong(maxFeeGwei) * GWEI
                } else {
                    // maxFee = 2 * baseFee + priorityFee (standard formula)
                    baseFee * BigInteger.TWO + priorityFee
                }

                signedTxHex = EthTransactionSigner.signEip1559Transaction(
                    privateKey = privKey,
                    nonce = nonce,
                    maxPriorityFeePerGas = priorityFee,
                    maxFeePerGas = maxFee,
                    gasLimit = estGasLimit,
                    toAddress = toAddress,
                    valueWei = amountWei,
                    data = byteArrayOf(),
                    chainId = chainId
                )
            } else {
                // ── Legacy (type 0) fallback ────────────────────────
                val hexPrice = InfuraRpcService.shared.getAllGasPrice(coinNetwork, chainId.toInt())
                val gasPriceWei = if (hexPrice != null) hexToBigInt(hexPrice)
                else BigInteger.fromLong(20_000_000_000L) // 20 gwei fallback

                signedTxHex = EthTransactionSigner.signLegacyTransaction(
                    privateKey = privKey,
                    nonce = nonce,
                    gasPriceWei = gasPriceWei,
                    gasLimit = estGasLimit,
                    toAddress = toAddress,
                    valueWei = amountWei,
                    data = byteArrayOf(),
                    chainId = chainId
                )
            }

            val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, signedTxHex)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Backward-compatible sendEth with Long amount.
     * Delegates to [sendEthBigInt].
     */
    suspend fun sendEth(
        toAddress: String,
        amountWei: Long,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): TransferResponseModel {
        return sendEthBigInt(
            toAddress = toAddress,
            amountWei = BigInteger.fromLong(amountWei),
            coinNetwork = coinNetwork,
            gasLimit = gasLimit,
            maxFeeGwei = gasPriceGwei  // legacy gasPrice maps to maxFee
        )
    }

    /**
     * Build, sign, and submit an ERC-20 token transfer.
     * Uses BigInteger for token amount (safe for high-decimal tokens).
     *
     * @param contractAddress ERC-20 token contract address
     * @param toAddress Recipient address
     * @param amount Token amount in smallest unit (BigInteger)
     * @param coinNetwork Network configuration
     * @param gasLimit Optional gas limit override
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendErc20TokenBigInt(
        contractAddress: String,
        toAddress: String,
        amount: BigInteger,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null
    ): TransferResponseModel {
        val privKey = privateKeyBytes
            ?: return TransferResponseModel(false, "No private key — mnemonic not provided", null)
        val fromAddr = walletAddress
            ?: return TransferResponseModel(false, "No wallet address", null)

        return try {
            val nonce = getNonce(coinNetwork)
            val chainId = getChainId(coinNetwork).toLong()

            val transferData = EthTransactionSigner.encodeErc20TransferBigInt(toAddress, amount)

            val estGasLimit = gasLimit ?: run {
                val model = TransferTokenModel(
                    nonce = "", addressFrom = fromAddr,
                    contractAddress = contractAddress,
                    dataEncodeABI = "0x" + transferData.toHexString(),
                    value = "0x0"
                )
                getEstGas(model, coinNetwork).toLong()
            }

            val baseFeeHex = InfuraRpcService.shared.getBaseFee(coinNetwork)
            val signedTxHex: String

            if (baseFeeHex != null) {
                val baseFee = hexToBigInt(baseFeeHex)
                val GWEI = BigInteger.fromLong(1_000_000_000)
                val rpcTip = InfuraRpcService.shared.getMaxPriorityFeePerGas(coinNetwork)
                val priorityFee = if (rpcTip != null) hexToBigInt(rpcTip)
                else BigInteger.fromLong(1_500_000_000)
                val maxFee = baseFee * BigInteger.TWO + priorityFee

                signedTxHex = EthTransactionSigner.signEip1559Transaction(
                    privateKey = privKey, nonce = nonce,
                    maxPriorityFeePerGas = priorityFee, maxFeePerGas = maxFee,
                    gasLimit = estGasLimit, toAddress = contractAddress,
                    valueWei = BigInteger.ZERO, data = transferData, chainId = chainId
                )
            } else {
                val hexPrice = InfuraRpcService.shared.getAllGasPrice(coinNetwork, chainId.toInt())
                val gasPriceWei = if (hexPrice != null) hexToBigInt(hexPrice)
                else BigInteger.fromLong(20_000_000_000L)

                signedTxHex = EthTransactionSigner.signLegacyTransaction(
                    privateKey = privKey, nonce = nonce,
                    gasPriceWei = gasPriceWei, gasLimit = estGasLimit,
                    toAddress = contractAddress, valueWei = BigInteger.ZERO,
                    data = transferData, chainId = chainId
                )
            }

            val txHash = InfuraRpcService.shared.sendSignedTransaction(coinNetwork, signedTxHex)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Backward-compatible sendErc20Token with Long amount.
     */
    suspend fun sendErc20Token(
        contractAddress: String,
        toAddress: String,
        amount: Long,
        coinNetwork: CoinNetwork,
        gasLimit: Long? = null,
        gasPriceGwei: Long? = null
    ): TransferResponseModel {
        return sendErc20TokenBigInt(
            contractAddress, toAddress,
            BigInteger.fromLong(amount), coinNetwork, gasLimit
        )
    }

    /**
     * Estimate the total fee for a transaction in ETH.
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

    override suspend fun getBalanceToken(address: String, contractAddress: String, coinNetwork: CoinNetwork, decimals: Int): Double {
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
