package com.lybia.cryptowallet

class JVMPlatform : Platform {
    override val name: String = "JVM ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
