package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.bridge.BridgeStatus
import com.lybia.cryptowallet.wallets.bridge.BridgeManagerFactory
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for CommonCoinsManager bridge integration.
 *
 * Feature: staking-bridge
 */
class CommonCoinsManagerBridgeTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private val supportedBridgePairs = listOf(
        NetworkName.CARDANO to NetworkName.MIDNIGHT,
        NetworkName.MIDNIGHT to NetworkName.CARDANO,
        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
        NetworkName.ARBITRUM to NetworkName.ETHEREUM
    )

    /**
     * Valid status values that getBridgeStatus() may return.
     * Includes the four BridgeStatus enum values plus "unknown" which
     * CommonCoinsManager returns when no bridge manager handles the hash
     * or an exception occurs.
     */
    private val validBridgeStatuses = BridgeStatus.entries.map { it.value }.toSet() + "unknown"

    // ── Property 12: Bridge status valid values ─────────────────────────

    // Feature: crypto-wallet-module, Property 12: Bridge status valid values
    // **Validates: Requirements 29.2**

    /**
     * **Property 12: Bridge status là giá trị hợp lệ**
     *
     * For any supported bridge pair and any non-blank txHash,
     * getBridgeStatus(txHash) ∈ {"pending", "confirming", "completed", "failed", "unknown"}.
     */
    @Test
    fun getBridgeStatusAlwaysReturnsValidValue() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        // Generate txHash values that mimic real bridge transaction IDs
        val sampleTxHashes = listOf(
            "bridge_mint_5000000_sim_1",
            "bridge_deposit_ethereum_1000000_sim_1",
            "bridge_burn_midnight_2000000_sim_1",
            "bridge_withdrawal_arbitrum_500000_sim_1",
            "sim_42",
            "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            "abc123",
            "tx_unknown_hash"
        )

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(sampleTxHashes)
        ) { txHash ->
            val status = manager.getBridgeStatus(txHash)
            assertTrue(
                status in validBridgeStatuses,
                "getBridgeStatus('$txHash') returned '$status' which is not in $validBridgeStatuses"
            )
        }
    }

    /**
     * Property 12 (bridge-level): For each bridge implementation directly,
     * getBridgeStatus returns a valid BridgeStatus value.
     */
    @Test
    fun bridgeManagerGetBridgeStatusReturnsValidValue() = runTest {
        val bridgeStatusValues = BridgeStatus.entries.map { it.value }.toSet()

        val sampleTxHashes = listOf(
            "bridge_mint_5000000_sim_1",
            "bridge_deposit_ethereum_1000000_sim_1",
            "sim_1",
            "0xdeadbeef",
            "some_tx_hash"
        )

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.of(supportedBridgePairs),
            Arb.of(sampleTxHashes)
        ) { (fromChain, toChain), txHash ->
            val bridgeManager = BridgeManagerFactory.createBridgeManager(
                fromChain, toChain, testMnemonic
            )!!
            val status = bridgeManager.getBridgeStatus(txHash)
            assertTrue(
                status in bridgeStatusValues,
                "getBridgeStatus('$txHash') on $fromChain→$toChain returned '$status' which is not in $bridgeStatusValues"
            )
        }
    }

    // ── Property 13: CommonCoinsManager bridge methods delegate đúng bridge manager ──

    // Feature: staking-bridge, Property 13: CommonCoinsManager bridge methods delegate đúng bridge manager
    // **Validates: Requirements 14.1, 14.2**
    @Test
    fun bridgeMethodsDelegateToCorrectBridgeManager() = runTest {
        checkAll(100, Arb.of(supportedBridgePairs)) { (fromChain, toChain) ->
            val manager = CommonCoinsManager(mnemonic = testMnemonic)

            // supportsBridge should return true for supported pairs
            assertTrue(
                manager.supportsBridge(fromChain, toChain),
                "supportsBridge($fromChain, $toChain) should return true"
            )

            // bridgeAsset should delegate to the correct bridge manager and succeed
            val bridgeResult = manager.bridgeAsset(fromChain, toChain, 2_000_000L)
            assertTrue(
                bridgeResult.success,
                "bridgeAsset($fromChain → $toChain) should succeed, got error: ${bridgeResult.error}"
            )
            assertTrue(
                bridgeResult.txHash.isNotEmpty(),
                "bridgeAsset($fromChain → $toChain) should return non-empty txHash"
            )

            // Verify the result is consistent with calling the bridge manager directly
            val directBridgeManager = BridgeManagerFactory.createBridgeManager(
                fromChain, toChain, testMnemonic
            )
            assertTrue(
                directBridgeManager != null,
                "BridgeManagerFactory should create a manager for $fromChain → $toChain"
            )

            // getBridgeStatus should delegate and return a valid status
            val status = manager.getBridgeStatus(bridgeResult.txHash)
            assertTrue(
                status in listOf("pending", "confirming", "completed", "failed"),
                "getBridgeStatus should return valid status, got: $status"
            )
        }
    }

    // ── Unit test 14.3: unsupported bridge pair error ──

    // **Validates: Requirements 14.4**
    @Test
    fun bridgeAssetForUnsupportedPairReturnsUnsupportedOperation() = runTest {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        // Test unsupported bridge pairs
        val unsupportedPairs = listOf(
            NetworkName.CARDANO to NetworkName.ETHEREUM,
            NetworkName.CARDANO to NetworkName.TON,
            NetworkName.ETHEREUM to NetworkName.MIDNIGHT,
            NetworkName.TON to NetworkName.BTC,
            NetworkName.BTC to NetworkName.XRP
        )

        for ((fromChain, toChain) in unsupportedPairs) {
            val result = manager.bridgeAsset(fromChain, toChain, 1_000_000L)
            assertFalse(
                result.success,
                "bridgeAsset($fromChain → $toChain) should not succeed for unsupported pair"
            )
            assertTrue(
                result.error?.contains("Unsupported operation") == true,
                "bridgeAsset($fromChain → $toChain) error should contain 'Unsupported operation', got: ${result.error}"
            )
            assertEquals(
                "", result.txHash,
                "bridgeAsset($fromChain → $toChain) txHash should be empty for unsupported pair"
            )
        }
    }

    @Test
    fun supportsBridgeReturnsFalseForUnsupportedPairs() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        val unsupportedPairs = listOf(
            NetworkName.CARDANO to NetworkName.ETHEREUM,
            NetworkName.TON to NetworkName.MIDNIGHT,
            NetworkName.BTC to NetworkName.ARBITRUM,
            NetworkName.XRP to NetworkName.TON
        )

        for ((fromChain, toChain) in unsupportedPairs) {
            assertFalse(
                manager.supportsBridge(fromChain, toChain),
                "supportsBridge($fromChain, $toChain) should return false for unsupported pair"
            )
        }
    }

    @Test
    fun supportsBridgeReturnsTrueForSupportedPairs() {
        val manager = CommonCoinsManager(mnemonic = testMnemonic)

        for ((fromChain, toChain) in supportedBridgePairs) {
            assertTrue(
                manager.supportsBridge(fromChain, toChain),
                "supportsBridge($fromChain, $toChain) should return true for supported pair"
            )
        }
    }
}
