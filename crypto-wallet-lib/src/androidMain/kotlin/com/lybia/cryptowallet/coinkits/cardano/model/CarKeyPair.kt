package com.lybia.cryptowallet.coinkits.cardano.model

import org.spongycastle.crypto.digests.SHA512Digest
import org.whispersystems.curve25519.java.*

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
        val signatureL  = ByteArray(32)
        val signatureR  = ByteArray(32)
        val hram        = ByteArray(64)
        val r           = ByteArray(64)
        val iR          = ge_p3()
        val sha512      = SHA512Digest()
        sha512.update(privateKey.bytes(), 32, 32)
        sha512.update(message, 0, message.size)
        sha512.doFinal(r, 0)
        sc_reduce.sc_reduce(r)
        ge_scalarmult_base.ge_scalarmult_base(iR, r)
        ge_p3_tobytes.ge_p3_tobytes(signatureL, iR)
        sha512.reset()
        sha512.update(signatureL, 0, 32)
        sha512.update(publicKey.bytes(), 0, 32)
        sha512.update(message, 0, message.size)
        sha512.doFinal(hram, 0)
        sc_reduce.sc_reduce(hram)
        sc_muladd.sc_muladd(signatureR, hram, privateKey.bytes(), r)
        return signatureL + signatureR
    }

}