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
        Config.shared.setNetwork(Network.MAINNET)
        val tonManager = TonManager(testMnemonic)
        // Use a valid TON address in raw format (workchain:hex) for the pool
        val poolAddress = "0:d8b602bb622aa7d78222d138d5ca421975e03c30419f2e794111f8be286d143a"
        val amount = 10_000_000_000L // 10 TON
        val seqno = 5

        val boc = tonManager.signDepositToNominatorPool(poolAddress, amount, seqno)

        assertTrue(boc.isNotEmpty(), "Signed BOC should not be empty")
        println("Signed Deposit BOC: $boc")
    }

    // ─── W5 Tests ────────────────────────────────────────────────────────────

    @Test
    fun testW5AddressMainnet() {
        Config.shared.setNetwork(Network.MAINNET)
        val w5Manager = TonManager(testMnemonic, WalletVersion.W5)
        val w4Manager = TonManager(testMnemonic, WalletVersion.W4)

        val w5Address = w5Manager.getAddress()
        val w4Address = w4Manager.getAddress()

        assertEquals(48, w5Address.length, "W5 address should be 48 chars")
        assertEquals(48, w4Address.length, "W4 address should be 48 chars")
        assertTrue(w5Address != w4Address, "W5 and W4 addresses must differ")
        println("W5 Mainnet Address: $w5Address")
        println("W4 Mainnet Address: $w4Address")
    }

    @Test
    fun testW5DefaultVersion() {
        Config.shared.setNetwork(Network.MAINNET)
        val defaultManager = TonManager(testMnemonic)
        assertEquals(WalletVersion.W5, defaultManager.walletVersion, "Default should be W5")
    }

    @Test
    fun testW5AndW4AddressesDifferOnTestnet() {
        Config.shared.setNetwork(Network.TESTNET)
        val w5Manager = TonManager(testMnemonic, WalletVersion.W5)
        val w4Manager = TonManager(testMnemonic, WalletVersion.W4)

        val w5Address = w5Manager.getAddress()
        val w4Address = w4Manager.getAddress()

        assertTrue(w5Address != w4Address, "W5 and W4 must differ on testnet too")
        println("W5 Testnet: $w5Address")
        println("W4 Testnet: $w4Address")
    }

    @Test
    fun testW5MainnetAndTestnetAddressesDiffer() {
        // Create both managers (they precompute addresses for both networks at construction)
        val manager = TonManager(testMnemonic, WalletVersion.W5)

        // W5 encodes networkGlobalId in wallet_id → different addresses per network.
        // The `address` property reads Config.shared at call time, so we must switch
        // the global network before each access.
        Config.shared.setNetwork(Network.MAINNET)
        val mainnetAddr = manager.address.toString(userFriendly = false)

        Config.shared.setNetwork(Network.TESTNET)
        val testnetAddr = manager.address.toString(userFriendly = false)

        assertTrue(mainnetAddr != testnetAddr, "W5 mainnet/testnet raw addresses must differ")
        println("W5 mainnet raw: $mainnetAddr")
        println("W5 testnet raw: $testnetAddr")
    }

    @Test
    fun testW5SignTransaction() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = TonManager(testMnemonic, WalletVersion.W5)
        // Use a valid TON address (non-bounceable, mainnet) as destination
        val toAddress = "UQDYtgK7Yiqn14Ii0TjVykIZdeA8MUGZ8ueUEfi-KG0UOh-o"
        val boc = manager.signTransaction(toAddress, 1_000_000_000L, seqno = 5)
        assertTrue(boc.isNotEmpty(), "W5 signed BOC should not be empty")
        println("W5 signTransaction BOC (seqno=5): $boc")
    }

    @Test
    fun testW5SignTransactionSeqnoZero() = runTest {
        Config.shared.setNetwork(Network.MAINNET)
        val manager = TonManager(testMnemonic, WalletVersion.W5)
        // Use a valid TON address (non-bounceable, mainnet) as destination
        val toAddress = "UQDYtgK7Yiqn14Ii0TjVykIZdeA8MUGZ8ueUEfi-KG0UOh-o"
        val boc = manager.signTransaction(toAddress, 1_000_000_000L, seqno = 0)
        assertTrue(boc.isNotEmpty(), "W5 signed BOC (seqno=0, includes stateInit) should not be empty")
        println("W5 signTransaction BOC (seqno=0): $boc")
    }
}
