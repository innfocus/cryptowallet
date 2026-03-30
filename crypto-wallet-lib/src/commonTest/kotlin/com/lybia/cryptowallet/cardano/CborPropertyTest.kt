package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropertyTesting
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for CBOR serialization.
 */
class CborPropertyTest {

    private val encoder = CborEncoder()
    private val decoder = CborDecoder()
    private val printer = CborPrettyPrinter()

    // ---- Generators ----

    /** Generate a CborValue tree with bounded depth to avoid stack overflow. */
    private fun arbCborValue(maxDepth: Int = 3): Arb<CborValue> = Arb.choice(
        listOf(
            arbCborUInt(),
            arbCborNegInt(),
            arbCborByteString(),
            arbCborTextString(),
            arbCborSimple()
        ) + if (maxDepth > 0) listOf(
            arbCborArray(maxDepth - 1),
            arbCborMap(maxDepth - 1),
            arbCborTag(maxDepth - 1)
        ) else emptyList()
    )

    private fun arbCborUInt(): Arb<CborValue> =
        Arb.uLong(0uL..ULong.MAX_VALUE).map { CborValue.CborUInt(it) }

    private fun arbCborNegInt(): Arb<CborValue> =
        Arb.uLong(0uL..Long.MAX_VALUE.toULong()).map { CborValue.CborNegInt(it) }

    private fun arbCborByteString(): Arb<CborValue> =
        Arb.byteArray(Arb.int(0..64), Arb.byte()).map { CborValue.CborByteString(it) }

    private fun arbCborTextString(): Arb<CborValue> =
        Arb.string(0..32, Codepoint.alphanumeric()).map { CborValue.CborTextString(it) }

    private fun arbCborSimple(): Arb<CborValue> =
        Arb.of(
            CborValue.CborSimple.FALSE,
            CborValue.CborSimple.TRUE,
            CborValue.CborSimple.NULL,
            CborValue.CborSimple.UNDEFINED
        )

    private fun arbCborArray(maxDepth: Int): Arb<CborValue> =
        Arb.list(arbCborValue(maxDepth), 0..5).map { CborValue.CborArray(it) }

    private fun arbCborMap(maxDepth: Int): Arb<CborValue> =
        Arb.list(
            Arb.pair(arbCborValue(maxDepth), arbCborValue(maxDepth)),
            0..4
        ).map { CborValue.CborMap(it) }

    private fun arbCborTag(maxDepth: Int): Arb<CborValue> =
        Arb.bind(
            Arb.uLong(0uL..1000uL),
            arbCborValue(maxDepth)
        ) { tag, content -> CborValue.CborTag(tag, content) }

    // ---- Helpers ----

    /** Deep structural equality for CborValue (handles ByteArray in CborByteString). */
    private fun cborEquals(a: CborValue, b: CborValue): Boolean {
        return when {
            a is CborValue.CborUInt && b is CborValue.CborUInt -> a.value == b.value
            a is CborValue.CborNegInt && b is CborValue.CborNegInt -> a.value == b.value
            a is CborValue.CborByteString && b is CborValue.CborByteString -> a.bytes.contentEquals(b.bytes)
            a is CborValue.CborTextString && b is CborValue.CborTextString -> a.text == b.text
            a is CborValue.CborArray && b is CborValue.CborArray ->
                a.items.size == b.items.size && a.items.zip(b.items).all { (x, y) -> cborEquals(x, y) }
            a is CborValue.CborMap && b is CborValue.CborMap ->
                a.entries.size == b.entries.size && a.entries.zip(b.entries).all { (x, y) ->
                    cborEquals(x.first, y.first) && cborEquals(x.second, y.second)
                }
            a is CborValue.CborTag && b is CborValue.CborTag ->
                a.tag == b.tag && cborEquals(a.content, b.content)
            a is CborValue.CborSimple && b is CborValue.CborSimple -> a.value == b.value
            else -> false
        }
    }

    // ---- Property Tests ----

    // Feature: cardano-midnight-support, Property 1: CBOR Serialization Round-Trip
    // **Validates: Requirements 7.1, 7.2, 7.4, 4.5**
    @Test
    fun cborRoundTrip() = runTest {
        checkAll(100, arbCborValue(maxDepth = 3)) { original ->
            val encoded = encoder.encode(original)
            val decoded = decoder.decode(encoded)
            assertTrue(
                cborEquals(original, decoded),
                "Round-trip failed for $original\nEncoded: ${encoded.toHex()}\nDecoded: $decoded"
            )
        }
    }

    // Feature: cardano-midnight-support, Property 2: CBOR Pretty Printer Produces Structured Output
    // **Validates: Requirements 7.3, 4.6**
    @Test
    fun cborPrettyPrinterProducesStructuredOutput() = runTest {
        checkAll(100, arbCborValue(maxDepth = 3)) { value ->
            val output = printer.format(value)
            assertTrue(output.isNotEmpty(), "Pretty printer output should not be empty for $value")

            // Verify structural markers are present based on type
            when (value) {
                is CborValue.CborUInt -> assertTrue(output.contains("uint("), "UInt output should contain 'uint(' marker")
                is CborValue.CborNegInt -> assertTrue(output.contains("negint("), "NegInt output should contain 'negint(' marker")
                is CborValue.CborByteString -> assertTrue(output.contains("bytes("), "ByteString output should contain 'bytes(' marker")
                is CborValue.CborTextString -> assertTrue(output.contains("text("), "TextString output should contain 'text(' marker")
                is CborValue.CborArray -> assertTrue(
                    output.contains("[") && output.contains("]"),
                    "Array output should contain brackets"
                )
                is CborValue.CborMap -> assertTrue(
                    output.contains("{") && output.contains("}"),
                    "Map output should contain braces"
                )
                is CborValue.CborTag -> assertTrue(output.contains("tag("), "Tag output should contain 'tag(' marker")
                is CborValue.CborSimple -> assertTrue(
                    output.contains("true") || output.contains("false") ||
                        output.contains("null") || output.contains("undefined") ||
                        output.contains("simple("),
                    "Simple output should contain type indicator"
                )
            }
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
