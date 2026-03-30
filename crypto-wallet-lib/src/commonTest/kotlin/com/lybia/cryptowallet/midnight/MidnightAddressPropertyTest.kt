package com.lybia.cryptowallet.midnight

import com.lybia.cryptowallet.wallets.midnight.MidnightAddress
import fr.acinq.bitcoin.MnemonicCode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for Midnight address generation.
 */
class MidnightAddressPropertyTest {

    /**
     * Generate a valid BIP-39 12-word mnemonic from random 16-byte entropy.
     */
    private fun arbMnemonic(): Arb<String> = Arb.byteArray(Arb.constant(16), Arb.byte()).map { entropy ->
        MnemonicCode.toMnemonics(entropy).joinToString(" ")
    }

    // Feature: cardano-midnight-support, Property 17: Midnight Address from Mnemonic
    // **Validates: Requirements 8.1**
    @Test
    fun midnightAddressFromMnemonicIsDeterministicAndNonEmpty() = runTest {
        checkAll(100, arbMnemonic()) { mnemonic ->
            val address1 = MidnightAddress.fromMnemonic(mnemonic)
            val address2 = MidnightAddress.fromMnemonic(mnemonic)

            // Address should be non-empty
            assertTrue(address1.isNotEmpty(), "Midnight address should be non-empty")

            // Same mnemonic should always produce the same address (deterministic)
            assertEquals(address1, address2, "Same mnemonic should produce same address")

            // Address should start with "midnight" prefix (Bech32)
            assertTrue(address1.startsWith("midnight1"), "Address should start with midnight1, got: $address1")

            // Address should be a valid Midnight address
            assertTrue(MidnightAddress.isValid(address1), "Generated address should be valid")
        }
    }
}
