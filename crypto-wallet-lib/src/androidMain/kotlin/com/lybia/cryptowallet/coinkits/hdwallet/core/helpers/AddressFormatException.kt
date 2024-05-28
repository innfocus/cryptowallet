package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers


open class AddressFormatException : IllegalArgumentException {
    constructor() : super()

    constructor(message: String?) : super(message)

    /**
     * This exception is thrown by [Base58], [Bech32] and the [PrefixedChecksummedBytes] hierarchy of
     * classes when you try to decode data and a character isn't valid. You shouldn't allow the user to proceed in this
     * case.
     */
    class InvalidCharacter(val character: Char, val position: Int) : AddressFormatException(
        "Invalid character '$character' at position $position"
    )

    /**
     * This exception is thrown by [Base58], [Bech32] and the [PrefixedChecksummedBytes] hierarchy of
     * classes when you try to decode data and the data isn't of the right size. You shouldn't allow the user to proceed
     * in this case.
     */
    class InvalidDataLength : AddressFormatException {
        constructor() : super()

        constructor(message: String?) : super(message)
    }

    /**
     * This exception is thrown by [Base58], [Bech32] and the [PrefixedChecksummedBytes] hierarchy of
     * classes when you try to decode data and the checksum isn't valid. You shouldn't allow the user to proceed in this
     * case.
     */
    class InvalidChecksum : AddressFormatException {
        constructor() : super("Checksum does not validate")

        constructor(message: String?) : super(message)
    }

    /**
     * This exception is thrown by the [PrefixedChecksummedBytes] hierarchy of classes when you try and decode an
     * address or private key with an invalid prefix (version header or human-readable part). You shouldn't allow the
     * user to proceed in this case.
     */
    open class InvalidPrefix : AddressFormatException {
        constructor() : super()

        constructor(message: String?) : super(message)
    }

    /**
     * This exception is thrown by the [PrefixedChecksummedBytes] hierarchy of classes when you try and decode an
     * address with a prefix (version header or human-readable part) that used by another network (usually: mainnet vs
     * testnet). You shouldn't allow the user to proceed in this case as they are trying to send money across different
     * chains, an operation that is guaranteed to destroy the money.
     */
    class WrongNetwork : InvalidPrefix {
        constructor(versionHeader: Int) : super("Version code of address did not match acceptable versions for network: $versionHeader")

        constructor(hrp: String) : super("Human readable part of address did not match acceptable HRPs for network: $hrp")
    }
}