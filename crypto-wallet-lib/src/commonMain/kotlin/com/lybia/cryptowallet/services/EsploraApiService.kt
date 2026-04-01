package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.bitcoin.FeeRateRecommendation
import com.lybia.cryptowallet.models.bitcoin.UtxoInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Blockstream Esplora REST API client for Bitcoin.
 *
 * Provides UTXO fetching, fee estimation, and raw tx broadcast
 * without requiring an API key.
 *
 * Mainnet: https://blockstream.info/api
 * Testnet: https://blockstream.info/testnet/api
 * Mempool.space (fee rates): https://mempool.space/api
 */
class EsploraApiService {

    companion object {
        val INSTANCE: EsploraApiService = EsploraApiService()
    }

    private fun baseUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://blockstream.info/api"
            else -> "https://blockstream.info/testnet/api"
        }
    }

    private fun mempoolBaseUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://mempool.space/api"
            else -> "https://mempool.space/testnet/api"
        }
    }

    /**
     * Fetch UTXOs for a given Bitcoin address.
     * Esplora endpoint: GET /address/{address}/utxo
     */
    suspend fun getUtxos(address: String): List<UtxoInfo>? {
        return try {
            val response = HttpClientService.INSTANCE.client.get(
                "${baseUrl()}/address/$address/utxo"
            )
            if (response.status.value in 200..299) {
                response.body<List<UtxoInfo>>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get the raw transaction hex for a given txid.
     * Esplora endpoint: GET /tx/{txid}/hex
     * Needed to get the full previous tx for legacy signing.
     */
    suspend fun getRawTransactionHex(txid: String): String? {
        return try {
            val response = HttpClientService.INSTANCE.client.get(
                "${baseUrl()}/tx/$txid/hex"
            )
            if (response.status.value in 200..299) {
                response.body<String>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Broadcast a signed raw transaction.
     * Esplora endpoint: POST /tx (body = raw hex string)
     *
     * @return txid on success, null on failure
     */
    suspend fun broadcastTransaction(rawTxHex: String): String? {
        return try {
            val response = HttpClientService.INSTANCE.client.post(
                "${baseUrl()}/tx"
            ) {
                contentType(ContentType.Text.Plain)
                setBody(rawTxHex)
            }
            if (response.status.value in 200..299) {
                response.body<String>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get recommended fee rates in sat/vB from Mempool.space.
     */
    suspend fun getRecommendedFeeRates(): FeeRateRecommendation? {
        return try {
            val response = HttpClientService.INSTANCE.client.get(
                "${mempoolBaseUrl()}/v1/fees/recommended"
            )
            if (response.status.value in 200..299) {
                response.body<FeeRateRecommendation>()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
