package com.lybia.cryptowallet

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** Returns the current epoch time in seconds. KMP-compatible. */
expect fun currentEpochSeconds(): Long