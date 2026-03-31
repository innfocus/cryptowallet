package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.errors.WalletError
import com.lybia.cryptowallet.wallets.centrality.CentralityError
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class CentralityApiServiceTest {

    private val rpcUrl = "https://test-rpc.example.com"
    private val explorerUrl = "https://test-explorer.example.com"
    private val localApiUrl = "https://test-local.example.com"
    private val json = Json { ignoreUnknownKeys = true }

    private fun service(client: HttpClient) = CentralityApiService(
        rpcBaseUrl = rpcUrl,
        explorerBaseUrl = explorerUrl,
        localApiBaseUrl = localApiUrl,
        client = client
    )

    private fun jsonMockClient(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
    }

    private fun capturingClient(
        responseBody: String,
        onRequest: (url: String, body: String, contentType: String?) -> Unit
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val reqBody = String(request.body.toByteArray())
                    val reqUrl = request.url.toString()
                    val ct = request.headers[HttpHeaders.ContentType]
                    onRequest(reqUrl, reqBody, ct)
                    respond(responseBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
    }

    // ── JSON-RPC request payload structure ───────────────────────

    @Test
    fun rpcCallSendsCorrectPayloadStructure() = runTest {
        var capturedBody = ""
        var capturedUrl = ""
        var capturedCt: String? = null
        val rpcResp = """{"id":1,"jsonrpc":"2.0","result":{"specVersion":39,"transactionVersion":5}}"""
        val client = capturingClient(rpcResp) { url, body, ct ->
            capturedUrl = url; capturedBody = body; capturedCt = ct
        }
        service(client).getRuntimeVersion()
        assertTrue(capturedBody.isNotEmpty(), "Request body should be captured")
        val payload = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals(1, payload["id"]!!.jsonPrimitive.int)
        assertEquals("2.0", payload["jsonrpc"]!!.jsonPrimitive.content)
        assertEquals("state_getRuntimeVersion", payload["method"]!!.jsonPrimitive.content)
        assertNotNull(payload["params"])
        assertTrue(capturedUrl.startsWith(rpcUrl), "URL should start with rpcUrl, got: $capturedUrl")
        assertTrue(capturedUrl.contains("/public"), "URL should contain /public, got: $capturedUrl")
        client.close()
    }

    @Test
    fun rpcCallSendsParamsCorrectly() = runTest {
        var capturedBody = ""
        val client = capturingClient("""{"id":1,"jsonrpc":"2.0","result":"0xabc"}""") { _, body, _ ->
            capturedBody = body
        }
        service(client).chainGetBlockHash()
        val payload = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("chain_getBlockHash", payload["method"]!!.jsonPrimitive.content)
        val params = payload["params"]!!.jsonArray
        assertEquals(1, params.size)
        assertEquals(0, params[0].jsonPrimitive.int)
        client.close()
    }

    @Test
    fun rpcCallSendsStringParamsCorrectly() = runTest {
        var capturedBody = ""
        val client = capturingClient("""{"id":1,"jsonrpc":"2.0","result":5}""") { _, body, _ ->
            capturedBody = body
        }
        service(client).systemAccountNextIndex("5Grwva")
        val payload = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("system_accountNextIndex", payload["method"]!!.jsonPrimitive.content)
        assertEquals("5Grwva", payload["params"]!!.jsonArray[0].jsonPrimitive.content)
        client.close()
    }

    // ── JSON-RPC successful responses ───────────────────────────

    @Test
    fun getRuntimeVersionParsesResponse() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":{"specVersion":39,"transactionVersion":5}}""")
        val (spec, tx) = service(client).getRuntimeVersion()
        assertEquals(39, spec)
        assertEquals(5, tx)
        client.close()
    }

    @Test
    fun chainGetBlockHashReturnsGenesisHash() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":"0xgenesis123"}""")
        assertEquals("0xgenesis123", service(client).chainGetBlockHash())
        client.close()
    }

    @Test
    fun chainGetFinalizedHeadReturnsBlockHash() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":"0xfinalized456"}""")
        assertEquals("0xfinalized456", service(client).chainGetFinalizedHead())
        client.close()
    }

    @Test
    fun chainGetHeaderReturnsBlockNumber() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":{"number":"0x6211cb"}}""")
        assertEquals(6427083L, service(client).chainGetHeader("0xhash"))
        client.close()
    }

    @Test
    fun systemAccountNextIndexReturnsNonce() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":42}""")
        assertEquals(42, service(client).systemAccountNextIndex("addr"))
        client.close()
    }

    @Test
    fun paymentQueryInfoReturnsFee() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":{"class":"normal","partialFee":1000,"weight":200}}""")
        val fee = service(client).paymentQueryInfo("0xext")
        assertEquals("normal", fee.classFee)
        assertEquals(1000, fee.partialFee)
        assertEquals(200, fee.weight)
        client.close()
    }

    @Test
    fun submitExtrinsicReturnsHash() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","result":"0xtxhash789"}""")
        assertEquals("0xtxhash789", service(client).submitExtrinsic("0xsigned"))
        client.close()
    }

    // ── JSON-RPC error → CentralityError.RpcError ───────────────

    @Test
    fun rpcErrorResponseThrowsRpcError() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0","error":{"code":-32601,"message":"Method not found"}}""")
        val error = assertFailsWith<CentralityError.RpcError> {
            service(client).getRuntimeVersion()
        }
        assertEquals("state_getRuntimeVersion", error.method)
        assertEquals(-32601, error.code)
        assertTrue(error.message.contains("Method not found"))
        client.close()
    }

    @Test
    fun rpcMissingResultThrowsRpcError() = runTest {
        val client = jsonMockClient("""{"id":1,"jsonrpc":"2.0"}""")
        val error = assertFailsWith<CentralityError.RpcError> {
            service(client).chainGetFinalizedHead()
        }
        assertTrue(error.message.contains("No result"))
        client.close()
    }

    // ── Network failure → WalletError.ConnectionError ───────────

    @Test
    fun networkFailureThrowsConnectionError() = runTest {
        val client = HttpClient(MockEngine) {
            engine { addHandler { throw Exception("Network unreachable") } }
        }
        val error = assertFailsWith<WalletError.ConnectionError> {
            service(client).getRuntimeVersion()
        }
        assertTrue(error.message.contains(rpcUrl))
        client.close()
    }

    @Test
    fun httpErrorStatusThrowsConnectionError() = runTest {
        val client = jsonMockClient("Internal Server Error", HttpStatusCode.InternalServerError)
        val error = assertFailsWith<WalletError.ConnectionError> {
            service(client).submitExtrinsic("0xsigned")
        }
        assertTrue(error.message.contains(rpcUrl))
        client.close()
    }

    // ── REST API methods ────────────────────────────────────────

    @Test
    fun scanAccountReturnsAccountData() = runTest {
        val resp = """{"code":0,"message":"OK","ttl":1,"data":{"address":"a1","nonce":10,"balances":[{"assetId":1,"free":50000,"lock":0}]}}"""
        val client = jsonMockClient(resp)
        val account = service(client).scanAccount("a1")
        assertEquals("a1", account.address)
        assertEquals(10L, account.nonce)
        assertEquals(1, account.balances.size)
        assertEquals(50000L, account.balances[0].free)
        client.close()
    }

    @Test
    fun scanAccountReturnsDefaultWhenDataNull() = runTest {
        val client = jsonMockClient("""{"code":0,"message":"OK","ttl":1,"data":null}""")
        val account = service(client).scanAccount("a1")
        assertEquals("", account.address)
        assertTrue(account.balances.isEmpty())
        client.close()
    }

    @Test
    fun scanTransfersReturnsData() = runTest {
        val resp = """{"code":0,"message":"OK","ttl":1,"data":{"transfers":[{"from":"a","to":"b","extrinsic_index":"1-0","hash":"0xh","block_num":100,"block_timestamp":170,"module":"ga","amount":5000,"asset_id":1,"success":true}],"count":1}}"""
        val client = jsonMockClient(resp)
        val result = service(client).scanTransfers("a1")
        assertEquals(1L, result.count)
        assertEquals("a", result.transfers[0].from)
        assertEquals(5000L, result.transfers[0].amount)
        client.close()
    }

    @Test
    fun getPublicAddressReturnsCentralityAddress() = runTest {
        val resp = """{"address":"5Grwva","publicKey":"0xd435"}"""
        val client = jsonMockClient(resp)
        val addr = service(client).getPublicAddress("seed")
        assertEquals("5Grwva", addr.address)
        assertNotNull(addr.publicKey)
        client.close()
    }

    @Test
    fun signMessageReturnsSignature() = runTest {
        val client = jsonMockClient("""{"signature":"0xabcdef"}""")
        assertEquals("0xabcdef", service(client).signMessage("seed", "0xpayload"))
        client.close()
    }

    @Test
    fun signMessageThrowsWhenNoSignature() = runTest {
        val client = jsonMockClient("""{"error":"bad"}""")
        assertFailsWith<CentralityError.SigningFailed> {
            service(client).signMessage("seed", "0xpayload")
        }
        client.close()
    }

    @Test
    fun restApiNetworkFailureThrowsConnectionError() = runTest {
        val client = HttpClient(MockEngine) {
            engine { addHandler { throw Exception("refused") } }
        }
        val error = assertFailsWith<WalletError.ConnectionError> {
            service(client).scanAccount("a1")
        }
        assertTrue(error.message.contains(explorerUrl))
        client.close()
    }

    // ── REST API payload verification ───────────────────────────

    @Test
    fun scanAccountSendsCorrectPayload() = runTest {
        var capturedBody = ""
        var capturedUrl = ""
        val resp = """{"code":0,"message":"OK","ttl":1,"data":{"address":"a1","nonce":0,"balances":[]}}"""
        val client = capturingClient(resp) { url, body, _ ->
            capturedUrl = url; capturedBody = body
        }
        service(client).scanAccount("a1")
        val payload = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("a1", payload["address"]!!.jsonPrimitive.content)
        assertEquals("$explorerUrl${CENNZ_ENDPOINTS.SCAN_ACCOUNT}", capturedUrl)
        client.close()
    }

    @Test
    fun scanTransfersSendsCorrectPayload() = runTest {
        var capturedBody = ""
        val resp = """{"code":0,"message":"OK","ttl":1,"data":{"transfers":[],"count":0}}"""
        val client = capturingClient(resp) { _, body, _ -> capturedBody = body }
        service(client).scanTransfers("a1", row = 50, page = 2)
        val payload = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("a1", payload["address"]!!.jsonPrimitive.content)
        assertEquals(50, payload["row"]!!.jsonPrimitive.int)
        assertEquals(2, payload["page"]!!.jsonPrimitive.int)
        client.close()
    }
}
