package com.lybia.cryptowallet.utils.cbor

/**
 * CBOR decoder per RFC 7049.
 * Supports definite-length, indefinite-length, and tag 24 (embedded CBOR).
 */
class CborDecoder {

    /**
     * Decode the first CBOR item from [bytes].
     */
    fun decode(bytes: ByteArray): CborValue {
        val state = DecodeState(bytes)
        return decodeItem(state)
    }

    /**
     * Decode all CBOR items from [bytes] (for streams with multiple top-level items).
     */
    fun decodeAll(bytes: ByteArray): List<CborValue> {
        val state = DecodeState(bytes)
        val results = mutableListOf<CborValue>()
        while (state.pos < state.data.size) {
            results.add(decodeItem(state))
        }
        return results
    }

    // ---- Internal decoding ----

    private class DecodeState(val data: ByteArray, var pos: Int = 0) {
        fun readByte(): Int {
            if (pos >= data.size) throw CborDecodingException("Unexpected end of CBOR data at position $pos")
            return data[pos++].toInt() and 0xFF
        }

        fun readBytes(n: Int): ByteArray {
            if (pos + n > data.size) throw CborDecodingException("Unexpected end of CBOR data at position $pos, need $n bytes")
            val result = data.copyOfRange(pos, pos + n)
            pos += n
            return result
        }

        fun peekByte(): Int {
            if (pos >= data.size) throw CborDecodingException("Unexpected end of CBOR data at position $pos")
            return data[pos].toInt() and 0xFF
        }
    }

    private fun decodeItem(state: DecodeState): CborValue {
        val initial = state.readByte()
        val majorType = initial shr 5
        val additionalInfo = initial and 0x1F

        return when (majorType) {
            0 -> CborValue.CborUInt(readArgument(additionalInfo, state))
            1 -> CborValue.CborNegInt(readArgument(additionalInfo, state))
            2 -> decodeByteString(additionalInfo, state)
            3 -> decodeTextString(additionalInfo, state)
            4 -> decodeArray(additionalInfo, state)
            5 -> decodeMap(additionalInfo, state)
            6 -> decodeTag(additionalInfo, state)
            7 -> decodeSimple(additionalInfo, state)
            else -> throw CborDecodingException("Unknown major type $majorType at position ${state.pos - 1}")
        }
    }

    private fun readArgument(additionalInfo: Int, state: DecodeState): ULong {
        return when {
            additionalInfo < 24 -> additionalInfo.toULong()
            additionalInfo == 24 -> state.readByte().toULong()
            additionalInfo == 25 -> {
                val b = state.readBytes(2)
                ((b[0].toInt() and 0xFF).toULong() shl 8) or
                    (b[1].toInt() and 0xFF).toULong()
            }
            additionalInfo == 26 -> {
                val b = state.readBytes(4)
                ((b[0].toInt() and 0xFF).toULong() shl 24) or
                    ((b[1].toInt() and 0xFF).toULong() shl 16) or
                    ((b[2].toInt() and 0xFF).toULong() shl 8) or
                    (b[3].toInt() and 0xFF).toULong()
            }
            additionalInfo == 27 -> {
                val b = state.readBytes(8)
                ((b[0].toInt() and 0xFF).toULong() shl 56) or
                    ((b[1].toInt() and 0xFF).toULong() shl 48) or
                    ((b[2].toInt() and 0xFF).toULong() shl 40) or
                    ((b[3].toInt() and 0xFF).toULong() shl 32) or
                    ((b[4].toInt() and 0xFF).toULong() shl 24) or
                    ((b[5].toInt() and 0xFF).toULong() shl 16) or
                    ((b[6].toInt() and 0xFF).toULong() shl 8) or
                    (b[7].toInt() and 0xFF).toULong()
            }
            else -> throw CborDecodingException("Invalid additional info $additionalInfo at position ${state.pos}")
        }
    }

    private fun decodeByteString(additionalInfo: Int, state: DecodeState): CborValue.CborByteString {
        if (additionalInfo == 31) {
            // Indefinite-length byte string
            val chunks = mutableListOf<Byte>()
            while (state.peekByte() != 0xFF) {
                val item = decodeItem(state)
                if (item is CborValue.CborByteString) {
                    chunks.addAll(item.bytes.toList())
                } else {
                    throw CborDecodingException("Expected byte string chunk in indefinite-length byte string at position ${state.pos}")
                }
            }
            state.readByte() // consume break
            return CborValue.CborByteString(chunks.toByteArray())
        }
        val len = readArgument(additionalInfo, state)
        return CborValue.CborByteString(state.readBytes(len.toInt()))
    }

    private fun decodeTextString(additionalInfo: Int, state: DecodeState): CborValue.CborTextString {
        if (additionalInfo == 31) {
            // Indefinite-length text string
            val sb = StringBuilder()
            while (state.peekByte() != 0xFF) {
                val item = decodeItem(state)
                if (item is CborValue.CborTextString) {
                    sb.append(item.text)
                } else {
                    throw CborDecodingException("Expected text string chunk in indefinite-length text string at position ${state.pos}")
                }
            }
            state.readByte() // consume break
            return CborValue.CborTextString(sb.toString())
        }
        val len = readArgument(additionalInfo, state)
        val bytes = state.readBytes(len.toInt())
        return CborValue.CborTextString(bytes.decodeToString())
    }

    private fun decodeArray(additionalInfo: Int, state: DecodeState): CborValue.CborArray {
        if (additionalInfo == 31) {
            // Indefinite-length array
            val items = mutableListOf<CborValue>()
            while (state.peekByte() != 0xFF) {
                items.add(decodeItem(state))
            }
            state.readByte() // consume break
            return CborValue.CborArray(items, indefinite = true)
        }
        val count = readArgument(additionalInfo, state).toInt()
        val items = (0 until count).map { decodeItem(state) }
        return CborValue.CborArray(items)
    }

    private fun decodeMap(additionalInfo: Int, state: DecodeState): CborValue.CborMap {
        if (additionalInfo == 31) {
            // Indefinite-length map
            val entries = mutableListOf<Pair<CborValue, CborValue>>()
            while (state.peekByte() != 0xFF) {
                val key = decodeItem(state)
                val value = decodeItem(state)
                entries.add(key to value)
            }
            state.readByte() // consume break
            return CborValue.CborMap(entries)
        }
        val count = readArgument(additionalInfo, state).toInt()
        val entries = (0 until count).map {
            val key = decodeItem(state)
            val value = decodeItem(state)
            key to value
        }
        return CborValue.CborMap(entries)
    }

    private fun decodeTag(additionalInfo: Int, state: DecodeState): CborValue.CborTag {
        val tag = readArgument(additionalInfo, state)
        val content = decodeItem(state)
        return CborValue.CborTag(tag, content)
    }

    private fun decodeSimple(additionalInfo: Int, state: DecodeState): CborValue {
        return when {
            additionalInfo < 24 -> CborValue.CborSimple(additionalInfo)
            additionalInfo == 24 -> CborValue.CborSimple(state.readByte())
            else -> throw CborDecodingException("Unsupported simple value additional info $additionalInfo at position ${state.pos}")
        }
    }
}

class CborDecodingException(message: String) : Exception(message)
