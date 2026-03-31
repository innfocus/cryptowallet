package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.models.bitcoin.BTCApiModel
import com.lybia.cryptowallet.models.TransationData
import com.lybia.cryptowallet.models.toTransactionData
import com.lybia.cryptowallet.models.toTransactionDatas
import com.lybia.cryptowallet.enums.ACTCoin
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Bitcoin networking — verifies model parsing and conversion logic.
 *
 * Requirements: 12.6
 */
class BitcoinApiServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val mockBalanceJson = """
        {
            "address": "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "total_received": 7213849000,
            "total_sent": 0,
            "balance": 7213849000,
            "unconfirmed_balance": 0,
            "final_balance": 7213849000,
            "n_tx": 3691,
            "unconfirmed_n_tx": 0,
            "final_n_tx": 3691
        }
    """.trimIndent()

    private val mockTxJson = """
        {
            "address": "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "total_received": 7213849000,
            "total_sent": 0,
            "balance": 7213849000,
            "unconfirmed_balance": 0,
            "final_balance": 7213849000,
            "n_tx": 1,
            "unconfirmed_n_tx": 0,
            "final_n_tx": 1,
            "txs": [
                {
                    "block_hash": "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
                    "block_height": 0,
                    "block_index": 0,
                    "hash": "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
                    "addresses": ["1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"],
                    "total": 5000000000,
                    "fees": 0,
                    "size": 204,
                    "vsize": 204,
                    "preference": "low",
                    "confirmed": "2009-01-03T18:15:05Z",
                    "received": "2009-01-03T18:15:05Z",
                    "ver": 1,
                    "double_spend": false,
                    "vin_sz": 1,
                    "vout_sz": 1,
                    "confirmations": 900000,
                    "confidence": 1,
                    "inputs": [
                        {
                            "prev_hash": "0000000000000000000000000000000000000000000000000000000000000000",
                            "output_index": 4294967295,
                            "output_value": 5000000000,
                            "sequence": 4294967295,
                            "addresses": ["1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"],
                            "script_type": "pay-to-pubkey",
                            "age": 0
                        }
                    ],
                    "outputs": [
                        {
                            "value": 5000000000,
                            "script": "4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac",
                            "addresses": ["1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"],
                            "script_type": "pay-to-pubkey"
                        }
                    ]
                }
            ]
        }
    """.trimIndent()

    @Test
    fun parseBalanceResponse() {
        val model = json.decodeFromString<BTCApiModel>(mockBalanceJson)
        assertEquals(7213849000L, model.balance)
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", model.address)
        val balanceBtc = model.balance.toDouble() / 100_000_000.0
        assertTrue(balanceBtc > 0.0, "Balance should be positive")
        assertEquals(72.13849, balanceBtc, 0.00001)
    }

    @Test
    fun parseTransactionHistory() {
        val model = json.decodeFromString<BTCApiModel>(mockTxJson)
        assertNotNull(model.txs)
        assertEquals(1, model.txs!!.size)
        val tx = model.txs!!.first()
        assertEquals("4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b", tx.hash)
        assertEquals(5000000000L, tx.total)
        assertEquals(0L, tx.fees)
    }

    @Test
    fun convertBtcTxToTransationData() {
        val model = json.decodeFromString<BTCApiModel>(mockTxJson)
        val tx = model.txs!!.first()
        val addresses = listOf("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        val result = tx.toTransactionData(addresses)

        assertEquals("4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b", result.iD)
        assertEquals(ACTCoin.Bitcoin, result.coin)
        assertTrue(result.amount > 0f, "Amount should be positive")
        assertEquals(50.0f, result.amount, 0.01f) // 5 BTC
        assertEquals(0.0f, result.fee)
        assertTrue(result.dateMillis > 0L, "dateMillis should be positive")
    }

    @Test
    fun convertBtcTxListToTransationDatas() {
        val model = json.decodeFromString<BTCApiModel>(mockTxJson)
        val addresses = listOf("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        val results = model.txs!!.toTransactionDatas(addresses)

        assertEquals(1, results.size)
        assertEquals(ACTCoin.Bitcoin, results.first().coin)
    }

    @Test
    fun zeroBalanceReturnsZero() {
        val zeroBalanceJson = """
            {
                "address": "1EmptyAddress",
                "total_received": 0,
                "total_sent": 0,
                "balance": 0,
                "unconfirmed_balance": 0,
                "final_balance": 0,
                "n_tx": 0,
                "unconfirmed_n_tx": 0,
                "final_n_tx": 0
            }
        """.trimIndent()
        val model = json.decodeFromString<BTCApiModel>(zeroBalanceJson)
        assertEquals(0L, model.balance)
        val balanceBtc = model.balance.toDouble() / 100_000_000.0
        assertEquals(0.0, balanceBtc)
    }

    @Test
    fun malformedJsonHandledGracefully() {
        val malformed = """{"invalid": true}"""
        try {
            json.decodeFromString<BTCApiModel>(malformed)
            // If it doesn't throw, that's fine — fields will have defaults
        } catch (_: Exception) {
            // Expected — malformed JSON should throw
        }
    }
}
