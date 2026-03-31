package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTLanguages
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **Validates: Requirements 3.2**
 *
 * Property 5: BIP39 mnemonic generation validity
 * For any valid strength: word count == strength/32*3 and entropyString does not throw
 */
class BIP39GenerationPropertyTest {

    @Test
    fun generatedMnemonicHasCorrectWordCount() = runTest {
        val strengthArb = Arb.element(128, 160, 192, 224, 256)
        checkAll(100, strengthArb) { strength ->
            val mnemonic = ACTBIP39.generateMnemonic(strength, ACTLanguages.English)
            val words = mnemonic.split(" ")
            val expectedWordCount = strength / 32 * 3
            assertEquals(expectedWordCount, words.size,
                "Expected $expectedWordCount words for strength=$strength, got ${words.size}")
            // entropyString should not throw
            val entropy = ACTBIP39.entropyString(mnemonic)
            assertNotNull(entropy)
        }
    }
}
