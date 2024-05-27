package com.lybia.cryptowallet.coinkits.ripple.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

class XRPTransaction (
    @SerializedName("marker")
    val marker                  : JsonObject?,
    @SerializedName("limit")
    val count                   : Int   = 0,
    @SerializedName("status")
    val result                  : String   = "",
    @SerializedName("transactions")
    private val _transactions    : JsonElement
){
    private var transactionsTmp  : Array<XRPTransactionItem>? = null

    var transactions    get()           = transactionsTmp ?: XRPTransactionItem.parser(_transactions)
                        set(value)      {transactionsTmp = value}
    companion object {
        fun parser(json: JsonElement): XRPTransaction? {
            return try {
                Gson().fromJson(json, XRPTransaction::class.java)
            }catch (e: JsonSyntaxException) {
                null
            }
        }
    }
}

class XRPTransactionItem(
    @SerializedName("tx")
    private val _tx             : JsonElement,
    @SerializedName("meta")
    private val _meta           : JsonElement
){
    private var txTmp   : XRPTX?    = null
            var tx      get()       = txTmp ?: XRPTX.parser(_tx)
                        set(value)  {txTmp = value}

    private var metaTmp : XRPMeta?  = null
            var meta    get()       = metaTmp ?: XRPMeta.parser(_meta)
                        set(value)  {metaTmp = value}

    companion object {
        fun parser(json: JsonElement): Array<XRPTransactionItem> {
            return try {
                Gson().fromJson(json, Array<XRPTransactionItem>::class.java) ?: emptyArray()
            }catch (e: JsonSyntaxException) {
                emptyArray()
            }
        }
    }
}

class XRPTX(
    @SerializedName("hash")
    val hash                    : String   = "",
        @SerializedName("date")
    val date                    : Long   = 0,
    @SerializedName("TransactionType")
    val transactionType             : String   = "",
    @SerializedName("Amount")
    val amount                      : Float   = 0f,
    @SerializedName("Fee")
    val fee                         : Float   = 0f,
    @SerializedName("Account")
    val account                     : String   = "",
    @SerializedName("Destination")
    val destination                 : String?   = "",
    @SerializedName("DestinationTag")
    val destinationTag              : String   = "",
    @SerializedName("Memos")
    private val _memos              : JsonElement
){
    private var memosTmp   : Array<XRPMemoRes>?    = null
    var memos   get()       = memosTmp ?: XRPMemoRes.parser(_memos)
                set(value)  {memosTmp = value}
    companion object {
        fun parser(json: JsonElement): XRPTX? {
            return try {
                Gson().fromJson(json, XRPTX::class.java)
            }catch (e: JsonSyntaxException) {
                null
            }
        }
    }
}

class XRPMeta(
    @SerializedName("TransactionIndex")
    val index   : UInt   = 0u,
    @SerializedName("TransactionResult")
    val result  : String   = ""
){
    companion object {
        fun parser(json: JsonElement): XRPMeta {
            return try {
                Gson().fromJson(json, XRPMeta::class.java)
            }catch (e: JsonSyntaxException) {
                XRPMeta()
            }
        }
    }
}

class XRPMemoRes {
    @SerializedName("MemoData")
    val memoData : String   = ""
    companion object {
        fun parser(json: JsonElement?): Array<XRPMemoRes> {
            if (json == null) {
                return emptyArray()
            }
            var rs = arrayOf<XRPMemoRes>()
            try {
                if (json.isJsonArray) {
                    val memosJson = json.asJsonArray
                    for (i in 0 until memosJson.count()) {
                        val memoJson = memosJson[i].asJsonObject["Memo"]
                        if (!memoJson.isJsonNull) {
                            rs += Gson().fromJson(memoJson, XRPMemoRes::class.java)
                        }
                    }
                }
            }catch (e: JsonSyntaxException) {}
            return rs
        }
    }
}