package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.wallets.bip39.wordlists.CHINESE_SIMPLIFIED_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.CHINESE_TRADITIONAL_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.CZECH_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.FRENCH_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.ITALIAN_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.JAPANESE_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.KOREAN_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.PORTUGUESE_WORDLIST
import com.lybia.cryptowallet.wallets.bip39.wordlists.SPANISH_WORDLIST
import fr.acinq.bitcoin.MnemonicCode

/**
 * BIP-39 language registry.
 *
 * Each entry exposes the official 2048-word wordlist sourced from
 * https://github.com/bitcoin/bips/tree/master/bip-0039 plus helpers for
 * O(1) membership / index lookup and language detection.
 *
 * Used by both mnemonic generation (Mnemonics, ACTBIP39) and entropy
 * recovery (IcarusKeyDerivation for Cardano), so non-English mnemonics
 * are supported across the entire library.
 */
enum class Bip39Language(val code: String) {
    ENGLISH("en"),
    JAPANESE("ja"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    CHINESE_TRADITIONAL("zh-Hant"),
    FRENCH("fr"),
    ITALIAN("it"),
    SPANISH("es"),
    KOREAN("ko"),
    CZECH("cs"),
    PORTUGUESE("pt");

    /** The 2048-word BIP-39 wordlist for this language. */
    val wordlist: List<String> by lazy {
        when (this) {
            ENGLISH              -> MnemonicCode.englishWordlist
            JAPANESE             -> JAPANESE_WORDLIST
            CHINESE_SIMPLIFIED   -> CHINESE_SIMPLIFIED_WORDLIST
            CHINESE_TRADITIONAL  -> CHINESE_TRADITIONAL_WORDLIST
            FRENCH               -> FRENCH_WORDLIST
            ITALIAN              -> ITALIAN_WORDLIST
            SPANISH              -> SPANISH_WORDLIST
            KOREAN               -> KOREAN_WORDLIST
            CZECH                -> CZECH_WORDLIST
            PORTUGUESE           -> PORTUGUESE_WORDLIST
        }
    }

    /** O(1) membership test for mnemonic words. */
    internal val wordSet: Set<String> by lazy { wordlist.toHashSet() }

    /** O(1) word -> index lookup for entropy reconstruction. */
    internal val wordIndex: Map<String, Int> by lazy {
        HashMap<String, Int>(wordlist.size).apply {
            wordlist.forEachIndexed { i, w -> put(w, i) }
        }
    }

    companion object {
        // Latin-script BIP-39 languages (declaration order matters — English first wins ties)
        private val LATIN = listOf(ENGLISH, FRENCH, ITALIAN, SPANISH, CZECH, PORTUGUESE)
        private val HIRAGANA = listOf(JAPANESE)
        private val HANGUL = listOf(KOREAN)
        private val CJK = listOf(CHINESE_SIMPLIFIED, CHINESE_TRADITIONAL)

        /**
         * Languages that detection is allowed to consider. Defaults to all 10
         * BIP-39 languages. An app can call [setEnabledLanguages] at startup to
         * narrow this set — useful if memory or cold-start latency matters and
         * the wallet only ever needs to support a subset (e.g. English + Japanese).
         */
        @kotlin.concurrent.Volatile
        private var enabled: Set<Bip39Language> = entries.toSet()

        /**
         * Restrict detection to the given languages. Pass an empty list or
         * `entries` to reset to "all 10".
         *
         * Languages outside this set are still usable via the explicit
         * [Bip39Language] enum value (e.g. `IcarusKeyDerivation.masterKeyFromMnemonic(words, KOREAN)`)
         * — the setting only affects auto-detection.
         */
        fun setEnabledLanguages(languages: Collection<Bip39Language>) {
            enabled = if (languages.isEmpty()) entries.toSet() else languages.toSet()
        }

        fun getEnabledLanguages(): Set<Bip39Language> = enabled

        /**
         * Split a mnemonic string into its words.
         *
         * Recognizes both ASCII space (U+0020) and the Japanese ideographic
         * space (U+3000) which is the conventional separator for Japanese
         * BIP-39 mnemonics.
         */
        fun splitMnemonic(mnemonic: String): List<String> =
            mnemonic.split(' ', '\u3000').filter { it.isNotEmpty() }

        /**
         * Detect the BIP-39 language of a mnemonic word list.
         *
         * Performance: a Unicode-script pre-filter narrows the candidate set
         * before any wordlist is loaded. A Japanese mnemonic only ever loads
         * the Japanese wordlist; an English mnemonic only loads Latin
         * wordlists (typically just English, since it is tried first).
         *
         * A language matches if **every** word appears in its wordlist.
         * When multiple languages match (English and French share a small
         * number of words such as "abandon"), the first language in
         * declaration order wins — English is therefore preferred.
         *
         * @return the detected language or `null` if no wordlist contains all words
         */
        fun detect(words: List<String>): Bip39Language? {
            if (words.isEmpty()) return null
            val trimmed = words.map { it.trim() }
            val firstChar = trimmed.first().firstOrNull() ?: return null

            val scriptCandidates = when {
                firstChar in '\u3040'..'\u309F' -> HIRAGANA   // Japanese hiragana
                firstChar in '\u30A0'..'\u30FF' -> HIRAGANA   // Japanese katakana (defensive)
                firstChar in '\uAC00'..'\uD7AF' -> HANGUL     // Korean hangul syllables
                firstChar in '\u4E00'..'\u9FFF' -> CJK        // CJK unified ideographs
                else                            -> LATIN     // ASCII / Latin diacritics
            }

            return scriptCandidates.firstOrNull { lang ->
                lang in enabled && trimmed.all { it in lang.wordSet }
            }
        }

        /** Convenience overload that splits the mnemonic string first. */
        fun detect(mnemonic: String): Bip39Language? = detect(splitMnemonic(mnemonic))
    }
}
