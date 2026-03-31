package com.lybia.cryptowallet.wallets.centrality.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.centrality.codec.ScaleCodec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for ExtrinsicBuilder.
 */
class ExtrinsicBuilderTest {

    // ---- Known test data ----

    /** Alice's well-known SS58 address (network prefix 42) */
    private val aliceAddress = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val alicePubKeyHex = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"

    /** Bob's well-known SS58 address */
    private val bobAddress = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"

    private val testGenesisHash = "0x" + "aa".repeat(32)
    private val testBlockHash = "0x" + "bb".repeat(32)
    private val testSignatureHex = "cc".repeat(64)
    private val testEra = byteArrayOf(0x15, 0x02)

    private fun buildTestExtrinsic(
        to: String = bobAddress,
        amount: Long = 50000L,
        assetId: Int = 1,
        signer: String = aliceAddress,
        nonce: Int = 5,
        specVersion: Int = 39,
        transactionVersion: Int = 5,
        genesisHash: String = testGenesisHash,
        blockHash: String = testBlockHash,
        era: ByteArray = testEra,
        signatureHex: String = testSignatureHex
    ): ExtrinsicBuilder {
        return ExtrinsicBuilder()
            .paramsMethod(to, amount, assetId)
            .paramsSignature(signer, nonce)
            .signOptions(specVersion, transactionVersion, genesisHash, blockHash, era)
            .sign(signatureHex)
    }

    // ---- Property Tests ----

    // Feature: centrality-kmp-migration, Property 6: Extrinsic encoding invariants
    // **Validates: Requirements 5.1, 5.2, 5.6**
    @Test
    fun extrinsicEncodingInvariants() = runTest {
        // Generate varying amounts, nonces, assetIds, specVersions, txVersions
        checkAll(
            200,
            Arb.long(1L, 1_000_000_000L),  // amount
            Arb.int(0, 10000),               // nonce
            Arb.int(1, 100),                 // assetId
            Arb.int(1, 200),                 // specVersion
            Arb.int(1, 50)                   // transactionVersion
        ) { amount, nonce, assetId, specVersion, txVersion ->
            val builder = ExtrinsicBuilder()
                .paramsMethod(bobAddress, amount, assetId)
                .paramsSignature(aliceAddress, nonce)
                .signOptions(specVersion, txVersion, testGenesisHash, testBlockHash, testEra)
                .sign(testSignatureHex)

            // Invariant 1: toHex() starts with "0x"
            val hex = builder.toHex()
            assertTrue(hex.startsWith("0x"), "toHex() should start with '0x', got: ${hex.take(10)}")

            // Invariant 2: toU8a() starts with compact-encoded length prefix
            val u8a = builder.toU8a()
            val (decodedLen, prefixBytes) = ScaleCodec.compactFromU8a(u8a)
            val expectedBodyLen = u8a.size - prefixBytes
            assertEquals(
                BigInteger(expectedBodyLen),
                decodedLen,
                "Compact length prefix should equal body length"
            )

            // Invariant 3: Byte after length prefix = 132 (version byte)
            assertEquals(
                132.toByte(),
                u8a[prefixBytes],
                "First byte after compact length should be version byte 132"
            )

            // Invariant 4: createPayload() contains genesisHash and blockHash at the end
            val payload = builder.createPayload()
            val genesisBytes = testGenesisHash.removePrefix("0x").fromHexToByteArray()
            val blockBytes = testBlockHash.removePrefix("0x").fromHexToByteArray()
            // blockHash is the last 32 bytes
            val tailBlockHash = payload.sliceArray(payload.size - 32 until payload.size)
            assertContentEquals(blockBytes, tailBlockHash, "Payload should end with blockHash bytes")
            // genesisHash is the 32 bytes before blockHash
            val tailGenesisHash = payload.sliceArray(payload.size - 64 until payload.size - 32)
            assertContentEquals(genesisBytes, tailGenesisHash, "Payload should contain genesisHash before blockHash")
        }
    }

    // ---- Unit Tests (Task 6.3) ----

    @Test
    fun createPayloadOutputByteLayout() {
        val builder = buildTestExtrinsic()
        val payload = builder.createPayload()

        // Payload starts with method bytes: callIndex(2) + compact(assetId) + publicKey(to)(32) + compact(amount)
        // callIndex = 0x0401
        assertEquals(0x04.toByte(), payload[0], "First byte should be callIndex high byte")
        assertEquals(0x01.toByte(), payload[1], "Second byte should be callIndex low byte")

        // After method bytes: era(2) + compact(nonce) + txPayment(2) + specVersion(4) + txVersion(4) + genesis(32) + block(32)
        // Verify genesisHash and blockHash at the end
        val genesisBytes = testGenesisHash.removePrefix("0x").fromHexToByteArray()
        val blockBytes = testBlockHash.removePrefix("0x").fromHexToByteArray()

        val tailBlockHash = payload.sliceArray(payload.size - 32 until payload.size)
        assertContentEquals(blockBytes, tailBlockHash, "Payload should end with blockHash")

        val tailGenesisHash = payload.sliceArray(payload.size - 64 until payload.size - 32)
        assertContentEquals(genesisBytes, tailGenesisHash, "Payload should have genesisHash before blockHash")

        // Verify specVersion (LE 4 bytes) before genesisHash
        // specVersion = 39 → LE: [0x27, 0x00, 0x00, 0x00]
        val specVersionBytes = payload.sliceArray(payload.size - 64 - 4 - 4 until payload.size - 64 - 4)
        assertContentEquals(
            byteArrayOf(0x27, 0x00, 0x00, 0x00),
            specVersionBytes,
            "specVersion should be 39 in LE 4 bytes"
        )

        // Verify transactionVersion (LE 4 bytes) before genesisHash
        // transactionVersion = 5 → LE: [0x05, 0x00, 0x00, 0x00]
        val txVersionBytes = payload.sliceArray(payload.size - 64 - 4 until payload.size - 64)
        assertContentEquals(
            byteArrayOf(0x05, 0x00, 0x00, 0x00),
            txVersionBytes,
            "transactionVersion should be 5 in LE 4 bytes"
        )
    }

    @Test
    fun toU8aEncodingVersionByteAndCompactLength() {
        val builder = buildTestExtrinsic()
        val u8a = builder.toU8a()

        // Decode compact length prefix
        val (decodedLen, prefixBytes) = ScaleCodec.compactFromU8a(u8a)
        val bodyLen = u8a.size - prefixBytes
        assertEquals(BigInteger(bodyLen), decodedLen, "Compact prefix should encode body length")

        // Version byte (132 = 0x84) is the first byte after the compact length prefix
        assertEquals(132.toByte(), u8a[prefixBytes], "Version byte should be 132 (0x84)")
    }

    @Test
    fun toHexFormatVerifyPrefix() {
        val builder = buildTestExtrinsic()
        val hex = builder.toHex()

        assertTrue(hex.startsWith("0x"), "toHex() should start with '0x'")

        // Verify it's valid hex after the prefix
        val hexBody = hex.removePrefix("0x")
        assertTrue(hexBody.isNotEmpty(), "Hex body should not be empty")
        assertTrue(
            hexBody.all { it in '0'..'9' || it in 'a'..'f' },
            "Hex body should contain only lowercase hex chars"
        )

        // Verify round-trip: toHex decodes back to toU8a
        val decoded = hexBody.fromHexToByteArray()
        assertContentEquals(builder.toU8a(), decoded, "toHex should decode back to toU8a bytes")
    }

    @Test
    fun signApplyKnownSignatureVerifyInOutput() {
        val knownSig = "ab".repeat(64) // 64-byte signature
        val builder = buildTestExtrinsic(signatureHex = knownSig)
        val u8a = builder.toU8a()

        // The signature bytes should appear in the encoded output
        val sigBytes = knownSig.fromHexToByteArray()
        val u8aHex = u8a.toHexString()

        // Signature appears after version byte + signer public key (32 bytes)
        // Find the signature in the output
        assertTrue(
            u8aHex.contains(knownSig),
            "Encoded extrinsic should contain the signature bytes"
        )
    }
}
