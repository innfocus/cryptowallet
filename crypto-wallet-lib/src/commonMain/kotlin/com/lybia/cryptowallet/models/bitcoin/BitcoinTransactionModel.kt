package com.lybia.cryptowallet.models.bitcoin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BitcoinTransactionModel(
    val tx: Tx,
    val tosign: List<String>,
    val signatures: List<String>? = null,
    val pubkeys: List<String>? = null
) {

    @Serializable
    data class Tx(
        @SerialName("block_height")
        val blockHeight: Long,

        @SerialName("block_index")
        val blockIndex: Long,

        val hash: String,
        val addresses: List<String>,
        val total: Long,
        val fees: Long,
        val size: Long,
        val vsize: Long,
        val preference: String,

        @SerialName("relayed_by")
        val relayedBy: String,

        val received: String,
        val ver: Long,

        @SerialName("double_spend")
        val doubleSpend: Boolean,

        @SerialName("vin_sz")
        val vinSz: Long,

        @SerialName("vout_sz")
        val voutSz: Long,

        val confirmations: Long,
        val inputs: List<Input>,
        val outputs: List<Output>
    )

    @Serializable
    data class Input(
        @SerialName("prev_hash")
        val prevHash: String,

        @SerialName("output_index")
        val outputIndex: Long,

        @SerialName("output_value")
        val outputValue: Long,

        val sequence: Long,
        val addresses: List<String>,

        @SerialName("script_type")
        val scriptType: String,

        val age: Long
    )

    @Serializable
    data class Output(
        val value: Long,
        val script: String,
        val addresses: List<String>,

        @SerialName("script_type")
        val scriptType: String
    )
}
