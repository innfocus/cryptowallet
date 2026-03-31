package com.lybia.cryptowallet.coinkits

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogcatWriter
import com.google.gson.JsonObject
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.enums.ACTNetwork
import com.lybia.cryptowallet.wallets.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.enums.Change
import com.lybia.cryptowallet.wallets.hdwallet.bip39.ACTBIP39Exception
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTAddress
import com.lybia.cryptowallet.wallets.hdwallet.bip44.ACTHDWallet
import com.lybia.cryptowallet.wallets.hdwallet.bip44.isAddress
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.models.MemoData
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TokenInfo
import com.lybia.cryptowallet.models.TransationData
import com.lybia.cryptowallet.models.toTransactionDatas
import com.lybia.cryptowallet.services.NFTListHandle
import com.lybia.cryptowallet.services.NFTService
import com.lybia.cryptowallet.services.NFTTransferHandle
import com.lybia.cryptowallet.services.SendTokenHandle
import com.lybia.cryptowallet.services.TokenBalanceHandle
import com.lybia.cryptowallet.services.TokenService
import com.lybia.cryptowallet.services.TokenTransactionsHandle
import com.lybia.cryptowallet.coinkits.ton.TonService
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.wallets.ton.TonManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

interface BalanceHandle {
    fun completionHandler(balance: Double, success: Boolean)
}

interface TransactionsHandle {
    fun completionHandler(
        transactions: Array<TransationData>?,
        moreParam: JsonObject?,
        errStr: String
    )
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
    fun getTransactions(
        coin: ACTCoin,
        moreParam: JsonObject?,
        completionHandler: TransactionsHandle
    )

    fun getADATransactions(addresses: Array<ACTAddress>, completionHandler: TransactionsHandle)
    fun sendCoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        networkMemo: MemoData? = null,
        completionHandler: SendCoinHandle
    )

    fun estimateFee(
        amount: Double,
        serAddressStr: String,
        paramFee: Double,
        networkMinFee: Double = 0.0,
        serviceFee: Double = 0.0,
        network: ACTNetwork,
        completionHandler: EstimateFeeHandle
    )
}

/** Token (Jetton / ERC-20) operations, dispatched per-chain. */
interface ITokenManager {
    fun getTokenBalance(
        coin: ACTCoin, address: String, contractAddress: String,
        completionHandler: TokenBalanceHandle
    )

    fun getTokenTransactions(
        coin: ACTCoin, address: String, contractAddress: String,
        completionHandler: TokenTransactionsHandle
    )

    fun sendToken(
        coin: ACTCoin, toAddress: String, contractAddress: String,
        amount: Double, decimals: Int, memo: String? = null,
        completionHandler: SendTokenHandle
    )
}

/** NFT operations, dispatched per-chain. */
interface INFTManager {
    fun getNFTs(coin: ACTCoin, address: String, completionHandler: NFTListHandle)
    fun transferNFT(
        coin: ACTCoin, nftAddress: String, toAddress: String,
        memo: String? = null, completionHandler: NFTTransferHandle
    )
}

class CoinsManager : ICoinsManager, ITokenManager, INFTManager,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

    private val logger = Logger.withTag("CoinsManager")

    init {
        // Initialize Kermit to write to Android Logcat
        Logger.setLogWriters(LogcatWriter())
    }

    companion object {
        val shared = CoinsManager()
    }

    // Lazily created; mnemonicProvider lambda ensures fresh mnemonic on every call.
    private val tonService: TonService by lazy { TonService({ mnemonic }, this) }

    /**
     * CommonCoinsManager from commonMain — delegates migrated coin operations.
     * Lazily initialized with the current mnemonic.
     */
    private val commonManager: CommonCoinsManager by lazy {
        CommonCoinsManager(mnemonic = _mnemonic)
    }

    private var hdWallet: ACTHDWallet? = null
    private var prvKeysManager = mutableMapOf<String, Array<ACTPrivateKey>>()
    private var extendPrvKeysNumber = mutableMapOf<String, Int>()
    private var addressesManager = mutableMapOf<String, Array<ACTAddress>>()
    private var coinsSupported = arrayListOf(
        ACTCoin.Bitcoin, ACTCoin.Ethereum, ACTCoin.Cardano, ACTCoin.Ripple,
        ACTCoin.Centrality, ACTCoin.XCoin, ACTCoin.TON
    )
    private var networkManager = mutableMapOf(
        ACTCoin.Bitcoin.symbolName() to ACTNetwork(ACTCoin.Bitcoin, true),
        ACTCoin.Ethereum.symbolName() to ACTNetwork(ACTCoin.Ethereum, true),
        ACTCoin.Cardano.symbolName() to ACTNetwork(ACTCoin.Cardano, false),
        ACTCoin.Ripple.symbolName() to ACTNetwork(ACTCoin.Ripple, true),
        ACTCoin.XCoin.symbolName() to ACTNetwork(ACTCoin.XCoin, true),
        ACTCoin.Centrality.symbolName() to ACTNetwork(ACTCoin.Centrality, false),
        ACTCoin.TON.symbolName() to ACTNetwork(ACTCoin.TON, false)
    )
    private var _mnemonic: String = ""
    var mnemonic: String
        get() = _mnemonic
        set(value) {
            updateMnemonic(value)
        }

    /** API Keys managed via global Config */
    var apiKeyInfura: String?
        get() = Config.shared.apiKeyInfura
        set(value) { Config.shared.apiKeyInfura = value }

    var apiKeyExplorer: String?
        get() = Config.shared.apiKeyExplorer
        set(value) { Config.shared.apiKeyExplorer = value }

    var apiKeyOwlRacle: String?
        get() = Config.shared.apiKeyOwlRacle
        set(value) { Config.shared.apiKeyOwlRacle = value }

    /** Legacy support for tonApiKey, redirects to apiKeyInfura */
    var apiKeyToncenter: String?
        get() = Config.shared.apiKeyToncenter
        set(value) { Config.shared.apiKeyToncenter = value }

    fun updateMnemonic(newMnemonic: String) {
        synchronized(this) {
            logger.i { "Updating mnemonic" }
            cleanAll()
            _mnemonic = newMnemonic
        }
    }

    override fun getHDWallet(): ACTHDWallet? {
        return hdWallet ?: try {
            if (_mnemonic.isEmpty()) return null
            ACTHDWallet(_mnemonic).also { hdWallet = it }
        } catch (e: ACTBIP39Exception) {
            logger.e(e) { "Failed to create HDWallet" }
            null
        }
    }

    override fun setNetworks(networks: Array<ACTNetwork>) {
        logger.i { "Setting networks: ${networks.joinToString { it.coin.symbolName() }}" }
        /* Store mnemonic before clean data */
        val mn = _mnemonic
        cleanAll()
        /* Restore mnemonic */
        _mnemonic = mn
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
        logger.i { "Cleaning all data" }
        hdWallet = null
        _mnemonic = ""
        addressesManager.clear()
        extendPrvKeysNumber.clear()
        prvKeysManager.clear()
    }

    override fun firstAddress(coin: ACTCoin): ACTAddress? {
        return addresses(coin)?.firstOrNull()
    }

    override fun addresses(coin: ACTCoin): Array<ACTAddress>? {
        if (!coinsSupported.contains(coin)) return null

        val symbolName = coin.symbolName()
        addressesManager[symbolName]?.let { return it }

        return when (coin) {
            ACTCoin.Centrality -> {
                logger.d { "Generating Centrality address via commonManager" }
                launch {
                    try {
                        val addr = commonManager.getAddress(NetworkName.CENTRALITY)
                        val actAddress = ACTAddress(addr, ACTNetwork(ACTCoin.Centrality, false))
                        addressesManager[symbolName] = arrayOf(actAddress)
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to get Centrality address" }
                    }
                }
                addressesManager[symbolName]
            }

            ACTCoin.TON -> {
                try {
                    logger.d { "Generating TON address" }
                    val tonAddress = TonManager(_mnemonic).getAddress()
                    val network = networkManager[symbolName] ?: ACTNetwork(ACTCoin.TON, false)
                    val adds = arrayOf(ACTAddress(tonAddress, network))
                    addressesManager[symbolName] = adds
                    adds
                } catch (e: Exception) {
                    logger.e(e) { "Failed to get TON address" }
                    null
                }
            }

            else -> {
                privateKeys(coin)?.let { prvKeys ->
                    val adds = prvKeys.map { ACTAddress(it.publicKey()) }.toTypedArray()
                    addressesManager[symbolName] = adds
                    adds
                }
            }
        }
    }

    override fun getBalance(coin: ACTCoin, completionHandler: BalanceHandle) {
        val adds = addresses(coin)
        if (adds.isNullOrEmpty()) {
            logger.w { "No addresses found for $coin to get balance" }
            completionHandler.completionHandler(0.0, false)
            return
        }

        logger.i { "Getting balance for $coin" }
        when (coin) {
            ACTCoin.Bitcoin -> getBTCBalance(adds, completionHandler)
            ACTCoin.Ethereum -> getETHBalance(adds.first(), completionHandler)
            ACTCoin.Cardano -> getADABalance(adds, completionHandler)
            ACTCoin.Ripple -> getXRPBalance(adds.first(), completionHandler)
            ACTCoin.Centrality -> {
                launch {
                    try {
                        val result = commonManager.getBalance(NetworkName.CENTRALITY, adds.first().rawAddressString())
                        withContext(Dispatchers.Main) {
                            completionHandler.completionHandler(result.balance, result.success)
                        }
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to get Centrality balance" }
                        withContext(Dispatchers.Main) { completionHandler.completionHandler(0.0, false) }
                    }
                }
            }

            ACTCoin.TON -> getTonBalance(adds.first(), completionHandler)
            else -> {
                logger.w { "Coin $coin not supported for balance" }
                completionHandler.completionHandler(0.0, false)
            }
        }
    }

    override fun getTransactions(
        coin: ACTCoin,
        moreParam: JsonObject?,
        completionHandler: TransactionsHandle
    ) {
        val adds = addresses(coin)
        if (adds.isNullOrEmpty()) {
            logger.w { "No addresses found for $coin to get transactions" }
            completionHandler.completionHandler(arrayOf(), null, "No addresses")
            return
        }

        logger.i { "Getting transactions for $coin" }
        when (coin) {
            ACTCoin.Bitcoin -> getBTCTransactions(adds, completionHandler)
            ACTCoin.Ethereum -> getETHTransactions(adds.first(), completionHandler)
            ACTCoin.Cardano -> getADATransactions(adds, completionHandler)
            ACTCoin.Ripple -> getXRPTransactions(adds.first(), moreParam, completionHandler)
            ACTCoin.TON -> getTonTransactions(adds.first(), completionHandler)
            else -> {
                logger.w { "Coin $coin not supported for transactions" }
                completionHandler.completionHandler(arrayOf(), null, "Not supported")
            }
        }
    }

    override fun estimateFee(
        amount: Double,
        serAddressStr: String,
        paramFee: Double,
        networkMinFee: Double,
        serviceFee: Double,
        network: ACTNetwork,
        completionHandler: EstimateFeeHandle
    ) {
        logger.i { "Estimating fee for ${network.coin.symbolName()}" }
        when (network.coin) {
            ACTCoin.Bitcoin -> {
                completionHandler.completionHandler(0.0, "TO DO")
            }

            ACTCoin.Ethereum -> {
                completionHandler.completionHandler(0.0, "TO DO")
            }

            ACTCoin.Ripple -> {
                val hasSerFee = serAddressStr.isAddress(ACTCoin.Ripple)
                completionHandler.completionHandler(
                    ACTCoin.Ripple.feeDefault() * (if (hasSerFee) 2 else 1),
                    ""
                )
            }

            ACTCoin.Cardano -> {
                // commonMain CardanoManager handles fee internally; return default fee
                completionHandler.completionHandler(ACTCoin.Cardano.feeDefault(), "")
            }

            ACTCoin.Centrality -> {
                completionHandler.completionHandler(ACTCoin.Centrality.feeDefault(), "")
            }

            ACTCoin.TON -> {
                getTonEstimateFee(amount, serAddressStr, paramFee, network, completionHandler)
            }

            else -> {
                logger.w { "Coin ${network.coin} not supported for fee estimation" }
                completionHandler.completionHandler(0.0, "Not supported")
            }
        }
    }

    override fun sendCoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        networkMemo: MemoData?,
        completionHandler: SendCoinHandle
    ) {
        val coin = fromAddress.network.coin
        logger.i { "Sending $coin: amount=$amount to=$toAddressStr" }
        when (coin) {
            ACTCoin.Bitcoin -> {
                sendBTCCoin(
                    fromAddress,
                    toAddressStr,
                    serAddressStr,
                    amount,
                    networkFee,
                    serviceFee,
                    completionHandler
                )
            }

            ACTCoin.Ethereum -> {
                sendETHCoin(
                    fromAddress,
                    toAddressStr,
                    serAddressStr,
                    amount,
                    networkFee,
                    serviceFee,
                    completionHandler
                )
            }

            ACTCoin.Cardano -> {
                sendADACoin(
                    fromAddress,
                    toAddressStr,
                    serAddressStr,
                    amount,
                    networkFee,
                    serviceFee,
                    completionHandler
                )
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
                    completionHandler
                )
            }

            ACTCoin.TON -> {
                sendTonCoin(toAddressStr, amount, networkMemo, completionHandler)
            }

            ACTCoin.Centrality -> {
                launch {
                    try {
                        val result = commonManager.sendCentrality(
                            fromAddress.rawAddressString(), toAddressStr, amount, coin.assetId
                        )
                        withContext(Dispatchers.Main) {
                            completionHandler.completionHandler(result.txHash, result.success, result.error ?: "")
                        }
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to send Centrality" }
                        withContext(Dispatchers.Main) {
                            completionHandler.completionHandler("", false, e.message ?: "Error")
                        }
                    }
                }
            }

            else -> {
                logger.w { "Coin $coin not supported for sending" }
                completionHandler.completionHandler("", false, "Not supported")
            }
        }
    }

    // ─── ITokenManager ────────────────────────────────────────────────────────

    override fun getTokenBalance(
        coin: ACTCoin, address: String, contractAddress: String,
        completionHandler: TokenBalanceHandle
    ) {
        logger.i { "Getting token balance for $coin, contract: $contractAddress" }
        when (coin) {
            ACTCoin.TON -> tonService.getTokenBalance(address, contractAddress, completionHandler)
            else -> {
                logger.w { "Token not supported for ${coin.symbolName()}" }
                completionHandler.completionHandler(
                    null,
                    false,
                    "Token not supported for ${coin.symbolName()}"
                )
            }
        }
    }

    override fun getTokenTransactions(
        coin: ACTCoin, address: String, contractAddress: String,
        completionHandler: TokenTransactionsHandle
    ) {
        logger.i { "Getting token transactions for $coin, contract: $contractAddress" }
        when (coin) {
            ACTCoin.TON -> tonService.getTokenTransactions(
                address,
                contractAddress,
                completionHandler
            )

            else -> {
                logger.w { "Token not supported for ${coin.symbolName()}" }
                completionHandler.completionHandler(
                    null,
                    "Token not supported for ${coin.symbolName()}"
                )
            }
        }
    }

    override fun sendToken(
        coin: ACTCoin, toAddress: String, contractAddress: String,
        amount: Double, decimals: Int, memo: String?,
        completionHandler: SendTokenHandle
    ) {
        logger.i { "Sending token $coin: amount=$amount to=$toAddress, contract=$contractAddress" }
        when (coin) {
            ACTCoin.TON -> tonService.sendToken(
                toAddress,
                contractAddress,
                amount,
                decimals,
                memo,
                completionHandler
            )

            else -> {
                logger.w { "Token not supported for ${coin.symbolName()}" }
                completionHandler.completionHandler(
                    "",
                    false,
                    "Token not supported for ${coin.symbolName()}"
                )
            }
        }
    }

    // ─── INFTManager ──────────────────────────────────────────────────────────

    override fun getNFTs(coin: ACTCoin, address: String, completionHandler: NFTListHandle) {
        logger.i { "Getting NFTs for $coin, address: $address" }
        when (coin) {
            ACTCoin.TON -> tonService.getNFTs(address, completionHandler)
            else -> {
                logger.w { "NFT not supported for ${coin.symbolName()}" }
                completionHandler.completionHandler(
                    null,
                    "NFT not supported for ${coin.symbolName()}"
                )
            }
        }
    }

    override fun transferNFT(
        coin: ACTCoin, nftAddress: String, toAddress: String,
        memo: String?, completionHandler: NFTTransferHandle
    ) {
        logger.i { "Transferring NFT $coin: nft=$nftAddress to=$toAddress" }
        when (coin) {
            ACTCoin.TON -> tonService.transferNFT(nftAddress, toAddress, memo, completionHandler)
            else -> {
                logger.w { "NFT not supported for ${coin.symbolName()}" }
                completionHandler.completionHandler(
                    "",
                    false,
                    "NFT not supported for ${coin.symbolName()}"
                )
            }
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

        return if (!checkExt.isNeed && !checkIn.isNeed) {
            extPrvKeys.plus(inPrvKeys)
        } else {
            val wallet = getHDWallet() ?: return null
            val nw = networkManager[symbolName] ?: return null
            val keys: ACTHDWallet.Result = wallet.generatePrivateKeys(
                checkExt.count,
                checkExt.fromIdx,
                checkIn.count,
                checkIn.fromIdx,
                nw
            )
            prvKeysManager[checkExt.keyName] = extPrvKeys.plus(keys.extKeys)
            prvKeysManager[checkIn.keyName] = inPrvKeys.plus(keys.intKeys)
            prvKeysManager[checkExt.keyName]!!.plus(prvKeysManager[checkIn.keyName]!!)
        }
    }

    private data class Result(
        val isNeed: Boolean,
        val fromIdx: Int,
        val count: Int,
        val keyName: String
    )

    private fun checkExtendPrvKeys(change: Change, coin: ACTCoin): Result {
        val symbolName = coin.symbolName()
        val keyName = symbolName + change.value.toString()
        val nw = networkManager[symbolName]
        val prvKeys = prvKeysManager[keyName] ?: arrayOf()
        val extNum = extendPrvKeysNumber[keyName] ?: 0
        return when (nw != null) {
            false -> Result(false, 0, 0, keyName)
            true -> {
                val begin = nw.derivateIdxMax(change)
                val total = begin + extNum
                val count = total - prvKeys.count()
                Result(count > 0, prvKeys.count(), count, keyName)
            }
        }
    }

    private fun getBTCBalance(addresses: Array<ACTAddress>, completionHandler: BalanceHandle) {
        launch {
            try {
                val result = commonManager.getBalance(NetworkName.BTC)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(result.balance, result.success)
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get BTC balance" }
                withContext(Dispatchers.Main) { completionHandler.completionHandler(0.0, false) }
            }
        }
    }

    private fun getETHBalance(address: ACTAddress, completionHandler: BalanceHandle) {
        logger.w { "getETHBalance: TO DO" }
        completionHandler.completionHandler(0.0, false)
    }

    private fun getADABalance(addresses: Array<ACTAddress>, completionHandler: BalanceHandle) {
        launch {
            try {
                val result = commonManager.getBalance(NetworkName.CARDANO)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(result.balance, result.success)
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get ADA balance" }
                withContext(Dispatchers.Main) { completionHandler.completionHandler(0.0, false) }
            }
        }
    }

    private fun getXRPBalance(address: ACTAddress, completionHandler: BalanceHandle) {
        launch {
            try {
                val result = commonManager.getBalance(NetworkName.XRP, address.rawAddressString())
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(result.balance, result.success)
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get XRP balance" }
                withContext(Dispatchers.Main) { completionHandler.completionHandler(0.0, false) }
            }
        }
    }

    private fun tonCoinNetwork() = CoinNetwork(name = NetworkName.TON)

    private fun getTonBalance(address: ACTAddress, completionHandler: BalanceHandle) {
        val tonManager = TonManager(_mnemonic)
        val coinNetwork = tonCoinNetwork()
        launch {
            try {
                val balance = tonManager.getBalance(address.rawAddressString(), coinNetwork)
                withContext(Dispatchers.Main) { completionHandler.completionHandler(balance, true) }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get TON balance" }
                withContext(Dispatchers.Main) { completionHandler.completionHandler(0.0, false) }
            }
        }
    }

    private fun getTonTransactions(address: ACTAddress, completionHandler: TransactionsHandle) {
        val tonManager = TonManager(_mnemonic)
        val coinNetwork = tonCoinNetwork()
        launch {
            try {
                val history =
                    tonManager.getTransactionHistory(address.rawAddressString(), coinNetwork)

                @Suppress("UNCHECKED_CAST")
                val transactions = (history as? List<TonTransaction>) ?: emptyList()
                val mapped = transactions.toTransactionDatas(address.rawAddressString()).toTypedArray()
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(
                        mapped,
                        null,
                        ""
                    )
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get TON transactions" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, null, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun getTonEstimateFee(
        amount: Double,
        serAddressStr: String,
        paramFee: Double,
        network: ACTNetwork,
        completionHandler: EstimateFeeHandle
    ) {
        val tonManager = TonManager(_mnemonic)
        val coinNetwork = tonCoinNetwork()
        launch {
            try {
                val seqno = tonManager.getSeqno(coinNetwork)
                val amountNano = (amount * 1_000_000_000).toLong()
                // Sign a dummy self-transfer to get a valid BOC for estimation
                val bocBase64 = tonManager.signTransaction(
                    toAddress = tonManager.getAddress(),
                    amountNano = amountNano,
                    seqno = seqno
                )
                val fee = tonManager.estimateFee(coinNetwork, tonManager.getAddress(), bocBase64)
                withContext(Dispatchers.Main) { completionHandler.completionHandler(fee, "") }
            } catch (e: Exception) {
                logger.e(e) { "Failed to estimate TON fee" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(ACTCoin.TON.feeDefault(), "")
                }
            }
        }
    }

    private fun sendTonCoin(
        toAddressStr: String,
        amount: Double,
        networkMemo: MemoData?,
        completionHandler: SendCoinHandle
    ) {
        val tonManager = TonManager(_mnemonic)
        val coinNetwork = tonCoinNetwork()
        launch {
            try {
                val seqno = tonManager.getSeqno(coinNetwork)
                val amountNano = (amount * 1_000_000_000).toLong()
                val memo = networkMemo?.memo?.takeIf { it.isNotEmpty() }
                val bocBase64 = tonManager.signTransaction(toAddressStr, amountNano, seqno, memo)
                val result = tonManager.transfer(bocBase64, coinNetwork)
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        logger.i { "TON transfer success: ${result.txHash}" }
                        completionHandler.completionHandler(result.txHash ?: "pending", true, "")
                    } else {
                        logger.e { "TON transfer failed: ${result.error}" }
                        completionHandler.completionHandler("", false, result.error ?: "Failed")
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to send TON coin" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler("", false, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun getBTCTransactions(
        addresses: Array<ACTAddress>,
        completionHandler: TransactionsHandle
    ) {
        launch {
            try {
                val history = commonManager.getTransactionHistory(NetworkName.BTC)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(arrayOf(), null, "")
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get BTC transactions" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, null, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun getETHTransactions(address: ACTAddress, completionHandler: TransactionsHandle) {
        logger.w { "getETHTransactions: TO DO" }
        completionHandler.completionHandler(arrayOf(), null, "TO DO")
    }

    override fun getADATransactions(
        addresses: Array<ACTAddress>,
        completionHandler: TransactionsHandle
    ) {
        launch {
            try {
                val transactions = commonManager.getTransactionHistory(NetworkName.CARDANO)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(arrayOf(), null, "")
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get ADA transactions" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, null, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun getXRPTransactions(
        address: ACTAddress,
        moreParam: JsonObject?,
        completionHandler: TransactionsHandle
    ) {
        launch {
            try {
                val history = commonManager.getTransactionHistory(NetworkName.XRP, address.rawAddressString())
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(arrayOf(), null, "")
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to get XRP transactions" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, null, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun sendBTCCoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        completionHandler: SendCoinHandle
    ) {
        logger.w { "sendBTCCoin: TO DO" }
        completionHandler.completionHandler("", false, "TO DO")
    }

    private fun sendETHCoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        completionHandler: SendCoinHandle
    ) {
        logger.w { "sendETHCoin: TO DO" }
        completionHandler.completionHandler("", false, "TO DO")
    }

    private fun sendADACoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        completionHandler: SendCoinHandle
    ) {
        launch {
            try {
                val amountLovelace = (amount * 1_000_000).toLong()
                val feeLovelace = (networkFee * 1_000_000).toLong()
                val result = commonManager.sendCardano(toAddressStr, amountLovelace, feeLovelace)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(
                        result.txHash, result.success, result.error ?: ""
                    )
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to send ADA" }
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler("", false, e.localizedMessage ?: "Error")
                }
            }
        }
    }

    private fun sendXRPCoin(
        fromAddress: ACTAddress,
        toAddressStr: String,
        serAddressStr: String,
        amount: Double,
        networkFee: Double,
        serviceFee: Double,
        networkMemo: MemoData?,
        sequence: Int? = null,
        completionHandler: SendCoinHandle
    ) {
        // Delegate to commonManager — XRP networking now in commonMain
        logger.w { "sendXRPCoin: delegating to commonManager (TO DO: full implementation)" }
        completionHandler.completionHandler("", false, "XRP send via commonManager not yet wired")
    }
}
