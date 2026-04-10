package com.lybia.cryptowallet.utils

import fr.acinq.bitcoin.Crypto

/**
 * PBKDF2-HMAC-SHA512 (RFC 2898) computed on raw bytes.
 *
 * Why this exists — bitcoin-kmp 0.30.0 bug on JVM/Android:
 * `fr.acinq.bitcoin.crypto.Pbkdf2.withHmacSha512` routes the password through
 * `PBEKeySpec(CharArray(password.size) { password[it].toInt().toChar() }, ...)`.
 * Because Kotlin's `Byte.toInt()` sign-extends, any byte ≥ 0x80 turns into a
 * Char in `U+FF80..U+FFFF`; SunJCE's `PBKDF2WithHmacSHA512` then UTF-8 encodes
 * that CharArray, emitting 3 garbage bytes per non-ASCII byte. For English
 * mnemonics every byte is < 0x80, so the transform is an identity and the bug
 * is invisible. For Japanese / Chinese / Korean / accented Latin mnemonics
 * the seed is corrupted and the derived address diverges from iOS ton-swift,
 * Tonkeeper, Ledger and every BIP-39 reference vector.
 *
 * This implementation stays in commonMain and delegates to [Crypto.hmac512],
 * which accepts a raw ByteArray key on every target — no Char conversion
 * anywhere in the pipeline. It is byte-identical to RFC 2898 PBKDF2-HMAC-SHA512
 * for all inputs, ASCII or not.
 *
 * @param password   raw password bytes; caller owns encoding (UTF-8 NFKD for BIP-39).
 * @param salt       raw salt bytes.
 * @param iterations iteration count (BIP-39 uses 2048).
 * @param dkLen      desired output length in bytes (BIP-39 uses 64).
 */
internal fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    dkLen: Int
): ByteArray {
    require(iterations > 0) { "iterations must be > 0" }
    require(dkLen > 0) { "dkLen must be > 0" }

    val hLen = 64 // HMAC-SHA512 output length
    val blocks = (dkLen + hLen - 1) / hLen
    val out = ByteArray(dkLen)

    for (i in 1..blocks) {
        // Block input: Salt || INT(i) where INT(i) is a 4-byte big-endian counter.
        val blockInput = ByteArray(salt.size + 4)
        salt.copyInto(blockInput)
        blockInput[salt.size]     = (i ushr 24).toByte()
        blockInput[salt.size + 1] = (i ushr 16).toByte()
        blockInput[salt.size + 2] = (i ushr 8).toByte()
        blockInput[salt.size + 3] = i.toByte()

        // U_1 = PRF(password, Salt || INT(i)); T_i starts as U_1.
        var u = Crypto.hmac512(password, blockInput)
        val t = u.copyOf()

        // T_i ^= U_c for c = 2..iterations, where U_c = PRF(password, U_{c-1}).
        for (c in 2..iterations) {
            u = Crypto.hmac512(password, u)
            for (j in 0 until hLen) {
                t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
        }

        val offset = (i - 1) * hLen
        val copyLen = minOf(hLen, dkLen - offset)
        t.copyInto(out, offset, 0, copyLen)
    }
    return out
}
