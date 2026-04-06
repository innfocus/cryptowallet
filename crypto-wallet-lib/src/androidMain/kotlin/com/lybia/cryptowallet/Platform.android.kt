package com.lybia.cryptowallet

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000