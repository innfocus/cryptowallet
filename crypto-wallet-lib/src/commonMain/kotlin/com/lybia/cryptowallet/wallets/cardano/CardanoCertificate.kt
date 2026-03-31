package com.lybia.cryptowallet.wallets.cardano

import com.lybia.cryptowallet.utils.cbor.CborValue

/**
 * Shelley-era delegation certificates for Cardano staking.
 *
 * CBOR encoding per Cardano ledger spec:
 * - StakeRegistration:  [0, [0, stakingKeyHash]]
 * - StakeDeregistration: [1, [0, stakingKeyHash]]
 * - Delegation:          [2, [0, stakingKeyHash], poolKeyHash]
 */
sealed class CardanoCertificate {

    /** Stake key registration (type 0) — deposit 2 ADA */
    data class StakeRegistration(val stakingKeyHash: ByteArray) : CardanoCertificate() {
        init {
            require(stakingKeyHash.size == 28) { "Staking key hash must be 28 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StakeRegistration) return false
            return stakingKeyHash.contentEquals(other.stakingKeyHash)
        }

        override fun hashCode(): Int = stakingKeyHash.contentHashCode()
    }

    /** Stake key deregistration (type 1) — refund 2 ADA */
    data class StakeDeregistration(val stakingKeyHash: ByteArray) : CardanoCertificate() {
        init {
            require(stakingKeyHash.size == 28) { "Staking key hash must be 28 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StakeDeregistration) return false
            return stakingKeyHash.contentEquals(other.stakingKeyHash)
        }

        override fun hashCode(): Int = stakingKeyHash.contentHashCode()
    }

    /** Delegation to pool (type 2) */
    data class Delegation(
        val stakingKeyHash: ByteArray,
        val poolKeyHash: ByteArray
    ) : CardanoCertificate() {
        init {
            require(stakingKeyHash.size == 28) { "Staking key hash must be 28 bytes" }
            require(poolKeyHash.size == 28) { "Pool key hash must be 28 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Delegation) return false
            return stakingKeyHash.contentEquals(other.stakingKeyHash) &&
                poolKeyHash.contentEquals(other.poolKeyHash)
        }

        override fun hashCode(): Int =
            stakingKeyHash.contentHashCode() * 31 + poolKeyHash.contentHashCode()
    }

    /**
     * Serialize this certificate to a CBOR value.
     */
    fun toCbor(): CborValue {
        return when (this) {
            is StakeRegistration -> CborValue.CborArray(listOf(
                CborValue.CborUInt(0u),
                CborValue.CborArray(listOf(
                    CborValue.CborUInt(0u),
                    CborValue.CborByteString(stakingKeyHash)
                ))
            ))
            is StakeDeregistration -> CborValue.CborArray(listOf(
                CborValue.CborUInt(1u),
                CborValue.CborArray(listOf(
                    CborValue.CborUInt(0u),
                    CborValue.CborByteString(stakingKeyHash)
                ))
            ))
            is Delegation -> CborValue.CborArray(listOf(
                CborValue.CborUInt(2u),
                CborValue.CborArray(listOf(
                    CborValue.CborUInt(0u),
                    CborValue.CborByteString(stakingKeyHash)
                )),
                CborValue.CborByteString(poolKeyHash)
            ))
        }
    }

    companion object {
        /**
         * Deserialize a CBOR value back into a [CardanoCertificate].
         */
        fun fromCbor(cbor: CborValue): CardanoCertificate {
            require(cbor is CborValue.CborArray) { "Certificate must be a CBOR array" }
            val items = cbor.items
            require(items.isNotEmpty()) { "Certificate array must not be empty" }

            val type = (items[0] as CborValue.CborUInt).value.toInt()
            return when (type) {
                0 -> {
                    val stakeCredential = (items[1] as CborValue.CborArray).items
                    val keyHash = (stakeCredential[1] as CborValue.CborByteString).bytes
                    StakeRegistration(keyHash)
                }
                1 -> {
                    val stakeCredential = (items[1] as CborValue.CborArray).items
                    val keyHash = (stakeCredential[1] as CborValue.CborByteString).bytes
                    StakeDeregistration(keyHash)
                }
                2 -> {
                    val stakeCredential = (items[1] as CborValue.CborArray).items
                    val stakingKeyHash = (stakeCredential[1] as CborValue.CborByteString).bytes
                    val poolKeyHash = (items[2] as CborValue.CborByteString).bytes
                    Delegation(stakingKeyHash, poolKeyHash)
                }
                else -> throw IllegalArgumentException("Unknown certificate type: $type")
            }
        }
    }
}
