package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ton.JettonMetadata
import com.lybia.cryptowallet.services.TonApiService
import org.ton.contract.wallet.*
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import org.ton.block.*
import org.ton.tlb.storeTlb
import org.ton.tlb.loadTlb
import org.ton.cell.*
import org.ton.boc.BagOfCells
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.jsonPrimitive
import org.ton.cell.buildCell

@OptIn(ExperimentalEncodingApi::class)
class TonManager(mnemonics: String) : BaseCoinManager(), ITokenAndNFT {
    private val mnemonicList = mnemonics.split(" ").filter { it.isNotEmpty() }

    private val seed = Mnemonic(mnemonicList).toSeed()
    private val privateKey = PrivateKeyEd25519(seed.sliceArray(0 until 32))
    val publicKey = privateKey.publicKey()

    // Use WalletV4R2 instead of WalletV4R2Contract to avoid LiteClient requirement
//    private val wallet = WalletV4R2Contract(publicKey, workchain = 0)
    val address: AddrStd = WalletV4R2Contract.address(
        privateKey = privateKey,
        workchainId = 0
    )

    override fun getAddress(): String {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        return address.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
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

    private suspend fun getJettonWalletAddress(
        userAddress: String,
        jettonMasterAddress: String,
        coinNetwork: CoinNetwork
    ): String? {
        val userAddr = MsgAddressInt.parse(userAddress)
        val bocBytes =
            BagOfCells(CellBuilder.createCell { storeTlb(MsgAddressInt, userAddr) }).toByteArray()
        val stackParams = listOf(
            listOf("tvm.Slice", Base64.Default.encode(bocBytes))
        )

        val resAddr = TonApiService.INSTANCE.runGetMethod(
            coinNetwork,
            jettonMasterAddress,
            "get_wallet_address",
            stackParams
        )
        if (resAddr?.ok == true && resAddr.result?.stack?.isNotEmpty() == true) {
            val jettonWalletAddrBoc = resAddr.result.stack[0][1].jsonPrimitive.content
            val jettonWalletAddr =
                BagOfCells(Base64.Default.decode(jettonWalletAddrBoc)).roots[0].beginParse()
                    .loadTlb(MsgAddressInt)
            return jettonWalletAddr.toString()
        }
        return null
    }

    override suspend fun getBalanceToken(
        address: String,
        contractAddress: String,
        coinNetwork: CoinNetwork
    ): Double {
        val jettonWalletAddr =
            getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return 0.0

        val resData =
            TonApiService.INSTANCE.runGetMethod(coinNetwork, jettonWalletAddr, "get_wallet_data")
        if (resData?.ok == true && resData.result?.stack?.isNotEmpty() == true) {
            val balanceRaw = resData.result.stack[0][1].jsonPrimitive.content
            val balanceNano = try {
                if (balanceRaw.startsWith("0x")) balanceRaw.substring(2)
                    .toLong(16) else balanceRaw.toLong()
            } catch (e: Exception) {
                0L
            }

            return balanceNano.toDouble() / 1_000_000_000.0
        }
        return 0.0
    }

    suspend fun getJettonMetadata(
        contractAddress: String,
        coinNetwork: CoinNetwork
    ): JettonMetadata? {
        val res =
            TonApiService.INSTANCE.runGetMethod(coinNetwork, contractAddress, "get_jetton_data")
        if (res?.ok == true && res.result?.stack != null && res.result.stack.size >= 4) {
            val contentBoc = res.result.stack[3][1].jsonPrimitive.content
            val contentCell = BagOfCells(Base64.Default.decode(contentBoc)).roots[0]
            val slice = contentCell.beginParse()

            val layout = slice.loadUInt(8).toInt()
            if (layout == 0x01) {
                // Try loadBits and convert if loadSnakeString is missing
                val url = slice.loadBitString(slice.remainingBits).toByteArray().decodeToString()
                var cleanUrl = url.filter { it.code in 32..126 } // Simple cleanup
                if (cleanUrl.startsWith("ipfs://")) {
                    cleanUrl = cleanUrl.replace("ipfs://", "https://ipfs.io/ipfs/")
                }
                return TonApiService.INSTANCE.getJettonMetadataFromUrl(cleanUrl)
            }
        }
        return null
    }

    override suspend fun getTransactionHistoryToken(
        address: String,
        contractAddress: String,
        coinNetwork: CoinNetwork
    ): Any? {
        val jettonWalletAddr =
            getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return null
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
            storeUInt(0x0f8a7ea5, 32) // transfer op-code (TEP-74)
            storeUInt(0, 64)          // query_id
            storeTlb(Coins, Coins(jettonAmountNano))
            storeTlb(MsgAddressInt, MsgAddressInt.parse(toAddress))
            storeTlb(MsgAddressInt, address) // response_destination = sender
            storeBoolean(false)              // custom_payload = null
            storeTlb(Coins, Coins(forwardTonAmountNano))
            storeBoolean(memo != null)
            if (memo != null) {
                val memoCell = CellBuilder.createCell {
                    storeUInt(0, 32)
                    storeBytes(memo.encodeToByteArray())
                }
                storeRef(memoCell)
            }
        }

        val walletId = WalletContract.DEFAULT_WALLET_ID
        val stateInit = if (seqno == 0) {
            WalletV4R2Contract.stateInit(WalletV4R2Contract.Data(0, publicKey)).load()
        } else null

        val transfer = WalletTransfer {
            destination = AddrStd.parse(myJettonWallet)
            bounceable = true // Jetton wallet is a smart contract
            coins = Coins(totalTonAmountNano)
            messageData = MessageData.Raw(jettonBody, null, null)
        }

        val message = WalletV4R2Contract.transferMessage(
            address = address,
            stateInit = stateInit,
            privateKey = privateKey,
            walletId = walletId,
            validUntil = Int.MAX_VALUE,
            seqno = seqno,
            transfer
        )

        val cell = buildCell { storeTlb(Message.Any, message) }
        return Base64.Default.encode(BagOfCells(cell).toByteArray())
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
                storeUInt(0, 32) // text comment prefix
                storeBytes(memo.encodeToByteArray())
            }
        } else Cell.empty()

        val walletId = WalletContract.DEFAULT_WALLET_ID
        val stateInit = if (seqno == 0) {
            WalletV4R2Contract.stateInit(WalletV4R2Contract.Data(0, publicKey)).load()
        } else null

        val transfer = WalletTransfer {
            destination = AddrStd.parse(toAddress)
            bounceable = false // safe default for user wallets
            coins = Coins(amountNano)
            messageData = MessageData.Raw(payload, null, null)
        }

        val message = WalletV4R2Contract.transferMessage(
            address = address,
            stateInit = stateInit,
            privateKey = privateKey,
            walletId = walletId,
            validUntil = Int.MAX_VALUE,
            seqno = seqno,
            transfer
        )

        val cell = buildCell { storeTlb(Message.Any, message) }
        return Base64.Default.encode(BagOfCells(cell).toByteArray())
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
            TransferResponseModel(
                success = false,
                error = "Failed to broadcast transaction",
                txHash = null
            )
        }
    }

    override suspend fun getChainId(coinNetwork: CoinNetwork): String {
        return if (Config.shared.getNetwork() == Network.MAINNET) "mainnet" else "testnet"
    }

    suspend fun resolveDns(
        domain: String,
        coinNetwork: CoinNetwork,
        category: Int = 0x9fd3
    ): String? {
        val rootDns = "Ef_SByTMM97KVRlaEFIqX_67pYI67FPRu_YBaAs7_pS48_p6"
        var currentResolver = rootDns

        val normalized = domain.lowercase().removeSuffix(".")
        val labels = normalized.split(".")

        var bitsToResolve = 0
        val builder = CellBuilder.beginCell()
        labels.reversed().forEach { label ->
            val bytes = label.encodeToByteArray()
            builder.storeBytes(bytes)
            builder.storeUInt(0, 8)
            bitsToResolve += (bytes.size + 1) * 8
        }
        var currentSubdomainCell = builder.endCell()

        repeat(5) { // Limit recursion to prevent infinite loops
            val subdomainBoc = Base64.Default.encode(BagOfCells(currentSubdomainCell).toByteArray())
            val stack = listOf(
                listOf("tvm.Slice", subdomainBoc),
                listOf("num", category.toString())
            )

            val res = TonApiService.INSTANCE.runGetMethod(
                coinNetwork,
                currentResolver,
                "dnsresolve",
                stack
            )
            if (res?.ok != true || res.result?.stack == null || res.result.stack.size < 2) return null

            val resolvedBits = try {
                val element = res.result.stack[0]
                val value = element[1].jsonPrimitive.content
                if (value.startsWith("0x")) value.substring(2).toInt(16) else value.toInt()
            } catch (e: Exception) {
                0
            }

            if (resolvedBits == 0) return null

            val resultCellBoc = res.result.stack[1][1].jsonPrimitive.content
            if (resultCellBoc == "null" || resultCellBoc.isEmpty()) return null

            val resultCell = BagOfCells(Base64.Default.decode(resultCellBoc)).roots[0]

            if (resolvedBits == bitsToResolve) {
                // Fully resolved
                val slice = resultCell.beginParse()
                if (slice.remainingBits >= 16) {
                    val resCategory = slice.loadUInt(16).toInt()
                    if (resCategory == category) {
                        return if (category == 0x9fd3) {
                            slice.loadTlb(MsgAddressInt).toString()
                        } else if (category == 0xe8d2) {
                            // TEP-81 Domain name
                            val domainSlice = slice.loadRef().beginParse()
                            val firstByte = domainSlice.loadUInt(8).toInt()
                            if (firstByte == 0x00) {
                                val bytes = domainSlice.loadBitString(domainSlice.remainingBits)
                                    .toByteArray()
                                bytes.decodeToString()
                            } else null
                        } else null
                    }
                }
                // Fallback
                return try {
                    if (category == 0x9fd3) {
                        resultCell.beginParse().loadTlb(MsgAddressInt).toString()
                    } else null
                } catch (e: Exception) {
                    null
                }
            } else {
                // Partially resolved, get next resolver
                val slice = resultCell.beginParse()
                if (slice.remainingBits >= 16) {
                    val resCategory = slice.loadUInt(16).toInt()
                    if (resCategory == 1) { // Category 1 is next resolver
                        currentResolver = slice.loadTlb(MsgAddressInt).toString()

                        // Update subdomain cell to remaining bits
                        val remainingBits = bitsToResolve - resolvedBits
                        val fullSlice = currentSubdomainCell.beginParse()
                        fullSlice.skipBits(resolvedBits)
                        currentSubdomainCell = CellBuilder.createCell {
                            storeBits(fullSlice.loadBitString(remainingBits))
                        }
                        bitsToResolve = remainingBits
                    } else return null
                } else return null
            }
        }

        return null
    }

    suspend fun reverseResolveDns(address: String, coinNetwork: CoinNetwork): String? {
        val addr = MsgAddressInt.parse(address)
        if (addr !is AddrStd) return null

        // Construct hex address domain: <hex>.addr.reverse
        val accountIdHex = addr.address.toByteArray().joinToString("") {
            it.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
        val domain = "$accountIdHex.addr.reverse"

        // Category 0xe8d2 for domain name (TEP-81)
        return resolveDns(domain, coinNetwork, category = 0xe8d2)
    }
}
