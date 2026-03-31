package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.models.ExplorerModel
import com.lybia.cryptowallet.models.GasPrice
import com.lybia.cryptowallet.models.InfuraRpcBalanceResponse
import com.lybia.cryptowallet.models.Transaction
import com.lybia.cryptowallet.models.TransactionToken
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Ethereum commonMain — verifies model parsing for
 * InfuraRpcService and ExplorerRpcService responses.
 * Requirements: 12.8
 */
class EthereumManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseInfuraBalanceResponse() {
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x1bc16d674ec80000"
            }
        """.trimIndent()
        val response = json.decodeFromString<InfuraRpcBalanceResponse>(responseJson)
        assertEquals("2.0", response.jsonrpc)
        assertEquals(1, response.id)
        assertNotNull(response.result)
        // 0x1bc16d674ec80000 = 2000000000000000000 wei = 2 ETH
        val weiValue = response.result!!.removePrefix("0x").toLong(16)
        assertEquals(2_000_000_000_000_000_000L, weiValue)
        val ethBalance = weiValue.toDouble() / 1e18
        assertEquals(2.0, ethBalance)
    }

    @Test
    fun parseInfuraErrorResponse() {
        val errorJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {
                    "code": -32602,
                    "message": "invalid argument"
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<InfuraRpcBalanceResponse>(errorJson)
        assertNotNull(response.error)
        assertEquals(-32602f, response.error!!.code)
    }

    @Test
    fun parseInfuraChainIdResponse() {
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x1"
            }
        """.trimIndent()
        val response = json.decodeFromString<InfuraRpcBalanceResponse>(responseJson)
        assertNotNull(response.result)
        val chainId = response.result!!.removePrefix("0x").toLong(16)
        assertEquals(1L, chainId) // Ethereum mainnet
    }

    @Test
    fun parseInfuraGasPriceResponse() {
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x3b9aca00"
            }
        """.trimIndent()
        val response = json.decodeFromString<InfuraRpcBalanceResponse>(responseJson)
        assertNotNull(response.result)
        val gasPrice = response.result!!.removePrefix("0x").toLong(16)
        assertEquals(1_000_000_000L, gasPrice) // 1 Gwei
    }

    @Test
    fun parseInfuraEstimateGasResponse() {
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x5208"
            }
        """.trimIndent()
        val response = json.decodeFromString<InfuraRpcBalanceResponse>(responseJson)
        assertNotNull(response.result)
        val gasLimit = response.result!!.removePrefix("0x").toLong(16)
        assertEquals(21000L, gasLimit) // Standard ETH transfer gas
    }

    @Test
    fun parseExplorerTransactionHistory() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": [
                    {
                        "blockNumber": "12345",
                        "timeStamp": "1700000000",
                        "hash": "0xabc123",
                        "nonce": "0",
                        "blockHash": "0xblock",
                        "transactionIndex": "0",
                        "from": "0xSender",
                        "to": "0xReceiver",
                        "value": "1000000000000000000",
                        "gas": "21000",
                        "gasPrice": "1000000000",
                        "isError": "0",
                        "txreceipt_status": "1",
                        "input": "0x",
                        "contractAddress": "",
                        "cumulativeGasUsed": "21000",
                        "gasUsed": "21000",
                        "confirmations": "100",
                        "methodId": "0x",
                        "functionName": ""
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ExplorerModel<List<Transaction>>>(responseJson)
        assertEquals("1", response.status)
        assertEquals(1, response.result.size)
        val tx = response.result.first()
        assertEquals("0xSender", tx.from)
        assertEquals("0xReceiver", tx.to)
        assertEquals("1000000000000000000", tx.value) // 1 ETH in wei
    }

    @Test
    fun parseExplorerTokenBalance() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": "1000000000"
            }
        """.trimIndent()
        val response = json.decodeFromString<ExplorerModel<String>>(responseJson)
        assertEquals("1", response.status)
        val balance = response.result.toDoubleOrNull() ?: 0.0
        assertTrue(balance > 0.0)
    }

    @Test
    fun parseExplorerGasOracle() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": {
                    "SafeGasPrice": "20",
                    "ProposeGasPrice": "25",
                    "FastGasPrice": "30"
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<ExplorerModel<GasPrice>>(responseJson)
        assertEquals("1", response.status)
        assertEquals("20", response.result.SafeGasPrice)
        assertEquals("25", response.result.ProposeGasPrice)
        assertEquals("30", response.result.FastGasPrice)
    }

    @Test
    fun parseExplorerTokenTransactionHistory() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": [
                    {
                        "blockNumber": "12345",
                        "timeStamp": "1700000000",
                        "hash": "0xtokentx",
                        "nonce": "1",
                        "blockHash": "0xblock",
                        "from": "0xSender",
                        "contractAddress": "0xToken",
                        "to": "0xReceiver",
                        "value": "500000000",
                        "tokenName": "USDT",
                        "tokenSymbol": "USDT",
                        "tokenDecimal": "6",
                        "transactionIndex": "0",
                        "gas": "60000",
                        "gasPrice": "1000000000",
                        "gasUsed": "45000",
                        "cumulativeGasUsed": "45000",
                        "input": "0xa9059cbb",
                        "confirmations": "50"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ExplorerModel<List<TransactionToken>>>(responseJson)
        assertEquals("1", response.status)
        assertEquals(1, response.result.size)
        val tokenTx = response.result.first()
        assertEquals("USDT", tokenTx.tokenSymbol)
        assertEquals("500000000", tokenTx.value)
        assertEquals("6", tokenTx.tokenDecimal)
    }

    @Test
    fun hexBalanceConversionZero() {
        val hex = "0x0"
        val value = hex.removePrefix("0x").toLong(16)
        assertEquals(0L, value)
    }

    @Test
    fun hexBalanceConversionLargeValue() {
        // 10 ETH = 10000000000000000000 wei = 0x8AC7230489E80000
        val hex = "0x8AC7230489E80000"
        val value = hex.removePrefix("0x").toULong(16)
        val ethBalance = value.toDouble() / 1e18
        assertEquals(10.0, ethBalance, 0.0001)
    }
}
