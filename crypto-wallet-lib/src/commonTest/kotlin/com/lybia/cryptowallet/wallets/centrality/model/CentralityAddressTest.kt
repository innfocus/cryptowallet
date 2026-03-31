package com.lybia.cryptowallet.wallets.centrality.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for CentralityAddress (SS58 parsing).
 */
class CentralityAddressTest {

    // ---- Known valid SS58 addresses (Substrate/CennzNet) ----

    /** Alice's well-known address on Substrate (network prefix 42) */
    private val aliceAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"

    /** Bob's well-known address on Substrate (network prefix 42) */
    private val bobAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"

    private val knownValidAddresses = listOf(aliceAddress, bobAddress)

    // ---- Property Tests ----

    // Feature: centrality-kmp-migration, Property 3: SS58 address parsing idempotence
    // **Validates: Requirements 3.1, 3.7**
    @Test
    fun ss58AddressParsingIdempotence() = runTest {
        // For known valid SS58 addresses, parsing twice produces same public key
        // If publicKey != null, publicKey.size == 32
        checkAll(200, Arb.string(1..50).map { knownValidAddresses.random() }) { addr ->
            val first = CentralityAddress(addr)
            val second = CentralityAddress(addr)

            // Idempotence: both parses produce the same result
            if (first.publicKey != null) {
                assertNotNull(second.publicKey, "Second parse should also produce non-null key")
                assertTrue(
                    first.publicKey.contentEquals(second.publicKey),
                    "Parsing the same address twice should yield identical public keys"
                )
                // Valid SS58 public keys are 32 bytes
                assertEquals(32, first.publicKey.size, "Public key should be 32 bytes")
            } else {
                assertNull(second.publicKey, "Second parse should also produce null")
            }
        }
    }

    // Feature: centrality-kmp-migration, Property 4: Invalid strings produce null public key
    // **Validates: Requirements 3.2**
    @Test
    fun invalidStringsProduceNullPublicKey() = runTest {
        // Random strings containing non-Base58 characters (0, O, I, l) should produce null
        val nonBase58Chars = "0OIl"
        checkAll(200, Arb.stringPattern("[a-zA-Z0-9]{1,50}").map { base ->
            // Inject at least one non-Base58 character
            val pos = (base.indices).random()
            val invalidChar = nonBase58Chars.random()
            base.substring(0, pos) + invalidChar + base.substring(pos)
        }) { invalidString ->
            val addr = CentralityAddress(invalidString)
            assertNull(
                addr.publicKey,
                "String with non-Base58 chars should produce null publicKey: $invalidString"
            )
        }
    }

    // Feature: crypto-wallet-module, Property 9: SS58 address round-trip
    // **Validates: Requirements 25.4**
    @Test
    fun ss58AddressRoundTrip() = runTest {
        // For known valid SS58 addresses, parse → publicKey → re-encode → parse again
        // must yield the same public key bytes.
        // We also generate addresses from random 32-byte public keys to broaden coverage.

        // Part 1: Known valid addresses round-trip
        for (addr in knownValidAddresses) {
            val parsed = CentralityAddress(addr)
            assertNotNull(parsed.publicKey, "Known address should parse successfully: $addr")

            // Re-encode the extracted public key back to SS58
            val reEncoded = CentralityAddress.encodeSS58(parsed.publicKey)
            val reParsed = CentralityAddress(reEncoded)
            assertNotNull(reParsed.publicKey, "Re-encoded address should parse successfully")
            assertTrue(
                parsed.publicKey.contentEquals(reParsed.publicKey),
                "Round-trip public key should match for address: $addr"
            )
        }

        // Part 2: Property — random 32-byte public keys round-trip through SS58
        checkAll(200, Arb.byteArray(Arb.constant(32), Arb.byte())) { randomKey ->
            val encoded = CentralityAddress.encodeSS58(randomKey)
            val parsed = CentralityAddress(encoded)
            assertNotNull(parsed.publicKey, "Encoded address should parse back successfully")
            assertTrue(
                randomKey.contentEquals(parsed.publicKey),
                "Round-trip: parse(encode(key)) should equal original key"
            )
            assertEquals(32, parsed.publicKey.size, "Public key should be 32 bytes")
        }
    }

    // ---- Unit Tests (Task 2.4) ----

    @Test
    fun parseKnownValidAliceAddress() {
        val addr = CentralityAddress(aliceAddress)
        assertTrue(addr.isValid(), "Alice's SS58 address should be valid")
        assertNotNull(addr.publicKey)
        assertEquals(32, addr.publicKey.size, "Public key should be 32 bytes")
    }

    @Test
    fun parseKnownValidBobAddress() {
        val addr = CentralityAddress(bobAddress)
        assertTrue(addr.isValid(), "Bob's SS58 address should be valid")
        assertNotNull(addr.publicKey)
        assertEquals(32, addr.publicKey.size, "Public key should be 32 bytes")
    }

    @Test
    fun rejectInvalidAddressStrings() {
        // Empty string
        assertNull(CentralityAddress("").publicKey, "Empty string should produce null")

        // Random garbage
        assertNull(CentralityAddress("notavalidaddress").publicKey, "Random string should produce null")

        // String with non-Base58 chars
        assertNull(CentralityAddress("0OIl").publicKey, "Non-Base58 chars should produce null")

        // Too short after decode
        assertNull(CentralityAddress("1").publicKey, "Too short should produce null")
    }

    @Test
    fun constructorWithKnownPublicKeyHex() {
        val knownHex = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        val addr = CentralityAddress(aliceAddress, knownHex)

        assertTrue(addr.isValid())
        assertNotNull(addr.publicKey)
        assertEquals(32, addr.publicKey.size)
        assertEquals(aliceAddress, addr.address)

        // Verify the hex was correctly decoded
        val expected = knownHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertTrue(addr.publicKey.contentEquals(expected), "Public key should match the provided hex")
    }

    @Test
    fun constructorWithHexPrefixStripsIt() {
        val knownHex = "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        val addr = CentralityAddress(aliceAddress, knownHex)

        assertTrue(addr.isValid())
        assertEquals(32, addr.publicKey!!.size)
    }

    @Test
    fun parsedAlicePublicKeyMatchesKnownValue() {
        // Alice's well-known public key on Substrate
        val expectedHex = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        val addr = CentralityAddress(aliceAddress)

        assertNotNull(addr.publicKey)
        val actualHex = addr.publicKey.joinToString("") { "%02x".format(it) }
        assertEquals(expectedHex, actualHex, "Parsed public key should match Alice's known key")
    }
}
