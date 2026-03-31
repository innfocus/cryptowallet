package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.models.ripple.RippleAccountData
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResponse
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResult
import com.lybia.cryptowallet.models.ripple.RippleAccountTxResponse
import com.lybia.cryptowallet.models.ripple.RippleSubmitResponse
import com.lybia.cryptowallet.utils.fromHexToByteArray
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Ripple networking — verifies model parsing and conversion logic.
 * Requirements: 12.7
 */
class RippleApiServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseAccountInfoResponse() {
        val responseJson = """
            {
                "result": {
                    "status": "success",
                    "account_data": {
                        "Account": "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                        "Balance": "100000000",
                        "Sequence": 1,
                        "Flags": 0
                    },
                    "ledger_current_index": 12345
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleAccountInfoResponse>(responseJson)
        assertEquals("success", response.result.status)
        assertNotNull(response.result.accountData)
        assertEquals("100000000", response.result.accountData!!.balance)
        val balanceXrp = response.result.accountData!!.balance.toLong() / 1_000_000.0
        assertEquals(100.0, balanceXrp)
    }

    @Test
    fun parseAccountInfoErrorResponse() {
        val errorJson = """
            {
                "result": {
                    "status": "error",
                    "error": "actNotFound",
                    "error_message": "Account not found."
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleAccountInfoResponse>(errorJson)
        assertEquals("error", response.result.status)
        assertEquals("actNotFound", response.result.error)
        assertNull(response.result.accountData)
    }

    @Test
    fun parseAccountTxResponse() {
        val txJson = """
            {
                "result": {
                    "status": "success",
                    "transactions": [
                        {
                            "tx": {
                                "Account": "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                                "Destination": "rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe",
                                "Amount": "25000000",
                                "Fee": "12",
                                "hash": "E08D6E9754025BA2534A78707605E0601F03ACE063687A0CA1BDDACFCD1698C7",
                                "date": 738574820,
                                "TransactionType": "Payment"
                            },
                            "meta": {
                                "TransactionResult": "tesSUCCESS"
                            },
                            "validated": true
                        }
                    ]
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleAccountTxResponse>(txJson)
        assertEquals("success", response.result.status)
        assertNotNull(response.result.transactions)
        assertEquals(1, response.result.transactions!!.size)
        val entry = response.result.transactions!!.first()
        assertTrue(entry.validated)
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", entry.tx!!.account)
        assertEquals("25000000", entry.tx!!.amount)
        val amountXrp = entry.tx!!.amount.toLong() / 1_000_000.0
        assertEquals(25.0, amountXrp)
    }

    @Test
    fun parseAccountTxWithMemos() {
        val txJson = """
            {
                "result": {
                    "status": "success",
                    "transactions": [
                        {
                            "tx": {
                                "Account": "rSender",
                                "Destination": "rReceiver",
                                "Amount": "1000000",
                                "Fee": "12",
                                "hash": "ABCDEF",
                                "date": 738574820,
                                "DestinationTag": 12345,
                                "Memos": [{"Memo": {"MemoData": "48656c6c6f", "MemoType": "746578742f706c61696e"}}],
                                "TransactionType": "Payment"
                            },
                            "meta": {"TransactionResult": "tesSUCCESS"},
                            "validated": true
                        }
                    ]
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleAccountTxResponse>(txJson)
        val tx = response.result.transactions!!.first().tx!!
        assertEquals(12345L, tx.destinationTag)
        assertNotNull(tx.memos)
        val memoText = tx.memos!!.first().memo.memoData?.fromHexToByteArray()?.toString(Charsets.UTF_8)
        assertEquals("Hello", memoText)
    }

    @Test
    fun parseSubmitSuccessResponse() {
        val submitJson = """
            {
                "result": {
                    "status": "success",
                    "engine_result": "tesSUCCESS",
                    "engine_result_message": "The transaction was applied.",
                    "tx_json": {"hash": "ABCDEF123456"}
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleSubmitResponse>(submitJson)
        assertEquals("success", response.result.status)
        assertEquals("tesSUCCESS", response.result.engineResult)
        assertEquals("ABCDEF123456", response.result.tx_json!!.hash)
    }

    @Test
    fun parseSubmitErrorResponse() {
        val errorJson = """
            {
                "result": {
                    "status": "error",
                    "error": "invalidTransaction",
                    "error_message": "Transaction is not valid."
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<RippleSubmitResponse>(errorJson)
        assertEquals("error", response.result.status)
        assertEquals("invalidTransaction", response.result.error)
    }

    @Test
    fun dropsToXrpConversion() {
        assertEquals(1.0, 1_000_000L / 1_000_000.0)
        assertEquals(0.000001, 1L / 1_000_000.0, 0.0000001)
        assertEquals(100.0, 100_000_000L / 1_000_000.0)
        assertEquals(0.0, 0L / 1_000_000.0)
    }

    @Test
    fun zeroBalanceReturnsZero() {
        val response = RippleAccountInfoResponse(
            result = RippleAccountInfoResult(
                status = "success",
                accountData = RippleAccountData(account = "rTest", balance = "0", sequence = 1)
            )
        )
        val balance = response.result.accountData!!.balance.toLong() / 1_000_000.0
        assertEquals(0.0, balance)
    }
}
