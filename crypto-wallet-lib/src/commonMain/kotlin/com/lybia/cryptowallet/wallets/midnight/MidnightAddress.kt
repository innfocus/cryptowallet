package com.lybia.cryptowallet.wallets.midnight

import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import com.lybia.cryptowallet.utils.Bech32
import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.wallets.cardano.Ed25519
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MnemonicCode

/**
 * Midnight address generation from mnemonic.
 *
 * Uses SLIP-0010 Ed25519 derivation with path m/1852'/1815'/0'/0'/0'
 * (same structure as Cardano CIP-1852 but encoded as Midnight Bech32 address).
 *
 * Address format: Bech32 with "midnight" prefix + Blake2b-224 hash of public key.
 */
object MidnightAddress {

    private const val BECH32_PREFIX = "midnight"

    /**
     * Derive a Midnight address from a BIP-39 mnemonic phrase.
     *
     * @param mnemonic Space-separated BIP-39 mnemonic words
     * @return Bech32-encoded Midnight address with "midnight" prefix
     */
    fun fromMnemonic(mnemonic: String): String {
        val words = Bip39Language.splitMnemonic(mnemonic)
        val seed = MnemonicCode.toSeed(words, "")
        val (privateKey, _) = slip10DeriveEd25519(seed, intArrayOf(
            hardenedIndex(1852),
            hardenedIndex(1815),
            hardenedIndex(0),
            hardenedIndex(0),
            hardenedIndex(0)
        ))
        val publicKey = Ed25519.publicKey(privateKey)
        val keyHash = Blake2b.hash(publicKey, 28) // Blake2b-224
        val data5bit = Bech32.convertBits(keyHash, 8, 5, true)
        return Bech32.encode(BECH32_PREFIX, data5bit)
    }

    /**
     * Validate a Midnight address string.
     * @return true if valid Midnight address with "midnight" prefix
     */
    fun isValid(address: String): Boolean {
        return try {
            val (hrp, data5bit) = Bech32.decode(address)
            if (hrp != BECH32_PREFIX) return false
            val payload = Bech32.convertBits(data5bit, 5, 8, false)
            payload.size == 28 // Blake2b-224 hash = 28 bytes
        } catch (_: Exception) {
            false
        }
    }

    // ── SLIP-0010 Ed25519 key derivation (hardened only) ────────────────────

    private fun hardenedIndex(i: Int): Int = 0x80000000.toInt() or i

    /**
     * SLIP-0010 Ed25519 key derivation.
     * Returns (privateKey 32 bytes, chainCode 32 bytes).
     */
    internal fun slip10DeriveEd25519(seed: ByteArray, path: IntArray): Pair<ByteArray, ByteArray> {
        var iBytes = Crypto.hmac512("ed25519 seed".encodeToByteArray(), seed)
        var kL = iBytes.sliceArray(0 until 32)
        var kR = iBytes.sliceArray(32 until 64)

        for (index in path) {
            val data = ByteArray(37)
            data[0] = 0x00
            kL.copyInto(data, 1)
            data[33] = (index ushr 24).toByte()
            data[34] = (index ushr 16).toByte()
            data[35] = (index ushr 8).toByte()
            data[36] = index.toByte()
            iBytes = Crypto.hmac512(kR, data)
            kL = iBytes.sliceArray(0 until 32)
            kR = iBytes.sliceArray(32 until 64)
        }
        return kL to kR
    }
}
