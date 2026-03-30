package com.lybia.cryptowallet.utils.cbor

/**
 * CBOR encoder per RFC 7049.
 * Supports definite-length, indefinite-length, canonical encoding, and tag 24 (embedded CBOR).
 */
class CborEncoder {

    /**
     * Encode a [CborValue] to a CBOR [ByteArray].
     */
    fun encode(value: CborValue): ByteArray {
        val buffer = mutableListOf<Byte>()
        encodeValue(value, buffer)
        return buffer.toByteArray()
    }

    /**
     * Encode a [CborValue] to canonical CBOR (deterministic encoding per RFC 7049 §3.9).
     * - Map keys are sorted by their encoded byte representation (shortest first, then lexicographic).
     * - Indefinite-length encoding is NOT used.
     */
    fun encodeCanonical(value: CborValue): ByteArray {
        val buffer = mutableListOf<Byte>()
        encodeCanonicalValue(value, buffer)
        return buffer.toByteArray()
    }

    // ---- Internal encoding ----

    private fun encodeValue(value: CborValue, buf: MutableList<Byte>) {
        when (value) {
            is CborValue.CborUInt -> encodeUInt(value.value, buf)
            is CborValue.CborNegInt -> encodeNegInt(value.value, buf)
            is CborValue.CborByteString -> encodeByteString(value.bytes, buf)
            is CborValue.CborTextString -> encodeTextString(value.text, buf)
            is CborValue.CborArray -> encodeArray(value, buf)
            is CborValue.CborMap -> encodeMap(value.entries, buf)
            is CborValue.CborTag -> encodeTag(value, buf)
            is CborValue.CborSimple -> encodeSimple(value.value, buf)
        }
    }

    private fun encodeCanonicalValue(value: CborValue, buf: MutableList<Byte>) {
        when (value) {
            is CborValue.CborUInt -> encodeUInt(value.value, buf)
            is CborValue.CborNegInt -> encodeNegInt(value.value, buf)
            is CborValue.CborByteString -> encodeByteString(value.bytes, buf)
            is CborValue.CborTextString -> encodeTextString(value.text, buf)
            is CborValue.CborArray -> {
                // Always definite-length in canonical
                encodeHead(MAJOR_ARRAY, value.items.size.toULong(), buf)
                for (item in value.items) encodeCanonicalValue(item, buf)
            }
            is CborValue.CborMap -> encodeCanonicalMap(value.entries, buf)
            is CborValue.CborTag -> {
                encodeHead(MAJOR_TAG, value.tag, buf)
                encodeCanonicalValue(value.content, buf)
            }
            is CborValue.CborSimple -> encodeSimple(value.value, buf)
        }
    }

    // ---- Major type encoders ----

    private fun encodeUInt(value: ULong, buf: MutableList<Byte>) {
        encodeHead(MAJOR_UINT, value, buf)
    }

    private fun encodeNegInt(value: ULong, buf: MutableList<Byte>) {
        encodeHead(MAJOR_NEG_INT, value, buf)
    }

    private fun encodeByteString(bytes: ByteArray, buf: MutableList<Byte>) {
        encodeHead(MAJOR_BYTE_STRING, bytes.size.toULong(), buf)
        buf.addAll(bytes.toList())
    }

    private fun encodeTextString(text: String, buf: MutableList<Byte>) {
        val utf8 = text.encodeToByteArray()
        encodeHead(MAJOR_TEXT_STRING, utf8.size.toULong(), buf)
        buf.addAll(utf8.toList())
    }

    private fun encodeArray(arr: CborValue.CborArray, buf: MutableList<Byte>) {
        if (arr.indefinite) {
            buf.add((MAJOR_ARRAY shl 5 or 31).toByte())
            for (item in arr.items) encodeValue(item, buf)
            buf.add(BREAK_BYTE)
        } else {
            encodeHead(MAJOR_ARRAY, arr.items.size.toULong(), buf)
            for (item in arr.items) encodeValue(item, buf)
        }
    }

    private fun encodeMap(entries: List<Pair<CborValue, CborValue>>, buf: MutableList<Byte>) {
        encodeHead(MAJOR_MAP, entries.size.toULong(), buf)
        for ((k, v) in entries) {
            encodeValue(k, buf)
            encodeValue(v, buf)
        }
    }

    private fun encodeCanonicalMap(entries: List<Pair<CborValue, CborValue>>, buf: MutableList<Byte>) {
        // Sort keys by encoded bytes: shortest first, then lexicographic
        val sorted = entries.sortedWith(Comparator { a, b ->
            val ka = encodeCanonical(a.first)
            val kb = encodeCanonical(b.first)
            if (ka.size != kb.size) return@Comparator ka.size - kb.size
            for (i in ka.indices) {
                val cmp = (ka[i].toInt() and 0xFF) - (kb[i].toInt() and 0xFF)
                if (cmp != 0) return@Comparator cmp
            }
            0
        })
        encodeHead(MAJOR_MAP, sorted.size.toULong(), buf)
        for ((k, v) in sorted) {
            encodeCanonicalValue(k, buf)
            encodeCanonicalValue(v, buf)
        }
    }

    private fun encodeTag(tag: CborValue.CborTag, buf: MutableList<Byte>) {
        encodeHead(MAJOR_TAG, tag.tag, buf)
        encodeValue(tag.content, buf)
    }

    private fun encodeSimple(value: Int, buf: MutableList<Byte>) {
        if (value < 24) {
            buf.add((MAJOR_SIMPLE shl 5 or value).toByte())
        } else {
            buf.add((MAJOR_SIMPLE shl 5 or 24).toByte())
            buf.add(value.toByte())
        }
    }

    // ---- Head encoding (major type + argument) ----

    private fun encodeHead(majorType: Int, value: ULong, buf: MutableList<Byte>) {
        val mt = majorType shl 5
        when {
            value < 24u -> {
                buf.add((mt or value.toInt()).toByte())
            }
            value <= UByte.MAX_VALUE.toULong() -> {
                buf.add((mt or 24).toByte())
                buf.add(value.toByte())
            }
            value <= UShort.MAX_VALUE.toULong() -> {
                buf.add((mt or 25).toByte())
                buf.add((value.toInt() shr 8).toByte())
                buf.add(value.toByte())
            }
            value <= UInt.MAX_VALUE.toULong() -> {
                buf.add((mt or 26).toByte())
                buf.add((value.toInt() shr 24).toByte())
                buf.add((value.toInt() shr 16).toByte())
                buf.add((value.toInt() shr 8).toByte())
                buf.add(value.toByte())
            }
            else -> {
                buf.add((mt or 27).toByte())
                val l = value.toLong()
                buf.add((l shr 56).toByte())
                buf.add((l shr 48).toByte())
                buf.add((l shr 40).toByte())
                buf.add((l shr 32).toByte())
                buf.add((l shr 24).toByte())
                buf.add((l shr 16).toByte())
                buf.add((l shr 8).toByte())
                buf.add(l.toByte())
            }
        }
    }

    companion object {
        internal const val MAJOR_UINT = 0
        internal const val MAJOR_NEG_INT = 1
        internal const val MAJOR_BYTE_STRING = 2
        internal const val MAJOR_TEXT_STRING = 3
        internal const val MAJOR_ARRAY = 4
        internal const val MAJOR_MAP = 5
        internal const val MAJOR_TAG = 6
        internal const val MAJOR_SIMPLE = 7
        internal const val BREAK_BYTE: Byte = 0xFF.toByte()
    }
}
