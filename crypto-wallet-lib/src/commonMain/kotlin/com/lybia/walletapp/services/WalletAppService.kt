package com.lybia.walletapp.services

import com.lybia.cryptowallet.services.HttpClientService
import com.lybia.walletapp.exceptions.RequestException
import com.lybia.walletapp.requests.CheckEmailVerificationRequest
import com.lybia.walletapp.requests.SendConsultationRequest
import com.lybia.walletapp.requests.SendEmailVerificationRequest
import com.lybia.walletapp.responses.BaseApiResponse
import com.lybia.walletapp.responses.CheckEmailVerificationResponse
import com.lybia.walletapp.responses.UserResponse
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class WalletAppService(
    private val serverUrl: String,
    private var authToken: String?
) {
    private val client = HttpClientService.INSTANCE.client

    // Private helper function to add Authorization header if authToken exists
    private fun HttpRequestBuilder.addAuthorizationHeader() {
        authToken?.let { token ->
            header("Authorization", "Bearer $token")
        }
    }

    suspend fun sendOtp(email: String): BaseApiResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v2/verifications") {
            contentType(ContentType.Application.Json)
            setBody(SendEmailVerificationRequest(email))
        }

        if (response.status != HttpStatusCode.OK)
            throw RequestException(response.status.value, response.body<BaseApiResponse>().message)

        val data = response.body<BaseApiResponse>()
        return data
    }

    suspend fun verifyOtp(email: String, code: String): CheckEmailVerificationResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v2/verifications/verify") {
            contentType(ContentType.Application.Json)
            setBody(CheckEmailVerificationRequest(email, code))
        }

        if (response.status != HttpStatusCode.OK)
            throw RequestException(response.status.value, response.body<BaseApiResponse>().message)

        val data = response.body<CheckEmailVerificationResponse>()
        return data
    }

    suspend fun getProfile(): UserResponse {
        val response: HttpResponse = client.get("$serverUrl/api/v1/users/me") {
            addAuthorizationHeader()
            contentType(ContentType.Application.Json)
        }

        if(response.status != HttpStatusCode.OK)
            throw RequestException(response.status.value, response.body<BaseApiResponse>().message)

        val data = response.body<UserResponse>()
        return data
    }

    suspend fun sendConsultation(
        inquiry: String,
        email: String?, name: String?
    ): BaseApiResponse {
        val response: HttpResponse = client.post("$serverUrl/api/v1/consultations") {
            addAuthorizationHeader()
            contentType(ContentType.Application.Json)
            setBody(SendConsultationRequest(inquiry, email, name))
        }

        val data = response.body<BaseApiResponse>()
        return data
    }
}