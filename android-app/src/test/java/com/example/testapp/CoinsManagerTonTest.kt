package com.example.testapp

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.coinkits.BalanceHandle
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.EstimateFeeHandle
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.TransationData
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.coinkits.services.TokenBalanceHandle
import com.lybia.cryptowallet.coinkits.services.TokenTransactionsHandle
import com.lybia.cryptowallet.coinkits.services.NFTListHandle
import com.lybia.cryptowallet.coinkits.models.TokenInfo
import com.lybia.cryptowallet.coinkits.models.NFTItem
import com.google.gson.JsonObject
import org.junit.Assert.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for TON integration in CoinsManager.
 * Focuses on Testnet operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoinsManagerTonTest {

    private val mnemonic =
        "push dawn mercy parade famous armor saddle caught profit gauge sunny bonus verify grape involve ensure reject duty pottery soap surround have napkin magnet"
    // Address (Testnet): 0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        println("DEBUG: CoinsManagerTonTest.setUp called")
        Dispatchers.setMain(testDispatcher)
        Config.shared.setNetwork(Network.TESTNET)
        // Using the new safe update method we just implemented
        CoinsManager.shared.updateMnemonic(mnemonic)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGetTonAddress() {
        val address = CoinsManager.shared.firstAddress(ACTCoin.TON)
        assertNotNull("TON Address should not be null", address)
        // v4r2 testnet address for this mnemonic
        assertEquals(
            "0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc",
            address?.rawAddressString()
        )
    }

    @Test
    fun testGetTonBalance() {
        val latch = CountDownLatch(1)
        var balanceResult = 0.0
        var successResult = false

        CoinsManager.shared.getBalance(ACTCoin.TON, object : BalanceHandle {
            override fun completionHandler(balance: Double, success: Boolean) {
                balanceResult = balance
                successResult = success
                latch.countDown()
            }
        })

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Request timed out", completed)
        assertTrue("Balance request should succeed", successResult)
        assertTrue("Balance should be >= 0, was $balanceResult", balanceResult >= 0)
        println("TON Balance: $balanceResult")
    }

    @Test
    fun testGetTonTransactions() {
        val latch = CountDownLatch(1)
        var transactionsResult: Array<TransationData>? = null
        var errStrResult = ""

        CoinsManager.shared.getTransactions(ACTCoin.TON, null, object : TransactionsHandle {
            override fun completionHandler(
                transactions: Array<TransationData>?,
                moreParam: JsonObject?,
                errStr: String
            ) {
                transactionsResult = transactions
                errStrResult = errStr
                latch.countDown()
            }
        })

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Request timed out", completed)
        assertNotNull("Transactions should not be null", transactionsResult)
        assertEquals("Error string should be empty", "", errStrResult)
        println("TON Transactions count: ${transactionsResult?.size}")

        if (transactionsResult != null && transactionsResult!!.isNotEmpty()) {
            val tx = transactionsResult!![0]
            assertNotNull("Tx ID should not be null", tx.iD)
            assertTrue("Tx Amount should be >= 0", tx.amount >= 0)
        }
    }

//    @Test
//    fun testEstimateTonFee() {
//        val latch = CountDownLatch(1)
//        var feeResult = 0.0
//        var errStrResult = ""
//
//        val network = ACTNetwork(ACTCoin.TON, true) // Testnet
//        CoinsManager.shared.estimateFee(
//            amount = 0.01,
//            serAddressStr = "0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc",
//            paramFee = 0.0,
//            network = network,
//            completionHandler = object : EstimateFeeHandle {
//                override fun completionHandler(estimateFee: Double, errStr: String) {
//                    feeResult = estimateFee
//                    errStrResult = errStr
//                    latch.countDown()
//                }
//            }
//        )
//
//        val completed = latch.await(30, TimeUnit.SECONDS)
//        assertTrue("Request timed out", completed)
//        assertTrue("Fee should be > 0, was $feeResult", feeResult > 0)
//        assertEquals("Error string should be empty", "", errStrResult)
//        println("Estimated TON Fee: $feeResult")
//    }

    @Test
    fun testGetJettonBalance() {
        val latch = CountDownLatch(1)
        var tokenInfoResult: TokenInfo? = null
        var successResult = false

        // Example Jetton on Testnet: USD₮ (EQCxE6mUtQJKFnGfaROTpIseLRdsOTnBGY7o6vOWhuX9A_7f)
        val jettonMaster = "EQCxE6mUtQJKFnGfaROTpIseLRdsOTnBGY7o6vOWhuX9A_7f"
        val address = "0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc"

        CoinsManager.shared.getTokenBalance(
            ACTCoin.TON,
            address,
            jettonMaster,
            object : TokenBalanceHandle {
                override fun completionHandler(
                    tokenInfo: TokenInfo?,
                    success: Boolean,
                    errStr: String
                ) {
                    tokenInfoResult = tokenInfo
                    successResult = success
                    latch.countDown()
                }
            })

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Request timed out", completed)
        assertTrue("Jetton balance request should succeed", successResult)
        assertNotNull("TokenInfo should not be null", tokenInfoResult)
        println("Jetton Balance: ${tokenInfoResult?.balance} ${tokenInfoResult?.symbol}")
    }

    @Test
    fun testGetJettonTransactions() {
        val latch = CountDownLatch(1)
        var transactionsResult: Array<TransationData>? = null

        val jettonMaster = "EQCxE6mUtQJKFnGfaROTpIseLRdsOTnBGY7o6vOWhuX9A_7f"
        val address = "0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc"

        CoinsManager.shared.getTokenTransactions(
            ACTCoin.TON,
            address,
            jettonMaster,
            object : TokenTransactionsHandle {
                override fun completionHandler(
                    transactions: Array<TransationData>?,
                    errStr: String
                ) {
                    transactionsResult = transactions
                    latch.countDown()
                }
            })

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Request timed out", completed)
        assertNotNull("Jetton transactions should not be null", transactionsResult)
        println("Jetton Transactions count: ${transactionsResult?.size}")
    }

    @Test
    fun testGetNFTs() {
        val latch = CountDownLatch(1)
        var nftsResult: Array<NFTItem>? = null

        val address = "0QDF3gcg1_fLn96gnocNhj1GT0deFHUFsJDWsmrcyaLgeJPc"

        CoinsManager.shared.getNFTs(ACTCoin.TON, address, object : NFTListHandle {
            override fun completionHandler(nfts: Array<NFTItem>?, errStr: String) {
                nftsResult = nfts
                latch.countDown()
            }
        })

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Request timed out", completed)
        assertNotNull("NFTs should not be null", nftsResult)
        println("NFT count: ${nftsResult?.size}")
    }
}
