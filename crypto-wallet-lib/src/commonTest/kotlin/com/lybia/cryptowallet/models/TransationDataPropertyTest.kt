package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.bitcoin.BTCApiModel
import com.lybia.cryptowallet.models.cardano.CardanoAmount
import com.lybia.cryptowallet.models.cardano.CardanoTransactionInfo
import com.lybia.cryptowallet.models.cardano.CardanoTxInOut
import com.lybia.cryptowallet.models.ton.TonMessage
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.models.ton.TonTransactionId
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Property 11: TransationData conversion preservation**
 *
 * For any raw transaction data, conversion functions must create TransationData
 * with all fields having correct values.
 *
 * **Validates: Requirements 6.2, 6.3, 6.4**
 */
class TransationDataPropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    private fun arbBtcTx(): Arb<BTCApiModel.Tx> = arbitrary {
        val hash = List(32) { "0123456789abcdef".random() }.joinToString("")
        val total = Arb.long(0L..1_000_000_000L).bind()
        val fees = Arb.long(0L..1_000_000L).bind()
        val addr1 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        val addr2 = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"
        BTCApiModel.Tx(
            blockHash = hash,
            blockHeight = 100,
            blockIndex = 0,
            hash = hash,
            addresses = listOf(addr1, addr2),
            total = total,
            fees = fees,
            size = 250,
            vsize = 200,
            preference = "high",
            confirmed = "2024-01-15T10:30:00Z",
            received = "2024-01-15T10:29:00Z",
            ver = 2,
            doubleSpend = false,
            vinSz = 1,
            voutSz = 2,
            confirmations = 6,
            confidence = 1,
            inputs = listOf(
                BTCApiModel.Input(
                    prevHash = hash, outputIndex = 0, outputValue = total + fees,
                    sequence = 0, addresses = listOf(addr1), scriptType = "pay-to-pubkey-hash", age = 100
                )
            ),
            outputs = listOf(
                BTCApiModel.Output(value = total, script = "", addresses = listOf(addr2), scriptType = "pay-to-pubkey-hash")
            )
        )
    }

    private fun arbTonTx(): Arb<Pair<TonTransaction, Boolean>> = arbitrary {
        val isSend = Arb.boolean().bind()
        val value = Arb.long(1L..10_000_000_000L).bind()
        val fee = Arb.long(1_000_000L..100_000_000L).bind()
        val utime = Arb.long(1_700_000_000L..1_800_000_000L).bind()
        val myAddr = "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2"
        val otherAddr = "EQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"

        val msg = TonMessage(
            source = if (isSend) "" else otherAddr,
            destination = if (isSend) otherAddr else myAddr,
            value = value.toString(),
            message = null
        )

        val tx = TonTransaction(
            utime = utime,
            transactionId = TonTransactionId(lt = "123", hash = "txhash_${utime}"),
            fee = fee.toString(),
            in_msg = if (isSend) TonMessage(source = "", destination = myAddr, value = "0") else msg,
            out_msgs = if (isSend) listOf(msg) else emptyList()
        )
        Pair(tx, isSend)
    }

    private fun arbCardanoTx(): Arb<CardanoTransactionInfo> = arbitrary {
        val lovelace = Arb.long(1_000_000L..100_000_000_000L).bind()
        val fees = Arb.long(170_000L..500_000L).bind()
        val blockTime = Arb.long(1_700_000_000L..1_800_000_000L).bind()
        val addr1 = "addr1qxck..."
        val addr2 = "addr1qytest..."
        CardanoTransactionInfo(
            txHash = "txhash_${blockTime}",
            blockHeight = 1000,
            blockTime = blockTime,
            fees = fees.toString(),
            inputs = listOf(CardanoTxInOut(addr1, listOf(CardanoAmount("lovelace", (lovelace + fees).toString())))),
            outputs = listOf(CardanoTxInOut(addr2, listOf(CardanoAmount("lovelace", lovelace.toString()))))
        )
    }

    // ── Property Tests ──────────────────────────────────────────────

    @Test
    fun btcConversionPreservesFields() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbBtcTx()) { tx ->
            val addresses = tx.inputs.flatMap { it.addresses }
            val result = tx.toTransactionData(addresses)

            assertEquals(tx.hash, result.iD, "Transaction ID must match hash")
            assertEquals(ACTCoin.Bitcoin, result.coin, "Coin must be Bitcoin")
            assertTrue(result.amount >= 0f, "Amount must be non-negative")
            assertTrue(result.fee >= 0f, "Fee must be non-negative")
            assertTrue(result.fromAddress.isNotEmpty(), "fromAddress must not be empty")
            assertTrue(result.toAddress.isNotEmpty(), "toAddress must not be empty")
            assertTrue(result.dateMillis > 0L, "dateMillis must be positive")
            // isSend should be true since we used input addresses
            assertTrue(result.isSend, "isSend should be true when address is in inputs")
        }
    }

    @Test
    fun tonConversionPreservesFields() = runTest {
        val myAddr = "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2"
        checkAll(PropTestConfig(iterations = 100), arbTonTx()) { (tx, expectedIsSend) ->
            val result = tx.toTransactionData(myAddr)

            assertEquals(tx.transactionId.hash, result.iD, "Transaction ID must match")
            assertEquals(ACTCoin.TON, result.coin, "Coin must be TON")
            assertEquals(tx.utime * 1000L, result.dateMillis, "dateMillis must be utime * 1000")
            assertTrue(result.fee >= 0f, "Fee must be non-negative")
            assertTrue(result.amount >= 0f, "Amount must be non-negative")
            assertEquals(expectedIsSend, result.isSend, "isSend must match expected")

            if (expectedIsSend) {
                assertEquals(myAddr, result.fromAddress, "Sender must be myAddress for outgoing")
            } else {
                assertEquals(myAddr, result.toAddress, "Receiver must be myAddress for incoming")
            }
        }
    }

    @Test
    fun cardanoConversionPreservesFields() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbCardanoTx()) { tx ->
            val addresses = tx.inputs.map { it.address }
            val result = tx.toTransactionData(addresses)

            assertEquals(tx.txHash, result.iD, "Transaction ID must match txHash")
            assertEquals(ACTCoin.Cardano, result.coin, "Coin must be Cardano")
            assertEquals(tx.blockTime * 1000L, result.dateMillis, "dateMillis must be blockTime * 1000")
            assertTrue(result.amount >= 0f, "Amount must be non-negative")
            assertTrue(result.fee >= 0f, "Fee must be non-negative")
            assertTrue(result.fromAddress.isNotEmpty(), "fromAddress must not be empty")
            assertTrue(result.toAddress.isNotEmpty(), "toAddress must not be empty")
        }
    }

    @Test
    fun transationDataDefaultConstruction() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            Arb.float(0f..1000f),
            Arb.float(0f..10f),
            Arb.string(10..20),
            Arb.long(0L..Long.MAX_VALUE / 2)
        ) { amount, fee, id, dateMillis ->
            val td = TransationData(
                amount = amount,
                fee = fee,
                iD = id,
                dateMillis = dateMillis,
                coin = ACTCoin.Bitcoin
            )
            assertEquals(amount, td.amount)
            assertEquals(fee, td.fee)
            assertEquals(id, td.iD)
            assertEquals(dateMillis, td.dateMillis)
            assertEquals(ACTCoin.Bitcoin, td.coin)
        }
    }
}
