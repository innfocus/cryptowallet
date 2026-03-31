package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.wallets.cardano.CardanoCertificate
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for CardanoCertificate CBOR serialization.
 *
 * Feature: staking-bridge, Property 1: Certificate CBOR round-trip
 * **Validates: Requirements 1.1, 2.1, 8.2, 8.3**
 */
class CardanoCertificatePropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    /** 28-byte staking key hash (Blake2b-224 of public key). */
    private fun arbStakingKeyHash(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(28), Arb.byte())

    /** 28-byte pool key hash. */
    private fun arbPoolKeyHash(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(28), Arb.byte())

    /** Generate any CardanoCertificate variant. */
    private fun arbCertificate(): Arb<CardanoCertificate> = Arb.choice(
        arbStakingKeyHash().map { CardanoCertificate.StakeRegistration(it) },
        arbStakingKeyHash().map { CardanoCertificate.StakeDeregistration(it) },
        Arb.bind(arbStakingKeyHash(), arbPoolKeyHash()) { sk, pk ->
            CardanoCertificate.Delegation(sk, pk)
        }
    )

    private val encoder = CborEncoder()
    private val decoder = CborDecoder()

    // ── Property Test ───────────────────────────────────────────────

    // Feature: staking-bridge, Property 1: Certificate CBOR round-trip
    // **Validates: Requirements 1.1, 2.1, 8.2, 8.3**
    @Test
    fun certificateCborRoundTrip() = runTest {
        checkAll(100, arbCertificate()) { cert ->
            // Serialize to CBOR
            val cborValue = cert.toCbor()
            val serialized = encoder.encode(cborValue)
            assertTrue(serialized.isNotEmpty(), "Serialized CBOR should not be empty")

            // Deserialize back
            val decoded = decoder.decode(serialized)
            val deserialized = CardanoCertificate.fromCbor(decoded)

            // Verify round-trip equality
            assertEquals(cert, deserialized, "Certificate should survive CBOR round-trip")
        }
    }
}
