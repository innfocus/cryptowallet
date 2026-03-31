package com.lybia.cryptowallet.wallets.centrality

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.CentralityApiService
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.wallets.centrality.model.*

/**
 * Centrality (CennzNet) wallet manager.
 * Implements IWalletManager, replaces CentralityNetwork singleton.
 */
class CentralityManager(
    private val mnemonic: String,
    private val apiService: CentralityApiService = CentralityApiService(),
    private val assetId: Int = 1
) : IWalletManager {

    private val logger = Logger.withTag("CentralityManager")
    private var cachedAddress: CentralityAddress? = null

    companion object {
        const val BASE_UNIT = 10000
        const val CAL_PERIOD = 128
    }

    // ─── IWalletManager Implementation ──────────────────────────────

    override fun getAddress(): String {
        return cachedAddress?.address ?: ""
    }

    suspend fun getAddressAsync(): String {
        if (cachedAddress == null) {
            val seedHex = getSeedHex()
            cachedAddress = apiService.getPublicAddress(seedHex)
        }
        return cachedAddress?.address ?: ""
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        val addr = address ?: getAddressAsync()
        val account = apiService.scanAccount(addr)
        val asset = account.balances.find { it.assetId == assetId }
        return (asset?.free?.toDouble() ?: 0.0) / BASE_UNIT
    }

    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        val addr = address ?: getAddressAsync()
        val result = apiService.scanTransfers(addr)
        return result.transfers.filter { it.assetId == assetId && it.success }
    }

    override suspend fun transfer(dataSigned: String, coinNetwork: CoinNetwork): TransferResponseModel {
        return try {
            val txHash = apiService.submitExtrinsic(dataSigned)
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return apiService.chainGetBlockHash()
    }

    // ─── Centrality-specific: full sendCoin flow ────────────────────

    suspend fun sendCoin(
        fromAddress: String,
        toAddress: String,
        amount: Double,
        assetId: Int = 1
    ): TransferResponseModel {
        return try {
            // 1. Get chain state
            val (specVersion, transactionVersion) = apiService.getRuntimeVersion()
            val genesisHash = apiService.chainGetBlockHash()
            val blockHash = apiService.chainGetFinalizedHead()
            val currentBlockNumber = apiService.chainGetHeader(blockHash)
            val nonce = apiService.systemAccountNextIndex(fromAddress)

            // 2. Build extrinsic
            val era = makeEraOption(currentBlockNumber)
            val extrinsic = ExtrinsicBuilder()
                .paramsMethod(toAddress, amount.toLong(), assetId)
                .paramsSignature(fromAddress, nonce)
                .signOptions(specVersion, transactionVersion, genesisHash, blockHash, era)

            // 3. Sign
            val seedHex = getSeedHex()
            val payloadHex = "0x" + extrinsic.createPayload().toHexString()
            val signature = apiService.signMessage(seedHex, payloadHex)
            extrinsic.sign(signature)

            // 4. Submit
            val txHash = apiService.submitExtrinsic(extrinsic.toHex())
            TransferResponseModel(success = true, error = null, txHash = txHash)
        } catch (e: Exception) {
            logger.e(e) { "sendCoin failed" }
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /**
     * Calculate era option from current block number.
     * calPeriod = 128, quantizedPhase = current % calPeriod
     * encoded = 6 + (quantizedPhase << 4)
     * result = [encoded & 0xff, encoded >> 8]
     */
    fun makeEraOption(currentBlockNumber: Long): ByteArray {
        val result = ByteArray(2)
        val quantizedPhase = currentBlockNumber % CAL_PERIOD
        val encoded = 6 + (quantizedPhase shl 4)
        result[0] = (encoded and 0xff).toByte()
        result[1] = (encoded shr 8).toByte()
        return result
    }

    /**
     * Convert hex block number to Long.
     * "0x6211cb" → 6427083
     */
    fun convertHexToBlockNumber(hex: String): Long {
        return hex.removePrefix("0x").toLong(16)
    }

    private fun getSeedHex(): String {
        return mnemonic
    }
}
