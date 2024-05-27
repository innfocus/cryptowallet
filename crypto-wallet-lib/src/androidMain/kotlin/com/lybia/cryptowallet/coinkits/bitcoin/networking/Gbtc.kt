package com.lybia.cryptowallet.coinkits.bitcoin.networking

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lybia.cryptowallet.coinkits.bitcoin.helper.BTCCoin
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCBalanceSummary
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCTransactionData
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCTransactionOutput
import com.lybia.cryptowallet.coinkits.exclude
import com.lybia.cryptowallet.coinkits.filter
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.prefix
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.*
import java.util.*
import com.lybia.cryptowallet.coinkits.TransationData


class BTCAPI {
    companion object {
        const val server               = "https://blockchain.info/"
        const val serverTest           = "https://testnet.blockchain.info/"
        const val balance              = "balance"
        const val unspentOutputs       = "unspent?limit=1000"
        const val transactionBroadcast = "pushtx"
        const val multiaddr            = "multiaddr?n=100"
    }
}

private interface IGbtc {

    @GET(BTCAPI.balance)
    fun addressUsed(@Query("active") addresses: String): Call<JsonElement>

    @GET(BTCAPI.multiaddr)
    fun transactions(@Query("active") addresses: String): Call<JsonElement>

    @GET(BTCAPI.unspentOutputs)
    fun unspentOutputs(@Query("active") addresses: String): Call<JsonElement>

    @POST(BTCAPI.transactionBroadcast)
    fun transactionBroadcast(@Body params: JsonObject): Call<JsonElement>

    companion object {
        fun create(server: String): IGbtc {
            val retrofit = Retrofit.Builder()
                .baseUrl(server)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(IGbtc::class.java)
        }
    }
}

interface BTCBalanceHandle                  { fun completionHandler(balance: Double, err: Throwable?)}
interface BTCAddressUsedHandle              { fun completionHandler(addressUsed: Array<BTCBalanceSummary>, err: Throwable?)}
interface BTCTransactionsHandle             { fun completionHandler(transactions: Array<BTCTransactionData>, err: Throwable?)}
interface BTCUnspentOutputsHandle           { fun completionHandler(transactions: Array<BTCTransactionOutput>, err: Throwable?)}
interface BTCTransactionBroadcastHandle     { fun completionHandler(transID: String, success: Boolean, err: Throwable?)}

class Gbtc {
    companion object {
        val shared = Gbtc()
    }

    private val apiService      = IGbtc.create(BTCAPI.server)
    private val apiTestService  = IGbtc.create(BTCAPI.serverTest)

    private fun getService(isTestNet: Boolean): IGbtc {
        return when(isTestNet) {
            true    -> apiTestService
            false   -> apiService
        }
    }

    private data class Result(val valid: Boolean, val isTestnet: Boolean)
    private fun validateAddresses(addresses: Array<String>): Result {
        val adds = addresses.filter { it.matches(Regex(ACTCoin.Bitcoin.regex()))}
        return when(adds.size == addresses.size) {
            true -> {
                val prefix = Base58.decode(adds[0]).toHexString().prefix(2)
                Result(true, prefix == "6f")
            }
            false -> Result(false, false)
        }
    }

    fun getBalance(addresses        : Array<String>,
                   completionHandler: BTCBalanceHandle) {
        addressUsed(addresses,
            completionHandler =  object : BTCAddressUsedHandle {
                override fun completionHandler(addressUsed: Array<BTCBalanceSummary>, err: Throwable?) {
                    when(err != null) {
                        true    -> completionHandler.completionHandler(0.0, err)
                        false   -> {
                            val balance = addressUsed.filter{it.finalBalance > 0}.map { it.finalBalance / BTCCoin }.sum()
                            completionHandler.completionHandler(balance, null)
                        }
                    }
                }
            })
    }

    fun transactions(addresses          : Array<String>,
                     completionHandler  : BTCTransactionsHandle)
    {
        addressUsed(addresses,
            completionHandler =  object : BTCAddressUsedHandle {
                override fun completionHandler(addressUsed: Array<BTCBalanceSummary>, err: Throwable?) {
                    when((err != null) or addressUsed.isEmpty()) {
                        true    -> completionHandler.completionHandler(arrayOf(), err)
                        false   -> {
                            val adds = addressUsed.map{it.address}.toTypedArray()
                            val validate = validateAddresses(arrayOf(adds[0]))
                            val call = getService(validate.isTestnet).transactions(adds.joinToString(separator = "|"))
                            call.enqueue(object : Callback<JsonElement> {
                                override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                    val body = response.body()
                                    if ((body != null) && body!!.isJsonObject) {
                                        val bd = body!!.asJsonObject
                                        val txs = bd["txs"]
                                        if (txs.isJsonArray) {
                                            val trans = BTCTransactionData.parser(txs.asJsonArray)
                                            trans.forEach { tran ->
                                                val inputsFilter    = tran.inPuts.filter(adds)
                                                val outputsFilter   = tran.outPuts.filter(adds)
                                                val outputsExclude  = tran.outPuts.exclude(adds)
                                                if (inputsFilter.isNotEmpty()){
                                                    tran.inPuts  = inputsFilter
                                                    tran.outPuts = when(outputsExclude.isNotEmpty()) {
                                                        true    -> outputsExclude
                                                        false   -> outputsFilter}
                                                }else if (outputsFilter.isNotEmpty()){
                                                    tran.outPuts = outputsFilter
                                                }
                                                tran.amount = tran.outPuts.map { it.value }.sum()
                                                tran.timeCreate = Date(tran._time * 1000L)
                                            }
                                            completionHandler.completionHandler(trans, null)
                                            return
                                        }
                                    }
                                    completionHandler.completionHandler(arrayOf(), null)
                                }
                                override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                                    completionHandler.completionHandler(arrayOf(), t)
                                }
                            })
                        }
                    }
                }
            })
    }

    fun addressUsed(addresses           : Array<String>,
                    summaryJoins        : Array<BTCBalanceSummary> = arrayOf(),
                    completionHandler   : BTCAddressUsedHandle){
        val validate = validateAddresses(addresses)
        when(addresses.isNotEmpty() && validate.valid) {
            false -> return completionHandler.completionHandler(arrayOf(), null)
            true -> {
                val list        = addresses.toList()
                val addsRemain  = list.drop(100).toTypedArray()
                val adds        = list.dropLast(addsRemain.size).toTypedArray()
                val call = getService(validate.isTestnet).addressUsed(adds.joinToString(separator = "|"))
                call.enqueue(object: Callback<JsonElement> {
                    override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                        val errBody = response.errorBody()
                        if (errBody != null) {
                            completionHandler.completionHandler(summaryJoins, null)
                        }else {
                            val body = response.body()
                            if ((body != null) && body!!.isJsonObject) {
                                val js          = body!!.asJsonObject
                                var addrsUsed   = mutableListOf<BTCBalanceSummary>()
                                addrsUsed.addAll(summaryJoins)
                                js.keySet().forEach {
                                    val item = js[it]
                                    if (item.isJsonObject) {
                                        var sumary = BTCBalanceSummary.parser(item)
                                        if ((sumary != null) && (sumary!!.txNumber > 0)) {
                                            sumary!!.address = it
                                            addrsUsed.add(sumary!!)
                                        }
                                    }
                                }
                                if (addsRemain.isEmpty()) {
                                    completionHandler.completionHandler(addrsUsed.toTypedArray(), null)
                                }else{
                                    addressUsed(addsRemain, addrsUsed.toTypedArray(), completionHandler)
                                }
                            }else{
                                completionHandler.completionHandler(summaryJoins, null)
                            }
                        }
                    }

                    override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                        completionHandler.completionHandler(summaryJoins, t)
                    }
                })
            }
        }
    }

    fun unspentOutputs(addresses    : Array<String>,
                       completionHandler: BTCUnspentOutputsHandle) {
        addressUsed(addresses,
            completionHandler =  object : BTCAddressUsedHandle {
                override fun completionHandler(addressUsed: Array<BTCBalanceSummary>, err: Throwable?) {
                    val addsFilter = addressUsed.filter{it.finalBalance > 0}
                    when((err != null) or addsFilter.isEmpty()) {
                        true    -> completionHandler.completionHandler(arrayOf(), err)
                        false   -> {
                            val adds        = addsFilter.map{it.address}.toTypedArray()
                            val validate    = validateAddresses(arrayOf(adds[0]))
                            val call        = getService(validate.isTestnet).unspentOutputs(adds.joinToString(separator = "|"))
                            call.enqueue(object : Callback<JsonElement> {
                                override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                                    val body = response.body()
                                    if ((body != null) && body!!.isJsonObject) {
                                        val bd = body!!.asJsonObject
                                        val txs = bd["unspent_outputs"]
                                        if ((txs != null) && txs.isJsonArray) {
                                            completionHandler.completionHandler(BTCTransactionOutput.parser(txs!!.asJsonArray), null)
                                            return
                                        }
                                    }
                                    completionHandler.completionHandler(arrayOf(), null)
                                }

                                override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                                    completionHandler.completionHandler(arrayOf(), t)
                                }
                            })
                        }
                    }

                }
            })
    }

    fun transactionBroadcast(rawTX              : ByteArray,
                             transID            : String,
                             isTestnet          : Boolean,
                             completionHandler  : BTCTransactionBroadcastHandle) {
        val params  = JsonObject()
        params.addProperty("tx", rawTX.toHexString())
        val call    = getService(isTestnet).transactionBroadcast(params)
        call.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                completionHandler.completionHandler(transID, true, null)
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                completionHandler.completionHandler(transID, false, t)
            }
        })
    }
}