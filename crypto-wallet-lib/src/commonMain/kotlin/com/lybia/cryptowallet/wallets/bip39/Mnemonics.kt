package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.utils.sha256
import fr.acinq.bitcoin.MnemonicCode
import kotlin.random.Random

enum class MNEMONIC_SIZE(val size: Int) {
    WORDS_12(12),
    WORDS_15(15),
    WORDS_18(18),
    WORDS_21(21),
    WORDS_24(24)
}

object Mnemonics {

    /**
     * Generate a random BIP-39 mnemonic in the requested language.
     *
     * @param wordSize number of words (12/15/18/21/24)
     * @param language wordlist language — defaults to English. Supports all 10
     *                 official BIP-39 languages via [Bip39Language].
     */
    fun generateRandomSeed(
        wordSize: MNEMONIC_SIZE,
        language: Bip39Language = Bip39Language.ENGLISH
    ): List<String> {
        // BIP-39 entropy size in bytes for each word count
        val entropyBytes = when (wordSize) {
            MNEMONIC_SIZE.WORDS_12 -> 16
            MNEMONIC_SIZE.WORDS_15 -> 20
            MNEMONIC_SIZE.WORDS_18 -> 24
            MNEMONIC_SIZE.WORDS_21 -> 28
            MNEMONIC_SIZE.WORDS_24 -> 32
        }
        val entropy = Random.nextBytes(entropyBytes)
        return entropyToMnemonic(entropy, language)
    }

    /**
     * Convert raw entropy bytes into a BIP-39 mnemonic for the given language.
     *
     * Implements the standard BIP-39 algorithm: append SHA-256 checksum bits,
     * split into 11-bit groups, look up each group in the language's wordlist.
     */
    fun entropyToMnemonic(
        entropy: ByteArray,
        language: Bip39Language = Bip39Language.ENGLISH
    ): List<String> {
        require(entropy.size in intArrayOf(16, 20, 24, 28, 32)) {
            "Entropy size must be 16/20/24/28/32 bytes, got ${entropy.size}"
        }
        val checksumBits = entropy.size * 8 / 32
        val hash = entropy.sha256()
        val checksumByte = hash[0].toInt() and 0xFF

        val totalBits = entropy.size * 8 + checksumBits
        val bits = BooleanArray(totalBits)
        for (i in entropy.indices) {
            val b = entropy[i].toInt() and 0xFF
            for (j in 0 until 8) bits[i * 8 + j] = (b shr (7 - j)) and 1 != 0
        }
        for (j in 0 until checksumBits) {
            bits[entropy.size * 8 + j] = (checksumByte shr (7 - j)) and 1 != 0
        }

        val wordlist = language.wordlist
        val wordCount = totalBits / 11
        return List(wordCount) { i ->
            var idx = 0
            for (j in 0 until 11) {
                idx = (idx shl 1) or (if (bits[i * 11 + j]) 1 else 0)
            }
            wordlist[idx]
        }
    }

    /**
     * Validate a mnemonic in any supported language.
     *
     * Auto-detects the language and verifies the BIP-39 checksum. The English
     * path delegates to bitcoin-kmp's validator for backwards compatibility;
     * other languages run the same checksum check via [entropyToMnemonic]
     * round-trip.
     */
    fun validateSeedWord(seed: String) {
        val words = Bip39Language.splitMnemonic(seed)
        val language = Bip39Language.detect(words)
            ?: throw IllegalArgumentException("Mnemonic does not match any supported BIP-39 wordlist")

        if (language == Bip39Language.ENGLISH) {
            MnemonicCode.validate(words.joinToString(" "))
            return
        }

        // Reconstruct entropy from mnemonic, then verify checksum by round-trip.
        require(words.size in intArrayOf(12, 15, 18, 21, 24)) {
            "Mnemonic must contain 12/15/18/21/24 words, got ${words.size}"
        }
        val wordIndex = language.wordIndex
        val totalBits = words.size * 11
        val checksumBits = totalBits / 33
        val entropyBits = totalBits - checksumBits
        val bits = BooleanArray(totalBits)
        words.forEachIndexed { i, w ->
            val idx = wordIndex[w.trim()]
                ?: throw IllegalArgumentException("Word '$w' not in ${language.code} wordlist")
            for (j in 0 until 11) bits[i * 11 + j] = (idx shr (10 - j)) and 1 != 0
        }
        val entropy = ByteArray(entropyBits / 8)
        for (i in entropy.indices) {
            var b = 0
            for (j in 0 until 8) if (bits[i * 8 + j]) b = b or (1 shl (7 - j))
            entropy[i] = b.toByte()
        }
        val expected = entropyToMnemonic(entropy, language)
        if (expected != words.map { it.trim() }) {
            throw IllegalArgumentException("Invalid BIP-39 checksum for ${language.code} mnemonic")
        }
    }
}
