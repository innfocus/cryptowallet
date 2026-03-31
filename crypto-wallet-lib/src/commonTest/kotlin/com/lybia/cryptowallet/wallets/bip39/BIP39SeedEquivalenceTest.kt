package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Validates: Requirements 3.4, 3.7**
 *
 * Property 7: BIP39 seed generation equivalence
 * Verify deterministicSeedString output matches BIP39 TREZOR reference test vectors.
 */
class BIP39SeedEquivalenceTest {

    // TREZOR BIP39 test vectors (mnemonic → seed with passphrase "TREZOR")
    private val testVectors = listOf(
        Pair(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
        ),
        Pair(
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607"
        ),
        Pair(
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
            "d71de856f81a8acc65e6fc851a38d4d7ec216fd0796d0a6827a3ad6ed5511a30fa280f12eb2e47ed2ac03b5c462a0358d18d69fe4f985ec81778c1b370b652a8"
        ),
        Pair(
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
            "ac27495480225222079d7be181583751e86f571027b0497b5b5d11218e0a8a13332572917f0f8e5a589620c6f15b11c61dee327651a14c34e18231052e48c069"
        )
    )

    @Test
    fun seedMatchesTrezorVectors() = runTest {
        val vectorArb = Arb.element(testVectors)
        checkAll(100, vectorArb) { (mnemonic, expectedSeed) ->
            val seed = ACTBIP39.deterministicSeedString(mnemonic, "TREZOR")
            assertEquals(expectedSeed, seed,
                "Seed mismatch for mnemonic: ${mnemonic.take(30)}...")
        }
    }
}
