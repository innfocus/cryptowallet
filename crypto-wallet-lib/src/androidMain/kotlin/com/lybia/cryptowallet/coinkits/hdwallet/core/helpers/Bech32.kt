package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers

import java.io.ByteArrayOutputStream
import java.util.Locale


object Bech32 {
    const val CHARSET: String = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    const val SEPARATOR: Char = 0x31.toChar() // '1'

    fun bech32Encode(hrp: ByteArray, data: ByteArray): String {
        val chk = createChecksum(hrp, data)
        val combined = ByteArray(chk.size + data.size)

        System.arraycopy(data, 0, combined, 0, data.size)
        System.arraycopy(chk, 0, combined, data.size, chk.size)

        val xlat = ByteArray(combined.size)
        for (i in combined.indices) {
            xlat[i] = CHARSET[combined[i].toInt()].code.toByte()
        }

        val ret = ByteArray(hrp.size + xlat.size + 1)
        System.arraycopy(hrp, 0, ret, 0, hrp.size)
        System.arraycopy(byteArrayOf(0x31), 0, ret, hrp.size, 1)
        System.arraycopy(xlat, 0, ret, hrp.size + 1, xlat.size)

        return String(ret)
    }

    fun bech32Decode(bech: String): HrpAndData {
        var bech = bech
        require(!(bech != bech.lowercase(Locale.getDefault()) && bech != bech.uppercase(Locale.getDefault()))) { "bech32 cannot mix upper and lower case" }

        val buffer = bech.toByteArray()
        for (b in buffer) {
            require(!(b < 0x21 || b > 0x7e)) { "bech32 characters out of range" }
        }

        bech = bech.lowercase(Locale.getDefault())
        val pos = bech.lastIndexOf("1")
        require(pos >= 1) { "bech32 missing separator" }
        require(pos + 7 <= bech.length) { "bech32 separator misplaced" }

        val s = bech.substring(pos + 1)
        for (i in 0 until s.length) {
            require(CHARSET.indexOf(s[i]) != -1) { "bech32 characters  out of range" }
        }

        val hrp = bech.substring(0, pos).toByteArray()

        val data = ByteArray(bech.length - pos - 1)
        var j = 0
        var i = pos + 1
        while (i < bech.length) {
            data[j] = CHARSET.indexOf(bech[i]).toByte()
            i++
            j++
        }

        require(verifyChecksum(hrp, data)) { "invalid bech32 checksum" }

        val ret = ByteArray(data.size - 6)
        System.arraycopy(data, 0, ret, 0, data.size - 6)

        return HrpAndData(hrp, ret)
    }

    private fun polymod(values: ByteArray): Int {
        val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

        var chk = 1

        for (b in values) {
            val top = (chk shr 0x19).toByte()
            chk = b.toInt() xor ((chk and 0x1ffffff) shl 5)
            for (i in 0..4) {
                chk = chk xor if (((top.toInt() shr i) and 1) == 1) GENERATORS[i] else 0
            }
        }

        return chk
    }

    private fun hrpExpand(hrp: ByteArray): ByteArray {
        val buf1 = ByteArray(hrp.size)
        val buf2 = ByteArray(hrp.size)
        val mid = ByteArray(1)

        for (i in hrp.indices) {
            buf1[i] = (hrp[i].toInt() shr 5).toByte()
        }
        mid[0] = 0x00
        for (i in hrp.indices) {
            buf2[i] = (hrp[i].toInt() and 0x1f).toByte()
        }

        val ret = ByteArray((hrp.size * 2) + 1)
        System.arraycopy(buf1, 0, ret, 0, buf1.size)
        System.arraycopy(mid, 0, ret, buf1.size, mid.size)
        System.arraycopy(buf2, 0, ret, buf1.size + mid.size, buf2.size)

        return ret
    }

    private fun verifyChecksum(hrp: ByteArray, data: ByteArray): Boolean {
        val exp = hrpExpand(hrp)

        val values = ByteArray(exp.size + data.size)
        System.arraycopy(exp, 0, values, 0, exp.size)
        System.arraycopy(data, 0, values, exp.size, data.size)

        return (1 == polymod(values))
    }

    private fun createChecksum(hrp: ByteArray, data: ByteArray): ByteArray {
        val zeroes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val expanded = hrpExpand(hrp)
        val values = ByteArray(zeroes.size + expanded.size + data.size)

        System.arraycopy(expanded, 0, values, 0, expanded.size)
        System.arraycopy(data, 0, values, expanded.size, data.size)
        System.arraycopy(zeroes, 0, values, expanded.size + data.size, zeroes.size)

        val polymod = polymod(values) xor 1
        val ret = ByteArray(6)
        for (i in ret.indices) {
            ret[i] = ((polymod shr 5 * (5 - i)) and 0x1f).toByte()
        }

        return ret
    }

    /**
     * Helper for re-arranging bits into groups.
     */
    @Throws(AddressFormatException::class)
    fun convertBits(
        `in`: ByteArray, inStart: Int, inLen: Int, fromBits: Int,
        toBits: Int, pad: Boolean
    ): ByteArray {
        var acc = 0
        var bits = 0
        val out = ByteArrayOutputStream(64)
        val maxv = (1 shl toBits) - 1
        val max_acc = (1 shl (fromBits + toBits - 1)) - 1
        for (i in 0 until inLen) {
            val value = `in`[i + inStart].toInt() and 0xff
            if ((value ushr fromBits) != 0) {
                throw AddressFormatException(
                    String.format("Input value '%X' exceeds '%d' bit size", value, fromBits)
                )
            }
            acc = ((acc shl fromBits) or value) and max_acc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.write((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) out.write((acc shl (toBits - bits)) and maxv)
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            throw AddressFormatException("Could not convert bits, invalid padding")
        }
        return out.toByteArray()
    }

    class HrpAndData(var hrp: ByteArray, var data: ByteArray) {
        override fun toString(): String {
            return "HrpAndData [hrp=" + hrp.contentToString() + ", data=" + data.contentToString() + "]"
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + data.contentHashCode()
            result = prime * result + hrp.contentHashCode()
            return result
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (javaClass != obj.javaClass) return false
            val other = obj as HrpAndData
            if (!data.contentEquals(other.data)) return false
            if (!hrp.contentEquals(other.hrp)) return false
            return true
        }
    }
}
