package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers

import org.spongycastle.crypto.digests.Blake2bDigest
import org.spongycastle.jcajce.provider.digest.SHA3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.Normalizer
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.zip.CRC32


private  class  StringExtension {
    companion object {
        fun DIGITS_LOWER() = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
        fun DIGITS_UPPER() = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    }
}

fun String.suffix(length: Int): String {
    val startIdx = kotlin.math.max(count() - length, 0)
    val endIdx = count()
    return  substring(startIdx, endIdx)
}

fun String.prefix(length: Int): String {
    val endIdx = kotlin.math.min(length, count())
    return  substring(0, endIdx)
}

fun String.fromHexToByteArray(): ByteArray{
    val ns = length / 2
    val rs = ByteArray(ns)
    if (ns * 2 == length) {
        for (i in 0 until ns) {
            val cl = get(i*2).uppercaseChar()
            val cr = get(i*2 + 1).uppercaseChar()
            if (StringExtension.DIGITS_UPPER().contains(cl) && StringExtension.DIGITS_UPPER().contains(cr)) {
                val l = StringExtension.DIGITS_UPPER().indexOf(cl) shl 4
                val r = StringExtension.DIGITS_UPPER().indexOf(cr)
                rs[i] = (l or r).toByte()
            }else{
                return ByteArray(0)
            }
        }
    }
    return rs
}

fun String.normalized(): ByteArray {
    return Normalizer.normalize(this, Normalizer.Form.NFKD).toByteArray()
}

fun String.toDate(dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"): Date {
    return try {
        val format      = SimpleDateFormat(dateFormat)
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.parse(this)
    }catch (e: ParseException) {
        Date()
    }catch (e: NullPointerException) {
        Date()
    }catch (e: IllegalArgumentException) {
        Date()
    }
}

/*
* ByteArray extensions
*/
fun ByteArray.dropFirst(): ByteArray {
    return when(size > 0) {
        true    -> copyOfRange(1, size)
        false   -> this
    }
}

fun ByteArray.suffix(length: Int): ByteArray {
    val startIdx = kotlin.math.max(size - length, 0)
    val endIdx = size
    return copyOfRange(startIdx, endIdx)
}

fun ByteArray.prefix(length: Int): ByteArray {
    val endIdx = kotlin.math.min(length, size)
    return  copyOfRange(0, endIdx)
}

fun ByteArray.toHexString(): String {
    var hexString = ""
    for (i in 0 until size) {
        val current = get(i).toInt() and 0xFF
        val l = current shr 4
        val r = current and 0x0f
        hexString += StringExtension.DIGITS_LOWER()[l]
        hexString += StringExtension.DIGITS_LOWER()[r]
    }
    return hexString
}

fun ByteArray.sha256(): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(this)
    return md.digest()
}

fun ByteArray.sha512(): ByteArray {
    val md = MessageDigest.getInstance("SHA-512")
    md.update(this)
    return md.digest()
}

fun ByteArray.sha3256(): ByteArray {
    val sha3256 = SHA3.Digest256()
    sha3256.update(this)
    return  sha3256.digest()
}

fun ByteArray.blake2b(outLength: Int): ByteArray {
    val b       = Blake2bDigest(null, outLength, null, null)
    b.update(this, 0, size)
    val hash    = ByteArray(outLength)
    b.doFinal(hash, 0)
    return hash
}

fun ByteArray.crc32(): Long {
    val checksum = CRC32()
    checksum.update(this)
    return  checksum.value
}

fun ByteArray.toBitsString(): String {
    return map { ("00000000" + (it.toInt() and 0xFF).toString(2)).suffix(8) }.joinToString(separator = "")
}

fun ByteArray.toCharArray(): CharArray {
    val rs = CharArray(size)
    for (i in 0 until size) {
        rs[i] = get(i).toInt().toChar()
    }
    return rs
}

fun ByteArray.littleENDIAN(): Int {
    val bb = ByteBuffer.wrap(this)
    bb.order(ByteOrder.LITTLE_ENDIAN)
    return bb.int
}

fun ByteArray.bigENDIAN(): Int {
    val bb = ByteBuffer.wrap(this)
    bb.order(ByteOrder.BIG_ENDIAN)
    return bb.int
}

/*
* CharArray extensions
*/

fun CharArray.toHexString(): String {
    return toByteArray().toHexString()
}

fun CharArray.toByteArray(): ByteArray {
    val rs = ByteArray(size)
    for (i in 0 until size) {
        rs[i] = get(i).toByte()
    }
    return rs
}

fun CharArray.toBitsString(): String {
    return toByteArray().toBitsString()
}

/*
* Int extensions
*/

fun Int.littleENDIAN(): ByteArray {
    val bb = ByteBuffer.allocate(4)
    bb.order(ByteOrder.LITTLE_ENDIAN)
    bb.putInt(this)
    return bb.array()
}

fun Int.bigENDIAN(): ByteArray {
    val bb = ByteBuffer.allocate(4)
    bb.order(ByteOrder.BIG_ENDIAN)
    bb.putInt(this)
    return bb.array()
}

fun Int.int32Bytes(): ByteArray {
    val bb = ByteBuffer.allocate(4)
    bb.putInt(this)
    return bb.array()
}

fun Double.int64Bytes(): ByteArray {
    val bb = ByteBuffer.allocate(8)
    bb.order(ByteOrder.BIG_ENDIAN)
    bb.putLong(this.toLong())
    return bb.array()
}

/*
* Byte extensions
*/

fun Byte.littleENDIAN(): ByteArray {
    val bb = ByteBuffer.allocate(1)
    bb.order(ByteOrder.LITTLE_ENDIAN)
    bb.put(this)
    return bb.array()
}

/*
* Date extensions
*/

fun Date.toDateString(dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"): String {
    return try {
        val format      = SimpleDateFormat(dateFormat)
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.format(this)
    }catch (e: NullPointerException) {
        ""
    }catch (e: IllegalArgumentException) {
        ""
    }
}