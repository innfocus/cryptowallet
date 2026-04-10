package com.lybia.cryptowallet.utils

import com.lybia.cryptowallet.wallets.bip39.Bip39Language

/**
 * BIP-39 seed derivation — spec-compliant for every BIP-39 language.
 *
 * Replaces direct calls to `fr.acinq.bitcoin.MnemonicCode.toSeed`, whose JVM
 * actual mangles non-ASCII passwords (see [pbkdf2HmacSha512] for the root-cause
 * analysis). Output is byte-identical to the old path for ASCII-only mnemonics,
 * so integrated Android apps using English seed phrases keep the exact same
 * addresses after upgrading. For non-ASCII mnemonics the seed now matches the
 * BIP-39 reference vectors and every correct implementation on other platforms.
 *
 * Pipeline:
 *   1. NFKD-normalize mnemonic and passphrase (required by BIP-39).
 *   2. Split into words on ASCII space or ideographic space U+3000, rejoin with
 *      a single ASCII space — this canonicalizes the whitespace the same way
 *      bitcoin-kmp's `MnemonicCode.toSeed` did.
 *   3. UTF-8 encode both password and salt.
 *   4. PBKDF2-HMAC-SHA512 with 2048 iterations → 64-byte seed.
 *
 * @param mnemonic   mnemonic phrase in any normalization form, separators
 *                   may be ASCII space or ideographic space.
 * @param passphrase optional BIP-39 passphrase (default empty).
 */
internal fun bip39MnemonicToSeed(
    mnemonic: String,
    passphrase: String = ""
): ByteArray {
    val words = Bip39Language.splitMnemonic(mnemonic.nfkd())
    return bip39MnemonicToSeed(words, passphrase)
}

/**
 * Overload for callers that already hold a split word list. The list MUST
 * contain NFKD-normalized words; if in doubt, call the String overload.
 */
internal fun bip39MnemonicToSeed(
    words: List<String>,
    passphrase: String = ""
): ByteArray {
    val password = words.joinToString(" ").encodeToByteArray()
    val salt = ("mnemonic" + passphrase.nfkd()).encodeToByteArray()
    return pbkdf2HmacSha512(password, salt, iterations = 2048, dkLen = 64)
}
