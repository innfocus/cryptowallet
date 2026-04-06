package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.ACTCrypto
import fr.acinq.bitcoin.MnemonicCode

/**
 * Icarus key derivation (CIP-0003 / ed25519-bip32 V2).
 *
 * This is the key derivation scheme used by Yoroi, Daedalus (Icarus-compatible wallets)
 * for **both Byron AND Shelley** addresses. It is INCOMPATIBLE with SLIP-0010.
 *
 * ## Core differences from SLIP-0010
 *
 * | Aspect            | Icarus V2 (Byron + Shelley)              | SLIP-0010 (TON, Solana)       |
 * |-------------------|------------------------------------------|-------------------------------|
 * | Master input      | PBKDF2(password="", salt=entropy, 96B)   | HMAC("ed25519 seed", seed)    |
 * | Private key size  | 64 bytes (kL‖kR)                         | 32 bytes                      |
 * | Child derivation  | newKL = zL*8 + kL  (add, no replace)     | kL = HMAC(kR, [0\|\|kL\|\|i]) |
 * | Soft derivation   | Supported (role + index must be soft)    | Not supported                 |
 * | Public key        | Ed25519Icarus (scalar = kL, no SHA-512)  | Ed25519 RFC 8032 (SHA-512)    |
 *
 * ## Derivation path
 * ```
 * m / 44' / 1815' / 0' / 0 / index
 *      ↑     ↑      ↑   ↑    ↑
 *   BIP44  ADA   acct  role  address
 *          coin       (soft) (soft)
 * ```
 *
 * References:
 * - [CIP-0003 Icarus](https://github.com/cardano-foundation/CIPs/blob/master/CIP-0003/Icarus.md)
 * - [ed25519-bip32](https://input-output-hk.github.io/adrestia/cardano-wallet/concepts/master-key-generation)
 */
internal object IcarusKeyDerivation {

    // ── Master key ────────────────────────────────────────────────────────────

    /**
     * Derive Icarus master extended key from a BIP-39 mnemonic word list.
     *
     * @param mnemonicWords BIP-39 mnemonic words (12/15/18/21/24 words)
     * @return Pair(extKey[64], chainCode[32])
     */
    fun masterKeyFromMnemonic(mnemonicWords: List<String>): Pair<ByteArray, ByteArray> {
        val entropy = mnemonicToEntropy(mnemonicWords)
        return masterKeyFromEntropy(entropy)
    }

    /**
     * BIP-39 mnemonic → raw entropy bytes.
     *
     * bitcoin-kmp's MnemonicCode does not expose this, so we derive it here
     * using the english wordlist that is already bundled in MnemonicCode.
     */
    private fun mnemonicToEntropy(words: List<String>): ByteArray {
        val wordMap = MnemonicCode.englishWordlist.mapIndexed { i, w -> w to i }.toMap()
        require(words.all { it in wordMap }) { "Mnemonic contains unknown words" }
        require(words.size % 3 == 0) { "Mnemonic word count (${words.size}) must be a multiple of 3" }

        // Each word encodes 11 bits
        val allBits = words.flatMap { word ->
            val idx = wordMap.getValue(word)
            (10 downTo 0).map { bit -> (idx shr bit) and 1 != 0 }
        }

        val totalBits  = allBits.size      // = words.size * 11
        val entropyBits = totalBits * 32 / 33   // = words.size * (32/3) — rounds down correctly

        // Convert entropy bits to bytes
        val entropyBytes = allBits.take(entropyBits)
            .chunked(8)
            .map { group -> group.foldIndexed(0) { i, acc, bit -> if (bit) acc or (1 shl (7 - i)) else acc }.toByte() }
            .toByteArray()

        return entropyBytes
    }

    /**
     * Derive Icarus master extended key from raw entropy bytes.
     *
     * Algorithm:
     * 1. raw = PBKDF2-HMAC-SHA512(password="", salt=entropy, iter=4096, dkLen=96)
     * 2. Clamp: raw[0] &= 0xF8, raw[31] &= 0x1F, raw[31] |= 0x40
     * 3. extKey = raw[0..63], chainCode = raw[64..95]
     *
     * @param entropy BIP-39 entropy bytes
     * @return Pair(extKey[64], chainCode[32])
     */
    fun masterKeyFromEntropy(entropy: ByteArray): Pair<ByteArray, ByteArray> {
        val raw = ACTCrypto.pbkdf2SHA512(
            password   = byteArrayOf(),
            salt       = entropy,
            iterations = 4096,
            keyLength  = 96
        )
        // Clamp — standard Icarus cofactor + bit manipulation
        raw[0]  = (raw[0].toInt()  and 0xF8).toByte()   // clear bits 0,1,2
        raw[31] = (raw[31].toInt() and 0x1F).toByte()   // clear bits 5,6,7
        raw[31] = (raw[31].toInt() or  0x40).toByte()   // set bit 6

        return raw.copyOfRange(0, 64) to raw.copyOfRange(64, 96)
    }

    // ── Child key derivation ──────────────────────────────────────────────────

    /**
     * Derive one level of child key using Icarus V2 scheme.
     *
     * Hardened (index or index+0x80000000):
     *   Z   = HMAC-SHA512(cc, [0x00 || extKey[64] || indexLE[4]])
     *   newCC_raw = HMAC-SHA512(cc, [0x01 || extKey[64] || indexLE[4]])
     *
     * Soft (non-hardened):
     *   Z   = HMAC-SHA512(cc, [0x02 || pubKey[32] || indexLE[4]])
     *   newCC_raw = HMAC-SHA512(cc, [0x03 || pubKey[32] || indexLE[4]])
     *
     * newKL = Z[0..27]*8 + parent kL   (little-endian 256-bit, no modular reduction)
     * newKR = Z[32..63]  + parent kR   (little-endian 256-bit)
     * newCC = newCC_raw[32..63]
     *
     * @param extKey   64-byte extended private key (kL[32] ‖ kR[32])
     * @param chainCode 32-byte chain code
     * @param index    derivation index (without hardened bit — e.g., 44, not 0x80000044)
     * @param hardened true for hardened ('') derivation
     * @return Pair(newExtKey[64], newChainCode[32])
     */
    fun deriveChildKey(
        extKey: ByteArray,
        chainCode: ByteArray,
        index: Int,
        hardened: Boolean
    ): Pair<ByteArray, ByteArray> {
        require(extKey.size == 64)    { "extKey must be 64 bytes, got ${extKey.size}" }
        require(chainCode.size == 32) { "chainCode must be 32 bytes, got ${chainCode.size}" }

        val edge      = 0x80000000.toInt()
        val fullIndex = if (hardened) (edge or index) else index
        val indexLE   = intToLE4(fullIndex)

        val tagZ:  Byte
        val tagCC: Byte
        val keyMaterial: ByteArray
        if (hardened) {
            tagZ        = 0x00
            tagCC       = 0x01
            keyMaterial = extKey
        } else {
            tagZ        = 0x02
            tagCC       = 0x03
            keyMaterial = publicKeyFromExtended(extKey)
        }

        val z     = ACTCrypto.hmacSHA512(chainCode, byteArrayOf(tagZ)  + keyMaterial + indexLE)
        val newCC = ACTCrypto.hmacSHA512(chainCode, byteArrayOf(tagCC) + keyMaterial + indexLE)
            .copyOfRange(32, 64)

        val newKL = addScalarsLE(multiply8LE(z.copyOfRange(0, 28)), extKey.copyOfRange(0, 32))
        val newKR = addScalarsLE(z.copyOfRange(32, 64),             extKey.copyOfRange(32, 64))

        return (newKL + newKR) to newCC
    }

    /**
     * Compute Ed25519 public key from a 64-byte Icarus extended key.
     * Uses kL[0..31] directly as the pre-clamped scalar — NO SHA-512 hashing.
     *
     * @param extKey 64-byte Icarus extended private key
     * @return 32-byte Ed25519 public key
     */
    fun publicKeyFromExtended(extKey: ByteArray): ByteArray {
        require(extKey.size == 64) { "extKey must be 64 bytes" }
        return Ed25519Icarus.publicKeyFromScalar(extKey.copyOfRange(0, 32))
    }

    // ── Full path derivation ──────────────────────────────────────────────────

    /**
     * Derive the Byron address key at m/44'/1815'/0'/0/[index] from a mnemonic.
     *
     * @param mnemonicWords BIP-39 mnemonic words
     * @param index         address index (0-based, soft/non-hardened)
     * @return Triple(pubKey[32], chainCode[32], extKey[64])
     */
    fun deriveByronAddressKey(
        mnemonicWords: List<String>,
        index: Int = 0
    ): Triple<ByteArray, ByteArray, ByteArray> {
        val (masterExt, masterCC) = masterKeyFromMnemonic(mnemonicWords)
        return deriveByronPath(masterExt, masterCC, index)
    }

    /**
     * Traverse m/44'/1815'/0'/0/[index] starting from a master extended key.
     *
     * @return Triple(pubKey[32], chainCode[32], extKey[64])
     */
    fun deriveByronPath(
        extKey: ByteArray,
        chainCode: ByteArray,
        index: Int
    ): Triple<ByteArray, ByteArray, ByteArray> {
        var k  = extKey
        var cc = chainCode

        // m/44' — purpose (hardened)
        deriveChildKey(k, cc, 44, hardened = true).let   { (nk, nc) -> k = nk; cc = nc }
        // m/44'/1815' — Cardano coin type (hardened)
        deriveChildKey(k, cc, 1815, hardened = true).let { (nk, nc) -> k = nk; cc = nc }
        // m/44'/1815'/0' — account 0 (hardened)
        deriveChildKey(k, cc, 0, hardened = true).let    { (nk, nc) -> k = nk; cc = nc }
        // m/44'/1815'/0'/0 — external chain (SOFT — must NOT be hardened)
        deriveChildKey(k, cc, 0, hardened = false).let   { (nk, nc) -> k = nk; cc = nc }
        // m/44'/1815'/0'/0/index — address (SOFT — must NOT be hardened)
        deriveChildKey(k, cc, index, hardened = false).let { (nk, nc) -> k = nk; cc = nc }

        return Triple(publicKeyFromExtended(k), cc, k)
    }

    // ── Low-level math ────────────────────────────────────────────────────────

    /** Encode Int as 4-byte little-endian */
    private fun intToLE4(v: Int): ByteArray = byteArrayOf(
        (v         and 0xFF).toByte(),
        ((v shr 8)  and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    /**
     * Left-shift a 28-byte little-endian integer by 3 bits (multiply by 8).
     * Input: 28 bytes. Output: 32 bytes (accommodates overflow).
     *
     * This is the critical step that keeps the Icarus scalar in the correct subgroup.
     */
    internal fun multiply8LE(src: ByteArray): ByteArray {
        require(src.size == 28) { "src must be 28 bytes for multiply8LE, got ${src.size}" }
        val dst   = ByteArray(32)
        var carry = 0
        for (i in src.indices) {
            val b   = src[i].toInt() and 0xFF
            dst[i]  = ((b shl 3) or carry).toByte()
            carry   = b ushr 5
        }
        dst[src.size] = carry.toByte()   // byte 28 holds the overflow
        return dst
    }

    /**
     * Add two 32-byte little-endian scalars with carry propagation.
     * No modular reduction — intentional per Icarus spec.
     */
    internal fun addScalarsLE(a: ByteArray, b: ByteArray): ByteArray {
        val dst   = ByteArray(32)
        var carry = 0
        for (i in 0 until 32) {
            val ai  = if (i < a.size) a[i].toInt() and 0xFF else 0
            val bi  = if (i < b.size) b[i].toInt() and 0xFF else 0
            val sum = ai + bi + carry
            dst[i]  = (sum and 0xFF).toByte()
            carry   = sum ushr 8
        }
        return dst
    }
}
