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

class BitcoinManager(mnemonics: String) : BaseCoinManager() {
    private val seed = MnemonicCode.toSeed(mnemonics, "")
    private val master = DeterministicWallet.generate(seed)
    private var walletAddress: String? = null
    private var keyPath: KeyPath? = null


    fun getLegacyAddress(): String? {
        when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                keyPath = KeyPath("m/44'/0'/0'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(master, keyPath!!)
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Mainnet.chainHash)
            }

            else -> {
                keyPath = KeyPath("m/44'/1'/0'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(master, keyPath!!)
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Testnet4.chainHash)
            }
        }
        return walletAddress
    }

    fun getNativeSegWitAddress(numberAccount: Int = 0): String? {
        when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                keyPath = KeyPath("m/84'/0'/${numberAccount}'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(
                        master,
                        keyPath!!
                    )
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Mainnet.chainHash)
            }

            else -> {
                keyPath = KeyPath("m/84'/1'/${numberAccount}'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(
                        master,
                        keyPath!!
                    )
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Testnet4.chainHash)
            }
        }


        return walletAddress
    }

    fun getSegWitAddress(): String? {
        when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                keyPath = KeyPath("m/49'/0'/0'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(master, keyPath!!)
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Mainnet.chainHash)
            }

            else -> {
                keyPath = KeyPath("m/49'/1'/0'/0/0")
                val account =
                    DeterministicWallet.derivePrivateKey(master, keyPath!!)
                walletAddress =
                    Bitcoin.computeBIP84Address(account.publicKey, Chain.Testnet4.chainHash)
            }
        }

        return walletAddress
    }


    override fun getAddress(): String {
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
        val transactionRequest =
            BitcoinApiService.INSTANCE.createNewTransaction(walletAddress!!, toAddress, 100)
        require(transactionRequest != null) { "Transaction request is null" }
        val pubsKey = mutableListOf<String>()
        require(keyPath != null) { "Key path is null" }
        val signature: List<String> = transactionRequest.tosign.map { sign ->
            val privateKeyStr = DeterministicWallet.derivePrivateKey(
                master,
                keyPath!!
            ).privateKey.toBase58(Base58.Prefix.SecretKeyTestnet)
            val privateKey =
                PrivateKey.fromBase58(privateKeyStr, Base58.Prefix.SecretKeyTestnet).first
            val publicKey = privateKey.publicKey()
            val data = Crypto.sha256(sign.encodeToByteArray())
            val encoded = Crypto.sign(
                data,
                privateKey
            )

            require(Crypto.verifySignature(data, encoded, publicKey)) { "Invalid signature" }

            val sig = Crypto.compact2der(encoded)

            pubsKey.add(publicKey.value.toString())

            Hex.encode(sig.toByteArray())

        }

        val requestSend = BitcoinTransactionModel(
            transactionRequest.tx,
            transactionRequest.tosign,
            signature,
            pubsKey
        )

        println(requestSend)
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        TODO("Not yet implemented")
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> Chain.Mainnet.name
            else -> Chain.Testnet4.name
        }
    }


}