package com.lybia.cryptowallet.wallets.centrality.codec

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.ceil

/**
 * SCALE (Simple Concatenated Aggregate Little-Endian) codec for Substrate.
 * Replaces U8a from androidMain.
 */
object ScaleCodec {
    private val MAX_U8 = BigInteger(2).pow(6) - BigInteger.ONE      // 63
    private val MAX_U16 = BigInteger(2).pow(14) - BigInteger.ONE     // 16383
    private val MAX_U32 = BigInteger(2).pow(30) - BigInteger.ONE     // 1073741823

    /**
     * Compact SCALE encoding for non-negative integer.
     * - Single-byte mode: value <= 63 -> 1 byte, (value << 2)
     * - Two-byte mode: value <= 16383 -> 2 bytes LE, (value << 2) + 1
     * - Four-byte mode: value <= 1073741823 -> 4 bytes LE, (value << 2) + 2
     * - Big-integer mode: value > 1073741823 -> length prefix + LE bytes
     */
    fun compactToU8a(value: BigInteger): ByteArray {
        return when {
            value <= MAX_U8 -> {
                val result = ByteArray(1)
                result[0] = value.shl(2).intValue().toByte()
                result
            }
            value <= MAX_U16 -> {
                var re = value.shl(2) + BigInteger.ONE
                toArrayLikeLE(re, 2)
            }
            value <= MAX_U32 -> {
                var re = value.shl(2) + BigInteger(2)
                toArrayLikeLE(re, 4)
            }
            else -> {
                val u8a = toArrayLikeLE(value)
                var length = u8a.size
                while (u8a[length - 1] == 0.toByte()) {
                    length -= 1
                }
                var result = ByteArray(1)
                result[0] = (((length - 4) shl 2) + 0b11).toByte()
                result + u8a.sliceArray(0 until length)
            }
        }
    }

    /**
     * Encode BigInteger to little-endian byte array with specified length.
     */
    fun toArrayLikeLE(value: BigInteger, byteLength: Int = -1): ByteArray {
        val length = if (byteLength == -1) {
            ceil(value.bitLength().toDouble() / 8.0).toInt()
        } else {
            byteLength
        }
        var q = value
        val res = ByteArray(length)
        var i = 0
        while (q != BigInteger.ZERO) {
            val b = q.and(BigInteger(0xff))
            q = q.shr(8)
            res[i] = b.intValue().toByte()
            i += 1
        }
        while (i < length) {
            res[i] = 0
            i += 1
        }
        return res
    }

    /**
     * Prepend compact-encoded length before input byte array.
     */
    fun compactAddLength(input: ByteArray): ByteArray {
        val length = input.size
        val prefix = compactToU8a(BigInteger(length))
        return prefix + input
    }

    /**
     * Decode compact SCALE encoded bytes back to BigInteger.
     * Returns a Pair of (decoded value, number of bytes consumed).
     */
    fun compactFromU8a(input: ByteArray): Pair<BigInteger, Int> {
        require(input.isNotEmpty()) { "Input must not be empty" }
        val mode = input[0].toInt() and 0b11
        return when (mode) {
            0b00 -> {
                // Single-byte mode
                val value = BigInteger((input[0].toInt() and 0xff) shr 2)
                Pair(value, 1)
            }
            0b01 -> {
                // Two-byte mode
                require(input.size >= 2) { "Two-byte mode requires at least 2 bytes" }
                val raw = (input[0].toInt() and 0xff) or ((input[1].toInt() and 0xff) shl 8)
                val value = BigInteger(raw shr 2)
                Pair(value, 2)
            }
            0b10 -> {
                // Four-byte mode
                require(input.size >= 4) { "Four-byte mode requires at least 4 bytes" }
                val raw = (input[0].toInt() and 0xff).toLong() or
                    ((input[1].toInt() and 0xff).toLong() shl 8) or
                    ((input[2].toInt() and 0xff).toLong() shl 16) or
                    ((input[3].toInt() and 0xff).toLong() shl 24)
                val value = BigInteger(raw shr 2)
                Pair(value, 4)
            }
            0b11 -> {
                // Big-integer mode
                val byteLen = ((input[0].toInt() and 0xff) shr 2) + 4
                require(input.size >= 1 + byteLen) { "Big-integer mode requires at least ${1 + byteLen} bytes" }
                var value = BigInteger.ZERO
                for (i in 0 until byteLen) {
                    val byteVal = input[1 + i].toInt() and 0xff
                    if (byteVal != 0) {
                        value = value + (BigInteger(byteVal).shl(8 * i))
                    }
                }
                Pair(value, 1 + byteLen)
            }
            else -> error("Unreachable")
        }
    }
}
