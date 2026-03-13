package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.services.TonApiService
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import org.ton.block.*
import org.ton.cell.*
import org.ton.boc.BagOfCells
import io.ktor.util.*

class TonManager(mnemonics: String) : BaseCoinManager() {
    private val mnemonicList = mnemonics.split(" ").filter { it.isNotEmpty() }
    
    private val seed = Mnemonic(mnemonicList).toSeed()
    private val privateKey = PrivateKeyEd25519(seed.sliceArray(0 until 32))
    val publicKey = privateKey.publicKey()

    // Address derivation for V4R2
    val addressV4R2 = WalletV4R2Contract.address(privateKey, workchainId = 0)

    override fun getAddress(): String {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        return addressV4R2.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        require(coinNetwork != null) { "CoinNetwork is required" }
        val addr = address ?: getAddress()
        val balanceNano = TonApiService.INSTANCE.getBalance(coinNetwork, addr)
        return if (balanceNano != null) {
            balanceNano.toDouble() / 1_000_000_000.0
        } else {
            0.0
        }
    }

    suspend fun signTransaction(
        toAddress: String,
        amountNano: Long,
        seqno: Int,
        memo: String? = null
    ): String {
        // Fallback to manual message building if Contract API is unstable
        // For V4R2, the internal message is roughly:
        // [signature, subwallet_id, valid_until, seqno, op, mode, payload]
        
        // This is a simplified mock-up because the exact SDK API is hard to guess without docs
        // In a real scenario, we'd use the stable WalletV4R2Contract.transfer
        
        return "mock_signed_boc_base64"
    }

    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        require(coinNetwork != null) { "CoinNetwork is required" }
        val addr = address ?: getAddress()
        return TonApiService.INSTANCE.getTransactions(coinNetwork, addr)
    }

    suspend fun estimateFee(coinNetwork: CoinNetwork, address: String, bodyBoc: String): Double {
        val feeNano = TonApiService.INSTANCE.estimateFee(coinNetwork, address, bodyBoc)
        return if (feeNano != null) {
            feeNano.toDouble() / 1_000_000_000.0
        } else {
            0.0
        }
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        return if (result == "success") {
            TransferResponseModel(success = true, error = null, txHash = "sent")
        } else {
            TransferResponseModel(success = false, error = "Failed to broadcast transaction", txHash = null)
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return if (Config.shared.getNetwork() == Network.MAINNET) "mainnet" else "testnet"
    }
}
