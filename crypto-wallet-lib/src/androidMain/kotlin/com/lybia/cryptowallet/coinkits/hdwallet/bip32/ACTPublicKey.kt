package com.lybia.cryptowallet.coinkits.hdwallet.bip32

import com.lybia.cryptowallet.coinkits.cardano.model.CarPublicKey
import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto


class ACTPublicKey(): ACTKey(ACTTypeKey.PublicKey) {

    constructor(priKey      : ACTPrivateKey,
                chainCode   : ByteArray?    = null,
                network     : ACTNetwork,
                depth       : Byte          = 0,
                fingerprint : Int           = 0,
                childIndex  : Int           = 0): this() {
        this.chainCode      = chainCode
        this.depth          = depth
        this.fingerprint    = fingerprint
        this.childIndex     = childIndex
        this.network        = network

        when(network.coin) {
            ACTCoin.Cardano -> {
                val pub = CarPublicKey.derive(priKey.raw!!)
                raw     = pub.bytes()
            }
            else -> {
                raw = ACTCryto.generatePublicKey(priKey.raw!!, true)
            }
        }
    }

}