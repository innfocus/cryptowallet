package com.lybia.cryptowallet.models

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.bitcoin.BTCApiModel
import com.lybia.cryptowallet.models.cardano.CardanoTransactionInfo
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.wallets.centrality.CentralityManager
import com.lybia.cryptowallet.wallets.centrality.model.CennzTransfer
import kotlin.jvm.JvmName

/**
 * KMP-compatible extension functions for converting chain-specific transaction models
 * to the unified TransationData model.
 *
 * Replaces androidMain version: java.util.Date → epoch millis, java.util.Locale → Kotlin lowercase().
 */

private const val BTC_UNIT = 100_000_000f
private const val ADA_UNIT = 1_000_000f
private const val TON_UNIT = 1_000_000_000f
private const val CENNZ_UNIT = CentralityManager.BASE_UNIT.toFloat()

// ─── Bitcoin ────────────────────────────────────────────────────────

@JvmName("btcToTransactionDatas")
fun List<BTCApiModel.Tx>.toTransactionDatas(addresses: List<String>): List<TransationData> {
    return map { it.toTransactionData(addresses) }.sortedByDescending { it.dateMillis }
}

fun BTCApiModel.Tx.toTransactionData(addresses: List<String>): TransationData {
    val result = TransationData()
    result.iD = hash
    val inputAddresses = inputs.flatMap { it.addresses }.distinct()
    val outputAddresses = outputs.flatMap { it.addresses }.distinct()
    result.fromAddress = inputAddresses.joinToString(separator = "\n")
    result.toAddress = outputAddresses.joinToString(separator = "\n")
    // Parse ISO 8601 confirmed timestamp to epoch millis
    result.dateMillis = parseIso8601ToMillis(confirmed)
    result.amount = total.toFloat() / BTC_UNIT
    result.fee = fees.toFloat() / BTC_UNIT
    result.coin = ACTCoin.Bitcoin
    result.isSend = addresses.any { addr ->
        result.fromAddress.contains(addr, ignoreCase = true)
    }
    return result
}

// ─── Cardano ────────────────────────────────────────────────────────

@JvmName("cardanoToTransactionDatas")
fun List<CardanoTransactionInfo>.toTransactionDatas(addresses: List<String>): List<TransationData> {
    return map { it.toTransactionData(addresses) }.sortedByDescending { it.dateMillis }
}

fun CardanoTransactionInfo.toTransactionData(addresses: List<String>): TransationData {
    val result = TransationData()
    result.iD = txHash
    val inputAddrs = inputs.map { it.address }.distinct()
    val outputAddrs = outputs.map { it.address }.distinct()
    result.fromAddress = inputAddrs.joinToString(separator = "\n")
    result.toAddress = outputAddrs.joinToString(separator = "\n")
    // blockTime is already epoch seconds from Blockfrost
    result.dateMillis = blockTime * 1000L
    // Sum lovelace amounts from inputs/outputs
    val totalLovelace = outputs.flatMap { it.amount }
        .filter { it.unit == "lovelace" }
        .sumOf { it.quantity.toLongOrNull() ?: 0L }
    result.amount = (totalLovelace.toFloat() / ADA_UNIT)
    result.fee = ((fees.toLongOrNull() ?: 0L).toFloat() / ADA_UNIT)
    result.coin = ACTCoin.Cardano
    result.isSend = addresses.any { addr ->
        result.fromAddress.contains(addr, ignoreCase = true)
    }
    return result
}

// ─── TON ────────────────────────────────────────────────────────────

@JvmName("tonToTransactionDatas")
fun List<TonTransaction>.toTransactionDatas(myAddress: String): List<TransationData> {
    return map { it.toTransactionData(myAddress) }.sortedByDescending { it.dateMillis }
}

fun TonTransaction.toTransactionData(myAddress: String): TransationData {
    val tran = TransationData()

    tran.iD = transactionId.hash
    tran.dateMillis = utime * 1000L
    tran.fee = (fee.toLongOrNull() ?: 0L).toFloat() / TON_UNIT
    tran.coin = ACTCoin.TON

    val isSend = out_msgs?.isNotEmpty() == true
    tran.isSend = isSend

    if (isSend) {
        val outMsg = out_msgs!!.first()
        tran.fromAddress = myAddress
        tran.toAddress = outMsg.destination
        tran.amount = (outMsg.value.toLongOrNull() ?: 0L).toFloat() / TON_UNIT
        outMsg.message?.takeIf { it.isNotEmpty() }?.let { tran.memoNetwork = MemoData(it, null) }
    } else {
        val inMsg = in_msg
        tran.fromAddress = inMsg?.source ?: ""
        tran.toAddress = myAddress
        tran.amount = (inMsg?.value?.toLongOrNull() ?: 0L).toFloat() / TON_UNIT
        inMsg?.message?.takeIf { it.isNotEmpty() }?.let { tran.memoNetwork = MemoData(it, null) }
    }

    return tran
}

// ─── Centrality (CENNZ / CPAY) ─────────────────────────────────────

@JvmName("cennzToTransactionDatas")
fun List<CennzTransfer>.toTransactionDatas(myAddress: String, coin: ACTCoin = ACTCoin.Centrality): List<TransationData> {
    return map { it.toTransactionData(myAddress, coin) }.sortedByDescending { it.dateMillis }
}

fun CennzTransfer.toTransactionData(myAddress: String, coin: ACTCoin = ACTCoin.Centrality): TransationData {
    val result = TransationData()
    result.iD = hash
    result.fromAddress = from
    result.toAddress = to
    result.dateMillis = blockTimestamp * 1000L
    result.amount = amount.toFloat() / CENNZ_UNIT
    result.coin = coin
    result.isSend = from.equals(myAddress, ignoreCase = true)
    return result
}

// ─── Utility ────────────────────────────────────────────────────────

/**
 * Parse ISO 8601 date string to epoch millis.
 * Handles formats like "2024-01-15T10:30:00Z" and "2024-01-15T10:30:00.000Z".
 * Returns 0L if parsing fails.
 */
internal fun parseIso8601ToMillis(dateStr: String): Long {
    return try {
        // Strip trailing Z and split
        val cleaned = dateStr.trimEnd('Z', 'z')
        val parts = cleaned.split("T")
        if (parts.size != 2) return 0L

        val dateParts = parts[0].split("-")
        if (dateParts.size != 3) return 0L
        val year = dateParts[0].toInt()
        val month = dateParts[1].toInt()
        val day = dateParts[2].toInt()

        val timePart = parts[1].split(".")
        val timeParts = timePart[0].split(":")
        if (timeParts.size != 3) return 0L
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val second = timeParts[2].toInt()

        // Calculate days from epoch (1970-01-01)
        val daysFromEpoch = daysSinceEpoch(year, month, day)
        val secondsFromEpoch = daysFromEpoch * 86400L + hour * 3600L + minute * 60L + second
        secondsFromEpoch * 1000L
    } catch (_: Exception) {
        0L
    }
}

private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
    // Days in each month (non-leap)
    val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    var totalDays = 0L
    // Add days for complete years
    for (y in 1970 until year) {
        totalDays += if (isLeapYear(y)) 366 else 365
    }
    // Add days for complete months in current year
    for (m in 1 until month) {
        totalDays += daysInMonth[m]
        if (m == 2 && isLeapYear(year)) totalDays += 1
    }
    // Add remaining days
    totalDays += day - 1
    return totalDays
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
