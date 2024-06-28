package com.lybia.cryptowallet.wallets.bitcoin

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.BitcoinApiService
import fr.acinq.bitcoin.Bitcoin
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.secp256k1.Secp256k1

class BitcoinManager(mnemonics: String): BaseCoinManager() {
    private val seed = MnemonicCode.toSeed(mnemonics, "")
    private val master = DeterministicWallet.generate(seed)
    private var walletAddress: String? = null
    private var chain: Chain = when (Config.shared.getNetwork()) {
        Network.MAINNET -> Chain.Mainnet
        else -> Chain.Testnet
    }


    fun getLegacyAddress(): String? {
        val account = DeterministicWallet.derivePrivateKey(master, KeyPath("m/44'/0'/0'/0/0"))

        walletAddress = Bitcoin.computeBIP44Address(account.publicKey, chain.chainHash)

        return walletAddress
    }

    fun getNativeSegWitAddress(numberAccount: Int = 0): String? {
        val account =
            DeterministicWallet.derivePrivateKey(master, KeyPath("m/84'/0'/${numberAccount}'/0/0"))
        walletAddress = Bitcoin.computeBIP84Address(account.publicKey, chain.chainHash)

        return walletAddress
    }

    fun getSegWitAddress(): String? {
        val account = DeterministicWallet.derivePrivateKey(master, KeyPath("m/49'/0'/0'/0/0"))

        walletAddress = Bitcoin.computeBIP49Address(account.publicKey, chain.chainHash)

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

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        TODO("Not yet implemented")
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return chain.name
    }


}