package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.*
import com.lybia.cryptowallet.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Phase 1 models and enums.
 *
 * **Validates: Requirements 2.8, 12.5**
 */
class Phase1UnitTest {

    // ── MemoData ─────────────────────────────────────────────────────

    @Test
    fun memoDataConstruction() {
        val memo = MemoData("test memo", 42u)
        assertEquals("test memo", memo.memo)
        assertEquals(42u, memo.destinationTag)
    }

    @Test
    fun memoDataNullFields() {
        val memo = MemoData(null, null)
        assertNull(memo.memo)
        assertNull(memo.destinationTag)
    }

    // ── TokenInfo ────────────────────────────────────────────────────

    @Test
    fun tokenInfoFields() {
        val info = TokenInfo(
            coin = ACTCoin.Ethereum,
            contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            name = "Tether USD",
            symbol = "USDT",
            decimals = 6,
            balance = 100.5,
            imageUrl = "https://example.com/usdt.png"
        )
        assertEquals(ACTCoin.Ethereum, info.coin)
        assertEquals("0xdAC17F958D2ee523a2206206994597C13D831ec7", info.contractAddress)
        assertEquals("Tether USD", info.name)
        assertEquals("USDT", info.symbol)
        assertEquals(6, info.decimals)
        assertEquals(100.5, info.balance)
        assertEquals("https://example.com/usdt.png", info.imageUrl)
    }

    @Test
    fun tokenInfoDefaults() {
        val info = TokenInfo(ACTCoin.TON, "addr", null, null)
        assertEquals(9, info.decimals)
        assertEquals(0.0, info.balance)
        assertNull(info.imageUrl)
    }

    // ── NFTItem ──────────────────────────────────────────────────────

    @Test
    fun nftItemFields() {
        val nft = NFTItem(
            coin = ACTCoin.TON,
            address = "EQabc123",
            collectionAddress = "EQcol456",
            index = 7L,
            name = "Cool NFT",
            description = "A cool NFT",
            imageUrl = "https://example.com/nft.png",
            attributes = mapOf("rarity" to "legendary")
        )
        assertEquals(ACTCoin.TON, nft.coin)
        assertEquals("EQabc123", nft.address)
        assertEquals("EQcol456", nft.collectionAddress)
        assertEquals(7L, nft.index)
        assertEquals("Cool NFT", nft.name)
        assertEquals("A cool NFT", nft.description)
        assertEquals("https://example.com/nft.png", nft.imageUrl)
        assertEquals(mapOf("rarity" to "legendary"), nft.attributes)
    }

    // ── ACTCoin unitValue ────────────────────────────────────────────

    @Test
    fun actCoinBitcoinUnitValue() {
        assertEquals(100_000_000.0, ACTCoin.Bitcoin.unitValue())
    }

    @Test
    fun actCoinEthereumUnitValue() {
        assertEquals(1e18, ACTCoin.Ethereum.unitValue())
    }

    @Test
    fun actCoinCardanoUnitValue() {
        assertEquals(1_000_000.0, ACTCoin.Cardano.unitValue())
    }

    @Test
    fun actCoinRippleUnitValue() {
        assertEquals(1_000_000.0, ACTCoin.Ripple.unitValue())
    }

    @Test
    fun actCoinTonUnitValue() {
        assertEquals(1_000_000_000.0, ACTCoin.TON.unitValue())
    }

    @Test
    fun actCoinMidnightUnitValue() {
        assertEquals(1_000_000.0, ACTCoin.Midnight.unitValue())
    }

    // ── ACTNetwork ───────────────────────────────────────────────────

    @Test
    fun actNetworkBitcoinMainnetCoinType() {
        assertEquals(0, ACTNetwork(ACTCoin.Bitcoin, false).coinType())
    }

    @Test
    fun actNetworkBitcoinTestnetCoinType() {
        assertEquals(1, ACTNetwork(ACTCoin.Bitcoin, true).coinType())
    }

    @Test
    fun actNetworkEthereumCoinType() {
        assertEquals(60, ACTNetwork(ACTCoin.Ethereum, false).coinType())
    }

    @Test
    fun actNetworkCardanoCoinType() {
        assertEquals(1815, ACTNetwork(ACTCoin.Cardano, false).coinType())
    }

    // ── FeeEstimate / FeeEstimateParams ──────────────────────────────

    @Test
    fun feeEstimateParamsConstruction() {
        val params = FeeEstimateParams("0xFrom", "0xTo", 1.5, "0xdata")
        assertEquals("0xFrom", params.fromAddress)
        assertEquals("0xTo", params.toAddress)
        assertEquals(1.5, params.amount)
        assertEquals("0xdata", params.data)
    }

    @Test
    fun feeEstimateConstruction() {
        val est = FeeEstimate(0.001, 21000L, 20_000_000_000L, "gwei")
        assertEquals(0.001, est.fee)
        assertEquals(21000L, est.gasLimit)
        assertEquals(20_000_000_000L, est.gasPrice)
        assertEquals("gwei", est.unit)
    }

    @Test
    fun feeEstimateDefaults() {
        val est = FeeEstimate(0.5)
        assertNull(est.gasLimit)
        assertNull(est.gasPrice)
        assertEquals("native", est.unit)
    }

    // ── Algorithm & Change enums ─────────────────────────────────────

    @Test
    fun algorithmValues() {
        assertTrue(Algorithm.entries.map { it.name }.containsAll(listOf("Ed25519", "Secp256k1", "Sr25519")))
    }

    @Test
    fun changeValues() {
        assertEquals(0, Change.External.value)
        assertEquals(1, Change.Internal.value)
    }
}
