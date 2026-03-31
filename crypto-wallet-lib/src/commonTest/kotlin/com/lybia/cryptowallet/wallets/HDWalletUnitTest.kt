package com.lybia.cryptowallet.wallets

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTDerivationNode
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39Exception
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTLanguages
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for BIP39/BIP32/BIP44 — TREZOR reference vectors and known test cases.
 */
class HDWalletUnitTest {

    // ── BIP39 Tests ──

    @Test
    fun bip39TrezorVector1() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = ACTBIP39.deterministicSeedString(mnemonic, "TREZOR")
        val expected = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
        assertEquals(expected, seed)
    }

    @Test
    fun bip39TrezorVector2() {
        val mnemonic = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        val seed = ACTBIP39.deterministicSeedString(mnemonic, "TREZOR")
        val expected = "ac27495480225222079d7be181583751e86f571027b0497b5b5d11218e0a8a13332572917f0f8e5a589620c6f15b11c61dee327651a14c34e18231052e48c069"
        assertEquals(expected, seed)
    }

    @Test
    fun bip39GenerateMnemonic12Words() {
        val mnemonic = ACTBIP39.generateMnemonic(128, ACTLanguages.English)
        assertEquals(12, mnemonic.split(" ").size)
    }

    @Test
    fun bip39GenerateMnemonic24Words() {
        val mnemonic = ACTBIP39.generateMnemonic(256, ACTLanguages.English)
        assertEquals(24, mnemonic.split(" ").size)
    }

    @Test
    fun bip39InvalidStrengthThrows() {
        assertFailsWith<ACTBIP39Exception> {
            ACTBIP39.generateMnemonic(100, ACTLanguages.English)
        }
    }

    @Test
    fun bip39EntropyRoundTrip() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val entropy = ACTBIP39.entropyString(mnemonic)
        assertEquals("00000000000000000000000000000000", entropy)
    }

    @Test
    fun bip39CorrectMnemonicIdempotent() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val corrected = ACTBIP39.correctMnemonic(mnemonic)
        assertEquals(mnemonic, corrected)
    }

    @Test
    fun bip39DetectLanguageEnglish() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val lang = ACTLanguages.detectTypeWithMnemonic(mnemonic)
        assertEquals(ACTLanguages.English, lang)
    }

    // ── BIP32 Tests ──

    @Test
    fun bip32MasterKeyFromSeed() {
        val seed = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(seed, network)
        assertEquals("e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35", masterKey.raw!!.toHexString())
        assertEquals("873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508", masterKey.chainCode!!.toHexString())
    }

    @Test
    fun bip32HardenedChildDerivation() {
        val seed = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(seed, network)
        val child = masterKey.derived(ACTDerivationNode(0, true))
        assertEquals("edb2e14f9ee77d26dd93b4ecede8d16ed408ce149b6cd80b0715a2d911a0afea", child.raw!!.toHexString())
    }

    @Test
    fun bip32ExtendedKeyFormat() {
        val seed = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(seed, network)
        val xprv = masterKey.extended()
        assertTrue(xprv.startsWith("xprv"))
        val xpub = masterKey.publicKey().extended()
        assertTrue(xpub.startsWith("xpub"))
    }

    @Test
    fun bip32PublicKeyCompressed33Bytes() {
        val seed = "000102030405060708090a0b0c0d0e0f".fromHexToByteArray()
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val masterKey = ACTPrivateKey(seed, network)
        val pubKey = masterKey.publicKey()
        assertEquals(33, pubKey.raw!!.size)
    }

    // ── BIP44 Address Tests ──

    @Test
    fun bip44BitcoinAddressGeneration() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val network = ACTNetwork(ACTCoin.Bitcoin, false)
        val wallet = ACTHDWallet(mnemonic)
        val pubKey = wallet.generateExternalPublicKey(0, network)
        val address = ACTAddress(pubKey).rawAddressString()
        assertTrue(address.isNotEmpty())
        // Bitcoin P2PKH address should start with 1 or 3
        assertTrue(address.startsWith("1") || address.startsWith("3"),
            "Bitcoin mainnet address should start with 1 or 3, got: $address")
    }

    @Test
    fun bip44EthereumAddressGeneration() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val network = ACTNetwork(ACTCoin.Ethereum, false)
        val wallet = ACTHDWallet(mnemonic)
        val pubKey = wallet.generateExternalPublicKey(0, network)
        val address = ACTAddress(pubKey).rawAddressString()
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length, "Ethereum address should be 42 chars (0x + 40 hex)")
    }

    @Test
    fun bip44RippleAddressGeneration() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val network = ACTNetwork(ACTCoin.Ripple, false)
        val wallet = ACTHDWallet(mnemonic)
        val pubKey = wallet.generateExternalPublicKey(0, network)
        val address = ACTAddress(pubKey).rawAddressString()
        assertTrue(address.isNotEmpty())
        assertTrue(address.startsWith("r"), "Ripple address should start with 'r', got: $address")
    }

    @Test
    fun bip44AddressConsistency() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val coins = listOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Ripple)
        for (coin in coins) {
            val network = ACTNetwork(coin, false)
            val wallet1 = ACTHDWallet(mnemonic)
            val wallet2 = ACTHDWallet(mnemonic)
            val addr1 = ACTAddress(wallet1.generateExternalPublicKey(0, network)).rawAddressString()
            val addr2 = ACTAddress(wallet2.generateExternalPublicKey(0, network)).rawAddressString()
            assertEquals(addr1, addr2, "Same mnemonic should produce same ${coin.nameCoin()} address")
        }
    }

    // ── Edge Cases ──

    @Test
    fun emptyMnemonicThrows() {
        assertFailsWith<Exception> {
            ACTBIP39.entropyString("")
        }
    }

    @Test
    fun invalidMnemonicThrows() {
        assertFailsWith<Exception> {
            ACTBIP39.entropyString("invalid words that are not in bip39 wordlist at all")
        }
    }
}
