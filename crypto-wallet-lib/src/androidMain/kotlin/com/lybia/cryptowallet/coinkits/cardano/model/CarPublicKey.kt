package com.lybia.cryptowallet.coinkits.cardano.model

import org.bouncycastle.math.ec.rfc7748.X25519

class CarPublicKey {
    private var buffer: ByteArray = byteArrayOf()

    @Throws(CardanoException::class)
    constructor(bytes: ByteArray) {
        if (bytes.count() != 32) {
            throw CardanoException(CarError.InvalidPublicKeyLength.message)
        }
        buffer = bytes
    }
    fun bytes(): ByteArray {
        return  buffer
    }

    companion object {
        fun derive(fromSecret: ByteArray):CarPublicKey {
            val publicKey = ByteArray(32)
            X25519.scalarMultBase(fromSecret, 0, publicKey, 0)
            return CarPublicKey(publicKey)
        }
    }
}