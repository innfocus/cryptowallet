package com.lybia.cryptowallet.models.ton

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TonRpcRequest<T>(
    val method: String,
    val params: T,
    val id: String = "1",
    val jsonrpc: String = "2.0"
)

@Serializable
data class TonAddressInformationResponse(
    val ok: Boolean,
    val result: TonAddressInformation? = null,
    val error: String? = null,
    val code: Int? = null
)

@Serializable
data class TonAddressInformation(
    val balance: String,
    val state: String,
    @SerialName("wallet_type")
    val walletType: String? = null,
    @SerialName("last_transaction_id")
    val lastTransactionId: TonTransactionId? = null
)

@Serializable
data class TonTransactionId(
    val lt: String,
    val hash: String
)

@Serializable
data class TonTransactionsResponse(
    val ok: Boolean,
    val result: List<TonTransaction>? = null,
    val error: String? = null
)

@Serializable
data class TonTransaction(
    val utime: Long,
    val data: String? = null,
    @SerialName("transaction_id")
    val transactionId: TonTransactionId,
    val fee: String,
    val in_msg: TonMessage? = null,
    val out_msgs: List<TonMessage>? = null
)

@Serializable
data class TonMessage(
    val source: String,
    val destination: String,
    val value: String,
    val message: String? = null,
    val hash: String? = null
)

@Serializable
data class TonFeeResponse(
    val ok: Boolean,
    val result: TonFeeResult? = null,
    val error: String? = null
)

@Serializable
data class TonFeeResult(
    @SerialName("@type")
    val type: String,
    @SerialName("source_fees")
    val sourceFees: TonSourceFees
)

@Serializable
data class TonSourceFees(
    @SerialName("@type")
    val type: String,
    @SerialName("in_fwd_fee")
    val inFwdFee: Long,
    @SerialName("storage_fee")
    val storageFee: Long,
    @SerialName("gas_fee")
    val gasFee: Long,
    @SerialName("fwd_fee")
    val fwdFee: Long
)

@Serializable
data class TonRunGetMethodResponse(
    val ok: Boolean,
    val result: TonRunGetMethodResult? = null
)

@Serializable
data class TonRunGetMethodResult(
    @SerialName("@type")
    val type: String,
    val stack: List<List<kotlinx.serialization.json.JsonElement>>
)

@Serializable
data class JettonMetadata(
    val name: String? = null,
    val symbol: String? = null,
    val decimals: Int = 9,
    val description: String? = null,
    val image: String? = null
)

@Serializable
data class TonStakingBalance(
    val poolAddress: String,
    val amount: Double,
    val pendingDeposit: Double = 0.0,
    val pendingWithdrawal: Double = 0.0,
    val liquidBalance: Double = 0.0,
    val rewards: Double = 0.0
)



