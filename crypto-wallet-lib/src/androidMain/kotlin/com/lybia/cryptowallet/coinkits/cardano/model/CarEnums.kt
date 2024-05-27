package com.lybia.cryptowallet.coinkits.cardano.model

enum class CarDerivationScheme(val value: Int) {
    V1(1),
    V2(2)
}

class CardanoException(message: String) : Exception(message)

enum class CarError(val message: String)
{
    SeedGenerationFailed    ("SeedGenerationFailed"),
    InvalidSeedLength       ("InvalidSeedLength"),
    InvalidScalarLength     ("InvalidScalarLength"),
    InvalidPublicKeyLength  ("InvalidPublicKeyLength"),
    InvalidPrivateKeyLength ("InvalidPrivateKeyLength"),
    InvalidSignatureLength  ("InvalidSignatureLength"),
}

enum class CarAddressType(val value: Int) {
    ATPubKey(0),
    ATScript(1),
    ATRedeem(2)
}
