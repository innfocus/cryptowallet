package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.ExplorerModel
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.NFTTransaction
import com.lybia.cryptowallet.models.TransferResponseModel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Ethereum NFT operations.
 *
 * Tests cover:
 * - NFTTransaction model parsing from Etherscan/Arbiscan API responses
 * - NFTTransaction → NFTItem mapping logic (distinctBy, field mapping)
 * - transferNFT success/failure TransferResponseModel structure
 * - Edge cases: empty results, duplicate NFTs, invalid tokenID
 *
 * Requirements: 11.5, 32.5
 */
class EthereumNFTTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── NFTTransaction model parsing ────────────────────────────────

    @Test
    fun parseNFTTransactionResponse_singleItem() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": [
                    {
                        "contractAddress": "0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D",
                        "tokenID": "1234",
                        "tokenName": "BoredApeYachtClub",
                        "tokenSymbol": "BAYC",
                        "from": "0xSender",
                        "to": "0xReceiver"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ExplorerModel<List<NFTTransaction>>>(responseJson)
        assertEquals("1", response.status)
        assertEquals("OK", response.message)
        assertEquals(1, response.result.size)

        val nft = response.result.first()
        assertEquals("0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D", nft.contractAddress)
        assertEquals("1234", nft.tokenID)
        assertEquals("BoredApeYachtClub", nft.tokenName)
        assertEquals("BAYC", nft.tokenSymbol)
        assertEquals("0xSender", nft.from)
        assertEquals("0xReceiver", nft.to)
    }

    @Test
    fun parseNFTTransactionResponse_multipleItems() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": [
                    {
                        "contractAddress": "0xContractA",
                        "tokenID": "1",
                        "tokenName": "CollectionA",
                        "tokenSymbol": "CA",
                        "from": "0xFrom1",
                        "to": "0xTo1"
                    },
                    {
                        "contractAddress": "0xContractB",
                        "tokenID": "42",
                        "tokenName": "CollectionB",
                        "tokenSymbol": "CB",
                        "from": "0xFrom2",
                        "to": "0xTo2"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ExplorerModel<List<NFTTransaction>>>(responseJson)
        assertEquals(2, response.result.size)
        assertEquals("0xContractA", response.result[0].contractAddress)
        assertEquals("0xContractB", response.result[1].contractAddress)
    }

    @Test
    fun parseNFTTransactionResponse_emptyResult() {
        val responseJson = """
            {
                "status": "0",
                "message": "No transactions found",
                "result": []
            }
        """.trimIndent()

        val response = json.decodeFromString<ExplorerModel<List<NFTTransaction>>>(responseJson)
        assertEquals("0", response.status)
        assertTrue(response.result.isEmpty())
    }

    // ── NFTTransaction → NFTItem mapping ────────────────────────────

    @Test
    fun mapNFTTransaction_toNFTItem_correctFields() {
        val nftTx = NFTTransaction(
            contractAddress = "0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D",
            tokenID = "5678",
            tokenName = "BoredApeYachtClub",
            tokenSymbol = "BAYC",
            from = "0xSender",
            to = "0xReceiver"
        )

        // Replicate the mapping logic from EthereumManager.getNFTs()
        val nftItem = NFTItem(
            coin = ACTCoin.Ethereum,
            address = nftTx.contractAddress,
            collectionAddress = nftTx.contractAddress,
            index = nftTx.tokenID.toLongOrNull() ?: 0L,
            name = nftTx.tokenName,
            description = null,
            imageUrl = null
        )

        assertEquals(ACTCoin.Ethereum, nftItem.coin)
        assertEquals("0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D", nftItem.address)
        assertEquals("0xBC4CA0EdA7647A8aB7C2061c2E118A18a936f13D", nftItem.collectionAddress)
        assertEquals(5678L, nftItem.index)
        assertEquals("BoredApeYachtClub", nftItem.name)
        assertNull(nftItem.description)
        assertNull(nftItem.imageUrl)
    }

    @Test
    fun mapNFTTransaction_invalidTokenID_defaultsToZero() {
        val nftTx = NFTTransaction(
            contractAddress = "0xContract",
            tokenID = "not-a-number",
            tokenName = "TestNFT",
            tokenSymbol = "TNFT",
            from = "0xFrom",
            to = "0xTo"
        )

        val index = nftTx.tokenID.toLongOrNull() ?: 0L
        assertEquals(0L, index)
    }

    @Test
    fun mapNFTTransaction_largeTokenID_parsesCorrectly() {
        val nftTx = NFTTransaction(
            contractAddress = "0xContract",
            tokenID = "999999999999",
            tokenName = "LargeID",
            tokenSymbol = "LID",
            from = "0xFrom",
            to = "0xTo"
        )

        val index = nftTx.tokenID.toLongOrNull() ?: 0L
        assertEquals(999999999999L, index)
    }

    // ── distinctBy deduplication logic ───────────────────────────────

    @Test
    fun distinctBy_removeDuplicateNFTs() {
        val transactions = listOf(
            NFTTransaction("0xContractA", "1", "NFT_A", "A", "0xFrom", "0xTo"),
            NFTTransaction("0xContractA", "1", "NFT_A", "A", "0xTo", "0xFrom"),  // duplicate
            NFTTransaction("0xContractA", "2", "NFT_A", "A", "0xFrom", "0xTo"),  // same contract, different token
            NFTTransaction("0xContractB", "1", "NFT_B", "B", "0xFrom", "0xTo")   // different contract
        )

        // Replicate EthereumManager.getNFTs() distinctBy logic
        val distinct = transactions.distinctBy { it.contractAddress + it.tokenID }

        assertEquals(3, distinct.size)
        assertEquals("0xContractA", distinct[0].contractAddress)
        assertEquals("1", distinct[0].tokenID)
        assertEquals("0xContractA", distinct[1].contractAddress)
        assertEquals("2", distinct[1].tokenID)
        assertEquals("0xContractB", distinct[2].contractAddress)
        assertEquals("1", distinct[2].tokenID)
    }

    @Test
    fun distinctBy_allUnique_noRemoval() {
        val transactions = listOf(
            NFTTransaction("0xA", "1", "A", "A", "0xF", "0xT"),
            NFTTransaction("0xB", "2", "B", "B", "0xF", "0xT"),
            NFTTransaction("0xC", "3", "C", "C", "0xF", "0xT")
        )

        val distinct = transactions.distinctBy { it.contractAddress + it.tokenID }
        assertEquals(3, distinct.size)
    }

    @Test
    fun distinctBy_emptyList_returnsEmpty() {
        val transactions = emptyList<NFTTransaction>()
        val distinct = transactions.distinctBy { it.contractAddress + it.tokenID }
        assertTrue(distinct.isEmpty())
    }

    // ── Full mapping pipeline ───────────────────────────────────────

    @Test
    fun fullMappingPipeline_parseThenMapToNFTItems() {
        val responseJson = """
            {
                "status": "1",
                "message": "OK",
                "result": [
                    {
                        "contractAddress": "0xABC",
                        "tokenID": "10",
                        "tokenName": "CoolNFT",
                        "tokenSymbol": "CNFT",
                        "from": "0xSender",
                        "to": "0xOwner"
                    },
                    {
                        "contractAddress": "0xABC",
                        "tokenID": "10",
                        "tokenName": "CoolNFT",
                        "tokenSymbol": "CNFT",
                        "from": "0xOwner",
                        "to": "0xBuyer"
                    },
                    {
                        "contractAddress": "0xDEF",
                        "tokenID": "99",
                        "tokenName": "RareNFT",
                        "tokenSymbol": "RNFT",
                        "from": "0xMinter",
                        "to": "0xOwner"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ExplorerModel<List<NFTTransaction>>>(responseJson)
        assertEquals(3, response.result.size)

        // Apply the same pipeline as EthereumManager.getNFTs()
        val nftItems = response.result
            .distinctBy { it.contractAddress + it.tokenID }
            .map { nft ->
                NFTItem(
                    coin = ACTCoin.Ethereum,
                    address = nft.contractAddress,
                    collectionAddress = nft.contractAddress,
                    index = nft.tokenID.toLongOrNull() ?: 0L,
                    name = nft.tokenName,
                    description = null,
                    imageUrl = null
                )
            }

        // Duplicate (0xABC + 10) should be removed
        assertEquals(2, nftItems.size)

        val first = nftItems[0]
        assertEquals("0xABC", first.address)
        assertEquals(10L, first.index)
        assertEquals("CoolNFT", first.name)

        val second = nftItems[1]
        assertEquals("0xDEF", second.address)
        assertEquals(99L, second.index)
        assertEquals("RareNFT", second.name)
    }

    // ── transferNFT response structure ──────────────────────────────

    @Test
    fun transferNFT_successResponse_hasCorrectStructure() {
        val response = TransferResponseModel(
            success = true,
            error = null,
            txHash = "0xabcdef1234567890"
        )

        assertTrue(response.success)
        assertNull(response.error)
        assertNotNull(response.txHash)
        assertEquals("0xabcdef1234567890", response.txHash)
    }

    @Test
    fun transferNFT_failureResponse_hasCorrectStructure() {
        val response = TransferResponseModel(
            success = false,
            error = "insufficient funds for gas",
            txHash = null
        )

        assertFalse(response.success)
        assertNotNull(response.error)
        assertEquals("insufficient funds for gas", response.error)
        assertNull(response.txHash)
    }

    @Test
    fun transferNFT_networkError_hasDescriptiveMessage() {
        val response = TransferResponseModel(
            success = false,
            error = "Connection refused",
            txHash = null
        )

        assertFalse(response.success)
        assertNotNull(response.error)
        assertTrue(response.error!!.isNotBlank())
        assertNull(response.txHash)
    }

    // ── Infura RPC response parsing for sendSignedTransaction ───────

    @Test
    fun parseInfuraSendRawTransaction_successResponse() {
        val responseJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x9fc76417374aa880d4449a1f7f31ec597f00b1f6f3dd2d66f4c9c6c445836d8b"
            }
        """.trimIndent()

        val response = json.decodeFromString<com.lybia.cryptowallet.models.InfuraRpcBalanceResponse>(responseJson)
        assertNotNull(response.result)
        assertNull(response.error)
        assertTrue(response.result!!.startsWith("0x"))
    }

    @Test
    fun parseInfuraSendRawTransaction_errorResponse() {
        val errorJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {
                    "code": -32000,
                    "message": "already known"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<com.lybia.cryptowallet.models.InfuraRpcBalanceResponse>(errorJson)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32000f, response.error!!.code)
        assertEquals("already known", response.error!!.message)
    }

    @Test
    fun parseInfuraSendRawTransaction_nonceError() {
        val errorJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {
                    "code": -32000,
                    "message": "nonce too low"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<com.lybia.cryptowallet.models.InfuraRpcBalanceResponse>(errorJson)
        assertNotNull(response.error)
        assertEquals("nonce too low", response.error!!.message)
    }
}
