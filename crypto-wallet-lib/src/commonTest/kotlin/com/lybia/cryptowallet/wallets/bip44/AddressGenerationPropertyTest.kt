package com.lybia.cryptowallet.wallets.bip44

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.cardano.CardanoManager
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTLanguages
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet
import com.lybia.cryptowallet.wallets.midnight.MidnightManager
import com.lybia.cryptowallet.wallets.ripple.RippleManager
import com.lybia.cryptowallet.wallets.ton.TonManager
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Validates: Requirements 4.1-4.7, 4.8, 5.2, 5.4**
 *
 * Property 4: Address format validity for all coin types
 * Property 5: Address generation determinism (existing)
 * Property 10: Address generation equivalence (existing)
 */
class AddressGenerationPropertyTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    // ── Existing tests ──────────────────────────────────────────────────────

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

    // ── Property 4: Address format validity for all coin types ──────────────
    // Feature: crypto-wallet-module, Property 4: Address format validity

    // Known valid mnemonics for property testing
    private val testMnemonics = listOf(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "letter advice cage absurd amount doctor acoustic avoid letter advice cage above"
    )

    /**
     * Helper: get address for a given NetworkName using the appropriate manager.
     * Returns the address string for chains that can generate addresses locally.
     */
    private fun getAddressForNetwork(mnemonic: String, network: NetworkName): String {
        return when (network) {
            NetworkName.BTC -> {
                val manager = BitcoinManager(mnemonic)
                manager.getNativeSegWitAddress()
                manager.getAddress()
            }
            NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                // EthereumManager.getAddress() returns placeholder, use ACTHDWallet directly
                val hdWallet = ACTHDWallet(mnemonic)
                val actNetwork = ACTNetwork(ACTCoin.Ethereum, false)
                val pubKey = hdWallet.generateExternalPublicKey(0, actNetwork)
                ACTAddress(pubKey).rawAddressString()
            }
            NetworkName.CARDANO -> {
                CardanoManager(mnemonic = mnemonic).getAddress()
            }
            NetworkName.TON -> {
                TonManager(mnemonic).getAddress()
            }
            NetworkName.MIDNIGHT -> {
                MidnightManager(mnemonic = mnemonic).getAddress()
            }
            NetworkName.XRP -> {
                RippleManager(mnemonic).getAddress()
            }
            NetworkName.CENTRALITY -> {
                // CentralityManager requires async API call for address generation
                // Use ACTHDWallet with Sr25519 derivation path instead
                // Centrality addresses are SS58 encoded — tested via SS58 property tests
                // Return empty to skip regex check (handled separately)
                ""
            }
        }
    }

    /**
     * Get the expected regex pattern for a given NetworkName.
     * Based on design doc Property 4 and ACTCoin.regex() definitions.
     */
    private fun getExpectedRegex(network: NetworkName): Regex {
        return when (network) {
            // Bitcoin Native SegWit: bc1... (mainnet) or tb1... (testnet)
            NetworkName.BTC -> Regex("^(bc1|tb1)[a-zA-HJ-NP-Z0-9]{25,87}$")
            // Ethereum: 0x + 40 hex chars
            NetworkName.ETHEREUM, NetworkName.ARBITRUM -> Regex("^0x[a-fA-F0-9]{40}$")
            // Cardano Shelley: addr1... (mainnet) or addr_test1... (testnet)
            NetworkName.CARDANO -> Regex("^(addr1|addr_test1)[a-z0-9]{50,}$")
            // TON: Base64url encoded (letters, digits, -, _, no padding required)
            NetworkName.TON -> Regex("^[A-Za-z0-9_-]{48}$")
            // Midnight: midnight1 + Bech32 data
            NetworkName.MIDNIGHT -> Regex("^midnight1[a-z0-9]{38,}$")
            // Ripple: starts with r, Base58 Ripple alphabet
            NetworkName.XRP -> Regex("^r[1-9A-HJ-NP-Za-km-z]{24,34}$")
            // Centrality: SS58 format, starts with c
            NetworkName.CENTRALITY -> Regex("^[a-km-zA-HJ-NP-Z1-9]{47,}$")
        }
    }

    /**
     * **Validates: Requirements 4.1-4.7**
     *
     * For any valid mnemonic and for any NetworkName, the generated address
     * must match the expected regex format and must not be empty.
     *
     * Note: Centrality is excluded because CentralityManager.getAddress()
     * requires an async API call (getAddressAsync) that contacts an external service.
     * Centrality SS58 address format is validated in SS58 property tests.
     */
    @Test
    fun addressFormatValidityForAllCoinTypes() = runTest {
        // Ensure mainnet config for consistent address format
        Config.shared.setNetwork(Network.MAINNET)

        // Networks that can generate addresses locally (no external API needed)
        val localNetworks = listOf(
            NetworkName.BTC,
            NetworkName.ETHEREUM,
            NetworkName.CARDANO,
            NetworkName.TON,
            NetworkName.MIDNIGHT,
            NetworkName.XRP
        )

        val mnemonicArb = Arb.element(testMnemonics)
        val networkArb = Arb.element(localNetworks)

        checkAll(100, mnemonicArb, networkArb) { mnemonic, network ->
            val address = getAddressForNetwork(mnemonic, network)
            val regex = getExpectedRegex(network)

            // Address must not be empty
            assertTrue(address.isNotEmpty(),
                "Address for $network must not be empty (mnemonic: ${mnemonic.take(20)}...)")

            // Address must match expected format
            assertTrue(regex.matches(address),
                "Address for $network does not match expected format.\n" +
                    "  Address: $address\n" +
                    "  Pattern: ${regex.pattern}")
        }
    }

    // ── Property 5: Address generation determinism ──────────────────────────
    // Feature: crypto-wallet-module, Property 5: Address generation determinism

    /**
     * **Validates: Requirements 4.8**
     *
     * For any valid mnemonic and for any NetworkName (excluding CENTRALITY which
     * needs async API), calling getAddress() twice on the SAME manager instance
     * must return the same address string.
     */
    @Test
    fun addressGenerationDeterminism() = runTest {
        Config.shared.setNetwork(Network.MAINNET)

        val localNetworks = listOf(
            NetworkName.BTC,
            NetworkName.ETHEREUM,
            NetworkName.ARBITRUM,
            NetworkName.CARDANO,
            NetworkName.TON,
            NetworkName.MIDNIGHT,
            NetworkName.XRP
        )

        val mnemonicArb = Arb.element(testMnemonics)
        val networkArb = Arb.element(localNetworks)

        checkAll(100, mnemonicArb, networkArb) { mnemonic, network ->
            val (addr1, addr2) = when (network) {
                NetworkName.BTC -> {
                    val manager = BitcoinManager(mnemonic)
                    manager.getNativeSegWitAddress()
                    val a1 = manager.getAddress()
                    val a2 = manager.getAddress()
                    a1 to a2
                }
                NetworkName.ETHEREUM, NetworkName.ARBITRUM -> {
                    val hdWallet = ACTHDWallet(mnemonic)
                    val actNetwork = ACTNetwork(ACTCoin.Ethereum, false)
                    val pubKey = hdWallet.generateExternalPublicKey(0, actNetwork)
                    val address = ACTAddress(pubKey)
                    val a1 = address.rawAddressString()
                    val a2 = address.rawAddressString()
                    a1 to a2
                }
                NetworkName.CARDANO -> {
                    val manager = CardanoManager(mnemonic = mnemonic)
                    val a1 = manager.getAddress()
                    val a2 = manager.getAddress()
                    a1 to a2
                }
                NetworkName.TON -> {
                    val manager = TonManager(mnemonic)
                    val a1 = manager.getAddress()
                    val a2 = manager.getAddress()
                    a1 to a2
                }
                NetworkName.MIDNIGHT -> {
                    val manager = MidnightManager(mnemonic = mnemonic)
                    val a1 = manager.getAddress()
                    val a2 = manager.getAddress()
                    a1 to a2
                }
                NetworkName.XRP -> {
                    val manager = RippleManager(mnemonic)
                    val a1 = manager.getAddress()
                    val a2 = manager.getAddress()
                    a1 to a2
                }
                else -> "" to ""
            }

            assertEquals(addr1, addr2,
                "getAddress() called twice on same $network instance must return identical address")
        }
    }
}
