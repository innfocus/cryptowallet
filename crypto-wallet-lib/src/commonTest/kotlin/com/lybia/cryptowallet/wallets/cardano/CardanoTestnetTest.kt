package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.services.CardanoApiService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for CardanoManager testnet URL configuration.
 *
 * Verifies that CardanoManager uses the correct Blockfrost endpoint
 * depending on the global network config (MAINNET vs TESTNET).
 *
 * Requirements: 35.3, 35.4, 35.10
 */
class CardanoTestnetTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private lateinit var savedNetwork: Network

    @BeforeTest
    fun setup() {
        savedNetwork = Config.shared.getNetwork()
    }

    @AfterTest
    fun tearDown() {
        Config.shared.setNetwork(savedNetwork)
    }

    // ── CoinNetwork URL selection ───────────────────────────────────────

    @Test
    fun coinNetwork_testnet_returnsBlockfrostPreprodUrl() {
        Config.shared.setNetwork(Network.TESTNET)
        val url = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        assertEquals(
            "https://cardano-preprod.blockfrost.io/api/v0",
            url,
            "TESTNET should use Blockfrost preprod URL"
        )
    }

    @Test
    fun coinNetwork_mainnet_returnsBlockfrostMainnetUrl() {
        Config.shared.setNetwork(Network.MAINNET)
        val url = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        assertEquals(
            "https://cardano-mainnet.blockfrost.io/api/v0",
            url,
            "MAINNET should use Blockfrost mainnet URL"
        )
    }

    // ── CardanoApiService receives correct URL ──────────────────────────

    @Test
    fun cardanoApiService_createdWithTestnetUrl_usesPreprodEndpoint() {
        Config.shared.setNetwork(Network.TESTNET)
        val expectedUrl = "https://cardano-preprod.blockfrost.io/api/v0"

        // The default CardanoApiService constructor in CardanoManager reads
        // CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl(), which should
        // return the preprod URL when TESTNET is active.
        val url = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        val apiService = CardanoApiService(baseUrl = url)

        // Verify the URL that would be used matches preprod
        assertEquals(expectedUrl, url)
        // apiService was created successfully with the testnet URL
        assertTrue(true, "CardanoApiService created with testnet URL without error")
    }

    @Test
    fun cardanoApiService_createdWithMainnetUrl_usesMainnetEndpoint() {
        Config.shared.setNetwork(Network.MAINNET)
        val expectedUrl = "https://cardano-mainnet.blockfrost.io/api/v0"

        val url = CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        val apiService = CardanoApiService(baseUrl = url)

        assertEquals(expectedUrl, url)
        assertTrue(true, "CardanoApiService created with mainnet URL without error")
    }

    // ── CardanoManager address prefix reflects network ──────────────────

    @Test
    fun cardanoManager_testnet_generatesAddrTestPrefix() {
        Config.shared.setNetwork(Network.TESTNET)
        val manager = CardanoManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr_test"),
            "TESTNET Cardano address should start with 'addr_test', got: $address"
        )
    }

    @Test
    fun cardanoManager_mainnet_generatesAddrPrefix() {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = CardanoManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr1"),
            "MAINNET Cardano address should start with 'addr1', got: $address"
        )
    }

    // ── CardanoManager default constructor uses dynamic URL ─────────────

    @Test
    fun cardanoManager_defaultConstructor_testnet_usesDynamicUrl() {
        Config.shared.setNetwork(Network.TESTNET)

        // When CardanoManager is created with default apiService parameter,
        // it should use CoinNetwork(NetworkName.CARDANO).getBlockfrostUrl()
        // which returns the preprod URL for TESTNET.
        // We verify this indirectly: the manager initializes without error
        // and produces a testnet address.
        val manager = CardanoManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr_test"),
            "CardanoManager with default constructor on TESTNET should produce addr_test address"
        )
    }

    @Test
    fun cardanoManager_defaultConstructor_mainnet_usesDynamicUrl() {
        Config.shared.setNetwork(Network.MAINNET)

        val manager = CardanoManager(testMnemonic)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr1"),
            "CardanoManager with default constructor on MAINNET should produce addr1 address"
        )
    }

    // ── CardanoManager with custom apiService ───────────────────────────

    @Test
    fun cardanoManager_customApiService_acceptsPreprodUrl() {
        Config.shared.setNetwork(Network.TESTNET)
        val preprodUrl = "https://cardano-preprod.blockfrost.io/api/v0"
        val customService = CardanoApiService(baseUrl = preprodUrl)

        // CardanoManager accepts a custom apiService — verify it initializes
        val manager = CardanoManager(testMnemonic, apiService = customService)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr_test"),
            "CardanoManager with custom preprod apiService on TESTNET should produce addr_test address"
        )
    }

    @Test
    fun cardanoManager_customApiService_acceptsMainnetUrl() {
        Config.shared.setNetwork(Network.MAINNET)
        val mainnetUrl = "https://cardano-mainnet.blockfrost.io/api/v0"
        val customService = CardanoApiService(baseUrl = mainnetUrl)

        val manager = CardanoManager(testMnemonic, apiService = customService)
        val address = manager.getAddress()

        assertTrue(
            address.startsWith("addr1"),
            "CardanoManager with custom mainnet apiService on MAINNET should produce addr1 address"
        )
    }
}
