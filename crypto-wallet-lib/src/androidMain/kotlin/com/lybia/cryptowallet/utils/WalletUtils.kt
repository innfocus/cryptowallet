package com.lybia.cryptowallet.utils


import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.NetworkName
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Bip44WalletUtils
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.EthGetTransactionCount
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.tx.gas.DefaultGasProvider

class WalletsUtils(private val coinNetwork: CoinNetwork) {

    private val web3j: Web3j = Web3j.build(HttpService(coinNetwork.getInfuraRpcUrl()))

    companion object;


    private fun getBip39Credentials(mnemonic: String?): Credentials {
        return WalletUtils.loadBip39Credentials(null, mnemonic)
    }

    fun getBip44Credentials(mnemonic: String?): Credentials {
        return Bip44WalletUtils.loadBip44Credentials(null, mnemonic)
    }


    fun getCredentialsFromPrivateKey(privatekey: String?): Credentials {
        //String privateKeyHexValue = Numeric.toHexString(privatekey.getBytes());
        return Credentials.create(privatekey)
    }

    fun signTransaction(mnemonic: String?, amount: BigDecimal?, toAddress: String?): String? {
        val credentials = getBip44Credentials(mnemonic)
        val ethGetTransactionCount: EthGetTransactionCount
        try {
            ethGetTransactionCount =
                web3j.ethGetTransactionCount(credentials.address, DefaultBlockParameterName.LATEST)
                    .send()
            val nonce = ethGetTransactionCount.transactionCount
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            val transaction = Transaction.createFunctionCallTransaction(
                credentials.address,
                nonce,
                gasPrice,
                BigInteger.ZERO,
                toAddress,
                Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger(),
                ""
            )
            val gasLimit: BigInteger = web3j.ethEstimateGas(transaction).send().amountUsed
            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                toAddress,
                Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger()
            )
            val chainId = web3j.ethChainId().send().chainId
            val signedMessage =
                TransactionEncoder.signMessage(rawTransaction, chainId.toLong(), credentials)
            return Numeric.toHexString(signedMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun signTokenTransaction(
        mnemonic: String?,
        amount: BigDecimal,
        toAddress: String?,
        contract: String?,
        decimals: Int
    ): String? {
        val credentials = getBip44Credentials(mnemonic)
        try {
            val ethGetTransactionCount =
                web3j.ethGetTransactionCount(credentials.address, DefaultBlockParameterName.LATEST)
                    .send()
            val nonce = ethGetTransactionCount.transactionCount

            val function = encodeTransferDataFunction(
                toAddress,
                BigInteger.valueOf(amount.multiply(BigDecimal.TEN.pow(decimals)).toLong())
            )

            val encodeData = FunctionEncoder.encode(function)

            val gasPrice = web3j.ethGasPrice().send().gasPrice
            val transaction = Transaction.createFunctionCallTransaction(
                credentials.address,
                nonce,
                gasPrice,
                DefaultGasProvider.GAS_LIMIT,
                contract,
                BigInteger.valueOf(amount.multiply(BigDecimal.TEN.pow(decimals)).toLong()),
                encodeData
            )

            var gasLimit : BigInteger = BigInteger.ZERO
            if(coinNetwork.name == NetworkName.ETHEREUM){
                gasLimit = BigInteger.valueOf(120000)
            }else{
                val estimatedGasLimit: BigInteger = web3j.ethEstimateGas(transaction).send().amountUsed
                gasLimit = estimatedGasLimit.add(BigInteger.valueOf(10000)) // Buffer for contract
            }


            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contract,
                encodeData
            )

            val chainId = web3j.ethChainId().send().chainId

            val signedMessage =
                TransactionEncoder.signMessage(rawTransaction, chainId.toLong(), credentials)

            return Numeric.toHexString(signedMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun encodeTransferDataFunction(to: String?, value: BigInteger): Function {
        return Function(
            "transfer",
            listOf(Address(to), Uint256(value)),
            listOf(object : TypeReference<Bool>() {})
        )
    }
}
