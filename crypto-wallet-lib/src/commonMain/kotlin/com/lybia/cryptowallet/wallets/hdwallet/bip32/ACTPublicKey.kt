package com.lybia.cryptowallet.wallets.hdwallet.bip32

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.wallets.cardano.Ed25519

class ACTPublicKey : ACTKey {

    constructor() : super(ACTTypeKey.PublicKey)

    constructor(
        priKey: ACTPrivateKey,
        chainCode: ByteArray? = null,
        network: ACTNetwork,
        depth: Byte = 0,
        fingerprint: Int = 0,
        childIndex: Int = 0
    ) : super(ACTTypeKey.PublicKey) {
        this.chainCode = chainCode
        this.depth = depth
        this.fingerprint = fingerprint
        this.childIndex = childIndex
        this.network = network

        when (network.coin) {
            ACTCoin.Cardano -> {
                // Ed25519 public key from first 32 bytes of the 64-byte expanded key
                val seed32 = priKey.raw!!.copyOfRange(0, 32)
                raw = Ed25519.publicKey(seed32)
            }
            else -> {
                raw = ACTCrypto.generatePublicKey(priKey.raw!!, true)
            }
        }
    }
}
