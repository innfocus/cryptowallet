package com.lybia.cryptowallet.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Blockfrost-style API response model for a Cardano UTXO.
 * Uses "CardanoApiUtxo" to avoid conflict with CardanoUtxo in CardanoNativeToken.kt.
 */
@Serializable
data class CardanoApiUtxo(
    @SerialName("tx_hash") val txHash: String,
    @SerialName("tx_index") val txIndex: Int,
    val amount: List<CardanoAmount>
)

@Serializable
data class CardanoAmount(
    val unit: String,
    val quantity: String
)

@Serializable
data class CardanoBlockInfo(
    val epoch: Int,
    val slot: Long,
    val hash: String,
    val height: Long
)

@Serializable
data class CardanoProtocolParams(
    @SerialName("min_fee_a") val minFeeA: Int,
    @SerialName("min_fee_b") val minFeeB: Int,
    @SerialName("coins_per_utxo_size") val coinsPerUtxoSize: String
)

@Serializable
data class CardanoTransactionInfo(
    @SerialName("tx_hash") val txHash: String,
    @SerialName("block_height") val blockHeight: Long,
    @SerialName("block_time") val blockTime: Long,
    val fees: String,
    val inputs: List<CardanoTxInOut>,
    val outputs: List<CardanoTxInOut>
)

@Serializable
data class CardanoTxInOut(
    val address: String,
    val amount: List<CardanoAmount>
)

@Serializable
data class CardanoAssetInfo(
    val asset: String,
    @SerialName("policy_id") val policyId: String,
    @SerialName("asset_name") val assetName: String? = null,
    val metadata: CardanoAssetMetadata? = null
)

@Serializable
data class CardanoAssetMetadata(
    val name: String? = null,
    val description: String? = null,
    val ticker: String? = null,
    val decimals: Int? = null
)
