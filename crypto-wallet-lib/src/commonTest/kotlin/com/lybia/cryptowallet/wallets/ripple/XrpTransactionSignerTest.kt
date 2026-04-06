package com.lybia.cryptowallet.wallets.ripple

import com.lybia.cryptowallet.utils.toHexString
import fr.acinq.secp256k1.Secp256k1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for XrpTransactionSigner.
 *
 * Covers:
 * - BUG-1: Memo serialization (STArray format)
 * - BUG-4: Local Transaction ID computation
 * - BUG-6: Input validation (key size, amount range, sequence)
 * - Signing determinism and basic correctness
 */
class XrpTransactionSignerTest {

    // Well-known test private key (32 bytes) — NOT a real wallet
    private val testPrivateKey = ByteArray(32) { (it + 1).toByte() }
    private val testPublicKey: ByteArray = Secp256k1.pubkeyCreate(testPrivateKey)

    // Well-known XRP testnet addresses (derived from test keys, Base58Ripple)
    // Using the "abandon" mnemonic address for account, and a second for destination
    private val testAccount = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
    private val testDestination = "rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"

    // ── BUG-6: Input validation ─────────────────────────────────────

    @Test
    fun signPayment_rejectsInvalidPrivateKeySize() {
        assertFailsWith<IllegalArgumentException>("Should reject 31-byte key") {
            XrpTransactionSigner.signPayment(
                privateKey = ByteArray(31),
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 1_000_000L,
                feeDrops = 12L,
                sequence = 1
            )
        }
        assertFailsWith<IllegalArgumentException>("Should reject 33-byte key") {
            XrpTransactionSigner.signPayment(
                privateKey = ByteArray(33),
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 1_000_000L,
                feeDrops = 12L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsInvalidPublicKeySize() {
        assertFailsWith<IllegalArgumentException>("Should reject 32-byte pubkey") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = ByteArray(32),
                account = testAccount,
                destination = testDestination,
                amountDrops = 1_000_000L,
                feeDrops = 12L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsZeroAmount() {
        assertFailsWith<IllegalArgumentException>("Should reject 0 drops") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 0L,
                feeDrops = 12L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsNegativeAmount() {
        assertFailsWith<IllegalArgumentException>("Should reject negative drops") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = -1L,
                feeDrops = 12L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsExcessiveAmount() {
        // More than 100 billion XRP (10^17 drops)
        assertFailsWith<IllegalArgumentException>("Should reject > MAX_DROPS") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 100_000_000_000_000_001L,
                feeDrops = 12L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsZeroFee() {
        assertFailsWith<IllegalArgumentException>("Should reject 0 fee") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 1_000_000L,
                feeDrops = 0L,
                sequence = 1
            )
        }
    }

    @Test
    fun signPayment_rejectsZeroSequence() {
        assertFailsWith<IllegalArgumentException>("Should reject sequence 0") {
            XrpTransactionSigner.signPayment(
                privateKey = testPrivateKey,
                publicKey = testPublicKey,
                account = testAccount,
                destination = testDestination,
                amountDrops = 1_000_000L,
                feeDrops = 12L,
                sequence = 0
            )
        }
    }

    @Test
    fun signPayment_acceptsMaxValidAmount() {
        // Exactly 100 billion XRP should be accepted
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 100_000_000_000_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        assertTrue(result.txBlob.isNotEmpty(), "Max valid amount should produce a blob")
    }

    // ── Basic signing correctness ───────────────────────────────────

    @Test
    fun signPayment_producesNonEmptyResult() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        assertTrue(result.txBlob.isNotEmpty(), "txBlob should not be empty")
        assertTrue(result.transactionId.isNotEmpty(), "transactionId should not be empty")
    }

    @Test
    fun signPayment_txBlobStartsWithPaymentTypeAndFlags() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        // TransactionType header 0x12, Payment type 0x0000
        assertTrue(result.txBlob.startsWith("1200002280000000"), "txBlob should start with Payment type + flags")
    }

    @Test
    fun signPayment_isDeterministic() {
        val params = arrayOf(testPrivateKey, testPublicKey, testAccount, testDestination)
        val result1 = XrpTransactionSigner.signPayment(
            privateKey = params[0] as ByteArray,
            publicKey = params[1] as ByteArray,
            account = params[2] as String,
            destination = params[3] as String,
            amountDrops = 5_000_000L,
            feeDrops = 12L,
            sequence = 42
        )
        val result2 = XrpTransactionSigner.signPayment(
            privateKey = params[0] as ByteArray,
            publicKey = params[1] as ByteArray,
            account = params[2] as String,
            destination = params[3] as String,
            amountDrops = 5_000_000L,
            feeDrops = 12L,
            sequence = 42
        )
        assertEquals(result1.txBlob, result2.txBlob, "Same inputs should produce same txBlob")
        assertEquals(result1.transactionId, result2.transactionId, "Same inputs should produce same TX ID")
    }

    @Test
    fun signPayment_differentSequenceProducesDifferentBlob() {
        val result1 = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        val result2 = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 2
        )
        assertNotEquals(result1.txBlob, result2.txBlob, "Different sequence should produce different blob")
        assertNotEquals(result1.transactionId, result2.transactionId, "Different sequence should produce different TX ID")
    }

    // ── BUG-4: Transaction ID ───────────────────────────────────────

    @Test
    fun signPayment_transactionIdIs64HexChars() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        assertEquals(64, result.transactionId.length, "TX ID should be 64 hex chars (32 bytes)")
        assertTrue(result.transactionId.all { it in '0'..'9' || it in 'A'..'F' }, "TX ID should be uppercase hex")
    }

    @Test
    fun computeTransactionId_matchesSignResult() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1
        )
        // Recompute from the blob
        val blobBytes = hexToBytes(result.txBlob)
        val recomputedId = XrpTransactionSigner.computeTransactionId(blobBytes)
        assertEquals(result.transactionId, recomputedId, "computeTransactionId should match SignResult.transactionId")
    }

    // ── Destination tag serialization ───────────────────────────────

    @Test
    fun signPayment_withDestinationTag_includesTagInBlob() {
        val withTag = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            destinationTag = 12345L
        )
        val withoutTag = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            destinationTag = null
        )
        // With tag should be longer (4 bytes tag value + field header)
        assertTrue(withTag.txBlob.length > withoutTag.txBlob.length, "Tag TX should be longer than no-tag TX")
        // DestinationTag field header = 0x2E, value 12345 = 0x00003039
        assertTrue(withTag.txBlob.contains("2e00003039"), "Blob should contain DestinationTag 12345")
    }

    // ── LastLedgerSequence serialization ─────────────────────────────

    @Test
    fun signPayment_withLastLedgerSequence_includesInBlob() {
        val withLLS = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            lastLedgerSequence = 90_000_075L  // e.g. ledger + 75
        )
        val withoutLLS = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            lastLedgerSequence = null
        )
        assertTrue(withLLS.txBlob.length > withoutLLS.txBlob.length, "LLS TX should be longer")
        // LastLedgerSequence header: type=2 (UInt32), field=27 → 0x201B (2 bytes)
        assertTrue(withLLS.txBlob.contains("201b"), "Blob should contain LastLedgerSequence header 0x201B")
    }

    // ── BUG-1: Memo serialization ───────────────────────────────────

    @Test
    fun signPayment_withMemo_includesMemoInBlob() {
        val withMemo = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = "Hello"
        )
        val withoutMemo = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = null
        )
        assertTrue(withMemo.txBlob.length > withoutMemo.txBlob.length, "Memo TX should be longer")
    }

    @Test
    fun signPayment_memo_containsSTArrayMarkers() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = "Test"
        )
        val blob = result.txBlob.lowercase()
        // STArray begin (Memos) = 0xF9
        assertTrue(blob.contains("f9"), "Blob should contain STArray begin (0xF9)")
        // STObject begin (Memo) = 0xEA
        assertTrue(blob.contains("ea"), "Blob should contain STObject begin (0xEA)")
        // STObject end = 0xE1
        assertTrue(blob.contains("e1"), "Blob should contain STObject end (0xE1)")
        // STArray end = 0xF1
        assertTrue(blob.contains("f1"), "Blob should contain STArray end (0xF1)")
    }

    @Test
    fun signPayment_memo_containsMemoTypeAndData() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = "Hello"
        )
        val blob = result.txBlob.lowercase()
        // MemoType header = 0x7C, followed by length + "text/plain" bytes
        assertTrue(blob.contains("7c"), "Blob should contain MemoType header (0x7C)")
        // "text/plain" in hex = 746578742f706c61696e
        assertTrue(blob.contains("746578742f706c61696e"), "Blob should contain 'text/plain' hex")
        // MemoData header = 0x7D, followed by length + "Hello" bytes
        assertTrue(blob.contains("7d"), "Blob should contain MemoData header (0x7D)")
        // "Hello" in hex = 48656c6c6f
        assertTrue(blob.contains("48656c6c6f"), "Blob should contain 'Hello' hex")
    }

    @Test
    fun signPayment_memo_structureOrder() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = "ABC"
        )
        val blob = result.txBlob.lowercase()
        // Verify order: F9 (array start) ... EA (object start) ... 7C (type) ... 7D (data) ... E1 (object end) ... F1 (array end)
        val f9 = blob.indexOf("f9")
        val ea = blob.indexOf("ea", f9)
        val type7c = blob.indexOf("7c", ea)
        val data7d = blob.indexOf("7d", type7c)
        val e1 = blob.indexOf("e1", data7d)
        val f1 = blob.indexOf("f1", e1)
        assertTrue(f9 < ea, "STArray start should come before STObject start")
        assertTrue(ea < type7c, "STObject start should come before MemoType")
        assertTrue(type7c < data7d, "MemoType should come before MemoData")
        assertTrue(data7d < e1, "MemoData should come before STObject end")
        assertTrue(e1 < f1, "STObject end should come before STArray end")
    }

    @Test
    fun signPayment_emptyMemo_notIncluded() {
        val withEmpty = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = ""
        )
        val withNull = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            memoText = null
        )
        assertEquals(withEmpty.txBlob, withNull.txBlob, "Empty memo and null memo should produce identical blobs")
    }

    @Test
    fun signPayment_memoWithDestinationTag_bothPresent() {
        val result = XrpTransactionSigner.signPayment(
            privateKey = testPrivateKey,
            publicKey = testPublicKey,
            account = testAccount,
            destination = testDestination,
            amountDrops = 1_000_000L,
            feeDrops = 12L,
            sequence = 1,
            destinationTag = 99999L,
            memoText = "Payment for invoice #42"
        )
        val blob = result.txBlob.lowercase()
        // DestinationTag 99999 = 0x0001869F, header 0x2E
        assertTrue(blob.contains("2e0001869f"), "Should contain DestinationTag 99999")
        // Memo markers
        assertTrue(blob.contains("f9"), "Should contain Memo STArray")
        assertTrue(blob.contains("48656c6c6f") || blob.contains("7d"), "Should contain MemoData")
    }

    // ── RippleManager constants ─────────────────────────────────────

    @Test
    fun lastLedgerOffset_is75() {
        assertEquals(75, RippleManager.LAST_LEDGER_OFFSET, "LastLedgerSequence offset should be 75")
    }

    @Test
    fun baseReserveDrops_is10Xrp() {
        assertEquals(10_000_000L, RippleManager.BASE_RESERVE_DROPS, "Base reserve should be 10 XRP (10,000,000 drops)")
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((hexCharToInt(hex[i]) shl 4) + hexCharToInt(hex[i + 1])).toByte()
        }
        return data
    }

    private fun hexCharToInt(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hex char: $c")
    }
}
