package com.lybia.cryptowallet.wallets.ripple

import com.lybia.cryptowallet.utils.ACTCrypto
import com.lybia.cryptowallet.utils.Base58Ext
import com.lybia.cryptowallet.utils.toHexString
import fr.acinq.secp256k1.Secp256k1

/**
 * Pure-Kotlin XRP Ledger transaction signer.
 * Builds Payment transactions, serializes to binary, signs with secp256k1,
 * and produces a tx_blob hex string ready for submit RPC.
 *
 * Reference: https://xrpl.org/serialization.html
 */
object XrpTransactionSigner {

    // ── XRP Ledger field type codes ─────────────────────────────────

    // Type IDs
    private const val ST_UINT16 = 1
    private const val ST_UINT32 = 2
    private const val ST_AMOUNT = 6
    private const val ST_VL = 7    // Variable-length (Blob)
    private const val ST_ACCOUNT = 8

    // Field IDs for Payment transaction
    private const val FIELD_TRANSACTION_TYPE = 2   // UInt16
    private const val FIELD_FLAGS = 2              // UInt32
    private const val FIELD_SEQUENCE = 4           // UInt32
    private const val FIELD_DESTINATION_TAG = 14   // UInt32
    private const val FIELD_LAST_LEDGER_SEQ = 27   // UInt32
    private const val FIELD_AMOUNT = 1             // Amount
    private const val FIELD_FEE = 8                // Amount
    private const val FIELD_SIGNING_PUB_KEY = 3    // VL (Blob)
    private const val FIELD_TXN_SIGNATURE = 4      // VL (Blob)
    private const val FIELD_ACCOUNT = 1            // Account
    private const val FIELD_DESTINATION = 3        // Account

    // Memo STArray/STObject markers (XRP Ledger serialization spec)
    private const val ST_OBJECT_END = 0xE1.toByte()  // End of STObject
    private const val ST_ARRAY_END = 0xF1.toByte()   // End of STArray
    private val MEMO_ARRAY_START = byteArrayOf(0xF9.toByte())   // STArray field 9 (Memos)
    private val MEMO_OBJECT_START = byteArrayOf(0xEA.toByte())  // STObject field 10 (Memo)
    private val MEMO_DATA_HEADER = byteArrayOf(0x7D.toByte())   // VL field 13, type 7 (MemoData)
    private val MEMO_TYPE_HEADER = byteArrayOf(0x7C.toByte())   // VL field 12, type 7 (MemoType)

    // Transaction type: Payment = 0
    private const val TT_PAYMENT = 0

    // Hash prefixes
    private val HASH_PREFIX_SIGN = byteArrayOf(0x53, 0x54, 0x58, 0x00) // "STX\0"
    private val HASH_PREFIX_TX_ID = byteArrayOf(0x54, 0x58, 0x4E, 0x00) // "TXN\0"

    /** Maximum XRP supply in drops (100 billion XRP) */
    private const val MAX_DROPS = 100_000_000_000_000_000L // 10^17

    /**
     * Result of signing a Payment transaction.
     * @param txBlob Hex-encoded signed transaction blob for submit RPC
     * @param transactionId Locally computed transaction hash (SHA-512Half of TXN\0 + signed blob)
     */
    data class SignResult(val txBlob: String, val transactionId: String)

    /**
     * Build, serialize, sign, and return a hex-encoded XRP Payment transaction blob
     * together with a locally computed transaction ID.
     *
     * @param privateKey 32-byte secp256k1 private key
     * @param publicKey 33-byte compressed secp256k1 public key
     * @param account Sender r-address
     * @param destination Recipient r-address
     * @param amountDrops Amount in drops (1 XRP = 1,000,000 drops)
     * @param feeDrops Fee in drops
     * @param sequence Account sequence number
     * @param destinationTag Optional destination tag
     * @param lastLedgerSequence Optional last ledger sequence for expiry
     * @param memoText Optional memo text (will be hex-encoded as MemoData with MemoType "text/plain")
     * @return SignResult containing txBlob and transactionId
     */
    fun signPayment(
        privateKey: ByteArray,
        publicKey: ByteArray,
        account: String,
        destination: String,
        amountDrops: Long,
        feeDrops: Long,
        sequence: Long,
        destinationTag: Long? = null,
        lastLedgerSequence: Long? = null,
        memoText: String? = null
    ): SignResult {
        // Input validation
        require(privateKey.size == 32) { "Private key must be 32 bytes, got ${privateKey.size}" }
        require(publicKey.size == 33) { "Public key must be 33 bytes (compressed), got ${publicKey.size}" }
        require(amountDrops in 1..MAX_DROPS) { "Amount must be 1..$MAX_DROPS drops, got $amountDrops" }
        require(feeDrops in 1..1_000_000_000L) { "Fee must be 1..1,000,000,000 drops, got $feeDrops" }
        require(sequence >= 1) { "Sequence must be >= 1, got $sequence" }

        // Build serialized fields for signing (without TxnSignature)
        val fields = buildPaymentFields(
            publicKey, account, destination,
            amountDrops, feeDrops, sequence,
            destinationTag, lastLedgerSequence,
            signature = null,
            memoText = memoText
        )

        // Hash: SHA-512Half(HASH_PREFIX_SIGN + serialized)
        val signingData = HASH_PREFIX_SIGN + fields
        val hash = sha512Half(signingData)

        // Sign with secp256k1 (DER-encoded signature)
        val sigBytes = Secp256k1.sign(hash, privateKey)
        val derSig = Secp256k1.compact2der(sigBytes)

        // Rebuild with signature included
        val signedFields = buildPaymentFields(
            publicKey, account, destination,
            amountDrops, feeDrops, sequence,
            destinationTag, lastLedgerSequence,
            signature = derSig,
            memoText = memoText
        )

        val txBlob = signedFields.toHexString()
        val transactionId = computeTransactionId(signedFields)

        return SignResult(txBlob, transactionId)
    }

    /**
     * Compute the transaction ID (hash) from the signed transaction bytes.
     * TransactionID = SHA-512Half(0x54584E00 + signedBlob)
     */
    fun computeTransactionId(signedBlob: ByteArray): String {
        return sha512Half(HASH_PREFIX_TX_ID + signedBlob).toHexString().uppercase()
    }

    // ── Serialization ───────────────────────────────────────────────

    private fun buildPaymentFields(
        publicKey: ByteArray,
        account: String,
        destination: String,
        amountDrops: Long,
        feeDrops: Long,
        sequence: Long,
        destinationTag: Long?,
        lastLedgerSequence: Long?,
        signature: ByteArray?,
        memoText: String? = null
    ): ByteArray {
        var result = byteArrayOf()

        // TransactionType (UInt16, field 2) — type 1, field 2 → header byte 0x12
        result += encodeFieldHeader(ST_UINT16, FIELD_TRANSACTION_TYPE)
        result += encodeUInt16(TT_PAYMENT)

        // Flags (UInt32, field 2) — tfFullyCanonicalSig = 0x80000000
        result += encodeFieldHeader(ST_UINT32, FIELD_FLAGS)
        result += encodeUInt32(0x80000000L)

        // Sequence (UInt32, field 4)
        result += encodeFieldHeader(ST_UINT32, FIELD_SEQUENCE)
        result += encodeUInt32(sequence)

        // DestinationTag (UInt32, field 14) — optional
        if (destinationTag != null) {
            result += encodeFieldHeader(ST_UINT32, FIELD_DESTINATION_TAG)
            result += encodeUInt32(destinationTag)
        }

        // LastLedgerSequence (UInt32, field 27) — optional
        if (lastLedgerSequence != null) {
            result += encodeFieldHeader(ST_UINT32, FIELD_LAST_LEDGER_SEQ)
            result += encodeUInt32(lastLedgerSequence)
        }

        // Amount (Amount, field 1) — XRP drops
        result += encodeFieldHeader(ST_AMOUNT, FIELD_AMOUNT)
        result += encodeXrpAmount(amountDrops)

        // Fee (Amount, field 8) — XRP drops
        result += encodeFieldHeader(ST_AMOUNT, FIELD_FEE)
        result += encodeXrpAmount(feeDrops)

        // SigningPubKey (VL, field 3)
        result += encodeFieldHeader(ST_VL, FIELD_SIGNING_PUB_KEY)
        result += encodeVL(publicKey)

        // TxnSignature (VL, field 4) — only in signed tx
        if (signature != null) {
            result += encodeFieldHeader(ST_VL, FIELD_TXN_SIGNATURE)
            result += encodeVL(signature)
        }

        // Account (Account, field 1)
        result += encodeFieldHeader(ST_ACCOUNT, FIELD_ACCOUNT)
        result += encodeAccount(account)

        // Destination (Account, field 3)
        result += encodeFieldHeader(ST_ACCOUNT, FIELD_DESTINATION)
        result += encodeAccount(destination)

        // Memos (STArray) — optional
        if (!memoText.isNullOrEmpty()) {
            result += encodeMemos(memoText)
        }

        return result
    }

    /**
     * Encode Memos as an STArray containing one Memo STObject.
     * Format: STArray(Memos) → STObject(Memo) → MemoType + MemoData → EndObject → EndArray
     *
     * MemoType = "text/plain" (hex-encoded)
     * MemoData = memoText bytes (UTF-8, hex-encoded in the field)
     *
     * Reference: https://xrpl.org/transaction-common-fields.html#memos-field
     */
    private fun encodeMemos(memoText: String): ByteArray {
        val memoDataBytes = memoText.encodeToByteArray()
        val memoTypeBytes = "text/plain".encodeToByteArray()

        var result = byteArrayOf()
        // STArray begin — Memos field (type 15, field 9 → 0xF9)
        result += MEMO_ARRAY_START
        // STObject begin — Memo field (type 14, field 10 → 0xEA)
        result += MEMO_OBJECT_START
        // MemoType (VL field 12, type 7 → 0x7C)
        result += MEMO_TYPE_HEADER
        result += encodeVLLength(memoTypeBytes.size)
        result += memoTypeBytes
        // MemoData (VL field 13, type 7 → 0x7D)
        result += MEMO_DATA_HEADER
        result += encodeVLLength(memoDataBytes.size)
        result += memoDataBytes
        // STObject end
        result += ST_OBJECT_END
        // STArray end
        result += ST_ARRAY_END
        return result
    }

    // ── Field header encoding ───────────────────────────────────────

    private fun encodeFieldHeader(typeCode: Int, fieldCode: Int): ByteArray {
        return if (typeCode < 16 && fieldCode < 16) {
            byteArrayOf(((typeCode shl 4) or fieldCode).toByte())
        } else if (typeCode < 16) {
            byteArrayOf((typeCode shl 4).toByte(), fieldCode.toByte())
        } else if (fieldCode < 16) {
            byteArrayOf(fieldCode.toByte(), typeCode.toByte())
        } else {
            byteArrayOf(0x00, typeCode.toByte(), fieldCode.toByte())
        }
    }

    // ── Type encoders ───────────────────────────────────────────────

    private fun encodeUInt16(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun encodeUInt32(value: Long): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    /**
     * Encode XRP amount (drops) as 8 bytes.
     * Bit 63 = 0 (not IOU), bit 62 = 1 (positive), bits 0-61 = drops value.
     */
    private fun encodeXrpAmount(drops: Long): ByteArray {
        // Set bit 62 (positive flag) for positive amounts
        val encoded = drops or 0x4000000000000000L
        val result = ByteArray(8)
        for (i in 7 downTo 0) {
            result[7 - i] = ((encoded shr (i * 8)) and 0xFF).toByte()
        }
        return result
    }

    /**
     * Encode variable-length data (length prefix + data).
     */
    private fun encodeVL(data: ByteArray): ByteArray {
        val lenPrefix = encodeVLLength(data.size)
        return lenPrefix + data
    }

    private fun encodeVLLength(length: Int): ByteArray {
        return when {
            length <= 192 -> byteArrayOf(length.toByte())
            length <= 12480 -> {
                val adjusted = length - 193
                byteArrayOf(
                    (193 + (adjusted shr 8)).toByte(),
                    (adjusted and 0xFF).toByte()
                )
            }
            else -> {
                val adjusted = length - 12481
                byteArrayOf(
                    (241 + (adjusted shr 16)).toByte(),
                    ((adjusted shr 8) and 0xFF).toByte(),
                    (adjusted and 0xFF).toByte()
                )
            }
        }
    }

    /**
     * Encode an XRP account (r-address) as VL-encoded AccountID (20 bytes).
     */
    private fun encodeAccount(rAddress: String): ByteArray {
        val accountId = decodeRAddress(rAddress)
        return encodeVL(accountId)
    }

    // ── Address encoding/decoding ───────────────────────────────────

    /**
     * Decode an XRP r-address to 20-byte AccountID.
     * r-address = Base58Check with version byte 0x00.
     * Uses Base58Ext with Ripple alphabet (shared utility).
     */
    private fun decodeRAddress(rAddress: String): ByteArray {
        val decoded = Base58Ext.decode(rAddress, Base58Ext.Base58Type.Ripple)
        // decoded = [version(1)] + [payload(20)] + [checksum(4)]
        require(decoded.size == 25) { "Invalid r-address length: ${decoded.size}" }
        require(decoded[0] == 0x00.toByte()) { "Invalid r-address version byte" }
        // Verify checksum
        val payload = decoded.copyOfRange(0, 21)
        val checksum = decoded.copyOfRange(21, 25)
        val computed = ACTCrypto.doubleSHA256(payload).copyOfRange(0, 4)
        require(checksum.contentEquals(computed)) { "Invalid r-address checksum" }
        return decoded.copyOfRange(1, 21)
    }

    // ── Hashing ─────────────────────────────────────────────────────

    /**
     * SHA-512 first half (first 32 bytes of SHA-512).
     * XRP Ledger uses this for transaction hashing.
     */
    private fun sha512Half(data: ByteArray): ByteArray {
        val full = korlibs.crypto.SHA512.digest(data).bytes
        return full.copyOfRange(0, 32)
    }
}
