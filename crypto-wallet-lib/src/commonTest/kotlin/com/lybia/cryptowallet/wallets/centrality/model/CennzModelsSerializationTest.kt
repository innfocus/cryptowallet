package com.lybia.cryptowallet.wallets.centrality.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based and unit tests for Centrality data model serialization.
 */
class CennzModelsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Property Tests ----

    // Feature: centrality-kmp-migration, Property 5: Model serialization round-trip
    // **Validates: Requirements 4.8, 4.9**

    @Test
    fun cennzTransferSerializationRoundTrip() = runTest {
        val arbTransfer = Arb.bind(
            Arb.string(0..20),  // from
            Arb.string(0..20),  // to
            Arb.string(0..10),  // extrinsicIndex
            Arb.string(0..20),  // hash
            Arb.long(0L..Long.MAX_VALUE),  // blockNum
            Arb.long(0L..Long.MAX_VALUE),  // blockTimestamp
            Arb.string(0..10),  // module
            Arb.long(0L..Long.MAX_VALUE),  // amount
            Arb.int(0..Int.MAX_VALUE),  // assetId
            Arb.boolean()  // success
        ) { from, to, exIdx, hash, blockNum, blockTs, module, amount, assetId, success ->
            CennzTransfer(from, to, exIdx, hash, blockNum, blockTs, module, amount, assetId, success)
        }

        checkAll(200, arbTransfer) { original ->
            val encoded = json.encodeToString(CennzTransfer.serializer(), original)
            val decoded = json.decodeFromString(CennzTransfer.serializer(), encoded)
            assertEquals(original, decoded, "CennzTransfer round-trip failed for: $original")
        }
    }

    @Test
    fun cennzExtrinsicSerializationRoundTrip() = runTest {
        val arbExtrinsic = Arb.bind(
            Arb.string(0..20),  // accountId
            Arb.long(0L..Long.MAX_VALUE),  // blockNum
            Arb.long(0L..Long.MAX_VALUE),  // blockTimestamp
            Arb.string(0..10),  // extrinsicIndex
            Arb.string(0..20),  // extrinsicHash
            Arb.string(0..10),  // callModule
            Arb.string(0..10),  // callModuleFunction
            Arb.string(0..20),  // params
            Arb.long(0L..Long.MAX_VALUE),  // fee
            Arb.boolean(),  // success
            Arb.long(0L..Long.MAX_VALUE)  // nonce
        ) { accId, blockNum, blockTs, exIdx, exHash, callMod, callModFn, params, fee, success, nonce ->
            CennzExtrinsic(accId, blockNum, blockTs, exIdx, exHash, callMod, callModFn, params, fee, success, nonce)
        }

        checkAll(200, arbExtrinsic) { original ->
            val encoded = json.encodeToString(CennzExtrinsic.serializer(), original)
            val decoded = json.decodeFromString(CennzExtrinsic.serializer(), encoded)
            assertEquals(original, decoded, "CennzExtrinsic round-trip failed for: $original")
        }
    }

    @Test
    fun cennzPartialFeeSerializationRoundTrip() = runTest {
        val arbFee = Arb.bind(
            Arb.string(0..10),  // classFee
            Arb.int(0..Int.MAX_VALUE),  // partialFee
            Arb.int(0..Int.MAX_VALUE)  // weight
        ) { classFee, partialFee, weight ->
            CennzPartialFee(classFee, partialFee, weight)
        }

        checkAll(200, arbFee) { original ->
            val encoded = json.encodeToString(CennzPartialFee.serializer(), original)
            val decoded = json.decodeFromString(CennzPartialFee.serializer(), encoded)
            assertEquals(original, decoded, "CennzPartialFee round-trip failed for: $original")
        }
    }

    @Test
    fun cennzScanAssetSerializationRoundTrip() = runTest {
        val arbAsset = Arb.bind(
            Arb.int(0..Int.MAX_VALUE),  // assetId
            Arb.long(0L..Long.MAX_VALUE),  // free
            Arb.long(0L..Long.MAX_VALUE)  // lock
        ) { assetId, free, lock ->
            CennzScanAsset(assetId, free, lock)
        }

        checkAll(200, arbAsset) { original ->
            val encoded = json.encodeToString(CennzScanAsset.serializer(), original)
            val decoded = json.decodeFromString(CennzScanAsset.serializer(), encoded)
            assertEquals(original, decoded, "CennzScanAsset round-trip failed for: $original")
        }
    }

    // ---- Unit Tests (Task 5.7) ----

    @Test
    fun cennzTransferSerialNameMapping() {
        val transfer = CennzTransfer(
            from = "5GrwvaEF",
            to = "5FHneW46",
            extrinsicIndex = "123-1",
            hash = "0xabc",
            blockNum = 100,
            blockTimestamp = 1700000000,
            module = "genericAsset",
            amount = 5000,
            assetId = 1,
            success = true
        )
        val encoded = json.encodeToString(CennzTransfer.serializer(), transfer)

        // Verify snake_case keys from @SerialName
        assertTrue(encoded.contains("\"extrinsic_index\""), "Should use snake_case extrinsic_index")
        assertTrue(encoded.contains("\"block_num\""), "Should use snake_case block_num")
        assertTrue(encoded.contains("\"block_timestamp\""), "Should use snake_case block_timestamp")
        assertTrue(encoded.contains("\"asset_id\""), "Should use snake_case asset_id")

        // Verify camelCase keys are NOT present
        assertTrue(!encoded.contains("\"extrinsicIndex\""), "Should not use camelCase extrinsicIndex")
        assertTrue(!encoded.contains("\"blockNum\""), "Should not use camelCase blockNum")
        assertTrue(!encoded.contains("\"blockTimestamp\""), "Should not use camelCase blockTimestamp")
        assertTrue(!encoded.contains("\"assetId\""), "Should not use camelCase assetId")
    }

    @Test
    fun cennzExtrinsicSerialNameMapping() {
        val extrinsic = CennzExtrinsic(
            accountId = "5GrwvaEF",
            blockNum = 200,
            blockTimestamp = 1700000001,
            extrinsicIndex = "200-0",
            extrinsicHash = "0xdef",
            callModule = "genericAsset",
            callModuleFunction = "transfer",
            params = "[]",
            fee = 100,
            success = true,
            nonce = 5
        )
        val encoded = json.encodeToString(CennzExtrinsic.serializer(), extrinsic)

        // Verify snake_case keys from @SerialName
        assertTrue(encoded.contains("\"account_id\""), "Should use snake_case account_id")
        assertTrue(encoded.contains("\"block_num\""), "Should use snake_case block_num")
        assertTrue(encoded.contains("\"block_timestamp\""), "Should use snake_case block_timestamp")
        assertTrue(encoded.contains("\"extrinsic_index\""), "Should use snake_case extrinsic_index")
        assertTrue(encoded.contains("\"extrinsic_hash\""), "Should use snake_case extrinsic_hash")
        assertTrue(encoded.contains("\"call_module\""), "Should use snake_case call_module")
        assertTrue(encoded.contains("\"call_module_function\""), "Should use snake_case call_module_function")
    }

    @Test
    fun scanAccountDefaultBalancesIsEmptyList() {
        val account = ScanAccount()
        assertEquals("", account.address)
        assertEquals(0L, account.nonce)
        assertEquals(emptyList(), account.balances)
    }

    @Test
    fun scanAccountDeserializationWithBalances() {
        val jsonStr = """
            {
                "address": "5GrwvaEF",
                "nonce": 10,
                "balances": [
                    {"assetId": 1, "free": 50000, "lock": 0},
                    {"assetId": 2, "free": 30000, "lock": 100}
                ]
            }
        """.trimIndent()
        val account = json.decodeFromString(ScanAccount.serializer(), jsonStr)

        assertEquals("5GrwvaEF", account.address)
        assertEquals(10L, account.nonce)
        assertEquals(2, account.balances.size)
        assertEquals(1, account.balances[0].assetId)
        assertEquals(50000L, account.balances[0].free)
        assertEquals(2, account.balances[1].assetId)
    }

    @Test
    fun scanAccountResponseDeserialization() {
        val jsonStr = """
            {
                "code": 0,
                "message": "Success",
                "ttl": 1,
                "data": {
                    "address": "5GrwvaEF",
                    "nonce": 5,
                    "balances": []
                }
            }
        """.trimIndent()
        val response = json.decodeFromString(ScanAccountResponse.serializer(), jsonStr)

        assertEquals(0L, response.code)
        assertEquals("Success", response.message)
        assertEquals(1L, response.ttl)
        assertEquals("5GrwvaEF", response.data?.address)
        assertEquals(emptyList(), response.data?.balances)
    }

    @Test
    fun scanTransferResponseDeserialization() {
        val jsonStr = """
            {
                "code": 0,
                "message": "Success",
                "ttl": 1,
                "data": {
                    "transfers": [
                        {
                            "from": "5GrwvaEF",
                            "to": "5FHneW46",
                            "extrinsic_index": "100-1",
                            "hash": "0xabc",
                            "block_num": 100,
                            "block_timestamp": 1700000000,
                            "module": "genericAsset",
                            "amount": 5000,
                            "asset_id": 1,
                            "success": true
                        }
                    ],
                    "count": 1
                }
            }
        """.trimIndent()
        val response = json.decodeFromString(ScanTransferResponse.serializer(), jsonStr)

        assertEquals(0L, response.code)
        assertEquals(1, response.data?.transfers?.size)
        assertEquals("5GrwvaEF", response.data?.transfers?.get(0)?.from)
        assertEquals(1L, response.data?.count)
    }

    @Test
    fun cennzPartialFeeClassFieldMapping() {
        // "class" is a reserved keyword in Kotlin, mapped via @SerialName("class")
        val jsonStr = """{"class":"normal","partialFee":1000,"weight":500}"""
        val fee = json.decodeFromString(CennzPartialFee.serializer(), jsonStr)

        assertEquals("normal", fee.classFee)
        assertEquals(1000, fee.partialFee)
        assertEquals(500, fee.weight)

        // Verify serialization outputs "class" not "classFee"
        val encoded = json.encodeToString(CennzPartialFee.serializer(), fee)
        assertTrue(encoded.contains("\"class\""), "Should serialize as 'class'")
        assertTrue(!encoded.contains("\"classFee\""), "Should not serialize as 'classFee'")
    }
}
