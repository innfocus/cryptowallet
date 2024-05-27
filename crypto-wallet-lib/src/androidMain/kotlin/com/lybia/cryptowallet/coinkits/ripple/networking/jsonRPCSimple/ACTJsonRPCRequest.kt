package com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple

import com.google.gson.JsonElement
import com.google.gson.JsonObject

interface ACTJsonRPCRequest<T> {
    var method      : String
    var parameters  : JsonObject?
    fun response(resultObject: JsonElement): T?
}