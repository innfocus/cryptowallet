package com.lybia.cryptowallet.coinkits.centrality.model

import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.blake2b
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray

class CennzAddress {
    var address: String = ""
    var publicKey: ByteArray? = null

    constructor(
        address: String
    ) {
        this.address = address
        this.publicKey = this.cennzParseAddress(address)
    }

    constructor(
        address: String,
        publicKeyString: String
    ) {
        this.address = address
        this.publicKey = publicKeyString.removePrefix("0x").fromHexToByteArray()
    }

    fun cennzParseAddress(address: String): ByteArray? {
        val decoded = Base58.decode(address)
        val allowedEncodedLengths = intArrayOf(3, 4, 6, 10, 36, 35, 37, 38)
        if (!allowedEncodedLengths.contains(decoded.size)) {
            return null
        }
        val ss58Length = if (decoded[0].toInt().and(0b01000000) != 0) {
            2
        } else {
            1
        }

        val isPublicKey = intArrayOf(34 + ss58Length, 35 + ss58Length).contains(decoded.size)
        val length = if (isPublicKey) {
            decoded.size - 2
        } else {
            decoded.size - 1
        }

        val key = decoded.copyOfRange(0, length)

        var ssHash = "SS58PRE".toByteArray()
        ssHash += key

        val hash = ssHash.blake2b(64)

        val firstCondition = decoded[0].toInt().and(0b1000_0000) == 0
        val secondCondition = !intArrayOf(46, 47).contains(decoded[0].toInt())
        val thirdCondition = if (isPublicKey) {
            decoded[decoded.size - 2] == hash[0] && decoded[decoded.size - 1] == hash[1]
        } else {
            decoded[decoded.size - 1] == hash[0]
        }
        val isValid = firstCondition && secondCondition && thirdCondition
        return if (isValid) {
            return decoded.copyOfRange(ss58Length, length)
        } else {
            null
        }
    }

    fun checkValid(): Boolean {
        return this.publicKey != null
    }
}