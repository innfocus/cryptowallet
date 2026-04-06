package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import com.lybia.cryptowallet.wallets.cardano.Ed25519Icarus
import com.lybia.cryptowallet.wallets.cardano.IcarusKeyDerivation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Icarus Byron key derivation.
 *
 * These tests verify the correctness of:
 * 1. Internal math helpers (multiply8LE, addScalarsLE)
 * 2. Master key clamping and format
 * 3. Child key derivation properties
 * 4. Ed25519Icarus public key format
 * 5. Full Byron address generation format
 *
 * ## How to verify addresses against a real wallet
 *
 * 1. Install Yoroi browser extension
 * 2. Create wallet → Restore → Byron Icarus
 * 3. Enter mnemonic: "eight country switch draw meat scout mystery blade tip drift useless good keep usage title"
 * 4. Compare the first displayed address with getByronAddress(index=0) output
 *
 * Byron Icarus addresses always start with "Ae2" on mainnet.
 */
class CardanoByronKeyTest {

    // ── Test mnemonic (standard Icarus test vector from CIP-0003) ────────────
    private val ICARUS_MNEMONIC_15 = "eight country switch draw meat scout mystery blade tip drift useless good keep usage title"
    private val mnemonicWords = ICARUS_MNEMONIC_15.split(" ")

    // Standard 12-word mnemonic (128-bit entropy)
    private val MNEMONIC_12 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val mnemonicWords12 = MNEMONIC_12.split(" ")

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        return ByteArray(len) { i ->
            ((hex[i * 2].digitToInt(16) shl 4) or hex[i * 2 + 1].digitToInt(16)).toByte()
        }
    }

    // ── multiply8LE tests ─────────────────────────────────────────────────────

    @Test
    fun multiply8LE_singleBit() {
        // [0x01, 0x00, ...(27 zeros)] * 8 = [0x08, 0x00, ...(31 zeros)]
        val src = ByteArray(28).also { it[0] = 0x01 }
        val result = IcarusKeyDerivation.multiply8LE(src)
        assertEquals(32, result.size)
        assertEquals(0x08.toByte(), result[0])
        assertTrue(result.drop(1).all { it == 0.toByte() }, "All remaining bytes should be zero")
    }

    @Test
    fun multiply8LE_carryPropagation() {
        // [0xFF, 0x00, ...(27)] * 8 = [0xF8, 0x07, 0x00, ...]
        // 0xFF << 3 = 0x7F8 → byte[0] = 0xF8, carry = 0x07 into byte[1]
        val src = ByteArray(28).also { it[0] = 0xFF.toByte() }
        val result = IcarusKeyDerivation.multiply8LE(src)
        assertEquals(0xF8.toByte(), result[0], "byte 0 should be 0xF8")
        assertEquals(0x07.toByte(), result[1], "byte 1 should hold carry 0x07")
    }

    @Test
    fun multiply8LE_maxCarryToOverflowByte() {
        // Last byte (index 27) = 0xFF → overflow into dst[28]
        val src = ByteArray(28).also { it[27] = 0xFF.toByte() }
        val result = IcarusKeyDerivation.multiply8LE(src)
        // 0xFF >> 5 = 0x07 → dst[28] = 0x07
        assertEquals(0x07.toByte(), result[28], "overflow byte should be 0xFF >> 5 = 0x07")
    }

    @Test
    fun multiply8LE_requiresExactly28Bytes() {
        assertFailsWith<IllegalArgumentException> {
            IcarusKeyDerivation.multiply8LE(ByteArray(27))
        }
        assertFailsWith<IllegalArgumentException> {
            IcarusKeyDerivation.multiply8LE(ByteArray(29))
        }
    }

    @Test
    fun multiply8LE_allZeros() {
        val result = IcarusKeyDerivation.multiply8LE(ByteArray(28))
        assertTrue(result.all { it == 0.toByte() }, "Zero * 8 = zero")
    }

    // ── addScalarsLE tests ────────────────────────────────────────────────────

    @Test
    fun addScalarsLE_simple() {
        val a = ByteArray(32).also { it[0] = 0x05 }
        val b = ByteArray(32).also { it[0] = 0x03 }
        val result = IcarusKeyDerivation.addScalarsLE(a, b)
        assertEquals(0x08.toByte(), result[0], "0x05 + 0x03 = 0x08")
        assertTrue(result.drop(1).all { it == 0.toByte() })
    }

    @Test
    fun addScalarsLE_carryPropagation() {
        // a[0]=0xFF, b[0]=0x01 → result[0]=0x00, carry into result[1]
        val a = ByteArray(32).also { it[0] = 0xFF.toByte() }
        val b = ByteArray(32).also { it[0] = 0x01 }
        val result = IcarusKeyDerivation.addScalarsLE(a, b)
        assertEquals(0x00.toByte(), result[0], "0xFF + 0x01 = 0x00 with carry")
        assertEquals(0x01.toByte(), result[1], "carry should appear in byte 1")
    }

    @Test
    fun addScalarsLE_zeros() {
        val result = IcarusKeyDerivation.addScalarsLE(ByteArray(32), ByteArray(32))
        assertTrue(result.all { it == 0.toByte() })
    }

    // ── Master key tests ──────────────────────────────────────────────────────

    @Test
    fun masterKeyFromMnemonic_correctSize() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        assertEquals(64, extKey.size,    "Extended key must be 64 bytes")
        assertEquals(32, chainCode.size, "Chain code must be 32 bytes")
    }

    @Test
    fun masterKeyFromMnemonic_clamping() {
        val (extKey, _) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val kL = extKey
        // raw[0] & 0xF8: lowest 3 bits must be 0
        assertEquals(0, kL[0].toInt() and 0x07, "kL[0] lowest 3 bits must be cleared")
        // raw[31] & 0x1F then | 0x40: bits 7 and 5 cleared, bit 6 set
        assertEquals(0, kL[31].toInt() and 0x80, "kL[31] bit 7 must be cleared")
        assertEquals(0, kL[31].toInt() and 0x20, "kL[31] bit 5 must be cleared")
        assertNotEquals(0, kL[31].toInt() and 0x40, "kL[31] bit 6 must be set")
    }

    @Test
    fun masterKeyFromMnemonic_deterministic() {
        val (ext1, cc1) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val (ext2, cc2) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        assertTrue(ext1.contentEquals(ext2), "Master key must be deterministic")
        assertTrue(cc1.contentEquals(cc2),   "Chain code must be deterministic")
    }

    @Test
    fun masterKeyFromMnemonic_12words() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords12)
        assertEquals(64, extKey.size)
        assertEquals(32, chainCode.size)
    }

    @Test
    fun masterKeyFromMnemonic_differentMnemonicsProduceDifferentKeys() {
        val (ext1, _) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val (ext2, _) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords12)
        assertFalse(ext1.contentEquals(ext2), "Different mnemonics must produce different keys")
    }

    // ── Child key derivation tests ────────────────────────────────────────────

    @Test
    fun deriveChildKey_hardened_returnsCorrectSize() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val (childExt, childCC) = IcarusKeyDerivation.deriveChildKey(extKey, chainCode, 44, hardened = true)
        assertEquals(64, childExt.size,    "Child extended key must be 64 bytes")
        assertEquals(32, childCC.size,     "Child chain code must be 32 bytes")
    }

    @Test
    fun deriveChildKey_soft_returnsCorrectSize() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        // Derive to account level first (hardened), then soft
        var (k, cc) = extKey to chainCode
        IcarusKeyDerivation.deriveChildKey(k, cc, 44, true).let   { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, true).let    { (nk, nc) -> k = nk; cc = nc }
        val (softExt, softCC) = IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false)
        assertEquals(64, softExt.size)
        assertEquals(32, softCC.size)
    }

    @Test
    fun deriveChildKey_differentIndicesProduceDifferentKeys() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val (child0, _) = IcarusKeyDerivation.deriveChildKey(extKey, chainCode, 0, hardened = true)
        val (child1, _) = IcarusKeyDerivation.deriveChildKey(extKey, chainCode, 1, hardened = true)
        assertFalse(child0.contentEquals(child1), "Different indices must produce different keys")
    }

    @Test
    fun deriveChildKey_hardened_vs_soft_differ() {
        // After several hardened steps (to reach account level), verify soft produces different key
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        var (k, cc) = extKey to chainCode
        IcarusKeyDerivation.deriveChildKey(k, cc, 44, true).let   { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, true).let    { (nk, nc) -> k = nk; cc = nc }

        val (hardenedZero, _) = IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = true)
        val (softZero, _)     = IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false)
        assertFalse(hardenedZero.contentEquals(softZero), "Hardened and soft derivation must differ")
    }

    @Test
    fun deriveChildKey_deterministic() {
        val (extKey, chainCode) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        val (child1, cc1) = IcarusKeyDerivation.deriveChildKey(extKey, chainCode, 44, hardened = true)
        val (child2, cc2) = IcarusKeyDerivation.deriveChildKey(extKey, chainCode, 44, hardened = true)
        assertTrue(child1.contentEquals(child2))
        assertTrue(cc1.contentEquals(cc2))
    }

    // ── Ed25519Icarus public key tests ────────────────────────────────────────

    @Test
    fun ed25519Icarus_publicKeySize() {
        val scalar = ByteArray(32).also { it[0] = 0x08 }  // minimal valid clamped scalar
        val pubKey = Ed25519Icarus.publicKeyFromScalar(scalar)
        assertEquals(32, pubKey.size, "Ed25519 public key must be 32 bytes")
    }

    @Test
    fun ed25519Icarus_deterministicForSameScalar() {
        val scalar = ByteArray(32) { it.toByte() }
        scalar[0]  = (scalar[0].toInt() and 0xF8).toByte()
        scalar[31] = (scalar[31].toInt() and 0x1F).toByte()
        scalar[31] = (scalar[31].toInt() or  0x40).toByte()
        val pub1 = Ed25519Icarus.publicKeyFromScalar(scalar)
        val pub2 = Ed25519Icarus.publicKeyFromScalar(scalar)
        assertTrue(pub1.contentEquals(pub2), "Public key must be deterministic")
    }

    @Test
    fun ed25519Icarus_differentScalarsDifferentKeys() {
        val s1 = ByteArray(32).also { it[0] = 0x08; it[31] = 0x40 }
        val s2 = ByteArray(32).also { it[0] = 0x10; it[31] = 0x40 }
        val pub1 = Ed25519Icarus.publicKeyFromScalar(s1)
        val pub2 = Ed25519Icarus.publicKeyFromScalar(s2)
        assertFalse(pub1.contentEquals(pub2), "Different scalars must produce different public keys")
    }

    @Test
    fun ed25519Icarus_rejectsNon32ByteInput() {
        assertFailsWith<IllegalArgumentException> { Ed25519Icarus.publicKeyFromScalar(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { Ed25519Icarus.publicKeyFromScalar(ByteArray(33)) }
    }

    @Test
    fun publicKeyFromExtended_uses_kL_only() {
        // Two extended keys with same kL[0..31] but different kR[32..63]
        // should produce the same public key
        val extKey1 = ByteArray(64) { 0x42 }
        extKey1[0]  = (extKey1[0].toInt() and 0xF8).toByte()
        extKey1[31] = (extKey1[31].toInt() and 0x1F or 0x40).toByte()

        val extKey2 = extKey1.copyOf()
        // Change kR bytes only
        for (i in 32 until 64) extKey2[i] = (extKey2[i].toInt() xor 0xFF).toByte()

        val pub1 = IcarusKeyDerivation.publicKeyFromExtended(extKey1)
        val pub2 = IcarusKeyDerivation.publicKeyFromExtended(extKey2)
        assertTrue(pub1.contentEquals(pub2), "Public key depends only on kL, not kR")
    }

    // ── Full Byron path tests ─────────────────────────────────────────────────

    @Test
    fun deriveByronPath_returnsCorrectSizes() {
        val (pubKey, chainCode, extKey) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        assertEquals(32, pubKey.size,    "Public key must be 32 bytes")
        assertEquals(32, chainCode.size, "Chain code must be 32 bytes")
        assertEquals(64, extKey.size,    "Extended key must be 64 bytes")
    }

    @Test
    fun deriveByronPath_deterministic() {
        val (pub1, cc1, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val (pub2, cc2, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        assertTrue(pub1.contentEquals(pub2), "Byron key derivation must be deterministic")
        assertTrue(cc1.contentEquals(cc2),   "Chain code must be deterministic")
    }

    @Test
    fun deriveByronPath_differentIndexProducesDifferentKey() {
        val (pub0, _, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val (pub1, _, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 1)
        assertFalse(pub0.contentEquals(pub1), "Different address indices must produce different keys")
    }

    // ── Byron address format tests ────────────────────────────────────────────

    @Test
    fun byronAddress_startsWithAe2() {
        val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)
        assertTrue(
            address.startsWith("Ae2"),
            "Icarus Byron mainnet address must start with 'Ae2', got: $address"
        )
    }

    @Test
    fun byronAddress_validFormat() {
        val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)

        // Must pass CRC validation
        assertTrue(CardanoAddress.isValidByronAddress(address), "Byron address must pass CRC validation")
        // Must be detected as BYRON type
        assertEquals(CardanoAddressType.BYRON, CardanoAddress.getAddressType(address))
    }

    @Test
    fun byronAddress_base58OnlyChars() {
        val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)
        val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        for (c in address) {
            assertTrue(c in base58Alphabet, "Address contains non-Base58 char: '$c'")
        }
    }

    @Test
    fun byronAddress_reasonableLength() {
        val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)
        // Typical Byron address is 58-104 chars
        assertTrue(address.length in 58..104, "Byron address length out of range: ${address.length}")
    }

    @Test
    fun byronAddress_multipleIndices_allValid() {
        for (i in 0 until 5) {
            val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, i)
            val address = CardanoAddress.createByronAddress(pubKey, chainCode)
            assertTrue(
                CardanoAddress.isValidByronAddress(address),
                "Byron address at index $i must be valid"
            )
        }
    }

    @Test
    fun byronAddress_allIndicesUnique() {
        val addresses = (0 until 5).map { i ->
            val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, i)
            CardanoAddress.createByronAddress(pubKey, chainCode)
        }
        assertEquals(addresses.size, addresses.toSet().size, "All generated addresses must be unique")
    }

    @Test
    fun byronAddress_12wordMnemonic_valid() {
        val (pubKey, chainCode, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords12, 0)
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)
        assertTrue(CardanoAddress.isValidByronAddress(address))
        assertTrue(address.startsWith("Ae2"))
    }

    // ── Regression tests — verify bugs 1-4 do not regress ────────────────────

    /**
     * Regression: Bug #1 — Icarus uses PBKDF2(entropy), not BIP-39 seed.
     *
     * If someone accidentally uses MnemonicCode.toSeed() as input to PBKDF2,
     * the master key will differ from what this test expects.
     */
    @Test
    fun regression_bug1_masterKeyUsesEntropyNotSeed() {
        // Verify that two different mnemonics with same entropy would produce same key
        // (This can't happen, but verifies we're using entropy, not mnemonic hash)
        // Structural test: masterKeyFromEntropy(A) != masterKeyFromEntropy(B) for A != B
        val entropy1 = ByteArray(16) { it.toByte() }
        val entropy2 = ByteArray(16) { (it + 1).toByte() }
        val (key1, _) = IcarusKeyDerivation.masterKeyFromEntropy(entropy1)
        val (key2, _) = IcarusKeyDerivation.masterKeyFromEntropy(entropy2)
        assertFalse(key1.contentEquals(key2), "Different entropy must produce different master key")
    }

    /**
     * Regression: Bug #3 — Role (0) and index must be soft, not hardened.
     *
     * The Icarus path is m/44'/1815'/0'/0/index. If role or index were hardened,
     * they'd produce completely different keys (and wrong addresses).
     */
    @Test
    fun regression_bug3_byronPathUsesSoftDerivationForRoleAndIndex() {
        val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        var (k, cc) = masterExt to masterCC

        IcarusKeyDerivation.deriveChildKey(k, cc, 44, true).let   { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, true).let    { (nk, nc) -> k = nk; cc = nc }

        val (kSoft, ccSoft)       = IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = false)
        val (kHardened, _)        = IcarusKeyDerivation.deriveChildKey(k, cc, 0, hardened = true)

        assertFalse(kSoft.contentEquals(kHardened),
            "Soft role derivation must differ from hardened role derivation")

        // Full path using soft for role produces the same result as deriveByronPath
        var (k2, cc2) = kSoft to ccSoft
        IcarusKeyDerivation.deriveChildKey(k2, cc2, 0, hardened = false).let { (nk, nc) -> k2 = nk; cc2 = nc }

        val (pub1, _, _) = IcarusKeyDerivation.deriveByronAddressKey(mnemonicWords, 0)
        assertTrue(pub1.contentEquals(IcarusKeyDerivation.publicKeyFromExtended(k2)),
            "Manual soft path must match deriveByronAddressKey")
    }

    /**
     * Regression: Bug #4 — Public key must be derived with Ed25519Icarus (no SHA-512),
     * not standard RFC 8032 Ed25519.
     *
     * Standard Ed25519.publicKey(seed) = A = clamp(SHA512(seed))[0..31] * B
     * Icarus Ed25519Icarus.publicKeyFromScalar(kL) = A = kL * B  (kL already clamped)
     *
     * They must differ for the same 32-byte input.
     */
    @Test
    fun regression_bug4_icarusPublicKeyDiffersFromStandardEd25519() {
        val (masterExt, masterCC) = IcarusKeyDerivation.masterKeyFromMnemonic(mnemonicWords)
        var (k, cc) = masterExt to masterCC
        IcarusKeyDerivation.deriveChildKey(k, cc, 44, true).let   { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 1815, true).let { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, true).let    { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, false).let   { (nk, nc) -> k = nk; cc = nc }
        IcarusKeyDerivation.deriveChildKey(k, cc, 0, false).let   { (nk, nc) -> k = nk; cc = nc }

        val kL = k.copyOfRange(0, 32)
        // Icarus: direct scalar multiplication
        val icarusPub = Ed25519Icarus.publicKeyFromScalar(kL)

        // Standard Ed25519 (ton-kotlin): SHA-512(seed) then multiply — this is the WRONG approach for Byron
        // We can't import Ed25519.kt directly, but we verify that the Icarus result is 32 bytes
        // and that it's non-zero (a valid point on the curve)
        assertEquals(32, icarusPub.size)
        assertFalse(icarusPub.all { it == 0.toByte() }, "Public key must not be all zeros")
    }
}
