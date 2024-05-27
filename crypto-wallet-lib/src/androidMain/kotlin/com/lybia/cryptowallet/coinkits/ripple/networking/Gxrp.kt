package com.lybia.cryptowallet.coinkits.ripple.networking

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.coinkits.ripple.model.XRPAccountInfo
import com.lybia.cryptowallet.coinkits.ripple.model.XRPCoin
import com.lybia.cryptowallet.coinkits.ripple.model.XRPSubmitResponse
import com.lybia.cryptowallet.coinkits.ripple.model.XRPTransaction
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.lybia.cryptowallet.coinkits.ripple.model.transaction.XRPMemo
import com.lybia.cryptowallet.coinkits.ripple.model.transaction.XRPTransactionRaw

class XRPAPI {
    companion object {
        const val balance       = "accounts/xxx/balances?currency=XRP"
        const val transactions  = "accounts/xxx/transactions?limit=20&type=Payment"
        const val ledgerServer  = "https://s1.ripple.com:51234/"
        const val ledgerServerTest  = "https://s.altnet.rippletest.net:51234/"
    }
}

private interface IGxrp {
    @POST("/")
    fun query(@Body params: JsonObject): Call<JsonObject>

    @GET
    fun getBalance(@Url url: String): Call<JsonElement>

    @GET("accounts/{address}/transactions")
    fun transactions(@Path("address") address: String, @QueryMap options: Map<String, String>): Call<JsonElement>

    companion object {
        fun create(server: String): IGxrp {
            val retrofit = Retrofit.Builder()
                .baseUrl(server)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(IGxrp::class.java)
        }
    }
}

interface XRPBalanceHandle      {   fun completionHandler(balance: Double, err: Throwable?)}
interface XRPTransactionsHandle {   fun completionHandler(transactions: XRPTransaction?, err: Throwable?)}
interface XRPSubmitTxtHandle    {   fun completionHandler(transID: String, sequence: Int?, success: Boolean, errStr: String)}

class Gxrp {
    companion object {
        val shared = Gxrp()
    }
    private val apiService      = IGxrp.create(XRPAPI.ledgerServer)
    private val apiTestService  = IGxrp.create(XRPAPI.ledgerServerTest)

    private fun getService(): IGxrp {
        val nw = CoinsManager.shared.currentNetwork(ACTCoin.Ripple) ?: return apiService
        return when (nw.isTestNet) {
            true -> apiTestService
            false -> apiService
        }
    }

    fun getBalance(address          : String,
                   completionHandler: XRPBalanceHandle
    )
    {

        val params = JsonArray()

        val account = JsonObject()
        account.addProperty("account", address)

        params.add(account)
        val payload = JsonObject()
        payload.addProperty("method", "account_info")
        payload.add("params", params)

        val call = getService().query(payload)

        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val body =
                        response.body() ?: return completionHandler.completionHandler(0.0, null)
                    if (body.isJsonObject) {
                        val resultJson = body.getAsJsonObject("result")
                        val status = resultJson.get("status").asString
                        return if (status.equals(("success"))) {
                            val accountJson = resultJson.getAsJsonObject("account_data")
                            var balance = accountJson.get("Balance").asDouble
                            balance /= XRPCoin
                            completionHandler.completionHandler(balance, null)
                        } else {
                            completionHandler.completionHandler(0.0, null)
                        }
                    }
                    completionHandler.completionHandler(0.0, null)
                } else {
                    completionHandler.completionHandler(0.0, null)

                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(0.0, t)
            }
        })
    }

    fun getTransactions(
        address: String,
        marker: JsonObject?,
        completionHandler: XRPTransactionsHandle
    ) {
        val params = JsonArray()

        val account = JsonObject()
        account.addProperty("account", address)
        account.addProperty("limit", 100)
        if (marker != null) {
            account.add("marker", marker)
        }
        params.add(account)
        val payload = JsonObject()
        payload.addProperty("method", "account_tx")
        payload.add("params", params)

        val call = getService().query(payload)

        call.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (response.isSuccessful) {
                    val body =
                        response.body() ?: return completionHandler.completionHandler(null, null)
                    if (body.isJsonObject) {
                        val resultJson = body.getAsJsonObject("result")
                        val status = resultJson.get("status").asString
                        return if (status.equals(("success"))) {
                            completionHandler.completionHandler(XRPTransaction.parser(resultJson), null)
                        } else {
                            completionHandler.completionHandler(null, null)
                        }
                    }
                    completionHandler.completionHandler(null, null)
                } else {
                    completionHandler.completionHandler(null, null)

                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                completionHandler.completionHandler(null, t)
            }
        })
    }

    fun sendCoin(prvKey             : ACTPrivateKey,
                 address            : ACTAddress,
                 toAddressStr       : String,
                 amount             : Double,
                 networkFee         : Double,
                 memo               : XRPMemo?  = null,
                 sequence           : Int?      = null,
                 completionHandler  : XRPSubmitTxtHandle
    ) {
        val nw      = prvKey.network
        val account = address.rawAddressString()
        val jsonRPC = XRPJsonRPC(nw.isTestNet)
        jsonRPC.getAccountInfo(account, object : XRPAccountInfoHandle {
            override fun completionHandler(accInfo: XRPAccountInfo?, err: Throwable?) {
                val acc = accInfo ?: return completionHandler.completionHandler("", null, false, err?.localizedMessage ?: "")
                val balance             = acc.accountData.balance.toDoubleOrNull() ?: 0.0
                // Around = 10 unit of XRP
                if (balance < (((amount + nw.coin.minimumAmount()) * XRPCoin) + networkFee)) {
                    return completionHandler.completionHandler("", null, false, "Insufficient Funds")
                }
                val tranRaw             = XRPTransactionRaw()
                tranRaw.account         = account
                tranRaw.destination     = toAddressStr
                tranRaw.sequence        = sequence ?: acc.accountData.sequence
                /* Expire this transaction if it doesn't execute within ~5 minutes: "maxLedgerVersionOffset": 75 */
                tranRaw.lastLedgerSeq   = acc.ledgerIndex + 75
                tranRaw.amount          = amount * XRPCoin
                tranRaw.fee             = networkFee
                tranRaw.memo            = memo
                val signed = tranRaw.sign(prvKey) ?: return completionHandler.completionHandler("", tranRaw.sequence, false, err?.localizedMessage ?: "")
                jsonRPC.submit(signed.txBlob, object : XRPSubmitHandle {
                    override fun completionHandler(submitRes: XRPSubmitResponse?, err: Throwable?) {
                        val res = submitRes ?: return completionHandler.completionHandler("", tranRaw.sequence, false, err?.localizedMessage ?: "")
                        val msg = res.engineResultMessage + "|" + res.engineResult + "|" + res.engineResultCode
                        completionHandler.completionHandler(signed.transactionID, tranRaw.sequence, res.engineResultCode == 0, msg)
                    }
                })
            }
        })
    }
}