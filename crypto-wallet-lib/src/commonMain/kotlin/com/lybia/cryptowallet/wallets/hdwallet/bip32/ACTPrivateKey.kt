package com.lybia.cryptowallet.wallets.hdwallet.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.enums.Algorithm
import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.utils.bigENDIAN
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.int32Bytes
import com.lybia.cryptowallet.utils.littleENDIAN
import com.lybia.cryptowallet.utils.suffix

class ACTBIP32Exception(message: String) : Exception(message)

enum class ACTBIP32Error(val message: String) {
    KeyDerivateionFailed("Key Derivateion Failed"),
    KeyPublicCreateFailed("Key Public Create Failed")
}

class ACTPrivateKey : ACTKey {

    constructor() : super(ACTTypeKey.PrivateKey)

    constructor(
        raw: ByteArray? = null,
        chainCode: ByteArray? = null,
        depth: Byte = 0,
        fingerprint: Int = 0,
        childIndex: Int = 0,
        network: ACTNetwork
    ) : super(ACTTypeKey.PrivateKey) {
        this.raw = raw
        this.chainCode = chainCode
        this.depth = depth
        this.fingerprint = fingerprint
        this.childIndex = childIndex
        this.network = network
    }

    constructor(seed: ByteArray, network: ACTNetwork) : super(ACTTypeKey.PrivateKey) {
        this.network = network
        when (network.coin.algorithm()) {
            Algorithm.Secp256k1 -> setupWithSecp256k1(seed)
            else -> setupWithEd25519V2(seed)
        }
    }

    fun publicKey(): ACTPublicKey {
        return ACTPublicKey(this, chainCode, network, depth, fingerprint, childIndex)
    }

    fun signSerializeDER(hash: ByteArray): ByteArray? {
        val r = raw ?: return null
        return ACTCrypto.signSerializeDER(hash, r)
    }

    @Throws(ACTBIP32Exception::class)
    fun derived(node: ACTDerivationNode): ACTPrivateKey {
        val edge = 0x80000000.toInt()
        if ((edge and node.index) != 0) {
            throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
        }
        return when (network.coin) {
            ACTCoin.Cardano -> derivedCardano(node)
            else -> derivedSecp256k1(node, edge)
        }
    }

    private fun derivedSecp256k1(node: ACTDerivationNode, edge: Int): ACTPrivateKey {
        val r = raw ?: throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
        val c = chainCode ?: throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)
        var data = byteArrayOf()
        when (node.hardens) {
            true -> {
                data += 0x00
                data += r
            }
            false -> {
                data += ACTCrypto.generatePublicKey(r, true)
            }
        }
        val derivingIndex = (if (node.hardens) edge or node.index else node.index) and 0xFFFFFFFF.toInt()
        data += derivingIndex.int32Bytes()
        val digest = ACTCrypto.hmacSHA512(c, data)
        // Manual big-integer addition modulo secp256k1 curve order
        val factorBytes = digest.copyOfRange(0, 32)
        val curveOrderBytes = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".fromHexToByteArray()
        val derivedPrivateKey = addModCurveOrder(r, factorBytes, curveOrderBytes).suffix(32)
        val derivedChainCode = digest.copyOfRange(32, 64)
        val pubKeyRaw = publicKey().raw!!
        val sha256ripemd160 = ACTCrypto.sha256ripemd160(pubKeyRaw)
        val fp = sha256ripemd160.copyOfRange(0, 4).littleENDIAN()
        return ACTPrivateKey(derivedPrivateKey, derivedChainCode, depth.inc(), fp, derivingIndex, network)
    }

    /**
     * Cardano Ed25519-BIP32 V2 derivation (same logic as androidMain CarDerivation.kt).
     */
    private fun derivedCardano(node: ACTDerivationNode): ACTPrivateKey {
        val edge = 0x80000000.toInt()
        val derIndex = if (node.hardens) (edge or node.index) else node.index
        val idxBuf = serializeCardanoIndex(derIndex)
        val prvKey = raw!!
        val pubKey = publicKey()

        var data = byteArrayOf()
        if (node.hardens) {
            data += 0x00.toByte()
            data += prvKey
        } else {
            data += 0x02.toByte()
            data += pubKey.raw!!
        }
        data += idxBuf
        val z = ACTCrypto.hmacSHA512(chainCode!!, data)

        var skey = ByteArray(64)
        // addLeft
        val zl8 = multiply8V2(z, 28)
        val leftResult = scalarAddNoOverflow(zl8, prvKey.copyOfRange(0, 32))
        skey = leftResult + skey.copyOfRange(32, 64)
        // addRight
        val rZ = z.copyOfRange(32, 64)
        val rPriv = prvKey.copyOfRange(32, 64)
        val rightResult = add256bitsV2(rZ, rPriv)
        skey = skey.copyOfRange(0, 32) + rightResult

        data = byteArrayOf()
        if (node.hardens) {
            data += 0x01.toByte()
            data += prvKey
        } else {
            data += 0x03.toByte()
            data += pubKey.raw!!
        }
        data += idxBuf
        val hmacOut = ACTCrypto.hmacSHA512(chainCode!!, data)
        val cc = hmacOut.copyOfRange(32, 64)

        val childIndex = idxBuf.bigENDIAN()
        return ACTPrivateKey(skey, cc, depth.inc(), 0, childIndex, network)
    }

    // ── Cardano helper functions ──

    private fun serializeCardanoIndex(index: Int): ByteArray {
        // V2: little-endian
        return byteArrayOf(
            (index and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(),
            ((index shr 16) and 0xFF).toByte(),
            ((index shr 24) and 0xFF).toByte()
        )
    }

    private fun multiply8V2(src: ByteArray, bytes: Int): ByteArray {
        val dst = ByteArray(32)
        var prevAcc = 0
        for (i in 0 until bytes) {
            val v = src[i].toInt() and 0xFF
            dst[i] = (((v shl 3) or prevAcc) and 0xFF).toByte()
            prevAcc = v ushr 5
        }
        dst[bytes] = ((src[bytes - 1].toInt() and 0xFF) ushr 5).toByte()
        return dst
    }

    private fun scalarAddNoOverflow(sk1: ByteArray, sk2: ByteArray): ByteArray {
        val dst = ByteArray(32)
        var r = 0
        for (i in 0 until 32) {
            r += (sk1[i].toInt() and 0xFF) + (sk2[i].toInt() and 0xFF)
            dst[i] = (r and 0xFF).toByte()
            r = r ushr 8
        }
        return dst
    }

    private fun add256bitsV2(src1: ByteArray, src2: ByteArray): ByteArray {
        val dst = ByteArray(32)
        var carry = 0
        for (i in 0 until 32) {
            val a = src1[i].toInt() and 0xFF
            val b = src2[i].toInt() and 0xFF
            val r = a + b + carry
            dst[i] = (r and 0xFF).toByte()
            carry = if (r > 0xFF) 1 else 0
        }
        return dst
    }

    // ── Setup methods ──

    private fun setupWithSecp256k1(seed: ByteArray) {
        val output = ACTCrypto.hmacSHA512("Bitcoin seed".encodeToByteArray(), seed)
        this.raw = output.copyOfRange(0, 32)
        this.chainCode = output.copyOfRange(32, 64)
    }

    private fun setupWithEd25519V2(seed: ByteArray) {
        val s = seed.copyOf()
        s[0] = ((s[0].toInt() and 0xFF) and 0xF8).toByte()
        s[31] = ((s[31].toInt() and 0xFF) and 0x1F).toByte()
        s[31] = ((s[31].toInt() and 0xFF) or 0x40).toByte()
        this.raw = s.copyOfRange(0, 64)
        this.chainCode = s.copyOfRange(64, s.size)
    }

    companion object {
        /**
         * Add two 256-bit numbers modulo secp256k1 curve order.
         * Replaces java.math.BigInteger usage.
         */
        internal fun addModCurveOrder(a: ByteArray, b: ByteArray, curveOrder: ByteArray): ByteArray {
            // Convert big-endian byte arrays to little-endian for easier arithmetic
            val aLE = a.reversedArray()
            val bLE = b.reversedArray()
            val nLE = curveOrder.reversedArray()

            // Add a + b
            val sum = ByteArray(33)
            var carry = 0
            for (i in 0 until 32) {
                val s = (aLE[i].toInt() and 0xFF) + (bLE[i].toInt() and 0xFF) + carry
                sum[i] = (s and 0xFF).toByte()
                carry = s shr 8
            }
            sum[32] = carry.toByte()

            // Reduce modulo curveOrder: if sum >= curveOrder, subtract curveOrder
            val result = if (compare33vs32(sum, nLE) >= 0) {
                subtract(sum, nLE)
            } else {
                sum.copyOfRange(0, 32)
            }

            // Convert back to big-endian
            return result.reversedArray().let {
                if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it
            }
        }

        private fun compare33vs32(a: ByteArray, b: ByteArray): Int {
            if ((a[32].toInt() and 0xFF) > 0) return 1
            for (i in 31 downTo 0) {
                val av = a[i].toInt() and 0xFF
                val bv = b[i].toInt() and 0xFF
                if (av > bv) return 1
                if (av < bv) return -1
            }
            return 0
        }

        private fun subtract(a: ByteArray, b: ByteArray): ByteArray {
            val result = ByteArray(32)
            var borrow = 0
            for (i in 0 until 32) {
                val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF) - borrow
                result[i] = (diff and 0xFF).toByte()
                borrow = if (diff < 0) 1 else 0
            }
            return result
        }
    }
}
