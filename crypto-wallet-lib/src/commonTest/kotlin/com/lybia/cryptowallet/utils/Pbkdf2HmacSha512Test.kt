package com.lybia.cryptowallet.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test vectors for [pbkdf2HmacSha512] and [bip39MnemonicToSeed].
 *
 * These lock the output byte-for-byte against reference values computed
 * independently (Python `hashlib.pbkdf2_hmac('sha512', …)` and the BIP-39
 * reference implementation). If anything in the pipeline regresses — even
 * subtly — these tests fail immediately, unlike `JapaneseMnemonicNfkdTest`
 * which only checks two inputs produce the same (possibly corrupted) output.
 */
class Pbkdf2HmacSha512Test {

    private fun ByteArray.hex(): String =
        joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    /** RFC 6070 §2 test case adapted to SHA-512 using reference Python output. */
    @Test
    fun pbkdf2HmacSha512_referenceVectorAsciiPassword() {
        val out = pbkdf2HmacSha512(
            password = "password".encodeToByteArray(),
            salt = "salt".encodeToByteArray(),
            iterations = 1,
            dkLen = 64
        )
        assertEquals(
            "867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252" +
                "c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce",
            out.hex()
        )
    }

    /** Second RFC-style vector: multi-iteration, multi-block output. */
    @Test
    fun pbkdf2HmacSha512_referenceVectorMultiIteration() {
        val out = pbkdf2HmacSha512(
            password = "password".encodeToByteArray(),
            salt = "salt".encodeToByteArray(),
            iterations = 4096,
            dkLen = 64
        )
        assertEquals(
            "d197b1b33db0143e018b12f3d1d1479e6cdebdcc97c5c0f87f6902e072f457b5" +
                "143f30602641b3d55cd335988cb36b84376060ecd532e039b742a239434af2d5",
            out.hex()
        )
    }

    /**
     * Locked BIP-39 seed for the user-reported Japanese mnemonic.
     * Reference computed via Python:
     *   pbkdf2_hmac('sha512', nfkd(mnemonic).utf8, b'mnemonic', 2048, 64)
     */
    @Test
    fun bip39MnemonicToSeed_japanesePhraseMatchesReference() {
        val mnemonic =
            "ちいき\u3000とくてん\u3000せけん\u3000はにかむ\u3000うなずく\u3000ほたて\u3000" +
                "いみん\u3000きぞん\u3000ききて\u3000むのう\u3000そがい\u3000へいせつ"
        val seed = bip39MnemonicToSeed(mnemonic)
        assertEquals(
            "3a973fb059be2503c2d45669bd8db8e672807a2d22be29ca400e954169d08d2b" +
                "de25ceb858652d925b8b26e5ede5b1c9d77c28a01627d1f82b25b73fdbae9976",
            seed.hex()
        )
    }

    /**
     * Backward-compat guard: for ASCII-only English mnemonics the seed is
     * byte-identical to the official BIP-39 TREZOR test vector. This is the
     * contract that protects every Android app currently integrated — their
     * existing English-seed addresses cannot drift after this fix ships.
     *
     * Source: github.com/trezor/python-mnemonic/blob/master/vectors.json
     */
    @Test
    fun bip39MnemonicToSeed_englishTrezorVectorMatches() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        val seed = bip39MnemonicToSeed(mnemonic, "TREZOR")
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e5349553" +
                "1f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            seed.hex()
        )
    }
}
