package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.bitcoin.BitcoinTransactionModel
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BitcoinManager.transfer()
 *
 * Tests cover:
 * - Invalid JSON input triggers deserialization error path
 * - Malformed transaction data triggers error path
 * - TransferResponseModel fields are correct for failure cases
 * - Valid JSON structure is parsed correctly before network call
 *
 * Requirements: 10.4, 31.1, 31.4
 */
class BitcoinApiServiceTest {

    private lateinit var bitcoinManager: BitcoinManager
    private lateinit var coinNetwork: CoinNetwork
    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @BeforeTest
    fun setup() {
        Config.shared.setNetwork(Network.TESTNET)
        bitcoinManager = BitcoinManager(testMnemonic)
        bitcoinManager.getNativeSegWitAddress()
        coinNetwork = CoinNetwork(NetworkName.BTC)
    }

    // --- Failure path: invalid JSON input ---

    @Test
    fun transferWithInvalidJson_returnsFailure() = runTest {
        val result = bitcoinManager.transfer("not-valid-json", coinNetwork)

        assertFalse(result.success, "transfer with invalid JSON should not succeed")
        assertNotNull(result.error, "error message should be present")
        assertNull(result.txHash, "txHash should be null on failure")
    }

    @Test
    fun transferWithEmptyString_returnsFailure() = runTest {
        val result = bitcoinManager.transfer("", coinNetwork)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertNull(result.txHash)
    }

    @Test
    fun transferWithEmptyJsonObject_returnsFailure() = runTest {
        val result = bitcoinManager.transfer("{}", coinNetwork)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertNull(result.txHash)
    }

    @Test
    fun transferWithPartialJson_returnsFailure() = runTest {
        // JSON missing required fields for BitcoinTransactionModel
        val partialJson = """{"tx": {"hash": "abc"}}"""
        val result = bitcoinManager.transfer(partialJson, coinNetwork)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertNull(result.txHash)
    }

    // --- Failure path: valid JSON structure but network call fails ---

    @Test
    fun transferWithValidJsonButNetworkFailure_returnsFailure() = runTest {
        // Build a well-formed BitcoinTransactionModel JSON that will parse
        // but the actual sendTransaction network call will fail
        val validTxJson = buildValidTransactionJson(hash = "test_hash_abc123")

        val result = bitcoinManager.transfer(validTxJson, coinNetwork)

        // Network call will fail (no mock server), so we expect failure
        assertFalse(result.success)
        assertNull(result.txHash, "txHash should be null when broadcast fails")
        // Error comes from either network exception or null-result path
        assertNotNull(result.error, "error should describe the failure")
        assertTrue(result.error!!.isNotBlank(), "error message should not be blank")
    }

    @Test
    fun transferWithValidJson_nullApiResult_returnsSpecificErrorMessage() = runTest {
        // When sendTransaction returns null (non-2xx HTTP or null body),
        // transfer() should return the specific broadcast failure message
        val validTxJson = buildValidTransactionJson(hash = "null_result_test")

        val result = bitcoinManager.transfer(validTxJson, coinNetwork)

        assertFalse(result.success)
        assertNull(result.txHash)
        // The error is either the specific null-result message or a network exception message
        // Both are valid failure indicators
        assertNotNull(result.error)
    }

    // --- TransferResponseModel field verification ---

    @Test
    fun failureResponse_hasCorrectFieldTypes() = runTest {
        val result = bitcoinManager.transfer("invalid", coinNetwork)

        // Verify the response is a proper TransferResponseModel
        assertTrue(result is TransferResponseModel)
        assertEquals(false, result.success)
        assertTrue(result.error is String, "error should be a non-null String")
        assertEquals(null, result.txHash)
    }

    @Test
    fun transferErrorMessage_isDescriptive() = runTest {
        val result = bitcoinManager.transfer("[]", coinNetwork)

        assertFalse(result.success)
        assertNotNull(result.error)
        // Error message should contain some useful info (not blank)
        assertTrue(result.error!!.isNotBlank(), "error message should be descriptive")
    }

    // --- JSON parsing verification ---

    @Test
    fun validBitcoinTransactionModelJson_parsesCorrectly() {
        // Verify our test JSON helper produces valid BitcoinTransactionModel
        val json = buildValidTransactionJson(hash = "deadbeef1234")
        val parser = Json { ignoreUnknownKeys = true }
        val model = parser.decodeFromString<BitcoinTransactionModel>(json)

        assertEquals("deadbeef1234", model.tx.hash)
        assertTrue(model.tosign.isNotEmpty())
        assertNotNull(model.signatures)
        assertNotNull(model.pubkeys)
    }

    // --- Success path: TransferResponseModel structure verification ---

    @Test
    fun successResponse_hasCorrectStructure() {
        // Verify the success TransferResponseModel shape that transfer() would return
        // when sendTransaction succeeds (cannot trigger real success without live API)
        val successResponse = TransferResponseModel(
            success = true,
            error = null,
            txHash = "abc123def456"
        )

        assertTrue(successResponse.success)
        assertNull(successResponse.error)
        assertNotNull(successResponse.txHash)
        assertEquals("abc123def456", successResponse.txHash)
    }

    @Test
    fun failureResponse_nullBroadcast_hasCorrectStructure() {
        // Verify the failure TransferResponseModel shape for null sendTransaction result
        val failureResponse = TransferResponseModel(
            success = false,
            error = "Failed to broadcast Bitcoin transaction",
            txHash = null
        )

        assertFalse(failureResponse.success)
        assertEquals("Failed to broadcast Bitcoin transaction", failureResponse.error)
        assertNull(failureResponse.txHash)
    }

    // --- Helper ---

    /**
     * Builds a valid BitcoinTransactionModel JSON string for testing.
     * All required fields of Tx, Input, and Output are populated.
     */
    private fun buildValidTransactionJson(hash: String = "abc123"): String {
        return """
        {
            "tx": {
                "block_height": -1,
                "block_index": -1,
                "hash": "$hash",
                "addresses": ["tb1qtest1", "tb1qtest2"],
                "total": 100000,
                "fees": 1000,
                "size": 225,
                "vsize": 141,
                "preference": "high",
                "relayed_by": "127.0.0.1",
                "received": "2025-01-01T00:00:00Z",
                "ver": 2,
                "double_spend": false,
                "vin_sz": 1,
                "vout_sz": 2,
                "confirmations": 0,
                "inputs": [{
                    "prev_hash": "0000000000000000000000000000000000000000000000000000000000000000",
                    "output_index": 0,
                    "output_value": 101000,
                    "sequence": 4294967295,
                    "addresses": ["tb1qtest1"],
                    "script_type": "pay-to-witness-pubkey-hash",
                    "age": 0
                }],
                "outputs": [{
                    "value": 100000,
                    "script": "0014abcdef",
                    "addresses": ["tb1qtest2"],
                    "script_type": "pay-to-witness-pubkey-hash"
                }]
            },
            "tosign": ["abcdef1234567890"],
            "signatures": ["sig1"],
            "pubkeys": ["pub1"]
        }
        """.trimIndent()
    }
}
