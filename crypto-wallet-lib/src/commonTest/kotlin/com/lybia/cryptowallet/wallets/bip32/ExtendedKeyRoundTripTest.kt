package com.lybia.cryptowallet.wallets.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTDerivationNode
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPrivateKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Validates: Requirements 4.5**
 *
 * Property 9: Extended key serialization round-trip
 * For any valid ACTPrivateKey/ACTPublicKey: extended() creates a valid Base58 string.
 */
class ExtendedKeyRoundTripTest {

    private val testSeed = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()

    @Test
    fun extendedKeyProducesValidBase58() = runTest {
        val coins = listOf(ACTCoin.Bitcoin)
        val coinArb = Arb.element(coins)
        checkAll(100, coinArb) { coin ->
            val network = ACTNetwork(coin, false)
            val masterKey = ACTPrivateKey(testSeed, network)
            val extPriv = masterKey.extended()
            assertTrue(extPriv.isNotEmpty(), "Extended private key should not be empty")
            assertTrue(extPriv.startsWith("xprv"), "Extended private key should start with xprv")

            val pubKey = masterKey.publicKey()
            val extPub = pubKey.extended()
            assertTrue(extPub.isNotEmpty(), "Extended public key should not be empty")
            assertTrue(extPub.startsWith("xpub"), "Extended public key should start with xpub")
        }
    }

    @Test
    fun extendedKeyMatchesBIP32Vectors() = runTest {
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(testSeed, network)

        // BIP32 Test Vector 1: Chain m
        val expectedXprv = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
        val expectedXpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

        assertEquals(expectedXprv, masterKey.extended())
        assertEquals(expectedXpub, masterKey.publicKey().extended())
    }
}
