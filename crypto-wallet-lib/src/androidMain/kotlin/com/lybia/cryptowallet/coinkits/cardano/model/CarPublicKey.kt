package com.lybia.cryptowallet.coinkits.cardano.model

import java.math.BigInteger

class CarPublicKey {
    private var buffer: ByteArray = byteArrayOf()

    @Throws(CardanoException::class)
    constructor(bytes: ByteArray) {
        if (bytes.count() != 32) {
            throw CardanoException(CarError.InvalidPublicKeyLength.message)
        }
        buffer = bytes
    }

    fun bytes(): ByteArray {
        return buffer
    }

    companion object {

        // P = 2^255 - 19
        private val P =
            BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)

        // d = -121665 * (121666^-1) mod P
        private val d =
            BigInteger("52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3", 16)

        // Base Point Y = 4/5 mod P
        private val By =
            BigInteger("6666666666666666666666666666666666666666666666666666666666666658", 16)

        // Base Point X
        private val Bx =
            BigInteger("216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51a", 16)

        fun derive(fromSecret: ByteArray): CarPublicKey {

            // 'fromSecret' is the 64-byte expanded key. Use first 32 bytes as clamped scalar.
            val seed32 = fromSecret.copyOfRange(0, 32)

            require(seed32.size == 32) {
                "Cardano Ed25519 scalar must be 32 bytes"
            }

            // 1. Convert byte[] (Little Endian) to BigInteger
            val scalar = BigInteger(1, seed32.reversedArray())

            // 2. Point Multiplication: Result = scalar * BasePoint
            val resultPoint = scalarMult(Bx, By, scalar)

            // 3. Encode Point (Compress) -> 32 bytes
            // y-coord standard (Little Endian) | sign bit of x (highest bit)
            val yBytes = toLittleEndian(resultPoint.second)

            // Check parity of X properly (x mod 2)
            val xParity = resultPoint.first.testBit(0)
            if (xParity) { // if x is odd
                // Set MSB of the last byte
                yBytes[31] = (yBytes[31].toInt() or 0x80).toByte()
            } else {
                // Ensure MSB is clear (though it should be if y < P)
                yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
            }

            return CarPublicKey(yBytes)
        }

        private fun scalarMult(
            bx: BigInteger,
            by: BigInteger,
            s: BigInteger
        ): Pair<BigInteger, BigInteger> {
            var rX = BigInteger.ZERO
            var rY = BigInteger.ONE // Neutral point (0, 1)

            var currX = bx
            var currY = by

            // Double and Add
            for (i in 0 until 255) { // 255 bits is enough for Ed25519 (scalar is 254 bits effectively)
                if (s.testBit(i)) {
                    val sum = pointAdd(rX, rY, currX, currY)
                    rX = sum.first
                    rY = sum.second
                }
                val doubled = pointAdd(currX, currY, currX, currY)
                currX = doubled.first
                currY = doubled.second
            }

            return Pair(rX, rY)
        }

        private fun pointAdd(
            x1: BigInteger,
            y1: BigInteger,
            x2: BigInteger,
            y2: BigInteger
        ): Pair<BigInteger, BigInteger> {
            // Twisted Edwards Addition
            // x3 = (x1y2 + y1x2) / (1 + dx1x2y1y2)
            // y3 = (y1y2 + x1x2) / (1 - dx1x2y1y2)

            val x1y2 = x1.multiply(y2).mod(P)
            val y1x2 = y1.multiply(x2).mod(P)
            val y1y2 = y1.multiply(y2).mod(P)
            val x1x2 = x1.multiply(x2).mod(P)

            val dX1X2Y1Y2 = d.multiply(x1x2).mod(P).multiply(y1y2).mod(P)

            val numX = x1y2.add(y1x2).mod(P)
            val denX = BigInteger.ONE.add(dX1X2Y1Y2).mod(P)

            val numY = y1y2.add(x1x2).mod(P)
            val denY = BigInteger.ONE.subtract(dX1X2Y1Y2).mod(P)

            // Inverse
            val x3 = numX.multiply(denX.modInverse(P)).mod(P)
            val y3 = numY.multiply(denY.modInverse(P)).mod(P)

            return Pair(x3, y3)
        }

        private fun toLittleEndian(n: BigInteger): ByteArray {
            val arr = ByteArray(32)
            val bytes = n.toByteArray()
            // byte[] is BigEndian.

            var start = 0
            if (bytes.size > 32 && bytes[0] == 0.toByte()) {
                start = 1 // skip sign byte
            }

            val len = minOf(bytes.size - start, 32)
            // Copy reversed (BigEndian -> LittleEndian)
            // LSB of BigInt is at bytes[last]
            // We want LSB at arr[0]
            for (i in 0 until len) {
                arr[i] =
                    bytes[bytes.size - 1 - i - start + start /* cancel start logic complexity */]
                // Wait. bytes last element is LSB.
                // arr[0] should be LSB.
                arr[i] = bytes[bytes.size - 1 - i]
            }
            return arr
        }
    }
}