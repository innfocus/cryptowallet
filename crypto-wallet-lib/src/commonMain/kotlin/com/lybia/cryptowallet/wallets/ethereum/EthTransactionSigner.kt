package com.lybia.cryptowallet.wallets.ethereum

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.lybia.cryptowallet.utils.Keccak
import com.lybia.cryptowallet.utils.fromHexToByteArray
import com.lybia.cryptowallet.utils.toHexString
import fr.acinq.secp256k1.Secp256k1

/**
 * Pure-Kotlin Ethereum transaction signer.
 * Supports both legacy (type 0, EIP-155) and EIP-1559 (type 2) transactions.
 * Uses BigInteger for all wei amounts to avoid Long overflow (Long.MAX ≈ 9.2 ETH).
 */
object EthTransactionSigner {

    // ── Legacy (type 0) EIP-155 transaction ─────────────────────────

    /**
     * Build and sign a legacy (type 0) EIP-155 transaction.
     *
     * @param privateKey 32-byte secp256k1 private key
     * @param nonce Transaction nonce
     * @param gasPriceWei Gas price in wei (BigInteger)
     * @param gasLimit Gas limit
     * @param toAddress Destination address (0x-prefixed hex)
     * @param valueWei Value in wei (BigInteger — safe for any ETH amount)
     * @param data Call data (empty for simple transfers)
     * @param chainId EIP-155 chain ID
     * @return "0x"-prefixed hex string of the signed transaction
     */
    fun signLegacyTransaction(
        privateKey: ByteArray,
        nonce: Long,
        gasPriceWei: BigInteger,
        gasLimit: Long,
        toAddress: String,
        valueWei: BigInteger,
        data: ByteArray,
        chainId: Long
    ): String {
        val to = toAddress.removePrefix("0x").fromHexToByteArray()

        val unsignedFields = listOf(
            rlpEncodeLong(nonce),
            rlpEncodeBigInt(gasPriceWei),
            rlpEncodeLong(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeBigInt(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeLong(chainId),
            rlpEncodeLong(0),
            rlpEncodeLong(0)
        )
        val unsignedTx = rlpEncodeList(unsignedFields)
        val txHash = Keccak.keccak256(unsignedTx)

        val sigData = Secp256k1.sign(txHash, privateKey)
        val r = sigData.copyOfRange(0, 32)
        val s = sigData.copyOfRange(32, 64)
        val recId = findRecoveryId(txHash, sigData, privateKey)
        val v = chainId * 2 + 35 + recId

        val signedFields = listOf(
            rlpEncodeLong(nonce),
            rlpEncodeBigInt(gasPriceWei),
            rlpEncodeLong(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeBigInt(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeLong(v),
            rlpEncodeBytes(stripLeadingZeros(r)),
            rlpEncodeBytes(stripLeadingZeros(s))
        )
        return "0x" + rlpEncodeList(signedFields).toHexString()
    }

    /**
     * Backward-compatible legacy sign with Long amounts.
     * Delegates to [signLegacyTransaction] with BigInteger conversion.
     */
    fun signTransaction(
        privateKey: ByteArray,
        nonce: Long,
        gasPriceWei: Long,
        gasLimit: Long,
        toAddress: String,
        valueWei: Long,
        data: ByteArray,
        chainId: Long
    ): String = signLegacyTransaction(
        privateKey, nonce,
        BigInteger.fromLong(gasPriceWei), gasLimit,
        toAddress, BigInteger.fromLong(valueWei),
        data, chainId
    )

    // ── EIP-1559 (type 2) transaction ───────────────────────────────

    /**
     * Build and sign an EIP-1559 (type 2) transaction.
     *
     * Type 2 envelope: 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas,
     *   maxFeePerGas, gasLimit, to, value, data, accessList])
     *
     * Signed: 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas,
     *   maxFeePerGas, gasLimit, to, value, data, accessList, v, r, s])
     *
     * @param privateKey 32-byte secp256k1 private key
     * @param nonce Transaction nonce
     * @param maxPriorityFeePerGas Tip to miner in wei (BigInteger)
     * @param maxFeePerGas Maximum total fee per gas in wei (BigInteger)
     * @param gasLimit Gas limit
     * @param toAddress Destination address (0x-prefixed hex)
     * @param valueWei Value in wei (BigInteger)
     * @param data Call data
     * @param chainId Chain ID (1 = mainnet, 11155111 = sepolia, etc.)
     * @return "0x"-prefixed hex string of the signed type 2 transaction
     */
    fun signEip1559Transaction(
        privateKey: ByteArray,
        nonce: Long,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: Long,
        toAddress: String,
        valueWei: BigInteger,
        data: ByteArray,
        chainId: Long
    ): String {
        val to = toAddress.removePrefix("0x").fromHexToByteArray()

        // Unsigned payload: [chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
        //                    gasLimit, to, value, data, accessList(empty)]
        val unsignedFields = listOf(
            rlpEncodeLong(chainId),
            rlpEncodeLong(nonce),
            rlpEncodeBigInt(maxPriorityFeePerGas),
            rlpEncodeBigInt(maxFeePerGas),
            rlpEncodeLong(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeBigInt(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeList(emptyList()) // empty accessList
        )
        val unsignedPayload = rlpEncodeList(unsignedFields)

        // Hash: keccak256(0x02 || unsignedPayload)
        val txHash = Keccak.keccak256(byteArrayOf(0x02) + unsignedPayload)

        // Sign
        val sigData = Secp256k1.sign(txHash, privateKey)
        val r = sigData.copyOfRange(0, 32)
        val s = sigData.copyOfRange(32, 64)
        val recId = findRecoveryId(txHash, sigData, privateKey)

        // Signed payload: [...unsignedFields, v(yParity), r, s]
        val signedFields = listOf(
            rlpEncodeLong(chainId),
            rlpEncodeLong(nonce),
            rlpEncodeBigInt(maxPriorityFeePerGas),
            rlpEncodeBigInt(maxFeePerGas),
            rlpEncodeLong(gasLimit),
            rlpEncodeBytes(to),
            rlpEncodeBigInt(valueWei),
            rlpEncodeBytes(data),
            rlpEncodeList(emptyList()), // empty accessList
            rlpEncodeLong(recId.toLong()),  // yParity (0 or 1)
            rlpEncodeBytes(stripLeadingZeros(r)),
            rlpEncodeBytes(stripLeadingZeros(s))
        )
        val signedPayload = rlpEncodeList(signedFields)

        // Type 2 envelope: 0x02 || signedPayload
        return "0x" + (byteArrayOf(0x02) + signedPayload).toHexString()
    }

    // ── ERC-20 encoding ─────────────────────────────────────────────

    /**
     * Encode ERC-20 transfer(address, uint256) call data with BigInteger amount.
     */
    fun encodeErc20TransferBigInt(toAddress: String, amount: BigInteger): ByteArray {
        val selector = "a9059cbb".fromHexToByteArray()
        val addrBytes = toAddress.removePrefix("0x").fromHexToByteArray()
        val paddedAddr = ByteArray(32)
        addrBytes.copyInto(paddedAddr, 32 - addrBytes.size)
        val amountBytes = bigIntToBytes32(amount)
        return selector + paddedAddr + amountBytes
    }

    /**
     * Encode ERC-20 transfer(address, uint256) call data with Long amount.
     * Kept for backward compatibility.
     */
    fun encodeErc20Transfer(toAddress: String, amount: Long): ByteArray {
        return encodeErc20TransferBigInt(toAddress, BigInteger.fromLong(amount))
    }

    /**
     * Encode ERC-20 approve(address, uint256) call data.
     * Function selector: 0x095ea7b3 = keccak256("approve(address,uint256)")
     *
     * @param spenderAddress The address authorized to spend tokens
     * @param amount The approved amount in smallest unit (BigInteger)
     * @return 68-byte ABI-encoded call data
     */
    fun encodeErc20Approve(spenderAddress: String, amount: BigInteger): ByteArray {
        val selector = "095ea7b3".fromHexToByteArray()
        val addrBytes = spenderAddress.removePrefix("0x").fromHexToByteArray()
        val paddedAddr = ByteArray(32)
        addrBytes.copyInto(paddedAddr, 32 - addrBytes.size)
        val amountBytes = bigIntToBytes32(amount)
        return selector + paddedAddr + amountBytes
    }

    /**
     * Encode ERC-20 allowance(address, address) call data for eth_call.
     * Function selector: 0xdd62ed3e = keccak256("allowance(address,address)")
     *
     * @param ownerAddress The token owner address
     * @param spenderAddress The spender address
     * @return 68-byte ABI-encoded call data
     */
    fun encodeErc20Allowance(ownerAddress: String, spenderAddress: String): ByteArray {
        val selector = "dd62ed3e".fromHexToByteArray()
        val ownerBytes = ownerAddress.removePrefix("0x").fromHexToByteArray()
        val paddedOwner = ByteArray(32)
        ownerBytes.copyInto(paddedOwner, 32 - ownerBytes.size)
        val spenderBytes = spenderAddress.removePrefix("0x").fromHexToByteArray()
        val paddedSpender = ByteArray(32)
        spenderBytes.copyInto(paddedSpender, 32 - spenderBytes.size)
        return selector + paddedOwner + paddedSpender
    }

    // ── RLP encoding ────────────────────────────────────────────────

    internal fun rlpEncodeBytes(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return byteArrayOf(0x80.toByte())
        return when {
            bytes.size == 1 && bytes[0].toInt() and 0xFF < 0x80 -> bytes
            bytes.size <= 55 -> byteArrayOf((0x80 + bytes.size).toByte()) + bytes
            else -> {
                val lenBytes = toLengthBytes(bytes.size)
                byteArrayOf((0xB7 + lenBytes.size).toByte()) + lenBytes + bytes
            }
        }
    }

    internal fun rlpEncodeLong(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0x80.toByte())
        val bytes = stripLeadingZeros(longToBytesBigEndian(value))
        return rlpEncodeBytes(bytes)
    }

    internal fun rlpEncodeBigInt(value: BigInteger): ByteArray {
        if (value == BigInteger.ZERO) return byteArrayOf(0x80.toByte())
        val bytes = stripLeadingZeros(value.toByteArray())
        return rlpEncodeBytes(bytes)
    }

    internal fun rlpEncodeList(items: List<ByteArray>): ByteArray {
        val payload = items.fold(byteArrayOf()) { acc, item -> acc + item }
        return if (payload.size <= 55) {
            byteArrayOf((0xC0 + payload.size).toByte()) + payload
        } else {
            val lenBytes = toLengthBytes(payload.size)
            byteArrayOf((0xF7 + lenBytes.size).toByte()) + lenBytes + payload
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun longToBytesBigEndian(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val result = mutableListOf<Byte>()
        var v = value
        while (v > 0) {
            result.add(0, (v and 0xFF).toByte())
            v = v ushr 8
        }
        return result.toByteArray()
    }

    private fun bigIntToBytes32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size < 32 -> ByteArray(32 - raw.size) + raw
            else -> raw.copyOfRange(raw.size - 32, raw.size)
        }
    }

    internal fun stripLeadingZeros(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        var i = 0
        while (i < bytes.size - 1 && bytes[i] == 0.toByte()) i++
        return bytes.copyOfRange(i, bytes.size)
    }

    private fun toLengthBytes(length: Int): ByteArray {
        return stripLeadingZeros(longToBytesBigEndian(length.toLong()))
    }

    private fun findRecoveryId(hash: ByteArray, sig: ByteArray, privateKey: ByteArray): Int {
        val pubKey = Secp256k1.pubkeyCreate(privateKey)
        for (recId in 0..1) {
            try {
                val recovered = Secp256k1.ecdsaRecover(sig, hash, recId)
                if (recovered.contentEquals(pubKey)) return recId
            } catch (_: Exception) { }
        }
        return 0
    }
}
