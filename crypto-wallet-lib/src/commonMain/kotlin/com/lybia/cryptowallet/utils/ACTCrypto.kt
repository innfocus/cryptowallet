package com.lybia.cryptowallet.utils

import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.secp256k1.Secp256k1
import korlibs.crypto.HMAC

/**
 * KMP-compatible crypto functions replacing androidMain ACTCryto (Spongy Castle).
 * Uses bitcoin-kmp and krypto for all operations.
 */
object ACTCrypto {

    /** HMAC-SHA512 using krypto */
    fun hmacSHA512(key: ByteArray, data: ByteArray): ByteArray {
        return HMAC.hmacSHA512(key, data).bytes
    }

    /** PBKDF2-HMAC-SHA512 — pure Kotlin implementation */
    fun pbkdf2SHA512(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = 2048,
        keyLength: Int = 64
    ): ByteArray {
        val hLen = 64
        val dkLen = keyLength
        val numBlocks = (dkLen + hLen - 1) / hLen
        val result = ByteArray(dkLen)
        for (blockIndex in 1..numBlocks) {
            val blockBytes = byteArrayOf(
                ((blockIndex shr 24) and 0xFF).toByte(),
                ((blockIndex shr 16) and 0xFF).toByte(),
                ((blockIndex shr 8) and 0xFF).toByte(),
                (blockIndex and 0xFF).toByte()
            )
            var u = HMAC.hmacSHA512(password, salt + blockBytes).bytes
            val t = u.copyOf()
            for (iter in 2..iterations) {
                u = HMAC.hmacSHA512(password, u).bytes
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            val offset = (blockIndex - 1) * hLen
            val len = minOf(hLen, dkLen - offset)
            t.copyInto(result, offset, 0, len)
        }
        return result
    }

    /** SHA256 + RIPEMD160 (Hash160) — returns ByteArray */
    fun sha256ripemd160(data: ByteArray): ByteArray = Crypto.hash160(data)

    /** Double SHA256 — returns ByteArray */
    fun doubleSHA256(data: ByteArray): ByteArray = Crypto.hash256(data)

    /** Keccak-256 hash (Ethereum's "SHA3") */
    fun hashSHA3256(data: ByteArray): ByteArray = Keccak.keccak256(data)

    /** Generate compressed or uncompressed public key from private key bytes */
    fun generatePublicKey(priKey: ByteArray, compressed: Boolean): ByteArray {
        // Use secp256k1 directly to generate public key
        val pubKey = Secp256k1.pubkeyCreate(priKey)
        return if (compressed) Secp256k1.pubKeyCompress(pubKey) else pubKey
    }

    /** Convert compressed public key to uncompressed */
    fun convertToUncompressed(publicKey: ByteArray): ByteArray {
        return Secp256k1.pubkeyParse(publicKey)
    }

    /** Sign hash and serialize to DER format */
    fun signSerializeDER(sighash: ByteArray, privateKey: ByteArray): ByteArray? {
        val pk = PrivateKey(privateKey)
        val data = Crypto.sha256(sighash)
        val sig = Crypto.sign(data, pk)
        // Convert compact signature (64 bytes) to DER format
        return Secp256k1.compact2der(sig.toByteArray())
    }
}
