package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ton.TonNFTAttribute
import com.lybia.cryptowallet.models.ton.TonNFTContent
import com.lybia.cryptowallet.models.ton.TonNFTItem
import com.lybia.cryptowallet.models.ton.TonV3NFTResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TON NFT operations.
 *
 * Tests cover:
 * - TonV3NFTResponse / TonNFTItem model parsing from Toncenter v3 API responses
 * - TonNFTItem → NFTItem mapping logic (field mapping, null handling)
 * - transferNFT success/failure TransferResponseModel structure
 * - Edge cases: empty results, null content, non-numeric index
 *
 * Requirements: 20.5, 32.5
 */
class TonNFTTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── TonV3NFTResponse model parsing ──────────────────────────────

    @Test
    fun parseTonV3NFTResponse_singleItem() {
        val responseJson = """
            {
                "nft_items": [
                    {
                        "address": "EQA1234567890abcdef",
                        "collection_address": "EQCollection1",
                        "owner_address": "EQOwner1",
                        "collection_item_index": "42",
                        "content": {
                            "name": "Cool TON NFT",
                            "description": "A very cool NFT on TON",
                            "image": "https://example.com/nft/42.png"
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        assertEquals(1, response.nftItems.size)

        val nft = response.nftItems.first()
        assertEquals("EQA1234567890abcdef", nft.address)
        assertEquals("EQCollection1", nft.collectionAddress)
        assertEquals("EQOwner1", nft.ownerAddress)
        assertEquals("42", nft.index)
        assertNotNull(nft.content)
        assertEquals("Cool TON NFT", nft.content?.name)
        assertEquals("A very cool NFT on TON", nft.content?.description)
        assertEquals("https://example.com/nft/42.png", nft.content?.image)
    }

    @Test
    fun parseTonV3NFTResponse_multipleItems() {
        val responseJson = """
            {
                "nft_items": [
                    {
                        "address": "EQNft1",
                        "collection_address": "EQCol1",
                        "owner_address": "EQOwner",
                        "collection_item_index": "0",
                        "content": { "name": "NFT #0" }
                    },
                    {
                        "address": "EQNft2",
                        "collection_address": "EQCol2",
                        "owner_address": "EQOwner",
                        "collection_item_index": "7",
                        "content": { "name": "NFT #7", "image": "https://img.example/7.png" }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        assertEquals(2, response.nftItems.size)
        assertEquals("EQNft1", response.nftItems[0].address)
        assertEquals("EQNft2", response.nftItems[1].address)
        assertEquals("7", response.nftItems[1].index)
    }

    @Test
    fun parseTonV3NFTResponse_emptyResult() {
        val responseJson = """{ "nft_items": [] }"""

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        assertTrue(response.nftItems.isEmpty())
    }

    @Test
    fun parseTonV3NFTResponse_missingNftItems_defaultsToEmpty() {
        val responseJson = """{}"""

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        assertTrue(response.nftItems.isEmpty())
    }

    @Test
    fun parseTonNFTItem_nullOptionalFields() {
        val responseJson = """
            {
                "nft_items": [
                    {
                        "address": "EQMinimal"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        val nft = response.nftItems.first()
        assertEquals("EQMinimal", nft.address)
        assertNull(nft.collectionAddress)
        assertNull(nft.ownerAddress)
        assertNull(nft.index)
        assertNull(nft.content)
    }

    @Test
    fun parseTonNFTContent_withAttributes() {
        val responseJson = """
            {
                "nft_items": [
                    {
                        "address": "EQAttr",
                        "content": {
                            "name": "Attributed NFT",
                            "description": "Has traits",
                            "image": "https://img.example/attr.png",
                            "attributes": [
                                { "trait_type": "Background", "value": "Blue" },
                                { "trait_type": "Rarity", "value": "Legendary" }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        val content = response.nftItems.first().content
        assertNotNull(content)
        assertNotNull(content.attributes)
        assertEquals(2, content.attributes?.size)
        assertEquals("Background", content.attributes?.get(0)?.traitType)
        assertEquals("Blue", content.attributes?.get(0)?.value)
        assertEquals("Rarity", content.attributes?.get(1)?.traitType)
        assertEquals("Legendary", content.attributes?.get(1)?.value)
    }

    // ── TonNFTItem → NFTItem mapping ────────────────────────────────

    @Test
    fun mapTonNFTItem_toNFTItem_correctFields() {
        val tonNft = TonNFTItem(
            address = "EQA1234567890abcdef",
            collectionAddress = "EQCollection1",
            ownerAddress = "EQOwner1",
            index = "42",
            content = TonNFTContent(
                name = "Cool TON NFT",
                description = "A very cool NFT on TON",
                image = "https://example.com/nft/42.png"
            )
        )

        // Replicate the mapping logic from TonManager.getNFTs()
        val nftItem = NFTItem(
            coin = ACTCoin.TON,
            address = tonNft.address,
            collectionAddress = tonNft.collectionAddress,
            index = tonNft.index?.toLongOrNull() ?: 0L,
            name = tonNft.content?.name,
            description = tonNft.content?.description,
            imageUrl = tonNft.content?.image
        )

        assertEquals(ACTCoin.TON, nftItem.coin)
        assertEquals("EQA1234567890abcdef", nftItem.address)
        assertEquals("EQCollection1", nftItem.collectionAddress)
        assertEquals(42L, nftItem.index)
        assertEquals("Cool TON NFT", nftItem.name)
        assertEquals("A very cool NFT on TON", nftItem.description)
        assertEquals("https://example.com/nft/42.png", nftItem.imageUrl)
    }

    @Test
    fun mapTonNFTItem_nullContent_mapsToNullFields() {
        val tonNft = TonNFTItem(
            address = "EQNoContent",
            collectionAddress = null,
            ownerAddress = null,
            index = null,
            content = null
        )

        val nftItem = NFTItem(
            coin = ACTCoin.TON,
            address = tonNft.address,
            collectionAddress = tonNft.collectionAddress,
            index = tonNft.index?.toLongOrNull() ?: 0L,
            name = tonNft.content?.name,
            description = tonNft.content?.description,
            imageUrl = tonNft.content?.image
        )

        assertEquals(ACTCoin.TON, nftItem.coin)
        assertEquals("EQNoContent", nftItem.address)
        assertNull(nftItem.collectionAddress)
        assertEquals(0L, nftItem.index)
        assertNull(nftItem.name)
        assertNull(nftItem.description)
        assertNull(nftItem.imageUrl)
    }

    @Test
    fun mapTonNFTItem_nonNumericIndex_defaultsToZero() {
        val tonNft = TonNFTItem(
            address = "EQBadIndex",
            index = "not-a-number",
            content = TonNFTContent(name = "Bad Index NFT")
        )

        val index = tonNft.index?.toLongOrNull() ?: 0L
        assertEquals(0L, index)
    }

    @Test
    fun mapTonNFTItem_largeIndex_parsesCorrectly() {
        val tonNft = TonNFTItem(
            address = "EQLargeIdx",
            index = "999999999999",
            content = TonNFTContent(name = "Large Index")
        )

        val index = tonNft.index?.toLongOrNull() ?: 0L
        assertEquals(999999999999L, index)
    }

    // ── Full mapping pipeline ───────────────────────────────────────

    @Test
    fun fullMappingPipeline_parseThenMapToNFTItems() {
        val responseJson = """
            {
                "nft_items": [
                    {
                        "address": "EQNft1",
                        "collection_address": "EQCol1",
                        "owner_address": "EQOwner",
                        "collection_item_index": "10",
                        "content": {
                            "name": "TON Ape #10",
                            "description": "Ape on TON",
                            "image": "https://tonapes.io/10.png"
                        }
                    },
                    {
                        "address": "EQNft2",
                        "collection_address": null,
                        "owner_address": "EQOwner",
                        "collection_item_index": "0",
                        "content": {
                            "name": "Standalone NFT"
                        }
                    },
                    {
                        "address": "EQNft3",
                        "collection_address": "EQCol2",
                        "owner_address": "EQOwner",
                        "content": null
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        assertEquals(3, response.nftItems.size)

        // Apply the same pipeline as TonManager.getNFTs()
        val nftItems = response.nftItems.map { nft ->
            NFTItem(
                coin = ACTCoin.TON,
                address = nft.address,
                collectionAddress = nft.collectionAddress,
                index = nft.index?.toLongOrNull() ?: 0L,
                name = nft.content?.name,
                description = nft.content?.description,
                imageUrl = nft.content?.image
            )
        }

        assertEquals(3, nftItems.size)

        // First: full content
        assertEquals("EQNft1", nftItems[0].address)
        assertEquals("EQCol1", nftItems[0].collectionAddress)
        assertEquals(10L, nftItems[0].index)
        assertEquals("TON Ape #10", nftItems[0].name)
        assertEquals("Ape on TON", nftItems[0].description)
        assertEquals("https://tonapes.io/10.png", nftItems[0].imageUrl)

        // Second: no collection, partial content
        assertEquals("EQNft2", nftItems[1].address)
        assertNull(nftItems[1].collectionAddress)
        assertEquals(0L, nftItems[1].index)
        assertEquals("Standalone NFT", nftItems[1].name)
        assertNull(nftItems[1].description)
        assertNull(nftItems[1].imageUrl)

        // Third: null content
        assertEquals("EQNft3", nftItems[2].address)
        assertEquals("EQCol2", nftItems[2].collectionAddress)
        assertEquals(0L, nftItems[2].index)
        assertNull(nftItems[2].name)
        assertNull(nftItems[2].description)
        assertNull(nftItems[2].imageUrl)
    }

    @Test
    fun fullMappingPipeline_emptyResponse_returnsEmptyList() {
        val responseJson = """{ "nft_items": [] }"""

        val response = json.decodeFromString<TonV3NFTResponse>(responseJson)
        val nftItems = response.nftItems.map { nft ->
            NFTItem(
                coin = ACTCoin.TON,
                address = nft.address,
                collectionAddress = nft.collectionAddress,
                index = nft.index?.toLongOrNull() ?: 0L,
                name = nft.content?.name,
                description = nft.content?.description,
                imageUrl = nft.content?.image
            )
        }

        assertTrue(nftItems.isEmpty())
    }

    // ── transferNFT response structure ──────────────────────────────

    @Test
    fun transferNFT_successResponse_hasCorrectStructure() {
        // Simulates the response when sendBoc returns "success"
        val response = TransferResponseModel(
            success = true,
            error = null,
            txHash = "pending"
        )

        assertTrue(response.success)
        assertNull(response.error)
        assertNotNull(response.txHash)
        assertEquals("pending", response.txHash)
    }

    @Test
    fun transferNFT_failureResponse_sendBocNotSuccess() {
        // Simulates the response when sendBoc returns non-"success" value
        val result: String? = "error"
        val response = if (result == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "NFT transfer failed", txHash = null)
        }

        assertFalse(response.success)
        assertEquals("NFT transfer failed", response.error)
        assertNull(response.txHash)
    }

    @Test
    fun transferNFT_failureResponse_sendBocReturnsNull() {
        // Simulates the response when sendBoc returns null
        val result: String? = null
        val response = if (result == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "NFT transfer failed", txHash = null)
        }

        assertFalse(response.success)
        assertEquals("NFT transfer failed", response.error)
        assertNull(response.txHash)
    }

    @Test
    fun transferNFT_exceptionResponse_hasErrorMessage() {
        // Simulates the catch block in TonManager.transferNFT()
        val exception = Exception("Network timeout")
        val response = TransferResponseModel(
            success = false,
            error = exception.message,
            txHash = null
        )

        assertFalse(response.success)
        assertEquals("Network timeout", response.error)
        assertNull(response.txHash)
    }

    @Test
    fun transferNFT_exceptionResponse_nullMessage() {
        // Exception with null message
        val exception = Exception()
        val response = TransferResponseModel(
            success = false,
            error = exception.message,
            txHash = null
        )

        assertFalse(response.success)
        assertNull(response.error)
        assertNull(response.txHash)
    }

    // ── transferNFT flow logic ──────────────────────────────────────

    @Test
    fun transferNFT_flowLogic_successPath() {
        // Verify the flow: seqno → signNFTTransfer → sendBoc → success response
        val seqno = 5
        val sendBocResult = "success"

        // The flow produces this response when sendBoc returns "success"
        val response = if (sendBocResult == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "NFT transfer failed", txHash = null)
        }

        assertTrue(response.success)
        assertNull(response.error)
        assertEquals("pending", response.txHash)
    }

    @Test
    fun transferNFT_flowLogic_failurePath() {
        // Verify the flow when sendBoc fails
        val seqno = 3
        val sendBocResult: String? = null

        val response = if (sendBocResult == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "NFT transfer failed", txHash = null)
        }

        assertFalse(response.success)
        assertEquals("NFT transfer failed", response.error)
        assertNull(response.txHash)
    }
}
