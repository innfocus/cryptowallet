package com.lybia.cryptowallet.coinkits.cardano.model

@Deprecated(
    message = "Use commonMain Cardano module instead. This enum is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
enum class CarDerivationScheme(val value: Int) {
    V1(1),
    V2(2)
}

@Deprecated(
    message = "Use commonMain Cardano module instead. This class is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
class CardanoException(message: String) : Exception(message)

@Deprecated(
    message = "Use commonMain Cardano module instead. This enum is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
enum class CarError(val message: String)
{
    SeedGenerationFailed    ("SeedGenerationFailed"),
    InvalidSeedLength       ("InvalidSeedLength"),
    InvalidScalarLength     ("InvalidScalarLength"),
    InvalidPublicKeyLength  ("InvalidPublicKeyLength"),
    InvalidPrivateKeyLength ("InvalidPrivateKeyLength"),
    InvalidSignatureLength  ("InvalidSignatureLength"),
}

@Deprecated(
    message = "Use commonMain Cardano module instead. This enum is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
enum class CarAddressType(val value: Int) {
    ATPubKey(0),
    ATScript(1),
    ATRedeem(2)
}
