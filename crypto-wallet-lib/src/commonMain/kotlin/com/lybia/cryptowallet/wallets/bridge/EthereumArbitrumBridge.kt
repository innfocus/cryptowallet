package com.lybia.cryptowallet.wallets.bridge

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.IBridgeManager
import com.lybia.cryptowallet.coinkits.ChainConfig
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.errors.BridgeError
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.bridge.BridgeFeeEstimate
import com.lybia.cryptowallet.models.bridge.BridgeStatus
import com.lybia.cryptowallet.services.InfuraRpcService
import com.lybia.cryptowallet.wallets.ethereum.EthTransactionSigner
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bridge implementation for Ethereum ↔ Arbitrum (ETH).
 *
 * Flow:
 * - ETH → Arbitrum: Deposit ETH to Arbitrum Delayed Inbox contract on Ethereum L1.
 *   The inbox contract auto-mints equivalent ETH on Arbitrum L2 (~10 min).
 * - Arbitrum → ETH: Withdraw via ArbSys precompile on Arbitrum L2.
 *   Withdrawal has a ~7-day challenge period before funds can be claimed on L1.
 */
class EthereumArbitrumBridge(
    private val mnemonic: String,
    private val configs: Map<NetworkName, ChainConfig> = emptyMap()
) : IBridgeManager {

    companion object {
        /** Minimum bridge amount in wei (0.001 ETH) */
        internal const val MIN_BRIDGE_AMOUNT = 1_000_000_000_000_000L

        /** Fixed bridge protocol fee rate (0.05%) */
        internal const val BRIDGE_FEE_RATE = 0.0005

        /** Fixed source chain gas fee in ETH */
        internal const val SOURCE_GAS_FEE_ETH = 0.005

        /** Fixed destination chain gas fee in ETH */
        internal const val DESTINATION_GAS_FEE_ETH = 0.001

        /**
         * Arbitrum Delayed Inbox contract on Ethereum L1.
         * depositEth() — send ETH value to this contract to bridge to Arbitrum.
         * Mainnet: 0x4Dbd4fc535Ac27206064B68FfCf827b0A60BAB3f
         * Sepolia: 0xaAe29B0366299461418F5324a79Afc425BE5ae21
         */
        internal const val INBOX_MAINNET = "0x4Dbd4fc535Ac27206064B68FfCf827b0A60BAB3f"
        internal const val INBOX_SEPOLIA = "0xaAe29B0366299461418F5324a79Afc425BE5ae21"

        /**
         * ArbSys precompile on Arbitrum L2.
         * withdrawEth(address destination) — initiate L2→L1 withdrawal.
         */
        internal const val ARBSYS_ADDRESS = "0x0000000000000000000000000000000000000064"

        private val SUPPORTED_PAIRS = setOf(
            NetworkName.ETHEREUM to NetworkName.ARBITRUM,
            NetworkName.ARBITRUM to NetworkName.ETHEREUM
        )
    }

    private val ethManager by lazy { EthereumManager(mnemonic) }

    /**
     * Bridge asset between Ethereum and Arbitrum.
     *
     * - ETHEREUM → ARBITRUM: Deposit via Arbitrum bridge contract on Ethereum
     * - ARBITRUM → ETHEREUM: Withdrawal via Arbitrum bridge contract on Arbitrum
     *
     * @param fromChain source chain (ETHEREUM or ARBITRUM)
     * @param toChain destination chain (ARBITRUM or ETHEREUM)
     * @param amount amount in wei
     * @return TransferResponseModel with bridge transaction ID
     * @throws BridgeError.UnsupportedBridgePair if chain pair is not supported
     * @throws BridgeError.InsufficientBridgeBalance if balance is insufficient
     */
    override suspend fun bridgeAsset(
        fromChain: NetworkName,
        toChain: NetworkName,
        amount: Long
    ): TransferResponseModel {
        // Validate supported pair
        if ((fromChain to toChain) !in SUPPORTED_PAIRS) {
            throw BridgeError.UnsupportedBridgePair(fromChain, toChain)
        }

        // Validate minimum amount
        if (amount < MIN_BRIDGE_AMOUNT) {
            throw BridgeError.InsufficientBridgeBalance(
                available = amount.toDouble(),
                required = MIN_BRIDGE_AMOUNT.toDouble(),
                fee = 0.0
            )
        }

        // Estimate fees for the bridge
        val feeEstimate = estimateBridgeFee(fromChain, toChain, amount)

        return when (fromChain) {
            NetworkName.ETHEREUM -> bridgeEthToArbitrum(amount, feeEstimate)
            NetworkName.ARBITRUM -> bridgeArbitrumToEth(amount, feeEstimate)
            else -> throw BridgeError.UnsupportedBridgePair(fromChain, toChain)
        }
    }

    /**
     * Query bridge transaction status.
     *
     * @param txHash the bridge transaction hash/ID
     * @return status string: "pending", "confirming", "completed", or "failed"
     */
    override suspend fun getBridgeStatus(txHash: String): String {
        if (txHash.isBlank()) {
            throw BridgeError.BridgeTransactionFailed(txHash, "Transaction hash cannot be blank")
        }

        // In production, this would query the transaction receipt via InfuraRpcService.
        // Simulated: return status based on receipt query.
        return try {
            queryTransactionReceiptStatus(txHash)
        } catch (e: Exception) {
            if (e is BridgeError) throw e
            throw BridgeError.BridgeServiceUnavailable("Arbitrum Bridge RPC")
        }
    }

    /**
     * Estimate bridge fees for a given transfer.
     *
     * @param fromChain source chain
     * @param toChain destination chain
     * @param amount amount in wei
     * @return BridgeFeeEstimate with fee breakdown in ETH
     */
    suspend fun estimateBridgeFee(
        fromChain: NetworkName,
        toChain: NetworkName,
        amount: Long
    ): BridgeFeeEstimate {
        if ((fromChain to toChain) !in SUPPORTED_PAIRS) {
            throw BridgeError.UnsupportedBridgePair(fromChain, toChain)
        }

        val amountInEth = amount.toDouble() / 1_000_000_000_000_000_000.0
        val bridgeFee = amountInEth * BRIDGE_FEE_RATE
        val sourceFee = SOURCE_GAS_FEE_ETH
        val destinationFee = DESTINATION_GAS_FEE_ETH
        val totalFee = sourceFee + destinationFee + bridgeFee

        return BridgeFeeEstimate(
            sourceFee = sourceFee,
            destinationFee = destinationFee,
            bridgeFee = bridgeFee,
            totalFee = totalFee,
            sourceUnit = "ETH",
            destinationUnit = "ETH"
        )
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Deposit ETH to Arbitrum via Delayed Inbox contract on Ethereum L1.
     * Sends ETH directly to the inbox contract with depositEth() call data.
     */
    private suspend fun bridgeEthToArbitrum(
        amount: Long,
        feeEstimate: BridgeFeeEstimate
    ): TransferResponseModel {
        return try {
            val coinNetwork = CoinNetwork(NetworkName.ETHEREUM)
            val inboxAddress = getInboxAddress()

            // depositEth() has no parameters — just send ETH to inbox with selector
            val result = ethManager.executeContract(
                contractAddress = inboxAddress,
                functionSignature = "depositEth()",
                params = emptyList(),
                valueWei = BigInteger.fromLong(amount),
                coinNetwork = coinNetwork
            )

            if (!result.success) {
                throw BridgeError.BridgeTransactionFailed(
                    result.txHash ?: "",
                    result.error ?: "Deposit transaction failed"
                )
            }

            result
        } catch (e: Exception) {
            if (e is BridgeError) throw e
            TransferResponseModel(
                success = false,
                error = e.message ?: "Bridge ETH → Arbitrum failed",
                txHash = null
            )
        }
    }

    /**
     * Withdraw ETH from Arbitrum via ArbSys precompile on Arbitrum L2.
     * Calls withdrawEth(address destination) on the ArbSys precompile.
     * Note: Withdrawal has a ~7-day challenge period before L1 claim.
     */
    private suspend fun bridgeArbitrumToEth(
        amount: Long,
        feeEstimate: BridgeFeeEstimate
    ): TransferResponseModel {
        return try {
            val coinNetwork = CoinNetwork(NetworkName.ARBITRUM)
            val destination = ethManager.getAddress()

            // withdrawEth(address destination) — send ETH value with recipient
            val result = ethManager.executeContract(
                contractAddress = ARBSYS_ADDRESS,
                functionSignature = "withdrawEth(address)",
                params = listOf(EthTransactionSigner.AbiParam.Address(destination)),
                valueWei = BigInteger.fromLong(amount),
                coinNetwork = coinNetwork
            )

            if (!result.success) {
                throw BridgeError.BridgeTransactionFailed(
                    result.txHash ?: "",
                    result.error ?: "Withdrawal transaction failed"
                )
            }

            result
        } catch (e: Exception) {
            if (e is BridgeError) throw e
            TransferResponseModel(
                success = false,
                error = e.message ?: "Bridge Arbitrum → ETH failed",
                txHash = null
            )
        }
    }

    /**
     * Query transaction receipt status from the RPC service.
     * Maps receipt status: 0x1 → COMPLETED, 0x0 → FAILED, null → PENDING.
     */
    private suspend fun queryTransactionReceiptStatus(txHash: String): String {
        // Try both L1 and L2 networks
        val networks = listOf(
            CoinNetwork(NetworkName.ETHEREUM),
            CoinNetwork(NetworkName.ARBITRUM)
        )

        for (network in networks) {
            val receiptJson = InfuraRpcService.shared.getTransactionReceipt(network, txHash)
                ?: continue

            val json = Json { ignoreUnknownKeys = true }
            val receiptObj = json.parseToJsonElement(receiptJson).jsonObject
            val status = receiptObj["status"]?.jsonPrimitive?.content

            return when (status) {
                "0x1" -> BridgeStatus.COMPLETED.value
                "0x0" -> BridgeStatus.FAILED.value
                else -> BridgeStatus.CONFIRMING.value
            }
        }

        // Receipt not found on either chain — still pending
        return BridgeStatus.PENDING.value
    }

    /**
     * Get the Inbox contract address based on current network (mainnet/testnet).
     */
    private fun getInboxAddress(): String {
        return when (com.lybia.cryptowallet.Config.shared.getNetwork()) {
            com.lybia.cryptowallet.enums.Network.MAINNET -> INBOX_MAINNET
            com.lybia.cryptowallet.enums.Network.TESTNET -> INBOX_SEPOLIA
        }
    }
}
