package com.lybia.cryptowallet.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json


actual class HttpClientService {
    actual val client: HttpClient
        get() = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(Logging){
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }
            install(HttpTimeout){
                connectTimeoutMillis = 15000
            }
        }

    actual companion object{
        actual val INSTANCE: HttpClientService = HttpClientService()
    }


}