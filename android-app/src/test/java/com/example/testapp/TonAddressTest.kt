package com.example.testapp

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.wallets.ton.TonManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonAddressTest {

    @Test
    fun testTonAddressGeneration() {
        val mnemonic = ""
        val mnemonic24 =
            "push dawn mercy parade famous armor saddle caught profit gauge sunny bonus verify grape involve ensure reject duty pottery soap surround have napkin magnet"
        Config.shared.setNetwork(Network.TESTNET)

        // Test Mainnet
//        Config.shared.setNetwork(Network.MAINNET)
        var tonManager = TonManager(mnemonic)
        var address = tonManager.getAddress()
        println("address 12: $address")
//        assertEquals("UQCrSeQwZGKy9KRQMyz0dTuKomxJ1WHFkHs5A2sn1ubgWPcu", address)

//        tonManager = TonManager(mnemonic24)
//        address = tonManager.getAddress()
//        println("address 24: $address")
//        assertEquals("UQByrCknMpLynnPTjz6w_-Xn3dbqiGoEYo29jdqolDVWBezb", address)

        // Test Testnet
//        Config.shared.setNetwork(Network.TESTNET)
//        tonManager = TonManager(mnemonic)
//        address = tonManager.getAddress()
//        println("address 12: $address")
        assertEquals("0QDDGFDUxb25-19TkjQts1vdxjlDpWKu97B7BKl1KIyYbMev", address)

//        tonManager = TonManager(mnemonic24)
//        address = tonManager.getAddress()
//        println("address 24: $address")
//        assertEquals("0QByrCknMpLynnPTjz6w_-Xn3dbqiGoEYo29jdqolDVWBVdR", address)
    }

//    @Test
//    fun testTonBalanceAndHistory() = runBlocking {
//        val testAddressW5 = "0QByrCknMpLynnPTjz6w_-Xn3dbqiGoEYo29jdqolDVWBVdR"
//        val mnemonic =
//            "push dawn mercy parade famous armor saddle caught profit gauge sunny bonus verify grape involve ensure reject duty pottery soap surround have napkin magnet"
//
//        Config.shared.setNetwork(Network.TESTNET)
//        val tonManager = TonManager(mnemonic)
//        val coinNetwork =
//            CoinNetwork(NetworkName.TON)
//
//        // Check Balance
//        val balance = tonManager.getBalance(testAddressW5, coinNetwork)
//        println("Balance for $testAddressW5: $balance TON")
//        assertTrue("Balance should be > 1, but was $balance", balance > 1.0)
//
//        // Check History
//        val history =
//            tonManager.getTransactionHistory(testAddressW5, coinNetwork) as? List<*>
//        println("History for $testAddressW5: ${history?.size} transactions")
//        assertNotNull("History should not be null", history)
//        assertTrue("History should not be empty", history?.isNotEmpty() == true)
//    }
}
