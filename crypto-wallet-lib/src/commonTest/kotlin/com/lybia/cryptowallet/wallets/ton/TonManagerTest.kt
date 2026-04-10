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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // ─── Live Network Tests (manual only) ─────────────────────────────────

    /**
     * Diagnostic: print W4 vs W5 addresses and check balance on each.
     * Run manually to determine which wallet version holds funds.
     *
     * Command:
     *   ./gradlew :crypto-wallet-lib:testDebugUnitTest --tests "*.TonManagerTest.testDiagnosticWalletVersion" -Pignore.skip=true
     */
    // ── Helper: retry with delay to handle Toncenter 429 rate limits ──
    private suspend fun <T> retryWithDelay(
        maxRetries: Int = 3,
        delayMs: Long = 2000,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                println("  Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    println("  Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        throw lastException!!
    }

    private fun setupMainnet() {
        Config.shared.setNetwork(Network.MAINNET)
        Config.shared.apiKeyToncenter = ""
    }

    @Test
    @Ignore // Debug: verify on-chain code hash vs our W5R1 code
    fun testDebugW5CodeHash() = runTest {
        withContext(Dispatchers.Default) {
            setupMainnet()
            val tonManager = TonManager(testMnemonic, WalletVersion.W5)
            val addr = tonManager.getAddress()
            println("W5 Address: $addr")

            // Our W5R1 code cell hash
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            val w5Code = org.ton.boc.BagOfCells(
                kotlin.io.encoding.Base64.decode(
                    "te6cckECFAEAAoEAART/APSkE/S88sgLAQIBIAINAgFIAwQC3NAg10nBIJFbj2Mg1wsfIIIQ" +
                    "ZXh0br0hghBzaW50vbCSXwPgghBleHRuuo60gCDXIQHQdNch+kAw+kT4KPpEMFi9kVvg7UTQ" +
                    "gQFB1yH0BYMH9A5voTGRMOGAQNchcH/bPOAxINdJgQKAuZEw4HDiEA8CASAFDAIBIAYJAgFu" +
                    "BwgAGa3OdqJoQCDrkOuF/8AAGa8d9qJoQBDrkOuFj8ACAUgKCwAXsyX7UTQcdch1wsfgABGy" +
                    "YvtRNDXCgCAAGb5fD2omhAgKDrkPoCwBAvIOAR4g1wsfghBzaWduuvLgin8PAeaO8O2i7fshg" +
                    "wjXIgKDCNcjIIAg1yHTH9Mf0x/tRNDSANMfINMf0//XCgAK+QFAzPkQmiiUXwrbMeHywIff" +
                    "ArNQB7Dy0IRRJbry4IVQNrry4Ib4I7vy0IgikvgA3gGkf8jKAMsfAc8Wye1UIJL4D95w2zzY" +
                    "EAP27aLt+wL0BCFukmwhjkwCIdc5MHCUIccAs44tAdcoIHYeQ2wg10nACPLgkyDXSsAC8uCT" +
                    "INcdBscSwgBSMLDy0InXTNc5MAGk6GwShAe78uCT10rAAPLgk+1V4tIAAcAAkVvg69csCBQg" +
                    "kXCWAdcsCBwS4lIQseMPINdKERITAJYB+kAB+kT4KPpEMFi68uCR7UTQgQFB1xj0BQSdf8jK" +
                    "AEAEgwf0U/Lgi44UA4MH9Fvy4Iwi1woAIW4Bs7Dy0JDiyFADzxYS9ADJ7VQAcjDXLAgkji0h" +
                    "8uCS0gDtRNDSAFETuvLQj1RQMJExnAGBAUDXIdcKAPLgjuLIygBYzxbJ7VST8sCN4gAQk1vb" +
                    "MeHXTNC01sNe"
                )
            ).first()
            val ourCodeHash = w5Code.hash().toByteArray().joinToString("") { "%02x".format(it) }
            println("Our W5R1 code hash: $ourCodeHash")

            // Query on-chain code hash via get_method
            val coinNetwork = CoinNetwork(NetworkName.TON)
            val body = com.lybia.cryptowallet.services.TonApiService.INSTANCE.runGetMethod(
                coinNetwork, addr, "get_subwallet_id"
            )
            println("get_subwallet_id result: $body")

            kotlinx.coroutines.delay(1500)

            // Also check if it's a wallet by querying is_plugin_installed or seqno
            val seqnoBody = com.lybia.cryptowallet.services.TonApiService.INSTANCE.runGetMethod(
                coinNetwork, addr, "seqno"
            )
            println("seqno result: $seqnoBody")

            kotlinx.coroutines.delay(1500)

            // Check public key stored on-chain
            val pkBody = com.lybia.cryptowallet.services.TonApiService.INSTANCE.runGetMethod(
                coinNetwork, addr, "get_public_key"
            )
            println("get_public_key result: $pkBody")
            println("Our public key: ${tonManager.publicKey.key.toByteArray().joinToString("") { "%02x".format(it) }}")
        }
    }

    @Test
    @Ignore // Live network — run manually
    fun testDiagnosticWalletVersion() = runTest {
        // withContext(Dispatchers.Default) to use real time instead of virtual test time
        withContext(Dispatchers.Default) {
            setupMainnet()
            val coinNetwork = CoinNetwork(NetworkName.TON)

            val w4 = TonManager(testMnemonic, WalletVersion.W4)
            val w5 = TonManager(testMnemonic, WalletVersion.W5)

            val w4Addr = w4.getAddress()
            val w5Addr = w5.getAddress()
            println("W4 Address: $w4Addr")
            println("W5 Address: $w5Addr")

            val w4Balance = w4.getBalance(w4Addr, coinNetwork)
            kotlinx.coroutines.delay(1500)
            val w5Balance = w5.getBalance(w5Addr, coinNetwork)
            println("W4 Balance: $w4Balance TON")
            println("W5 Balance: $w5Balance TON")

            kotlinx.coroutines.delay(1500)
            val w4Seqno = try { w4.getSeqno(coinNetwork) } catch (e: Exception) { -1 }
            kotlinx.coroutines.delay(1500)
            val w5Seqno = try { w5.getSeqno(coinNetwork) } catch (e: Exception) { -1 }
            println("W4 Seqno: $w4Seqno (deployed: ${w4Seqno >= 0})")
            println("W5 Seqno: $w5Seqno (deployed: ${w5Seqno >= 0})")

            assertTrue(w4Balance > 0 || w5Balance > 0, "At least one wallet version should have funds")
        }
    }

    /**
     * Send 1 TON on mainnet. Detects W4/W5, sends from whichever has funds.
     *
     * ⚠️ REAL TRANSACTION — costs actual TON. Only run when intentionally testing.
     *
     * Command:
     *   ./gradlew :crypto-wallet-lib:jvmTest --tests "*.TonManagerTest.testSendTonMainnet"
     */
    @Test
    @Ignore // Live network — REAL TRANSACTION, run manually only
    fun testSendTonMainnet() = runTest {
        withContext(Dispatchers.Default) {
            setupMainnet()
            val coinNetwork = CoinNetwork(NetworkName.TON)
            val toAddress = "UQDYtgK7Yiqn14Ii0TjVykIZdeA8MUGZ8ueUEfi-KG0UOh-o"
            val amountNano = 1_000_000_000L // 1 TON

            // ── Step 0: detect which wallet version has funds ──
            val w4 = TonManager(testMnemonic, WalletVersion.W4)
            val w5 = TonManager(testMnemonic, WalletVersion.W5)

            val w4Balance = w4.getBalance(w4.getAddress(), coinNetwork)
            kotlinx.coroutines.delay(1500)
            val w5Balance = w5.getBalance(w5.getAddress(), coinNetwork)
            println("W4 [${w4.getAddress()}] balance: $w4Balance TON")
            println("W5 [${w5.getAddress()}] balance: $w5Balance TON")

            val tonManager = when {
                w5Balance >= 1.1 -> w5.also { println("Using W5") }
                w4Balance >= 1.1 -> w4.also { println("Using W4 (W5 has insufficient funds)") }
                else -> throw IllegalStateException(
                    "Neither W4 nor W5 has enough balance (need ≥1.1 TON). W4=$w4Balance, W5=$w5Balance"
                )
            }

            // ── Step 1: get seqno (with retry for rate limits) ──
            kotlinx.coroutines.delay(1500)
            val seqno = retryWithDelay { tonManager.getSeqno(coinNetwork) }
            println("Seqno: $seqno")

            // ── Step 2: sign transaction ──
            val bocBase64 = tonManager.signTransaction(toAddress, amountNano, seqno)
            println("Signed BOC length: ${bocBase64.length}")
            assertTrue(bocBase64.isNotEmpty(), "BOC should not be empty")

            // ── Step 3: broadcast ──
            kotlinx.coroutines.delay(1500)
            val result = retryWithDelay { tonManager.transfer(bocBase64, coinNetwork) }
            println("Transfer result: success=${result.success}, txHash=${result.txHash}, error=${result.error}")
            assertTrue(result.success, "Transfer should succeed, error: ${result.error}")
        }
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

    /**
     * Locked test vector for a 12-word Japanese BIP-39 mnemonic.
     *
     * Expected address was computed against the BIP-39 reference pipeline
     * (NFKD → PBKDF2-HMAC-SHA512 → SLIP-0010 Ed25519 m/44'/607'/0' → W5R1)
     * and cross-verified with iOS ton-swift. This test guards against the
     * bitcoin-kmp JVM `Pbkdf2.withHmacSha512` regression where non-ASCII
     * passwords were mangled by a CharArray round-trip — see
     * `utils/Pbkdf2HmacSha512.kt`. Before the fix this test produced
     * `UQAE7M55WHB0f-Bqo_ImrWQ60HAkTdA0MYDyf5ev-LeBakZE`.
     */
    @Test
    fun testGetAddressJapaneseBip39() {
        Config.shared.setNetwork(Network.MAINNET)
        val jpMnemonic =
            "ちいき\u3000とくてん\u3000せけん\u3000はにかむ\u3000うなずく\u3000ほたて\u3000" +
            "いみん\u3000きぞん\u3000ききて\u3000むのう\u3000そがい\u3000へいせつ"

        val address = TonManager(jpMnemonic, WalletVersion.W5).getAddress()
        assertEquals(
            "UQDDn3oV7vjoyP_vIGj70-ppxYYK-0QifddQtuJS_RiXA4Hx",
            address,
            "Japanese BIP-39 mnemonic must derive the BIP-39-spec W5R1 address"
        )
    }
}
