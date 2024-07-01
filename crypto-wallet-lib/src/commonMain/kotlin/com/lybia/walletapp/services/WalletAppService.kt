package com.lybia.walletapp.services

import com.lybia.cryptowallet.services.HttpClientService
import com.lybia.walletapp.requests.CheckEmailVerificationRequest
import com.lybia.walletapp.requests.SendConsultationRequest
import com.lybia.walletapp.requests.SendEmailVerificationRequest
import com.lybia.walletapp.responses.BaseApiResponse
import com.lybia.walletapp.responses.CheckEmailVerificationResponse
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WalletAppService(private val serverUrl: String) {
    private val client = HttpClientService.INSTANCE.client

    suspend fun sendOtp(email: String): BaseApiResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v2/verifications") {
            contentType(ContentType.Application.Json)
            setBody(SendEmailVerificationRequest(email))
        }

        val data = response.body<BaseApiResponse>()
        return data
    }

    suspend fun verifyOtp(email: String, code: String): CheckEmailVerificationResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v2/verifications/verify") {
            contentType(ContentType.Application.Json)
            setBody(CheckEmailVerificationRequest(email, code))
        }

        val data = response.body<CheckEmailVerificationResponse>()
        return data
    }

    suspend fun sendConsultation(
        inquiry: String,
        email: String?, name: String?
    ): BaseApiResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v1/consultations") {
            contentType(ContentType.Application.Json)
            setBody(SendConsultationRequest(inquiry, email, name))
        }

        val data = response.body<BaseApiResponse>()
        return data
    }
}