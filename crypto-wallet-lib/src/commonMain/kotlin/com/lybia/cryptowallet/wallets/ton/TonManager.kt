package com.lybia.cryptowallet.wallets.ton

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.models.TransferResponseModel
import com.lybia.cryptowallet.models.ton.*
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
import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MnemonicCode

/**
 * SLIP-0010 ED25519 key derivation.
 * Derives a private key from [seed] following the given hardened [path] indices.
 * All path components MUST be hardened (bit 31 set).
 */
private fun slip10DeriveEd25519(seed: ByteArray, path: IntArray): ByteArray {
    // Master key: HMAC-SHA512(key="ed25519 seed", data=seed)
    var iBytes = Crypto.hmac512("ed25519 seed".encodeToByteArray(), seed)
    var kL = iBytes.sliceArray(0 until 32)
    var kR = iBytes.sliceArray(32 until 64)

    for (index in path) {
        // Child key (hardened only): HMAC-SHA512(key=chainCode, data=0x00 || kL || ser32(index))
        val data = ByteArray(37)
        data[0] = 0x00
        kL.copyInto(data, 1)
        data[33] = (index ushr 24).toByte()
        data[34] = (index ushr 16).toByte()
        data[35] = (index ushr 8).toByte()
        data[36] = index.toByte()
        iBytes = Crypto.hmac512(kR, data)
        kL = iBytes.sliceArray(0 until 32)
        kR = iBytes.sliceArray(32 until 64)
    }
    return kL
}

@OptIn(ExperimentalEncodingApi::class)
class TonManager(mnemonics: String) : BaseCoinManager(), ITokenAndNFT {
    private val logger = Logger.withTag("TonManager")
    private val mnemonicList = mnemonics.split(" ").filter { it.isNotEmpty() }

    private val privateKey: PrivateKeyEd25519 = when (mnemonicList.size) {
        24 -> {
            // TON native 24-word mnemonic
            val seed = Mnemonic(mnemonicList).toSeed()
            PrivateKeyEd25519(seed.sliceArray(0 until 32))
        }
        else -> {
            // BIP39 mnemonic (12-word) — SLIP-0010 ED25519, path m/44'/607'/0'
            // Used by Tonkeeper and hardware wallets
            val bip39Seed = MnemonicCode.toSeed(mnemonicList, "")
            val privateKeyBytes = slip10DeriveEd25519(bip39Seed, intArrayOf(
                0x80000000.toInt() or 44,
                0x80000000.toInt() or 607,
                0x80000000.toInt() or 0
            ))
            PrivateKeyEd25519(privateKeyBytes)
        }
    }
    val publicKey = privateKey.publicKey()

    // Use WalletV4R2 instead of WalletV4R2Contract to avoid LiteClient requirement
//    private val wallet = WalletV4R2Contract(publicKey, workchain = 0)
    val address: AddrStd = WalletV4R2Contract.address(
        privateKey = privateKey,
        workchainId = 0
    )

    override fun getAddress(): String {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        val result = address.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
        logger.i { "TON Address: $result (Testnet: $isTestnet)" }
        return result
    }

    override suspend fun getBalance(address: String?, coinNetwork: CoinNetwork?): Double {
        require(coinNetwork != null) { "CoinNetwork is required" }
        val addr = address ?: getAddress()
        logger.i { "Getting TON balance for address: $addr" }
        val balanceNano = TonApiService.INSTANCE.getBalance(coinNetwork, addr)
        val balance = if (balanceNano != null) {
            balanceNano.toDouble() / 1_000_000_000.0
        } else {
            0.0
        }
        logger.i { "TON balance for $addr: $balance" }
        return balance
    }

    private suspend fun getJettonWalletAddress(
        userAddress: String,
        jettonMasterAddress: String,
        coinNetwork: CoinNetwork
    ): String? {
        logger.i { "Getting Jetton wallet address for $userAddress, Master: $jettonMasterAddress" }
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
            val result = jettonWalletAddr.toString()
            logger.i { "Jetton wallet address: $result" }
            return result
        }
        logger.w { "Could not find Jetton wallet address for $userAddress" }
        return null
    }

    override suspend fun getBalanceToken(
        address: String,
        contractAddress: String,
        coinNetwork: CoinNetwork
    ): Double {
        logger.i { "Getting Jetton balance for $address, Token: $contractAddress" }
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
                logger.e(e) { "Error parsing Jetton balance: $balanceRaw" }
                0L
            }

            val balance = balanceNano.toDouble() / 1_000_000_000.0
            logger.i { "Jetton balance for $address: $balance" }
            return balance
        }
        return 0.0
    }

    suspend fun getJettonMetadata(
        contractAddress: String,
        coinNetwork: CoinNetwork
    ): JettonMetadata? {
        logger.i { "Getting Jetton metadata for $contractAddress" }
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
                logger.i { "Jetton metadata URL: $cleanUrl" }
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
        logger.i { "Getting Jetton transaction history for $address, Token: $contractAddress" }
        val jettonWalletAddr =
            getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return null
        return TonApiService.INSTANCE.getTransactions(coinNetwork, jettonWalletAddr)
    }

    override suspend fun getNFT() {
        // TODO
    }

    override suspend fun TransferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        logger.i { "Broadcasting Jetton transfer" }
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        logger.i { "Broadcasting result: $result" }
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
        logger.i { "Signing Jetton transaction: $jettonAmountNano to $toAddress, seqno: $seqno" }
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
        val encoded = Base64.Default.encode(BagOfCells(cell).toByteArray())
        logger.i { "Signed Jetton transaction BOC: $encoded" }
        return encoded
    }

    suspend fun getSeqno(coinNetwork: CoinNetwork): Int {
        val s = TonApiService.INSTANCE.getSeqno(coinNetwork, getAddress())
        logger.i { "Current seqno: $s" }
        return s
    }

    suspend fun signTransaction(
        toAddress: String,
        amountNano: Long,
        seqno: Int,
        memo: String? = null
    ): String {
        logger.i { "Signing TON transaction: $amountNano to $toAddress, seqno: $seqno, memo: $memo" }
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
        val encoded = Base64.Default.encode(BagOfCells(cell).toByteArray())
        logger.i { "Signed TON transaction BOC: $encoded" }
        return encoded
    }

    // ─── NFT ─────────────────────────────────────────────────────────────────

    /** Signs a TEP-62 NFT transfer message. Returns BOC base64 ready to broadcast. */
    suspend fun signNFTTransfer(
        nftAddress: String,
        toAddress: String,
        seqno: Int,
        forwardTonAmountNano: Long = 50_000_000L,  // 0.05 TON for gas
        totalTonAmountNano: Long  = 100_000_000L,  // 0.1 TON total
        memo: String? = null
    ): String {
        logger.i { "Signing NFT transfer for $nftAddress to $toAddress" }
        val nftBody = CellBuilder.createCell {
            storeUInt(0x5fcc3d14, 32) // transfer op-code (TEP-62)
            storeUInt(0, 64)          // query_id
            storeTlb(MsgAddressInt, MsgAddressInt.parse(toAddress))  // new_owner
            storeTlb(MsgAddressInt, address)  // response_destination = sender
            storeBoolean(false)               // custom_payload = null
            storeTlb(Coins, Coins(forwardTonAmountNano))
            storeBoolean(memo != null)
            if (memo != null) {
                storeRef(CellBuilder.createCell {
                    storeUInt(0, 32)
                    storeBytes(memo.encodeToByteArray())
                })
            }
        }

        val walletId = WalletContract.DEFAULT_WALLET_ID
        val stateInit = if (seqno == 0) {
            WalletV4R2Contract.stateInit(WalletV4R2Contract.Data(0, publicKey)).load()
        } else null

        val transfer = WalletTransfer {
            destination = AddrStd.parse(nftAddress) // send TO the NFT contract
            bounceable = true
            coins = Coins(totalTonAmountNano)
            messageData = MessageData.Raw(nftBody, null, null)
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
        val encoded = Base64.Default.encode(BagOfCells(cell).toByteArray())
        logger.i { "Signed NFT transfer BOC: $encoded" }
        return encoded
    }

    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        require(coinNetwork != null) { "CoinNetwork is required" }
        val addr = address ?: getAddress()
        logger.i { "Getting TON transaction history for $addr" }
        return TonApiService.INSTANCE.getTransactions(coinNetwork, addr)
    }

    suspend fun estimateFee(coinNetwork: CoinNetwork, address: String, bodyBoc: String): Double {
        logger.i { "Estimating fee for $address" }
        val feeNano = TonApiService.INSTANCE.estimateFee(coinNetwork, address, bodyBoc)
        val fee = if (feeNano != null) {
            feeNano.toDouble() / 1_000_000_000.0
        } else {
            0.0
        }
        logger.i { "Estimated fee: $fee TON" }
        return fee
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        logger.i { "Broadcasting TON transaction" }
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        logger.i { "Broadcast result: $result" }
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
        logger.i { "Resolving DNS for domain: $domain, category: $category" }
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
                        val result = if (category == 0x9fd3) {
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
                        logger.i { "DNS resolved to: $result" }
                        return result
                    }
                }
                // Fallback
                return try {
                    if (category == 0x9fd3) {
                        val r = resultCell.beginParse().loadTlb(MsgAddressInt).toString()
                        logger.i { "DNS fallback resolved to: $r" }
                        r
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
                        logger.i { "DNS next resolver: $currentResolver" }

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
        logger.i { "Reverse resolving DNS for address: $address" }
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

    suspend fun getNominatorStakingBalance(
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): TonStakingBalance? {
        logger.i { "Getting Nominator staking balance for pool: $poolAddress" }
        val userAddr = getAddress()
        val bocBytes = BagOfCells(CellBuilder.createCell { storeTlb(MsgAddressInt, MsgAddressInt.parse(userAddr)) }).toByteArray()
        val stackParams = listOf(
            listOf("tvm.Slice", Base64.Default.encode(bocBytes))
        )

        val res = TonApiService.INSTANCE.runGetMethod(
            coinNetwork,
            poolAddress,
            "get_nominator_data",
            stackParams
        )
        if (res?.ok == true && res.result?.stack?.isNotEmpty() == true && res.result.stack.size >= 5) {
            // Stack: [utime, amount, pending_deposit, pending_withdrawal, liquid_balance]
            val amountRaw = res.result.stack[1][1].jsonPrimitive.content
            val pendingDepositRaw = res.result.stack[2][1].jsonPrimitive.content
            val pendingWithdrawalRaw = res.result.stack[3][1].jsonPrimitive.content
            val liquidBalanceRaw = res.result.stack[4][1].jsonPrimitive.content

            val amountNano = amountRaw.removePrefix("0x").toLong(if (amountRaw.startsWith("0x")) 16 else 10)
            val pendingDepositNano = pendingDepositRaw.removePrefix("0x").toLong(if (pendingDepositRaw.startsWith("0x")) 16 else 10)
            val pendingWithdrawalNano = pendingWithdrawalRaw.removePrefix("0x").toLong(if (pendingWithdrawalRaw.startsWith("0x")) 16 else 10)
            val liquidBalanceNano = liquidBalanceRaw.removePrefix("0x").toLong(if (liquidBalanceRaw.startsWith("0x")) 16 else 10)

            val balance = TonStakingBalance(
                poolAddress = poolAddress,
                amount = amountNano.toDouble() / 1_000_000_000.0,
                pendingDeposit = pendingDepositNano.toDouble() / 1_000_000_000.0,
                pendingWithdrawal = pendingWithdrawalNano.toDouble() / 1_000_000_000.0,
                liquidBalance = liquidBalanceNano.toDouble() / 1_000_000_000.0
            )
            logger.i { "Nominator staking balance: $balance" }
            return balance
        }
        return null
    }

    suspend fun getTonstakersStakingBalance(
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): TonStakingBalance? {
        logger.i { "Getting Tonstakers staking balance for pool: $poolAddress" }
        // PoolAddress here is the Tonstakers Master contract
        // Tonstakers (tsTON)
        val tsTonBalance = getBalanceToken(getAddress(), poolAddress, coinNetwork)
        if (tsTonBalance == 0.0) return TonStakingBalance(poolAddress, 0.0)

        val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_pool_full_data")
        if (res?.ok == true && res.result?.stack != null && res.result.stack.size >= 2) {
            val totalStakingBalanceRaw = res.result.stack[0][1].jsonPrimitive.content
            val totalTokensSupplyRaw = res.result.stack[1][1].jsonPrimitive.content

            val totalStakingBalance = totalStakingBalanceRaw.removePrefix("0x").toLong(if (totalStakingBalanceRaw.startsWith("0x")) 16 else 10)
            val totalTokensSupply = totalTokensSupplyRaw.removePrefix("0x").toLong(if (totalTokensSupplyRaw.startsWith("0x")) 16 else 10)

            if (totalTokensSupply > 0) {
                val rate = totalStakingBalance.toDouble() / totalTokensSupply.toDouble()
                val amountInTon = tsTonBalance * rate
                val balance = TonStakingBalance(
                    poolAddress = poolAddress,
                    amount = amountInTon,
                    rewards = amountInTon - tsTonBalance // Simplified rewards
                )
                logger.i { "Tonstakers staking balance: $balance" }
                return balance
            }
        }

        return TonStakingBalance(poolAddress, tsTonBalance)
    }

    suspend fun getBemoStakingBalance(
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): TonStakingBalance? {
        logger.i { "Getting Bemo staking balance for pool: $poolAddress" }
        // Bemo (stTON)
        val stTonBalance = getBalanceToken(getAddress(), poolAddress, coinNetwork)
        if (stTonBalance == 0.0) return TonStakingBalance(poolAddress, 0.0)

        val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_full_data")
        if (res?.ok == true && res.result?.stack != null && res.result.stack.size >= 2) {
            // Stack elements for Bemo: [total_ton, total_st_ton, ...]
            val totalTonRaw = res.result.stack[0][1].jsonPrimitive.content
            val totalStTonRaw = res.result.stack[1][1].jsonPrimitive.content

            val totalTon = totalTonRaw.removePrefix("0x").toLong(if (totalTonRaw.startsWith("0x")) 16 else 10)
            val totalStTon = totalStTonRaw.removePrefix("0x").toLong(if (totalStTonRaw.startsWith("0x")) 16 else 10)

            if (totalStTon > 0) {
                val rate = totalTon.toDouble() / totalStTon.toDouble()
                val amountInTon = stTonBalance * rate
                val balance = TonStakingBalance(
                    poolAddress = poolAddress,
                    amount = amountInTon,
                    rewards = amountInTon - stTonBalance
                )
                logger.i { "Bemo staking balance: $balance" }
                return balance
            }
        }
        return TonStakingBalance(poolAddress, stTonBalance)
    }

    suspend fun signDepositToNominatorPool(
        poolAddress: String,
        amountNano: Long,
        seqno: Int
    ): String {
        logger.i { "Signing deposit to Nominator Pool: $poolAddress, amount: $amountNano" }
        // Standard Nominator Pool deposit op-code is 0x4e73746b ("nstk")
        val payload = CellBuilder.createCell {
            storeUInt(0x4e73746b, 32)
        }

        val walletId = WalletContract.DEFAULT_WALLET_ID
        val stateInit = if (seqno == 0) {
            WalletV4R2Contract.stateInit(WalletV4R2Contract.Data(0, publicKey)).load()
        } else null

        val transfer = WalletTransfer {
            destination = AddrStd.parse(poolAddress)
            bounceable = true
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
        val encoded = Base64.Default.encode(BagOfCells(cell).toByteArray())
        logger.i { "Signed Nominator Pool deposit BOC: $encoded" }
        return encoded
    }

    suspend fun signTonstakersDeposit(
        masterAddress: String,
        amountNano: Long,
        seqno: Int
    ): String {
        logger.i { "Signing Tonstakers deposit: $masterAddress, amount: $amountNano" }
        // Tonstakers deposit (simple transfer to master contract)
        // Usually, it's just a transfer to master address.
        // Some protocols use specific op-codes.
        return signTransaction(masterAddress, amountNano, seqno)
    }

    suspend fun signBemoDeposit(
        masterAddress: String,
        amountNano: Long,
        seqno: Int
    ): String {
        logger.i { "Signing Bemo deposit: $masterAddress, amount: $amountNano" }
        // Bemo deposit
        return signTransaction(masterAddress, amountNano, seqno)
    }
}
