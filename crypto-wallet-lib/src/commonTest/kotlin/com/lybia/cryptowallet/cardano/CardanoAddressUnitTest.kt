package com.lybia.cryptowallet.cardano

import com.lybia.cryptowallet.utils.Bech32
import com.lybia.cryptowallet.utils.Blake2b
import com.lybia.cryptowallet.utils.CRC32
import com.lybia.cryptowallet.utils.SHA3
import com.lybia.cryptowallet.wallets.cardano.CardanoAddress
import com.lybia.cryptowallet.wallets.cardano.CardanoAddressType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Cardano address generation and validation
 * using known test vectors and edge cases.
 */
class CardanoAddressUnitTest {

    // ---- Helper ----
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = ((hex[i * 2].digitToInt(16) shl 4) or
                    hex[i * 2 + 1].digitToInt(16)).toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    // ---- CRC32 Tests ----
    @Test
    fun crc32KnownValues() {
        // CRC32 of empty byte array
        assertEquals(0L, CRC32.compute(byteArrayOf()))
        // CRC32 of "123456789" = 0xCBF43926
        val input = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926L, CRC32.compute(input))
    }

    // ---- Blake2b Tests ----
    @Test
    fun blake2b256KnownVector() {
        // Blake2b-256 of empty input (verified with Python hashlib.blake2b)
        val expected = "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8"
        val hash = Blake2b.hash(byteArrayOf(), 32)
        assertEquals(expected, hash.toHex())
    }

    @Test
    fun blake2b224KnownVector() {
        // Blake2b-224 of empty input
        val expected = "836cc68931c2e4e3e838602eca1902591d216837bafddfe6f0c8cb07"
        val hash = Blake2b.hash(byteArrayOf(), 28)
        assertEquals(expected, hash.toHex())
    }

    // ---- SHA3-256 Tests ----
    @Test
    fun sha3_256KnownVector() {
        // SHA3-256 of empty input
        val expected = "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a"
        val hash = SHA3.sha3_256(byteArrayOf())
        assertEquals(expected, hash.toHex())
    }

    @Test
    fun sha3_256AbcVector() {
        // SHA3-256 of "abc"
        val expected = "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532"
        val hash = SHA3.sha3_256("abc".encodeToByteArray())
        assertEquals(expected, hash.toHex())
    }

    // ---- Bech32 Tests ----
    @Test
    fun bech32RoundTrip() {
        val hrp = "addr"
        val data = ByteArray(10) { it.toByte() }
        val data5bit = Bech32.convertBits(data, 8, 5, true)
        val encoded = Bech32.encode(hrp, data5bit)
        val (decodedHrp, decodedData5bit) = Bech32.decode(encoded)
        assertEquals(hrp, decodedHrp)
        val decodedData = Bech32.convertBits(decodedData5bit, 5, 8, false)
        assertTrue(data.contentEquals(decodedData))
    }

    // ---- Byron Address Tests ----

    @Test
    fun byronAddressGenerationDeterministic() {
        // Same inputs should produce same address
        val pubKey = ByteArray(32) { 0x01 }
        val chainCode = ByteArray(32) { 0x02 }
        val addr1 = CardanoAddress.createByronAddress(pubKey, chainCode)
        val addr2 = CardanoAddress.createByronAddress(pubKey, chainCode)
        assertEquals(addr1, addr2, "Same inputs should produce same Byron address")
    }

    @Test
    fun byronAddressIsValidBase58() {
        val pubKey = ByteArray(32) { (it % 256).toByte() }
        val chainCode = ByteArray(32) { ((it + 32) % 256).toByte() }
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)

        // Should be valid Base58 (no 0, O, I, l characters)
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        for (c in address) {
            assertTrue(c in base58Chars, "Byron address should only contain Base58 characters, found '$c'")
        }
    }

    @Test
    fun byronAddressValidation() {
        val pubKey = ByteArray(32) { (it * 3 % 256).toByte() }
        val chainCode = ByteArray(32) { (it * 7 % 256).toByte() }
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)

        assertTrue(CardanoAddress.isValidByronAddress(address))
        assertEquals(CardanoAddressType.BYRON, CardanoAddress.getAddressType(address))
    }

    @Test
    fun byronAddressInvalidInputs() {
        assertFalse(CardanoAddress.isValidByronAddress(""))
        assertFalse(CardanoAddress.isValidByronAddress("notanaddress"))
        assertFalse(CardanoAddress.isValidByronAddress("Ae2tdPwUPEZ"))

        // Verify error messages
        val error1 = CardanoAddress.validateByronAddress("")
        assertNotNull(error1)
        assertTrue(error1.contains("Base58") || error1.contains("CBOR"))

        val error2 = CardanoAddress.validateByronAddress("notanaddress")
        assertNotNull(error2)
    }

    @Test
    fun byronAddressCrcCorruption() {
        // Generate a valid address, then corrupt it
        val pubKey = ByteArray(32) { 0xAA.toByte() }
        val chainCode = ByteArray(32) { 0xBB.toByte() }
        val address = CardanoAddress.createByronAddress(pubKey, chainCode)

        // Flip a character to corrupt the CRC
        val chars = address.toCharArray()
        if (chars.size > 10) {
            chars[5] = if (chars[5] == '1') '2' else '1'
            val corrupted = String(chars)
            assertFalse(CardanoAddress.isValidByronAddress(corrupted),
                "Corrupted Byron address should be invalid")
        }
    }

    // ---- Shelley Address Tests ----

    @Test
    fun shelleyBaseAddressMainnet() {
        val paymentHash = ByteArray(28) { 0x01 }
        val stakingHash = ByteArray(28) { 0x02 }
        val address = CardanoAddress.createBaseAddress(paymentHash, stakingHash, isTestnet = false)

        assertTrue(address.startsWith("addr1"), "Mainnet base address should start with 'addr1'")
        assertTrue(CardanoAddress.isValidShelleyAddress(address))
        assertEquals(CardanoAddressType.SHELLEY_BASE, CardanoAddress.getAddressType(address))
    }

    @Test
    fun shelleyBaseAddressTestnet() {
        val paymentHash = ByteArray(28) { 0x01 }
        val stakingHash = ByteArray(28) { 0x02 }
        val address = CardanoAddress.createBaseAddress(paymentHash, stakingHash, isTestnet = true)

        assertTrue(address.startsWith("addr_test1"), "Testnet base address should start with 'addr_test1'")
        assertTrue(CardanoAddress.isValidShelleyAddress(address))
        assertEquals(CardanoAddressType.SHELLEY_BASE, CardanoAddress.getAddressType(address))
    }

    @Test
    fun shelleyEnterpriseAddress() {
        val paymentHash = ByteArray(28) { 0x03 }
        val address = CardanoAddress.createEnterpriseAddress(paymentHash, isTestnet = false)

        assertTrue(address.startsWith("addr1"))
        assertTrue(CardanoAddress.isValidShelleyAddress(address))
        assertEquals(CardanoAddressType.SHELLEY_ENTERPRISE, CardanoAddress.getAddressType(address))
    }

    @Test
    fun shelleyRewardAddress() {
        val stakingHash = ByteArray(28) { 0x04 }
        val address = CardanoAddress.createRewardAddress(stakingHash, isTestnet = false)

        assertTrue(address.startsWith("stake1"))
        assertTrue(CardanoAddress.isValidShelleyAddress(address))
        assertEquals(CardanoAddressType.SHELLEY_REWARD, CardanoAddress.getAddressType(address))
    }

    @Test
    fun shelleyAddressDeterministic() {
        val paymentHash = ByteArray(28) { 0x05 }
        val stakingHash = ByteArray(28) { 0x06 }
        val addr1 = CardanoAddress.createBaseAddress(paymentHash, stakingHash)
        val addr2 = CardanoAddress.createBaseAddress(paymentHash, stakingHash)
        assertEquals(addr1, addr2, "Same inputs should produce same Shelley address")
    }

    @Test
    fun shelleyAddressPayloadLength() {
        val paymentHash = ByteArray(28) { 0x07 }
        val stakingHash = ByteArray(28) { 0x08 }

        // Base address: 1 header + 28 payment + 28 staking = 57 bytes
        val baseAddr = CardanoAddress.createBaseAddress(paymentHash, stakingHash)
        val baseData = Bech32.decode(baseAddr).second
        val basePayload = Bech32.convertBits(baseData, 5, 8, false)
        assertEquals(57, basePayload.size, "Base address payload should be 57 bytes")

        // Enterprise address: 1 header + 28 payment = 29 bytes
        val entAddr = CardanoAddress.createEnterpriseAddress(paymentHash)
        val entData = Bech32.decode(entAddr).second
        val entPayload = Bech32.convertBits(entData, 5, 8, false)
        assertEquals(29, entPayload.size, "Enterprise address payload should be 29 bytes")

        // Reward address: 1 header + 28 staking = 29 bytes
        val rewAddr = CardanoAddress.createRewardAddress(stakingHash)
        val rewData = Bech32.decode(rewAddr).second
        val rewPayload = Bech32.convertBits(rewData, 5, 8, false)
        assertEquals(29, rewPayload.size, "Reward address payload should be 29 bytes")
    }

    @Test
    fun shelleyAddressInvalidInputs() {
        assertFalse(CardanoAddress.isValidShelleyAddress(""))
        assertFalse(CardanoAddress.isValidShelleyAddress("notanaddress"))
        assertFalse(CardanoAddress.isValidShelleyAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))

        val error = CardanoAddress.validateShelleyAddress("notanaddress")
        assertNotNull(error)
    }

    // ---- Address Type Detection Tests ----

    @Test
    fun addressTypeDetection() {
        val pubKey = ByteArray(32) { 0x10 }
        val chainCode = ByteArray(32) { 0x20 }
        val paymentHash = ByteArray(28) { 0x30 }
        val stakingHash = ByteArray(28) { 0x40 }

        val byronAddr = CardanoAddress.createByronAddress(pubKey, chainCode)
        assertEquals(CardanoAddressType.BYRON, CardanoAddress.getAddressType(byronAddr))

        val baseAddr = CardanoAddress.createBaseAddress(paymentHash, stakingHash)
        assertEquals(CardanoAddressType.SHELLEY_BASE, CardanoAddress.getAddressType(baseAddr))

        val entAddr = CardanoAddress.createEnterpriseAddress(paymentHash)
        assertEquals(CardanoAddressType.SHELLEY_ENTERPRISE, CardanoAddress.getAddressType(entAddr))

        val rewAddr = CardanoAddress.createRewardAddress(stakingHash)
        assertEquals(CardanoAddressType.SHELLEY_REWARD, CardanoAddress.getAddressType(rewAddr))

        assertEquals(CardanoAddressType.UNKNOWN, CardanoAddress.getAddressType("garbage"))
        assertEquals(CardanoAddressType.UNKNOWN, CardanoAddress.getAddressType(""))
    }

    // ---- hashKey Tests ----

    @Test
    fun hashKeyProduces28Bytes() {
        val pubKey = ByteArray(32) { 0xFF.toByte() }
        val hash = CardanoAddress.hashKey(pubKey)
        assertEquals(28, hash.size, "hashKey should produce 28-byte Blake2b-224 hash")
    }

    @Test
    fun hashKeyDeterministic() {
        val pubKey = ByteArray(32) { 0xAB.toByte() }
        val hash1 = CardanoAddress.hashKey(pubKey)
        val hash2 = CardanoAddress.hashKey(pubKey)
        assertTrue(hash1.contentEquals(hash2), "hashKey should be deterministic")
    }

    // ---- Cross-validation: Byron is not Shelley and vice versa ----

    @Test
    fun byronAddressIsNotShelley() {
        val pubKey = ByteArray(32) { 0x50 }
        val chainCode = ByteArray(32) { 0x60 }
        val byronAddr = CardanoAddress.createByronAddress(pubKey, chainCode)
        assertFalse(CardanoAddress.isValidShelleyAddress(byronAddr),
            "Byron address should not be valid as Shelley")
    }

    @Test
    fun shelleyAddressIsNotByron() {
        val paymentHash = ByteArray(28) { 0x70 }
        val stakingHash = ByteArray(28) { 0x80.toByte() }
        val shelleyAddr = CardanoAddress.createBaseAddress(paymentHash, stakingHash)
        assertFalse(CardanoAddress.isValidByronAddress(shelleyAddr),
            "Shelley address should not be valid as Byron")
    }
}
