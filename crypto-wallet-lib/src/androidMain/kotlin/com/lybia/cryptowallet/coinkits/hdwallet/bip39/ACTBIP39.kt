package com.lybia.cryptowallet.coinkits.hdwallet.bip39

import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.normalized
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.prefix
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.sha256
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.suffix
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toBitsString
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toHexString
import java.security.SecureRandom


class ACTBIP39Exception(message: String) : Exception(message)

enum class ACTBIP39Error(val message: String)
{
    InvalidStrength         ("Invalid strength"),
    UnableToGetRandomData   ("Unable to get random data"),
    UnableToCreateSeedData  ("Unable to create seed data"),
    UnableToCreateEntropy   ("Unable to create entropy")
}

class ACTBIP39 {
    /*
    *   CS = ENT / 32
    *   MS = (ENT + CS) / 11
    *   => MS = 3 * CS

    *   |  ENT  | CS | ENT+CS |  MS  |
    *   +-------+----+--------+------+
    *   |  128  |  4 |   132  |  12  |
    *   |  160  |  5 |   165  |  15  |
    *   |  192  |  6 |   198  |  18  |
    *   |  224  |  7 |   231  |  21  |
    *   |  256  |  8 |   264  |  24  |
    */

    companion object {
        @Throws(ACTBIP39Exception::class)
        fun mnemonicString(entropyHex: String, language: ACTLanguages): String {
            val ent         = entropyHex.fromHexToByteArray()
            val hash        = ent.sha256()
            val bits        = ent.count() * 8
            val hashBits    = hash.toBitsString()
            val cs          = hashBits.prefix(bits/32)
            val seedBits    = ent.toBitsString() + cs
            val ms          = seedBits.count() / 11
            if ((ms % 3 != 0) || (ms < 12) || (ms > 24)) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            val words = language.words()
            val mnemonic = arrayListOf<String>()

            for (i in 0 until ms) {
                val subStr = seedBits.substring(11*i, 11*(i + 1))
                val idxWord = subStr.toInt(2)
                mnemonic.add(words[idxWord])
            }
            return mnemonic.joinToString(separator = " ")
        }

        @Throws(ACTBIP39Exception::class)
        fun entropyString(mnemonic: String, skipValidate: Boolean = false): String {
            val language = ACTLanguages.detectTypeWithMnemonic(mnemonic) ?: throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
            val  mnemonicSlice = splitMnemonicWords(mnemonic)
            if (!mnemonicSlice.isValid) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            var entropyHex = ""
            val words       = language.words()
            var seedBits    = ""

            for (i in 0 until mnemonicSlice.words.count()) {
                val idx = words.indexOf(mnemonicSlice.words[i])
                if (idx == -1) {
                    throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
                }
                seedBits += ("00000000000" + idx.toString(2)).suffix(11)
            }
            val ms  = mnemonicSlice.words.count()
            val ent = seedBits.substring(0, seedBits.count() - ms/3)
            val cs  = seedBits.substring(seedBits.count() - ms/3, seedBits.count())
            for (i in 0 until ent.count()/8) {
                entropyHex += ("00" + ent.substring(i*8, (i+1)*8).toInt(2).toString(16)).suffix(2)
            }
            if (!validateCheckSum(entropyHex, cs) and !skipValidate) {
                throw ACTBIP39Exception(ACTBIP39Error.UnableToCreateEntropy.message)
            }
            return  entropyHex
        }

        @Throws(ACTBIP39Exception::class)
        fun correctMnemonic(mnemonic: String): String {
            val entropyHex  = entropyString(mnemonic, true)
            val language    = ACTLanguages.detectTypeWithMnemonic(mnemonic)
            return  mnemonicString(entropyHex, language!!)
        }

        private fun validateCheckSum(entropyHex: String, cs: String): Boolean {
            val ent         = entropyHex.fromHexToByteArray()
            val hash        = ent.sha256()
            val bits        = ent.count() * 8
            val hashBits    = hash.toBitsString()
            val csNew       = hashBits.prefix(bits/32)
            return csNew == cs
        }

        @Throws(ACTBIP39Exception::class)
        fun deterministicSeedString(mnemonic: String, passphrase: String = ""): String {
            val password  = mnemonic.normalized()
            val salt      = ("mnemonic" + passphrase).normalized()
            val bytes = ACTCryto.pbkdf2SHA512(password, salt)
            return bytes.toHexString()
        }

        @Throws(ACTBIP39Exception::class)
        fun generateMnemonic(strength: Int, language: ACTLanguages): String {
            if (strength % 32 != 0) {
                throw ACTBIP39Exception(ACTBIP39Error.InvalidStrength.message)
            }
            val c       = strength / 8
            val bs      = ByteArray(c)
            val random  = SecureRandom()
            random.nextBytes(bs)
            return mnemonicString(bs.toHexString(), language)
        }

        data class Result(val words: List<String>, val isValid: Boolean)
        private fun splitMnemonicWords(mnemonic: String): Result {
            val words   = mnemonic.split(" ")
            val ms      = words.count()
            if (ms % 3 != 0 || ms < 12 || ms > 24) {
                return Result(listOf(), false)
            }
            return Result(words, true)
        }
    }

}