package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ton.JettonMetadata
import com.lybia.cryptowallet.services.TonApiService
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.contract.wallet.WalletTransfer
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import org.ton.block.*
import org.ton.tlb.Coins
import org.ton.cell.*
import org.ton.boc.BagOfCells
import io.ktor.util.*
import kotlinx.serialization.json.jsonPrimitive

class TonManager(mnemonics: String) : BaseCoinManager(), ITokenAndNFT {
    private val mnemonicList = mnemonics.split(" ").filter { it.isNotEmpty() }
    
    private val seed = Mnemonic(mnemonicList).toSeed()
    private val privateKey = PrivateKeyEd25519(seed.sliceArray(0 until 32))
    val publicKey = privateKey.publicKey()

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

    private suspend fun getJettonWalletAddress(userAddress: String, jettonMasterAddress: String, coinNetwork: CoinNetwork): String? {
        val userAddr = MsgAddressInt.parse(userAddress)
        val stackParams = listOf(
            listOf("tvm.Slice", CellBuilder.createCell { storeAddress(userAddr) }.toBoc().encodeBase64())
        )
        
        val resAddr = TonApiService.INSTANCE.runGetMethod(coinNetwork, jettonMasterAddress, "get_wallet_address", stackParams)
        if (resAddr?.ok == true && resAddr.result?.stack?.isNotEmpty() == true) {
            val jettonWalletAddrBoc = resAddr.result.stack[0][1].jsonPrimitive.content
            val jettonWalletAddr = BagOfCells(jettonWalletAddrBoc.decodeBase64Bytes()).roots[0].beginParse().loadAddress()
            return jettonWalletAddr.toString()
        }
        return null
    }

    override suspend fun getBalanceToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double {
        val jettonWalletAddr = getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return 0.0
        
        val resData = TonApiService.INSTANCE.runGetMethod(coinNetwork, jettonWalletAddr, "get_wallet_data")
        if (resData?.ok == true && resData.result?.stack?.isNotEmpty() == true) {
            val balanceRaw = resData.result.stack[0][1].jsonPrimitive.content
            val balanceNano = try {
                if (balanceRaw.startsWith("0x")) balanceRaw.substring(2).toLong(16) else balanceRaw.toLong()
            } catch (e: Exception) { 0L }
            
            // Decimals should come from metadata, using 9 as fallback
            return balanceNano.toDouble() / 1_000_000_000.0 
        }
        return 0.0
    }

    /**
     * T6.3: Lấy Metadata của Jetton
     */
    suspend fun getJettonMetadata(contractAddress: String, coinNetwork: CoinNetwork): JettonMetadata? {
        val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, contractAddress, "get_jetton_data")
        if (res?.ok == true && res.result?.stack != null && res.result.stack.size >= 4) {
            // Phần tử thứ 4 (index 3) là jetton_content (Cell)
            val contentBoc = res.result.stack[3][1].jsonPrimitive.content
            val contentCell = BagOfCells(contentBoc.decodeBase64Bytes()).roots[0]
            val slice = contentCell.beginParse()
            
            val layout = slice.loadUInt(8).toInt()
            if (layout == 0x01) { // Off-chain metadata (URL)
                var url = slice.loadSnakeString()
                if (url.startsWith("ipfs://")) {
                    url = url.replace("ipfs://", "https://ipfs.io/ipfs/")
                }
                return TonApiService.INSTANCE.getJettonMetadataFromUrl(url)
            } else if (layout == 0x00) { // On-chain metadata (Dict)
                // TODO: Implement On-chain parsing if needed. 
                // Hầu hết các Jetton lớn (USDT, TON) dùng off-chain JSON.
            }
        }
        return null
    }

    override suspend fun getTransactionHistoryToken(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any? {
        val jettonWalletAddr = getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return null
        return TonApiService.INSTANCE.getTransactions(coinNetwork, jettonWalletAddr)
    }

    override suspend fun getNFT() {
        // TODO
    }

    override suspend fun TransferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        return if (result == "success") "pending" else null
    }

    suspend fun signJettonTransaction(
        jettonMasterAddress: String,
        toAddress: String,
        jettonAmountNano: Long,
        seqno: Int,
        coinNetwork: CoinNetwork,
        forwardTonAmountNano: Long = 10_000_000L,
        totalTonAmountNano: Long = 50_000_000L,
        memo: String? = null
    ): String {
        val myJettonWallet = getJettonWalletAddress(getAddress(), jettonMasterAddress, coinNetwork)
            ?: throw Exception("Could not find Jetton Wallet")

        val jettonBody = CellBuilder.createCell {
            storeUInt(0x0f8a7ea5, 32)
            storeUInt(0, 64)
            storeCoins(jettonAmountNano)
            storeAddress(MsgAddressInt.parse(toAddress))
            storeAddress(addressV4R2)
            storeBit(false)
            storeCoins(forwardTonAmountNano)
            storeBit(memo != null)
            if (memo != null) {
                val memoCell = CellBuilder.createCell {
                    storeUInt(0, 32)
                    storeBytes(memo.encodeToByteArray())
                }
                storeRef(memoCell)
            }
        }

        val transfer = WalletTransfer {
            this.destination = AddrStd.parse(myJettonWallet)
            this.coins = Coins(totalTonAmountNano)
            this.body = jettonBody
            this.sendMode = 3
        }

        val walletContract = WalletV4R2Contract(publicKey, workchain = 0)
        val signedCell = walletContract.createTransferMessage(
            privateKey = privateKey,
            seqno = seqno,
            transfer = transfer
        )

        return BagOfCells(signedCell).toByteArray().encodeBase64()
    }

    suspend fun getSeqno(coinNetwork: CoinNetwork): Int {
        return TonApiService.INSTANCE.getSeqno(coinNetwork, getAddress())
    }

    suspend fun signTransaction(
        toAddress: String,
        amountNano: Long,
        seqno: Int,
        memo: String? = null
    ): String {
        val payload = if (memo != null) {
            CellBuilder.createCell {
                storeUInt(0, 32)
                storeBytes(memo.encodeToByteArray())
            }
        } else null

        val transfer = WalletTransfer {
            this.destination = AddrStd.parse(toAddress)
            this.coins = Coins(amountNano)
            this.body = payload
            this.sendMode = 3
        }

        val walletContract = WalletV4R2Contract(publicKey, workchain = 0)
        val signedCell = walletContract.createTransferMessage(
            privateKey = privateKey,
            seqno = seqno,
            transfer = transfer
        )

        return BagOfCells(signedCell).toByteArray().encodeBase64()
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
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "Failed to broadcast transaction", txHash = null)
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return if (Config.shared.getNetwork() == Network.MAINNET) "mainnet" else "testnet"
    }
}
