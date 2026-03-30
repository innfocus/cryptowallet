package com.lybia.cryptowallet.utils

/**
 * Pure Kotlin CRC32 implementation compatible with KMP.
 * Uses the standard CRC-32 polynomial (ISO 3309 / ITU-T V.42).
 */
object CRC32 {
    private val table = IntArray(256).also { t ->
        for (i in 0..255) {
            var crc = i
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
            t[i] = crc
        }
    }

    /**
     * Compute CRC32 checksum of [data].
     * @return unsigned 32-bit checksum as Long
     */
    fun compute(data: ByteArray): Long {
        var crc = 0xFFFFFFFF.toInt()
        for (b in data) {
            crc = table[(crc xor (b.toInt() and 0xFF)) and 0xFF] xor (crc ushr 8)
        }
        return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
    }
}
