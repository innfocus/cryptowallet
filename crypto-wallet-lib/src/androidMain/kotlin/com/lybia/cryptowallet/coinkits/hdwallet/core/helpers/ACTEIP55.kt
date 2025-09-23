package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers

import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto

class ACTEIP55 {
    companion object {
        fun encode(data: ByteArray): String {
            val address = data.toHexString()
            val hash    = ACTCryto.hashSHA3256(address.toCharArray().toByteArray()).toHexString()
            return hash.let { hexHash -> address.mapIndexed { index, hexChar ->
                when {
                    hexChar         in '0'..'9' -> hexChar
                    hexHash[index]  in '0'..'7' -> hexChar.lowercaseChar()
                    else                        -> hexChar.uppercaseChar()
                }
            } }.toCharArray().joinToString("")
        }
    }
}