package com.lybia.cryptowallet.coinkits.cardano.model

import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.core.ACTDerivationNode
import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.bigENDIAN
import unsigned.*


private val ENCRYPTED_KEY_SIZE      = 64
private val TAG_DERIVE_Z_NORMAL     = 0x02
private val TAG_DERIVE_Z_HARDENED   = 0x00
private val TAG_DERIVE_CC_NORMAL    = 0x03
private val TAG_DERIVE_CC_HARDENED  = 0x01

fun ACTPrivateKey.derivedCar(password   : String = "",
                             node       : ACTDerivationNode,
                             scheme     : CarDerivationScheme): ACTPrivateKey {
    val edge        = (0x80000000).toInt()
    val derIndex    = if (node.hardens) (edge or node.index) else node.index
    val idxBuf      = serialize(derIndex, scheme)
    val prvKey      = unencrypt(password, raw!!)
    val pubKey      = publicKey()
    var skey        = ByteArray(ENCRYPTED_KEY_SIZE)
    var data        = byteArrayOf()
    when(node.hardens) {
        true -> {
            data += TAG_DERIVE_Z_HARDENED.toByte()
            data += raw!!
        }
        false -> {
            data += TAG_DERIVE_Z_NORMAL.toByte()
            data += pubKey.raw!!
        }
    }
    data            += idxBuf
    val z           = ACTCryto.hmacSHA512(chainCode!!, data)
    skey            = addLeft  (skey, z!!, prvKey, scheme)
    skey            = addRight (skey, z!!, prvKey, scheme)

    data            = byteArrayOf()
    when(node.hardens) {
        true -> {
            data += TAG_DERIVE_CC_HARDENED.toByte()
            data += raw!!
        }
        false -> {
            data += TAG_DERIVE_CC_NORMAL.toByte()
            data += pubKey.raw!!
        }
    }
    data            += idxBuf
    val hmacOut = ACTCryto.hmacSHA512(chainCode!!, data)
    val cc      = hmacOut!!.copyOfRange(32, 64)
    val cPrv    = childDerived(password, skey)

    val childIndex = idxBuf.bigENDIAN()
    return ACTPrivateKey(cPrv, cc, depth.inc(), 0, childIndex, network)

}

/*
* Private methods
*/
private fun ACTPrivateKey.serialize(index: Int, scheme: CarDerivationScheme): ByteArray{
    val out = ByteArray(4)
    when(scheme) {
        CarDerivationScheme.V1  -> {
            out[0] = ((index shr 24) and 0xFF).toUByte()
            out[1] = ((index shr 16) and 0xFF).toUByte()
            out[2] = ((index shr  8) and 0xFF).toUByte()
            out[3] = ((index shr  0) and 0xFF).toUByte()
        }
        CarDerivationScheme.V2  -> {
            out[3] = ((index shr 24) and 0xFF).toUByte()
            out[2] = ((index shr 16) and 0xFF).toUByte()
            out[1] = ((index shr  8) and 0xFF).toUByte()
            out[0] = ((index shr  0) and 0xFF).toUByte()
        }
    }
    return out
}

private fun ACTPrivateKey.multiply8V2(src: ByteArray, bytes: Int): ByteArray {
    val dst             = ByteArray(32)
    var prevAcc: Ubyte  = 0.toUbyte()
    for (i in 0 until bytes) {
        dst[i]  = ((src[i].toUbyte() shl 3) + (prevAcc and 0x07)).toByte()
        prevAcc = (src[i] ushr 5).toUbyte()
    }
    dst[bytes]  = src[bytes - 1] ushr 5
    return dst
}

private fun ACTPrivateKey.add256bitsV2(src1: ByteArray, src2: ByteArray): ByteArray{
    val dst     = ByteArray(32)
    var carry   = 0
    for (i in 0 until 32) {
        val a   = src1[i].toUShort()
        val b   = src2[i].toUShort()
        val r   = a + b + carry
        dst[i]  = (r and 0xFF).toByte()
        carry   = if (r > 0xFF) 1 else 0
    }
    return dst
}

private fun ACTPrivateKey.scalarAddNoOverflow(sk1: ByteArray, sk2: ByteArray): ByteArray {
    var dst = ByteArray(32)
    var r   = 0
    for (i in 0 until 32) {
        r       += sk1[i].toUShort() + sk2[i].toUShort()
        dst[i]  = (r and 0xFF).toByte()
        r = r ushr 8
    }
    return dst
}

private fun ACTPrivateKey.addLeft(skey: ByteArray, z: ByteArray, prvKey: ByteArray, scheme: CarDerivationScheme): ByteArray {
    var rs      = ByteArray(32)
    when(scheme) {
        CarDerivationScheme.V1  -> {/* To Do */}
        CarDerivationScheme.V2  -> {
            val zl8 = multiply8V2(z, 28)
            rs = scalarAddNoOverflow(zl8, prvKey)
        }
    }
    return rs + skey.copyOfRange(32, 64)
}

private fun ACTPrivateKey.addRight(skey: ByteArray, z: ByteArray, prvKey: ByteArray, scheme: CarDerivationScheme): ByteArray {
    var rs      = ByteArray(32)
    val rZ      = z.copyOfRange(32, 64)
    val rPriv   = prvKey.copyOfRange(32, 64)
    when(scheme) {
        CarDerivationScheme.V1  -> {/* To Do */}
        CarDerivationScheme.V2  -> rs = add256bitsV2(rZ, rPriv)
    }
    return skey.copyOfRange(0, 32) + rs
}

private fun ACTPrivateKey.unencrypt(password: String, encryptedKey: ByteArray): ByteArray {
    return memoryCombine(password, encryptedKey)
}

private fun ACTPrivateKey.childDerived(password: String, skey: ByteArray): ByteArray {
    return memoryCombine(password, skey)
}

private fun ACTPrivateKey.memoryCombine(password: String, encryptedKey: ByteArray): ByteArray {
    return encryptedKey
}