package com.lybia.cryptowallet.wallets.cardano

import org.ton.kotlin.crypto.PrivateKeyEd25519

/**
 * Ed25519 helper for Cardano key operations.
 *
 * Delegates to ton-kotlin's Ed25519 implementation which is available
 * across all KMP targets (Android, iOS, JVM).
 */
internal object Ed25519 {

    /**
     * Compute Ed25519 public key from a 32-byte private key seed.
     * @return 32-byte public key
     */
    fun publicKey(seed: ByteArray): ByteArray {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val privateKey = PrivateKeyEd25519(seed)
        return privateKey.publicKey().key.toByteArray()
    }

    /**
     * Sign a message with Ed25519.
     * @param seed 32-byte private key seed
     * @param message message bytes to sign
     * @return 64-byte Ed25519 signature
     */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val privateKey = PrivateKeyEd25519(seed)
        return privateKey.signToByteArray(message)
    }
}
