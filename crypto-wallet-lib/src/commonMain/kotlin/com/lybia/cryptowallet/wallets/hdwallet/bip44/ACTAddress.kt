package com.lybia.cryptowallet.wallets.hdwallet.bip44

import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.utils.ACTEIP55
import com.lybia.cryptowallet.utils.Base58Ext
import com.lybia.cryptowallet.utils.dropFirst
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.suffix
import com.lybia.cryptowallet.utils.toHexString
import com.lybia.cryptowallet.utils.Bech32
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPublicKey
import org.ton.block.AddrStd

class ACTAddress {
    private var publicKey: ACTPublicKey? = null
    var network: ACTNetwork
    private var addressStr: String? = null

    constructor(publicKey: ACTPublicKey) {
        this.publicKey = publicKey
        this.network = publicKey.network
    }

    constructor(addressStr: String, network: ACTNetwork) {
        this.addressStr = addressStr
        this.network = network
    }

    fun raw(): ByteArray? {
        if (publicKey != null) {
            when (publicKey!!.network.coin) {
                ACTCoin.Bitcoin, ACTCoin.Ripple -> {
                    return byteArrayOf(publicKey!!.network.pubkeyhash()) +
                            ACTCrypto.sha256ripemd160(publicKey!!.raw!!)
                }
                ACTCoin.Ethereum, ACTCoin.XCoin -> {
                    val pubKeyUncompressed = ACTCrypto.convertToUncompressed(publicKey!!.raw!!)
                    return ACTCrypto.hashSHA3256(pubKeyUncompressed.dropFirst()).suffix(20)
                }
                ACTCoin.Cardano -> {
                    // Use commonMain CardanoAddress for Byron address
                    val chainCode = publicKey!!.chainCode ?: return null
                    val pubBytes = publicKey!!.raw ?: return null
                    val byronAddr = CardanoAddress.createByronAddress(pubBytes, chainCode)
                    return fr.acinq.bitcoin.Base58.decode(byronAddr)
                }
                else -> {}
            }
        } else if (addressStr != null) {
            when (network.coin) {
                ACTCoin.Bitcoin, ACTCoin.Ripple -> {
                    val type = if (network.coin == ACTCoin.Ripple) Base58Ext.Base58Type.Ripple else Base58Ext.Base58Type.Basic
                    val r = Base58Ext.decode(addressStr!!, type)
                    if (r.isEmpty()) return null
                    val checksum = r.suffix(4)
                    val pubKeyHash = r.copyOfRange(0, r.size - 4)
                    val checksumConfirm = ACTCrypto.doubleSHA256(pubKeyHash).copyOfRange(0, 4)
                    return if (checksum.toHexString() == checksumConfirm.toHexString()) pubKeyHash else null
                }
                ACTCoin.Ethereum, ACTCoin.XCoin -> {
                    return addressStr!!.substring(network.addressPrefix().length).fromHexToByteArray()
                }
                ACTCoin.Cardano -> {
                    return if (CardanoAddress.isValidShelleyAddress(addressStr!!)) {
                        try {
                            val (_, data5bit) = Bech32.decode(addressStr!!)
                            Bech32.convertBits(data5bit, 5, 8, false)
                        } catch (_: Exception) { null }
                    } else {
                        try {
                            fr.acinq.bitcoin.Base58.decode(addressStr!!)
                        } catch (_: Exception) { null }
                    }
                }
                ACTCoin.TON -> {
                    return try {
                        val addr = AddrStd.parse(addressStr!!)
                        addr.address.toByteArray()
                    } catch (_: Exception) { null }
                }
                else -> {}
            }
        }
        return null
    }

    fun rawAddressString(): String {
        if (addressStr != null) return addressStr!!
        val r = raw() ?: return ""
        return when (network.coin) {
            ACTCoin.Bitcoin, ACTCoin.Ripple -> {
                val cs = ACTCrypto.doubleSHA256(r).copyOfRange(0, 4)
                val type = if (network.coin == ACTCoin.Ripple) Base58Ext.Base58Type.Ripple else Base58Ext.Base58Type.Basic
                network.addressPrefix() + Base58Ext.encode(r + cs, type)
            }
            ACTCoin.Ethereum, ACTCoin.XCoin -> {
                network.addressPrefix() + ACTEIP55.encode(r)
            }
            ACTCoin.Cardano -> {
                if (publicKey != null) {
                    val chainCode = publicKey!!.chainCode ?: return ""
                    val pubBytes = publicKey!!.raw ?: return ""
                    CardanoAddress.createByronAddress(pubBytes, chainCode)
                } else {
                    // Shelley payload header nibble: 0x00 (base), 0x60 (enterprise), 0xE0 (reward)
                    val header = r[0].toInt() and 0xFF
                    val typeNibble = header and 0xF0
                    if (typeNibble == 0x00 || typeNibble == 0x60 || typeNibble == 0xE0) {
                        val isTestnet = (header and 0x0F) == 0x00
                        val prefix = when {
                            typeNibble == 0xE0 && isTestnet -> "stake_test"
                            typeNibble == 0xE0 -> "stake"
                            isTestnet -> "addr_test"
                            else -> "addr"
                        }
                        val data5bit = Bech32.convertBits(r, 8, 5, true)
                        Bech32.encode(prefix, data5bit)
                    } else {
                        fr.acinq.bitcoin.Base58.encode(r)
                    }
                }
            }
            ACTCoin.TON -> {
                // r is the 32-byte account hash; rebuild a non-bounceable mainnet address
                val isTestnet = network.isTestNet
                val addr = AddrStd(0, org.ton.bitstring.BitString(r))
                addr.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
            }
            else -> fr.acinq.bitcoin.Base58.encode(r)
        }
    }
}

fun String.isAddress(coin: ACTCoin): Boolean {
    return ACTAddress(this, ACTNetwork(coin, false)).raw() != null
}
