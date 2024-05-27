package com.lybia.cryptowallet.coinkits.hdwallet.bip32

import com.lybia.cryptowallet.coinkits.cardano.model.CarDerivationScheme
import com.lybia.cryptowallet.coinkits.cardano.model.derivedCar
import com.lybia.cryptowallet.coinkits.hdwallet.core.ACTDerivationNode
import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.int32Bytes
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.suffix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.math.BigInteger



class ACTBIP32Exception(message: String) : Exception(message)
enum class ACTBIP32Error(val message: String)
{
    KeyDerivateionFailed    ("Key Derivateion Failed"),
    KeyPublicCreateFailed   ("Key Public Create Failed")
}

class ACTPrivateKey(): ACTKey(ACTTypeKey.PrivateKey) {

    constructor(raw         : ByteArray?    = null,
                chainCode   : ByteArray?    = null,
                depth       : Byte          = 0,
                fingerprint : Int           = 0,
                childIndex  : Int           = 0,
                network     : ACTNetwork): this() {
        this.raw            = raw
        this.chainCode      = chainCode
        this.depth          = depth
        this.fingerprint    = fingerprint
        this.childIndex     = childIndex
        this.network        = network
    }

    constructor(seed        : ByteArray,
                network     : ACTNetwork): this() {
        this.network = network
        when(network.coin.algorithm()) {
            Algorithm.Secp256k1 -> {setupWithSecp256k1(seed)}
            else                -> {setupWithEd25519V2(seed)}
        }
    }

    fun publicKey(): ACTPublicKey {
        return  ACTPublicKey(this, chainCode, network, depth, fingerprint, childIndex)
    }

    fun signSerializeDER(hash: ByteArray): ByteArray? {
        val r = raw ?: return null
        return ACTCryto.signSerializeDER(hash, r)
    }

    @Throws(ACTBIP32Exception::class)
    fun derived(node: ACTDerivationNode): ACTPrivateKey {
        val edge = (0x80000000).toInt()
        if ((edge and node.index) != 0) {
            throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
        }
        return when(network.coin) {
            ACTCoin.Cardano -> {
                derivedCar("", node, CarDerivationScheme.V2)
            }
            else            -> {
                val r = raw         ?: return throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
                val c = chainCode   ?: return throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
                var data = byteArrayOf()
                when(node.hardens) {
                    true -> {
                        data += 0x00
                        data += r
                    }
                    false -> {
                        data += ACTCryto.generatePublicKey(r, true)
                    }
                }
                val derivingIndex = when(node.hardens) {
                    true    -> edge or node.index
                    false   -> node.index
                } and (0xFFFFFFFF).toInt()
                data += derivingIndex.int32Bytes()
                val digest              = ACTCryto.hmacSHA512(c, data) ?: return throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
                val factor              = BigInteger(1, digest.copyOfRange(0, 32))
                val curveOrder          = BigInteger(1, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".fromHexToByteArray())
                val derivedPrivateKey   = ((BigInteger(1, r) + factor) % curveOrder).toByteArray().suffix(32)
                val derivedChainCode    = digest.copyOfRange(32, 64)
                val sha256ripemd160     = ACTCryto.sha256ripemd160(publicKey().raw!!)
                val fingurePrint        = ByteBuffer.wrap(sha256ripemd160, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                ACTPrivateKey(derivedPrivateKey, derivedChainCode, depth.inc(), fingurePrint, derivingIndex, network)
            }
        }
    }

    /*
    * Private methods
    */
    private fun setupWithSecp256k1(seed: ByteArray) {
        val output      = ACTCryto.hmacSHA512("Bitcoin seed".toByteArray(), seed) ?: return
        this.raw        = output.copyOfRange(0, 32)
        this.chainCode  = output.copyOfRange(32, 64)
    }

    private fun setupWithEd25519V2(seed: ByteArray) {
        var s           = seed
        s[0]            = ((s[0].toInt()  and 0xFF) and 0xF8).toByte()
        s[31]           = ((s[31].toInt() and 0xFF) and 0x1F).toByte()
        s[31]           = ((s[31].toInt() and 0xFF) or  0x40).toByte()
        this.raw        = s.copyOfRange(0, 64)
        this.chainCode  = s.copyOfRange(64, s.count())
    }
}