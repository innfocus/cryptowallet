package com.lybia.cryptowallet.coinkits.ripple.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

class XRPAccountInfo(
    @SerializedName("ledger_index")
    val ledgerIndex             : Int,
    @SerializedName("account_data")
    private val _accountData    : JsonElement){

    private var accountDataTmp   : AccountData? = null
            var accountData     get()       = accountDataTmp ?: AccountData.parser(_accountData)
                                set(value)  {accountDataTmp = value}

    companion object {
        fun parser(json: JsonElement): XRPAccountInfo? {
            return try {
                Gson().fromJson(json, XRPAccountInfo::class.java)
            }catch (e: JsonSyntaxException) {
                null
            }
        }
    }
}

class AccountData (
    @SerializedName("Account")
    val account     : String = "",
    @SerializedName("Balance")
    val balance     : String = "",
    @SerializedName("Sequence")
    val sequence    : Int = 0) {
    companion object {
        fun parser(json: JsonElement): AccountData {
            return try {
                Gson().fromJson(json, AccountData::class.java)
            }catch (e: JsonSyntaxException) {
                AccountData()
            }
        }
    }
}