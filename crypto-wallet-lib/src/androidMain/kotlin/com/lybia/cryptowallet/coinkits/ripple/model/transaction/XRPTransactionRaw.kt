package com.lybia.cryptowallet.coinkits.ripple.model.transaction

import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.bigENDIAN
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.dropFirst
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.int64Bytes
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.prefix
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.sha512
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toHexString

class XRPTransactionRaw {
    var account         : String        = ""
    var amount          : Double        = 0.0
    var destination     : String        = ""
    var fee             : Double        = 0.0
    var flags           : Int           = 0x80000000.toInt()
    var sequence        : Int           = 0
    var lastLedgerSeq   : Int           = 0
    var signingPubKey   : ByteArray?    = null
    var txnSignature    : ByteArray?    = null
    var memo            : XRPMemo?      = null

    fun transactionID(): String? {
        val ser = serializer(XRPHashPrefix.TransactionID) ?: return null
        return ser.sha512().prefix(32).toHexString()
    }

    data class Result(val txBlob: ByteArray, val transactionID: String)
    fun sign(privateKey: ACTPrivateKey): Result? {
        signingPubKey   = privateKey.publicKey().raw
        val ser         = serializer(XRPHashPrefix.TxSign) ?: return null
        val hash        = ser.sha512().prefix(32)
        txnSignature    = privateKey.signSerializeDER(hash)
        val txBlob      = serializer()      ?: return null
        val tranID      = transactionID()   ?: return null
        return Result(txBlob, tranID)
    }

    fun serializer(hashPrefix: XRPHashPrefix? = null): ByteArray? {
        var acc = ACTAddress(account, ACTNetwork(ACTCoin.Ripple, false)).raw()      ?: return null
        var des = ACTAddress(destination, ACTNetwork(ACTCoin.Ripple, false)).raw()  ?: return null
        acc     = acc.dropFirst()
        des     = des.dropFirst()
        var data    = byteArrayOf()
        // Prefix
        if (hashPrefix != null) {
            data += hashPrefix!!.value
        }
        // TransactionType
        data        += XRPEnums.TransactionType.value
        data        += XRPTransactionType.Payment.value
        // Flags
        data        += XRPEnums.Flags.value
        data        += flags.bigENDIAN()
        // Sequence
        data        += XRPEnums.Sequence.value
        data        += sequence.bigENDIAN()
        // DestinationTag
        if (memo != null && memo?.destinationTag != null) {
            val tag = memo!!.destinationTag!!
            data    += XRPMemoEnum.DestinationTag.value
            data    += tag.toInt().bigENDIAN()
        }
        // LastLedgerSequence
        data        += XRPEnums.ReserveIncrement.value
        data        += XRPEnums.LastLedgerSequence.value
        data        += lastLedgerSeq.bigENDIAN()
        // Amount
        data        += XRPEnums.Amount.value
        data        += convert(amount)
        // Fee
        data        += XRPEnums.Fee.value
        data        += convert(fee)
        // SigningPubKey
        if (signingPubKey != null) {
            data    += XRPEnums.SigningPubKey.value
            data    += signingPubKey!!.count().toByte()
            data    += signingPubKey!!
        }
        // TxnSignature
        if (txnSignature != null) {
            data    += XRPEnums.TxnSignature.value
            data    += txnSignature!!.count().toByte()
            data    += txnSignature!!
        }
        // Account
        data    += XRPEnums.Account.value
        data    += acc.count().toByte()
        data    += acc
        // Destination
        data    += XRPEnums.Destination.value
        data    += des.count().toByte()
        data    += des
        // Memo
        if (memo != null && memo?.memo != null &&  memo?.memo!!.isNotEmpty()) {
            val bs  = memo?.memo!!.toByteArray()
            data    += XRPMemoEnum.Starts.value
            data    += XRPMemoEnum.Start.value
            data    += XRPMemoEnum.Data.value
            data    += bs.count().toByte()
            data    += bs
            data    += XRPMemoEnum.End.value
            data    += XRPMemoEnum.Ends.value
        }
        return data
    }

    private fun convert(amount: Double): ByteArray {
        var a = amount.int64Bytes()
        a[0] = 0x40
        return a
    }
}