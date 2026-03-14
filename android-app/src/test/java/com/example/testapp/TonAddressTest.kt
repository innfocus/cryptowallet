package com.example.testapp

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.wallets.ton.TonManager
import org.junit.Assert.assertEquals
import org.junit.Test

class TonAddressTest {

    @Test
    fun testTonAddressGeneration() {
        val mnemonic = "push dawn mercy parade famous armor saddle caught profit gauge sunny bonus verify grape involve ensure reject duty pottery soap surround have napkin magnet"
        
        // Test Mainnet
        Config.shared.setNetwork(Network.MAINNET)
        var tonManager = TonManager(mnemonic)
        var address = tonManager.getAddress()
        println(address)
        assertEquals("UQByrCknMpLynnPTjz6w_-Xn3dbqiGoEYo29jdqolDVWBezb", address)

        // Test Testnet
        Config.shared.setNetwork(Network.TESTNET)
        tonManager = TonManager(mnemonic)
        address = tonManager.getAddress()
        println(address)
        assertEquals("0QByrCknMpLynnPTjz6w_-Xn3dbqiGoEYo29jdqolDVWBVdR", address)
    }
}
