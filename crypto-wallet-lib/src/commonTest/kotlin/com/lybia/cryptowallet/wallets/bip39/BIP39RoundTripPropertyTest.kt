package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTLanguages
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Validates: Requirements 3.3**
 *
 * Property 4: BIP39 mnemonic round-trip
 * For any valid mnemonic: mnemonicString(entropyString(mnemonic)) == mnemonic
 */
class BIP39RoundTripPropertyTest {

    @Test
    fun mnemonicRoundTrip() = runTest {
        val strengthArb = Arb.element(128, 160, 192, 224, 256)
        checkAll(100, strengthArb) { strength ->
            val mnemonic = ACTBIP39.generateMnemonic(strength, ACTLanguages.English)
            val entropy = ACTBIP39.entropyString(mnemonic)
            val reconstructed = ACTBIP39.mnemonicString(entropy, ACTLanguages.English)
            assertEquals(mnemonic, reconstructed,
                "Round-trip failed for strength=$strength")
        }
    }

    /**
     * Feature: crypto-wallet-module, Property 2: BIP39 seed determinism
     *
     * **Validates: Requirements 2.3, 2.6**
     *
     * For any valid mnemonic and any passphrase, calling deterministicSeedString
     * twice with the same inputs must return the same seed hex string.
     */
    @Test
    fun seedGenerationIsDeterministic() = runTest {
        val strengthArb = Arb.element(128, 160, 192, 224, 256)
        val passphraseArb = Arb.string(0..32)
        checkAll(100, strengthArb, passphraseArb) { strength, passphrase ->
            val mnemonic = ACTBIP39.generateMnemonic(strength, ACTLanguages.English)

            val seed1 = ACTBIP39.deterministicSeedString(mnemonic, passphrase)
            val seed2 = ACTBIP39.deterministicSeedString(mnemonic, passphrase)

            assertEquals(seed1, seed2,
                "deterministicSeedString must return the same seed for identical inputs")
            assertTrue(seed1.isNotEmpty(), "Seed must not be empty")
            // BIP39 seed is 64 bytes = 128 hex chars
            assertEquals(128, seed1.length,
                "BIP39 seed must be 64 bytes (128 hex chars)")
        }
    }
}
