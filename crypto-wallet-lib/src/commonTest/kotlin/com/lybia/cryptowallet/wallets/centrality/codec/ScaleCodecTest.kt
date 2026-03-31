package com.lybia.cryptowallet.wallets.centrality.codec

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for ScaleCodec.
 */
class ScaleCodecTest {

    // ---- Generators ----

    /** Single-byte mode: 0..63 */
    private fun arbSingleByte(): Arb<BigInteger> =
        Arb.long(0L, 63L).map { BigInteger(it) }

    /** Two-byte mode: 64..16383 */
    private fun arbTwoByte(): Arb<BigInteger> =
        Arb.long(64L, 16383L).map { BigInteger(it) }

    /** Four-byte mode: 16384..1073741823 */
    private fun arbFourByte(): Arb<BigInteger> =
        Arb.long(16384L, 1073741823L).map { BigInteger(it) }

    /** Big-integer mode: 1073741824..Long.MAX_VALUE */
    private fun arbBigInteger(): Arb<BigInteger> =
        Arb.long(1073741824L, Long.MAX_VALUE).map { BigInteger(it) }

    /** Any non-negative value across all 4 modes */
    private fun arbNonNegative(): Arb<BigInteger> = Arb.of(
        arbSingleByte(), arbTwoByte(), arbFourByte(), arbBigInteger()
    ).let { arbOfArbs ->
        // Flatten: pick one of the 4 arbs, then sample from it
        Arb.long(0L, Long.MAX_VALUE).map { BigInteger(it) }
    }

    // ---- Property Tests ----

    // Feature: centrality-kmp-migration, Property 1: SCALE compact encoding round-trip
    // **Validates: Requirements 2.5**
    @Test
    fun scaleCompactEncodingRoundTrip() = runTest {
        // Test across all 4 modes
        checkAll(200, arbSingleByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, _) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Round-trip failed for single-byte value $value")
        }
        checkAll(200, arbTwoByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, _) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Round-trip failed for two-byte value $value")
        }
        checkAll(200, arbFourByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, _) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Round-trip failed for four-byte value $value")
        }
        checkAll(200, arbBigInteger()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, _) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Round-trip failed for big-integer value $value")
        }
    }

    // Feature: centrality-kmp-migration, Property 2: SCALE encoding mode selection
    // **Validates: Requirements 2.1, 2.2, 2.3**
    @Test
    fun scaleEncodingModeSelection() = runTest {
        // Single-byte mode: value <= 63 → exactly 1 byte
        checkAll(200, arbSingleByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            assertEquals(1, encoded.size, "Single-byte mode should produce 1 byte for value $value")
        }
        // Two-byte mode: 64 <= value <= 16383 → exactly 2 bytes
        checkAll(200, arbTwoByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            assertEquals(2, encoded.size, "Two-byte mode should produce 2 bytes for value $value")
        }
        // Four-byte mode: 16384 <= value <= 1073741823 → exactly 4 bytes
        checkAll(200, arbFourByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            assertEquals(4, encoded.size, "Four-byte mode should produce 4 bytes for value $value")
        }
        // Big-integer mode: value > 1073741823 → more than 4 bytes
        checkAll(200, arbBigInteger()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            assertTrue(encoded.size > 4, "Big-integer mode should produce >4 bytes for value $value")
        }
    }

    @Test
    fun toArrayLikeLeReturnsCorrectLength() = runTest {
        // Use values that fit within 4 bytes (max ~4 billion) to avoid overflow
        checkAll(200, Arb.long(0L, 0xFFFFFFFFL).map { BigInteger(it) }) { value ->
            val result4 = ScaleCodec.toArrayLikeLE(value, 4)
            assertEquals(4, result4.size, "toArrayLikeLE should return 4 bytes for value $value")
        }
        // 8-byte values
        checkAll(200, Arb.long(0L, Long.MAX_VALUE).map { BigInteger(it) }) { value ->
            val result8 = ScaleCodec.toArrayLikeLE(value, 8)
            assertEquals(8, result8.size, "toArrayLikeLE should return 8 bytes for value $value")
            val result16 = ScaleCodec.toArrayLikeLE(value, 16)
            assertEquals(16, result16.size, "toArrayLikeLE should return 16 bytes for value $value")
        }
    }

    @Test
    fun compactAddLengthPrefixDecodesToInputSize() = runTest {
        checkAll(100, Arb.long(0L, 255L).map { len ->
            ByteArray(len.toInt()) { it.toByte() }
        }) { input ->
            val withLength = ScaleCodec.compactAddLength(input)
            val (decodedLen, prefixBytes) = ScaleCodec.compactFromU8a(withLength)
            assertEquals(
                BigInteger(input.size),
                decodedLen,
                "compactAddLength prefix should decode to input.size=${input.size}"
            )
        }
    }

    // Feature: crypto-wallet-module, Property 8: SCALE encoding round-trip
    // For any valid non-negative BigInteger value, encodeCompact → decodeCompact
    // produces the equivalent value, across all 4 modes.
    // **Validates: Requirements 24.5**
    @Test
    fun scaleCompactEncodingRoundTripAllModes() = runTest {
        // Single-byte mode: 0..63
        checkAll(100, arbSingleByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, bytesConsumed) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Single-byte round-trip failed for $value")
            assertEquals(encoded.size, bytesConsumed, "Bytes consumed mismatch for single-byte $value")
        }
        // Two-byte mode: 64..16383
        checkAll(100, arbTwoByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, bytesConsumed) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Two-byte round-trip failed for $value")
            assertEquals(encoded.size, bytesConsumed, "Bytes consumed mismatch for two-byte $value")
        }
        // Four-byte mode: 16384..1073741823
        checkAll(100, arbFourByte()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, bytesConsumed) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Four-byte round-trip failed for $value")
            assertEquals(encoded.size, bytesConsumed, "Bytes consumed mismatch for four-byte $value")
        }
        // Big-integer mode: >1073741823
        checkAll(100, arbBigInteger()) { value ->
            val encoded = ScaleCodec.compactToU8a(value)
            val (decoded, bytesConsumed) = ScaleCodec.compactFromU8a(encoded)
            assertEquals(value, decoded, "Big-integer round-trip failed for $value")
            assertEquals(encoded.size, bytesConsumed, "Bytes consumed mismatch for big-integer $value")
        }
    }

    // ---- Unit Tests: Boundary Values (Task 1.4) ----

    @Test
    fun compactToU8aBoundaryZero() {
        // 0 → single-byte: (0 << 2) = 0x00
        val result = ScaleCodec.compactToU8a(BigInteger.ZERO)
        assertEquals(1, result.size)
        assertEquals(0x00.toByte(), result[0])
    }

    @Test
    fun compactToU8aBoundary63() {
        // 63 → single-byte: (63 << 2) = 252 = 0xFC
        val result = ScaleCodec.compactToU8a(BigInteger(63))
        assertEquals(1, result.size)
        assertEquals(0xFC.toByte(), result[0])
    }

    @Test
    fun compactToU8aBoundary64() {
        // 64 → two-byte: (64 << 2) + 1 = 257 → LE bytes [0x01, 0x01]
        val result = ScaleCodec.compactToU8a(BigInteger(64))
        assertEquals(2, result.size)
        assertEquals(0x01.toByte(), result[0])
        assertEquals(0x01.toByte(), result[1])
    }

    @Test
    fun compactToU8aBoundary16383() {
        // 16383 → two-byte: (16383 << 2) + 1 = 65533 → LE bytes [0xFD, 0xFF]
        val result = ScaleCodec.compactToU8a(BigInteger(16383))
        assertEquals(2, result.size)
        assertEquals(0xFD.toByte(), result[0])
        assertEquals(0xFF.toByte(), result[1])
    }

    @Test
    fun compactToU8aBoundary16384() {
        // 16384 → four-byte: (16384 << 2) + 2 = 65538 → LE 4 bytes [0x02, 0x00, 0x01, 0x00]
        val result = ScaleCodec.compactToU8a(BigInteger(16384))
        assertEquals(4, result.size)
        assertEquals(0x02.toByte(), result[0])
        assertEquals(0x00.toByte(), result[1])
        assertEquals(0x01.toByte(), result[2])
        assertEquals(0x00.toByte(), result[3])
    }

    @Test
    fun compactToU8aBoundary1073741823() {
        // 1073741823 → four-byte: (1073741823 << 2) + 2 = 4294967294 → LE [0xFE, 0xFF, 0xFF, 0xFF]
        val result = ScaleCodec.compactToU8a(BigInteger(1073741823))
        assertEquals(4, result.size)
        assertEquals(0xFE.toByte(), result[0])
        assertEquals(0xFF.toByte(), result[1])
        assertEquals(0xFF.toByte(), result[2])
        assertEquals(0xFF.toByte(), result[3])
    }

    @Test
    fun compactToU8aBoundary1073741824() {
        // 1073741824 → big-integer mode: >4 bytes
        val result = ScaleCodec.compactToU8a(BigInteger(1073741824))
        assertTrue(result.size > 4, "1073741824 should use big-integer mode (>4 bytes)")
        // Verify round-trip
        val (decoded, _) = ScaleCodec.compactFromU8a(result)
        assertEquals(BigInteger(1073741824), decoded)
    }

    @Test
    fun toArrayLikeLeByteOrder() {
        // 0x01020304 = 16909060 → LE: [0x04, 0x03, 0x02, 0x01]
        val result = ScaleCodec.toArrayLikeLE(BigInteger(0x01020304), 4)
        assertContentEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), result)
    }

    @Test
    fun toArrayLikeLeSmallValue() {
        // 1 in 4 bytes → [0x01, 0x00, 0x00, 0x00]
        val result = ScaleCodec.toArrayLikeLE(BigInteger.ONE, 4)
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00), result)
    }

    @Test
    fun toArrayLikeLeAutoLength() {
        // 256 = 0x100 → auto length = 2 bytes → LE: [0x00, 0x01]
        val result = ScaleCodec.toArrayLikeLE(BigInteger(256))
        assertContentEquals(byteArrayOf(0x00, 0x01), result)
    }

    @Test
    fun compactAddLengthKnownArrays() {
        // Empty array → prefix [0x00]
        val empty = ScaleCodec.compactAddLength(byteArrayOf())
        assertEquals(0x00.toByte(), empty[0])
        assertEquals(1, empty.size)

        // 3-byte array → prefix [0x0C] (3 << 2 = 12 = 0x0C), then the 3 bytes
        val input = byteArrayOf(0x01, 0x02, 0x03)
        val result = ScaleCodec.compactAddLength(input)
        assertEquals(0x0C.toByte(), result[0])
        assertContentEquals(input, result.sliceArray(1 until result.size))

        // 10-byte array → prefix [0x28] (10 << 2 = 40 = 0x28)
        val input10 = ByteArray(10) { (it + 1).toByte() }
        val result10 = ScaleCodec.compactAddLength(input10)
        assertEquals(0x28.toByte(), result10[0])
        assertContentEquals(input10, result10.sliceArray(1 until result10.size))
    }
}
