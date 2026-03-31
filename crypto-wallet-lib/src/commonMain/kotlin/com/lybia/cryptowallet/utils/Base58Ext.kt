package com.lybia.cryptowallet.utils

/**
 * Base58 encoding/decoding with support for Basic and Ripple alphabets.
 * Migrated from androidMain — replaces java.util.Arrays dependency.
 */
object Base58Ext {

    enum class Base58Type {
        Basic,
        Ripple;

        fun alphabet(): CharArray = when (this) {
            Basic -> "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
            Ripple -> "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz".toCharArray()
        }

        fun encodedZero(): Char = alphabet()[0]

        fun indexes(): IntArray {
            val rs = IntArray(128) { -1 }
            alphabet().forEachIndexed { i, c -> rs[c.code] = i }
            return rs
        }
    }

    fun encode(input: ByteArray, type: Base58Type = Base58Type.Basic): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) ++zeros
        val data = input.copyOf()
        val encoded = CharArray(data.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        while (inputStart < data.size) {
            encoded[--outputStart] = type.alphabet()[divmod(data, inputStart, 256, 58).toInt()]
            if (data[inputStart].toInt() == 0) ++inputStart
        }
        while (outputStart < encoded.size && encoded[outputStart] == type.encodedZero()) ++outputStart
        repeat(zeros) { encoded[--outputStart] = type.encodedZero() }
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String, type: Base58Type = Base58Type.Basic): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val indexes = type.indexes()
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) indexes[c.code] else -1
            if (digit < 0) return ByteArray(0)
            input58[i] = digit.toByte()
        }
        var zeros = 0
        while (zeros < input58.size && input58[zeros].toInt() == 0) ++zeros
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart].toInt() == 0) ++inputStart
        }
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) ++outputStart
        return decoded.copyOfRange(outputStart - zeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
