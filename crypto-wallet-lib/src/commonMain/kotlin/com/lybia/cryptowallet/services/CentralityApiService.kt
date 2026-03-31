package com.lybia.cryptowallet.services

import co.touchlab.kermit.Logger
import com.lybia.cryptowallet.errors.WalletError
import com.lybia.cryptowallet.wallets.centrality.CentralityError
import com.lybia.cryptowallet.wallets.centrality.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Ktor-based API service for CennzNet.
 * Replaces CentralityNetwork + Retrofit/OkHttp from androidMain.
 *
 * Two types of API:
 * 1. JSON-RPC: Substrate node RPC (rpcBaseUrl)
 * 2. REST API: CennzNet explorer + local signing service (explorerBaseUrl, localApiBaseUrl)
 */
class CentralityApiService(
    private val rpcBaseUrl: String = CENNZ_ENDPOINTS.RPC_SERVER,
    private val explorerBaseUrl: String = CENNZ_ENDPOINTS.EXPLORER_SERVER,
    private val localApiBaseUrl: String = CENNZ_ENDPOINTS.LOCAL_API_SERVER,
    private val client: HttpClient = HttpClientService.INSTANCE.client
) {
    private val logger = Logger.withTag("CentralityApiService")
    private val json = Json { ignoreUnknownKeys = true }

    // ─── JSON-RPC generic call ──────────────────────────────────────

    /**
     * Call a JSON-RPC method on the Substrate node.
     * Request format: {"id":1, "jsonrpc":"2.0", "method":"...", "params":[...]}
     * Parses "result" field; throws if "error" field is present.
     */
    private suspend fun rpcCall(method: String, params: JsonArray = JsonArray(emptyList())): JsonElement {
        val payload = buildJsonObject {
            put("id", 1)
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        val response: HttpResponse = try {
            client.post("$rpcBaseUrl/public") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            throw WalletError.ConnectionError(rpcBaseUrl, e)
        }
        if (response.status.value !in 200..299) {
            throw WalletError.ConnectionError(rpcBaseUrl, Exception("HTTP ${response.status}"))
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        body["error"]?.let { errorObj ->
            val errorObject = errorObj.jsonObject
            val message = errorObject["message"]?.jsonPrimitive?.content ?: "Unknown error"
            val code = errorObject["code"]?.jsonPrimitive?.int ?: -1
            throw CentralityError.RpcError(method, code, message)
        }
        return body["result"] ?: throw CentralityError.RpcError(method, -1, "No result in response")
    }

    // ─── JSON-RPC methods ───────────────────────────────────────────

    /** state_getRuntimeVersion → (specVersion, transactionVersion) */
    suspend fun getRuntimeVersion(): Pair<Int, Int> {
        val result = rpcCall("state_getRuntimeVersion").jsonObject
        return Pair(
            result["specVersion"]!!.jsonPrimitive.int,
            result["transactionVersion"]!!.jsonPrimitive.int
        )
    }

    /** chain_getBlockHash(0) → genesis hash string */
    suspend fun chainGetBlockHash(): String {
        val params = buildJsonArray { add(0) }
        return rpcCall("chain_getBlockHash", params).jsonPrimitive.content
    }

    /** chain_getFinalizedHead → finalized block hash string */
    suspend fun chainGetFinalizedHead(): String {
        return rpcCall("chain_getFinalizedHead").jsonPrimitive.content
    }

    /** chain_getHeader(blockHash) → block number (Long) */
    suspend fun chainGetHeader(blockHash: String): Long {
        val params = buildJsonArray { add(blockHash) }
        val result = rpcCall("chain_getHeader", params).jsonObject
        val numberHex = result["number"]!!.jsonPrimitive.content
        return numberHex.removePrefix("0x").toLong(16)
    }

    /** system_accountNextIndex(address) → nonce (Int) */
    suspend fun systemAccountNextIndex(address: String): Int {
        val params = buildJsonArray { add(address) }
        return rpcCall("system_accountNextIndex", params).jsonPrimitive.int
    }

    /** payment_queryInfo(extrinsicHex) → CennzPartialFee */
    suspend fun paymentQueryInfo(extrinsicHex: String): CennzPartialFee {
        val params = buildJsonArray { add(extrinsicHex) }
        val result = rpcCall("payment_queryInfo", params)
        return json.decodeFromJsonElement(CennzPartialFee.serializer(), result)
    }

    /** author_submitExtrinsic(signedExtrinsicHex) → extrinsic hash string */
    suspend fun submitExtrinsic(signedExtrinsicHex: String): String {
        val params = buildJsonArray { add(signedExtrinsicHex) }
        return rpcCall("author_submitExtrinsic", params).jsonPrimitive.content
    }

    // ─── REST API methods ───────────────────────────────────────────

    /** POST scanAccount → ScanAccount (balances) */
    suspend fun scanAccount(address: String): ScanAccount {
        val payload = buildJsonObject { put("address", address) }
        val response: HttpResponse = try {
            client.post("$explorerBaseUrl${CENNZ_ENDPOINTS.SCAN_ACCOUNT}") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            throw WalletError.ConnectionError(explorerBaseUrl, e)
        }
        val body = json.decodeFromString(ScanAccountResponse.serializer(), response.bodyAsText())
        return body.data ?: ScanAccount()
    }

    /** POST scanTransfers → ScanTransfer (transfers list) */
    suspend fun scanTransfers(address: String, row: Int = 100, page: Int = 0): ScanTransfer {
        val payload = buildJsonObject {
            put("address", address)
            put("row", row)
            put("page", page)
        }
        val response: HttpResponse = try {
            client.post("$explorerBaseUrl${CENNZ_ENDPOINTS.SCAN_TRANSFERS}") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            throw WalletError.ConnectionError(explorerBaseUrl, e)
        }
        val body = json.decodeFromString(ScanTransferResponse.serializer(), response.bodyAsText())
        return body.data ?: ScanTransfer()
    }

    /** POST getPublicAddress(seed) → CentralityAddress */
    suspend fun getPublicAddress(seed: String): CentralityAddress {
        val payload = buildJsonObject { put("seed", seed) }
        val response: HttpResponse = try {
            client.post("$localApiBaseUrl${CENNZ_ENDPOINTS.GET_ADDRESS}") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            throw WalletError.ConnectionError(localApiBaseUrl, e)
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val address = body["address"]?.jsonPrimitive?.content ?: ""
        val publicKey = body["publicKey"]?.jsonPrimitive?.content ?: ""
        return CentralityAddress(address, publicKey)
    }

    /** POST signMessage(seed, payloadHex) → signature string */
    suspend fun signMessage(seed: String, payloadHex: String): String {
        val payload = buildJsonObject {
            put("seed", seed)
            put("payload", payloadHex)
        }
        val response: HttpResponse = try {
            client.post("$localApiBaseUrl${CENNZ_ENDPOINTS.SIGN_MESSAGE}") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            throw WalletError.ConnectionError(localApiBaseUrl, e)
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return body["signature"]?.jsonPrimitive?.content
            ?: throw CentralityError.SigningFailed("No signature in response")
    }
}

/** Endpoint constants for CennzNet. */
object CENNZ_ENDPOINTS {
    const val EXPLORER_SERVER = "https://service.eks.centralityapp.com"
    const val RPC_SERVER = "https://cennznet.unfrastructure.io"
    const val LOCAL_API_SERVER = "https://fgwallet.srsfc.com"

    const val SCAN_ACCOUNT = "/cennznet-explorer-api/api/scan/account"
    const val SCAN_TRANSFERS = "/cennznet-explorer-api/api/scan/transfers"
    const val GET_ADDRESS = "/cennz-address"
    const val SIGN_MESSAGE = "/cennz-sign"
}
