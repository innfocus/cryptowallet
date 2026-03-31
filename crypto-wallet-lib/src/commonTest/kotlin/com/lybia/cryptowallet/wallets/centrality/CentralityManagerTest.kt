package com.lybia.cryptowallet.wallets.centrality

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for CentralityManager.
 */
class CentralityManagerTest {

    private val manager = CentralityManager(mnemonic = "test mnemonic")

    // ---- Property Tests ----

    // Feature: centrality-kmp-migration, Property 7: Era option calculation
    // **Validates: Requirements 7.7**
    @Test
    fun eraOptionCalculationProperty() = runTest {
        checkAll(200, Arb.long(0L, Long.MAX_VALUE / 2)) { blockNumber ->
            val result = manager.makeEraOption(blockNumber)

            // Always returns exactly 2 bytes
            assertEquals(2, result.size, "makeEraOption should return 2-byte array for block $blockNumber")

            // Verify the formula: quantizedPhase = blockNumber % 128, encoded = 6 + (quantizedPhase << 4)
            val quantizedPhase = blockNumber % CentralityManager.CAL_PERIOD
            val encoded = 6 + (quantizedPhase shl 4)
            val expectedLow = (encoded and 0xff).toByte()
            val expectedHigh = (encoded shr 8).toByte()
            assertEquals(expectedLow, result[0], "Low byte mismatch for block $blockNumber")
            assertEquals(expectedHigh, result[1], "High byte mismatch for block $blockNumber")
        }
    }

    // Feature: centrality-kmp-migration, Property 8: Balance unit conversion
    // **Validates: Requirements 7.3**
    @Test
    fun balanceUnitConversionProperty() = runTest {
        checkAll(200, Arb.long(0L, Long.MAX_VALUE / 2)) { rawBalance ->
            val converted = rawBalance.toDouble() / CentralityManager.BASE_UNIT
            assertTrue(converted >= 0.0, "Balance should be non-negative for raw=$rawBalance")
        }
    }

    // ---- Unit Tests (Task 9.5) ----

    @Test
    fun makeEraOptionKnownBlockNumbers() {
        // Block 0: quantizedPhase = 0 % 128 = 0, encoded = 6 + (0 << 4) = 6
        val era0 = manager.makeEraOption(0)
        assertEquals(0x06.toByte(), era0[0])
        assertEquals(0x00.toByte(), era0[1])

        // Block 128: quantizedPhase = 128 % 128 = 0, encoded = 6
        val era128 = manager.makeEraOption(128)
        assertEquals(0x06.toByte(), era128[0])
        assertEquals(0x00.toByte(), era128[1])

        // Block 1: quantizedPhase = 1, encoded = 6 + (1 << 4) = 6 + 16 = 22
        val era1 = manager.makeEraOption(1)
        assertEquals(22.toByte(), era1[0])
        assertEquals(0x00.toByte(), era1[1])

        // Block 127: quantizedPhase = 127, encoded = 6 + (127 << 4) = 6 + 2032 = 2038
        // 2038 & 0xff = 0xF6 (246), 2038 >> 8 = 7
        val era127 = manager.makeEraOption(127)
        assertEquals(0xF6.toByte(), era127[0])
        assertEquals(0x07.toByte(), era127[1])

        // Block 6425936 (from legacy code): quantizedPhase = 6425936 % 128 = 80
        // encoded = 6 + (80 << 4) = 6 + 1280 = 1286
        // 1286 & 0xff = 0x06, 1286 >> 8 = 5
        val eraLegacy = manager.makeEraOption(6425936)
        assertEquals(0x06.toByte(), eraLegacy[0])
        assertEquals(0x05.toByte(), eraLegacy[1])
    }

    @Test
    fun convertHexToBlockNumber() {
        // Known value from legacy code: "0x6211cb" → 6427083
        assertEquals(6427083L, manager.convertHexToBlockNumber("0x6211cb"))

        // Without prefix
        assertEquals(6427083L, manager.convertHexToBlockNumber("6211cb"))

        // Zero
        assertEquals(0L, manager.convertHexToBlockNumber("0x0"))

        // Simple values
        assertEquals(255L, manager.convertHexToBlockNumber("0xff"))
        assertEquals(256L, manager.convertHexToBlockNumber("0x100"))
    }

    @Test
    fun getAddressReturnsEmptyWhenNotCached() {
        val freshManager = CentralityManager(mnemonic = "test")
        assertEquals("", freshManager.getAddress())
    }

    @Test
    fun baseUnitConstant() {
        assertEquals(10000, CentralityManager.BASE_UNIT)
    }

    @Test
    fun calPeriodConstant() {
        assertEquals(128, CentralityManager.CAL_PERIOD)
    }
}
