package com.lybia.cryptowallet.wallets.bitcoin

import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.bitcoin.UtxoInfo
import fr.acinq.bitcoin.*
import fr.acinq.secp256k1.Hex
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [BitcoinTransactionBuilder].
 *
 * Tests cover:
 * - UTXO selection (sufficient/insufficient funds, dust handling)
 * - Transaction building and signing for all 3 address types
 * - Address-to-script conversion
 * - Fee estimation
 */
class BitcoinTransactionBuilderTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey

    @BeforeTest
    fun setup() {
        Config.shared.setNetwork(Network.MAINNET)
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        // BIP-84 m/84'/0'/0'/0/0
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/84'/0'/0'/0/0"))
        privateKey = derived.privateKey
        publicKey = derived.publicKey
    }

    private fun makeUtxo(value: Long, confirmed: Boolean = true) = UtxoInfo(
        txid = "a" .repeat(64),
        vout = 0,
        value = value,
        status = UtxoInfo.UtxoStatus(confirmed = confirmed)
    )

    // ---- UTXO Selection Tests ----

    @Test
    fun selectUtxos_sufficientFunds_returnsSelection() {
        val utxos = listOf(makeUtxo(100_000), makeUtxo(50_000))
        val result = BitcoinTransactionBuilder.selectUtxos(
            utxos, 80_000, 10, BitcoinAddressType.NATIVE_SEGWIT
        )
        assertNotNull(result)
        val (selected, fee) = result
        assertTrue(selected.isNotEmpty())
        assertTrue(fee > 0)
        assertTrue(selected.sumOf { it.value } >= 80_000 + fee)
    }

    @Test
    fun selectUtxos_insufficientFunds_returnsNull() {
        val utxos = listOf(makeUtxo(1_000))
        val result = BitcoinTransactionBuilder.selectUtxos(
            utxos, 100_000, 10, BitcoinAddressType.NATIVE_SEGWIT
        )
        assertNull(result)
    }

    @Test
    fun selectUtxos_ignoresUnconfirmed() {
        val utxos = listOf(makeUtxo(100_000, confirmed = false))
        val result = BitcoinTransactionBuilder.selectUtxos(
            utxos, 50_000, 10, BitcoinAddressType.NATIVE_SEGWIT
        )
        assertNull(result, "Should not select unconfirmed UTXOs")
    }

    @Test
    fun selectUtxos_largestFirst() {
        val utxos = listOf(makeUtxo(10_000), makeUtxo(200_000), makeUtxo(50_000))
        val result = BitcoinTransactionBuilder.selectUtxos(
            utxos, 100_000, 1, BitcoinAddressType.NATIVE_SEGWIT
        )
        assertNotNull(result)
        // Should pick the 200k UTXO first (largest), which is enough alone
        assertEquals(1, result.first.size)
        assertEquals(200_000, result.first[0].value)
    }

    // ---- Vsize Estimation Tests ----

    @Test
    fun estimateVsize_nativeSegwit() {
        val vsize = BitcoinTransactionBuilder.estimateVsize(1, 2, BitcoinAddressType.NATIVE_SEGWIT)
        // 11 + 68 + 2*31 = 141
        assertEquals(141, vsize)
    }

    @Test
    fun estimateVsize_legacy() {
        val vsize = BitcoinTransactionBuilder.estimateVsize(1, 2, BitcoinAddressType.LEGACY)
        // 11 + 148 + 2*31 = 221
        assertEquals(221, vsize)
    }

    @Test
    fun estimateVsize_nestedSegwit() {
        val vsize = BitcoinTransactionBuilder.estimateVsize(1, 2, BitcoinAddressType.NESTED_SEGWIT)
        // 11 + 91 + 2*31 = 164
        assertEquals(164, vsize)
    }

    // ---- Address to Script Tests ----

    @Test
    fun addressToScript_p2wpkh_mainnet() {
        val address = Bitcoin.computeBIP84Address(publicKey, Chain.Mainnet.chainHash)
        val script = BitcoinTransactionBuilder.addressToScript(address, Chain.Mainnet)
        assertTrue(script.size() > 0)
        // P2WPKH script: OP_0 <20-byte-hash>
        val parsed = Script.parse(script)
        assertTrue(Script.isPay2wpkh(parsed))
    }

    @Test
    fun addressToScript_p2pkh_mainnet() {
        val address = Bitcoin.computeP2PkhAddress(publicKey, Chain.Mainnet.chainHash)
        val script = BitcoinTransactionBuilder.addressToScript(address, Chain.Mainnet)
        assertTrue(script.size() > 0)
        val parsed = Script.parse(script)
        assertTrue(Script.isPay2pkh(parsed))
    }

    @Test
    fun addressToScript_p2sh_mainnet() {
        val address = Bitcoin.computeP2ShOfP2WpkhAddress(publicKey, Chain.Mainnet.chainHash)
        val script = BitcoinTransactionBuilder.addressToScript(address, Chain.Mainnet)
        assertTrue(script.size() > 0)
        val parsed = Script.parse(script)
        assertTrue(Script.isPay2sh(parsed))
    }

    // ---- Build & Sign Tests ----

    @Test
    fun buildAndSign_p2wpkh_producesValidTx() {
        val utxos = listOf(
            UtxoInfo(
                txid = "a".repeat(64),
                vout = 0,
                value = 100_000,
                status = UtxoInfo.UtxoStatus(confirmed = true)
            )
        )
        val toAddress = Bitcoin.computeBIP84Address(publicKey, Chain.Mainnet.chainHash)
        val changeAddress = toAddress

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = changeAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet
        )

        assertTrue(result.rawTxHex.isNotEmpty())
        assertTrue(result.txid.isNotEmpty())
        assertEquals(1_000, result.fee)
        assertTrue(result.vsize > 0)

        // Verify the raw tx can be deserialized
        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        assertEquals(1, tx.txIn.size)
        assertEquals(2, tx.txOut.size) // recipient + change
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        assertEquals(Satoshi(49_000), tx.txOut[1].amount) // 100k - 50k - 1k fee
        // P2WPKH: witness should have 2 items (sig, pubkey)
        assertTrue(tx.txIn[0].witness.stack.size == 2)
        // scriptSig should be empty for native segwit
        assertTrue(tx.txIn[0].signatureScript.isEmpty())
    }

    @Test
    fun buildAndSign_p2shP2wpkh_producesValidTx() {
        // Derive key for BIP-49
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/49'/0'/0'/0/0"))
        val pk = derived.privateKey
        val pub = derived.publicKey

        val utxos = listOf(
            UtxoInfo(
                txid = "b".repeat(64),
                vout = 1,
                value = 200_000,
                status = UtxoInfo.UtxoStatus(confirmed = true)
            )
        )
        val toAddress = Bitcoin.computeP2ShOfP2WpkhAddress(pub, Chain.Mainnet.chainHash)

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 100_000,
            feeSat = 2_000,
            changeAddress = toAddress,
            privateKey = pk,
            publicKey = pub,
            addressType = BitcoinAddressType.NESTED_SEGWIT,
            chain = Chain.Mainnet
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // P2SH-P2WPKH: scriptSig should NOT be empty (contains redeem script push)
        assertTrue(tx.txIn[0].signatureScript.size() > 0)
        // witness should have 2 items
        assertTrue(tx.txIn[0].witness.stack.size == 2)
    }

    @Test
    fun buildAndSign_p2pkh_producesValidTx() {
        // Derive key for BIP-44
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/44'/0'/0'/0/0"))
        val pk = derived.privateKey
        val pub = derived.publicKey

        val utxos = listOf(
            UtxoInfo(
                txid = "c".repeat(64),
                vout = 0,
                value = 150_000,
                status = UtxoInfo.UtxoStatus(confirmed = true)
            )
        )
        val toAddress = Bitcoin.computeP2PkhAddress(pub, Chain.Mainnet.chainHash)

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 80_000,
            feeSat = 3_000,
            changeAddress = toAddress,
            privateKey = pk,
            publicKey = pub,
            addressType = BitcoinAddressType.LEGACY,
            chain = Chain.Mainnet
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // P2PKH: scriptSig should contain sig + pubkey
        assertTrue(tx.txIn[0].signatureScript.size() > 0)
        // No witness for legacy
        assertTrue(tx.txIn[0].witness.isNull())
        assertEquals(2, tx.txOut.size)
        assertEquals(Satoshi(80_000), tx.txOut[0].amount)
        assertEquals(Satoshi(67_000), tx.txOut[1].amount) // 150k - 80k - 3k
    }

    @Test
    fun buildAndSign_noChange_whenDustAmount() {
        val utxos = listOf(makeUtxo(50_500))
        val toAddress = Bitcoin.computeBIP84Address(publicKey, Chain.Mainnet.chainHash)

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 400,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // Change = 50500 - 50000 - 400 = 100 sat (below dust)
        // So change should be absorbed → only 1 output
        assertEquals(1, tx.txOut.size)
    }
}
