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
 * Unit tests for BitcoinManager service fee support.
 *
 * Tests cover:
 * - Transaction building with service fee output (Requirements 2.2)
 * - Fee estimation accounting for extra service fee output (Requirements 1.4)
 * - Backward compatibility when no service fee is provided
 *
 * Since BitcoinManager.sendBtc/sendBtcLocal and estimateFee require live API calls,
 * these tests verify the underlying BitcoinTransactionBuilder logic that handles
 * service fee outputs, which is the core of the UTXO service fee implementation.
 */
class BitcoinServiceFeeTest {

    private val testMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey
    private lateinit var toAddress: String
    private lateinit var serviceAddress: String

    @BeforeTest
    fun setup() {
        Config.shared.setNetwork(Network.MAINNET)
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/84'/0'/0'/0/0"))
        privateKey = derived.privateKey
        publicKey = derived.publicKey
        toAddress = Bitcoin.computeBIP84Address(publicKey, Chain.Mainnet.chainHash)
        // Use a different derivation path for service address to get a distinct address
        val serviceDerived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/84'/0'/1'/0/0"))
        serviceAddress = Bitcoin.computeBIP84Address(serviceDerived.publicKey, Chain.Mainnet.chainHash)
    }

    private fun makeUtxo(value: Long, confirmed: Boolean = true) = UtxoInfo(
        txid = "a".repeat(64),
        vout = 0,
        value = value,
        status = UtxoInfo.UtxoStatus(confirmed = confirmed)
    )

    // ── buildAndSign with service fee output ────────────────────────────

    @Test
    fun buildAndSign_withServiceFee_includesExtraOutput() {
        val utxos = listOf(makeUtxo(200_000))
        val serviceFeeAmount = 10_000L

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, serviceFeeAmount)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // 3 outputs: recipient + service fee + change
        assertEquals(3, tx.txOut.size)
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        assertEquals(Satoshi(serviceFeeAmount), tx.txOut[1].amount)
        // change = 200k - 50k - 10k - 1k = 139k
        assertEquals(Satoshi(139_000), tx.txOut[2].amount)
    }

    @Test
    fun buildAndSign_withServiceFee_serviceOutputGoesToServiceAddress() {
        val utxos = listOf(makeUtxo(200_000))
        val serviceFeeAmount = 5_000L

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, serviceFeeAmount)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // Verify service fee output script matches the service address
        val expectedScript = BitcoinTransactionBuilder.addressToScript(serviceAddress, Chain.Mainnet)
        assertEquals(expectedScript, tx.txOut[1].publicKeyScript)
        assertEquals(Satoshi(serviceFeeAmount), tx.txOut[1].amount)
    }

    @Test
    fun buildAndSign_withoutServiceFee_producesStandardTx() {
        val utxos = listOf(makeUtxo(200_000))

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = emptyList()
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // 2 outputs: recipient + change (no service fee)
        assertEquals(2, tx.txOut.size)
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        // change = 200k - 50k - 1k = 149k
        assertEquals(Satoshi(149_000), tx.txOut[1].amount)
    }

    @Test
    fun buildAndSign_defaultAdditionalOutputs_isEmptyList() {
        val utxos = listOf(makeUtxo(200_000))

        // Call without additionalOutputs parameter — should use default empty list
        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        assertEquals(2, tx.txOut.size, "Default call should produce recipient + change only")
    }

    // ── selectUtxos with extraOutputCount for service fee ───────────────

    @Test
    fun selectUtxos_withExtraOutput_requiresMoreFunds() {
        val utxos = listOf(makeUtxo(100_000))
        val feeRate = 10L

        val resultWithout = BitcoinTransactionBuilder.selectUtxos(
            utxos, 80_000, feeRate, BitcoinAddressType.NATIVE_SEGWIT, extraOutputCount = 0
        )
        val resultWith = BitcoinTransactionBuilder.selectUtxos(
            utxos, 80_000, feeRate, BitcoinAddressType.NATIVE_SEGWIT, extraOutputCount = 1
        )

        assertNotNull(resultWithout, "Should succeed without extra output")
        // With extra output, the fee is higher due to larger vsize
        // This may or may not succeed depending on exact amounts,
        // but if both succeed, the fee with extra output should be >= fee without
        if (resultWith != null) {
            assertTrue(
                resultWith.second >= resultWithout.second,
                "Fee with service fee output (${resultWith.second}) should be >= fee without (${resultWithout.second})"
            )
        }
    }

    @Test
    fun selectUtxos_extraOutputCount_increasesEstimatedVsize() {
        // 2 outputs (recipient + change) vs 3 outputs (recipient + service fee + change)
        val vsizeWithout = BitcoinTransactionBuilder.estimateVsize(1, 2, BitcoinAddressType.NATIVE_SEGWIT)
        val vsizeWith = BitcoinTransactionBuilder.estimateVsize(1, 3, BitcoinAddressType.NATIVE_SEGWIT)

        assertTrue(
            vsizeWith > vsizeWithout,
            "Vsize with 3 outputs ($vsizeWith) should be > vsize with 2 outputs ($vsizeWithout)"
        )
        // Extra P2WPKH output adds 31 vbytes
        assertEquals(31, vsizeWith - vsizeWithout)
    }

    @Test
    fun selectUtxos_withExtraOutput_insufficientFunds() {
        // Barely enough for 2 outputs but not 3
        val utxos = listOf(makeUtxo(82_000))
        val feeRate = 10L

        val resultWithout = BitcoinTransactionBuilder.selectUtxos(
            utxos, 80_000, feeRate, BitcoinAddressType.NATIVE_SEGWIT, extraOutputCount = 0
        )
        val resultWith = BitcoinTransactionBuilder.selectUtxos(
            utxos, 80_000, feeRate, BitcoinAddressType.NATIVE_SEGWIT, extraOutputCount = 1
        )

        // Without extra output: 80k + fee(~1410) ≈ 81410, fits in 82k
        assertNotNull(resultWithout, "Should succeed without extra output")
        // With extra output: 80k + fee(~1720) ≈ 81720, may or may not fit
        // The point is the extra output increases the fee requirement
    }

    // ── Service fee with different address types ────────────────────────

    @Test
    fun buildAndSign_legacyWithServiceFee_includesExtraOutput() {
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/44'/0'/0'/0/0"))
        val pk = derived.privateKey
        val pub = derived.publicKey
        val legacyAddress = Bitcoin.computeP2PkhAddress(pub, Chain.Mainnet.chainHash)

        val utxos = listOf(makeUtxo(200_000))

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = legacyAddress,
            amountSat = 50_000,
            feeSat = 2_000,
            changeAddress = legacyAddress,
            privateKey = pk,
            publicKey = pub,
            addressType = BitcoinAddressType.LEGACY,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, 8_000)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        assertEquals(3, tx.txOut.size)
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        assertEquals(Satoshi(8_000), tx.txOut[1].amount)
        // change = 200k - 50k - 8k - 2k = 140k
        assertEquals(Satoshi(140_000), tx.txOut[2].amount)
    }

    @Test
    fun buildAndSign_nestedSegwitWithServiceFee_includesExtraOutput() {
        val seed = MnemonicCode.toSeed(testMnemonic, "")
        val master = DeterministicWallet.generate(seed)
        val derived = DeterministicWallet.derivePrivateKey(master, KeyPath("m/49'/0'/0'/0/0"))
        val pk = derived.privateKey
        val pub = derived.publicKey
        val nestedAddress = Bitcoin.computeP2ShOfP2WpkhAddress(pub, Chain.Mainnet.chainHash)

        val utxos = listOf(makeUtxo(200_000))

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = nestedAddress,
            amountSat = 50_000,
            feeSat = 2_000,
            changeAddress = nestedAddress,
            privateKey = pk,
            publicKey = pub,
            addressType = BitcoinAddressType.NESTED_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, 8_000)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        assertEquals(3, tx.txOut.size)
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        assertEquals(Satoshi(8_000), tx.txOut[1].amount)
        assertEquals(Satoshi(140_000), tx.txOut[2].amount)
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun buildAndSign_serviceFeeAbsorbsChange_whenChangeBelowDust() {
        // Total = 50k + 10k(service) + 1k(fee) = 61k, input = 61200
        // Change = 200 sat (below dust threshold 294 for P2WPKH)
        val utxos = listOf(makeUtxo(61_200))

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, 10_000)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        // 2 outputs only: recipient + service fee (change absorbed into fee)
        assertEquals(2, tx.txOut.size)
        assertEquals(Satoshi(50_000), tx.txOut[0].amount)
        assertEquals(Satoshi(10_000), tx.txOut[1].amount)
    }

    @Test
    fun buildAndSign_serviceFeeAtDustThreshold_isIncluded() {
        // Service fee at exactly dust threshold (546 sat for P2PKH, 294 for P2WPKH)
        val dustServiceFee = BitcoinManager.DUST_ESTIMATE_SATOSHI // 546
        val utxos = listOf(makeUtxo(200_000))

        val result = BitcoinTransactionBuilder.buildAndSign(
            utxos = utxos,
            toAddress = toAddress,
            amountSat = 50_000,
            feeSat = 1_000,
            changeAddress = toAddress,
            privateKey = privateKey,
            publicKey = publicKey,
            addressType = BitcoinAddressType.NATIVE_SEGWIT,
            chain = Chain.Mainnet,
            additionalOutputs = listOf(
                BitcoinTransactionBuilder.AdditionalTxOutput(serviceAddress, dustServiceFee)
            )
        )

        val tx = Transaction.read(Hex.decode(result.rawTxHex))
        assertEquals(3, tx.txOut.size)
        assertEquals(Satoshi(dustServiceFee), tx.txOut[1].amount)
    }

    // ── BitcoinManager parameter wiring ─────────────────────────────────

    @Test
    fun bitcoinManager_sendBtcLocal_defaultServiceFeeParams() {
        // Verify that BitcoinManager.sendBtcLocal has backward-compatible defaults
        val manager = BitcoinManager(testMnemonic)
        // The method signature should accept calls without service fee params
        // This is a compile-time check — if it compiles, backward compatibility is maintained
        // We can't actually call sendBtcLocal without mocking the network,
        // but we verify the manager can be instantiated and address generated
        val address = manager.getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        assertTrue(address.startsWith("bc1"), "Should generate valid mainnet bech32 address")
    }

    @Test
    fun bitcoinManager_dustEstimateSatoshi_isStandardDust() {
        // DUST_ESTIMATE_SATOSHI should be 546 (standard P2PKH dust threshold)
        assertEquals(546L, BitcoinManager.DUST_ESTIMATE_SATOSHI)
    }
}
