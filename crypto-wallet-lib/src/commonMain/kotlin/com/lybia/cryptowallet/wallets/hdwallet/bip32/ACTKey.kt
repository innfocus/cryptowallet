package com.lybia.cryptowallet.wallets.hdwallet.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.utils.Base58Ext
import com.lybia.cryptowallet.utils.bigENDIAN
import com.lybia.cryptowallet.utils.littleENDIAN

enum class ACTTypeKey {
    PrivateKey,
    PublicKey
}

open class ACTKey(val typeKey: ACTTypeKey) {
    var raw: ByteArray? = null
    var chainCode: ByteArray? = null
    var depth: Byte = 0
    var fingerprint: Int = 0
    var childIndex: Int = 0
    var network: ACTNetwork = ACTNetwork(ACTCoin.Bitcoin, true)

    fun extended(): String {
        val r = raw ?: return ""
        val c = chainCode ?: return ""
        var extData = byteArrayOf()
        val isPrv = typeKey == ACTTypeKey.PrivateKey
        extData += if (isPrv) {
            network.privateKeyPrefix().bigENDIAN()
        } else {
            network.publicKeyPrefix().bigENDIAN()
        }
        extData += depth.littleENDIAN()
        extData += fingerprint.littleENDIAN()
        extData += childIndex.bigENDIAN()
        extData += c
        if (isPrv) {
            extData += 0x00
        }
        extData += r
        val cs = ACTCrypto.doubleSHA256(extData).copyOfRange(0, 4)
        return Base58Ext.encode(extData + cs)
    }
}
