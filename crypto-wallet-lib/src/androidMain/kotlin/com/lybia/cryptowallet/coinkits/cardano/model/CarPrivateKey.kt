package com.lybia.cryptowallet.coinkits.cardano.model

@Deprecated(
    message = "Use commonMain Cardano module instead. This class is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
class CarPrivateKey {
    private var buffer: ByteArray = byteArrayOf()

    @Throws(CardanoException::class)
    constructor(bytes: ByteArray) {
        if (bytes.count() != 64) {
            throw CardanoException(CarError.InvalidPublicKeyLength.message)
        }
        buffer = bytes
    }
    fun bytes(): ByteArray {
        return  buffer
    }
}