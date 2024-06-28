package com.lybia.cryptowallet.wallets.bip39

import fr.acinq.bitcoin.MnemonicCode
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlin.random.Random

enum class MNEMONIC_SIZE(val size: Int) {
    WORDS_12(12),
    WORDS_15(15),
    WORDS_18(18),
    WORDS_21(21)
}

object Mnemonics{

    fun generateRandomSeed(wordSize: MNEMONIC_SIZE): List<String>{
        val entropyBytes = when (wordSize){
            MNEMONIC_SIZE.WORDS_12 -> ByteArray(16)
            MNEMONIC_SIZE.WORDS_15 -> ByteArray(20)
            MNEMONIC_SIZE.WORDS_18 -> ByteArray(24)
            MNEMONIC_SIZE.WORDS_21 -> ByteArray(28)
        }

        val randomByte = Random.nextBytes(entropyBytes)
        return MnemonicCode.toMnemonics(randomByte)
    }

    fun validateSeedWord(seed: String){
        MnemonicCode.validate(seed)
    }

}