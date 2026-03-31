package com.lybia.cryptowallet.wallets.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTDerivationNode
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPrivateKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Validates: Requirements 3.1, 3.6, 4.2, 4.3, 4.8**
 *
 * Property 3: BIP32 key derivation determinism and validity
 * - Same seed + path → same key pair byte-for-byte
 * - Private key 32 bytes (Secp256k1), public key 33 bytes compressed (Secp256k1)
 *
 * Property 8: BIP32 key derivation equivalence (test vectors)
 */
class BIP32DerivationPropertyTest {

    // BIP32 Test Vector 1 (from BIP32 specification)
    // Seed: 000102030405060708090a0b0c0d0e0f
    private val testSeed1 = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()

    // Expected master key from seed (Chain m)
    private val expectedMasterPrivKey = "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
    private val expectedMasterChainCode = "873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508"

    data class DerivationVector(
        val coin: ACTCoin,
        val seed: ByteArray,
        val expectedPrivKeyHex: String,
        val expectedChainCodeHex: String
    )

    @Test
    fun masterKeyDerivationMatchesBIP32Vectors() = runTest {
        val vectors = listOf(
            DerivationVector(
                ACTCoin.Bitcoin, testSeed1,
                expectedMasterPrivKey, expectedMasterChainCode
            )
        )
        val vectorArb = Arb.element(vectors)
        checkAll(100, vectorArb) { vector ->
            val network = ACTNetwork(vector.coin, false)
            val masterKey = ACTPrivateKey(vector.seed, network)
            assertNotNull(masterKey.raw)
            assertNotNull(masterKey.chainCode)
            assertEquals(vector.expectedPrivKeyHex, masterKey.raw!!.toHexString(),
                "Master private key mismatch")
            assertEquals(vector.expectedChainCodeHex, masterKey.chainCode!!.toHexString(),
                "Master chain code mismatch")
        }
    }

    @Test
    fun childKeyDerivationMatchesBIP32Vectors() = runTest {
        // BIP32 Test Vector 1: Chain m/0'
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(testSeed1, network)
        val childKey = masterKey.derived(ACTDerivationNode(0, true))

        // Expected values from BIP32 spec for m/0'
        val expectedChildPriv = "edb2e14f9ee77d26dd93b4ecede8d16ed408ce149b6cd80b0715a2d911a0afea"
        val expectedChildChain = "47fdacbd0f1097043b78c63c20c34ef4ed9a111d980047ad16282c7ae6236141"

        assertEquals(expectedChildPriv, childKey.raw!!.toHexString())
        assertEquals(expectedChildChain, childKey.chainCode!!.toHexString())
    }

    @Test
    fun publicKeyGenerationWorks() = runTest {
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(testSeed1, network)
        val pubKey = masterKey.publicKey()
        assertNotNull(pubKey.raw)
        // Compressed public key should be 33 bytes
        assertEquals(33, pubKey.raw!!.size)
        // Expected master public key from BIP32 spec
        val expectedPubKey = "0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2"
        assertEquals(expectedPubKey, pubKey.raw!!.toHexString())
    }

    // ── Property 3: BIP32 key derivation determinism and validity ──

    // Multiple known seeds for property testing
    private val testSeeds = listOf(
        "000102030405060708090a0b0c0d0e0f",
        "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542",
        "4b381541583be4423346c643850da4b320e46a87ae3d2a4e6da11eba819cd4acba45d239319ac14f863b8d5ab5a0d0c64d2e8a1e7d1457df2e5a3c51c73235be"
    )

    /**
     * Feature: crypto-wallet-module, Property 3: BIP32 key derivation determinism
     *
     * **Validates: Requirements 3.1, 3.6**
     *
     * For any valid seed, deriving the master key twice must produce
     * identical private key and chain code byte-for-byte.
     */
    @Test
    fun masterKeyDerivationIsDeterministic() = runTest {
        val seedArb = Arb.element(testSeeds)
        val coinArb = Arb.element(listOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Ripple))
        checkAll(100, seedArb, coinArb) { seedHex, coin ->
            val seed = seedHex.fromHexToByteArray()
            val network = ACTNetwork(coin, false)

            val key1 = ACTPrivateKey(seed, network)
            val key2 = ACTPrivateKey(seed, network)

            assertNotNull(key1.raw, "Private key must not be null")
            assertNotNull(key2.raw, "Private key must not be null")
            assertTrue(key1.raw!!.contentEquals(key2.raw!!),
                "Same seed must produce same private key for $coin")
            assertTrue(key1.chainCode!!.contentEquals(key2.chainCode!!),
                "Same seed must produce same chain code for $coin")
        }
    }

    /**
     * Feature: crypto-wallet-module, Property 3: BIP32 child key derivation determinism
     *
     * **Validates: Requirements 3.1, 3.6**
     *
     * For any valid seed and child derivation node, deriving the same child
     * twice must produce identical key pairs.
     */
    @Test
    fun childKeyDerivationIsDeterministic() = runTest {
        val seedArb = Arb.element(testSeeds)
        val indexArb = Arb.int(0..19)
        val hardenArb = Arb.boolean()
        checkAll(100, seedArb, indexArb, hardenArb) { seedHex, index, harden ->
            val seed = seedHex.fromHexToByteArray()
            val network = ACTNetwork(ACTCoin.Bitcoin, false)
            val node = ACTDerivationNode(index, harden)

            val master1 = ACTPrivateKey(seed, network)
            val child1 = master1.derived(node)

            val master2 = ACTPrivateKey(seed, network)
            val child2 = master2.derived(node)

            assertTrue(child1.raw!!.contentEquals(child2.raw!!),
                "Same seed + path must produce same child private key (index=$index, harden=$harden)")
            assertTrue(child1.chainCode!!.contentEquals(child2.chainCode!!),
                "Same seed + path must produce same child chain code (index=$index, harden=$harden)")

            // Public keys must also match
            val pub1 = child1.publicKey()
            val pub2 = child2.publicKey()
            assertTrue(pub1.raw!!.contentEquals(pub2.raw!!),
                "Same seed + path must produce same public key (index=$index, harden=$harden)")
        }
    }

    /**
     * Feature: crypto-wallet-module, Property 3: BIP32 key size validity
     *
     * **Validates: Requirements 3.1, 3.6**
     *
     * For Secp256k1 coins: private key must be 32 bytes, compressed public key must be 33 bytes.
     */
    @Test
    fun secp256k1KeySizesAreValid() = runTest {
        val seedArb = Arb.element(testSeeds)
        val coinArb = Arb.element(listOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Ripple))
        checkAll(100, seedArb, coinArb) { seedHex, coin ->
            val seed = seedHex.fromHexToByteArray()
            val network = ACTNetwork(coin, false)
            val masterKey = ACTPrivateKey(seed, network)

            assertEquals(32, masterKey.raw!!.size,
                "Secp256k1 private key must be 32 bytes for $coin")
            assertEquals(32, masterKey.chainCode!!.size,
                "Chain code must be 32 bytes for $coin")

            val pubKey = masterKey.publicKey()
            assertEquals(33, pubKey.raw!!.size,
                "Secp256k1 compressed public key must be 33 bytes for $coin")
        }
    }
}
