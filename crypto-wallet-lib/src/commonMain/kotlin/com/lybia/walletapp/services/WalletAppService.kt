package com.lybia.walletapp.services

import com.lybia.cryptowallet.services.HttpClientService
import com.lybia.walletapp.requests.SendEmailVerificationRequest
import com.lybia.walletapp.responses.VerificationResponse
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WalletAppService(private val serverUrl: String) {
    private val client = HttpClientService.INSTANCE.client

    suspend fun sendOtp(email: String): VerificationResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v2/verifications") {
            contentType(ContentType.Application.Json)
            setBody(SendEmailVerificationRequest(email))
        }

        val data = response.body<VerificationResponse>()
        return data
    }
}