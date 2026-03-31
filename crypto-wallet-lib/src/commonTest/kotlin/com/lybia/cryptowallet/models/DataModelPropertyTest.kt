package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 1: Migrated data model field preservation
 *
 * For any MemoData, TokenInfo, or NFTItem instance, all fields must
 * retain their values after construction.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 */
class DataModelPropertyTest {

    private val arbCoin = Arb.enum<ACTCoin>()
    private val arbNullableString = Arb.string(0..50).orNull(0.3)
    private val arbString = Arb.string(1..50)
    private val arbNullableUInt = Arb.uInt().orNull(0.3)

    @Test
    fun memoDataFieldPreservation() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbNullableString, arbNullableUInt) { memo, desTag ->
            val data = MemoData(memo, desTag)
            assertEquals(memo, data.memo)
            assertEquals(desTag, data.destinationTag)
        }
    }

    @Test
    fun tokenInfoFieldPreservation() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbCoin,
            arbString,
            arbNullableString,
            arbNullableString,
            Arb.int(0..18),
            Arb.double(0.0..1_000_000.0),
            arbNullableString
        ) { coin, contractAddress, name, symbol, decimals, balance, imageUrl ->
            val info = TokenInfo(coin, contractAddress, name, symbol, decimals, balance, imageUrl)
            assertEquals(coin, info.coin)
            assertEquals(contractAddress, info.contractAddress)
            assertEquals(name, info.name)
            assertEquals(symbol, info.symbol)
            assertEquals(decimals, info.decimals)
            assertEquals(balance, info.balance)
            assertEquals(imageUrl, info.imageUrl)
        }
    }

    @Test
    fun nftItemFieldPreservation() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbCoin,
            arbString,
            arbNullableString,
            Arb.long(0L..1_000_000L),
            arbNullableString,
            arbNullableString,
            arbNullableString
        ) { coin, address, collectionAddress, index, name, description, imageUrl ->
            val nft = NFTItem(coin, address, collectionAddress, index, name, description, imageUrl)
            assertEquals(coin, nft.coin)
            assertEquals(address, nft.address)
            assertEquals(collectionAddress, nft.collectionAddress)
            assertEquals(index, nft.index)
            assertEquals(name, nft.name)
            assertEquals(description, nft.description)
            assertEquals(imageUrl, nft.imageUrl)
        }
    }
}
