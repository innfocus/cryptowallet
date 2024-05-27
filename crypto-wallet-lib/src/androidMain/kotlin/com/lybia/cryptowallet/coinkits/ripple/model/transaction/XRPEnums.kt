package com.lybia.cryptowallet.coinkits.ripple.model.transaction

enum class XRPEnums(val value: ByteArray) {
    TransactionType     (byteArrayOf(0x12.toByte())),
    Flags               (byteArrayOf(0x22.toByte())),
    Sequence            (byteArrayOf(0x24.toByte())),
    ReserveIncrement    (byteArrayOf(0x20.toByte())),
    LastLedgerSequence  (byteArrayOf(0x1B.toByte())),
    Amount              (byteArrayOf(0x61.toByte())),
    Fee                 (byteArrayOf(0x68.toByte())),
    SigningPubKey       (byteArrayOf(0x73.toByte())),
    TxnSignature        (byteArrayOf(0x74.toByte())),
    Account             (byteArrayOf(0x81.toByte())),
    Destination         (byteArrayOf(0x83.toByte()));
}

enum class XRPTransactionType(val value: ByteArray) {
    Payment             (byteArrayOf(0x00, 0x00));
}

enum class XRPHashPrefix(val value: ByteArray) {
    TransactionID       (byteArrayOf(0x54, 0x58, 0x4E, 0x00)),
    TxSign              (byteArrayOf(0x53, 0x54, 0x58, 0x00));
}

enum class XRPMemoEnum(val value: ByteArray) {
    DestinationTag      (byteArrayOf(0x2E.toByte())),
    Starts              (byteArrayOf(0xF9.toByte())),
    Start               (byteArrayOf(0xEA.toByte())),
    End                 (byteArrayOf(0xE1.toByte())),
    Ends                (byteArrayOf(0xF1.toByte())),
    Data                (byteArrayOf(0x7D.toByte())),
    Type                (byteArrayOf(0x7C.toByte()));
}