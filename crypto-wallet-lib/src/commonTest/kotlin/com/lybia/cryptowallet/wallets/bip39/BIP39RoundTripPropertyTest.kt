package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTLanguages
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
