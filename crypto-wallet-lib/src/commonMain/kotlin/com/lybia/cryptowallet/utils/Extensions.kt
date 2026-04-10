package com.lybia.cryptowallet.utils

/**
 * KMP-compatible extensions migrated from androidMain.
 * Replaces java.security.MessageDigest, java.nio.ByteBuffer, java.text.Normalizer, etc.
 */

private val DIGITS_LOWER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
private val DIGITS_UPPER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

// ── String extensions ──────────────────────────────────────────────

fun String.suffix(length: Int): String {
    val startIdx = maxOf(this.length - length, 0)
    return substring(startIdx)
}

fun String.prefix(length: Int): String {
    val endIdx = minOf(length, this.length)
    return substring(0, endIdx)
}

fun String.fromHexToByteArray(): ByteArray {
    val ns = length / 2
    val rs = ByteArray(ns)
    if (ns * 2 == length) {
        for (i in 0 until ns) {
            val cl = get(i * 2).uppercaseChar()
            val cr = get(i * 2 + 1).uppercaseChar()
            if (DIGITS_UPPER.contains(cl) && DIGITS_UPPER.contains(cr)) {
                val l = DIGITS_UPPER.indexOf(cl) shl 4
                val r = DIGITS_UPPER.indexOf(cr)
                rs[i] = (l or r).toByte()
            } else {
                return ByteArray(0)
            }
        }
    }
    return rs
}

/**
 * NFKD normalization to UTF-8 bytes.
 *
 * Required by BIP-39 before PBKDF2 — without this, CJK mnemonics (esp.
 * Japanese with dakuten like げ, べ, で) break cross-platform because iOS
 * and Android may deliver the same characters in different normalization
 * forms (NFC vs NFD). Uses the platform NFKD implementation via
 * [String.nfkd] expect/actual.
 */
fun String.normalized(): ByteArray = this.nfkd().encodeToByteArray()

// ── ByteArray extensions ───────────────────────────────────────────

fun ByteArray.dropFirst(): ByteArray {
    return if (isNotEmpty()) copyOfRange(1, size) else this
}

fun ByteArray.suffix(length: Int): ByteArray {
    val startIdx = maxOf(size - length, 0)
    return copyOfRange(startIdx, size)
}

fun ByteArray.prefix(length: Int): ByteArray {
    val endIdx = minOf(length, size)
    return copyOfRange(0, endIdx)
}

fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(DIGITS_LOWER[v shr 4])
        sb.append(DIGITS_LOWER[v and 0x0f])
    }
    return sb.toString()
}

fun ByteArray.sha256(): ByteArray {
    return fr.acinq.bitcoin.Crypto.sha256(this)
}

fun ByteArray.toBitsString(): String {
    return joinToString(separator = "") { byte ->
        val v = byte.toInt() and 0xFF
        v.toString(2).padStart(8, '0')
    }
}

// ── Int extensions (replace java.nio.ByteBuffer) ───────────────────

/** Big-endian 4-byte encoding (replaces Int.bigENDIAN()) */
fun Int.bigENDIAN(): ByteArray {
    return byteArrayOf(
        ((this shr 24) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte()
    )
}

/** Little-endian 4-byte encoding (replaces Int.littleENDIAN()) */
fun Int.littleENDIAN(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
}

/** Big-endian 4-byte encoding (same as bigENDIAN, replaces Int.int32Bytes()) */
fun Int.int32Bytes(): ByteArray = bigENDIAN()

// ── Byte extensions ────────────────────────────────────────────────

/** Little-endian 1-byte encoding (replaces Byte.littleENDIAN()) */
fun Byte.littleENDIAN(): ByteArray = byteArrayOf(this)

// ── ByteArray endian conversions ───────────────────────────────────

/** Read first 4 bytes as little-endian Int */
fun ByteArray.littleENDIAN(): Int {
    return (this[0].toInt() and 0xFF) or
            ((this[1].toInt() and 0xFF) shl 8) or
            ((this[2].toInt() and 0xFF) shl 16) or
            ((this[3].toInt() and 0xFF) shl 24)
}

/** Read first 4 bytes as big-endian Int */
fun ByteArray.bigENDIAN(): Int {
    return ((this[0].toInt() and 0xFF) shl 24) or
            ((this[1].toInt() and 0xFF) shl 16) or
            ((this[2].toInt() and 0xFF) shl 8) or
            (this[3].toInt() and 0xFF)
}
