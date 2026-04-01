package com.lybia.cryptowallet.wallets.ethereum

import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.utils.Keccak
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import fr.acinq.secp256k1.Secp256k1

/**
 * Pure-Kotlin EIP-155 Ethereum transaction signer.
 * Builds legacy (type 0) transactions with RLP encoding, signs with secp256k1.
 */
object EthTransactionSigner {

    /**
     * Build, RLP-encode, sign, and return a hex-encoded signed transaction.
     *
     * @param privateKey 32-byte secp256k1 private key
     * @param nonce Transaction nonce
     * @param gasPriceWei Gas price in wei
     * @param gasLimit Gas limit
     * @param toAddress Destination address (0x-prefixed hex)
     * @param valueWei Value in wei
     * @param data Call data (empty for simple transfers)
     * @param chainId EIP-155 chain ID (1 = mainnet, 11155111 = sepolia, etc.)
     * @return "0x"-prefixed hex string of the signed transaction
     */
    fun signTransaction(
        privateKey: ByteArray,
        nonce: Long,
        gasPriceWei: Long,
        gasLimit: Long,
        toAddress: String,
        valueWei: Long,
        data: ByteArray,
        chainId: Long
    ): String {
        val to = toAddress.removePrefix("0x").fromHexToByteArray()

        // RLP-encode for signing: [nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0]
        val unsignedFields = listOf(
            rlpEncodeInteger(nonce),
            rlpEncodeInteger(gasPriceWei),
            rlpEncodeInteger(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeInteger(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeInteger(chainId),
            rlpEncodeInteger(0),
            rlpEncodeInteger(0)
        )
        val unsignedTx = rlpEncodeList(unsignedFields)
        val txHash = Keccak.keccak256(unsignedTx)

        // Sign with secp256k1
        val sigData = Secp256k1.sign(txHash, privateKey)
        // sigData = 64 bytes (r[32] + s[32]), recId from ecdsaSign
        val sig = Secp256k1.compact2der(sigData) // we need compact, not DER
        val r = sigData.copyOfRange(0, 32)
        val s = sigData.copyOfRange(32, 64)

        // Recovery ID: try both 0 and 1 to find the correct one
        val recId = findRecoveryId(txHash, sigData, privateKey)

        // EIP-155: v = chainId * 2 + 35 + recId
        val v = chainId * 2 + 35 + recId

        // RLP-encode signed tx: [nonce, gasPrice, gasLimit, to, value, data, v, r, s]
        val signedFields = listOf(
            rlpEncodeInteger(nonce),
            rlpEncodeInteger(gasPriceWei),
            rlpEncodeInteger(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeInteger(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeInteger(v),
            rlpEncodeBytes(stripLeadingZeros(r)),
            rlpEncodeBytes(stripLeadingZeros(s))
        )
        val signedTx = rlpEncodeList(signedFields)
        return "0x" + signedTx.toHexString()
    }

    /**
     * Encode ERC-20 transfer(address, uint256) call data.
     * Function selector: 0xa9059cbb
     */
    fun encodeErc20Transfer(toAddress: String, amount: Long): ByteArray {
        val selector = "a9059cbb".fromHexToByteArray() // transfer(address,uint256)
        val addrBytes = toAddress.removePrefix("0x").fromHexToByteArray()
        // Pad address to 32 bytes (left-padded with zeros)
        val paddedAddr = ByteArray(32)
        addrBytes.copyInto(paddedAddr, 32 - addrBytes.size)
        // Encode amount as uint256 (32 bytes, big-endian)
        val amountBytes = longToBytes32(amount)
        return selector + paddedAddr + amountBytes
    }

    // ── RLP encoding ────────────────────────────────────────────────

    private fun rlpEncodeBytes(bytes: ByteArray): ByteArray {
        return when {
            bytes.size == 1 && bytes[0].toInt() and 0xFF < 0x80 -> bytes
            bytes.size <= 55 -> byteArrayOf((0x80 + bytes.size).toByte()) + bytes
            else -> {
                val lenBytes = toLengthBytes(bytes.size)
                byteArrayOf((0xB7 + lenBytes.size).toByte()) + lenBytes + bytes
            }
        }
    }

    private fun rlpEncodeInteger(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0x80.toByte())
        val bytes = stripLeadingZeros(longToBytesBigEndian(value))
        return rlpEncodeBytes(bytes)
    }

    private fun rlpEncodeList(items: List<ByteArray>): ByteArray {
        val payload = items.fold(byteArrayOf()) { acc, item -> acc + item }
        return if (payload.size <= 55) {
            byteArrayOf((0xC0 + payload.size).toByte()) + payload
        } else {
            val lenBytes = toLengthBytes(payload.size)
            byteArrayOf((0xF7 + lenBytes.size).toByte()) + lenBytes + payload
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun longToBytesBigEndian(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val result = mutableListOf<Byte>()
        var v = value
        while (v > 0) {
            result.add(0, (v and 0xFF).toByte())
            v = v ushr 8
        }
        return result.toByteArray()
    }

    private fun longToBytes32(value: Long): ByteArray {
        val result = ByteArray(32)
        var v = value
        for (i in 31 downTo 0) {
            result[i] = (v and 0xFF).toByte()
            v = v ushr 8
            if (v == 0L) break
        }
        return result
    }

    private fun stripLeadingZeros(bytes: ByteArray): ByteArray {
        var i = 0
        while (i < bytes.size - 1 && bytes[i] == 0.toByte()) i++
        return bytes.copyOfRange(i, bytes.size)
    }

    private fun toLengthBytes(length: Int): ByteArray {
        return stripLeadingZeros(longToBytesBigEndian(length.toLong()))
    }

    private fun findRecoveryId(hash: ByteArray, sig: ByteArray, privateKey: ByteArray): Int {
        val pubKey = Secp256k1.pubkeyCreate(privateKey)
        for (recId in 0..1) {
            try {
                val recovered = Secp256k1.ecdsaRecover(sig, hash, recId)
                if (recovered.contentEquals(pubKey)) return recId
            } catch (_: Exception) { }
        }
        return 0 // fallback
    }
}
