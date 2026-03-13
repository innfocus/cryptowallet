package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TonManagerTest {

    private val testMnemonic =
        "left arena awkward spin damp pipe liar ribbon few husband execute whisper"

    @Test
    fun testGetAddressMainnet() {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val address = tonManager.getAddress()

        // V4R2 addresses typically start with 'EQ' (Bounceable) or 'UQ' (Non-bounceable)
        // Since I used bounceable = false in getAddress(), it should start with 'U'
        assertTrue(address.startsWith("U"), "Address should be non-bounceable by default")
        assertEquals(48, address.length, "TON user-friendly address length should be 48 characters")
        println("Mainnet Address: $address")
    }

    @Test
    fun testGetAddressTestnet() {
        Config.shared.setNetwork(Network.TESTNET)
        val tonManager = TonManager(testMnemonic)
        val address = tonManager.getAddress()

        // Testnet addresses typically start with 'k' (Bounceable) or '0' (Non-bounceable)
        // Wait, for non-bounceable testnet it might be different.
        // Actually, in ton-kotlin 0.5.0, toString(testOnly = true) should produce a testnet format.
        println("Testnet Address: $address")
        assertEquals(48, address.length, "TON user-friendly address length should be 48 characters")
    }
}
