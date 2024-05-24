package com.lybia.cryptowallet.utils

expect class Utils {
    companion object {
        fun convertHexStringToDouble(hex: String?, decimals: Int?): Double

        fun convertDoubleToHexString(value: Double, decimals: Int?): String
    }
}