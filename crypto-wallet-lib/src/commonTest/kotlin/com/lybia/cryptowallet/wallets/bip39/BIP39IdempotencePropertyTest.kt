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
 * **Validates: Requirements 3.5, 3.6**
 *
 * Property 6: BIP39 mnemonic correction idempotence
 * For any valid mnemonic: correctMnemonic(mnemonic) == mnemonic
 */
class BIP39IdempotencePropertyTest {

    @Test
    fun correctMnemonicIsIdempotent() = runTest {
        val strengthArb = Arb.element(128, 160, 192, 224, 256)
        checkAll(100, strengthArb) { strength ->
            val mnemonic = ACTBIP39.generateMnemonic(strength, ACTLanguages.English)
            val corrected = ACTBIP39.correctMnemonic(mnemonic)
            assertEquals(mnemonic, corrected,
                "correctMnemonic should be idempotent for valid mnemonics")
        }
    }
}
