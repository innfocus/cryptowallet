package com.lybia.cryptowallet.models.ton

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TonRpcRequest(
    val method: String,
    val params: kotlinx.serialization.json.JsonElement,
    val id: String = "1",
    val jsonrpc: String = "2.0"
)

@Serializable
data class TonGenericResponse(
    val ok: Boolean,
    val error: String? = null,
    val code: Int? = null
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
    val sourceFees: TonSourceFees,
    @SerialName("destination_fees")
    val destinationFees: List<TonSourceFees> = emptyList()
)

@Serializable
data class TonSourceFees(
    @SerialName("@type")
    val type: String? = null,
    @SerialName("in_fwd_fee")
    val inFwdFee: Long,
    @SerialName("storage_fee")
    val storageFee: Long,
    @SerialName("gas_fee")
    val gasFee: Long,
    @SerialName("fwd_fee")
    val fwdFee: Long
) {
    /** Total fee in nanoTON. */
    val total: Long get() = inFwdFee + storageFee + gasFee + fwdFee
}

/**
 * Detailed fee breakdown for TON transactions.
 * All values in TON (not nanoTON).
 */
data class TonFeeBreakdown(
    val inFwdFee: Double,
    val storageFee: Double,
    val gasFee: Double,
    val fwdFee: Double,
    val totalSourceFee: Double,
    val destinationFees: List<TonFeeBreakdownEntry> = emptyList(),
    val totalFee: Double
) {
    data class TonFeeBreakdownEntry(
        val inFwdFee: Double,
        val storageFee: Double,
        val gasFee: Double,
        val fwdFee: Double,
        val total: Double
    )
}

@Serializable
data class TonSendBocReturnHashResponse(
    val ok: Boolean,
    val result: TonSendBocHashResult? = null,
    val error: String? = null,
    val code: Int? = null
)

@Serializable
data class TonSendBocHashResult(
    @SerialName("@type")
    val type: String? = null,
    val hash: String   // base64 message hash
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
    @SerialName("exit_code")
    val exitCode: Int? = null,
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

// ─── NFT Models (Toncenter v3 REST API) ───────────────────────────────────────

@Serializable
data class TonV3NFTResponse(
    @SerialName("nft_items")
    val nftItems: List<TonNFTItem> = emptyList()
)

@Serializable
data class TonNFTItem(
    val address: String,
    @SerialName("collection_address")
    val collectionAddress: String? = null,
    @SerialName("owner_address")
    val ownerAddress: String? = null,
    @SerialName("collection_item_index")
    val index: String? = null,
    val content: TonNFTContent? = null
)

@Serializable
data class TonNFTContent(
    val name: String? = null,
    val description: String? = null,
    val image: String? = null,
    val attributes: List<TonNFTAttribute>? = null
)

@Serializable
data class TonNFTAttribute(
    @SerialName("trait_type")
    val traitType: String,
    val value: String
)

// ─── Parsed Jetton Transaction ───────────────────────────────────────────────

/**
 * Parsed Jetton transaction with human-readable fields.
 * Extracted from raw TonTransaction by decoding Jetton-specific opcodes.
 */
@Serializable
data class JettonTransactionParsed(
    /** "send", "receive", "burn", "unknown" */
    val type: String,
    /** Amount in nano-tokens (raw, before decimal conversion) */
    val amountNano: Long,
    /** Sender address (null for mints) */
    val sender: String?,
    /** Recipient address (null for burns) */
    val recipient: String?,
    /** Optional memo/comment */
    val memo: String? = null,
    /** Unix timestamp */
    val timestamp: Long,
    /** Transaction fee in nanoTON */
    val fee: String,
    /** Original transaction ID */
    val transactionId: TonTransactionId
)



