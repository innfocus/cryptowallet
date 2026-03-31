package com.lybia.cryptowallet.wallets.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTDerivationNode
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPrivateKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **Validates: Requirements 4.2, 4.3, 4.8**
 *
 * Property 8: BIP32 key derivation equivalence
 * Verify with BIP32 test vectors: known seed + derivation path → expected keys.
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
}
