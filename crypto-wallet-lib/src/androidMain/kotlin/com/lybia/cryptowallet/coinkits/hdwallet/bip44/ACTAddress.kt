package com.lybia.cryptowallet.coinkits.hdwallet.bip44

import com.lybia.cryptowallet.coinkits.cardano.model.CarPublicKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPublicKey
import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.dropFirst
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.prefix
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.suffix
import com.lybia.cryptowallet.coinkits.cardano.model.CarAddress
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.toHexString
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.ACTEIP55


class ACTAddress {

    private var publicKey: ACTPublicKey? = null
    var network: ACTNetwork
    private var addressStr: String? = null

    constructor(publicKey: ACTPublicKey) {
        this.publicKey = publicKey
        this.network = publicKey.network
    }

    constructor(
        addressStr: String,
        network: ACTNetwork
    ) {
        this.addressStr = addressStr
        this.network = network
    }

    fun raw(): ByteArray? {
        if (publicKey != null) {
            when (publicKey!!.network.coin) {
                ACTCoin.Bitcoin,
                ACTCoin.Ripple -> {
                    return byteArrayOf(publicKey!!.network.pubkeyhash()) + ACTCryto.sha256ripemd160(
                        publicKey!!.raw!!
                    )
                }
                ACTCoin.Ethereum -> {
                    val pubKeyUnpressedData = ACTCryto.convertToUncompressed(publicKey!!.raw!!)
                    return ACTCryto.hashSHA3256(pubKeyUnpressedData.dropFirst()).suffix(20)
                }
                ACTCoin.Cardano -> {
                    val pub = CarPublicKey(publicKey!!.raw!!)
                    return CarAddress(pub, publicKey!!.chainCode).raw()
                }
                else -> {}
            }
        } else if (addressStr != null) {
            when (network.coin) {
                ACTCoin.Bitcoin,
                ACTCoin.Ripple -> {
                    val type =
                        if (network.coin == ACTCoin.Ripple) Base58.Base58Type.Ripple else Base58.Base58Type.Basic
                    val r = Base58.decode(addressStr!!, type) ?: return null
                    val checksum = r.suffix(4)
                    val pubKeyHash = r.dropLast(4).toByteArray()
                    val checksumConfirm = ACTCryto.doubleSHA256(pubKeyHash).prefix(4)
                    if (checksum.toHexString() == checksumConfirm.toHexString()) {
                        return pubKeyHash
                    } else {
                        return null
                    }
                }
                ACTCoin.Ethereum -> {
                    return addressStr!!.substring(network!!.addressPrefix().length)
                        .fromHexToByteArray()
                }
                ACTCoin.Cardano -> {
                    return CarAddress(addressStr = addressStr).raw()
                }
                else -> {}
            }
        }
        return null
    }

    fun rawAddressString(): String {
        if (addressStr != null) {
            return addressStr!!
        } else {
            val r = raw()
            if ((r != null) and (network != null)) {
                return when (network!!.coin) {
                    ACTCoin.Bitcoin,
                    ACTCoin.Ripple -> {
                        val cs = ACTCryto.doubleSHA256(r!!).copyOfRange(0, 4)
                        val type =
                            if (network!!.coin == ACTCoin.Ripple) Base58.Base58Type.Ripple else Base58.Base58Type.Basic
                        network!!.addressPrefix() + Base58.encode(r!! + cs, type)
                    }
                    ACTCoin.Ethereum -> {
                        network!!.addressPrefix() + ACTEIP55.encode(r!!)
                    }
                    ACTCoin.Cardano -> {
                        Base58.encode(r!!)
                    }
                    else -> {
                        Base58.encode(r!!)
                    }
                }
            } else {
                return ""
            }
        }
    }
}

fun String.isAddress(coin: ACTCoin): Boolean {
    return ACTAddress(this, ACTNetwork(coin, false)).raw() != null
}