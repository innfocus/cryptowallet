package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.coinkits.ChainConfig
import com.lybia.cryptowallet.coinkits.CommonCoinsManager
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.wallets.cardano.CardanoManager
import kotlinx.coroutines.test.runTest
import kotlin.math.pow
import kotlin.test.*

/**
 * Manual mainnet test for Cardano native token transfer (NIGHT).
 *
 * ⚠️ REAL MAINNET TRANSACTION — run manually only.
 * Requires: funded wallet with NIGHT tokens + ADA for fees.
 *
 * Run: ./gradlew :crypto-wallet-lib:jvmTest --tests "com.lybia.cryptowallet.cardano.CardanoMainnetManualTest"
 */
class CardanoMainnetManualTest {

    private val mnemonic = "left arena awkward spin damp pipe liar ribbon few husband execute whisper\n"

    private val toAddress = "addr1q99fuv64hlehyxp6ame5mnwksxvnp0adgfu0mzlcflctaxc2zmjfqyrvt35ts4p3tjxqrqhls7ltar34acnzufw59glsxhn2uv"

    // NIGHT token
    private val nightPolicyId = "0691b2fecca1ac4f53cb6dfb00b7013e561d1f34403b957cbb5af1fa"
    private val nightAssetName = "4e49474854" // hex of "NIGHT"
    private val nightScale = 6
    private val blockfrostApiKey = "mainnetlxxx"

    private fun createManager(): CardanoManager {
        val coinNetwork = CoinNetwork(NetworkName.CARDANO)
        val apiService = CardanoApiService(
            coinNetwork.getBlockfrostUrl(),
            blockfrostApiKey,
        )
        return CardanoManager(mnemonic, apiService)
    }

    private fun createCommonCoinsManager(): CommonCoinsManager {
        val coinNetwork = CoinNetwork(NetworkName.CARDANO)
        return CommonCoinsManager(
            mnemonic = mnemonic,
            configs = mapOf(
                NetworkName.CARDANO to ChainConfig(
                    apiBaseUrl = coinNetwork.getBlockfrostUrl(),
                    apiKey = blockfrostApiKey,
                    fallbackApiBaseUrl = coinNetwork.getKoiosUrl()
                )
            )
        )
    }

    @BeforeTest
    fun setup() {
        Config.shared.setNetwork(Network.MAINNET)
    }

    @Test
    @Ignore
    fun testGetAddress() {
        val manager = createManager()
        val address = manager.getAddress()
        println("=== Cardano Get Address ===")
        println("Mnemonic: $mnemonic")
        println("Network: MAINNET")
        println("Shelley address: $address")
        println("===========================")
        assertTrue(address.startsWith("addr1"), "Address should start with addr1, got: $address")
    }

    @Test
    @Ignore
    fun testGetBalance() = runTest {
        val manager = createManager()
        val address = manager.getAddress()
        println("=== Cardano Get Balance ===")
        println("Address: $address")
        val balance = manager.getBalance(address)
        println("ADA balance: $balance ADA")
        println("ADA balance (lovelace): ${(balance * 1_000_000).toLong()} lovelace")
        println("==========================")
        assertTrue(balance >= 0.0, "Balance should be >= 0")
    }

    @Test
    @Ignore
    fun testGetNightTokenBalance() = runTest {
        val manager = createManager()
        val address = manager.getAddress()
        println("=== Cardano NIGHT Token Balance ===")
        println("Address: $address")
        println("Policy ID: $nightPolicyId")
        println("Asset Name (hex): $nightAssetName")
        println("Scale: $nightScale")
        val balance = manager.getTokenBalance(address, nightPolicyId, nightAssetName)
        val humanBalance = balance.toDouble() / (10.0.pow(nightScale))
        println("NIGHT balance (raw): $balance")
        println("NIGHT balance: $humanBalance NIGHT")
        println("===================================")
        assertTrue(balance >= 0L, "Token balance should be >= 0")
    }

    @Test
    @Ignore
    fun testSendNightToken() = runTest {
        val manager = createManager()
        val address = manager.getAddress()
        println("=== Cardano Send NIGHT Token ===")
        println("From: $address")
        println("To: $toAddress")

        // Check balances before send
        val adaBalance = manager.getBalance(address)
        val nightBalance = manager.getTokenBalance(address, nightPolicyId, nightAssetName)
        val humanNight = nightBalance.toDouble() / (10.0.pow(nightScale))
        println("ADA balance: $adaBalance ADA")
        println("NIGHT balance: $humanNight NIGHT (raw: $nightBalance)")

        // Send 1 NIGHT (= 1_000_000 smallest unit with scale=6)
        val sendAmount = 1_000_000L  // 1 NIGHT
        val fee = 200_000L           // 0.2 ADA fee
        println("Send amount: 1 NIGHT (raw: $sendAmount)")
        println("Fee: ${fee.toDouble() / 1_000_000} ADA (raw: $fee lovelace)")

        assertTrue(adaBalance >= 2.0, "Need at least 2 ADA for min UTXO + fee")
        assertTrue(nightBalance >= sendAmount, "Need at least 1 NIGHT token")

        val txHash = manager.sendToken(
            toAddress = toAddress,
            policyId = nightPolicyId,
            assetName = nightAssetName,
            amount = sendAmount,
            fee = fee
        )
        println("TX submitted: $txHash")
        println("================================")
        assertTrue(txHash.isNotEmpty(), "Transaction hash should not be empty")
    }

    @Test
    @Ignore
    fun testSendNightTokenViaCommonCoinsManager() = runTest {
        val coinsManager = createCommonCoinsManager()

        // Get address
        val address = coinsManager.getAddress(NetworkName.CARDANO)
        println("=== CommonCoinsManager Send NIGHT ===")
        println("From: $address")
        println("To: $toAddress")

        // Check ADA balance
        val balanceResult = coinsManager.getBalance(NetworkName.CARDANO)
        println("ADA balance: ${balanceResult.balance} ADA (success: ${balanceResult.success})")

        // Check token balance
        val tokenResult = coinsManager.getTokenBalance(address, nightPolicyId, nightAssetName)
        val humanNight = tokenResult.balance.toDouble() / (10.0.pow(nightScale))
        println("NIGHT balance: $humanNight NIGHT (raw: ${tokenResult.balance}, success: ${tokenResult.success})")
        assertTrue(tokenResult.success, "Token balance query should succeed")

        // Send 1 NIGHT
        val sendAmount = 1_000_000L  // 1 NIGHT
        val fee = 200_000L           // 0.2 ADA
        println("Send amount: 1 NIGHT (raw: $sendAmount)")
        println("Fee: ${fee.toDouble() / 1_000_000} ADA")

        val result = coinsManager.sendToken(
            toAddress = toAddress,
            policyId = nightPolicyId,
            assetName = nightAssetName,
            amount = sendAmount,
            fee = fee
        )
        println("TX hash: ${result.txHash}")
        println("Success: ${result.success}")
        println("Error: ${result.error ?: "none"}")
        println("=====================================")
        assertTrue(result.success, "Send token should succeed: ${result.error}")
        assertTrue(result.txHash.isNotEmpty())
    }
}
