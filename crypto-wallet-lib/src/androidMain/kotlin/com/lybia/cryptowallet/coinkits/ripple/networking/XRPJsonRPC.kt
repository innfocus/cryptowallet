package com.lybia.cryptowallet.coinkits.ripple.networking

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lybia.cryptowallet.coinkits.ripple.model.XRPAccountInfo
import com.lybia.cryptowallet.coinkits.ripple.model.XRPSubmitResponse
import com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple.ACTClient
import com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple.ACTJsonRPCRequest
import com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple.RPCJSONHandle
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toHexString

private class XRPJsonRPCServer {
    companion object {
        const val mainnet  = "https://s1.ripple.com:51234"
        const val testnet  = "https://s.altnet.rippletest.net:51234"
    }
}

interface XRPAccountInfoHandle  {fun completionHandler(accInfo  : XRPAccountInfo?, err: Throwable?)}
interface XRPSubmitHandle       {fun completionHandler(submitRes: XRPSubmitResponse?, err: Throwable?)}

class XRPJsonRPC{

    val client: ACTClient

    constructor(testNet: Boolean) {
        val nodeEndpoint    = if (testNet) XRPJsonRPCServer.testnet else XRPJsonRPCServer.mainnet
        this.client         = ACTClient(nodeEndpoint)
    }

    fun getAccountInfo(address              : String,
                       completionHandler    : XRPAccountInfoHandle
    ) {
        val r = GetAccountInfo(address, true, "validated")
        client.send(r, object : RPCJSONHandle<XRPAccountInfo> {
            override fun completionHandler(response: XRPAccountInfo?, err: Throwable?) {
                completionHandler.completionHandler(response, err)
            }
        })
    }

    fun submit(txBlob           : ByteArray,
            completionHandler   : XRPSubmitHandle
    ) {
        val r = Submit(txBlob)
        client.send(r, object : RPCJSONHandle<XRPSubmitResponse> {
            override fun completionHandler(response: XRPSubmitResponse?, err: Throwable?) {
                completionHandler.completionHandler(response, err)
            }
        })
    }
}

private class GetAccountInfo: ACTJsonRPCRequest<XRPAccountInfo> {
    override var method: String = "account_info"
    override var parameters: JsonObject? = JsonObject()
    constructor(account       : String,
                strict        : Boolean,
                ledgerIndex   : String) {
        parameters!!.addProperty("account", account)
        parameters!!.addProperty("strict", strict)
        parameters!!.addProperty("ledger_index", ledgerIndex)
    }
    override fun response(resultObject: JsonElement): XRPAccountInfo? {
        return XRPAccountInfo.parser(resultObject)
    }
}

private class Submit: ACTJsonRPCRequest<XRPSubmitResponse> {
    override var method     : String = "submit"
    override var parameters : JsonObject? = JsonObject()
    constructor(tx_blob: ByteArray) {
        parameters!!.addProperty("tx_blob", tx_blob.toHexString())
    }
    override fun response(resultObject: JsonElement): XRPSubmitResponse? {
        return XRPSubmitResponse.parser(resultObject)
    }
}