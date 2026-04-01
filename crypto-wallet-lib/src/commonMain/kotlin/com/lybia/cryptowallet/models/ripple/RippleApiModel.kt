package com.lybia.cryptowallet.models.ripple

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── JSON-RPC Request/Response ──────────────────────────────────────

@Serializable
data class RippleRpcRequest(
    val method: String,
    val params: List<RippleRpcParam>
)

@Serializable
data class RippleRpcParam(
    val account: String? = null,
    @SerialName("tx_blob") val txBlob: String? = null,
    @SerialName("ledger_index") val ledgerIndex: String? = null,
    val limit: Int? = null,
    val forward: Boolean? = null,
    val marker: RippleMarker? = null
)

// ─── account_info response ──────────────────────────────────────────

@Serializable
data class RippleAccountInfoResponse(
    val result: RippleAccountInfoResult
)

@Serializable
data class RippleAccountInfoResult(
    val status: String? = null,
    @SerialName("account_data") val accountData: RippleAccountData? = null,
    @SerialName("ledger_current_index") val ledgerCurrentIndex: Long? = null,
    val error: String? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class RippleAccountData(
    @SerialName("Account") val account: String,
    @SerialName("Balance") val balance: String,
    @SerialName("Sequence") val sequence: Long,
    @SerialName("Flags") val flags: Long = 0
)

// ─── account_tx response ────────────────────────────────────────────

@Serializable
data class RippleAccountTxResponse(
    val result: RippleAccountTxResult
)

@Serializable
data class RippleAccountTxResult(
    val status: String? = null,
    val transactions: List<RippleTransactionEntry>? = null,
    val marker: RippleMarker? = null,
    val error: String? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class RippleMarker(
    val ledger: Long? = null,
    val seq: Long? = null
)

@Serializable
data class RippleTransactionEntry(
    val tx: RippleTxData? = null,
    val meta: RippleTxMeta? = null,
    val validated: Boolean = false
)

@Serializable
data class RippleTxData(
    @SerialName("Account") val account: String = "",
    @SerialName("Destination") val destination: String? = null,
    @SerialName("Amount") val amount: String = "0",
    @SerialName("Fee") val fee: String = "0",
    val hash: String = "",
    val date: Long = 0,
    @SerialName("DestinationTag") val destinationTag: Long? = null,
    @SerialName("Memos") val memos: List<RippleMemoWrapper>? = null,
    @SerialName("TransactionType") val transactionType: String = ""
)

@Serializable
data class RippleTxMeta(
    @SerialName("TransactionResult") val transactionResult: String = ""
)

@Serializable
data class RippleMemoWrapper(
    @SerialName("Memo") val memo: RippleMemo
)

@Serializable
data class RippleMemo(
    @SerialName("MemoData") val memoData: String? = null,
    @SerialName("MemoType") val memoType: String? = null
)

// ─── submit response ────────────────────────────────────────────────

@Serializable
data class RippleSubmitResponse(
    val result: RippleSubmitResult
)

@Serializable
data class RippleSubmitResult(
    val status: String? = null,
    @SerialName("engine_result") val engineResult: String? = null,
    @SerialName("engine_result_message") val engineResultMessage: String? = null,
    @SerialName("tx_blob") val txBlob: String? = null,
    val tx_json: RippleSubmitTxJson? = null,
    val error: String? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class RippleSubmitTxJson(
    val hash: String? = null
)
