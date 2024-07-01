package com.lybia.walletapp.exceptions

class RequestException(val code: Int, message: String) : Exception(message) {
    override val message: String
        get() = super.message ?: ""

    override fun toString(): String {
        return "RequestException(code=$code, message=$message)"
    }

}