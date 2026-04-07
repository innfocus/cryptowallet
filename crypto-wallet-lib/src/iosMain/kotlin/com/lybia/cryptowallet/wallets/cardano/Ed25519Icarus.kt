package com.lybia.cryptowallet.wallets.cardano

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import korlibs.crypto.SHA512

/**
 * iOS actual — uses com.ionspin.kotlin.bignum (no java.math.BigInteger on iOS).
 * Note: Cardano is Android-only; iOS integration is not active.
 */
internal actual object Ed25519Icarus {

    // Ed25519 field prime p = 2^255 − 19
    private val P = BigInteger.parseString(
        "7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16
    )

    // Ed25519 group order l = 2^252 + 27742317777372353535851937790883648493
    private val L = BigInteger.parseString(
        "1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16
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

    actual fun publicKeyFromScalar(scalar32: ByteArray): ByteArray {
        require(scalar32.size == 32) { "Scalar must be 32 bytes, got ${scalar32.size}" }
        val s = fromLittleEndian(scalar32)
        val (x, y) = scalarMultBase(s)
        return encodePoint(x, y)
    }

    actual fun sign(extKey64: ByteArray, message: ByteArray): ByteArray {
        require(extKey64.size == 64) { "extKey must be 64 bytes, got ${extKey64.size}" }
        val kL = extKey64.copyOfRange(0, 32)
        val kR = extKey64.copyOfRange(32, 64)
        val scalar = fromLittleEndian(kL)

        val rHash = sha512(kR + message)
        val r = fromLittleEndian(rHash).mod(L)

        val (rx, ry) = scalarMultBase(r)
        val rEnc = encodePoint(rx, ry)

        val aEnc = publicKeyFromScalar(kL)

        val kHash = sha512(rEnc + aEnc + message)
        val k = fromLittleEndian(kHash).mod(L)

        val s = (r + k * scalar).mod(L)
        val sEnc = toLittleEndian32(s)

        return rEnc + sEnc
    }

    private fun sha512(data: ByteArray): ByteArray = SHA512.digest(data).bytes

    private fun fromLittleEndian(le: ByteArray): BigInteger =
        BigInteger.fromByteArray(le.reversedArray(), Sign.POSITIVE)

    private fun toLittleEndian32(n: BigInteger): ByteArray {
        val dst = ByteArray(32)
        val be  = n.toByteArray()
        val len = minOf(be.size, 32)
        for (i in 0 until len) {
            dst[i] = be[be.size - 1 - i]
        }
        return dst
    }

    private fun modInverse(a: BigInteger): BigInteger = modPow(a, P - BigInteger.TWO, P)

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

    private fun scalarMultBase(s: BigInteger): Pair<BigInteger, BigInteger> {
        var rX = BigInteger.ZERO
        var rY = BigInteger.ONE
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
        val denY  = (BigInteger.ONE + P - dT).mod(P)

        val x3 = (numX * modInverse(denX)).mod(P)
        val y3 = (numY * modInverse(denY)).mod(P)
        return x3 to y3
    }

    private fun encodePoint(x: BigInteger, y: BigInteger): ByteArray {
        val yLE = toLittleEndian32(y)
        if ((x and BigInteger.ONE) != BigInteger.ZERO) {
            yLE[31] = (yLE[31].toInt() or 0x80).toByte()
        }
        return yLE
    }
}
