package com.lybia.cryptowallet.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExplorerModel<T>(
    val status: String,
    val message: String,
    var result: T
)



@Serializable
data class Transaction (
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val transactionIndex: String,
    val from: String,
    val to: String,
    val value: String,
    val gas: String,
    val gasPrice: String,
    val isError: String,

    @SerialName("txreceipt_status")
    val txreceiptStatus: String,

    val input: String,
    val contractAddress: String,
    val cumulativeGasUsed: String,
    val gasUsed: String,
    val confirmations: String,

    @SerialName("methodId")
    val methodID: String,

    val functionName: String
)

@Serializable
data class TransactionToken(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val from: String,
    val contractAddress: String,
    val to: String,
    val value: String,
    val tokenName: String,
    val tokenSymbol: String,
    val tokenDecimal: String,
    val transactionIndex: String,
    val gas: String,
    val gasPrice: String,
    val gasUsed: String,
    val cumulativeGasUsed: String,
    val input: String,
    val confirmations: String
)
@Serializable
data class GasPrice(
    val SafeGasPrice: String,
    val ProposeGasPrice: String,
    val FastGasPrice: String
)

