package com.lybia.cryptowallet.coinkits.hdwallet.bip32

import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.bigENDIAN
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.littleENDIAN


enum class ACTTypeKey {
    PrivateKey,
    PublicKey
}

open class ACTKey(typeKey: ACTTypeKey) {

    var raw         : ByteArray?    = null
    var chainCode   : ByteArray?    = null
    var depth       : Byte          = 0
    var fingerprint : Int           = 0
    var childIndex  : Int           = 0
    var network     : ACTNetwork    = ACTNetwork(ACTCoin.Bitcoin, true)
    var typeKey     : ACTTypeKey    = typeKey

    fun extended(): String {

        val r = raw         ?: return ""
        val c = chainCode   ?: return ""
        var extData = byteArrayOf()
        val isPrv   = typeKey == ACTTypeKey.PrivateKey
        extData     += when(isPrv) {
            true    -> network.privateKeyPrefix().bigENDIAN()
            false   -> network.publicKeyPrefix().bigENDIAN()
        }
        extData     += depth.littleENDIAN()
        extData     += fingerprint.littleENDIAN()
        extData     += childIndex.bigENDIAN()
        extData     += c
        if (isPrv) {
            extData += 0x00
        }
        extData     += r
        val cs      = ACTCryto.doubleSHA256(extData).copyOfRange(0, 4)
        return Base58.encode(extData + cs)
    }
}