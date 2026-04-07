package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.InfuraRpcBalanceResponse
import com.lybia.cryptowallet.models.InfuraRpcRequest
import com.lybia.cryptowallet.models.TransferTokenModel
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.http.contentType


class InfuraRpcService {

    companion object {
        val shared: InfuraRpcService = InfuraRpcService()
    }

    suspend fun getBalance(coin: CoinNetwork, address: String): String? {

        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {

            headers {
                append(HttpHeaders.Accept, "application/json")
            }

            contentType(ContentType.Application.Json)

            setBody(
                InfuraRpcRequest(
                    jsonrpc = "2.0",
                    method = "eth_getBalance",
                    params = listOf(address, "latest"),
                    id = 1
                )
            )
        }

        if(response.status.value in 200..299){
            val rpcResponse = response.body<InfuraRpcBalanceResponse>()
            return rpcResponse.result
        }
        return null
    }
    suspend fun estimateLimitGas(coin: CoinNetwork, requestModel: TransferTokenModel): String?{
        val body = mapOf(
            "jsonrpc" to "2.0",
            "method" to "eth_estimateGas",
            "params" to listOf(
                requestModel,
                "latest"
            ),
            "id" to 1
        )
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {

            headers {
                append(HttpHeaders.Accept, "application/json")
            }

            contentType(ContentType.Application.Json)

            setBody(
                Json.encodeToString(body)
            )
        }

        if(response.status.value in 200..299){
            val rpcResponse = response.body<InfuraRpcBalanceResponse>()
            return rpcResponse.result
        }

        return null
    }
    suspend fun sendSignedTransaction(coin: CoinNetwork, dataSigned: String): String?{
        val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
            headers { append(HttpHeaders.Accept, "application/json") }
            contentType(ContentType.Application.Json)
            setBody(
                InfuraRpcRequest(
                    jsonrpc = "2.0",
                    method = "eth_sendRawTransaction",
                    params = listOf(dataSigned),
                    id = 1
                )
            )
        }
        if(response.status.value in 200..299){
            val rpcResponse = response.body<InfuraRpcBalanceResponse>()
            return if(rpcResponse.error == null){
                rpcResponse.result
            }else{
                throw Exception(rpcResponse.error.message)
            }
        }
        throw Exception("Error")
    }

    /**
     * Get the transaction count (nonce) for an address.
     * @return Hex-encoded nonce string (e.g. "0x1a")
     */
    suspend fun getTransactionCount(coin: CoinNetwork, address: String): String? {
        try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(
                    InfuraRpcRequest(
                        jsonrpc = "2.0",
                        method = "eth_getTransactionCount",
                        params = listOf(address, "latest"),
                        id = 1
                    )
                )
            }
            if (response.status.value in 200..299) {
                val data = response.body<InfuraRpcBalanceResponse>()
                if (data.error == null) return data.result
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getAllGasPrice(coin: CoinNetwork, chainId: Int): String?{
        try{
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {

                headers {
                    append(HttpHeaders.Accept, "application/json")
                }

                contentType(ContentType.Application.Json)

                setBody(
                    InfuraRpcRequest(
                        jsonrpc = "2.0",
                        method = "eth_gasPrice",
                        params = listOf(),
                        id = chainId
                    )
                )
            }
            if(response.status.value in 200..299){
                val data = response.body<InfuraRpcBalanceResponse>()
                if(data.error == null) {
                    return data.result
                }
            }
            return null
        } catch (e: Exception){
            e.printStackTrace()
            return null
        }
    }
    suspend fun getChainId(coin: CoinNetwork): String?{
        try{
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(
                    InfuraRpcRequest(
                        jsonrpc = "2.0",
                        method = "eth_chainId",
                        params = listOf(),
                        id = 1
                    )
                )
            }
            if(response.status.value in 200..299){
                val data = response.body<InfuraRpcBalanceResponse>()
                if(data.error == null) {
                    return data.result
                }
            }
            return null
        } catch (e: Exception){
            e.printStackTrace()
            return null
        }
    }

    /**
     * Get the current max priority fee per gas (EIP-1559 tip).
     * @return Hex-encoded wei string (e.g. "0x59682f00")
     */
    suspend fun getMaxPriorityFeePerGas(coin: CoinNetwork): String? {
        try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(
                    InfuraRpcRequest(
                        jsonrpc = "2.0",
                        method = "eth_maxPriorityFeePerGas",
                        params = listOf(),
                        id = 1
                    )
                )
            }
            if (response.status.value in 200..299) {
                val data = response.body<InfuraRpcBalanceResponse>()
                if (data.error == null) return data.result
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Execute a read-only call via eth_call (no transaction broadcast).
     * Used for querying contract state (e.g. ERC-20 allowance, balanceOf).
     *
     * @param coin Network configuration
     * @param to Contract address
     * @param data ABI-encoded call data (0x-prefixed hex)
     * @return Hex-encoded result string, or null on failure
     */
    suspend fun ethCall(coin: CoinNetwork, to: String, data: String): String? {
        try {
            val callObject = mapOf("to" to to, "data" to data)
            val body = mapOf(
                "jsonrpc" to "2.0",
                "method" to "eth_call",
                "params" to listOf(callObject, "latest"),
                "id" to 1
            )
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(body))
            }
            if (response.status.value in 200..299) {
                val rpcResponse = response.body<InfuraRpcBalanceResponse>()
                if (rpcResponse.error == null) return rpcResponse.result
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Get the base fee of the latest block via eth_getBlockByNumber.
     * @return Hex-encoded baseFeePerGas string, or null
     */
    suspend fun getBaseFee(coin: CoinNetwork): String? {
        try {
            val response = HttpClientService.INSTANCE.client.post(coin.getInfuraRpcUrl()) {
                headers { append(HttpHeaders.Accept, "application/json") }
                contentType(ContentType.Application.Json)
                setBody(
                    InfuraRpcRequest(
                        jsonrpc = "2.0",
                        method = "eth_getBlockByNumber",
                        params = listOf("latest", "false"),
                        id = 1
                    )
                )
            }
            if (response.status.value in 200..299) {
                val bodyText = response.body<String>()
                // Parse baseFeePerGas from the block object
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val jsonObj = json.parseToJsonElement(bodyText)
                val result = jsonObj.jsonObject["result"]?.jsonObject
                val baseFee = result?.get("baseFeePerGas")?.jsonPrimitive?.content
                return baseFee
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}