package com.lybia.cryptowallet.coinkits.cardano.model

import org.spongycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.math.ec.rfc8032.Ed25519

class CarKeyPair {
    var publicKey   : CarPublicKey
    var privateKey  : CarPrivateKey

    constructor(publicKey: CarPublicKey, privateKey: CarPrivateKey) {
        this.publicKey  = publicKey
        this.privateKey = privateKey
    }

    constructor(pubKey: ByteArray, priKey: ByteArray) {
        this.publicKey  = CarPublicKey(pubKey)
        this.privateKey = CarPrivateKey(priKey)
    }

    fun sign(message: ByteArray): ByteArray {
        val privBytes = privateKey.bytes()
        val seed = if (privBytes.size == 64) privBytes.copyOfRange(0, 32) else privBytes
        val privParams = Ed25519PrivateKeyParameters(seed, 0)
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

}