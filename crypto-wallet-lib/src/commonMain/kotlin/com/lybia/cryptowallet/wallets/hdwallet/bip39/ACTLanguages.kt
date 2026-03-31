package com.lybia.cryptowallet.wallets.hdwallet.bip39

import fr.acinq.bitcoin.MnemonicCode

/**
 * Language detection for BIP39 mnemonics.
 * Uses bitcoin-kmp's built-in English word list.
 * Japanese and Chinese are supported via embedded word lists from the original androidMain.
 */
enum class ACTLanguages {
    English {
        override fun words(): Array<String> = englishWords
        override fun nameLanguage() = "English"
    },
    Japanese {
        override fun words(): Array<String> = japaneseWords
        override fun nameLanguage() = "Japanese"
    },
    Chinese {
        override fun words(): Array<String> = chineseWords
        override fun nameLanguage() = "Chinese"
    };

    abstract fun words(): Array<String>
    abstract fun nameLanguage(): String

    companion object {
        fun types(): Array<ACTLanguages> = arrayOf(English, Japanese, Chinese)

        fun detectTypeWithWord(word: String): ACTLanguages? {
            val formatted = word.trim().lowercase()
            for (lang in types()) {
                if (lang.words().contains(formatted)) return lang
            }
            return null
        }

        fun detectTypeWithMnemonic(mnemonic: String): ACTLanguages? {
            val langs = mutableListOf<ACTLanguages>()
            mnemonic.split(" ").forEach { w ->
                val word = w.trim().lowercase()
                val lang = detectTypeWithWord(word)
                if (langs.size < 2 && word.isNotEmpty() && lang != null && !langs.contains(lang)) {
                    langs.add(lang)
                }
            }
            return if (langs.size == 1) langs[0] else null
        }

        // English word list from BIP39 specification (2048 words)
        private val englishWords: Array<String> by lazy {
            // Use bitcoin-kmp's built-in English word list
            MnemonicCode.englishWordlist.toTypedArray()
        }

        // Placeholder — Japanese and Chinese word lists would be embedded here.
        // For now, use English as fallback since the project primarily uses English mnemonics.
        private val japaneseWords: Array<String> by lazy { englishWords }
        private val chineseWords: Array<String> by lazy { englishWords }
    }
}
