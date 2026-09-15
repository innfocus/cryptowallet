package com.lybia.cryptowallet.wallets.bitcoin

import fr.acinq.bitcoin.*
import fr.acinq.secp256k1.Hex

/**
 * Signs legacy P2PKH transactions that spend UTXOs held by many HD keys, for callers that do their
 * own UTXO selection, fee and dust handling (the iOS app's `Gbtc`, which previously used
 * CoreBitcoin). Unlike [BitcoinTransactionBuilder] it takes a private key per input and keeps the
 * transaction format CoreBitcoin produced: version 1, lock time 0, final sequence, SIGHASH_ALL,
 * compressed public keys, outputs in the given order.
 */
object BitcoinP2pkhSigner {

    /**
     * A UTXO to spend.
     *
     * @property txid transaction id in display (big-endian) hex, as shown by explorers.
     * @property scriptPubKeyHex the UTXO's locking script; must be the P2PKH script of [privateKey].
     * @property privateKey 32-byte secp256k1 private key owning the UTXO.
     */
    class Input(
        val txid: String,
        val vout: Long,
        val amountSat: Long,
        val scriptPubKeyHex: String,
        val privateKey: ByteArray
    )

    class Output(val address: String, val amountSat: Long)

    class SignedTransaction(val rawTxHex: String, val txid: String, val size: Int)

    private const val FINAL_SEQUENCE = 0xffffffffL

    private fun chain(testnet: Boolean): Chain = if (testnet) Chain.Testnet3 else Chain.Mainnet

    /**
     * Builds and signs the transaction, then verifies every input script against its UTXO
     * (the equivalent of CoreBitcoin's `BTCScriptMachine` check) before returning it.
     *
     * @throws IllegalArgumentException for an invalid address, a key that does not own its input's
     *   script, a non-P2PKH input, or a transaction that fails script verification.
     */
    @Throws(IllegalArgumentException::class)
    fun sign(inputs: List<Input>, outputs: List<Output>, testnet: Boolean): SignedTransaction {
        require(inputs.isNotEmpty()) { "no inputs" }
        require(outputs.isNotEmpty()) { "no outputs" }
        val chain = chain(testnet)

        val txOuts = outputs.map { TxOut(Satoshi(it.amountSat), addressScript(it.address, chain)) }
        val keys = inputs.mapIndexed { i, input ->
            val key = PrivateKey(input.privateKey)
            val expected = Hex.encode(Script.write(Script.pay2pkh(key.publicKey())))
            require(input.scriptPubKeyHex.equals(expected, ignoreCase = true)) {
                "input $i: private key does not own script ${input.scriptPubKeyHex}"
            }
            key
        }
        val txIns = inputs.map {
            TxIn(OutPoint(TxId(it.txid), it.vout), ByteVector.empty, FINAL_SEQUENCE)
        }

        var tx = Transaction(version = 1L, txIn = txIns, txOut = txOuts, lockTime = 0L)
        keys.forEachIndexed { i, key ->
            val publicKey = key.publicKey()
            val sig = tx.signInput(i, Script.pay2pkh(publicKey), SigHash.SIGHASH_ALL, key)
            tx = tx.updateSigScript(i, listOf(OP_PUSHDATA(sig), OP_PUSHDATA(publicKey.value)))
        }

        val spent = inputs.associate {
            OutPoint(TxId(it.txid), it.vout) to TxOut(Satoshi(it.amountSat), ByteVector(Hex.decode(it.scriptPubKeyHex)))
        }
        try {
            Transaction.correctlySpends(tx, spent, ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
        } catch (e: Exception) {
            throw IllegalArgumentException("script verification failed: ${e.message}", e)
        }

        val raw = Transaction.write(tx)
        return SignedTransaction(rawTxHex = Hex.encode(raw), txid = tx.txid.toString(), size = raw.size)
    }

    /** Compressed-key P2PKH address of [privateKey] (the app's change address). */
    @Throws(IllegalArgumentException::class)
    fun p2pkhAddress(privateKey: ByteArray, testnet: Boolean): String {
        val prefix = if (testnet) Base58.Prefix.PubkeyAddressTestnet else Base58.Prefix.PubkeyAddress
        return Base58Check.encode(prefix, PrivateKey(privateKey).publicKey().hash160())
    }

    /** Locking script (hex) of the compressed-key P2PKH address of [privateKey]. */
    @Throws(IllegalArgumentException::class)
    fun p2pkhScriptHex(privateKey: ByteArray): String =
        Hex.encode(Script.write(Script.pay2pkh(PrivateKey(privateKey).publicKey())))

    /** Whether [address] is a Bitcoin address (legacy, P2SH or bech32) of the given network. */
    fun isValidAddress(address: String, testnet: Boolean): Boolean =
        Bitcoin.addressToPublicKeyScript(chain(testnet).chainHash, address).isRight

    /** Whether [scriptHex] is a P2PKH locking script. */
    fun isP2pkhScript(scriptHex: String): Boolean =
        try {
            Script.isPay2pkh(Hex.decode(scriptHex))
        } catch (e: Exception) {
            false
        }

    private fun addressScript(address: String, chain: Chain): List<ScriptElt> {
        val result = Bitcoin.addressToPublicKeyScript(chain.chainHash, address)
        require(result.isRight) { "invalid address for ${chain.name}: $address" }
        return result.right!!
    }
}
