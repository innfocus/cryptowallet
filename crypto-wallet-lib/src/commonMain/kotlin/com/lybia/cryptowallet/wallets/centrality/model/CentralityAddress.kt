package com.lybia.cryptowallet.wallets.centrality.model

import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.fromHexToByteArray
import fr.acinq.bitcoin.Base58

/**
 * SS58 address parser and validator for CennzNet.
 * Replaces CennzAddress from androidMain.
 *
 * Uses:
 * - fr.acinq.bitcoin.Base58 (instead of Base58 from hdwallet)
 * - com.lybia.cryptowallet.utils.Blake2b (instead of blake2b from hdwallet)
 * - com.lybia.cryptowallet.utils.fromHexToByteArray (instead of hdwallet extension)
 */
class CentralityAddress {
    val address: String
    val publicKey: ByteArray?

    /**
     * Parse SS58 address string, extract public key.
     * Returns null publicKey if address is invalid.
     */
    constructor(address: String) {
        this.address = address
        this.publicKey = parseAddress(address)
    }

    /**
     * Create from address string and known public key hex string.
     */
    constructor(address: String, publicKeyHex: String) {
        this.address = address
        this.publicKey = publicKeyHex.removePrefix("0x").fromHexToByteArray()
    }

    fun isValid(): Boolean = publicKey != null

    companion object {
        private val ALLOWED_ENCODED_LENGTHS = intArrayOf(3, 4, 6, 10, 35, 36, 37, 38)
        private val SS58_PREFIX = "SS58PRE".encodeToByteArray()

        /**
         * Parse SS58 address, validate checksum, extract public key.
         *
         * Algorithm:
         * 1. Base58 decode the address string
         * 2. Check decoded length is in allowed set
         * 3. Determine ss58Length (1 or 2 bytes) from first byte bit 6
         * 4. Determine if public key based on decoded size
         * 5. Extract key prefix (decoded[0..length])
         * 6. Compute Blake2b-512 of "SS58PRE" + key prefix
         * 7. Validate checksum bytes
         * 8. Return public key bytes or null
         */
        /**
         * Encode a public key into an SS58 address string.
         *
         * Algorithm (inverse of parseAddress):
         * 1. Prepend network prefix byte to public key
         * 2. Compute Blake2b-512 of "SS58PRE" + prefix + publicKey
         * 3. Append first 2 checksum bytes
         * 4. Base58 encode the result
         *
         * @param publicKey 32-byte public key
         * @param networkPrefix SS58 network prefix byte (default 42 for Substrate generic)
         * @return SS58 encoded address string
         */
        fun encodeSS58(publicKey: ByteArray, networkPrefix: Byte = 42): String {
            val payload = byteArrayOf(networkPrefix) + publicKey
            val hash = Blake2b.hash(SS58_PREFIX + payload, 64)
            val complete = payload + hash.copyOfRange(0, 2)
            return Base58.encode(complete)
        }

        fun parseAddress(address: String): ByteArray? {
            val decoded: ByteArray
            try {
                decoded = Base58.decode(address)
            } catch (_: Exception) {
                return null
            }

            if (!ALLOWED_ENCODED_LENGTHS.contains(decoded.size)) {
                return null
            }

            val ss58Length = if (decoded[0].toInt().and(0b01000000) != 0) 2 else 1

            val isPublicKey = intArrayOf(34 + ss58Length, 35 + ss58Length).contains(decoded.size)
            val length = if (isPublicKey) decoded.size - 2 else decoded.size - 1

            val key = decoded.copyOfRange(0, length)

            val ssHash = SS58_PREFIX + key
            val hash = Blake2b.hash(ssHash, 64)

            val firstCondition = decoded[0].toInt().and(0b1000_0000) == 0
            val secondCondition = !intArrayOf(46, 47).contains(decoded[0].toInt())
            val thirdCondition = if (isPublicKey) {
                decoded[decoded.size - 2] == hash[0] && decoded[decoded.size - 1] == hash[1]
            } else {
                decoded[decoded.size - 1] == hash[0]
            }

            val isValid = firstCondition && secondCondition && thirdCondition
            return if (isValid) decoded.copyOfRange(ss58Length, length) else null
        }
    }
}
