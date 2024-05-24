package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.ExplorerModel
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.Transaction
import com.lybia.cryptowallet.models.TransactionToken
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers

class ExplorerRpcService {
    companion object {
        val INSTANCE: ExplorerRpcService = ExplorerRpcService()
    }

    suspend fun getTransactionHistory(
        coin: CoinNetwork,
        address: String,
    ): ExplorerModel<List<Transaction>>? {
        try{
            val response = HttpClientService.INSTANCE.client.get(coin.getExplorerEndpoint()){
                url {
                    parameters.append("address", address)
                    parameters.append("module", "account")
                    parameters.append("action", "txlist")
                    parameters.append("startblock", "0")
                    parameters.append("endblock", "99999999")
                    parameters.append("sort", "asc")
                    parameters.append("apikey", coin.apiKeyExplorer)
                }

                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            return if(response.status.value in 200..299){
                val rpcResponse = response.body<ExplorerModel<List<Transaction>>?>()
                if(rpcResponse != null && rpcResponse.status == "1"){
                    rpcResponse
                }else{
                    null
                }
            }else{
                null
            }
        }catch (e: Exception){
            e.printStackTrace()
            return null
        }

    }

    suspend fun getBalanceToken(
        coin: CoinNetwork,
        address: String,
        contractAddress: String
    ): String? {
        try{
            val response = HttpClientService.INSTANCE.client.get(coin.getExplorerEndpoint()){
                url {
                    parameters.append("module", "account")
                    parameters.append("action", "tokenbalance")
                    parameters.append("contractaddress", contractAddress)
                    parameters.append("address", address)
                    parameters.append("tag", "latest")
                    parameters.append("apikey", coin.apiKeyExplorer)
                }

                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            if(response.status.value in 200..299){
                val rpcResponse = response.body<ExplorerModel<String>?>()
                return rpcResponse?.result
            }

            return null
        }
        catch (e: Exception){
            e.printStackTrace()
            return null
        }
    }

    suspend fun getTransactionHistoryToken(
        coin: CoinNetwork,
        address: String,
        contractAddress: String
    ): ExplorerModel<List<TransactionToken>>?{
        try{
            val response = HttpClientService.INSTANCE.client.get(coin.getExplorerEndpoint()){
                url {
                    parameters.append("address", address)
                    parameters.append("module", "account")
                    parameters.append("action", "tokentx")
                    parameters.append("contractaddress", contractAddress)
                    parameters.append("startblock", "0")
                    parameters.append("endblock", "99999999")
                    parameters.append("sort", "asc")
                    parameters.append("apikey", coin.apiKeyExplorer)
                }

                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            return if(response.status.value in 200..299){
                val rpcResponse = response.body<ExplorerModel<List<TransactionToken>>?>()
                if(rpcResponse != null && rpcResponse.status == "1") {
                    rpcResponse
                }else{
                    null
                }

            }else{
                null

            }
        }catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }
    suspend fun getAllGasPrice(coin: CoinNetwork): GasPrice?{
        val response = HttpClientService.INSTANCE.client.get(coin.getExplorerEndpoint()){
            url{
                parameters.append("module","gastracker")
                parameters.append("action","gasoracle")
                parameters.append("apiKey",coin.apiKeyExplorer)
            }
            headers {
                append(HttpHeaders.Accept, "application/json")
            }
        }
        if(response.status.value in 200..299){
            val data = response.body<ExplorerModel<GasPrice>>()
            if(data.status == "1") {
                return data.result
            }
        }
        return null
    }
}