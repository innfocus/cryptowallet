package com.lybia.cryptowallet.wallets.hdwallet.bip39

import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.prefix
import com.lybia.cryptowallet.utils.sha256
import com.lybia.cryptowallet.utils.suffix
import com.lybia.cryptowallet.utils.toBitsString
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.utils.nfkd
import com.lybia.cryptowallet.utils.normalized
import com.lybia.cryptowallet.utils.bip39MnemonicToSeed
import kotlin.random.Random

class ACTBIP39Exception(message: String) : Exception(message)

enum class ACTBIP39Error(val message: String) {
    InvalidStrength("Invalid strength"),
    UnableToGetRandomData("Unable to get random data"),
    UnableToCreateSeedData("Unable to create seed data"),
    UnableToCreateEntropy("Unable to create entropy")
}

class ACTBIP39 {
    companion object {

        @Throws(ACTBIP39Exception::class)
        fun mnemonicString(entropyHex: String, language: ACTLanguages): String {
            val ent = entropyHex.fromHexToByteArray()
            val hash = ent.sha256()
            val bits = ent.size * 8
            val hashBits = hash.toBitsString()
            val cs = hashBits.prefix(bits / 32)
            val seedBits = ent.toBitsString() + cs
            val ms = seedBits.length / 11
            if (ms % 3 != 0 || ms < 12 || ms > 24) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            val words = language.words()
            val mnemonic = ArrayList<String>(ms)
            for (i in 0 until ms) {
                val subStr = seedBits.substring(11 * i, 11 * (i + 1))
                val idxWord = subStr.toInt(2)
                mnemonic.add(words[idxWord])
            }
            return mnemonic.joinToString(separator = " ")
        }

        @Throws(ACTBIP39Exception::class)
        fun entropyString(mnemonic: String, skipValidate: Boolean = false): String {
            val normalizedMnemonic = mnemonic.nfkd()
            val language = ACTLanguages.detectTypeWithMnemonic(normalizedMnemonic)
                ?: throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
            val mnemonicSlice = splitMnemonicWords(normalizedMnemonic)
            if (!mnemonicSlice.isValid) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            var entropyHex = ""
            val words = language.words()
            var seedBits = ""

            for (i in 0 until mnemonicSlice.words.size) {
                val idx = words.indexOf(mnemonicSlice.words[i])
                if (idx == -1) {
                    throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
                }
                seedBits += ("00000000000" + idx.toString(2)).suffix(11)
            }
            val ms = mnemonicSlice.words.size
            val ent = seedBits.substring(0, seedBits.length - ms / 3)
            val cs = seedBits.substring(seedBits.length - ms / 3, seedBits.length)
            for (i in 0 until ent.length / 8) {
                entropyHex += ("00" + ent.substring(i * 8, (i + 1) * 8).toInt(2).toString(16)).suffix(2)
            }
            if (!validateCheckSum(entropyHex, cs) && !skipValidate) {
                throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
            }
            return entropyHex
        }

        @Throws(ACTBIP39Exception::class)
        fun correctMnemonic(mnemonic: String): String {
            val entropyHex = entropyString(mnemonic, true)
            val language = ACTLanguages.detectTypeWithMnemonic(mnemonic)
                ?: throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
            return mnemonicString(entropyHex, language)
        }

        private fun validateCheckSum(entropyHex: String, cs: String): Boolean {
            val ent = entropyHex.fromHexToByteArray()
            val hash = ent.sha256()
            val bits = ent.size * 8
            val hashBits = hash.toBitsString()
            val csNew = hashBits.prefix(bits / 32)
            return csNew == cs
        }

        /**
         * Generate a deterministic BIP-39 seed (hex-encoded) from a mnemonic.
         * Delegates to [bip39MnemonicToSeed], which implements PBKDF2-HMAC-SHA512
         * on raw bytes and therefore works correctly for every BIP-39 language
         * — see `utils/Pbkdf2HmacSha512.kt` for why bitcoin-kmp's
         * `MnemonicCode.toSeed` cannot be used here.
         */
        @Throws(ACTBIP39Exception::class)
        fun deterministicSeedString(mnemonic: String, passphrase: String = ""): String =
            bip39MnemonicToSeed(mnemonic, passphrase).toHexString()

        @Throws(ACTBIP39Exception::class)
        fun generateMnemonic(strength: Int, language: ACTLanguages): String {
            if (strength % 32 != 0) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            val c = strength / 8
            val bs = Random.nextBytes(c)
            return mnemonicString(bs.toHexString(), language)
        }

        data class Result(val words: List<String>, val isValid: Boolean)
        private fun splitMnemonicWords(mnemonic: String): Result {
            // NFKD first so CJK wordlist lookups (words() indexOf) match the
            // canonical BIP-39 form shipped in the wordlist resources.
            val words = Bip39Language.splitMnemonic(mnemonic.nfkd())
            val ms = words.size
            if (ms % 3 != 0 || ms < 12 || ms > 24) {
                return Result(listOf(), false)
            }
            return Result(words, true)
        }
    }
}
