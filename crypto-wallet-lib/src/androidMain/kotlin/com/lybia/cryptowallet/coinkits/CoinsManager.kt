package com.lybia.cryptowallet.coinkits

import com.google.gson.JsonObject
import com.lybia.cryptowallet.coinkits.bitcoin.model.BTCTransactionData
import com.lybia.cryptowallet.coinkits.bitcoin.networking.BTCBalanceHandle
import com.lybia.cryptowallet.coinkits.bitcoin.networking.BTCTransactionsHandle
import com.lybia.cryptowallet.coinkits.bitcoin.networking.Gbtc
import com.lybia.cryptowallet.coinkits.cardano.networking.ADAAddressUsedHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.ADABalanceHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.ADACurrentBlockHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.ADAEstimateFeeHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.ADASendCoinHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.ADATransactionsHandle
import com.lybia.cryptowallet.coinkits.cardano.networking.Gada
import com.lybia.cryptowallet.coinkits.cardano.networking.models.ADATransaction
import com.lybia.cryptowallet.coinkits.cardano.networking.models.CardanoCurrentBestBlock
import com.lybia.cryptowallet.coinkits.centrality.model.CennzAddress
import com.lybia.cryptowallet.coinkits.centrality.networking.CennzEstimateFeeHandle
import com.lybia.cryptowallet.coinkits.centrality.networking.CennzGetAddressHandle
import com.lybia.cryptowallet.coinkits.centrality.networking.CennzGetBalanceHandle
import com.lybia.cryptowallet.coinkits.centrality.networking.CentralityNetwork
import com.lybia.cryptowallet.coinkits.centrality.networking.toHexWithPrefix
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.Change
import com.lybia.cryptowallet.coinkits.hdwallet.bip39.ACTBIP39Exception
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.ACTHDWallet
import com.lybia.cryptowallet.coinkits.hdwallet.bip44.isAddress
import com.lybia.cryptowallet.coinkits.ripple.model.XRPTransaction
import com.lybia.cryptowallet.coinkits.ripple.model.transaction.XRPMemo
import com.lybia.cryptowallet.coinkits.ripple.networking.Gxrp
import com.lybia.cryptowallet.coinkits.ripple.networking.XRPBalanceHandle
import com.lybia.cryptowallet.coinkits.ripple.networking.XRPSubmitTxtHandle
import com.lybia.cryptowallet.coinkits.ripple.networking.XRPTransactionsHandle
import java.util.Locale

interface BalanceHandle {
    fun completionHandler(balance: Double, success: Boolean)
}

interface TransactionsHandle {
    fun completionHandler(transactions: Array<TransationData>?, moreParam: JsonObject?, errStr: String)
}

interface SendCoinHandle {
    fun completionHandler(transID: String, success: Boolean, errStr: String)
}

interface EstimateFeeHandle {
    fun completionHandler(estimateFee: Double, errStr: String)
}

interface ICoinsManager {
    fun getHDWallet(): ACTHDWallet?
    fun setNetworks(networks: Array<ACTNetwork>)
    fun currentNetwork(coin: ACTCoin): ACTNetwork?
    fun cleanAll()
    fun firstAddress(coin: ACTCoin): ACTAddress?
    fun addresses(coin: ACTCoin): Array<ACTAddress>?
    fun getBalance(coin: ACTCoin, completionHandler: BalanceHandle)
    fun getTransactions(coin: ACTCoin, moreParam: JsonObject?, completionHandler: TransactionsHandle)
    fun getADATransactions(addresses: Array<ACTAddress>, completionHandler: TransactionsHandle)
    fun sendCoin(fromAddress: ACTAddress,
                 toAddressStr: String,
                 serAddressStr: String,
                 amount: Double,
                 networkFee: Double,
                 serviceFee: Double,
                 networkMemo: MemoData? = null,
                 completionHandler: SendCoinHandle
    )

    fun estimateFee(amount: Double,
                    serAddressStr: String,
                    paramFee: Double,
                    networkMinFee: Double = 0.0,
                    serviceFee: Double = 0.0,
                    network: ACTNetwork,
                    completionHandler: EstimateFeeHandle
    )
}

class CoinsManager : ICoinsManager {
    companion object {
        val shared = CoinsManager()
    }

    private var hdWallet: ACTHDWallet? = null
    private var prvKeysManager = mutableMapOf<String, Array<ACTPrivateKey>>()
    private var extendPrvKeysNumber = mutableMapOf<String, Int>()
    private var addressesManager = mutableMapOf<String, Array<ACTAddress>>()
    private var coinsSupported = arrayListOf(ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Cardano, ACTCoin.Ripple,
        ACTCoin.Centrality, ACTCoin.Centrality, ACTCoin.XCoin)
    private var networkManager = mutableMapOf(ACTCoin.Bitcoin.symbolName() to ACTNetwork(ACTCoin.Bitcoin, true),
            ACTCoin.Ethereum.symbolName() to ACTNetwork(ACTCoin.Ethereum, true),
            ACTCoin.Cardano.symbolName() to ACTNetwork(ACTCoin.Cardano, false),
            ACTCoin.Ripple.symbolName() to ACTNetwork(ACTCoin.Ripple, true),
            ACTCoin.XCoin.symbolName() to ACTNetwork(ACTCoin.XCoin, true),
            ACTCoin.Centrality.symbolName() to ACTNetwork(ACTCoin.Centrality, false))
    var mnemonicRecover = ""
    var mnemonic = ""

    override fun getHDWallet(): ACTHDWallet? {
        return when (hdWallet != null) {
            true -> hdWallet
            false -> {
                try {
                    hdWallet = ACTHDWallet(mnemonic)
                    hdWallet
                } catch (e: ACTBIP39Exception) {
                    null
                }
            }
        }
    }

    override fun setNetworks(networks: Array<ACTNetwork>) {
        /* Store mnemonic before clean data */
        val mn = mnemonic
        cleanAll()
        /* Restore mnemonic */
        mnemonic = mn
        coinsSupported.clear()
        networkManager.clear()
        networks.forEach {
            val coin = it.coin
            coinsSupported.add(coin)
            networkManager[coin.symbolName()] = it
        }
    }

    override fun currentNetwork(coin: ACTCoin): ACTNetwork? {
        return networkManager[coin.symbolName()]
    }

    override fun cleanAll() {
        hdWallet = null
        mnemonic = ""
        mnemonicRecover = ""
        addressesManager.clear()
        extendPrvKeysNumber.clear()
        prvKeysManager.clear()
    }

    override fun firstAddress(coin: ACTCoin): ACTAddress? {
        val adds = addresses(coin)
        return if ((adds != null) && adds.isNotEmpty()) {
            adds.first()
        } else {
            null
        }
    }

    override fun addresses(coin: ACTCoin): Array<ACTAddress>? {
        return when (coinsSupported.contains(coin)) {
            false -> null
            true -> {
                val symbolName = coin.symbolName()
                val adds = addressesManager[symbolName]
                when (adds != null) {
                    true -> adds
                    false -> {
                        when (coin) {
                            ACTCoin.Centrality -> {
                                val seed = getHDWallet()!!.calculateSeed(ACTNetwork(ACTCoin.Centrality, false))
                                CentralityNetwork.shared.getPublicAddress(seed.toHexWithPrefix(), object :
                                    CennzGetAddressHandle {
                                    override fun completionHandler(address: CennzAddress?, error: String) {
                                        if(address != null) {
                                            val actAddress = ACTAddress(address.address, ACTNetwork(ACTCoin.Centrality, false))
                                            addressesManager[symbolName] = arrayOf(actAddress)
                                        }
                                    }
                                })
                                null
                            }
                            else -> {
                                val prvKeys = privateKeys(coin)
                                if (prvKeys != null) {
                                    addressesManager[symbolName] = prvKeys.map { ACTAddress(it.publicKey()) }.toTypedArray()
                                    addressesManager[symbolName]
                                } else {
                                    null
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    override fun getBalance(coin: ACTCoin, completionHandler: BalanceHandle) {
        val adds = addresses(coin)
        if ((adds != null) && adds.isNotEmpty()) {
            when (coin) {
                ACTCoin.Bitcoin -> {
                    getBTCBalance(adds, completionHandler)
                }
                ACTCoin.Ethereum -> {
                    getETHBalance(adds.first(), completionHandler)
                }
                ACTCoin.Cardano -> {
                    getADABalance(adds, completionHandler)
                }
                ACTCoin.Ripple -> {
                    getXRPBalance(adds.first(), completionHandler)
                }
                ACTCoin.Centrality -> {
                    getCentralityBalance(adds.first().rawAddressString(),
                    coin.assetId,
                    completionHandler)
                }
                else -> {}
            }
        } else {
            completionHandler.completionHandler(0.0, false)
        }
    }

    override fun getTransactions(coin: ACTCoin, moreParam: JsonObject?, completionHandler: TransactionsHandle) {
        val adds = addresses(coin)
        if ((adds != null) && adds.isNotEmpty()) {
            when (coin) {
                ACTCoin.Bitcoin -> {
                    getBTCTransactions(adds, completionHandler)
                }
                ACTCoin.Ethereum -> {
                    getETHTransactions(adds.first(), completionHandler)
                }
                ACTCoin.Cardano -> {
                    getADATransactions(adds, completionHandler)
                }
                ACTCoin.Ripple -> {
                    getXRPTransactions(adds.first(), moreParam, completionHandler)
                }
                else -> {}
            }
        } else {
            completionHandler.completionHandler(arrayOf(), null, "")
        }
    }

    override fun estimateFee(amount: Double,
                             serAddressStr: String,
                             paramFee: Double,
                             networkMinFee: Double,
                             serviceFee: Double,
                             network: ACTNetwork,
                             completionHandler: EstimateFeeHandle
    ) {
        when (network.coin) {
            ACTCoin.Bitcoin -> {
                completionHandler.completionHandler(0.0, "TO DO")
            }
            ACTCoin.Ethereum -> {
                completionHandler.completionHandler(0.0, "TO DO")
            }
            ACTCoin.Ripple -> {
                val hasSerFee = serAddressStr.isAddress(ACTCoin.Ripple)
                completionHandler.completionHandler(ACTCoin.Ripple.feeDefault() * (if (hasSerFee) 2 else 1), "")
            }
            ACTCoin.Cardano -> {
                val prvKeys = privateKeys(ACTCoin.Cardano) ?: arrayOf()
                val addresses = addresses(ACTCoin.Cardano) ?: arrayOf()
                if (prvKeys.isNotEmpty() and addresses.isNotEmpty() and (prvKeys.size == addresses.size)) {
                    val unspentAddresses = addresses.map { it.rawAddressString() }.toTypedArray()
                    Gada.shared.calculateEstimateFee(prvKeys,
                            unspentAddresses,
                            addresses.first(),
                            addresses.first().rawAddressString(),
                            amount,
                            serAddressStr,
                            paramFee,
                            networkMinFee,
                            serviceFee,
                            object : ADAEstimateFeeHandle {
                                override fun completionHandler(estimateFee: Double, errStr: String) {
                                    completionHandler.completionHandler(estimateFee, errStr)
                                }
                            })
                } else {
                    completionHandler.completionHandler(0.0, "Error")
                }
            }
            ACTCoin.Centrality -> {
                CentralityNetwork.shared.calculateEstimateFee(object : CennzEstimateFeeHandle {
                    override fun completionHandler(estimateFee: Long, error: String) {
                        completionHandler.completionHandler(estimateFee.toDouble(), "")
                    }

                })
            }
            else -> {}
        }
    }

    override fun sendCoin(fromAddress: ACTAddress,
                          toAddressStr: String,
                          serAddressStr: String,
                          amount: Double,
                          networkFee: Double,
                          serviceFee: Double,
                          networkMemo: MemoData?,
                          completionHandler: SendCoinHandle
    ) {
        when (fromAddress.network.coin) {
            ACTCoin.Bitcoin -> {
                sendBTCCoin(
                        fromAddress,
                        toAddressStr,
                        serAddressStr,
                        amount,
                        networkFee,
                        serviceFee,
                        completionHandler)
            }
            ACTCoin.Ethereum -> {
                sendETHCoin(
                        fromAddress,
                        toAddressStr,
                        serAddressStr,
                        amount,
                        networkFee,
                        serviceFee,
                        completionHandler)
            }
            ACTCoin.Cardano -> {
                sendADACoin(
                        fromAddress,
                        toAddressStr,
                        serAddressStr,
                        amount,
                        networkFee,
                        serviceFee,
                        completionHandler)
            }
            ACTCoin.Ripple -> {
                sendXRPCoin(
                        fromAddress,
                        toAddressStr,
                        serAddressStr,
                        amount,
                        networkFee,
                        serviceFee,
                        networkMemo,
                        null,
                        completionHandler)
            }
            else -> {}
        }
    }

    /*
    * Private methods
    */
    private fun privateKeys(coin: ACTCoin): Array<ACTPrivateKey>? {
        val symbolName = coin.symbolName()
        val extName = symbolName + Change.External.value.toString()
        val inName = symbolName + Change.Internal.value.toString()
        val extPrvKeys = prvKeysManager[extName] ?: arrayOf()
        val inPrvKeys = prvKeysManager[inName] ?: arrayOf()

        val checkExt = checkExtendPrvKeys(Change.External, coin)
        val checkIn = checkExtendPrvKeys(Change.Internal, coin)

        return when (checkExt.isNeed or checkIn.isNeed) {
            false -> (extPrvKeys).plus(inPrvKeys)
            true -> {
                val wallet = getHDWallet()
                when (wallet != null) {
                    false -> null
                    true -> {
                        val nw = networkManager[symbolName]
                        if (nw != null) {
                            val keys: ACTHDWallet.Result = wallet.generatePrivateKeys(checkExt.count,
                                    checkExt.fromIdx,
                                    checkIn.count,
                                    checkIn.fromIdx,
                                    nw)
                            prvKeysManager[checkExt.keyName] = extPrvKeys.plus(keys.extKeys)
                            prvKeysManager[checkIn.keyName] = inPrvKeys.plus(keys.intKeys)
                            prvKeysManager[checkExt.keyName]!!.plus(prvKeysManager[checkIn.keyName]!!)
                        } else {
                            null
                        }
                    }
                }

            }
        }
    }

    private data class Result(val isNeed: Boolean, val fromIdx: Int, val count: Int, val keyName: String)

    private fun checkExtendPrvKeys(change: Change, coin: ACTCoin): Result {
        val symbolName = coin.symbolName()
        val keyName = symbolName + change.value.toString()
        val nw = networkManager[symbolName]
        val prvKeys = prvKeysManager[keyName] ?: arrayOf()
        var extNum = extendPrvKeysNumber[keyName] ?: 0
        return when (nw != null) {
            false -> Result(false, 0, 0, keyName)
            true -> {
                val begin = nw!!.derivateIdxMax(change)
                val total = begin + extNum
                val count = total - prvKeys.count()
                Result(count > 0, prvKeys.count(), count, keyName)
            }
        }
    }

    private fun getBTCBalance(addresses: Array<ACTAddress>, completionHandler: BalanceHandle) {
        val adds = addresses.map { it.rawAddressString() }
        if (adds.isNotEmpty()) {
            Gbtc.shared.getBalance(adds.toTypedArray(), object : BTCBalanceHandle {
                override fun completionHandler(balance: Double, err: Throwable?) {
                    if ((err != null) or (balance < 0)) {
                        completionHandler.completionHandler(0.0, false)
                    } else {
                        completionHandler.completionHandler(balance, true)
                    }
                }
            })
        } else {
            completionHandler.completionHandler(0.0, false)
        }
    }

    private fun getETHBalance(address: ACTAddress, completionHandler: BalanceHandle) {
        completionHandler.completionHandler(0.0, false)
    }

    private fun getADABalance(addresses: Array<ACTAddress>, completionHandler: BalanceHandle) {
        val adds = addresses.map { it.rawAddressString() }
        if (adds.isNotEmpty()) {
            Gada.shared.getBalance(adds.toTypedArray(), object : ADABalanceHandle {
                override fun completionHandler(balance: Double, err: Throwable?) {
                    if ((err != null) or (balance < 0)) {
                        completionHandler.completionHandler(0.0, false)
                    } else {
                        completionHandler.completionHandler(balance, true)
                    }
                }
            })
        } else {
            completionHandler.completionHandler(0.0, false)
        }
    }

    private fun getXRPBalance(address: ACTAddress, completionHandler: BalanceHandle) {
        val addString = address.rawAddressString()
        Gxrp.shared.getBalance(addString, object : XRPBalanceHandle {
            override fun completionHandler(balance: Double, err: Throwable?) {
                if ((err != null) or (balance < 0)) {
                    completionHandler.completionHandler(0.0, false)
                } else {
                    completionHandler.completionHandler(balance, true)
                }
            }
        })
    }

    private fun getCentralityBalance(
        address: String,
        assetId: Int,
        completionHandler: BalanceHandle
    ) {
        CentralityNetwork.shared.scanAccount(address, assetId, object : CennzGetBalanceHandle {
            override fun completionHandler(
                balance: Long,
                error: String
            ) {
                completionHandler.completionHandler(balance.toDouble(), true)
            }
        })
    }

    private fun getBTCTransactions(addresses: Array<ACTAddress>, completionHandler: TransactionsHandle) {
        val adds = addresses.map { it.rawAddressString() }
        if (adds.isNotEmpty()) {
            Gbtc.shared.transactions(adds.toTypedArray(), object : BTCTransactionsHandle {
                override fun completionHandler(transactions: Array<BTCTransactionData>, err: Throwable?) {
                    completionHandler.completionHandler(transactions.toTransactionDatas(adds.toTypedArray()), null, err?.localizedMessage
                            ?: "Error")
                }
            })
        } else {
            completionHandler.completionHandler(null, null, "Error")
        }
    }

    private fun getETHTransactions(address: ACTAddress, completionHandler: TransactionsHandle) {
        completionHandler.completionHandler(arrayOf(), null, "TO DO")
    }

    override fun getADATransactions(addresses: Array<ACTAddress>, completionHandler: TransactionsHandle) {
        val adds = addresses.map { it.rawAddressString() }
        if (adds.isNotEmpty()) {
            Gada.shared.bestblock(object : ADACurrentBlockHandle {
                override fun completionHandler(currentBestBlock: CardanoCurrentBestBlock?, errStr: String) {
                    if (currentBestBlock != null) {
                        Gada.shared.addressUsed(adds.toTypedArray(), completionHandler = object :
                            ADAAddressUsedHandle {
                            override fun completionHandler(addressUsed: Array<String>, err: Throwable?) {
                                Gada.shared.transactions(addressUsed,
                                        currentBestBlock.blockHash,
                                        null,
                                        null,
                                        ignoreAddsUsed = true,
                                        completionHandler = object : ADATransactionsHandle {
                                            override fun completionHandler(transactions: Array<ADATransaction>?, err: Throwable?) {
                                                if (transactions != null) {
                                                    completionHandler.completionHandler(transactions.toTransactionDatas(addressUsed), null, "")
                                                } else {
                                                    completionHandler.completionHandler(null, null, err?.localizedMessage
                                                            ?: "Error")
                                                }
                                            }
                                        })
                            }
                        })
                    } else {
                        completionHandler.completionHandler(null, null, "Error")
                    }
                }

            })

        } else {
            completionHandler.completionHandler(null, null, "Error")
        }
    }

    private fun getXRPTransactions(address: ACTAddress, moreParam: JsonObject?, completionHandler: TransactionsHandle) {
        Gxrp.shared.getTransactions(address.rawAddressString(), moreParam, object :
            XRPTransactionsHandle {
            override fun completionHandler(transactions: XRPTransaction?, err: Throwable?) {
                if (transactions != null) {
                    val xrpTranFilter = transactions.transactions!!.filter { it.meta.result.uppercase(Locale.getDefault())
                        .contains("SUCCESS") }.toTypedArray()
                    val trans = xrpTranFilter.toTransactionDatas(address.rawAddressString())
                    if (transactions.marker == null) {
                        completionHandler.completionHandler(trans, null, "")
                    } else {
                        completionHandler.completionHandler(trans, transactions.marker, "")
                    }
                } else {
                    completionHandler.completionHandler(null, null, err?.localizedMessage ?: "Error")
                }
            }
        })
    }

    private fun sendBTCCoin(fromAddress: ACTAddress,
                            toAddressStr: String,
                            serAddressStr: String,
                            amount: Double,
                            networkFee: Double,
                            serviceFee: Double,
                            completionHandler: SendCoinHandle
    ) {
        completionHandler.completionHandler("", false, "TO DO")
    }

    private fun sendETHCoin(fromAddress: ACTAddress,
                            toAddressStr: String,
                            serAddressStr: String,
                            amount: Double,
                            networkFee: Double,
                            serviceFee: Double,
                            completionHandler: SendCoinHandle
    ) {
        completionHandler.completionHandler("", false, "TO DO")
    }

    private fun sendADACoin(fromAddress: ACTAddress,
                            toAddressStr: String,
                            serAddressStr: String,
                            amount: Double,
                            networkFee: Double,
                            serviceFee: Double,
                            completionHandler: SendCoinHandle
    ) {
        val prvKeys = privateKeys(ACTCoin.Cardano) ?: arrayOf()
        val addresses = addresses(ACTCoin.Cardano) ?: arrayOf()

        if (prvKeys.isNotEmpty() and addresses.isNotEmpty() and (prvKeys.size == addresses.size)) {
            val unspentAddresses = addresses.map { it.rawAddressString() }.toTypedArray()
            Gada.shared.sendCoin(prvKeys,
                    unspentAddresses,
                    fromAddress,
                    toAddressStr,
                    serAddressStr,
                    amount,
                    networkFee,
                    serviceFee,
                    completionHandler = object : ADASendCoinHandle {
                        override fun completionHandler(transID: String, success: Boolean, errStr: String) {
                            completionHandler.completionHandler(transID, success, errStr)
                        }
                    })
        } else {
            completionHandler.completionHandler("", false, "")
        }
    }

    private fun sendXRPCoin(fromAddress: ACTAddress,
                            toAddressStr: String,
                            serAddressStr: String,
                            amount: Double,
                            networkFee: Double,
                            serviceFee: Double,
                            networkMemo: MemoData?,
                            sequence: Int? = null,
                            completionHandler: SendCoinHandle
    ) {
        val prvKeys = privateKeys(ACTCoin.Ripple)
                ?: return completionHandler.completionHandler("", false, "Not supported")
        val priKey = prvKeys.first()
        val memo = if (networkMemo != null) XRPMemo(networkMemo.memo, networkMemo.destinationTag) else null
        Gxrp.shared.sendCoin(priKey, fromAddress, toAddressStr, amount, networkFee, memo, sequence, object :
            XRPSubmitTxtHandle {
            override fun completionHandler(transID: String, sequence: Int?, success: Boolean, errStr: String) {
                completionHandler.completionHandler(transID, success, errStr)
                /* Check to send service fee */
                if (success && serAddressStr.isAddress(ACTCoin.Ripple) && serviceFee > 0) {
                    sendXRPCoin(fromAddress, serAddressStr, "", serviceFee, networkFee, 0.0, null, (sequence!! + 1), object :
                        SendCoinHandle {
                        override fun completionHandler(transID: String, success: Boolean, errStr: String) {}
                    })
                }
            }
        })
    }
}