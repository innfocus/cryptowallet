package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for CBOR edge cases: tag 24, indefinite-length arrays, large integers, empty values.
 */
class CborUnitTest {

    private val encoder = CborEncoder()
    private val decoder = CborDecoder()
    private val printer = CborPrettyPrinter()

    // ---- Tag 24 (Embedded CBOR) ----

    @Test
    fun encodeDecodeTag24() {
        val inner = CborValue.CborByteString(byteArrayOf(0x01, 0x02, 0x03))
        val tagged = CborValue.CborTag(24, inner)
        val encoded = encoder.encode(tagged)
        val decoded = decoder.decode(encoded)

        assertTrue(decoded is CborValue.CborTag)
        assertEquals(24uL, (decoded as CborValue.CborTag).tag)
        assertTrue(decoded.content is CborValue.CborByteString)
        assertTrue(
            (inner.bytes).contentEquals((decoded.content as CborValue.CborByteString).bytes)
        )
    }

    @Test
    fun tag24WithNestedCbor() {
        // Tag 24 wrapping a CBOR-encoded byte string (common in Byron addresses)
        val innerValue = CborValue.CborArray(
            listOf(
                CborValue.CborUInt(42),
                CborValue.CborTextString("hello")
            )
        )
        val innerBytes = encoder.encode(innerValue)
        val tag24 = CborValue.CborTag(24, CborValue.CborByteString(innerBytes))

        val encoded = encoder.encode(tag24)
        val decoded = decoder.decode(encoded)

        assertTrue(decoded is CborValue.CborTag)
        val decodedTag = decoded as CborValue.CborTag
        assertEquals(24uL, decodedTag.tag)
        assertTrue(decodedTag.content is CborValue.CborByteString)

        // Decode the embedded CBOR
        val embeddedBytes = (decodedTag.content as CborValue.CborByteString).bytes
        val embeddedValue = decoder.decode(embeddedBytes)
        assertTrue(embeddedValue is CborValue.CborArray)
        val arr = embeddedValue as CborValue.CborArray
        assertEquals(2, arr.items.size)
        assertEquals(CborValue.CborUInt(42uL), arr.items[0])
        assertEquals(CborValue.CborTextString("hello"), arr.items[1])
    }

    // ---- Indefinite-length arrays ----

    @Test
    fun encodeDecodeIndefiniteLengthArray() {
        val arr = CborValue.CborArray(
            listOf(
                CborValue.CborUInt(1),
                CborValue.CborUInt(2),
                CborValue.CborUInt(3)
            ),
            indefinite = true
        )
        val encoded = encoder.encode(arr)

        // First byte should be 0x9F (major type 4, additional info 31)
        assertEquals(0x9F.toByte(), encoded[0])
        // Last byte should be 0xFF (break)
        assertEquals(0xFF.toByte(), encoded.last())

        val decoded = decoder.decode(encoded)
        assertTrue(decoded is CborValue.CborArray)
        val decodedArr = decoded as CborValue.CborArray
        assertTrue(decodedArr.indefinite)
        assertEquals(3, decodedArr.items.size)
        assertEquals(CborValue.CborUInt(1uL), decodedArr.items[0])
        assertEquals(CborValue.CborUInt(2uL), decodedArr.items[1])
        assertEquals(CborValue.CborUInt(3uL), decodedArr.items[2])
    }

    @Test
    fun encodeDecodeEmptyIndefiniteLengthArray() {
        val arr = CborValue.CborArray(emptyList(), indefinite = true)
        val encoded = encoder.encode(arr)

        // Should be [0x9F, 0xFF]
        assertEquals(2, encoded.size)
        assertEquals(0x9F.toByte(), encoded[0])
        assertEquals(0xFF.toByte(), encoded[1])

        val decoded = decoder.decode(encoded)
        assertTrue(decoded is CborValue.CborArray)
        val decodedArr = decoded as CborValue.CborArray
        assertTrue(decodedArr.indefinite)
        assertTrue(decodedArr.items.isEmpty())
    }

    // ---- Large integers ----

    @Test
    fun encodeDecodeLargeUInt() {
        val value = CborValue.CborUInt(ULong.MAX_VALUE)
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun encodeDecodeUIntBoundaries() {
        // Test boundary values for different CBOR integer sizes
        val boundaries = listOf(
            0uL,           // 1-byte (inline)
            23uL,          // max 1-byte inline
            24uL,          // 2-byte
            255uL,         // max 2-byte
            256uL,         // 3-byte
            65535uL,       // max 3-byte
            65536uL,       // 5-byte
            4294967295uL,  // max 5-byte (UInt.MAX_VALUE)
            4294967296uL,  // 9-byte
            ULong.MAX_VALUE
        )
        for (v in boundaries) {
            val value = CborValue.CborUInt(v)
            val encoded = encoder.encode(value)
            val decoded = decoder.decode(encoded)
            assertEquals(value, decoded, "Failed for UInt value $v")
        }
    }

    @Test
    fun encodeDecodeNegIntBoundaries() {
        // NegInt stores -1-n, so value=0 means -1, value=23 means -24, etc.
        val boundaries = listOf(0uL, 23uL, 24uL, 255uL, 256uL, 65535uL, 65536uL, 4294967295uL)
        for (v in boundaries) {
            val value = CborValue.CborNegInt(v)
            val encoded = encoder.encode(value)
            val decoded = decoder.decode(encoded)
            assertEquals(value, decoded, "Failed for NegInt value $v (actual: ${value.actualValue})")
        }
    }

    // ---- Empty values ----

    @Test
    fun encodeDecodeEmptyByteString() {
        val value = CborValue.CborByteString(byteArrayOf())
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assertTrue(decoded is CborValue.CborByteString)
        assertTrue((decoded as CborValue.CborByteString).bytes.isEmpty())
    }

    @Test
    fun encodeDecodeEmptyTextString() {
        val value = CborValue.CborTextString("")
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun encodeDecodeEmptyArray() {
        val value = CborValue.CborArray(emptyList())
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assertTrue(decoded is CborValue.CborArray)
        assertTrue((decoded as CborValue.CborArray).items.isEmpty())
    }

    @Test
    fun encodeDecodeEmptyMap() {
        val value = CborValue.CborMap(emptyList())
        val encoded = encoder.encode(value)
        val decoded = decoder.decode(encoded)
        assertTrue(decoded is CborValue.CborMap)
        assertTrue((decoded as CborValue.CborMap).entries.isEmpty())
    }

    // ---- Simple values ----

    @Test
    fun encodeDecodeSimpleValues() {
        for (simple in listOf(
            CborValue.CborSimple.FALSE,
            CborValue.CborSimple.TRUE,
            CborValue.CborSimple.NULL,
            CborValue.CborSimple.UNDEFINED
        )) {
            val encoded = encoder.encode(simple)
            val decoded = decoder.decode(encoded)
            assertEquals(simple, decoded)
        }
    }

    // ---- Canonical encoding ----

    @Test
    fun canonicalEncodingSortsMapKeys() {
        val map = CborValue.CborMap(
            listOf(
                CborValue.CborUInt(10) to CborValue.CborTextString("ten"),
                CborValue.CborUInt(1) to CborValue.CborTextString("one"),
                CborValue.CborUInt(5) to CborValue.CborTextString("five")
            )
        )
        val canonical = encoder.encodeCanonical(map)
        val decoded = decoder.decode(canonical) as CborValue.CborMap

        // Keys should be sorted: 1, 5, 10
        assertEquals(CborValue.CborUInt(1uL), decoded.entries[0].first)
        assertEquals(CborValue.CborUInt(5uL), decoded.entries[1].first)
        assertEquals(CborValue.CborUInt(10uL), decoded.entries[2].first)
    }

    @Test
    fun canonicalEncodingForcesDefiniteLength() {
        val arr = CborValue.CborArray(
            listOf(CborValue.CborUInt(1), CborValue.CborUInt(2)),
            indefinite = true
        )
        val canonical = encoder.encodeCanonical(arr)
        // First byte should NOT be 0x9F (indefinite), should be 0x82 (definite, 2 items)
        assertEquals(0x82.toByte(), canonical[0])
    }

    // ---- decodeAll ----

    @Test
    fun decodeAllMultipleItems() {
        val v1 = CborValue.CborUInt(42)
        val v2 = CborValue.CborTextString("hello")
        val bytes = encoder.encode(v1) + encoder.encode(v2)
        val items = decoder.decodeAll(bytes)
        assertEquals(2, items.size)
        assertEquals(CborValue.CborUInt(42uL), items[0])
        assertEquals(CborValue.CborTextString("hello"), items[1])
    }

    // ---- Pretty printer ----

    @Test
    fun prettyPrintTag24() {
        val tag = CborValue.CborTag(24, CborValue.CborByteString(byteArrayOf(0xCA.toByte(), 0xFE.toByte())))
        val output = printer.format(tag)
        assertTrue(output.contains("tag(24)"))
        assertTrue(output.contains("bytes("))
        assertTrue(output.contains("cafe"))
    }

    @Test
    fun prettyPrintNestedStructure() {
        val value = CborValue.CborMap(
            listOf(
                CborValue.CborUInt(0) to CborValue.CborArray(
                    listOf(CborValue.CborTextString("a"), CborValue.CborTextString("b"))
                )
            )
        )
        val output = printer.format(value)
        assertTrue(output.contains("map("))
        assertTrue(output.contains("{"))
        assertTrue(output.contains("}"))
        assertTrue(output.contains("["))
        assertTrue(output.contains("]"))
        assertTrue(output.contains("uint("))
        assertTrue(output.contains("text("))
    }

    @Test
    fun prettyPrintFromBytes() {
        val value = CborValue.CborUInt(42)
        val bytes = encoder.encode(value)
        val output = printer.format(bytes)
        assertTrue(output.contains("uint(42)"))
    }

    // ---- Error handling ----

    @Test
    fun decodeEmptyBytesFails() {
        assertFailsWith<CborDecodingException> {
            decoder.decode(byteArrayOf())
        }
    }

    @Test
    fun decodeTruncatedDataFails() {
        // A 2-byte integer header but missing the value byte
        assertFailsWith<CborDecodingException> {
            decoder.decode(byteArrayOf(0x18)) // major 0, additional 24, but no following byte
        }
    }
}
