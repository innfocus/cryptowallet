package com.lybia.cryptowallet.wallets.bitcoin

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for Bitcoin multi-address type support (G9).
 *
 * Verifies that [BitcoinManager.getAddressByType] dispatches to the correct
 * bitcoin-kmp function for each [BitcoinAddressType], that legacy helper
 * methods delegate correctly, and that invalid inputs are rejected.
 *
 * Requirements: 36.1, 36.2, 36.3, 36.6, 36.7, 36.8, 36.11
 */
class BitcoinAddressTypeTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var savedNetwork: Network

    @BeforeTest
    fun setup() {
        savedNetwork = Config.shared.getNetwork()
        Config.shared.setNetwork(Network.MAINNET)
    }

    @AfterTest
    fun tearDown() {
        Config.shared.setNetwork(savedNetwork)
    }

    // ── getAddressByType — prefix correctness per type (mainnet) ────────

    @Test
    fun getAddressByType_nativeSegwit_mainnet_returnsBc1Prefix() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        assertTrue(
            address.startsWith("bc1"),
            "NATIVE_SEGWIT mainnet address should start with 'bc1', got: $address"
        )
    }

    @Test
    fun getAddressByType_nestedSegwit_mainnet_returns3Prefix() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.NESTED_SEGWIT, 0)
        assertTrue(
            address.startsWith("3"),
            "NESTED_SEGWIT mainnet address should start with '3', got: $address"
        )
    }

    @Test
    fun getAddressByType_legacy_mainnet_returns1Prefix() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.LEGACY, 0)
        assertTrue(
            address.startsWith("1"),
            "LEGACY mainnet address should start with '1', got: $address"
        )
    }

    // ── getAddressByType — prefix correctness per type (testnet) ────────

    @Test
    fun getAddressByType_nativeSegwit_testnet_returnsTb1Prefix() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        assertTrue(
            address.startsWith("tb1"),
            "NATIVE_SEGWIT testnet address should start with 'tb1', got: $address"
        )
    }

    @Test
    fun getAddressByType_nestedSegwit_testnet_returns2Prefix() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.NESTED_SEGWIT, 0)
        assertTrue(
            address.startsWith("2"),
            "NESTED_SEGWIT testnet address should start with '2', got: $address"
        )
    }

    @Test
    fun getAddressByType_legacy_testnet_returnsMorNPrefix() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddressByType(BitcoinAddressType.LEGACY, 0)
        assertTrue(
            address.startsWith("m") || address.startsWith("n"),
            "LEGACY testnet address should start with 'm' or 'n', got: $address"
        )
    }

    // ── getLegacyAddress — fix verification (Req 36.8) ──────────────────

    @Test
    fun getLegacyAddress_mainnet_returns1Prefix_notBc1() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getLegacyAddress()
        assertTrue(
            address.startsWith("1"),
            "getLegacyAddress() should return P2PKH address starting with '1', got: $address"
        )
        assertTrue(
            !address.startsWith("bc1"),
            "getLegacyAddress() must NOT return a bc1 (Native SegWit) address"
        )
    }

    // ── getNestedSegWitAddress — fix verification (Req 36.7) ────────────

    @Test
    fun getNestedSegWitAddress_mainnet_returns3Prefix_notBc1() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getNestedSegWitAddress()
        assertTrue(
            address.startsWith("3"),
            "getNestedSegWitAddress() should return P2SH-P2WPKH address starting with '3', got: $address"
        )
        assertTrue(
            !address.startsWith("bc1"),
            "getNestedSegWitAddress() must NOT return a bc1 (Native SegWit) address"
        )
    }

    // ── Negative accountIndex — validation (Req 36.11) ──────────────────

    @Test
    fun getAddressByType_negativeAccountIndex_throwsIllegalArgumentException() {
        val manager = BitcoinManager(testMnemonic)
        assertFailsWith<IllegalArgumentException> {
            manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, -1)
        }
    }

    @Test
    fun getAddressByType_negativeAccountIndex_legacy_throwsIllegalArgumentException() {
        val manager = BitcoinManager(testMnemonic)
        assertFailsWith<IllegalArgumentException> {
            manager.getAddressByType(BitcoinAddressType.LEGACY, -5)
        }
    }

    // ── Backward compatibility — getNativeSegWitAddress (Req 36.6) ──────

    @Test
    fun getNativeSegWitAddress_mainnet_returnsBc1Address() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getNativeSegWitAddress()
        assertTrue(
            address.startsWith("bc1"),
            "getNativeSegWitAddress() should return bc1 address, got: $address"
        )
    }

    @Test
    fun getNativeSegWitAddress_matchesGetAddressByType_nativeSegwit() {
        val manager = BitcoinManager(testMnemonic)
        val fromHelper = manager.getNativeSegWitAddress(0)
        val fromUnified = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        assertEquals(
            fromUnified,
            fromHelper,
            "getNativeSegWitAddress() should produce the same address as getAddressByType(NATIVE_SEGWIT)"
        )
    }

    // ── getAddress() default — returns Native SegWit ────────────────────

    @Test
    fun getAddress_default_returnsBc1Address() {
        val manager = BitcoinManager(testMnemonic)
        val address = manager.getAddress()
        assertTrue(
            address.startsWith("bc1"),
            "getAddress() default should return Native SegWit (bc1) address, got: $address"
        )
    }

    // ── Multi-account — different accounts produce different addresses ──

    @Test
    fun getAddressByType_differentAccounts_produceDifferentAddresses() {
        val manager = BitcoinManager(testMnemonic)
        val addr0 = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        val addr1 = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 1)
        assertTrue(
            addr0 != addr1,
            "Different account indices should produce different addresses"
        )
    }

    // ── Different types produce different addresses for same account ────

    @Test
    fun getAddressByType_differentTypes_sameAccount_produceDifferentAddresses() {
        val manager = BitcoinManager(testMnemonic)
        val native = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        val nested = manager.getAddressByType(BitcoinAddressType.NESTED_SEGWIT, 0)
        val legacy = manager.getAddressByType(BitcoinAddressType.LEGACY, 0)
        assertTrue(native != nested, "NATIVE_SEGWIT and NESTED_SEGWIT should differ")
        assertTrue(native != legacy, "NATIVE_SEGWIT and LEGACY should differ")
        assertTrue(nested != legacy, "NESTED_SEGWIT and LEGACY should differ")
    }

    // ── Determinism — same call twice returns same address ──────────────

    @Test
    fun getAddressByType_calledTwice_returnsSameAddress() {
        val manager = BitcoinManager(testMnemonic)
        val first = manager.getAddressByType(BitcoinAddressType.LEGACY, 0)
        val second = manager.getAddressByType(BitcoinAddressType.LEGACY, 0)
        assertEquals(first, second, "Same type + account should always produce the same address")
    }

    // ── Deprecated getSegWitAddress delegates to getNestedSegWitAddress ─

    @Test
    @Suppress("DEPRECATION")
    fun getSegWitAddress_delegatesToNestedSegWit() {
        val manager = BitcoinManager(testMnemonic)
        @Suppress("DEPRECATION")
        val deprecated = manager.getSegWitAddress()
        val nested = manager.getNestedSegWitAddress()
        assertEquals(
            nested,
            deprecated,
            "Deprecated getSegWitAddress() should return the same address as getNestedSegWitAddress()"
        )
    }
}
