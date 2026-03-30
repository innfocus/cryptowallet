package com.lybia.cryptowallet.coinkits.cardano.helpers

@Deprecated(
    message = "Use commonMain Cardano module instead. This is part of the legacy androidMain Cardano implementation.",
    level = DeprecationLevel.WARNING
)
typealias ADAAmount = Int
val ADACoin  : ADAAmount = 1000000
val ADACent  : ADAAmount = 1000
val ADABit   : ADAAmount = 1