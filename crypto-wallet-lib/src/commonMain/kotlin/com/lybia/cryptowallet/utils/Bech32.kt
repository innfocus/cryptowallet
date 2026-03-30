package com.lybia.cryptowallet.utils

/**
 * Bech32 encoding/decoding per BIP-173.
 * Cardano uses standard Bech32 (not Bech32m).
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val CHARSET_REV = IntArray(128) { -1 }.also { arr ->
        CHARSET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    private fun polymod(values: ByteArray): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (b in values) {
            val top = chk shr 25
            chk = (chk and 0x1ffffff shl 5) xor (b.toInt() and 0xFF)
            for (i in 0..4) {
                if ((top shr i and 1) == 1) chk = chk xor generators[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val result = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = (hrp[i].code shr 5).toByte()
        }
        result[hrp.length] = 0
        for (i in hrp.indices) {
            result[hrp.length + 1 + i] = (hrp[i].code and 0x1f).toByte()
        }
        return result
    }

    private fun createChecksum(hrp: String, data: ByteArray): ByteArray {
        val expanded = hrpExpand(hrp)
        val values = ByteArray(expanded.size + data.size + 6)
        expanded.copyInto(values)
        data.copyInto(values, expanded.size)
        val polymod = polymod(values) xor 1
        return ByteArray(6) { i -> ((polymod shr (5 * (5 - i))) and 0x1f).toByte() }
    }

    private fun verifyChecksum(hrp: String, data: ByteArray): Boolean {
        val expanded = hrpExpand(hrp)
        val values = ByteArray(expanded.size + data.size)
        expanded.copyInto(values)
        data.copyInto(values, expanded.size)
        return polymod(values) == 1
    }

    /**
     * Encode [hrp] and 5-bit [data] into a Bech32 string.
     */
    fun encode(hrp: String, data: ByteArray): String {
        val checksum = createChecksum(hrp, data)
        val combined = data + checksum
        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp)
        sb.append('1')
        for (b in combined) {
            sb.append(CHARSET[b.toInt() and 0x1f])
        }
        return sb.toString()
    }

    /**
     * Decode a Bech32 string into (hrp, 5-bit data).
     * @throws IllegalArgumentException on invalid input
     */
    fun decode(bech: String): Pair<String, ByteArray> {
        val lower = bech.lowercase()
        require(lower == bech || bech.uppercase() == bech) { "Mixed case in Bech32 string" }
        val work = lower
        val pos = work.lastIndexOf('1')
        require(pos >= 1) { "Missing separator" }
        require(pos + 7 <= work.length) { "Separator misplaced" }

        val hrp = work.substring(0, pos)
        val dataStr = work.substring(pos + 1)
        val data = ByteArray(dataStr.length)
        for (i in dataStr.indices) {
            val c = dataStr[i]
            require(c.code < 128) { "Invalid character" }
            val v = CHARSET_REV[c.code]
            require(v != -1) { "Invalid character '$c'" }
            data[i] = v.toByte()
        }
        require(verifyChecksum(hrp, data)) { "Invalid Bech32 checksum" }
        return hrp to data.copyOfRange(0, data.size - 6)
    }

    /**
     * Convert between bit groups.
     * @param data input bytes
     * @param fromBits source bits per element
     * @param toBits target bits per element
     * @param pad whether to pad the last group
     */
    fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            val value = b.toInt() and 0xFF
            require(value ushr fromBits == 0) { "Input value exceeds $fromBits bit size" }
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) result.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else {
            require(bits < fromBits) { "Could not convert bits, invalid padding" }
            require(((acc shl (toBits - bits)) and maxv) == 0) { "Non-zero padding" }
        }
        return result.toByteArray()
    }
}
