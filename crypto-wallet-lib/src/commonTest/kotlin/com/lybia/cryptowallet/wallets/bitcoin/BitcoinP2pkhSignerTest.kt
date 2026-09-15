package com.lybia.cryptowallet.wallets.bitcoin

import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.Transaction
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitcoinP2pkhSignerTest {

    private val key1 = Crypto.sha256("fgwallet-p2pkh-key-1".encodeToByteArray())
    private val key2 = Crypto.sha256("fgwallet-p2pkh-key-2".encodeToByteArray())

    private fun input(txidByte: String, vout: Long, amount: Long, key: ByteArray) =
        BitcoinP2pkhSigner.Input(
            txid = txidByte.repeat(32),
            vout = vout,
            amountSat = amount,
            scriptPubKeyHex = BitcoinP2pkhSigner.p2pkhScriptHex(key),
            privateKey = key
        )

    private fun signSample(): BitcoinP2pkhSigner.SignedTransaction =
        BitcoinP2pkhSigner.sign(
            inputs = listOf(input("aa", 0, 150_000, key1), input("bb", 1, 106_750, key2)),
            outputs = listOf(
                BitcoinP2pkhSigner.Output("bc1qq6secjllu78l7x7fml9sfzetten0lmusm6es5l", 200_000),
                BitcoinP2pkhSigner.Output(BitcoinP2pkhSigner.p2pkhAddress(key1, testnet = false), 47_526),
                BitcoinP2pkhSigner.Output("1KGSCR12dgpyNZeRxTXCnAF1Zoa5uh7EJ5", 8_412)
            ),
            testnet = false
        )

    @Test
    fun signsInputsOwnedByDifferentKeysInCoreBitcoinFormat() {
        val signed = signSample()
        val tx = Transaction.read(signed.rawTxHex)

        assertEquals(1L, tx.version)
        assertEquals(0L, tx.lockTime)
        assertEquals(2, tx.txIn.size)
        assertTrue(tx.txIn.all { it.sequence == 0xffffffffL && it.witness.isNull() })
        assertEquals(listOf(200_000L, 47_526L, 8_412L), tx.txOut.map { it.amount.sat })
        assertEquals(signed.txid, tx.txid.toString())
        assertEquals(signed.rawTxHex.length / 2, signed.size)
        // scriptSig = <sig + SIGHASH_ALL> <33-byte compressed pubkey>
        tx.txIn.forEach { txIn ->
            val bytes = txIn.signatureScript.toByteArray()
            val sigLen = bytes[0].toInt()
            assertEquals(0x01, bytes[sigLen].toInt())
            assertEquals(33, bytes[sigLen + 1].toInt())
        }
    }

    @Test
    fun signingIsDeterministic() {
        assertEquals(signSample().rawTxHex, signSample().rawTxHex)
    }

    @Test
    fun rejectsKeyThatDoesNotOwnTheInputScript() {
        val wrong = BitcoinP2pkhSigner.Input(
            txid = "aa".repeat(32), vout = 0, amountSat = 10_000,
            scriptPubKeyHex = BitcoinP2pkhSigner.p2pkhScriptHex(key2), privateKey = key1
        )
        assertFailsWith<IllegalArgumentException> {
            BitcoinP2pkhSigner.sign(listOf(wrong), listOf(BitcoinP2pkhSigner.Output("1KGSCR12dgpyNZeRxTXCnAF1Zoa5uh7EJ5", 5_000)), false)
        }
    }

    @Test
    fun rejectsAddressOfOtherNetwork() {
        assertFailsWith<IllegalArgumentException> {
            BitcoinP2pkhSigner.sign(
                listOf(input("aa", 0, 10_000, key1)),
                listOf(BitcoinP2pkhSigner.Output("1KGSCR12dgpyNZeRxTXCnAF1Zoa5uh7EJ5", 5_000)),
                testnet = true
            )
        }
    }

    @Test
    fun validatesAddressesPerNetwork() {
        assertTrue(BitcoinP2pkhSigner.isValidAddress("1KGSCR12dgpyNZeRxTXCnAF1Zoa5uh7EJ5", testnet = false))
        assertTrue(BitcoinP2pkhSigner.isValidAddress("bc1qq6secjllu78l7x7fml9sfzetten0lmusm6es5l", testnet = false))
        assertFalse(BitcoinP2pkhSigner.isValidAddress("1KGSCR12dgpyNZeRxTXCnAF1Zoa5uh7EJ5", testnet = true))
        assertTrue(BitcoinP2pkhSigner.isValidAddress(BitcoinP2pkhSigner.p2pkhAddress(key1, testnet = true), testnet = true))
        assertFalse(BitcoinP2pkhSigner.isValidAddress("not-an-address", testnet = false))
    }

    @Test
    fun recognisesP2pkhScripts() {
        assertTrue(BitcoinP2pkhSigner.isP2pkhScript(BitcoinP2pkhSigner.p2pkhScriptHex(key1)))
        assertFalse(BitcoinP2pkhSigner.isP2pkhScript("0014" + "00".repeat(20)))
        assertFalse(BitcoinP2pkhSigner.isP2pkhScript("zz"))
        assertEquals("76a914", BitcoinP2pkhSigner.p2pkhScriptHex(key1).take(6))
        assertEquals(50, Hex.decode(BitcoinP2pkhSigner.p2pkhScriptHex(key1)).size * 2)
    }
}
