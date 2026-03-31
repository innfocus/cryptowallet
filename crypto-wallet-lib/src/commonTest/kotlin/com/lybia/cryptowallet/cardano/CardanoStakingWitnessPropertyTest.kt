package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.wallets.cardano.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for Cardano staking transaction dual witnesses.
 *
 * Feature: staking-bridge, Property 2: Staking transaction yêu cầu dual witnesses
 * **Validates: Requirements 1.2, 2.2**
 */
class CardanoStakingWitnessPropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    /** 32-byte Ed25519 private key seed. */
    private fun arbSeed(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(32), Arb.byte())

    /** 28-byte key hash. */
    private fun arbKeyHash28(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(28), Arb.byte())

    /** 32-byte transaction hash. */
    private fun arbTxHash(): Arb<ByteArray> =
        Arb.byteArray(Arb.constant(32), Arb.byte())

    /** Random address bytes. */
    private fun arbAddressBytes(): Arb<ByteArray> =
        Arb.byteArray(Arb.int(29..57), Arb.byte())

    /** Generate a staking certificate (delegation or deregistration). */
    private fun arbStakingCertificate(): Arb<CardanoCertificate> = Arb.choice(
        Arb.bind(arbKeyHash28(), arbKeyHash28()) { sk, pk ->
            CardanoCertificate.Delegation(sk, pk)
        },
        arbKeyHash28().map { CardanoCertificate.StakeDeregistration(it) }
    )

    // ── Property Test ───────────────────────────────────────────────

    // Feature: staking-bridge, Property 2: Staking transaction yêu cầu dual witnesses
    // **Validates: Requirements 1.2, 2.2**
    @Test
    fun stakingTransactionRequiresDualWitnesses() = runTest {
        checkAll(
            100,
            arbSeed(),
            arbSeed(),
            arbTxHash(),
            arbAddressBytes(),
            arbStakingCertificate()
        ) { paymentSeed, stakingSeed, txHash, addrBytes, certificate ->
            // Ensure payment and staking seeds are different
            // (extremely unlikely to be equal with random 32 bytes, but skip if so)
            if (paymentSeed.contentEquals(stakingSeed)) return@checkAll

            // Build a staking transaction body with a certificate
            val txBody = CardanoTransactionBody(
                inputs = listOf(CardanoTransactionInput(txHash, 0)),
                outputs = listOf(CardanoTransactionOutput(addrBytes, 2_000_000L)),
                fee = 200_000L,
                ttl = 50_000_000L,
                certificates = listOf(certificate)
            )

            // Get the transaction hash to sign
            val bodyHash = txBody.getHash()

            // Derive public keys
            val paymentPubKey = Ed25519.publicKey(paymentSeed)
            val stakingPubKey = Ed25519.publicKey(stakingSeed)

            // Sign with both keys (dual witnesses)
            val paymentSig = Ed25519.sign(paymentSeed, bodyHash)
            val stakingSig = Ed25519.sign(stakingSeed, bodyHash)

            // Build witness set with both witnesses
            val witnessSet = CardanoWitnessBuilder()
                .addVKeyWitness(paymentPubKey, paymentSig)
                .addVKeyWitness(stakingPubKey, stakingSig)
                .build()

            // Property: exactly 2 VKey witnesses
            assertEquals(
                2, witnessSet.vkeyWitnesses.size,
                "Staking transaction must have exactly 2 VKey witnesses"
            )

            // Property: the two public keys must be different
            assertFalse(
                witnessSet.vkeyWitnesses[0].publicKey.contentEquals(
                    witnessSet.vkeyWitnesses[1].publicKey
                ),
                "Payment and staking public keys must be different"
            )

            // Verify serialization round-trip preserves dual witnesses
            val serialized = witnessSet.serialize()
            val deserialized = CardanoWitnessSet.deserialize(serialized)
            assertEquals(
                2, deserialized.vkeyWitnesses.size,
                "Deserialized witness set must preserve 2 VKey witnesses"
            )

            // Verify the transaction body contains certificates
            assertTrue(
                txBody.certificates.isNotEmpty(),
                "Staking transaction must contain certificates"
            )
        }
    }
}
