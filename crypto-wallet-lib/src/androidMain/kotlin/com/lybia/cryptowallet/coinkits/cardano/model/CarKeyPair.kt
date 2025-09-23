package com.lybia.cryptowallet.coinkits.cardano.model

import org.spongycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

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
        val privParams = Ed25519PrivateKeyParameters(privateKey.bytes(), 0)
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

}