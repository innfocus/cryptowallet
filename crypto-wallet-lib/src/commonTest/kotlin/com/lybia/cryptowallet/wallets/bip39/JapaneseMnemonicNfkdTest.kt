package com.lybia.cryptowallet.wallets.bip39

import com.lybia.cryptowallet.utils.nfkd
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for the cross-platform Japanese mnemonic bug.
 *
 * Symptom (before fix): iOS and Android generated different TON / BTC
 * addresses for the same Japanese seed phrase, because iOS could deliver
 * dakuten characters like げ in NFD form (け U+3051 + ゛ U+3099) while
 * Android delivered them in NFC form (げ U+3052). Without BIP-39's mandatory
 * NFKD normalization, the UTF-8 bytes fed into PBKDF2 differed, producing
 * distinct seeds → distinct private keys → distinct addresses.
 *
 * The fix applies NFKD at library entry points so any input form collapses
 * to the canonical BIP-39 representation before PBKDF2.
 */
class JapaneseMnemonicNfkdTest {

    /**
     * User-reported 12-word Japanese mnemonic in NFC form.
     * Separator is the ideographic space U+3000 — the conventional
     * Japanese BIP-39 delimiter; NFKD maps it to U+0020.
     */
    private val nfcMnemonic =
        "きせつ\u3000しゃこ\u3000けぶかい\u3000げいのうじん\u3000べんきょう\u3000" +
        "しゃしん\u3000こくご\u3000でんあつ\u3000うこん\u3000そうり\u3000ついか\u3000やそう"

    /**
     * Same phrase with every dakuten syllable manually decomposed to
     * base kana + combining voiced mark (U+3099). This is the form iOS
     * may deliver via Foundation APIs, and the form that previously broke
     * PBKDF2 cross-platform.
     *
     * Decompositions applied:
     *   ぶ U+3076 → ふ U+3075 + ゛ U+3099
     *   げ U+3052 → け U+3051 + ゛ U+3099
     *   べ U+3079 → へ U+3078 + ゛ U+3099
     *   ご U+3054 → こ U+3053 + ゛ U+3099
     *   で U+3067 → て U+3066 + ゛ U+3099
     */
    private val nfdMnemonic =
        "きせつ\u3000しゃこ\u3000け\u3075\u3099かい\u3000" +
        "\u3051\u3099いのうじん\u3000\u3078\u3099んきょう\u3000" +
        "しゃしん\u3000こく\u3053\u3099\u3000" +
        "\u3066\u3099んあつ\u3000うこん\u3000そうり\u3000ついか\u3000やそう"

    @Test
    fun nfkdCollapsesNfcAndNfdToIdenticalString() {
        // Sanity check: the two forms are byte-different before normalization…
        val nfcBytes = nfcMnemonic.encodeToByteArray()
        val nfdBytes = nfdMnemonic.encodeToByteArray()
        assertEquals(
            true,
            !nfcBytes.contentEquals(nfdBytes),
            "Test setup invalid: NFC and NFD encodings should differ"
        )

        // …but identical after NFKD.
        assertEquals(
            nfcMnemonic.nfkd(),
            nfdMnemonic.nfkd(),
            "NFKD must collapse NFC and NFD Japanese mnemonics to the same string"
        )
    }

    @Test
    fun bip39SeedIsStableAcrossNormalizationForms() {
        val seedFromNfc = ACTBIP39.deterministicSeedString(nfcMnemonic)
        val seedFromNfd = ACTBIP39.deterministicSeedString(nfdMnemonic)
        assertEquals(
            seedFromNfc,
            seedFromNfd,
            "BIP-39 PBKDF2 seed must be identical regardless of input normalization form"
        )
    }

    @Test
    fun bitcoinAddressIsStableAcrossNormalizationForms() {
        val addrFromNfc = BitcoinManager(nfcMnemonic).getAddress()
        val addrFromNfd = BitcoinManager(nfdMnemonic).getAddress()
        assertEquals(
            addrFromNfc,
            addrFromNfd,
            "Derived Bitcoin address must not depend on mnemonic normalization form"
        )
    }
}
