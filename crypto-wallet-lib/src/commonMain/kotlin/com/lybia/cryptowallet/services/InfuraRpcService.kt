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

            headers {
                append(HttpHeaders.Accept, "application/json")
            }

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

                headers {
                    append(HttpHeaders.Accept, "application/json")
                }

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
}