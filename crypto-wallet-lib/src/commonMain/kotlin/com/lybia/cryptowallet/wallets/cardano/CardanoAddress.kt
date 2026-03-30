package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.Bech32
import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.CRC32
import com.lybia.cryptowallet.utils.SHA3
import com.lybia.cryptowallet.utils.cbor.CborDecoder
import com.lybia.cryptowallet.utils.cbor.CborEncoder
import com.lybia.cryptowallet.utils.cbor.CborValue
import fr.acinq.bitcoin.Base58

/**
 * Cardano address generation and validation for Byron and Shelley eras.
 */
class CardanoAddress {
    companion object {
        // Shelley header byte type nibbles (upper 4 bits)
        private const val TYPE_BASE: Int = 0x00
        private const val TYPE_ENTERPRISE: Int = 0x60
        private const val TYPE_REWARD: Int = 0xE0

        // Network IDs
        private const val NETWORK_MAINNET: Int = 0x01
        private const val NETWORK_TESTNET: Int = 0x00

        // Bech32 prefixes
        private const val PREFIX_MAINNET = "addr"
        private const val PREFIX_TESTNET = "addr_test"
        private const val PREFIX_REWARD_MAINNET = "stake"
        private const val PREFIX_REWARD_TESTNET = "stake_test"

        // ---- Byron Address Generation (Task 2.1) ----

        /**
         * Create a Byron-era address from an extended public key and chain code.
         *
         * Byron address structure:
         * Base58(CBOR([tag24(address_payload), crc32]))
         * where address_payload = CBOR([address_root, attributes_map, address_type])
         * and address_root = Blake2b-224(SHA3-256(CBOR([address_type, [address_type, xpub], {}])))
         *
         * @param publicKey Ed25519 public key (32 bytes)
         * @param chainCode chain code (32 bytes)
         * @return Base58-encoded Byron address
         */
        fun createByronAddress(publicKey: ByteArray, chainCode: ByteArray): String {
            require(publicKey.size == 32) { "Public key must be 32 bytes" }
            require(chainCode.size == 32) { "Chain code must be 32 bytes" }

            val encoder = CborEncoder()
            val xpub = publicKey + chainCode
            val addrType = CborValue.CborUInt(0u) // ATPubKey = 0

            // Build the address root input:
            // CBOR([addrType, [addrType, xpub], {}])
            val rootInput = CborValue.CborArray(listOf(
                addrType,
                CborValue.CborArray(listOf(
                    addrType,
                    CborValue.CborByteString(xpub)
                )),
                CborValue.CborMap(emptyList()) // empty attributes
            ))
            val rootBytes = encoder.encode(rootInput)
            val sha3Hash = SHA3.sha3_256(rootBytes)
            val addressRoot = Blake2b.hash(sha3Hash, 28)

            // Build address payload:
            // CBOR([address_root_bytes, {}, address_type])
            val addressPayload = CborValue.CborArray(listOf(
                CborValue.CborByteString(addressRoot),
                CborValue.CborMap(emptyList()), // empty attributes
                addrType
            ))
            val payloadBytes = encoder.encode(addressPayload)

            // Compute CRC32 of the payload
            val crc = CRC32.compute(payloadBytes)

            // Build final Byron address:
            // CBOR([tag24(payload_bytes), crc32])
            val byronAddress = CborValue.CborArray(listOf(
                CborValue.CborTag(24, CborValue.CborByteString(payloadBytes)),
                CborValue.CborUInt(crc.toULong())
            ))
            val addressBytes = encoder.encode(byronAddress)
            return Base58.encode(addressBytes)
        }

        // ---- Byron Address Validation (Task 2.2) ----

        /**
         * Validate a Byron address string.
         * Checks: Base58 decode, CBOR structure (array of [tag24(bytes), uint]), CRC32 checksum.
         * @return true if valid Byron address
         */
        fun isValidByronAddress(address: String): Boolean {
            return validateByronAddress(address) == null
        }

        /**
         * Validate a Byron address and return error description if invalid.
         * @return null if valid, error description string if invalid
         */
        fun validateByronAddress(address: String): String? {
            // Step 1: Base58 decode
            val decoded: ByteArray
            try {
                decoded = Base58.decode(address)
                if (decoded.isEmpty()) return "Invalid Base58 encoding"
            } catch (_: Exception) {
                return "Invalid Base58 encoding"
            }

            // Step 2: CBOR decode — expect array of 2 items
            val cborValue: CborValue
            try {
                cborValue = CborDecoder().decode(decoded)
            } catch (_: Exception) {
                return "Invalid CBOR structure"
            }

            if (cborValue !is CborValue.CborArray || cborValue.items.size != 2) {
                return "Invalid CBOR structure: expected array of 2 items"
            }

            // Step 3: First item must be tag 24 wrapping a byte string
            val taggedItem = cborValue.items[0]
            if (taggedItem !is CborValue.CborTag || taggedItem.tag != 24uL) {
                return "Invalid CBOR structure: expected tag 24"
            }
            if (taggedItem.content !is CborValue.CborByteString) {
                return "Invalid CBOR structure: tag 24 content must be byte string"
            }
            val payloadBytes = (taggedItem.content as CborValue.CborByteString).bytes

            // Step 4: Second item must be unsigned integer (CRC32)
            val crcItem = cborValue.items[1]
            if (crcItem !is CborValue.CborUInt) {
                return "Invalid CBOR structure: expected CRC32 unsigned integer"
            }
            val storedCrc = crcItem.value.toLong()

            // Step 5: Verify CRC32
            val computedCrc = CRC32.compute(payloadBytes)
            if (storedCrc != computedCrc) {
                return "CRC32 mismatch: stored=$storedCrc, computed=$computedCrc"
            }

            return null // valid
        }

        // ---- Shelley Address Generation (Task 2.3) ----

        /**
         * Create a Shelley base address (payment key + staking key).
         * Header byte: 0x00 | network_id (mainnet=1, testnet=0)
         * Payload: header(1) + payment_key_hash(28) + staking_key_hash(28) = 57 bytes
         *
         * @param paymentKeyHash Blake2b-224 hash of payment verification key (28 bytes)
         * @param stakingKeyHash Blake2b-224 hash of staking verification key (28 bytes)
         * @param isTestnet true for testnet, false for mainnet
         */
        fun createBaseAddress(
            paymentKeyHash: ByteArray,
            stakingKeyHash: ByteArray,
            isTestnet: Boolean = false
        ): String {
            require(paymentKeyHash.size == 28) { "Payment key hash must be 28 bytes" }
            require(stakingKeyHash.size == 28) { "Staking key hash must be 28 bytes" }

            val networkId = if (isTestnet) NETWORK_TESTNET else NETWORK_MAINNET
            val header = (TYPE_BASE or networkId).toByte()
            val payload = byteArrayOf(header) + paymentKeyHash + stakingKeyHash
            val prefix = if (isTestnet) PREFIX_TESTNET else PREFIX_MAINNET
            val data5bit = Bech32.convertBits(payload, 8, 5, true)
            return Bech32.encode(prefix, data5bit)
        }

        /**
         * Create a Shelley enterprise address (payment key only).
         * Header byte: 0x60 | network_id
         * Payload: header(1) + payment_key_hash(28) = 29 bytes
         */
        fun createEnterpriseAddress(
            paymentKeyHash: ByteArray,
            isTestnet: Boolean = false
        ): String {
            require(paymentKeyHash.size == 28) { "Payment key hash must be 28 bytes" }

            val networkId = if (isTestnet) NETWORK_TESTNET else NETWORK_MAINNET
            val header = (TYPE_ENTERPRISE or networkId).toByte()
            val payload = byteArrayOf(header) + paymentKeyHash
            val prefix = if (isTestnet) PREFIX_TESTNET else PREFIX_MAINNET
            val data5bit = Bech32.convertBits(payload, 8, 5, true)
            return Bech32.encode(prefix, data5bit)
        }

        /**
         * Create a Shelley reward (staking) address.
         * Header byte: 0xE0 | network_id
         * Payload: header(1) + staking_key_hash(28) = 29 bytes
         */
        fun createRewardAddress(
            stakingKeyHash: ByteArray,
            isTestnet: Boolean = false
        ): String {
            require(stakingKeyHash.size == 28) { "Staking key hash must be 28 bytes" }

            val networkId = if (isTestnet) NETWORK_TESTNET else NETWORK_MAINNET
            val header = (TYPE_REWARD or networkId).toByte()
            val payload = byteArrayOf(header) + stakingKeyHash
            val prefix = if (isTestnet) PREFIX_REWARD_TESTNET else PREFIX_REWARD_MAINNET
            val data5bit = Bech32.convertBits(payload, 8, 5, true)
            return Bech32.encode(prefix, data5bit)
        }

        // ---- Shelley Address Validation (Task 2.4) ----

        /**
         * Validate a Shelley address string.
         * Checks: Bech32 decode, prefix (addr/addr_test/stake/stake_test),
         * header byte, payload length.
         * @return true if valid Shelley address
         */
        fun isValidShelleyAddress(address: String): Boolean {
            return validateShelleyAddress(address) == null
        }

        /**
         * Validate a Shelley address and return error description if invalid.
         * @return null if valid, error description string if invalid
         */
        fun validateShelleyAddress(address: String): String? {
            // Step 1: Bech32 decode
            val hrp: String
            val data5bit: ByteArray
            try {
                val result = Bech32.decode(address)
                hrp = result.first
                data5bit = result.second
            } catch (e: Exception) {
                return "Invalid Bech32 encoding: ${e.message}"
            }

            // Step 2: Check prefix
            val validPrefixes = listOf(PREFIX_MAINNET, PREFIX_TESTNET, PREFIX_REWARD_MAINNET, PREFIX_REWARD_TESTNET)
            if (hrp !in validPrefixes) {
                return "Invalid prefix '$hrp': expected one of $validPrefixes"
            }

            // Step 3: Convert from 5-bit to 8-bit
            val payload: ByteArray
            try {
                payload = Bech32.convertBits(data5bit, 5, 8, false)
            } catch (e: Exception) {
                return "Invalid data encoding: ${e.message}"
            }

            if (payload.isEmpty()) {
                return "Empty payload"
            }

            // Step 4: Check header byte
            val header = payload[0].toInt() and 0xFF
            val typeNibble = header and 0xF0
            val networkId = header and 0x0F

            // Verify network matches prefix
            val isRewardPrefix = hrp == PREFIX_REWARD_MAINNET || hrp == PREFIX_REWARD_TESTNET
            val isMainnetPrefix = hrp == PREFIX_MAINNET || hrp == PREFIX_REWARD_MAINNET
            if (isMainnetPrefix && networkId != NETWORK_MAINNET) {
                return "Network mismatch: mainnet prefix but network ID $networkId"
            }
            if (!isMainnetPrefix && networkId != NETWORK_TESTNET) {
                return "Network mismatch: testnet prefix but network ID $networkId"
            }

            // Step 5: Check payload length based on address type
            return when (typeNibble) {
                TYPE_BASE -> {
                    if (isRewardPrefix) return "Base address should not use stake prefix"
                    if (payload.size != 57) "Invalid base address payload length: ${payload.size}, expected 57"
                    else null
                }
                TYPE_ENTERPRISE -> {
                    if (isRewardPrefix) return "Enterprise address should not use stake prefix"
                    if (payload.size != 29) "Invalid enterprise address payload length: ${payload.size}, expected 29"
                    else null
                }
                TYPE_REWARD -> {
                    if (!isRewardPrefix) return "Reward address should use stake/stake_test prefix"
                    if (payload.size != 29) "Invalid reward address payload length: ${payload.size}, expected 29"
                    else null
                }
                else -> "Unknown address type nibble: 0x${typeNibble.toString(16)}"
            }
        }

        // ---- General Validation ----

        /**
         * Check if a string is any valid Cardano address (Byron or Shelley).
         */
        fun isValidAddress(address: String): Boolean {
            return isValidByronAddress(address) || isValidShelleyAddress(address)
        }

        // ---- Address Type Detection (Task 2.5) ----

        /**
         * Determine the type of a Cardano address.
         */
        fun getAddressType(address: String): CardanoAddressType {
            // Try Byron first
            if (isValidByronAddress(address)) return CardanoAddressType.BYRON

            // Try Shelley
            try {
                val (hrp, data5bit) = Bech32.decode(address)
                val validPrefixes = listOf(PREFIX_MAINNET, PREFIX_TESTNET, PREFIX_REWARD_MAINNET, PREFIX_REWARD_TESTNET)
                if (hrp !in validPrefixes) return CardanoAddressType.UNKNOWN

                val payload = Bech32.convertBits(data5bit, 5, 8, false)
                if (payload.isEmpty()) return CardanoAddressType.UNKNOWN

                val header = payload[0].toInt() and 0xFF
                val typeNibble = header and 0xF0

                return when (typeNibble) {
                    TYPE_BASE -> if (payload.size == 57) CardanoAddressType.SHELLEY_BASE else CardanoAddressType.UNKNOWN
                    TYPE_ENTERPRISE -> if (payload.size == 29) CardanoAddressType.SHELLEY_ENTERPRISE else CardanoAddressType.UNKNOWN
                    TYPE_REWARD -> if (payload.size == 29) CardanoAddressType.SHELLEY_REWARD else CardanoAddressType.UNKNOWN
                    else -> CardanoAddressType.UNKNOWN
                }
            } catch (_: Exception) {
                return CardanoAddressType.UNKNOWN
            }
        }

        /**
         * Compute Blake2b-224 hash of a public key (used for Shelley key hashes).
         */
        fun hashKey(publicKey: ByteArray): ByteArray {
            return Blake2b.hash(publicKey, 28)
        }
    }
}
