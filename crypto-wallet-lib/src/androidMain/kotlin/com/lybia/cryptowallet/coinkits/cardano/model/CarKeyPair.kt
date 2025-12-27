package com.lybia.cryptowallet.coinkits.cardano.model

import java.math.BigInteger
import java.security.MessageDigest

class CarKeyPair {
    var publicKey   : CarPublicKey
    var privateKey  : CarPrivateKey

    constructor(publicKey: CarPublicKey, privateKey: CarPrivateKey) {
        this.publicKey  = publicKey
        this.privateKey = privateKey
    }

    constructor(pubKey: ByteArray, priKey: ByteArray) {
        this.publicKey  = CarPublicKey(pubKey)
        this.privateKey = CarPrivateKey(priKey)
    }

    fun sign(message: ByteArray): ByteArray {
        val privBytes = privateKey.bytes()
        
        val scalar: BigInteger
        val prefix: ByteArray

        if (privBytes.size == 64) {
            // Expanded key: first 32 is scalar (s), last 32 is prefix
            // Note: standard Ed25519 assumes Little-Endian inputs for math
            scalar = BigInteger(1, privBytes.copyOfRange(0, 32).reversedArray())
            prefix = privBytes.copyOfRange(32, 64)
        } else if (privBytes.size == 32) {
            // Seed: expand it via SHA-512
            val md = MessageDigest.getInstance("SHA-512")
            val h = md.digest(privBytes)
            
            // Clamp key
            h[0] = (h[0].toInt() and 0xF8).toByte()
            h[31] = (h[31].toInt() and 0x7F).toByte()
            h[31] = (h[31].toInt() or 0x40).toByte()
            
            scalar = BigInteger(1, h.copyOfRange(0, 32).reversedArray())
            prefix = h.copyOfRange(32, 64)
        } else {
             throw CardanoException("Invalid private key length: ${privBytes.size}")
        }

        // --- Ed25519 Signing (RFC 8032) ---
        
        // 1. Calculate factor r = SHA512(prefix || M) (mod L)
        val md = MessageDigest.getInstance("SHA-512")
        md.update(prefix)
        md.update(message)
        val rBytes = md.digest() // 64 bytes
        // Interpret as integer little-endian
        val r = BigInteger(1, rBytes.reversedArray())
        
        // 2. Calculate Point R = r * B
        // We reduce r mod L for scalar multiplication efficiency (valid for cyclic group operation)
        val rReduced = r.mod(Ed25519Math.L)
        val pointR = Ed25519Math.scalarMultBase(rReduced)
        val encodedR = Ed25519Math.encodePoint(pointR)

        // 3. Calculate k = SHA512(R || A || M) (mod L)
        val mdK = MessageDigest.getInstance("SHA-512")
        mdK.update(encodedR)
        mdK.update(publicKey.bytes()) // A (Public Key)
        mdK.update(message)
        val kBytes = mdK.digest()
        val k = BigInteger(1, kBytes.reversedArray())

        // 4. Calculate S = (r + k * s) mod L
        // Note: use full 'r' here? RFC 8032 says "S = (r + k * s) mod L". 
        // Valid to use reduced r? Yes, because math is mod L.
        val S = (r.add(k.multiply(scalar))).mod(Ed25519Math.L)
        val encodedS = Ed25519Math.to32BytesLE(S)

        // 5. Signature = R || S
        return encodedR + encodedS
    }

    private object Ed25519Math {
        val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
        val L = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
        val d = BigInteger("52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3", 16)
        val By = BigInteger("6666666666666666666666666666666666666666666666666666666666666658", 16)
        val Bx = BigInteger("216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51a", 16)

        fun scalarMultBase(s: BigInteger): Pair<BigInteger, BigInteger> {
            return scalarMult(Bx, By, s)
        }

        fun scalarMult(bx: BigInteger, by: BigInteger, s: BigInteger): Pair<BigInteger, BigInteger> {
            var rX = BigInteger.ZERO
            var rY = BigInteger.ONE 
            var currX = bx
            var currY = by

            for (i in 0 until 255) {
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

        fun pointAdd(x1: BigInteger, y1: BigInteger, x2: BigInteger, y2: BigInteger): Pair<BigInteger, BigInteger> {
            val x1y2 = x1.multiply(y2).mod(P)
            val y1x2 = y1.multiply(x2).mod(P)
            val y1y2 = y1.multiply(y2).mod(P)
            val x1x2 = x1.multiply(x2).mod(P)
            val dX1X2Y1Y2 = d.multiply(x1x2).mod(P).multiply(y1y2).mod(P)
            val numX = x1y2.add(y1x2).mod(P)
            val denX = BigInteger.ONE.add(dX1X2Y1Y2).mod(P)
            val numY = y1y2.add(x1x2).mod(P)
            val denY = BigInteger.ONE.subtract(dX1X2Y1Y2).mod(P)
            return Pair(numX.multiply(denX.modInverse(P)).mod(P), numY.multiply(denY.modInverse(P)).mod(P))
        }

        fun encodePoint(point: Pair<BigInteger, BigInteger>): ByteArray {
            val yBytes = to32BytesLE(point.second)
            if (point.first.testBit(0)) {
                yBytes[31] = (yBytes[31].toInt() or 0x80).toByte()
            }
            return yBytes
        }

        fun to32BytesLE(n: BigInteger): ByteArray {
            val arr = ByteArray(32)
            val bytes = n.toByteArray()
            for (i in 0 until minOf(bytes.size, 32)) {
                arr[i] = bytes[bytes.size - 1 - i]
            }
            // If original had sign byte 0x00 and length 33, loop handles it correctly by skipping 0 index effectively?
            // Safer manual copy:
            val raw = n.toByteArray()
            val len = raw.size
            // n is positive, so raw might have leading 0x00
            var srcIdx = len - 1
            for(dstIdx in 0 until 32) {
                if (srcIdx >= 0) {
                     arr[dstIdx] = raw[srcIdx]
                     srcIdx--
                } else {
                     arr[dstIdx] = 0
                }
            }
            return arr
        }
    }
}