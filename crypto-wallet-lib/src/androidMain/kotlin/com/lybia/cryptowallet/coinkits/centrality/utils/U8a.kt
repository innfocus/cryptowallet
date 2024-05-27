package com.lybia.cryptowallet.coinkits.centrality.utils

import java.math.BigInteger
import kotlin.math.ceil

object U8a {
    private var MAX_U8 = 2.toBigInteger().pow(8 - 2) - 1.toBigInteger()
    private var MAX_U16 = 2.toBigInteger().pow(16 - 2) - 1.toBigInteger()
    private var MAX_U32 = 2.toBigInteger().pow(32 - 2) - 1.toBigInteger()

    fun compactAddLength(input: ByteArray): ByteArray {
        val length = input.size
        var rs = compactToU8a(length.toBigInteger())
        rs += input
        return rs
    }

    fun toArrayLikeLE(value: BigInteger, byteLength: Int = -1): ByteArray {
        var length = byteLength
        if (length == -1) {
            length = ceil(value.bitLength().toDouble() / 8.0).toInt()
        }
        var q = value
        val res = ByteArray(length)
        var i = 0
        while (q != 0.toBigInteger()) {
            val b = q.and(0xff.toBigInteger())
            q = q shr (8)
            res[i] = b.toByte()
            i += 1
        }
        while (i < length) {
            res[i] = 0
            i += 1
        }
        return res
    }

    fun compactToU8a(value: BigInteger): ByteArray {
        when {
            value <= MAX_U8 -> {
                val result = ByteArray(1)
                result[0] = (value shl 2).toByte()
                return result
            }
            value <= MAX_U16 -> {
                var re = value
                re = re shl 2
                re += 1.toBigInteger()
                return toArrayLikeLE(re, 2)
            }
            value <= MAX_U32 -> {
                var re = value
                re = re shl 2
                re += 2.toBigInteger()
                return toArrayLikeLE(re, 4)
            }
            else -> {
                val u8a = toArrayLikeLE(value)
                var length = u8a.size
                while (u8a[length - 1] == 0.toByte()) {
                    length -= 1
                }

                var result = ByteArray(1)
                result[0] = (((length - 4) shl 2) + 0b11).toByte()
                result += u8a.slice(0 until length)
                return result
            }
        }
    }
}
