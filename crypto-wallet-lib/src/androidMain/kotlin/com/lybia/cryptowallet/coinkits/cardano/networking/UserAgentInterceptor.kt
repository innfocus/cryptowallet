package com.lybia.cryptowallet.coinkits.cardano.networking

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class UserAgentInterceptor : Interceptor {

    companion object {
        private const val userAgent: String = "emurgo/58 CFNetwork/978.0.7 Darwin/18.7.0"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        val requestWithUserAgent: Request = originalRequest.newBuilder()
//                .header("User-Agent", "User-Agent": "emurgo/58 CFNetwork/978.0.7 Darwin/18.7.0")
                .header("yoroi-version", "android / 4.0.0")
                .header("tangata-manu", "yoroi")
                .build()
        return chain.proceed(requestWithUserAgent)
    }
}