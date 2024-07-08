package com.lybia.cryptowallet.models.bitcoin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BTCApiModel (
    val address: String,

    @SerialName("total_received")
    val totalReceived: Long,

    @SerialName("total_sent")
    val totalSent: Long,

    val balance: Long,

    @SerialName("unconfirmed_balance")
    val unconfirmedBalance: Long,

    @SerialName("final_balance")
    val finalBalance: Long,

    @SerialName("n_tx")
    val nTx: Long,

    @SerialName("unconfirmed_n_tx")
    val unconfirmedNTx: Long,

    @SerialName("final_n_tx")
    val finalNTx: Long,

    val txrefs: List<Txref>? = null,

    @SerialName("tx_url")
    val txURL: String? =null,

    val txs: List<Tx>? =null
){

    @Serializable
    data class Txref (
        @SerialName("tx_hash")
        val txHash: String,

        @SerialName("block_height")
        val blockHeight: Long,

        @SerialName("tx_input_n")
        val txInputN: Long,

        @SerialName("tx_output_n")
        val txOutputN: Long,

        val value: Long,

        @SerialName("ref_balance")
        val refBalance: Long,

        val spent: Boolean,
        val confirmations: Long,
        val confirmed: String,

        @SerialName("double_spend")
        val doubleSpend: Boolean
    )


    @Serializable
    data class Tx (
        @SerialName("block_hash")
        val blockHash: String,

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
        val relayedBy: String? = null,

        val confirmed: String,
        val received: String,
        val ver: Long,

        @SerialName("double_spend")
        val doubleSpend: Boolean,

        @SerialName("vin_sz")
        val vinSz: Long,

        @SerialName("vout_sz")
        val voutSz: Long,

        val confirmations: Long,
        val confidence: Long,
        val inputs: List<Input>,
        val outputs: List<Output>
    )

    @Serializable
    data class Input (
        @SerialName("prev_hash")
        val prevHash: String,

        @SerialName("output_index")
        val outputIndex: Long,

        val script: String? = null,

        @SerialName("output_value")
        val outputValue: Long,

        val sequence: Long,
        val addresses: List<String>,

        @SerialName("script_type")
        val scriptType: String,

        val age: Long
    )

    @Serializable
    data class Output (
        val value: Long,
        val script: String,
        val addresses: List<String>,

        @SerialName("script_type")
        val scriptType: String
    )



}

