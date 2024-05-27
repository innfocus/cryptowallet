package com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private interface IRPCJson {
    @POST(".")
    fun sendTo(@Body params: JsonObject): Call<JsonElement>

    companion object {
        fun create(server: String): IRPCJson {
            val retrofit = Retrofit.Builder()
                .baseUrl(server)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(IRPCJson::class.java)
        }
    }
}

interface RPCJSONHandle<T>{ fun completionHandler(response: T?, err: Throwable?)}
class ACTClient
{
    private val nodeEndpoint    : String
    private val version         : String
    private val service         : IRPCJson
    private var idGenerator     = ACTIDGenerator()

    constructor(nodeEndpoint   : String,
                version        : String = "2.0") {
        this.nodeEndpoint   = nodeEndpoint
        this.version        = version
        this.service        = IRPCJson.create(this.nodeEndpoint)
    }

    fun <T> send(request            : ACTJsonRPCRequest<T>,
                 completionHandler  : RPCJSONHandle<T>
    ) {
        val r       = ACTBatchElement(request, version, idGenerator.next())
        val params  = r.body
        val call    = service.sendTo(params)
        call.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.isJsonObject) {
                    val result = responseBody.asJsonObject["result"]
                    if (result != null) {
                        return completionHandler.completionHandler(request.response(result.asJsonObject), null)
                    }
                    completionHandler.completionHandler(null, null)
                } else {
                    completionHandler.completionHandler(null, null)
                }
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                completionHandler.completionHandler(null, t)
            }
        })
    }
}