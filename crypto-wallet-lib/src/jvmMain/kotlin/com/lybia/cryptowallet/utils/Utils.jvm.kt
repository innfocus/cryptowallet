package com.lybia.cryptowallet.utils

import kotlin.math.pow


actual class Utils {
    actual companion object {
        actual fun convertHexStringToDouble(hex: String?, decimals: Int?): Double {
            if(hex == null) return 0.0
            val convertHex = hex.replace("0x", "")
            return convertHex.toLong(16).toDouble().div(10.0.pow(decimals?.toDouble() ?: 0.0))
        }

        actual fun convertDoubleToHexString(
            value: Double,
            decimals: Int?
        ): String {
            return "0x" + (value * 10.0.pow(decimals?.toDouble() ?: 0.0)).toLong().toString(16)
        }
    }
}
