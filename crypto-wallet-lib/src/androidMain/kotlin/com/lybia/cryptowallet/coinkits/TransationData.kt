package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.coinkits.bitcoin.helper.BTCCoin
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCTransactionData
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCTransactionInOutData
import com.lybia.cryptowallet.coinkits.cardano.helpers.ADACoin
import com.lybia.cryptowallet.coinkits.cardano.networking.models.ADATransaction
import com.lybia.cryptowallet.coinkits.cardano.networking.models.ADATransactionInOut
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray
import com.lybia.cryptowallet.coinkits.ripple.model.XRPCoin
import com.lybia.cryptowallet.coinkits.ripple.model.XRPTransactionItem
import java.io.Serializable
import java.util.*

class TransationData : Serializable {
    var amount          : Float = 0.0f
    var fee             : Float = 0.0f
    var iD              : String = ""
    var fromAddress     : String = ""
    var toAddress       : String = ""
    var date            : Date = Date()
    var coin            : ACTCoin = ACTCoin.Bitcoin
    var isSend          = false
    var memoNetwork     : MemoData? = null
}

/*
* For Bitcoin
*/

fun Array<BTCTransactionInOutData>.exclude(addresses: Array<String>): Array<BTCTransactionInOutData> {
    val converted = addresses.map { it.lowercase(Locale.getDefault()) }
    return filter { !converted.contains(it.address.lowercase(Locale.getDefault()))}.toTypedArray()
}

fun Array<BTCTransactionInOutData>.filter(addresses: Array<String>): Array<BTCTransactionInOutData> {
    val converted = addresses.map { it.lowercase(Locale.getDefault()) }
    return filter { converted.contains(it.address.lowercase(Locale.getDefault()))}.toTypedArray()
}

fun Array<BTCTransactionData>.toTransactionDatas(addresses: Array<String>): Array<TransationData> {
    return map { it.toTransactionData(addresses) }.sortedByDescending { it.date }.toTypedArray()
}

fun BTCTransactionData.toTransactionData(addresses: Array<String>): TransationData {
    val result          = TransationData()
    result.iD           = hashString
    val iPs             = inPuts.map{ it.address }.distinct()
    val oPs             = outPuts.map{ it.address }.distinct()
    result.fromAddress  = iPs.joinToString(separator = "\n")
    result.toAddress    = oPs.joinToString(separator = "\n")
    result.date         = timeCreate
    result.amount       = amount/ BTCCoin
    result.fee          = fee   / BTCCoin
    result.coin         = ACTCoin.Bitcoin
    result.isSend       = addresses.filter { result.fromAddress.contains(it, ignoreCase = true)}.isNotEmpty()
    return result
}

/*
* For Cardano
*/

fun Array<ADATransactionInOut>.exclude(addresses: Array<String>): Array<ADATransactionInOut> {
    val converted = addresses.map { it.lowercase(Locale.getDefault()) }
    return filter { !converted.contains(it.address.lowercase(Locale.getDefault()))}.toTypedArray()
}

fun Array<ADATransactionInOut>.filterAddress(addresses: Array<String>): Array<ADATransactionInOut> {
    val converted = addresses.map { it.lowercase(Locale.getDefault()) }
    return filter { converted.contains(it.address.lowercase(Locale.getDefault()))}.toTypedArray()
}

fun Array<ADATransaction>.toTransactionDatas(addresses: Array<String>): Array<TransationData> {
    return filter { !it.hasCertificates()}.map { it.toTransactionData(addresses) }.sortedByDescending { it.date }.toTypedArray()
}

fun ADATransaction.toTransactionData(addresses: Array<String>): TransationData {
    val result          = TransationData()
    result.iD           = transactionID
    val iPs             = inputs.map{ it.address }.distinct()
    val oPs             = outputs.map{ it.address }.distinct()
    result.fromAddress  = iPs.joinToString(separator = "\n")
    result.toAddress    = oPs.joinToString(separator = "\n")
    result.date         = time()
    result.amount       = (amount/ ADACoin).toFloat()
    result.fee          = (fee   / ADACoin).toFloat()
    result.coin         = ACTCoin.Cardano
    result.isSend       = addresses.filter { result.fromAddress.contains(it, ignoreCase = true)}.isNotEmpty()
    return result
}

/*
* For Ripple
 */

fun Array<XRPTransactionItem>.toTransactionDatas(address: String): Array<TransationData> {
    return map { it.toTransactionData(address) }.sortedByDescending { it.date }.toTypedArray()
}

fun XRPTransactionItem.toTransactionData(address: String): TransationData {
    val tran = TransationData()
    tran.amount = tx!!.amount / XRPCoin
    tran.fee = tx!!.fee / XRPCoin
    tran.iD = tx!!.hash
    tran.fromAddress = tx!!.account
    tran.toAddress = tx!!.destination ?: ""

    tran.date = Date((tx!!.date +  946684800) * 1000)
    tran.coin = ACTCoin.Ripple
    tran.isSend =
        tran.fromAddress.lowercase(Locale.getDefault()) == address.lowercase(Locale.getDefault())
    try {
        val memoNetwork = MemoData("", null)
        var memoText: String? = null
        var destinationTag: UInt? = null
        if (!tx!!.memos.isNullOrEmpty()) {
            val memo = tx!!.memos.first()
            memoText = memo.memoData.fromHexToByteArray().toString(Charsets.UTF_8)
            memoNetwork.memo = memoText
        }

        if (!tx!!.destinationTag.isNullOrEmpty()) {
            destinationTag = tx!!.destinationTag.toUIntOrNull() ?: null
            memoNetwork.destinationTag = destinationTag
        }

        if ((memoText != null && memoText.isNotEmpty()) || (destinationTag != null && destinationTag > 0u)) {
            tran.memoNetwork = memoNetwork
        }

    } catch (e: NoSuchElementException) {
    }
    return tran
}

/* END */