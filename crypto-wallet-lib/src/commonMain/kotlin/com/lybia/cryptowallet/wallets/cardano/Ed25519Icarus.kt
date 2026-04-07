package com.lybia.cryptowallet.wallets.cardano

/**
 * Ed25519 public key derivation and signing for Icarus (ed25519-bip32) extended keys.
 *
 * Unlike standard RFC 8032 Ed25519, Icarus extended keys carry a PRE-CLAMPED
 * 32-byte scalar (kL) — the SHA-512 expansion step is intentionally skipped.
 *
 *   Standard RFC 8032 : A = clamp(SHA-512(seed)[0..31]) * B
 *   Icarus ed25519-bip32: A = kL * B          ← kL already clamped at master-key step
 *
 * ⚠️  Do NOT use this for standard Ed25519 seeds (TON / Shelley).
 *     For those, use Ed25519.kt (ton-kotlin wrapper).
 *
 * Platform implementations:
 *  - Android/JVM: java.math.BigInteger (native, crash-free)
 *  - iOS: com.ionspin.kotlin.bignum
 */
internal expect object Ed25519Icarus {
    /**
     * Compute Ed25519 public key A = scalar * B from a pre-clamped 32-byte scalar.
     *
     * @param scalar32 32-byte little-endian pre-clamped scalar (kL from Icarus extended key)
     * @return 32-byte compressed Ed25519 public key
     */
    fun publicKeyFromScalar(scalar32: ByteArray): ByteArray

    /**
     * Sign a message using the Icarus ed25519-bip32 signing algorithm.
     *
     * @param extKey64 64-byte Icarus extended key (kL[32] ‖ kR[32])
     * @param message  arbitrary-length message to sign (typically 32-byte tx hash)
     * @return 64-byte signature
     */
    fun sign(extKey64: ByteArray, message: ByteArray): ByteArray
}
