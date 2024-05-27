package com.lybia.cryptowallet.coinkits.centrality.networking

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lybia.cryptowallet.coinkits.centrality.model.CennzAddress
import com.lybia.cryptowallet.coinkits.centrality.model.CennzPartialFee
import com.lybia.cryptowallet.coinkits.centrality.model.CennzTransfer
import com.lybia.cryptowallet.coinkits.centrality.model.CentralityAppResponse
import com.lybia.cryptowallet.coinkits.centrality.model.ExtrinsicBase
import com.lybia.cryptowallet.coinkits.centrality.model.ScanAccount
import com.lybia.cryptowallet.coinkits.centrality.model.ScanTransfer
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork

class CENNZ_API {
    companion object {
        const val server = "https://service.eks.centralityapp.com"
        const val rpcServer = "https://cennznet.unfrastructure.io"
        const val scanAccount = "/cennznet-explorer-api/api/scan/account"
        const val scanTransfers = "/cennznet-explorer-api/api/scan/transfers"
        const val scanExtrinsic = "/cennznet-explorer-api/api/scan/extrinsic"
        const val localApiServer = "https://fgwallet.srsfc.com"
        const val getAddressApi = "/cennz-address"
        const val signMessageApi = "/cennz-sign"
    }
}

interface CennzCallbackHandle {
    fun completionHandler(success: Boolean, error: String)
}

interface CennzGetBalanceHandle {
    fun completionHandler(balance: Long, error: String)
}

interface CennzGetAddressHandle {
    fun completionHandler(address: CennzAddress?, error: String)
}

interface CennzGetTransactionsHandle {
    fun completionHandler(transactions: List<CennzTransfer>, error: String)
}

interface CennzEstimateFeeHandle {
    fun completionHandler(estimateFee: Long, error: String)
}

interface CennzPaymentQueryHandle {
    fun completionHandler(cennzPartialFee: CennzPartialFee?, error: String)
}

interface CennzAccountNextIndexHandle {
    fun completionHandler(nextIndex: Int, error: String)
}

interface CennzSendHandle {
    fun completionHandler(
        extrinsic: ExtrinsicBase?,
        extrinsicHash: String,
        success: Boolean,
        error: String
    )
}

interface CennzSubmitExtrinsicHandle {
    fun completionHandler(extrinsicHash: String, success: Boolean, error: String)
}

private interface CennzLocalApiServices {
    @POST(CENNZ_API.getAddressApi)
    fun getAddress(@Body params: JsonObject): Call<JsonObject>

    @POST(CENNZ_API.signMessageApi)
    fun signMessage(@Body params: JsonObject): Call<JsonObject>

    companion object {
        fun create(): CennzLocalApiServices {

            val client = OkHttpClient.Builder()
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(CENNZ_API.localApiServer)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(CennzLocalApiServices::class.java)
        }
    }
}

private interface CennzRpcApiServices {
    @POST("/public")
    fun query(@Body params: JsonObject): Call<JsonObject>

    companion object {
        fun create(): CennzRpcApiServices {

            val client = OkHttpClient.Builder()
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(CENNZ_API.rpcServer)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(CennzRpcApiServices::class.java)
        }
    }
}

private interface CentralityApiServices {

    @POST(CENNZ_API.scanExtrinsic)
    fun scanExtrinsic(@Body params: JsonObject): Call<JsonElement>

    @POST(CENNZ_API.scanTransfers)
    fun transactions(@Body params: JsonObject): Call<CentralityAppResponse<ScanTransfer>>

    @POST(CENNZ_API.scanAccount)
    fun scanAccount(@Body params: JsonObject): Call<CentralityAppResponse<ScanAccount>>


    companion object {
        fun create(): CentralityApiServices {

            val client = OkHttpClient.Builder()
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(CENNZ_API.server)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(CentralityApiServices::class.java)
        }
    }
}

class CentralityNetwork {

    companion object {
        val shared = CentralityNetwork()
        var specVersion = 39
        var transactionVersion = 5
        var genesisHash = ""
        var blockHash = ""
        var mortalLength = 65
        var current: Long = 6425936
        const val BASE_UNIT = 10000
    }

    private val apiService = CentralityApiServices.create()
    private val localApiServices = CennzLocalApiServices.create()
    private val cennzRpcApiServices = CennzRpcApiServices.create()

    //    0x6211cb => 6427083
    fun convertNumber(number: String): Long {
        val text = number.removePrefix("0x")
        return text.toLong(radix = 16)
    }

    //    [182, 4]
    fun makeEraOption(current: Long): ByteArray {
        val result = ByteArray(2)
        val calPeriod = 128
        val quantizedPhase = current % calPeriod
        val encoded = 6 + (quantizedPhase shl 4)

        val first = encoded shr 8
        val second = encoded and 0xff
        result[0] = second.toByte()
        result[1] = first.toByte()
        return result
    }

    fun sendCoin(
        fromAddress: String,
        toAddressStr: String,
        amount: Double,
        assetId: Int = 1,
        completionHandler: CennzSendHandle
    ) {
        getRuntimeVersion(
            object :
                CennzCallbackHandle {
                override fun completionHandler(success: Boolean, error: String) {
                    chainGetBlockHash(
                        object :
                            CennzCallbackHandle {
                            override fun completionHandler(success: Boolean, error: String) {
                                if (success) {
                                    chainGetFinalizedHead(
                                        object :
                                            CennzCallbackHandle {
                                            override fun completionHandler(
                                                success: Boolean,
                                                error: String
                                            ) {
                                                if (success) {
                                                    chainGetHeader(
                                                        blockHash,
                                                        object :
                                                            CennzCallbackHandle {
                                                            override fun completionHandler(
                                                                success: Boolean,
                                                                error: String
                                                            ) {
                                                                if (success) {

                                                                    systemAccountNextIndex(
                                                                        fromAddress,
                                                                        object :
                                                                            CennzAccountNextIndexHandle {
                                                                            override fun completionHandler(
                                                                                nextIndex: Int,
                                                                                error: String
                                                                            ) {
                                                                                val extrinsic =
                                                                                    ExtrinsicBase()
                                                                                extrinsic.paramsMethod(
                                                                                    toAddressStr,
                                                                                    amount.toLong(),
                                                                                    assetId
                                                                                )
                                                                                extrinsic.paramsSignature(
                                                                                    fromAddress,
                                                                                    nextIndex
                                                                                )

                                                                                extrinsic.signOptions(
                                                                                    specVersion,
                                                                                    transactionVersion,
                                                                                    genesisHash,
                                                                                    blockHash,
                                                                                    makeEraOption(
                                                                                        current
                                                                                    )
                                                                                )

                                                                                // Sign tx
                                                                                val seed =
                                                                                    CoinsManager.shared.getHDWallet()
                                                                                        ?.calculateSeed(
                                                                                            ACTNetwork(
                                                                                                ACTCoin.Centrality,
                                                                                                false
                                                                                            )
                                                                                        )
                                                                                seed?.let {
                                                                                    signExtrinsicBase(
                                                                                        it.toHexWithPrefix(),
                                                                                        extrinsic,
                                                                                        object :
                                                                                            CennzCallbackHandle {
                                                                                            override fun completionHandler(
                                                                                                success: Boolean,
                                                                                                error: String
                                                                                            ) {
                                                                                                if (success) {
                                                                                                    // Submit tx
                                                                                                    submitExtrinsic(
                                                                                                        extrinsic.toHex(),
                                                                                                        extrinsic.toString(),
                                                                                                        object :
                                                                                                            CennzSubmitExtrinsicHandle {
                                                                                                            override fun completionHandler(
                                                                                                                extrinsicHash: String,
                                                                                                                success: Boolean,
                                                                                                                error: String
                                                                                                            ) {
                                                                                                                completionHandler.completionHandler(
                                                                                                                    extrinsic,
                                                                                                                    extrinsicHash,
                                                                                                                    success,
                                                                                                                    error
                                                                                                                )
                                                                                                            }

                                                                                                        })
                                                                                                } else {
                                                                                                    completionHandler.completionHandler(
                                                                                                        null,
                                                                                                        "",
                                                                                                        false,
                                                                                                        "Sign error"
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        })
                                                                                }

                                                                            }
                                                                        })

                                                                } else {
                                                                    completionHandler.completionHandler(
                                                                        null,
                                                                        "",
                                                                        false,
                                                                        ""
                                                                    )
                                                                }
                                                            }
                                                        })
                                                } else {
                                                    completionHandler.completionHandler(
                                                        null,
                                                        "",
                                                        false,
                                                        ""
                                                    )
                                                }

                                            }
                                        })
                                } else {
                                    completionHandler.completionHandler(null, "", false, "")
                                }
                            }
                        })
                }
            })
    }

    fun calculateEstimateFee(completionHandler: CennzEstimateFeeHandle) {
        completionHandler.completionHandler(ACTCoin.Centrality.feeDefault().toLong(), "")
    }

    fun getRuntimeVersion(
        completionHandler: CennzCallbackHandle
    ) {
        val params = JsonArray()

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "state_getRuntimeVersion")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                false,
                                message
                            )
                        } else {
                            val result = data.getAsJsonObject("result")
                            specVersion = result.get("specVersion").asInt
                            transactionVersion = result.get("transactionVersion").asInt
                            completionHandler.completionHandler(true, "")
                        }
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                } else {
                    completionHandler.completionHandler(false, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(false, t.localizedMessage)
            }
        })
    }

    fun chainGetHeader(
        hash: String,
        completionHandler: CennzCallbackHandle
    ) {
        val params = JsonArray()
        params.add(hash)

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "chain_getHeader")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                false,
                                message
                            )
                        } else {
                            val result = data.getAsJsonObject("result")
                            val number = result.get("number").asString
                            current = convertNumber(number)
                            completionHandler.completionHandler(true, "")
                        }
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                } else {
                    completionHandler.completionHandler(false, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(false, t.localizedMessage)
            }
        })
    }

    fun chainGetFinalizedHead(
        completionHandler: CennzCallbackHandle
    ) {
        val params = JsonArray()

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "chain_getFinalizedHead")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                false,
                                message
                            )
                        } else {
                            blockHash = data.get("result").asString
                            completionHandler.completionHandler(true, "")
                        }
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                } else {
                    completionHandler.completionHandler(false, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(false, t.localizedMessage)
            }
        })
    }

    fun chainGetBlockHash(
        completionHandler: CennzCallbackHandle
    ) {
        val params = JsonArray()
        params.add(0)

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "chain_getBlockHash")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                false,
                                message
                            )
                        } else {
                            genesisHash = data.get("result").asString
                            completionHandler.completionHandler(true, "")
                        }
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                } else {
                    completionHandler.completionHandler(false, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(false, t.localizedMessage)
            }
        })
    }

    fun paymentQueryInfo(
        hash: String,
        completionHandler: CennzPaymentQueryHandle
    ) {
        val params = JsonArray()
        params.add(hash)

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "payment_queryInfo")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                null,
                                message
                            )
                        } else {
                            val gson = Gson()
                            val cennzPartialFee = gson.fromJson(
                                data.get("result"),
                                CennzPartialFee::class.java
                            )
                            completionHandler.completionHandler(cennzPartialFee, "")
                        }
                    } else {
                        completionHandler.completionHandler(null, "")
                    }
                } else {
                    completionHandler.completionHandler(null, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(null, t.localizedMessage)
            }
        })
    }

    fun systemAccountNextIndex(
        address: String,
        completionHandler: CennzAccountNextIndexHandle
    ) {
        val params = JsonArray()
        params.add(address)

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "system_accountNextIndex")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            val message = error.get("message").asString
                            completionHandler.completionHandler(
                                0,
                                message
                            )
                        } else {
                            val nextIndex = data.get("result").asInt
                            completionHandler.completionHandler(nextIndex, "")
                        }
                    } else {
                        completionHandler.completionHandler(0, "")
                    }
                } else {
                    completionHandler.completionHandler(0, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(0, t.localizedMessage)
            }
        })
    }

    fun submitExtrinsic(
        hash: String,
        json: String,
        completionHandler: CennzSubmitExtrinsicHandle
    ) {
        val params = JsonArray()
        params.add(hash)

        val payload = JsonObject()
        payload.addProperty("id", 1)
        payload.addProperty("jsonrpc", "2.0")
        payload.addProperty("method", "author_submitExtrinsic")
        payload.add("params", params)

        val call = cennzRpcApiServices.query(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                Log.d("SENDING_CENNZ", response.body().toString())
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val error = data.getAsJsonObject("error")
                        if (error != null) {
                            error.get("code")
                            val message = error.get("message").asString
                            val code = error.get("code").asInt
                            completionHandler.completionHandler(
                                "",
                                false,
                                "$message - Error code: $code - Tx: $hash - JSON: $json"
                            )
                        } else {
                            val extrinsicHash = data.get("result").asString
                            completionHandler.completionHandler(extrinsicHash, true, "")
                        }
                    } else {
                        completionHandler.completionHandler("", false, "")
                    }
                } else {
                    completionHandler.completionHandler("", false, "")
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler("", false, t.localizedMessage)
            }
        })
    }

    fun scanAccount(
        address: String,
        assetId: Int,
        completionHandler: CennzGetBalanceHandle
    ) {

        val payload = JsonObject()
        payload.addProperty("address", address)

        val call = apiService.scanAccount(payload)
        call.enqueue(object : Callback<CentralityAppResponse<ScanAccount>> {
            override fun onResponse(
                call: Call<CentralityAppResponse<ScanAccount>>,
                response: Response<CentralityAppResponse<ScanAccount>>
            ) {
                if (response.isSuccessful) {
                    val data: CentralityAppResponse<ScanAccount>? = response.body()
                    if (data != null) {
                        val check = data.data!!.balances.iterator()
                        while (check.hasNext()) {
                            val asset = check.next()
                            if (asset.assetID == assetId) {
                                return completionHandler.completionHandler(asset.free, "")
                            }
                        }
                        completionHandler.completionHandler(0, "")
                    } else {
                        completionHandler.completionHandler(0, "")
                    }

                } else {
                    val errorBody = response.errorBody()
                    if ((errorBody != null)) {
                        try {
                            val json = JSONObject(errorBody.string())
                            val message = json.getString("message")
                            completionHandler.completionHandler(
                                0,
                                message
                            )
                        } catch (e: JSONException) {
                            completionHandler.completionHandler(
                                0,
                                errorBody.string()
                            )
                        }
                    } else {
                        completionHandler.completionHandler(0, "")
                    }
                }
            }

            override fun onFailure(call: Call<CentralityAppResponse<ScanAccount>>, t: Throwable) {
                completionHandler.completionHandler(0, t.localizedMessage)
            }
        })
    }

    fun transactions(
        address: String,
        assetId: Int,
        row: Int = 100,
        page: Int = 0,
        completionHandler: CennzGetTransactionsHandle
    ) {

        val payload = JsonObject()
        payload.addProperty("address", address)
        payload.addProperty("row", row)
        payload.addProperty("page", page)

        val call = apiService.transactions(payload)
        call.enqueue(object : Callback<CentralityAppResponse<ScanTransfer>> {
            override fun onResponse(
                call: Call<CentralityAppResponse<ScanTransfer>>,
                response: Response<CentralityAppResponse<ScanTransfer>>
            ) {
                if (response.isSuccessful) {
                    val data: CentralityAppResponse<ScanTransfer>? = response.body()
                    if (data != null) {
                        if (data.data != null) {
                            val transfers = data.data!!.transfers.filter { it.assetID == assetId  && it.success }
                            completionHandler.completionHandler(transfers, "")
                        } else {
                            completionHandler.completionHandler(emptyList(), "")
                        }
                    } else {
                        completionHandler.completionHandler(emptyList(), "")
                    }

                } else {
                    val errorBody = response.errorBody()
                    if ((errorBody != null)) {
                        try {
                            val json = JSONObject(errorBody.string())
                            val message = json.getString("message")
                            completionHandler.completionHandler(
                                emptyList(),
                                message
                            )
                        } catch (e: JSONException) {
                            completionHandler.completionHandler(
                                emptyList(),
                                errorBody.string()
                            )
                        }
                    } else {
                        completionHandler.completionHandler(emptyList(), "")
                    }
                }
            }

            override fun onFailure(call: Call<CentralityAppResponse<ScanTransfer>>, t: Throwable) {
                completionHandler.completionHandler(emptyList(), t.localizedMessage)
            }
        })
    }

    fun getPublicAddress(
        seed: String,
        completionHandler: CennzGetAddressHandle
    ) {

        val payload = JsonObject()
        payload.addProperty("seed", seed)

        val call = localApiServices.getAddress(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(
                call: Call<JsonObject>,
                response: Response<JsonObject>
            ) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val address = data.get("address").asString
                        val publicKey = data.get("publicKey").asString
                        completionHandler.completionHandler(CennzAddress(address, publicKey), "")
                    } else {
                        completionHandler.completionHandler(null, "")
                    }
                } else {
                    val errorBody = response.errorBody()
                    if ((errorBody != null)) {
                        try {
                            val json = JSONObject(errorBody.string())
                            val message = json.getString("message")
                            completionHandler.completionHandler(
                                null,
                                message
                            )
                        } catch (e: JSONException) {
                            completionHandler.completionHandler(
                                null,
                                errorBody.string()
                            )
                        }
                    } else {
                        completionHandler.completionHandler(null, "")
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(null, t.localizedMessage)
            }
        })
    }

    fun signExtrinsicBase(
        seed: String,
        extrinsic: ExtrinsicBase,
        completionHandler: CennzCallbackHandle
    ) {

        val payload = JsonObject()
        payload.addProperty("seed", seed)
        payload.addProperty("payload", extrinsic.createPayload().toHexWithPrefix())

        val call = localApiServices.signMessage(payload)
        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(
                call: Call<JsonObject>,
                response: Response<JsonObject>
            ) {
                if (response.isSuccessful) {
                    val data: JsonObject? = response.body()
                    if (data != null) {
                        val signature = data.get("signature").asString
                        extrinsic.sign(signature)
                        completionHandler.completionHandler(true, "")
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                } else {
                    val errorBody = response.errorBody()
                    if ((errorBody != null)) {
                        try {
                            val json = JSONObject(errorBody.string())
                            val message = json.getString("message")
                            completionHandler.completionHandler(
                                false,
                                message
                            )
                        } catch (e: JSONException) {
                            completionHandler.completionHandler(
                                false,
                                errorBody.string()
                            )
                        }
                    } else {
                        completionHandler.completionHandler(false, "")
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(false, t.localizedMessage)
            }
        })
    }
}

fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

fun ByteArray.toHexWithPrefix(): String {
    return "0x" + toHex()
}