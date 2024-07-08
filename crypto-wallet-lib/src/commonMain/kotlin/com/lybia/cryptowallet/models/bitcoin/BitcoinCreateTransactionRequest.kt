package com.lybia.cryptowallet.models.bitcoin


import kotlinx.serialization.Serializable

@Serializable
data class BitcoinCreateTransactionRequest(
    val inputs: List<Input>,
    val outputs: List<Output>
) {

    @Serializable
    data class Input(
        val addresses: List<String>
    )

    @Serializable
    data class Output(
        val addresses: List<String>,
        val value: Long
    )

}
