package com.lybia.cryptowallet

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform