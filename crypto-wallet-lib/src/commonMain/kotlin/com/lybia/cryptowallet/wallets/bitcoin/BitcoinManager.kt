package com.lybia.cryptowallet.wallets.bitcoin

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.bitcoin.BitcoinTransactionModel
import com.lybia.cryptowallet.services.BitcoinApiService
import fr.acinq.bitcoin.Base58
import fr.acinq.bitcoin.Bitcoin
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1

class BitcoinManager(mnemonics: String) : BaseCoinManager() {
    private val seed = MnemonicCode.toSeed(mnemonics, "")
    private val master = DeterministicWallet.generate(seed)
    private var walletAddress: String? = null
    private var keyPath: KeyPath? = null

    /**
     * Unified method to generate a Bitcoin address by type and account index.
     *
     * Dispatches to the correct bitcoin-kmp function based on [addressType]:
     * - [BitcoinAddressType.NATIVE_SEGWIT] → `Bitcoin.computeBIP84Address()` (BIP-84, P2WPKH)
     * - [BitcoinAddressType.NESTED_SEGWIT] → `Bitcoin.computeP2ShOfP2WpkhAddress()` (BIP-49, P2SH-P2WPKH)
     * - [BitcoinAddressType.LEGACY] → `Bitcoin.computeP2PkhAddress()` (BIP-44, P2PKH)
     *
     * @param addressType the Bitcoin address type to generate
     * @param accountIndex the HD wallet account index (must be >= 0)
     * @return the generated Bitcoin address string
     * @throws IllegalArgumentException if [accountIndex] is negative
     */
    fun getAddressByType(
        addressType: BitcoinAddressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): String {
        require(accountIndex >= 0) { "Account index must be non-negative, received: $accountIndex" }

        val isMainnet = Config.shared.getNetwork() == Network.MAINNET
        val coinType = if (isMainnet) 0 else 1
        val chain = if (isMainnet) Chain.Mainnet else Chain.Testnet4

        val purpose = when (addressType) {
            BitcoinAddressType.NATIVE_SEGWIT -> 84
            BitcoinAddressType.NESTED_SEGWIT -> 49
            BitcoinAddressType.LEGACY -> 44
        }

        val path = KeyPath("m/${purpose}'/${coinType}'/${accountIndex}'/0/0")
        val derived = DeterministicWallet.derivePrivateKey(master, path)

        val address = when (addressType) {
            BitcoinAddressType.NATIVE_SEGWIT ->
                Bitcoin.computeBIP84Address(derived.publicKey, chain.chainHash)
            BitcoinAddressType.NESTED_SEGWIT ->
                Bitcoin.computeP2ShOfP2WpkhAddress(derived.publicKey, chain.chainHash)
            BitcoinAddressType.LEGACY ->
                Bitcoin.computeP2PkhAddress(derived.publicKey, chain.chainHash)
        }

        walletAddress = address
        keyPath = path
        return address
    }

    /**
     * Generate a Legacy (P2PKH) Bitcoin address.
     *
     * @param accountIndex the HD wallet account index (default 0)
     * @return the generated Legacy address
     */
    fun getLegacyAddress(accountIndex: Int = 0): String {
        return getAddressByType(BitcoinAddressType.LEGACY, accountIndex)
    }

    /**
     * Generate a Nested SegWit (P2SH-P2WPKH) Bitcoin address.
     *
     * @param accountIndex the HD wallet account index (default 0)
     * @return the generated Nested SegWit address
     */
    fun getNestedSegWitAddress(accountIndex: Int = 0): String {
        return getAddressByType(BitcoinAddressType.NESTED_SEGWIT, accountIndex)
    }

    /**
     * Generate a Nested SegWit address.
     *
     * @deprecated Use [getNestedSegWitAddress] instead for correct P2SH-P2WPKH address generation.
     */
    @Deprecated(
        message = "Use getNestedSegWitAddress() instead",
        replaceWith = ReplaceWith("getNestedSegWitAddress()")
    )
    fun getSegWitAddress(): String {
        return getNestedSegWitAddress()
    }

    /**
     * Generate a Native SegWit (P2WPKH / Bech32) Bitcoin address.
     *
     * @param numberAccount the HD wallet account index (default 0)
     * @return the generated Native SegWit address
     */
    fun getNativeSegWitAddress(numberAccount: Int = 0): String {
        return getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, numberAccount)
    }

    override fun getAddress(): String {
        if (walletAddress == null) {
            getAddressByType(BitcoinAddressType.NATIVE_SEGWIT, 0)
        }
        return walletAddress ?: ""
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        require(walletAddress != null) { "Wallet address is null" }
        val balance = BitcoinApiService.INSTANCE.getBalance(walletAddress!!)
        if (balance != null) {
            return balance.toDouble() / 100000000
        }
        return 0.0
    }

    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        require(walletAddress != null) { "Wallet address is null" }
        return BitcoinApiService.INSTANCE.getTransactionHistory(walletAddress!!)
    }

    suspend fun sendBitcoinTransaction(toAddress: String, amount: Double) {
        val amountSatoshi = (amount * 100_000_000).toLong()
        sendBtc(toAddress, amountSatoshi)
    }

    /**
     * Build, sign, and submit a Bitcoin transaction via BlockCypher API.
     *
     * Flow:
     * 1. BlockCypher creates unsigned tx with UTXO selection (`/txs/new`)
     * 2. Client signs each `tosign` hash with the derived private key
     * 3. BlockCypher broadcasts the signed tx (`/txs/send`)
     *
     * Works with all address types (Legacy, Nested SegWit, Native SegWit)
     * because BlockCypher handles script type detection automatically.
     *
     * @param toAddress Destination Bitcoin address (any format: 1..., 3..., bc1q...)
     * @param amountSatoshi Amount in satoshis (1 BTC = 100,000,000 satoshis)
     * @param addressType Address type of the sender (determines which key to sign with)
     * @param accountIndex HD wallet account index (default 0)
     * @return TransferResponseModel with txHash on success
     */
    suspend fun sendBtc(
        toAddress: String,
        amountSatoshi: Long,
        addressType: BitcoinAddressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): TransferResponseModel {
        val fromAddress = getAddressByType(addressType, accountIndex)

        val transactionRequest = BitcoinApiService.INSTANCE.createNewTransaction(
            fromAddress, toAddress, amountSatoshi
        ) ?: return TransferResponseModel(
            success = false,
            error = "Failed to create transaction (BlockCypher returned null)",
            txHash = null
        )

        val derivedKey = derivePrivateKey(addressType, accountIndex)
        val publicKey = derivedKey.publicKey()
        val pubKeyHex = publicKey.toHex()

        val signatures = mutableListOf<String>()
        val pubkeys = mutableListOf<String>()

        for (tosignHex in transactionRequest.tosign) {
            val hashBytes = Hex.decode(tosignHex)
            val compactSig = Crypto.sign(hashBytes, derivedKey)
            // BlockCypher requires DER-encoded signatures
            val derSig = Secp256k1.compact2der(compactSig.toByteArray())
            signatures.add(Hex.encode(derSig))
            pubkeys.add(pubKeyHex)
        }

        val signedTx = BitcoinTransactionModel(
            tx = transactionRequest.tx,
            tosign = transactionRequest.tosign,
            signatures = signatures,
            pubkeys = pubkeys
        )

        val result = BitcoinApiService.INSTANCE.sendTransaction(signedTx)
        return if (result != null) {
            TransferResponseModel(success = true, error = null, txHash = result.tx.hash)
        } else {
            TransferResponseModel(success = false, error = "Failed to broadcast transaction", txHash = null)
        }
    }

    /**
     * Derive the secp256k1 private key for a given address type and account index.
     */
    private fun derivePrivateKey(
        addressType: BitcoinAddressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): PrivateKey {
        val isMainnet = Config.shared.getNetwork() == Network.MAINNET
        val coinType = if (isMainnet) 0 else 1
        val purpose = when (addressType) {
            BitcoinAddressType.NATIVE_SEGWIT -> 84
            BitcoinAddressType.NESTED_SEGWIT -> 49
            BitcoinAddressType.LEGACY -> 44
        }
        val path = KeyPath("m/${purpose}'/${coinType}'/${accountIndex}'/0/0")
        return DeterministicWallet.derivePrivateKey(master, path).privateKey
    }

    /**
     * Estimate the transaction fee in satoshis via BlockCypher.
     * BlockCypher's `/txs/new` returns the estimated fee in the tx skeleton,
     * accounting for UTXO count and script types.
     *
     * @param toAddress Destination address
     * @param amountSatoshi Amount in satoshis
     * @param addressType Sender address type
     * @param accountIndex HD wallet account index
     * @return Fee in satoshis, or null if estimation failed
     */
    suspend fun estimateFee(
        toAddress: String,
        amountSatoshi: Long,
        addressType: BitcoinAddressType = BitcoinAddressType.NATIVE_SEGWIT,
        accountIndex: Int = 0
    ): Long? {
        val fromAddress = getAddressByType(addressType, accountIndex)
        val txSkeleton = BitcoinApiService.INSTANCE.createNewTransaction(
            fromAddress, toAddress, amountSatoshi
        ) ?: return null
        return txSkeleton.tx.fees
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        return try {
            val signedTxModel = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            }.decodeFromString<BitcoinTransactionModel>(dataSigned)
            val result = BitcoinApiService.INSTANCE.sendTransaction(signedTxModel)
            if (result != null) {
                TransferResponseModel(
                    success = true,
                    error = null,
                    txHash = result.tx.hash
                )
            } else {
                TransferResponseModel(
                    success = false,
                    error = "Failed to broadcast Bitcoin transaction",
                    txHash = null
                )
            }
        } catch (e: Exception) {
            TransferResponseModel(
                success = false,
                error = e.message,
                txHash = null
            )
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> Chain.Mainnet.name
            else -> Chain.Testnet4.name
        }
    }


}