package com.lybia.cryptowallet.utils

/**
 * EIP-55 mixed-case checksum address encoding for Ethereum.
 * Migrated from androidMain — uses KMP SHA3 (Keccak-256).
 */
object ACTEIP55 {
    fun encode(data: ByteArray): String {
        val address = data.toHexString()
        val hashInput = address.encodeToByteArray()
        // EIP-55 uses Keccak-256 (not standard SHA3-256)
        val hash = Keccak.keccak256(hashInput).toHexString()
        return address.mapIndexed { index, hexChar ->
            when {
                hexChar in '0'..'9' -> hexChar
                hash[index] in '0'..'7' -> hexChar.lowercaseChar()
                else -> hexChar.uppercaseChar()
            }
        }.joinToString("")
    }
}

/**
 * Keccak-256 (pre-standard SHA3, used by Ethereum).
 * Differs from NIST SHA3-256 only in the padding byte (0x01 vs 0x06).
 */
object Keccak {
    private val RC = ulongArrayOf(
        0x0000000000000001uL, 0x0000000000008082uL, 0x800000000000808AuL, 0x8000000080008000uL,
        0x000000000000808BuL, 0x0000000080000001uL, 0x8000000080008081uL, 0x8000000000008009uL,
        0x000000000000008AuL, 0x0000000000000088uL, 0x0000000080008009uL, 0x000000008000000AuL,
        0x000000008000808BuL, 0x800000000000008BuL, 0x8000000000008089uL, 0x8000000000008003uL,
        0x8000000000008002uL, 0x8000000000000080uL, 0x000000000000800AuL, 0x800000008000000AuL,
        0x8000000080008081uL, 0x8000000000008080uL, 0x0000000080000001uL, 0x8000000080008008uL
    )

    private val ROTATION_OFFSETS = intArrayOf(
        0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41,
        45, 15, 21, 8, 18, 2, 61, 56, 14
    )

    private val PI_LANE = intArrayOf(
        0, 10, 20, 5, 15, 16, 1, 11, 21, 6, 7, 17, 2, 12, 22, 23,
        8, 18, 3, 13, 14, 24, 9, 19, 4
    )

    private fun rotl64(x: ULong, n: Int): ULong = (x shl n) or (x shr (64 - n))

    private fun keccakF(state: ULongArray) {
        val c = ULongArray(5)
        val d = ULongArray(5)
        for (round in 0..23) {
            for (x in 0..4) c[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            for (x in 0..4) d[x] = c[(x + 4) % 5] xor rotl64(c[(x + 1) % 5], 1)
            for (x in 0..4) for (y in 0..4) state[x + 5 * y] = state[x + 5 * y] xor d[x]
            val temp = ULongArray(25)
            for (i in 0..24) temp[PI_LANE[i]] = rotl64(state[i], ROTATION_OFFSETS[i])
            for (y in 0..4) for (x in 0..4) {
                state[x + 5 * y] = temp[x + 5 * y] xor (temp[(x + 1) % 5 + 5 * y].inv() and temp[(x + 2) % 5 + 5 * y])
            }
            state[0] = state[0] xor RC[round]
        }
    }

    fun keccak256(input: ByteArray): ByteArray {
        val rate = 136
        val outputLen = 32
        val state = ULongArray(25)

        var offset = 0
        while (offset + rate <= input.size) {
            for (i in 0 until rate / 8) {
                var lane = 0uL
                for (b in 0..7) lane = lane or ((input[offset + i * 8 + b].toULong() and 0xFFuL) shl (b * 8))
                state[i] = state[i] xor lane
            }
            keccakF(state)
            offset += rate
        }

        val remaining = input.size - offset
        val padded = ByteArray(rate)
        if (remaining > 0) input.copyInto(padded, 0, offset, offset + remaining)
        padded[remaining] = 0x01  // Keccak padding (NOT SHA3's 0x06)
        padded[rate - 1] = (padded[rate - 1].toInt() or 0x80).toByte()

        for (i in 0 until rate / 8) {
            var lane = 0uL
            for (b in 0..7) lane = lane or ((padded[i * 8 + b].toULong() and 0xFFuL) shl (b * 8))
            state[i] = state[i] xor lane
        }
        keccakF(state)

        val output = ByteArray(outputLen)
        for (i in 0 until outputLen) {
            output[i] = (state[i / 8] shr (8 * (i % 8))).toByte()
        }
        return output
    }
}
