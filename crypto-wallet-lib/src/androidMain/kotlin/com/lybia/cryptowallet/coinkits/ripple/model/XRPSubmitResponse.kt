package com.lybia.cryptowallet.coinkits.ripple.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

class XRPSubmitResponse(
    @SerializedName("engine_result")
    val engineResult        : String = "",
    @SerializedName("engine_result_code")
    val engineResultCode    : Int = -1,
    @SerializedName("engine_result_message")
    val engineResultMessage : String = ""
) {
    companion object {
        fun parser(json: JsonElement): XRPSubmitResponse {
            return try {
                Gson().fromJson(json, XRPSubmitResponse::class.java)
            }catch (e: JsonSyntaxException) {
                XRPSubmitResponse()
            }
        }
    }
}