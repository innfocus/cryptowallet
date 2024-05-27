package com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple

import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ACTBatchElement<T> {
    val body : JsonObject = JsonObject()

    constructor(request   : ACTJsonRPCRequest<T>,
                version   : String,
                id        : Int){
        body.addProperty("jsonrpc"  , version)
        body.addProperty("method"   , request.method)
        body.addProperty("id"       , id)
        if (request.parameters != null) {
            val params = JsonArray()
            params.add(request.parameters!!)
            body.add("params", params)
        }
    }
}