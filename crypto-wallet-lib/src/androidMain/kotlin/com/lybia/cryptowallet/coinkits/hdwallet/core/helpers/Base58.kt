package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers

import java.util.Arrays


object Base58 {
    @JvmOverloads
    fun encode(input: ByteArray, type: Base58Type = Base58Type.Basic): String {
        var input = input
        if (input.size == 0) {
            return ""
        }
        // Count leading zeros.
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) {
            ++zeros
        }
        // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        input = input.copyOf(input.size) // since we modify it in-place
        val encoded = CharArray(input.size * 2) // upper bound
        var outputStart = encoded.size
        var inputStart = zeros
        while (inputStart < input.size) {
            encoded[--outputStart] = type.ALPHABET()[divmod(input, inputStart, 256, 58).toInt()]
            if (input[inputStart].toInt() == 0) {
                ++inputStart // optimization - skip leading zeros
            }
        }
        // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
        while (outputStart < encoded.size && encoded[outputStart] == type.ENCODED_ZERO()) {
            ++outputStart
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = type.ENCODED_ZERO()
        }
        // Return encoded string (including encoded leading zeros).
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    @JvmOverloads
    fun decode(input: String, type: Base58Type = Base58Type.Basic): ByteArray {
        if (input.length == 0) {
            return ByteArray(0)
        }
        // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
        val input58 = ByteArray(input.length)
        for (i in 0 until input.length) {
            val c = input[i]
            val digit = if (c.code < 128) type.INDEXES()[c.code] else -1
            if (digit < 0) {
                return ByteArray(0)
            }
            input58[i] = digit.toByte()
        }
        // Count leading zeros.
        var zeros = 0
        while (zeros < input58.size && input58[zeros].toInt() == 0) {
            ++zeros
        }
        // Convert base-58 digits to base-256 digits.
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart].toInt() == 0) {
                ++inputStart // optimization - skip leading zeros
            }
        }
        // Ignore extra leading zeroes that were added during the calculation.
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
            ++outputStart
        }
        // Return decoded data (including original number of leading zeros).
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        // this is just long division which accounts for the base of the input digits
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }

    enum class Base58Type {
        Basic,
        Ripple;

        fun ALPHABET(): CharArray {
            val rs = CharArray(128)
            return when (this) {
                Basic -> "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
                Ripple -> "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz".toCharArray()
            }
            return rs
        }

        fun ENCODED_ZERO(): Char {
            return ALPHABET()[0]
        }

        fun INDEXES(): IntArray {
            val rs = IntArray(128)
            Arrays.fill(rs, -1)
            for (i in ALPHABET().indices) {
                rs[ALPHABET()[i].code] = i
            }
            return rs
        }
    }
}
