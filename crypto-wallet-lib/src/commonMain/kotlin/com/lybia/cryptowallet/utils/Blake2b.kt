package com.lybia.cryptowallet.utils

/**
 * Pure Kotlin Blake2b implementation compatible with KMP.
 * Supports variable output length (1-64 bytes).
 * Reference: RFC 7693
 */
object Blake2b {
    private val IV = ulongArrayOf(
        0x6A09E667F3BCC908uL, 0xBB67AE8584CAA73BuL,
        0x3C6EF372FE94F82BuL, 0xA54FF53A5F1D36F1uL,
        0x510E527FADE682D1uL, 0x9B05688C2B3E6C1FuL,
        0x1F83D9ABFB41BD6BuL, 0x5BE0CD19137E2179uL
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    private fun rotr64(x: ULong, n: Int): ULong = (x shr n) or (x shl (64 - n))

    private fun g(v: ULongArray, a: Int, b: Int, c: Int, d: Int, x: ULong, y: ULong) {
        v[a] = v[a] + v[b] + x
        v[d] = rotr64(v[d] xor v[a], 32)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 24)
        v[a] = v[a] + v[b] + y
        v[d] = rotr64(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 63)
    }

    private fun littleEndianLong(buf: ByteArray, offset: Int): ULong {
        var v = 0uL
        for (i in 0..7) {
            v = v or ((buf[offset + i].toULong() and 0xFFuL) shl (i * 8))
        }
        return v
    }

    private fun compress(h: ULongArray, block: ByteArray, offset: Int, t: ULong, last: Boolean) {
        val v = ULongArray(16)
        for (i in 0..7) v[i] = h[i]
        for (i in 0..7) v[8 + i] = IV[i]
        v[12] = v[12] xor t
        v[13] = v[13] xor 0uL // upper 64 bits of counter (not needed for < 2^64 bytes)
        if (last) v[14] = v[14] xor ULong.MAX_VALUE

        val m = ULongArray(16)
        for (i in 0..15) m[i] = littleEndianLong(block, offset + i * 8)

        for (round in 0..11) {
            val s = SIGMA[round % 10]
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }

        for (i in 0..7) h[i] = h[i] xor v[i] xor v[i + 8]
    }

    /**
     * Compute Blake2b hash of [input] with [outputLength] bytes (1-64).
     */
    fun hash(input: ByteArray, outputLength: Int = 32): ByteArray {
        require(outputLength in 1..64) { "Output length must be 1-64" }

        val h = ULongArray(8)
        for (i in 0..7) h[i] = IV[i]
        // Parameter block: fanout=1, depth=1, digest length
        h[0] = h[0] xor (0x01010000uL or outputLength.toULong())

        val blockSize = 128
        var bytesCompressed = 0uL
        var offset = 0
        val remaining = input.size

        // Process full blocks
        while (remaining - offset > blockSize) {
            bytesCompressed += blockSize.toULong()
            compress(h, input, offset, bytesCompressed, false)
            offset += blockSize
        }

        // Final block (padded with zeros)
        val lastBlock = ByteArray(blockSize)
        val lastLen = remaining - offset
        input.copyInto(lastBlock, 0, offset, offset + lastLen)
        bytesCompressed += lastLen.toULong()
        compress(h, lastBlock, 0, bytesCompressed, true)

        // Extract output
        val result = ByteArray(outputLength)
        for (i in 0 until outputLength) {
            result[i] = (h[i / 8] shr (8 * (i % 8))).toByte()
        }
        return result
    }
}
