package com.lybia.cryptowallet.wallets.cardano

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * Ed25519 public key derivation for Icarus (ed25519-bip32) extended keys.
 *
 * Unlike standard RFC 8032 Ed25519, Icarus extended keys carry a PRE-CLAMPED
 * 32-byte scalar (kL) — the SHA-512 expansion step is intentionally skipped.
 *
 *   Standard RFC 8032 : A = clamp(SHA-512(seed)[0..31]) * B
 *   Icarus ed25519-bip32: A = kL * B          ← kL already clamped at master-key step
 *
 * ⚠️  Do NOT use this for standard Ed25519 seeds (TON / Shelley).
 *     For those, use Ed25519.kt (ton-kotlin wrapper).
 */
internal object Ed25519Icarus {

    // Ed25519 field prime p = 2^255 − 19
    private val P = BigInteger.parseString(
        "7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16
    )

    // Twisted Edwards constant d = −121665 * 121666^{−1} mod p
    private val D = BigInteger.parseString(
        "52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3", 16
    )

    // Base point B = (Bx, By)
    private val Bx = BigInteger.parseString(
        "216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51a", 16
    )
    private val By = BigInteger.parseString(
        "6666666666666666666666666666666666666666666666666666666666666658", 16
    )

    /**
     * Compute Ed25519 public key A = scalar * B from a pre-clamped 32-byte scalar.
     *
     * @param scalar32 32-byte little-endian pre-clamped scalar (kL from Icarus extended key)
     * @return 32-byte compressed Ed25519 public key
     */
    fun publicKeyFromScalar(scalar32: ByteArray): ByteArray {
        require(scalar32.size == 32) { "Scalar must be 32 bytes, got ${scalar32.size}" }
        val s = fromLittleEndian(scalar32)
        val (x, y) = scalarMultBase(s)
        return encodePoint(x, y)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fromLittleEndian(le: ByteArray): BigInteger {
        return BigInteger.fromByteArray(le.reversedArray(), Sign.POSITIVE)
    }

    /** Big-endian BigInteger → 32-byte little-endian byte array */
    private fun toLittleEndian32(n: BigInteger): ByteArray {
        val dst = ByteArray(32)
        val be  = n.toByteArray()           // big-endian, no leading sign byte for positive
        val len = minOf(be.size, 32)
        for (i in 0 until len) {
            dst[i] = be[be.size - 1 - i]   // reverse: BE[last] → LE[0]
        }
        return dst
    }

    /** Modular inverse via Fermat's little theorem: a^{p-2} mod p (p is prime) */
    private fun modInverse(a: BigInteger): BigInteger = modPow(a, P - BigInteger.TWO, P)

    /** Binary square-and-multiply modular exponentiation */
    private fun modPow(base: BigInteger, exp: BigInteger, mod: BigInteger): BigInteger {
        var result = BigInteger.ONE
        var b = base.mod(mod)
        var e = exp
        while (e > BigInteger.ZERO) {
            if ((e and BigInteger.ONE) != BigInteger.ZERO) {
                result = (result * b).mod(mod)
            }
            e = e shr 1
            b = (b * b).mod(mod)
        }
        return result
    }

    /** Scalar multiplication: s * BasePoint using double-and-add (255 iterations) */
    private fun scalarMultBase(s: BigInteger): Pair<BigInteger, BigInteger> {
        var rX = BigInteger.ZERO
        var rY = BigInteger.ONE   // neutral element (0, 1)
        var cX = Bx
        var cY = By
        for (i in 0 until 255) {
            if (((s shr i) and BigInteger.ONE) != BigInteger.ZERO) {
                val (nx, ny) = pointAdd(rX, rY, cX, cY)
                rX = nx; rY = ny
            }
            val (dx, dy) = pointAdd(cX, cY, cX, cY)
            cX = dx; cY = dy
        }
        return rX to rY
    }

    /** Twisted Edwards point addition */
    private fun pointAdd(
        x1: BigInteger, y1: BigInteger,
        x2: BigInteger, y2: BigInteger
    ): Pair<BigInteger, BigInteger> {
        val x1y2  = (x1 * y2).mod(P)
        val y1x2  = (y1 * x2).mod(P)
        val y1y2  = (y1 * y2).mod(P)
        val x1x2  = (x1 * x2).mod(P)
        val dT    = (D * x1x2 % P * y1y2).mod(P)

        val numX  = (x1y2 + y1x2).mod(P)
        val denX  = (BigInteger.ONE + dT).mod(P)
        val numY  = (y1y2 + x1x2).mod(P)
        // (1 − dT) mod p = (1 + p − dT) mod p  — avoids negative intermediate
        val denY  = (BigInteger.ONE + P - dT).mod(P)

        val x3 = (numX * modInverse(denX)).mod(P)
        val y3 = (numY * modInverse(denY)).mod(P)
        return x3 to y3
    }

    /**
     * Compress point: 32-byte little-endian y-coordinate,
     * with MSB of last byte set to sign bit of x (x mod 2).
     */
    private fun encodePoint(x: BigInteger, y: BigInteger): ByteArray {
        val yLE = toLittleEndian32(y)
        if ((x and BigInteger.ONE) != BigInteger.ZERO) {    // x is odd
            yLE[31] = (yLE[31].toInt() or 0x80).toByte()
        }
        return yLE
    }
}
