package com.lybia.cryptowallet.wallets.bitcoin

import com.lybia.cryptowallet.models.bitcoin.UtxoInfo
import fr.acinq.bitcoin.*
import fr.acinq.secp256k1.Hex

/**
 * Local Bitcoin transaction builder using bitcoin-kmp.
 *
 * Builds, signs, and serializes Bitcoin transactions entirely client-side
 * for P2PKH (Legacy), P2SH-P2WPKH (Nested SegWit), and P2WPKH (Native SegWit).
 *
 * Only requires external API calls for UTXO fetching and broadcasting.
 */
object BitcoinTransactionBuilder {

    /** Dust threshold in satoshis — outputs below this are rejected by nodes. */
    private const val DUST_THRESHOLD_P2WPKH = 294L
    private const val DUST_THRESHOLD_P2PKH = 546L

    /**
     * Result of building and signing a transaction.
     */
    data class BuildResult(
        val rawTxHex: String,
        val txid: String,
        val fee: Long,
        val vsize: Int
    )

    /**
     * Estimated virtual sizes per component (in vbytes) for fee estimation.
     */
    object VsizeEstimates {
        const val TX_OVERHEAD = 11        // version(4) + locktime(4) + segwit marker/flag(2) + vin/vout count
        const val P2WPKH_INPUT = 68      // outpoint(36) + sequence(4) + witness(107)/4 + scriptSig(1)
        const val P2SH_P2WPKH_INPUT = 91 // outpoint(36) + sequence(4) + scriptSig(23+1) + witness(107)/4
        const val P2PKH_INPUT = 148       // outpoint(36) + sequence(4) + scriptSig(~107)
        const val P2WPKH_OUTPUT = 31      // value(8) + scriptPubKey(1+22)
        const val P2PKH_OUTPUT = 34       // value(8) + scriptPubKey(1+25)
        const val P2SH_OUTPUT = 32        // value(8) + scriptPubKey(1+23)
    }

    /**
     * Estimate the transaction vsize for fee calculation.
     *
     * @param inputCount number of inputs
     * @param outputCount number of outputs (including change)
     * @param addressType sender address type
     * @return estimated vsize in virtual bytes
     */
    fun estimateVsize(
        inputCount: Int,
        outputCount: Int,
        addressType: BitcoinAddressType
    ): Int {
        val inputVsize = when (addressType) {
            BitcoinAddressType.NATIVE_SEGWIT -> VsizeEstimates.P2WPKH_INPUT
            BitcoinAddressType.NESTED_SEGWIT -> VsizeEstimates.P2SH_P2WPKH_INPUT
            BitcoinAddressType.LEGACY -> VsizeEstimates.P2PKH_INPUT
        }
        return VsizeEstimates.TX_OVERHEAD +
                (inputCount * inputVsize) +
                (outputCount * VsizeEstimates.P2WPKH_OUTPUT)
    }

    /**
     * Select UTXOs using a simple largest-first strategy.
     *
     * @param utxos available UTXOs (confirmed only)
     * @param targetAmount amount to send in satoshis
     * @param feeRatePerVbyte fee rate in sat/vB
     * @param addressType sender address type (affects input vsize)
     * @return pair of (selected UTXOs, total fee) or null if insufficient funds
     */
    fun selectUtxos(
        utxos: List<UtxoInfo>,
        targetAmount: Long,
        feeRatePerVbyte: Long,
        addressType: BitcoinAddressType
    ): Pair<List<UtxoInfo>, Long>? {
        // Only use confirmed UTXOs, sorted largest first
        val confirmed = utxos.filter { it.status.confirmed }.sortedByDescending { it.value }
        if (confirmed.isEmpty()) return null

        val selected = mutableListOf<UtxoInfo>()
        var totalInput = 0L

        for (utxo in confirmed) {
            selected.add(utxo)
            totalInput += utxo.value

            // Estimate fee with 2 outputs (recipient + change)
            val estimatedVsize = estimateVsize(selected.size, 2, addressType)
            val fee = estimatedVsize * feeRatePerVbyte

            if (totalInput >= targetAmount + fee) {
                val change = totalInput - targetAmount - fee
                // If change is below dust, absorb it into fee
                val dustThreshold = when (addressType) {
                    BitcoinAddressType.LEGACY -> DUST_THRESHOLD_P2PKH
                    else -> DUST_THRESHOLD_P2WPKH
                }
                val actualFee = if (change < dustThreshold) {
                    // No change output — recalculate fee with 1 output
                    val vsizeNoChange = estimateVsize(selected.size, 1, addressType)
                    val feeNoChange = vsizeNoChange * feeRatePerVbyte
                    // Absorb remaining into fee
                    totalInput - targetAmount
                } else {
                    fee
                }
                return Pair(selected, actualFee)
            }
        }
        return null // Insufficient funds
    }

    /**
     * Build, sign, and serialize a Bitcoin transaction locally.
     *
     * @param utxos selected UTXOs to spend
     * @param toAddress destination address
     * @param amountSat amount to send in satoshis
     * @param feeSat transaction fee in satoshis
     * @param changeAddress address to receive change (same type as sender)
     * @param privateKey sender's private key
     * @param publicKey sender's public key
     * @param addressType sender's address type
     * @return BuildResult with raw tx hex and metadata
     */
    fun buildAndSign(
        utxos: List<UtxoInfo>,
        toAddress: String,
        amountSat: Long,
        feeSat: Long,
        changeAddress: String,
        privateKey: PrivateKey,
        publicKey: PublicKey,
        addressType: BitcoinAddressType,
        chain: Chain
    ): BuildResult {
        val totalInput = utxos.sumOf { it.value }
        val change = totalInput - amountSat - feeSat

        // Build outputs
        val outputs = mutableListOf<TxOut>()
        outputs.add(TxOut(Satoshi(amountSat), addressToScript(toAddress, chain)))

        // Only add change output if above dust threshold
        val dustThreshold = when (addressType) {
            BitcoinAddressType.LEGACY -> DUST_THRESHOLD_P2PKH
            else -> DUST_THRESHOLD_P2WPKH
        }
        if (change > dustThreshold) {
            outputs.add(TxOut(Satoshi(change), addressToScript(changeAddress, chain)))
        }

        // Build unsigned inputs
        val inputs = utxos.map { utxo ->
            TxIn(
                outPoint = OutPoint(TxId(utxo.txid), utxo.vout.toLong()),
                signatureScript = ByteVector.empty,
                sequence = 0xfffffffdL // enable RBF
            )
        }

        var tx = Transaction(
            version = 2L,
            txIn = inputs,
            txOut = outputs,
            lockTime = 0L
        )

        // Sign each input based on address type
        tx = when (addressType) {
            BitcoinAddressType.NATIVE_SEGWIT -> signP2wpkh(tx, utxos, privateKey, publicKey)
            BitcoinAddressType.NESTED_SEGWIT -> signP2shP2wpkh(tx, utxos, privateKey, publicKey)
            BitcoinAddressType.LEGACY -> signP2pkh(tx, utxos, privateKey, publicKey)
        }

        val rawHex = Hex.encode(Transaction.write(tx))
        return BuildResult(
            rawTxHex = rawHex,
            txid = tx.txid.toString(),
            fee = feeSat,
            vsize = tx.weight() / 4
        )
    }

    /**
     * Sign P2WPKH (Native SegWit) inputs.
     * scriptSig is empty; witness = [DER_sig + sighash_byte, compressed_pubkey]
     */
    private fun signP2wpkh(
        tx: Transaction,
        utxos: List<UtxoInfo>,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): Transaction {
        var signed = tx
        for (i in utxos.indices) {
            val amount = Satoshi(utxos[i].value)
            // For P2WPKH, the "previous output script" used in sighash is pay2pkh(pubkey)
            val sig = signed.signInput(
                i,
                Script.pay2pkh(publicKey),
                SigHash.SIGHASH_ALL,
                amount,
                SigVersion.SIGVERSION_WITNESS_V0,
                privateKey
            )
            val witness = ScriptWitness(listOf(ByteVector(sig), publicKey.value))
            signed = signed.updateWitness(i, witness)
        }
        return signed
    }

    /**
     * Sign P2SH-P2WPKH (Nested SegWit) inputs.
     * scriptSig = push(redeemScript); witness = [DER_sig + sighash_byte, compressed_pubkey]
     * redeemScript = OP_0 <20-byte-pubkey-hash> (the P2WPKH script)
     */
    private fun signP2shP2wpkh(
        tx: Transaction,
        utxos: List<UtxoInfo>,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): Transaction {
        var signed = tx
        val redeemScript = Script.write(Script.pay2wpkh(publicKey))

        for (i in utxos.indices) {
            val amount = Satoshi(utxos[i].value)
            // Segwit sighash uses pay2pkh(pubkey) as the script code
            val sig = signed.signInput(
                i,
                Script.pay2pkh(publicKey),
                SigHash.SIGHASH_ALL,
                amount,
                SigVersion.SIGVERSION_WITNESS_V0,
                privateKey
            )
            // scriptSig = push the redeem script
            val scriptSig = Script.write(listOf(OP_PUSHDATA(redeemScript)))
            signed = signed.updateSigScript(i, scriptSig)
            // witness = [sig, pubkey]
            val witness = ScriptWitness(listOf(ByteVector(sig), publicKey.value))
            signed = signed.updateWitness(i, witness)
        }
        return signed
    }

    /**
     * Sign P2PKH (Legacy) inputs.
     * scriptSig = <DER_sig + sighash_byte> <compressed_pubkey>; no witness.
     */
    private fun signP2pkh(
        tx: Transaction,
        utxos: List<UtxoInfo>,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): Transaction {
        var signed = tx
        for (i in utxos.indices) {
            val sig = signed.signInput(
                i,
                Script.pay2pkh(publicKey),
                SigHash.SIGHASH_ALL,
                privateKey
            )
            val scriptSig = Script.write(
                listOf(OP_PUSHDATA(sig), OP_PUSHDATA(publicKey.value))
            )
            signed = signed.updateSigScript(i, scriptSig)
        }
        return signed
    }

    /**
     * Convert a Bitcoin address string to its corresponding output script (scriptPubKey).
     * Supports P2PKH (1.../m.../n...), P2SH (3.../2...), P2WPKH (bc1q.../tb1q...).
     */
    fun addressToScript(address: String, chain: Chain): ByteVector {
        return when {
            // Bech32 (Native SegWit P2WPKH or P2WSH)
            address.startsWith("bc1") || address.startsWith("tb1") -> {
                val (_, witnessVersion, program) = Bech32.decodeWitnessAddress(address)
                when {
                    witnessVersion == 0.toByte() && program.size == 20 ->
                        ByteVector(Script.write(Script.pay2wpkh(program)))
                    witnessVersion == 0.toByte() && program.size == 32 ->
                        ByteVector(Script.write(Script.pay2wsh(program)))
                    witnessVersion == 1.toByte() && program.size == 32 ->
                        ByteVector(Script.write(listOf(OP_1, OP_PUSHDATA(program))))
                    else -> throw IllegalArgumentException("Unsupported witness program: version=$witnessVersion, len=${program.size}")
                }
            }
            // Base58Check (P2PKH or P2SH)
            else -> {
                val (prefix, payload) = Base58Check.decode(address)
                when (prefix) {
                    Base58.Prefix.PubkeyAddress, Base58.Prefix.PubkeyAddressTestnet ->
                        ByteVector(Script.write(Script.pay2pkh(payload)))
                    Base58.Prefix.ScriptAddress, Base58.Prefix.ScriptAddressTestnet ->
                        ByteVector(Script.write(listOf(OP_HASH160, OP_PUSHDATA(payload), OP_EQUAL)))
                    else -> throw IllegalArgumentException("Unknown Base58 prefix: $prefix")
                }
            }
        }
    }
}
