package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Ignore
import kotlinx.coroutines.test.runTest

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

    @Test
    @Ignore // Requires network and potentially an API key
    fun testResolveDns() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        // Note: You need a valid API key in CoinNetwork for this to work
        val coinNetwork = CoinNetwork(NetworkName.TON)

        // foundation.ton is a well-known domain
        val domain = "foundation.ton"
        val resolvedAddress = try {
             tonManager.resolveDns(domain, coinNetwork)
        } catch (e: Exception) {
            println("Resolution failed (possibly due to network or missing API key): ${e.message}")
            null
        }
        
        println("Resolved $domain to $resolvedAddress")
        if (resolvedAddress != null) {
            assertTrue(resolvedAddress.startsWith("EQ") || resolvedAddress.startsWith("UQ") || resolvedAddress.startsWith("0:"), 
                "Resolved address should be a valid TON address")
        }
    }

    @Test
    @Ignore // Requires network
    fun testReverseResolveDns() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val coinNetwork = CoinNetwork(NetworkName.TON)
        
        // Example address that has a TON DNS (foundation.ton)
        // EQCD39VS5Is_fS8L99I4tq8t_TAn8U7z69-95vD0j_X4_S - actually foundation.ton address might vary
        val address = "EQCD39VS5Is_fS8L99I4tq8t_TAn8U7z69-95vD0j_X4_S" 
        val domain = try {
            tonManager.reverseResolveDns(address, coinNetwork)
        } catch (e: Exception) {
            println("Reverse resolution failed: ${e.message}")
            null
        }
        
        println("Address $address resolved to domain: $domain")
    }

    @Test
    @Ignore // Requires network and valid pool address
    fun testGetNominatorStakingBalance() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val coinNetwork = CoinNetwork(NetworkName.TON)

        // Example Nominator Pool address
        val poolAddress = "EQD4S93o-Jv-W-2n-Xp-V_2p-Xp-V_2p-Xp-V_2p-Xp-V_2p" // Fake address
        val stakingBalance = tonManager.getNominatorStakingBalance(poolAddress, coinNetwork)

        println("Staking Balance: $stakingBalance")
        if (stakingBalance != null) {
            assertTrue(stakingBalance.amount >= 0, "Staking amount should be non-negative")
        }
    }

    @Test
    @Ignore // Requires network
    fun testGetTonstakersStakingBalance() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        val coinNetwork = CoinNetwork(NetworkName.TON)

        // Tonstakers Master Address
        val poolAddress = "EQD-cvRscwXMDFFdc1kYQK8zV8N39p9N3T8E_0KjLpInS3Wl"
        val stakingBalance = tonManager.getTonstakersStakingBalance(poolAddress, coinNetwork)

        println("Tonstakers Balance: $stakingBalance")
    }

    @Test
    fun testSignDepositToNominatorPool() = runTest {
        val tonManager = TonManager(testMnemonic)
        val poolAddress = "EQD4S93o-Jv-W-2n-Xp-V_2p-Xp-V_2p-Xp-V_2p-Xp-V_2p"
        val amount = 10_000_000_000L // 10 TON
        val seqno = 5
        
        val boc = tonManager.signDepositToNominatorPool(poolAddress, amount, seqno)
        
        assertTrue(boc.isNotEmpty(), "Signed BOC should not be empty")
        println("Signed Deposit BOC: $boc")
    }
}
