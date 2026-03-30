package com.lybia.cryptowallet.utils.cbor

/**
 * Sealed class representing all CBOR data types per RFC 7049.
 */
sealed class CborValue {
    /** Major type 0: Unsigned integer (0..2^64-1) */
    data class CborUInt(val value: ULong) : CborValue() {
        constructor(value: Int) : this(value.toULong())
    }

    /** Major type 1: Negative integer (-1..-2^64) stored as -1 - value */
    data class CborNegInt(val value: ULong) : CborValue() {
        /** The actual negative number this represents: -1 - value */
        val actualValue: Long get() = (-1L - value.toLong())

        companion object {
            fun fromActual(n: Long): CborNegInt {
                require(n < 0) { "CborNegInt requires a negative number, got $n" }
                return CborNegInt((-1L - n).toULong())
            }
        }
    }

    /** Major type 2: Byte string */
    data class CborByteString(val bytes: ByteArray) : CborValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CborByteString) return false
            return bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Major type 3: Text (UTF-8) string */
    data class CborTextString(val text: String) : CborValue()

    /** Major type 4: Array of CBOR values */
    data class CborArray(
        val items: List<CborValue>,
        val indefinite: Boolean = false
    ) : CborValue()

    /** Major type 5: Map of CBOR key-value pairs (preserves insertion order) */
    data class CborMap(val entries: List<Pair<CborValue, CborValue>>) : CborValue()

    /** Major type 6: Semantic tag wrapping another CBOR value */
    data class CborTag(val tag: ULong, val content: CborValue) : CborValue() {
        constructor(tag: Int, content: CborValue) : this(tag.toULong(), content)
    }

    /** Major type 7: Simple values (false=20, true=21, null=22, undefined=23) */
    data class CborSimple(val value: Int) : CborValue() {
        companion object {
            val FALSE = CborSimple(20)
            val TRUE = CborSimple(21)
            val NULL = CborSimple(22)
            val UNDEFINED = CborSimple(23)
        }
    }
}
