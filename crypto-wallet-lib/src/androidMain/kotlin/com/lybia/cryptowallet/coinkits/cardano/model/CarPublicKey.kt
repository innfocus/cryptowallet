package com.lybia.cryptowallet.coinkits.cardano.model

import org.whispersystems.curve25519.java.ge_p3
import org.whispersystems.curve25519.java.ge_p3_tobytes
import org.whispersystems.curve25519.java.ge_scalarmult_base


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
            val pub     = ByteArray(32)
            val point   = ge_p3()
            ge_scalarmult_base.ge_scalarmult_base(point, fromSecret)
            ge_p3_tobytes.ge_p3_tobytes(pub, point)
            return CarPublicKey(pub)
        }
    }
}