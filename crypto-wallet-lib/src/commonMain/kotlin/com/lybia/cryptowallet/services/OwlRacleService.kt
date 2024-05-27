package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.OwlRacleModel
import com.lybia.cryptowallet.models.TransactionGas
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers

class OwlRacleService {
    companion object {
        val shared: OwlRacleService = OwlRacleService()
    }
    suspend fun getAllGasPrice(coin: CoinNetwork): GasPrice?{
        val response = HttpClientService.INSTANCE.client.get(coin.getOwlRacleUrl()){
            url{
                parameters.append("apikey",coin.apiKeyOwlRacle)
                parameters.append("blocks","200")
                parameters.append("accept","35,60,90")
                parameters.append("eip1559","false")
            }
            headers {
                append(HttpHeaders.Accept, "application/json")
            }
        }
        if(response.status.value in 200..299){
            val data = response.body<OwlRacleModel<TransactionGas>>()
            return GasPrice(data.speeds[0].gasPrice.toString(),data.speeds[1].gasPrice.toString(),data.speeds[2].gasPrice.toString())
        }
        return null
    }
}