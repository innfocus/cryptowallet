package com.lybia.cryptowallet.utils

actual class Utils {
    actual companion object {
        actual fun convertHexStringToDouble(hex: String?, decimals: Int?): Double {
            if(hex == null) return 0.0
            val convertHex = hex.replace("0x", "")
            return convertHex.toLong(16).toDouble()
        }

        actual fun convertDoubleToHexString(
            value: Double,
            decimals: Int?
        ): String {
            return "0x" + value.toLong().toString(16)
        }
    }
}