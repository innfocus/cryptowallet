package com.lybia.cryptowallet.services

import io.ktor.client.HttpClient

expect class HttpClientService {
    val client: HttpClient

    companion object{
        val INSTANCE: HttpClientService
    }
}