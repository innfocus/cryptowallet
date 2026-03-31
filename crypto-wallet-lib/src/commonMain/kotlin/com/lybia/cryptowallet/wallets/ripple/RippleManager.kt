package com.lybia.cryptowallet.wallets.ripple

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ripple.RippleTransactionEntry
import com.lybia.cryptowallet.services.RippleApiService
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet

/**
 * Ripple/XRP wallet manager for commonMain.
 * Implements IWalletManager using ACTHDWallet for key derivation (Secp256k1, coinType=144)
 * and RippleApiService for networking.
 */
class RippleManager(
    mnemonic: String,
    private val apiService: RippleApiService = RippleApiService.INSTANCE
) : BaseCoinManager(), IWalletManager {

    private val network = ACTNetwork(ACTCoin.Ripple, false)
    private val hdWallet = ACTHDWallet(mnemonic)
    private var walletAddress: String? = null

    companion object {
        /** 1 XRP = 1,000,000 drops */
        const val XRP_DROPS_PER_UNIT = 1_000_000.0
    }

    init {
        // Derive the first external address (m/44'/144'/0'/0/0)
        val publicKey = hdWallet.generateExternalPublicKey(0, network)
        val actAddress = ACTAddress(publicKey)
        walletAddress = actAddress.rawAddressString()
    }

    override fun getAddress(): String {
        return walletAddress ?: ""
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        val addr = address ?: walletAddress ?: return 0.0
        val balanceDrops = apiService.getBalance(addr)
        return (balanceDrops?.toLongOrNull() ?: 0L) / XRP_DROPS_PER_UNIT
    }

    override suspend fun getTransactionHistory(
        address: String?,
        coinNetwork: CoinNetwork?
    ): List<RippleTransactionEntry>? {
        val addr = address ?: walletAddress ?: return null
        val response = apiService.getTransactionHistory(addr)
        return response?.result?.transactions
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        return try {
            val response = apiService.submitTransaction(dataSigned)
            val result = response?.result
            if (result?.engineResult == "tesSUCCESS" || result?.engineResult == "terQUEUED") {
                TransferResponseModel(
                    success = true,
                    error = null,
                    txHash = result.tx_json?.hash
                )
            } else {
                TransferResponseModel(
                    success = false,
                    error = result?.engineResultMessage ?: "Transaction failed",
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
        // Ripple doesn't have a chain ID concept like Ethereum
        return "ripple"
    }
}
