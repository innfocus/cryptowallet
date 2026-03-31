package com.lybia.cryptowallet.wallets.bip44

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Validates: Requirements 5.2, 5.4**
 *
 * Property 10: Address generation equivalence
 * For known mnemonic + coin type: address must be non-empty and well-formed.
 */
class AddressGenerationPropertyTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun addressGenerationProducesValidAddresses() = runTest {
        val coins = listOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Ripple)
        val coinArb = Arb.element(coins)
        checkAll(100, coinArb) { coin ->
            val network = ACTNetwork(coin, false)
            val wallet = ACTHDWallet(testMnemonic)
            val pubKey = wallet.generateExternalPublicKey(0, network)
            val address = ACTAddress(pubKey)
            val addrStr = address.rawAddressString()
            assertTrue(addrStr.isNotEmpty(),
                "Address for ${coin.nameCoin()} should not be empty")
            when (coin) {
                ACTCoin.Bitcoin -> assertTrue(addrStr.length in 25..35,
                    "Bitcoin address length should be 25-35, got ${addrStr.length}")
                ACTCoin.Ethereum -> assertTrue(addrStr.startsWith("0x"),
                    "Ethereum address should start with 0x")
                ACTCoin.Ripple -> assertTrue(addrStr.isNotEmpty(),
                    "Ripple address should not be empty")
                else -> {}
            }
        }
    }

    @Test
    fun knownMnemonicProducesConsistentAddresses() = runTest {
        // Same mnemonic should always produce the same address
        val coins = listOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Ripple)
        val coinArb = Arb.element(coins)
        checkAll(100, coinArb) { coin ->
            val network = ACTNetwork(coin, false)
            val wallet1 = ACTHDWallet(testMnemonic)
            val wallet2 = ACTHDWallet(testMnemonic)
            val addr1 = ACTAddress(wallet1.generateExternalPublicKey(0, network)).rawAddressString()
            val addr2 = ACTAddress(wallet2.generateExternalPublicKey(0, network)).rawAddressString()
            assertTrue(addr1 == addr2,
                "Same mnemonic should produce same address for ${coin.nameCoin()}")
        }
    }
}
