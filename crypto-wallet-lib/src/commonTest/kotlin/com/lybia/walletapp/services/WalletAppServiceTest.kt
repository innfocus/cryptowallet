package com.lybia.walletapp.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

class WalletAppServiceTest {
    private val mockEngine = MockEngine { request ->
        when (request.url.fullPath) {
            "/api/v2/verifications" -> {
                respond(
                    content = """
                        {
                            "success": true,
                            "message": "Success",
                            "code": 200
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                )
            }
            else -> error("Unhandled ${request.url.fullPath}")
        }
    }

    private val mockClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
//            serializer = KotlinxSerializer()
        }
    }
}