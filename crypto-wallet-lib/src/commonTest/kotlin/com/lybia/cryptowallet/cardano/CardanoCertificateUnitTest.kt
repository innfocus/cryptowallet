package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.errors.StakingError
import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import com.lybia.cryptowallet.wallets.cardano.CardanoCertificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for Cardano certificate CBOR encoding.
 *
 * Validates: Requirements 1.1, 1.4, 2.1
 */
class CardanoCertificateUnitTest {

    private val encoder = CborEncoder()
    private val decoder = CborDecoder()

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    // ── Delegation certificate with specific pool hash ──────────────

    /**
     * Test delegation certificate CBOR encoding with a specific pool hash.
     * Validates: Requirement 1.1
     */
    @Test
    fun delegationCertificateEncodesCorrectly() {
        val stakingKeyHash = hexToBytes("abcdef0123456789abcdef0123456789abcdef0123456789abcdef01")
        val poolKeyHash = hexToBytes("1234567890abcdef1234567890abcdef1234567890abcdef12345678")

        val cert = CardanoCertificate.Delegation(stakingKeyHash, poolKeyHash)
        val cborValue = cert.toCbor()

        // Verify structure: [2, [0, stakingKeyHash], poolKeyHash]
        assertTrue(cborValue is CborValue.CborArray)
        val items = (cborValue as CborValue.CborArray).items
        assertEquals(3, items.size, "Delegation cert should have 3 items")

        // Type = 2
        assertEquals(2u.toULong(), (items[0] as CborValue.CborUInt).value)

        // Stake credential = [0, stakingKeyHash]
        val stakeCredential = (items[1] as CborValue.CborArray).items
        assertEquals(2, stakeCredential.size)
        assertEquals(0u.toULong(), (stakeCredential[0] as CborValue.CborUInt).value)
        assertTrue(stakingKeyHash.contentEquals((stakeCredential[1] as CborValue.CborByteString).bytes))

        // Pool key hash
        assertTrue(poolKeyHash.contentEquals((items[2] as CborValue.CborByteString).bytes))

        // Verify round-trip
        val serialized = encoder.encode(cborValue)
        val decoded = decoder.decode(serialized)
        val deserialized = CardanoCertificate.fromCbor(decoded)
        assertEquals(cert, deserialized)
    }

    // ── Deregistration certificate with specific staking key ────────

    /**
     * Test deregistration certificate CBOR encoding with a specific staking key.
     * Validates: Requirement 2.1
     */
    @Test
    fun deregistrationCertificateEncodesCorrectly() {
        val stakingKeyHash = hexToBytes("fedcba9876543210fedcba9876543210fedcba9876543210fedcba98")

        val cert = CardanoCertificate.StakeDeregistration(stakingKeyHash)
        val cborValue = cert.toCbor()

        // Verify structure: [1, [0, stakingKeyHash]]
        assertTrue(cborValue is CborValue.CborArray)
        val items = (cborValue as CborValue.CborArray).items
        assertEquals(2, items.size, "Deregistration cert should have 2 items")

        // Type = 1
        assertEquals(1u.toULong(), (items[0] as CborValue.CborUInt).value)

        // Stake credential = [0, stakingKeyHash]
        val stakeCredential = (items[1] as CborValue.CborArray).items
        assertEquals(2, stakeCredential.size)
        assertEquals(0u.toULong(), (stakeCredential[0] as CborValue.CborUInt).value)
        assertTrue(stakingKeyHash.contentEquals((stakeCredential[1] as CborValue.CborByteString).bytes))

        // Verify round-trip
        val serialized = encoder.encode(cborValue)
        val decoded = decoder.decode(serialized)
        val deserialized = CardanoCertificate.fromCbor(decoded)
        assertEquals(cert, deserialized)
    }

    // ── Registration certificate ────────────────────────────────────

    @Test
    fun registrationCertificateEncodesCorrectly() {
        val stakingKeyHash = hexToBytes("aabbccdd11223344aabbccdd11223344aabbccdd11223344aabbccdd")

        val cert = CardanoCertificate.StakeRegistration(stakingKeyHash)
        val cborValue = cert.toCbor()

        // Verify structure: [0, [0, stakingKeyHash]]
        assertTrue(cborValue is CborValue.CborArray)
        val items = (cborValue as CborValue.CborArray).items
        assertEquals(2, items.size, "Registration cert should have 2 items")
        assertEquals(0u.toULong(), (items[0] as CborValue.CborUInt).value)
    }

    // ── Invalid pool address error ──────────────────────────────────

    /**
     * Test that invalid pool address (wrong size) is rejected.
     * Validates: Requirement 1.4
     */
    @Test
    fun invalidPoolKeyHashSizeThrowsError() {
        val stakingKeyHash = ByteArray(28) { 0x01 }
        val invalidPoolKeyHash = ByteArray(16) { 0x02 } // Wrong size, should be 28

        assertFailsWith<IllegalArgumentException>("Pool key hash must be 28 bytes") {
            CardanoCertificate.Delegation(stakingKeyHash, invalidPoolKeyHash)
        }
    }

    @Test
    fun invalidStakingKeyHashSizeThrowsError() {
        val invalidStakingKeyHash = ByteArray(32) { 0x01 } // Wrong size, should be 28

        assertFailsWith<IllegalArgumentException>("Staking key hash must be 28 bytes") {
            CardanoCertificate.StakeRegistration(invalidStakingKeyHash)
        }
    }

    /**
     * Test that StakingError.PoolNotFound is created correctly for invalid pool addresses.
     * Validates: Requirement 1.4
     */
    @Test
    fun poolNotFoundErrorForInvalidAddress() {
        val invalidPoolAddress = "pool1invalid_address_xyz"
        val error = StakingError.PoolNotFound(invalidPoolAddress)
        assertTrue(error.message.contains(invalidPoolAddress))
    }
}
