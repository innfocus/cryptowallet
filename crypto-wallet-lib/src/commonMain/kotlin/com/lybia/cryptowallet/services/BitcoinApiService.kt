package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.bitcoin.BTCApiModel
import com.lybia.cryptowallet.models.bitcoin.BitcoinCreateTransactionRequest
import com.lybia.cryptowallet.models.bitcoin.BitcoinTransactionModel
import com.lybia.cryptowallet.utils.Urls
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers

class BitcoinApiService {
    companion object {
        val INSTANCE: BitcoinApiService = BitcoinApiService()
    }

    /**
     * Get balance for a single address (in satoshis as string).
     */
    suspend fun getBalance(address: String): String? {
        try {
            val network = when (Config.shared.getNetwork()) {
                Network.MAINNET -> "main"
                else -> "test3"
            }
            val response =
                HttpClientService.INSTANCE.client.get(Urls.getBitcoinApiBalance(network, address)) {
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                    }
                }

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<BTCApiModel?>()
                if (rpcResponse?.balance != null) {
                    return rpcResponse.balance.toString()
                }
                return null
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Get combined balance for multiple addresses (returns total in satoshis as string).
     */
    suspend fun getMultiAddressBalance(addresses: List<String>): String? {
        try {
            var totalBalance = 0L
            for (address in addresses) {
                val balanceStr = getBalance(address)
                if (balanceStr != null) {
                    totalBalance += balanceStr.toLongOrNull() ?: 0L
                }
            }
            return totalBalance.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getTransactionHistory(address: String): List<BTCApiModel.Tx>? {
        try {
            val network = when (Config.shared.getNetwork()) {
                Network.MAINNET -> "main"
                else -> "test3"
            }
            val response = HttpClientService.INSTANCE.client.get(
                Urls.getBitcoinApiTransaction(
                    network,
                    address
                )
            ) {
                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }
            if (response.status.value in 200..299) {
                val rpcResponse = response.body<BTCApiModel?>()
                if (rpcResponse?.txs != null) {
                    return rpcResponse.txs
                }
                return emptyList()
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Simple holder for an additional transaction output (e.g., service fee).
     */
    data class AdditionalOutput(val address: String, val value: Long)

    suspend fun createNewTransaction(
        fromAddress: String,
        toAddress: String,
        amount: Long,
        additionalOutputs: List<AdditionalOutput> = emptyList()
    ): BitcoinTransactionModel? {
        try {
            val network = when (Config.shared.getNetwork()) {
                Network.MAINNET -> "main"
                else -> "test3"
            }

            val outputs = mutableListOf(
                BitcoinCreateTransactionRequest.Output(
                    listOf(toAddress),
                    amount
                )
            )
            for (extra in additionalOutputs) {
                outputs.add(
                    BitcoinCreateTransactionRequest.Output(
                        listOf(extra.address),
                        extra.value
                    )
                )
            }

            val requestBody = BitcoinCreateTransactionRequest(
                listOf(
                    BitcoinCreateTransactionRequest.Input(
                        listOf(fromAddress)
                    )
                ),
                outputs
            )
            val response =
                HttpClientService.INSTANCE.client.post(Urls.getBitcoinApiCreateNewTransaction(network)) {
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                    }

                    contentType(ContentType.Application.Json)


                    setBody(requestBody)
                }
            if (response.status.value in 200..299) {
                val rpcResponse = response.body<BitcoinTransactionModel?>()
                return rpcResponse
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Send a signed Bitcoin transaction via BlockCypher.
     */
    suspend fun sendTransaction(signedTxModel: BitcoinTransactionModel): BitcoinTransactionModel? {
        try {
            val network = when (Config.shared.getNetwork()) {
                Network.MAINNET -> "main"
                else -> "test3"
            }
            val response =
                HttpClientService.INSTANCE.client.post(Urls.getBitcoinApiSendTransaction(network)) {
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(signedTxModel)
                }
            if (response.status.value in 200..299) {
                return response.body<BitcoinTransactionModel?>()
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}