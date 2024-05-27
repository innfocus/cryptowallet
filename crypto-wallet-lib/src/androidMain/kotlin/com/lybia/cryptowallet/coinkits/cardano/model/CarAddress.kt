package com.lybia.cryptowallet.coinkits.cardano.model

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Base58
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.Bech32
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.blake2b
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.crc32
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.sha3256
import java.io.ByteArrayOutputStream

class CarAddress {

    private var publicKey: CarPublicKey? = null
    private var addressStr: String

    constructor(
        publicKey: CarPublicKey? = null,
        chainCode: ByteArray? = null,
        addressStr: String? = null
    ) {
        this.addressStr = ""
        if ((publicKey != null) and (chainCode != null)) {
            this.publicKey = publicKey
            var output = ByteArrayOutputStream()
            val xPub = publicKey!!.bytes() + chainCode!!
            val addrType = UnsignedInteger(CarAddressType.ATPubKey.value.toLong())
            val addrAttributes = Map(0)
            var cb = CborBuilder().addArray()
                .add(addrType)
                .addArray()
                .add(addrType)
                .add(ByteString(xPub))
                .end()
                .add(addrAttributes)
                .end().build()
            CborEncoder(output).encode(cb)
            val addrRoot = output.toByteArray()
            val sha3 = addrRoot.sha3256()
            val abstractHash = sha3.blake2b(28)
            cb = CborBuilder().addArray()
                .add(ByteString(abstractHash))
                .add(addrAttributes)
                .add(addrType)
                .end().build()
            output.reset()
            CborEncoder(output).encode(cb)
            val address = output.toByteArray()
            val crc = address.crc32()
            val taggedAddress = ByteString(address)
            taggedAddress.setTag(24)
            cb = CborBuilder().addArray().add(taggedAddress).add(crc).end().build()
            output.reset()
            CborEncoder(output).encode(cb)
            val cwId = output.toByteArray()
            this.addressStr = Base58.encode(cwId)
        } else if (addressStr != null) {
            this.addressStr = addressStr
        }
    }

    fun raw(): ByteArray? {
        return try {
            val dataDecode = Bech32.bech32Decode(addressStr)
            val u5Data = dataDecode.data
            val u8Data = Bech32.convertBits(dataDecode.data, 0, u5Data.size, 5, 8, false)
            u8Data
        } catch (e: Exception) {
            when (addressStr != null) {
                true -> {
                    val r = Base58.decode(addressStr)
                    if (r.isNotEmpty()) r else null
                }
                false -> null
            }
        }
    }

    companion object {
        fun isValidAddress(address: String): Boolean {
            try {
                Bech32.bech32Decode(address)
                return true
            } catch (e: Exception) {

            }

            val r = Base58.decode(address)
            if (r.isNotEmpty()) {
                try {
                    val addressAsArray = CborDecoder.decode(r)
                    if (addressAsArray.size == 1) {
                        val cwId = addressAsArray[0]
                        if (cwId.majorType == MajorType.ARRAY) {
                            val tmp = cwId as Array
                            if (tmp.dataItems.size == 2) {
                                val addressDataEncode = tmp.dataItems[0]
                                val crc32Checksum = tmp.dataItems[1]
                                if ((addressDataEncode.majorType == MajorType.BYTE_STRING)
                                    and (crc32Checksum.majorType == MajorType.UNSIGNED_INTEGER)
                                    and addressDataEncode.hasTag()
                                ) {
                                    val tagged = addressDataEncode as ByteString
                                    val crc = crc32Checksum as UnsignedInteger
                                    return crc.value.toLong() == tagged.bytes.crc32()
                                }
                            }
                        }
                    }
                } catch (e: CborException) {
                }
            }
            return false
        }
    }
}