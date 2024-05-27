package com.lybia.cryptowallet.coinkits.cardano.networking

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import com.lybia.cryptowallet.coinkits.cardano.helpers.ADACoin
import com.lybia.cryptowallet.coinkits.cardano.model.transaction.*
import com.lybia.cryptowallet.coinkits.cardano.networking.models.ADATransaction
import com.lybia.cryptowallet.coinkits.cardano.networking.models.ADAUnspentTransaction
import com.lybia.cryptowallet.coinkits.cardano.networking.models.CardanoCurrentBestBlock
import com.lybia.cryptowallet.coinkits.exclude
import com.lybia.cryptowallet.coinkits.filterAddress
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.coinkits.cardano.model.CarAddress
import com.lybia.cryptowallet.coinkits.cardano.model.transaction.TransactionWitnessSet
import com.lybia.cryptowallet.coinkits.cardano.model.transaction.TxWitnessBuilder
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max


class YOROIAPI {
    companion object {
        const val server = "https://iohk-mainnet.yoroiwallet.com/api/"
        const val utxo = "txs/utxoForAddresses"
        const val utxoSum = "txs/utxoSumForAddresses"
        const val history = "v2/txs/history"
        const val signed = "txs/signed"
        const val addressUsed = "v2/addresses/filterUsed"
        const val bestblock = "v2/bestblock"
        const val TX_HISTORY_RESPONSE_LIMIT = 50
        const val MIN_AMOUNT_PER_TX = 1000000.0
    }
}

interface ADABalanceHandle {
    fun completionHandler(balance: Double, err: Throwable?)
}

interface ADATransactionsHandle {
    fun completionHandler(transactions: Array<ADATransaction>?, err: Throwable?)
}

interface ADASendCoinHandle {
    fun completionHandler(transID: String, success: Boolean, errStr: String)
}

interface ADAUnspentOutputsHandle {
    fun completionHandler(unspentOutputs: Array<ADAUnspentTransaction>, err: Throwable?)
}

interface ADAAddressUsedHandle {
    fun completionHandler(addressUsed: Array<String>, err: Throwable?)
}

interface ADASendTxAuxHandle {
    fun completionHandler(transID: String, success: Boolean, errStr: String)
}

interface ADACreateTxAuxHandle {
    fun completionHandler(txAux: TxAux?, errStr: String)
}

interface ADAEstimateFeeHandle {
    fun completionHandler(estimateFee: Double, errStr: String)
}

interface ADACurrentBlockHandle {
    fun completionHandler(currentBestBlock: CardanoCurrentBestBlock?, errStr: String)
}

private interface IGada {

    @POST(YOROIAPI.utxoSum)
    fun getBalance(@Body params: JsonObject): Call<JsonObject>

    @POST(YOROIAPI.history)
    fun transactions(@Body params: JsonObject): Call<JsonElement>

    @POST(YOROIAPI.utxo)
    fun unspentOutputs(@Body params: JsonObject): Call<JsonElement>

    @POST(YOROIAPI.addressUsed)
    fun addressUsed(@Body params: JsonObject): Call<JsonElement>

    @POST(YOROIAPI.signed)
    fun sendTxAux(@Body params: JsonObject): Call<JsonElement>

    @GET(YOROIAPI.bestblock)
    fun bestblock(): Call<JsonElement>

    companion object {
        fun create(): IGada {

            val client = OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(YOROIAPI.server)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(IGada::class.java)
        }
    }
}

class Gada {

    companion object {
        val shared = Gada()
        lateinit var currentBestBlock: CardanoCurrentBestBlock
    }

    private val apiService = IGada.create()

    fun getBalance(
        addresses: Array<String>,
        completionHandler: ADABalanceHandle
    ) {
        unspentOutputs(addresses, object : ADAUnspentOutputsHandle {
            override fun completionHandler(
                unspentOutputs: Array<ADAUnspentTransaction>,
                err: Throwable?
            ) {
                val total = unspentOutputs.map { it.amount }.sum()
                val sum = total.toDouble() / ADACoin
                completionHandler.completionHandler(sum, null)
            }
        })
    }

    fun transactions(
        addresses: Array<String>,
        untilBlock: String,
        afterTx: String?,
        afterBlock: String?,
        transJoin: Array<ADATransaction> = arrayOf(),
        ignoreAddsUsed: Boolean = false,
        completionHandler: ADATransactionsHandle
    ) {
        addressUsed(addresses, ignoreAddsUsed, object : ADAAddressUsedHandle {
            override fun completionHandler(addressUsed: Array<String>, err: Throwable?) {
                if (err == null) {
                    if (addressUsed.isNotEmpty()) {
                        val params = JsonObject()
                        params.add("addresses", addressUsed.toJsonArray())
                        params.addProperty("untilBlock", untilBlock)
                        if (afterTx != null) {
                            val after = JsonObject()
                            after.addProperty("tx", afterTx)
                            after.addProperty("block", afterBlock)
                            params.add("after", after)
                        }
                        val call = apiService.transactions(params)
                        call.enqueue(object : Callback<JsonElement> {
                            override fun onResponse(
                                call: Call<JsonElement>,
                                response: Response<JsonElement>
                            ) {
                                val errBody = response.errorBody()
                                if (errBody != null) {
                                    completionHandler.completionHandler(transJoin, null)
                                } else {
                                    val gson = Gson()
                                    val transactions = gson.fromJson(
                                        response.body().toString(),
                                        Array<ADATransaction>::class.java
                                    )
                                    transactions.forEach { tran ->
                                        val outs = tran.outputs
                                        val ins = tran.inputs
                                        tran.fee =
                                            ins.map { it.value }.sum() - outs.map { it.value }.sum()
                                        val inputsFilter = tran.inputs.filterAddress(addressUsed)
                                        val outputsFilter = tran.outputs.filterAddress(addressUsed)
                                        val outputsExclude = tran.outputs.exclude(addressUsed)
                                        if (inputsFilter.isNotEmpty()) {
                                            tran.inputs = inputsFilter
                                            tran.outputs = when (outputsExclude.isNotEmpty()) {
                                                true -> outputsExclude
                                                false -> outputsFilter
                                            }
                                        } else if (outputsFilter.isNotEmpty()) {
                                            tran.outputs = outputsFilter
                                        }
                                        tran.amount = tran.outputs.map { it.value }.sum()
                                    }
                                    transactions.sortByDescending { it.lastUpdate() }
                                    val sumTrans =
                                        arrayOf<ADATransaction>().plus(transactions).plus(transJoin)
                                    if (transactions.size < YOROIAPI.TX_HISTORY_RESPONSE_LIMIT) {
                                        completionHandler.completionHandler(sumTrans.distinctBy { it.transactionID }
                                            .filter { it.state.lowercase(Locale.getDefault()) != "failed" }
                                            .toTypedArray(), null)
                                    } else {
                                        val last = sumTrans.first()
                                        transactions(
                                            addressUsed,
                                            untilBlock,
                                            last.transactionID,
                                            last.blockHash,
                                            sumTrans,
                                            true,
                                            completionHandler
                                        )
                                    }
                                }
                            }

                            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                                completionHandler.completionHandler(null, t)
                            }
                        })
                    } else {
                        completionHandler.completionHandler(transJoin, null)
                    }
                } else {
                    completionHandler.completionHandler(null, err)
                }
            }
        })
    }

    fun calculateEstimateFee(
        prvKeys: Array<ACTPrivateKey>,
        unspentAddresses: Array<String>,
        fromAddress: ACTAddress,
        toAddressStr: String,
        amount: Double,
        serAddressStr: String,
        minerFee: Double,
        minFee: Double,
        serviceFee: Double,
        completionHandler: ADAEstimateFeeHandle
    ) {
        createTxAux(prvKeys = prvKeys,
                unspentAddresses = unspentAddresses,
                fromAddress = fromAddress,
                toAddressStr = toAddressStr,
                serAddressStr = serAddressStr,
                amount = max(YOROIAPI.MIN_AMOUNT_PER_TX, amount),
                serviceFee = serviceFee,
                completionHandler = object : ADACreateTxAuxHandle {
                    override fun completionHandler(txAux: TxAux?, errStr: String) {
                        if (txAux != null) {
                            val estimateFee = (txAux.encode().size * minerFee + minFee) / ADACoin
                            completionHandler.completionHandler(estimateFee, "")
                        } else {
                            completionHandler.completionHandler(0.0, errStr)
                        }
                    }
                })
    }

    private data class MapKeys(val priKey: ACTPrivateKey, val address: String)

    fun sendCoin(
        prvKeys: Array<ACTPrivateKey>,
        unspentAddresses: Array<String>,
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        completionHandler: ADASendCoinHandle
    ) {
        createTxAux(prvKeys,
            unspentAddresses,
            fromAddress,
            toAddressStr,
            serAddressStr,
            amount,
            networkFee,
            serviceFee,
            object : ADACreateTxAuxHandle {
                override fun completionHandler(txAux: TxAux?, errStr: String) {
                    if (txAux != null) {
                            sendTxAux(txAux.base64(), txAux.tx.getID(), object :
                                ADASendTxAuxHandle {
                                override fun completionHandler(transID: String, success: Boolean, errStr: String) {
                                    completionHandler.completionHandler(transID, success, errStr)
                                }
                            })
                    } else {
                        completionHandler.completionHandler("", false, errStr)
                    }
                }
            })
    }

    fun createTxAux(
        prvKeys: Array<ACTPrivateKey>,
        unspentAddresses: Array<String>,
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double = 0.0,
        networkFee: Double = 0.0,
        serviceFee: Double = 0.0,
        completionHandler: ADACreateTxAuxHandle
    ) {
        addressUsed(unspentAddresses, completionHandler = object : ADAAddressUsedHandle {
            override fun completionHandler(addressUsed: Array<String>, err: Throwable?) {
                if (err == null) {
                    unspentOutputs(addressUsed, object : ADAUnspentOutputsHandle {
                        override fun completionHandler(
                                unspentOutputs: Array<ADAUnspentTransaction>,
                                err: Throwable?
                        ) {
                            if (err == null) {
                                var mapKeys = arrayOf<MapKeys>()
                                for (i in prvKeys.indices) {
                                    if (addressUsed.contains(unspentAddresses[i])) {
                                        mapKeys = mapKeys.plus(MapKeys(prvKeys[i], unspentAddresses[i]))
                                    }
                                }

                                var prvKeyBytes = arrayOf<ByteArray>()
                                var chainCodes = arrayOf<ByteArray>()
                                var walletServiceFee = when (CarAddress.isValidAddress(serAddressStr)) {
                                    true -> floor(serviceFee)
                                    false -> 0.0
                                }
                                if (walletServiceFee > 0 && walletServiceFee < YOROIAPI.MIN_AMOUNT_PER_TX) {
                                    walletServiceFee = YOROIAPI.MIN_AMOUNT_PER_TX
                                }

                                var netFee = ceil(networkFee * ADACoin)
                                // Network fee always > 0
                                if (netFee <= 0) {
                                    netFee = 0.14 * ADACoin
                                }
                                var availableAmount = floor(amount)
                                val totalAmount = availableAmount + netFee + walletServiceFee
                                var spentCoins = 0.0

                                val tx = Tx()

                                unspentOutputs.forEach {
                                    if (it.amount > 0 && (spentCoins < totalAmount)) {
                                        spentCoins += it.amount
//                                        Log.d("TEST_TX", it.transationHash)
//                                        Log.d("TEST_TX", it.transactionIdx.toString())
                                        val input = TxoPointer(it.transationHash, it.transactionIdx.toLong())
                                        val add = it.receiver

                                        val keys = mapKeys.first { item -> item.address == add }
                                        prvKeyBytes = prvKeyBytes.plus(keys.priKey.raw!!)
                                        chainCodes = chainCodes.plus(keys.priKey.chainCode!!)
                                        tx.addInput(input)
                                    }
                                }

                                var change = (spentCoins - totalAmount).toLong()
                                // minimum_utxo_val
                                if (change < 0) {
                                    availableAmount += change
                                    change = 0
                                }
                                if (change >= YOROIAPI.MIN_AMOUNT_PER_TX) {
                                    val out1 = TxOut(fromAddress.rawAddressString(), change)
                                    tx.addOutput(out1)
                                } else {
                                    change = 0
                                }

                                val out2 = TxOut(toAddressStr, availableAmount.toLong())
                                tx.addOutput(out2)

                                if (walletServiceFee > 0) {
                                    val out3 = TxOut(serAddressStr, walletServiceFee.toLong())
                                    tx.addOutput(out3)
                                }

                                var fee = spentCoins - (availableAmount + walletServiceFee + change)
                                // Network fee always > 0
                                if (fee <= 0) {
                                    fee = 0.14 * ADACoin
                                }
                                tx.setFee(fee.toLong())
                                tx.setTtl(getTimeSlot())

                                val txId = tx.getID()

//                                Log.d("TEST_TX", txId)
                                val inWitnesses = TxWitnessBuilder.builder(txId, prvKeyBytes, chainCodes)
                                val witnessSet = TransactionWitnessSet(inWitnesses)
                                val txAux = TxAux(tx, witnessSet)

                                completionHandler.completionHandler(txAux, "")
                            } else {
                                completionHandler.completionHandler(null, err!!.localizedMessage)
                            }
                        }
                    })
                } else {
                    completionHandler.completionHandler(null, err!!.localizedMessage)
                }
            }
        })
    }

    fun unspentOutputs(
        addresses: Array<String>,
        completionHandler: ADAUnspentOutputsHandle
    ) {
        val params = JsonObject()
        params.add("addresses", addresses.toJsonArray())
        val call = apiService.unspentOutputs(params)
        call.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                val body = response.body()
                if ((body != null) && (body.isJsonArray)) {
                    completionHandler.completionHandler(ADAUnspentTransaction.parser(body), null)
                } else {
                    completionHandler.completionHandler(arrayOf(), null)
                }
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                completionHandler.completionHandler(arrayOf(), t)
            }
        })
    }

    fun addressUsed(
        addresses: Array<String>,
        skip: Boolean = false,
        completionHandler: ADAAddressUsedHandle
    ) {
        when (skip) {
            true -> {
                completionHandler.completionHandler(addresses, null)
            }
            false -> {
                val params = JsonObject()
                params.add("addresses", addresses.toJsonArray())
                val call = apiService.addressUsed(params)
                call.enqueue(object : Callback<JsonElement> {
                    override fun onResponse(
                        call: Call<JsonElement>,
                        response: Response<JsonElement>
                    ) {
                        val body = response.body()
                        if ((body != null) && (body.isJsonArray)) {
                            val items = body.asJsonArray.map { it.asString }
                            completionHandler.completionHandler(items.toTypedArray(), null)
                        } else {
                            completionHandler.completionHandler(arrayOf(), null)
                        }
                    }

                    override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                        completionHandler.completionHandler(arrayOf(), t)
                    }
                })
            }
        }
    }

    fun getTimeSlot(): Long {
        var slotDuration: Long = 20
        var slotsPerEpoch: Long = 21600
        var slotCount: Long = 0
        val genesisDate: Long = 1506203091000
        val numEpochs = 208 - 0
        val defaultTtlOffset = 7200

        val currentDateTime = Date()
        var timeLeftToTip = currentDateTime.time - genesisDate

        slotCount += slotsPerEpoch * numEpochs;
        timeLeftToTip -= (slotsPerEpoch * slotDuration * 1000) * numEpochs

        slotDuration = 1
        slotsPerEpoch = 43200
        val secondsSinceLastUpdate = timeLeftToTip / 1000;
        slotCount += (secondsSinceLastUpdate / slotDuration)
        slotCount += defaultTtlOffset
        return slotCount
    }

    fun sendTxAux(
        signedTx: String,
        txId: String,
        completionHandler: ADASendTxAuxHandle
    ) {
        val params = JsonObject()
        params.addProperty("signedTx", signedTx)
        val call = apiService.sendTxAux(params)
        call.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                Log.d("SENDING_ADA", response.body().toString())
                val errBody = response.errorBody()
                if ((errBody != null)) {
                    val js = JSONObject(errBody.string())
                    val msg = when (js.has("message")) {
                        true -> js.get("message").toString()
                        false -> js.toString()
                    }
                    completionHandler.completionHandler(txId, false, "$msg - Tx: $signedTx - TxID: $txId")
                } else {
                    completionHandler.completionHandler(txId, true, "")
                }
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                completionHandler.completionHandler("", false, t.localizedMessage)
            }
        })
    }

    fun bestblock(completionHandler: ADACurrentBlockHandle) {
        val call = apiService.bestblock()
        call.enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                val errBody = response.errorBody()
                if ((errBody != null)) {
                    val js = JSONObject(errBody.string())
                    val msg = when (js.has("message")) {
                        true -> js.get("message").toString()
                        false -> "ERROR"
                    }
                    completionHandler.completionHandler(null, msg)
                } else {
                    val gson = Gson()
                    currentBestBlock = gson.fromJson(
                        response.body().toString(),
                        CardanoCurrentBestBlock::class.java
                    )
                    completionHandler.completionHandler(currentBestBlock, "")
                }
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                completionHandler.completionHandler(null, t.localizedMessage)
            }
        })
    }
}

private fun Array<String>.toJsonArray(): JsonArray {
    val arrJson = JsonArray()
    forEach { arrJson.add(it) }
    return arrJson
}