package com.lybia.cryptowallet.wallets.hdwallet.bip39

import com.lybia.cryptowallet.wallets.bip39.Bip39Language

/**
 * Language for BIP-39 mnemonics.
 *
 * Backed by [Bip39Language], which holds the canonical 2048-word wordlists
 * for all 10 official BIP-39 languages. Add a new language by adding it
 * to [Bip39Language] — the entry here is just a thin façade kept for
 * source compatibility with existing call-sites.
 */
enum class ACTLanguages(val bip39: Bip39Language, private val displayName: String) {
    English(Bip39Language.ENGLISH, "English"),
    Japanese(Bip39Language.JAPANESE, "Japanese"),
    ChineseSimplified(Bip39Language.CHINESE_SIMPLIFIED, "Chinese (Simplified)"),
    ChineseTraditional(Bip39Language.CHINESE_TRADITIONAL, "Chinese (Traditional)"),
    French(Bip39Language.FRENCH, "French"),
    Italian(Bip39Language.ITALIAN, "Italian"),
    Spanish(Bip39Language.SPANISH, "Spanish"),
    Korean(Bip39Language.KOREAN, "Korean"),
    Czech(Bip39Language.CZECH, "Czech"),
    Portuguese(Bip39Language.PORTUGUESE, "Portuguese");

    /**
     * @deprecated Kept for source compatibility — Chinese Simplified is the default
     * resolution of the legacy "Chinese" entry. New code should use [ChineseSimplified]
     * or [ChineseTraditional] explicitly.
     */
    companion object {
        @Deprecated(
            "Use ChineseSimplified or ChineseTraditional explicitly",
            ReplaceWith("ACTLanguages.ChineseSimplified")
        )
        val Chinese: ACTLanguages get() = ChineseSimplified

        fun types(): Array<ACTLanguages> = entries.toTypedArray()

        fun detectTypeWithWord(word: String): ACTLanguages? {
            val formatted = word.trim()
            return entries.firstOrNull { formatted in it.bip39.wordSet }
        }

        fun detectTypeWithMnemonic(mnemonic: String): ACTLanguages? {
            val detected = Bip39Language.detect(mnemonic) ?: return null
            return entries.firstOrNull { it.bip39 == detected }
        }
    }

    fun words(): Array<String> = bip39.wordlist.toTypedArray()
    fun nameLanguage(): String = displayName
}
