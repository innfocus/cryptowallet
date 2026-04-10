package com.lybia.cryptowallet.wallets.ton

import kotlin.math.pow
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.base.BaseCoinManager
import com.lybia.cryptowallet.base.ITokenAndNFT
import com.lybia.cryptowallet.base.ITokenManager
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
import org.ton.bitstring.BitString
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import com.lybia.cryptowallet.base.INFTManager
import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.wallets.bip39.Bip39Language
import com.lybia.cryptowallet.currentEpochSeconds
import com.lybia.cryptowallet.errors.WalletError
import com.lybia.cryptowallet.models.NFTItem

/** Wallet contract version. W5 (V5R1) is the current standard and default. */
enum class WalletVersion {
    /** Legacy WalletV4R2 */
    W4,
    /** Current standard WalletV5R1 — network-aware wallet ID, signature at tail */
    W5
}

/** Destination for multi-transfer (bulk send). */
data class TonDestination(
    val address: String,
    val amountNano: Long,
    val memo: String? = null,
    val bounceable: Boolean = false,
    val sendMode: Int = 3
)

/** TON staking pool types, detected via get methods on pool contract. */
enum class TonPoolType {
    NOMINATOR,
    TONSTAKERS,
    BEMO,
    UNKNOWN
}

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
class TonManager(
    mnemonics: String,
    val walletVersion: WalletVersion = WalletVersion.W5
) : BaseCoinManager(), ITokenAndNFT, ITokenManager, IStakingManager, INFTManager {

    companion object {
        /**
         * Validate TON address format (friendly, raw, or URL-safe).
         * Friendly: 48-char base64 (e.g. "UQ...", "EQ...", "kQ...", "0Q...")
         * Raw: "workchain:hex" (e.g. "0:abc...def")
         */
        fun isValidTonAddress(address: String): Boolean = try {
            AddrStd.parse(address)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val logger = Logger.withTag("TonManager")
    private val mnemonicList = Bip39Language.splitMnemonic(mnemonics)

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

    // ─── W5R1 contract code (Base64 BOC) ─────────────────────────────────────
    // Source: https://github.com/ton-blockchain/wallet-contract-v5 (WalletV5R1)
    private val W5R1_CODE_BASE64 =
        "te6cckECFAEAAoEAART/APSkE/S88sgLAQIBIAINAgFIAwQC3NAg10nBIJFbj2Mg1wsfIIIQ" +
        "ZXh0br0hghBzaW50vbCSXwPgghBleHRuuo60gCDXIQHQdNch+kAw+kT4KPpEMFi9kVvg7UTQ" +
        "gQFB1yH0BYMH9A5voTGRMOGAQNchcH/bPOAxINdJgQKAuZEw4HDiEA8CASAFDAIBIAYJAgFu" +
        "BwgAGa3OdqJoQCDrkOuF/8AAGa8d9qJoQBDrkOuFj8ACAUgKCwAXsyX7UTQcdch1wsfgABGy" +
        "YvtRNDXCgCAAGb5fD2omhAgKDrkPoCwBAvIOAR4g1wsfghBzaWduuvLgin8PAeaO8O2i7fshg" +
        "wjXIgKDCNcjIIAg1yHTH9Mf0x/tRNDSANMfINMf0//XCgAK+QFAzPkQmiiUXwrbMeHywIff" +
        "ArNQB7Dy0IRRJbry4IVQNrry4Ib4I7vy0IgikvgA3gGkf8jKAMsfAc8Wye1UIJL4D95w2zzY" +
        "EAP27aLt+wL0BCFukmwhjkwCIdc5MHCUIccAs44tAdcoIHYeQ2wg10nACPLgkyDXSsAC8uCT" +
        "INcdBscSwgBSMLDy0InXTNc5MAGk6GwShAe78uCT10rAAPLgk+1V4tIAAcAAkVvg69csCBQg" +
        "kXCWAdcsCBwS4lIQseMPINdKERITAJYB+kAB+kT4KPpEMFi68uCR7UTQgQFB1xj0BQSdf8jK" +
        "AEAEgwf0U/Lgi44UA4MH9Fvy4Iwi1woAIW4Bs7Dy0JDiyFADzxYS9ADJ7VQAcjDXLAgkji0h" +
        "8uCS0gDtRNDSAFETuvLQj1RQMJExnAGBAUDXIdcKAPLgjuLIygBYzxbJ7VST8sCN4gAQk1vb" +
        "MeHXTNC01sNe"

    // ─── Address ──────────────────────────────────────────────────────────────
    //
    // W4: address bytes are network-independent (same on mainnet & testnet).
    //     Only the display encoding changes (testOnly flag).
    //
    // W5: wallet_id encodes the networkGlobalId, so address BYTES differ
    //     between mainnet (-239) and testnet (-3).
    //     We precompute both at construction and return the right one at runtime
    //     so that signing and display are always consistent with current network.

    private val w4Address: AddrStd? =
        if (walletVersion == WalletVersion.W4)
            WalletV4R2Contract.address(privateKey = privateKey, workchainId = 0)
        else null

    private val w5MainnetAddress: AddrStd? =
        if (walletVersion == WalletVersion.W5) computeW5Address(isTestnet = false) else null

    private val w5TestnetAddress: AddrStd? =
        if (walletVersion == WalletVersion.W5) computeW5Address(isTestnet = true) else null

    val address: AddrStd
        get() = when (walletVersion) {
            WalletVersion.W4 -> w4Address!!
            WalletVersion.W5 ->
                if (Config.shared.getNetwork() == Network.TESTNET) w5TestnetAddress!!
                else w5MainnetAddress!!
        }

    // ─── W5 helpers ──────────────────────────────────────────────────────────

    /**
     * Calculate W5 wallet_id = context XOR networkGlobalId.
     * context = parse(1bit=1 | workchain(8) | version=0(8) | subwallet=0(15)) as int32
     * For workchain=0: context = 0x80000000 = Int.MIN_VALUE
     * networkGlobalId: -239 for mainnet, -3 for testnet
     */
    private fun calculateW5WalletId(isTestnet: Boolean = false): Int {
        val networkGlobalId = if (isTestnet) -3 else -239
        // context bits: 1_00000000_00000000_000000000000000 = 0x80000000 for workchain=0
        val context = Int.MIN_VALUE  // 0x80000000 for workchain=0, subwallet=0
        return context xor networkGlobalId
    }

    private fun loadW5CodeCell(): Cell =
        BagOfCells(Base64.decode(W5R1_CODE_BASE64)).first()

    private fun buildW5DataCell(walletId: Int): Cell =
        CellBuilder.createCell {
            storeBoolean(true)                           // is_signature_allowed = true
            storeUInt(0, 32)                         // initial seqno = 0
            storeUInt(walletId, 32)                  // wallet_id (uint32)
            storeBytes(publicKey.key.toByteArray())  // 256-bit public key
            storeBoolean(false)                          // empty extensions dict
        }

    private fun buildW5StateInit(isTestnet: Boolean): StateInit =
        StateInit(loadW5CodeCell(), buildW5DataCell(calculateW5WalletId(isTestnet)))

    private fun computeW5Address(isTestnet: Boolean): AddrStd {
        val stateInitRef = CellRef(buildW5StateInit(isTestnet), StateInit)
        return AddrStd(0, stateInitRef.hash())
    }

    /**
     * Build W5 OutList cell for a single transfer.
     * TL-B: out_list$_ {n:#} prev:^(OutList n) action:OutAction = OutList (n + 1)
     * ActionSendMsg: 0x0ec3c86d(32) + sendMode(8) + prev_ref + msg_ref
     */
    private fun buildW5OutListCell(transfer: WalletTransfer, sendMode: Int = 3): Cell {
        val msgCell = CellRef(transfer.toMessageRelaxed(), MessageRelaxed.tlbCodec(AnyTlbConstructor)).cell
        val emptyCell = CellBuilder.createCell {}  // OutList 0 (empty prev)
        return CellBuilder.createCell {
            storeUInt(0x0ec3c86d, 32)  // OUT_ACTION_SEND_MSG_TAG
            storeUInt(sendMode, 8)
            storeRef(emptyCell)         // prev OutList
            storeRef(msgCell)           // message
        }
    }

    /**
     * Build W5 InnerRequest cell: Maybe outList + has_other_actions(0)
     */
    private fun buildW5InnerRequest(transfer: WalletTransfer, sendMode: Int = 3): Cell {
        val outListCell = buildW5OutListCell(transfer, sendMode)
        return CellBuilder.createCell {
            storeBoolean(true)          // has out_list ref (Maybe = present)
            storeRef(outListCell)
            storeBoolean(false)         // has_other_actions = false
        }
    }

    /**
     * Build W5 OutList chain for multiple transfers.
     * Each node: OUT_ACTION_SEND_MSG_TAG(32) + sendMode(8) + ref(prev) + ref(msg)
     */
    private fun buildW5OutListCellMulti(transfers: List<WalletTransfer>, sendMode: Int = 3): Cell {
        var list = CellBuilder.createCell {} // OutList 0 (empty)
        for (transfer in transfers) {
            val msgCell = CellRef(transfer.toMessageRelaxed(), MessageRelaxed.tlbCodec(AnyTlbConstructor)).cell
            list = CellBuilder.createCell {
                storeUInt(0x0ec3c86d, 32) // OUT_ACTION_SEND_MSG_TAG
                storeUInt(sendMode, 8)
                storeRef(list)    // prev OutList
                storeRef(msgCell) // message
            }
        }
        return list
    }

    private fun buildW5InnerRequestMulti(transfers: List<WalletTransfer>, sendMode: Int = 3): Cell {
        val outListCell = buildW5OutListCellMulti(transfers, sendMode)
        return CellBuilder.createCell {
            storeBoolean(true)    // has out_list
            storeRef(outListCell)
            storeBoolean(false)   // has_other_actions = false
        }
    }

    /**
     * Build the signed external message body for W5.
     *
     * Format (ton4j / ton-swift reference):
     *   signed_request$_
     *     opcode:      uint32  = 0x7369676E
     *     wallet_id:   uint32
     *     valid_until: uint32
     *     seqno:       uint32
     *     inner:       InnerRequest
     *     signature:   bits512   ← at the TAIL
     *   = SignedRequest
     *
     * The signature signs the hash of the cell WITHOUT the signature suffix.
     */
    private fun buildW5SignedBody(seqno: Int, innerRequest: Cell): Cell {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        val walletId = calculateW5WalletId(isTestnet)

        // validUntil: 0xFFFFFFFF for seqno==0 (deployment), currentTime+60s otherwise
        val validUntilBits = if (seqno == 0) 0xFFFFFFFFL else defaultValidUntil().toLong()

        val signingBody = CellBuilder.createCell {
            storeUInt(0x7369676E, 32)           // PREFIX_SIGNED_EXTERNAL ("sign")
            storeUInt(walletId, 32)             // wallet_id
            storeUInt(validUntilBits, 32)       // valid_until
            storeUInt(seqno, 32)                // seqno
            storeBitString(innerRequest.bits)         // inner request bits
            storeRefs(innerRequest.refs)         // inner request refs
        }

        val signature = BitString(privateKey.signToByteArray(signingBody.hash().toByteArray()))

        // W5R1 uses get_last_bits(512) / remove_last_bits(512) — signature at TAIL.
        // Body layout: [signing body bits] [signing body refs] [signature 512 bits]
        return CellBuilder.createCell {
            storeBitString(signingBody.bits)
            storeRefs(signingBody.refs)
            storeBitString(signature)
        }
    }

    /** Wrap a signed W5 body in a Message<Cell> with optional StateInit. */
    private fun buildW5Message(body: Cell, stateInit: StateInit?): Message<Cell> {
        val info = ExtInMsgInfo(src = AddrNone, dest = address, importFee = Coins())
        val maybeStateInit = Maybe.of(stateInit?.let {
            Either.of<StateInit, CellRef<StateInit>>(null, CellRef(value = it, StateInit))
        })
        val messageBody = Either.of<Cell, CellRef<Cell>>(null, CellRef(value = body, AnyTlbConstructor))
        return Message(info = info, init = maybeStateInit, body = messageBody)
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    override fun getAddress(): String {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        val result = address.toString(userFriendly = true, bounceable = false, testOnly = isTestnet)
        logger.i { "TON Address (${walletVersion.name}): $result (Testnet: $isTestnet)" }
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
        coinNetwork: CoinNetwork,
        decimals: Int
    ): Double {
        logger.i { "Getting Jetton balance for $address, Token: $contractAddress, decimals: $decimals" }
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

            val divisor = 10.0.pow(decimals.toDouble())
            val balance = balanceNano.toDouble() / divisor
            logger.i { "Jetton balance for $address: $balance (decimals=$decimals)" }
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
                val url = slice.loadBitString(slice.remainingBits).toByteArray().decodeToString()
                var cleanUrl = url.filter { it.code in 32..126 }
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
        return getJettonTransactionsParsed(address, contractAddress, coinNetwork)
    }

    /**
     * Get parsed Jetton transaction history with human-readable fields.
     * Decodes TEP-74 opcodes from raw messages:
     * - 0x0f8a7ea5 = transfer (outgoing)
     * - 0x7362d09c = transfer_notification (incoming)
     * - 0x595f07bc = burn
     * - 0xd53276db = excesses (refund, skipped)
     */
    suspend fun getJettonTransactionsParsed(
        address: String,
        contractAddress: String,
        coinNetwork: CoinNetwork,
        limit: Int = 10,
        lt: String? = null,
        hash: String? = null
    ): List<JettonTransactionParsed>? {
        val jettonWalletAddr =
            getJettonWalletAddress(address, contractAddress, coinNetwork) ?: return null
        val rawTxs = TonApiService.INSTANCE.getTransactions(
            coinNetwork, jettonWalletAddr, limit, lt, hash
        ) ?: return null

        return rawTxs.mapNotNull { tx -> parseJettonTransaction(tx, address) }
    }

    private fun parseJettonTransaction(tx: TonTransaction, userAddress: String): JettonTransactionParsed? {
        // Try to parse outgoing messages first (user sent a Jetton transfer)
        tx.out_msgs?.forEach { msg ->
            val parsed = tryParseJettonMessage(msg)
            if (parsed != null) {
                return JettonTransactionParsed(
                    type = parsed.type,
                    amountNano = parsed.amount,
                    sender = userAddress,
                    recipient = parsed.destination,
                    memo = parsed.memo,
                    timestamp = tx.utime,
                    fee = tx.fee,
                    transactionId = tx.transactionId
                )
            }
        }
        // Try incoming message (received Jetton transfer_notification)
        tx.in_msg?.let { msg ->
            val parsed = tryParseJettonMessage(msg)
            if (parsed != null) {
                return JettonTransactionParsed(
                    type = if (parsed.type == "transfer_notification") "receive" else parsed.type,
                    amountNano = parsed.amount,
                    sender = parsed.sender ?: msg.source,
                    recipient = userAddress,
                    memo = parsed.memo,
                    timestamp = tx.utime,
                    fee = tx.fee,
                    transactionId = tx.transactionId
                )
            }
        }
        // Fallback: return basic info from value fields
        val inMsg = tx.in_msg
        if (inMsg != null && inMsg.value != "0") {
            return JettonTransactionParsed(
                type = "unknown",
                amountNano = inMsg.value.toLongOrNull() ?: 0L,
                sender = inMsg.source,
                recipient = inMsg.destination,
                memo = inMsg.message,
                timestamp = tx.utime,
                fee = tx.fee,
                transactionId = tx.transactionId
            )
        }
        return null
    }

    private data class ParsedJettonMsg(
        val type: String,
        val amount: Long,
        val sender: String?,
        val destination: String?,
        val memo: String?
    )

    private fun tryParseJettonMessage(msg: TonMessage): ParsedJettonMsg? {
        val body = msg.message ?: return null
        if (body.length < 8) return null

        return try {
            val bodyBytes = try {
                Base64.Default.decode(body)
            } catch (_: Exception) {
                return null
            }
            if (bodyBytes.size < 4) return null

            val opcode = ((bodyBytes[0].toInt() and 0xFF) shl 24) or
                    ((bodyBytes[1].toInt() and 0xFF) shl 16) or
                    ((bodyBytes[2].toInt() and 0xFF) shl 8) or
                    (bodyBytes[3].toInt() and 0xFF)

            when (opcode.toLong() and 0xFFFFFFFFL) {
                0x0f8a7ea5L -> { // transfer
                    ParsedJettonMsg("send", 0L, null, msg.destination, null)
                }
                0x7362d09cL -> { // transfer_notification
                    ParsedJettonMsg("transfer_notification", 0L, msg.source, null, null)
                }
                0x595f07bcL -> { // burn
                    ParsedJettonMsg("burn", 0L, null, null, null)
                }
                0xd53276dbL -> { // excesses (refund, skip)
                    null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getNFT() {
        logger.w { "getNFT() (ITokenAndNFT legacy) called — use getNFTs(address, coinNetwork) instead" }
    }

    override suspend fun TransferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        logger.i { "Broadcasting Jetton transfer" }
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        logger.i { "Broadcasting result: $result" }
        return if (result == "success") "pending" else null
    }

    // ─── ITokenManager Implementation ────────────────────────────────────────

    override suspend fun getTokenBalance(address: String, contractAddress: String, coinNetwork: CoinNetwork): Double {
        return getBalanceToken(address, contractAddress, coinNetwork)
    }

    override suspend fun getTokenTransactionHistory(address: String, contractAddress: String, coinNetwork: CoinNetwork): Any? {
        return getTransactionHistoryToken(address, contractAddress, coinNetwork)
    }

    override suspend fun transferToken(dataSigned: String, coinNetwork: CoinNetwork): String? {
        return TransferToken(dataSigned, coinNetwork)
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
        require(isValidTonAddress(toAddress)) { "Invalid recipient address: $toAddress" }
        require(isValidTonAddress(jettonMasterAddress)) { "Invalid Jetton master address: $jettonMasterAddress" }
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

        val transfer = WalletTransfer {
            destination = AddrStd.parse(myJettonWallet)
            bounceable = true // Jetton wallet is a smart contract
            coins = Coins(totalTonAmountNano)
            messageData = MessageData.Raw(jettonBody, null, null)
        }

        return when (walletVersion) {
            WalletVersion.W4 -> signTransferV4(transfer, seqno)
            WalletVersion.W5 -> signTransferV5(transfer, seqno)
        }.also { logger.i { "Signed Jetton transaction BOC: $it" } }
    }

    // ─── Bulk Transfer (W5 only) ────────────────────────────────────────

    /**
     * Sign a multi-recipient transfer in a single W5R1 transaction.
     * @param destinations list of recipients (1–255)
     * @param seqno current wallet seqno
     * @return base64-encoded BOC ready for broadcast
     */
    fun signBulkTransfer(destinations: List<TonDestination>, seqno: Int): String {
        require(destinations.isNotEmpty()) { "At least one destination required" }
        require(destinations.size <= 255) { "Maximum 255 recipients, got ${destinations.size}" }
        require(walletVersion == WalletVersion.W5) { "Bulk transfer requires W5 wallet" }
        destinations.forEach { require(isValidTonAddress(it.address)) { "Invalid address: ${it.address}" } }

        logger.i { "Signing bulk transfer: ${destinations.size} recipients, seqno=$seqno" }

        val transfers = destinations.map { dest ->
            val payload = if (dest.memo != null) {
                CellBuilder.createCell {
                    storeUInt(0, 32)
                    storeBytes(dest.memo.encodeToByteArray())
                }
            } else Cell.empty()

            WalletTransfer {
                destination = AddrStd.parse(dest.address)
                bounceable = dest.bounceable
                coins = Coins(dest.amountNano)
                messageData = MessageData.Raw(payload, null, null)
                sendMode = dest.sendMode
            }
        }

        val innerRequest = buildW5InnerRequestMulti(transfers)
        val body = buildW5SignedBody(seqno, innerRequest)
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        val stateInit = if (seqno == 0) buildW5StateInit(isTestnet) else null
        val message = buildW5Message(body, stateInit)
        val cell = buildCell { storeTlb(Message.Any, message) }
        return Base64.Default.encode(BagOfCells(cell).toByteArray())
            .also { logger.i { "Signed bulk transfer BOC length: ${it.length}" } }
    }

    suspend fun getSeqno(coinNetwork: CoinNetwork): Int {
        val s = TonApiService.INSTANCE.getSeqno(coinNetwork, getAddress())
            ?: throw WalletError.NetworkError("Failed to retrieve seqno for ${getAddress()}")
        logger.i { "Current seqno: $s" }
        return s
    }

    suspend fun signTransaction(
        toAddress: String,
        amountNano: Long,
        seqno: Int,
        memo: String? = null
    ): String {
        require(isValidTonAddress(toAddress)) { "Invalid TON address: $toAddress" }
        logger.i { "Signing TON transaction: $amountNano to $toAddress, seqno: $seqno, memo: $memo" }
        val payload = if (memo != null) {
            CellBuilder.createCell {
                storeUInt(0, 32) // text comment prefix
                storeBytes(memo.encodeToByteArray())
            }
        } else Cell.empty()

        val transfer = WalletTransfer {
            destination = AddrStd.parse(toAddress)
            bounceable = false // safe default for user wallets
            coins = Coins(amountNano)
            messageData = MessageData.Raw(payload, null, null)
        }

        return when (walletVersion) {
            WalletVersion.W4 -> signTransferV4(transfer, seqno)
            WalletVersion.W5 -> signTransferV5(transfer, seqno)
        }.also { logger.i { "Signed TON transaction BOC: $it" } }
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
        require(isValidTonAddress(toAddress)) { "Invalid recipient address: $toAddress" }
        require(isValidTonAddress(nftAddress)) { "Invalid NFT address: $nftAddress" }
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

        val transfer = WalletTransfer {
            destination = AddrStd.parse(nftAddress) // send TO the NFT contract
            bounceable = true
            coins = Coins(totalTonAmountNano)
            messageData = MessageData.Raw(nftBody, null, null)
        }

        return when (walletVersion) {
            WalletVersion.W4 -> signTransferV4(transfer, seqno)
            WalletVersion.W5 -> signTransferV5(transfer, seqno)
        }.also { logger.i { "Signed NFT transfer BOC: $it" } }
    }

    // ─── INFTManager Implementation ──────────────────────────────────────────

    override suspend fun getNFTs(address: String, coinNetwork: CoinNetwork): List<NFTItem>? {
        return try {
            TonApiService.INSTANCE.getNFTItems(coinNetwork, address)
                ?.map { nft ->
                    NFTItem(
                        coin = ACTCoin.TON,
                        address = nft.address,
                        collectionAddress = nft.collectionAddress,
                        index = nft.index?.toLongOrNull() ?: 0L,
                        name = nft.content?.name,
                        description = nft.content?.description,
                        imageUrl = nft.content?.image
                    )
                }
        } catch (e: Exception) {
            logger.e(e) { "Error getting NFTs" }
            null
        }
    }

    override suspend fun transferNFT(
        nftAddress: String,
        toAddress: String,
        memo: String?,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        return try {
            val seqno = getSeqno(coinNetwork)
            val boc = signNFTTransfer(nftAddress, toAddress, seqno, memo = memo)
            val result = TonApiService.INSTANCE.sendBoc(coinNetwork, boc)
            if (result == "success") {
                TransferResponseModel(success = true, error = null, txHash = "pending")
            } else {
                TransferResponseModel(success = false, error = "NFT transfer failed", txHash = null)
            }
        } catch (e: Exception) {
            logger.e(e) { "Error transferring NFT" }
            TransferResponseModel(success = false, error = e.message, txHash = null)
        }
    }

    /** Transaction expiry: current time + 60 seconds. Prevents stale signed messages. */
    private fun defaultValidUntil(): Int = (currentEpochSeconds() + 60).toInt()

    /**
     * Extended expiry for new wallet deployment (seqno == 0).
     * 5 minutes to allow time for stateInit to be included and processed.
     */
    private fun deployValidUntil(): Int = (currentEpochSeconds() + 300).toInt()

    // ─── Private signing helpers ──────────────────────────────────────────────

    private fun signTransferV4(transfer: WalletTransfer, seqno: Int): String {
        val walletId = WalletContract.DEFAULT_WALLET_ID
        val stateInit = if (seqno == 0) {
            WalletV4R2Contract.stateInit(WalletV4R2Contract.Data(0, publicKey)).load()
        } else null

        val message = WalletV4R2Contract.transferMessage(
            address = address,
            stateInit = stateInit,
            privateKey = privateKey,
            walletId = walletId,
            validUntil = if (seqno == 0) deployValidUntil() else defaultValidUntil(),
            seqno = seqno,
            transfer
        )
        val cell = buildCell { storeTlb(Message.Any, message) }
        return Base64.Default.encode(BagOfCells(cell).toByteArray())
    }

    private fun signTransferV5(transfer: WalletTransfer, seqno: Int): String {
        val isTestnet = Config.shared.getNetwork() == Network.TESTNET
        val innerRequest = buildW5InnerRequest(transfer)
        val body = buildW5SignedBody(seqno, innerRequest)
        val stateInit = if (seqno == 0) buildW5StateInit(isTestnet) else null
        val message = buildW5Message(body, stateInit)
        val cell = buildCell { storeTlb(Message.Any, message) }
        return Base64.Default.encode(BagOfCells(cell).toByteArray())
    }

    // ─── Remaining methods ────────────────────────────────────────────────────

    override suspend fun getTransactionHistory(address: String?, coinNetwork: CoinNetwork?): Any? {
        require(coinNetwork != null) { "CoinNetwork is required" }
        val addr = address ?: getAddress()
        logger.i { "Getting TON transaction history for $addr" }
        return TonApiService.INSTANCE.getTransactions(coinNetwork, addr)
    }

    /** Paginated transaction history. Pass lt/hash from the last TonTransaction.transactionId for next page. */
    suspend fun getTransactionHistory(
        address: String? = null,
        coinNetwork: CoinNetwork,
        limit: Int = 10,
        lt: String? = null,
        hash: String? = null
    ): List<TonTransaction>? {
        val addr = address ?: getAddress()
        logger.i { "Getting TON transaction history (paginated) for $addr, lt=$lt" }
        return TonApiService.INSTANCE.getTransactions(coinNetwork, addr, limit, lt, hash)
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

    /**
     * Estimate fee with full breakdown: source fees (in_fwd, storage, gas, fwd) + destination fees per recipient.
     */
    suspend fun estimateFeeDetailed(coinNetwork: CoinNetwork, address: String, bodyBoc: String): TonFeeBreakdown? {
        logger.i { "Estimating detailed fee for $address" }
        val result = TonApiService.INSTANCE.estimateFeeDetailed(coinNetwork, address, bodyBoc) ?: return null

        fun nanoToTon(nano: Long): Double = nano.toDouble() / 1_000_000_000.0
        fun TonSourceFees.toEntry() = TonFeeBreakdown.TonFeeBreakdownEntry(
            inFwdFee = nanoToTon(inFwdFee),
            storageFee = nanoToTon(storageFee),
            gasFee = nanoToTon(gasFee),
            fwdFee = nanoToTon(fwdFee),
            total = nanoToTon(total)
        )

        val src = result.sourceFees
        val destEntries = result.destinationFees.map { it.toEntry() }
        val totalSourceNano = src.total
        val totalDestNano = result.destinationFees.sumOf { it.total }

        return TonFeeBreakdown(
            inFwdFee = nanoToTon(src.inFwdFee),
            storageFee = nanoToTon(src.storageFee),
            gasFee = nanoToTon(src.gasFee),
            fwdFee = nanoToTon(src.fwdFee),
            totalSourceFee = nanoToTon(totalSourceNano),
            destinationFees = destEntries,
            totalFee = nanoToTon(totalSourceNano + totalDestNano)
        ).also { logger.i { "Detailed fee: source=${it.totalSourceFee}, dest=${destEntries.size} entries, total=${it.totalFee} TON" } }
    }

    /**
     * Broadcast and poll until the transaction is confirmed on-chain.
     * @param maxAttempts max polling attempts (default 12 × 5s = 60s)
     * @param pollIntervalMs delay between polls in milliseconds
     */
    suspend fun transferWithConfirmation(
        dataSigned: String,
        coinNetwork: CoinNetwork,
        maxAttempts: Int = 12,
        pollIntervalMs: Long = 5000L
    ): TransferResponseModel {
        logger.i { "Broadcasting with confirmation | BOC length: ${dataSigned.length}" }
        val msgHash = TonApiService.INSTANCE.sendBocReturnHash(coinNetwork, dataSigned)
        if (msgHash == null) {
            logger.e { "Broadcast failed — no message hash returned" }
            return TransferResponseModel(success = false, error = "Failed to broadcast transaction", txHash = null)
        }
        logger.i { "Broadcast OK, polling for confirmation | msgHash=$msgHash" }

        for (attempt in 1..maxAttempts) {
            kotlinx.coroutines.delay(pollIntervalMs)
            logger.d { "Confirmation poll attempt $attempt/$maxAttempts" }
            val txs = TonApiService.INSTANCE.getTransactions(coinNetwork, getAddress(), limit = 5)
            if (txs != null) {
                for (tx in txs) {
                    if (tx.in_msg?.hash == msgHash) {
                        logger.i { "Transaction confirmed! txHash=${tx.transactionId.hash}" }
                        return TransferResponseModel(success = true, error = null, txHash = tx.transactionId.hash)
                    }
                }
            }
        }

        logger.w { "Confirmation timeout after ${maxAttempts * pollIntervalMs / 1000}s — tx may still be processing" }
        return TransferResponseModel(success = true, error = "Confirmation timeout", txHash = "pending")
    }

    override suspend fun transfer(
        dataSigned: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        logger.i { "Broadcasting TON transaction | network: ${Config.shared.getNetwork()} | BOC length: ${dataSigned.length}" }
        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, dataSigned)
        logger.i { "Broadcast result: $result" }
        return if (result == "success") {
            logger.i { "TON transfer broadcast successful" }
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            logger.e { "TON transfer broadcast failed: $result" }
            TransferResponseModel(
                success = false,
                error = result ?: "Failed to broadcast transaction",
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
                            storeBitString(fullSlice.loadBitString(remainingBits))
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
                    rewards = amountInTon - tsTonBalance
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
        val stTonBalance = getBalanceToken(getAddress(), poolAddress, coinNetwork)
        if (stTonBalance == 0.0) return TonStakingBalance(poolAddress, 0.0)

        val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_full_data")
        if (res?.ok == true && res.result?.stack != null && res.result.stack.size >= 2) {
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
        val payload = CellBuilder.createCell {
            storeUInt(0x4e73746b, 32)
        }

        val transfer = WalletTransfer {
            destination = AddrStd.parse(poolAddress)
            bounceable = true
            coins = Coins(amountNano)
            messageData = MessageData.Raw(payload, null, null)
        }

        return when (walletVersion) {
            WalletVersion.W4 -> signTransferV4(transfer, seqno)
            WalletVersion.W5 -> signTransferV5(transfer, seqno)
        }.also { logger.i { "Signed Nominator Pool deposit BOC: $it" } }
    }

    suspend fun signTonstakersDeposit(
        masterAddress: String,
        amountNano: Long,
        seqno: Int
    ): String {
        logger.i { "Signing Tonstakers deposit: $masterAddress, amount: $amountNano" }
        return signTransaction(masterAddress, amountNano, seqno)
    }

    suspend fun signBemoDeposit(
        masterAddress: String,
        amountNano: Long,
        seqno: Int
    ): String {
        logger.i { "Signing Bemo deposit: $masterAddress, amount: $amountNano" }
        return signTransaction(masterAddress, amountNano, seqno)
    }

    /**
     * Signs a TEP-74 Jetton burn message.
     * Used for unstaking from liquid staking pools (Tonstakers, Bemo).
     * Opcode: 0x595f07bc
     */
    suspend fun signJettonBurn(
        jettonMasterAddress: String,
        jettonAmountNano: Long,
        seqno: Int,
        coinNetwork: CoinNetwork,
        totalTonAmountNano: Long = 50_000_000L
    ): String {
        logger.i { "Signing Jetton burn: $jettonAmountNano from master $jettonMasterAddress" }
        val myJettonWallet = getJettonWalletAddress(getAddress(), jettonMasterAddress, coinNetwork)
            ?: throw Exception("Could not find Jetton Wallet for burn")

        val burnBody = CellBuilder.createCell {
            storeUInt(0x595f07bc, 32)  // burn op-code (TEP-74)
            storeUInt(0, 64)           // query_id
            storeTlb(Coins, Coins(jettonAmountNano))
            storeTlb(MsgAddressInt, address) // response_destination = sender
            storeBoolean(false)              // custom_payload = null
        }

        val transfer = WalletTransfer {
            destination = AddrStd.parse(myJettonWallet)
            bounceable = true
            coins = Coins(totalTonAmountNano)
            messageData = MessageData.Raw(burnBody, null, null)
        }

        return when (walletVersion) {
            WalletVersion.W4 -> signTransferV4(transfer, seqno)
            WalletVersion.W5 -> signTransferV5(transfer, seqno)
        }.also { logger.i { "Signed Jetton burn BOC: $it" } }
    }

    // ─── Pool Type Detection ──────────────────────────────────────────────────

    /**
     * Detect the type of a TON staking pool by calling get methods on the pool contract.
     * Tries get_nominator_data → NOMINATOR, get_pool_full_data → TONSTAKERS,
     * get_full_data → BEMO, else → UNKNOWN.
     */
    suspend fun detectPoolType(poolAddress: String, coinNetwork: CoinNetwork): TonPoolType {
        logger.i { "Detecting pool type for: $poolAddress" }

        // Try get_nominator_data → NOMINATOR
        try {
            val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_nominator_data")
            if (res?.ok == true && res.result?.stack?.isNotEmpty() == true) {
                logger.i { "Pool type detected: NOMINATOR" }
                return TonPoolType.NOMINATOR
            }
        } catch (_: Exception) { /* not a nominator pool */ }

        // Try get_pool_full_data → TONSTAKERS
        try {
            val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_pool_full_data")
            if (res?.ok == true && res.result?.stack?.isNotEmpty() == true) {
                logger.i { "Pool type detected: TONSTAKERS" }
                return TonPoolType.TONSTAKERS
            }
        } catch (_: Exception) { /* not a tonstakers pool */ }

        // Try get_full_data → BEMO
        try {
            val res = TonApiService.INSTANCE.runGetMethod(coinNetwork, poolAddress, "get_full_data")
            if (res?.ok == true && res.result?.stack?.isNotEmpty() == true) {
                logger.i { "Pool type detected: BEMO" }
                return TonPoolType.BEMO
            }
        } catch (_: Exception) { /* not a bemo pool */ }

        logger.w { "Pool type unknown for: $poolAddress" }
        return TonPoolType.UNKNOWN
    }

    // ─── IStakingManager Implementation ───────────────────────────────────────

    override suspend fun stake(
        amount: Long,
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        logger.i { "IStakingManager.stake: amount=$amount, pool=$poolAddress" }
        val poolType = detectPoolType(poolAddress, coinNetwork)
        val seqno = getSeqno(coinNetwork)

        val boc = when (poolType) {
            TonPoolType.NOMINATOR -> signDepositToNominatorPool(poolAddress, amount, seqno)
            TonPoolType.TONSTAKERS -> signTonstakersDeposit(poolAddress, amount, seqno)
            TonPoolType.BEMO -> signBemoDeposit(poolAddress, amount, seqno)
            TonPoolType.UNKNOWN -> throw WalletError.UnsupportedOperation(
                "stake", "TON (unknown pool type for $poolAddress)"
            )
        }

        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, boc)
        return if (result == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "Failed to broadcast staking transaction", txHash = null)
        }
    }

    override suspend fun unstake(amount: Long, coinNetwork: CoinNetwork): TransferResponseModel {
        throw WalletError.UnsupportedOperation(
            "unstake",
            "TON — use unstake(amount, poolAddress, coinNetwork) instead"
        )
    }

    /**
     * Unstake from a liquid staking pool by burning staking tokens.
     * Only supported for TONSTAKERS and BEMO pools.
     * NOMINATOR pools handle withdrawals automatically via the pool contract.
     */
    override suspend fun unstake(
        amount: Long,
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): TransferResponseModel {
        val poolType = detectPoolType(poolAddress, coinNetwork)
        val seqno = getSeqno(coinNetwork)

        val boc = when (poolType) {
            TonPoolType.TONSTAKERS, TonPoolType.BEMO ->
                signJettonBurn(poolAddress, amount, seqno, coinNetwork)
            TonPoolType.NOMINATOR -> throw WalletError.UnsupportedOperation(
                "unstake", "TON Nominator pools (withdrawals are automatic)"
            )
            TonPoolType.UNKNOWN -> throw WalletError.UnsupportedOperation(
                "unstake", "TON (unknown pool type for $poolAddress)"
            )
        }

        val result = TonApiService.INSTANCE.sendBoc(coinNetwork, boc)
        return if (result == "success") {
            TransferResponseModel(success = true, error = null, txHash = "pending")
        } else {
            TransferResponseModel(success = false, error = "Failed to broadcast unstaking transaction", txHash = null)
        }
    }

    override suspend fun getStakingRewards(address: String, coinNetwork: CoinNetwork): Double {
        logger.i { "IStakingManager.getStakingRewards: address=$address" }
        // For TON, rewards are embedded in the staking balance query.
        // We need a pool address to query, but the interface only provides address.
        // Return 0.0 as rewards are returned via getStakingBalance.
        return 0.0
    }

    override suspend fun getStakingBalance(
        address: String,
        poolAddress: String,
        coinNetwork: CoinNetwork
    ): Double {
        logger.i { "IStakingManager.getStakingBalance: address=$address, pool=$poolAddress" }
        val poolType = detectPoolType(poolAddress, coinNetwork)

        val balance = when (poolType) {
            TonPoolType.NOMINATOR -> getNominatorStakingBalance(poolAddress, coinNetwork)
            TonPoolType.TONSTAKERS -> getTonstakersStakingBalance(poolAddress, coinNetwork)
            TonPoolType.BEMO -> getBemoStakingBalance(poolAddress, coinNetwork)
            TonPoolType.UNKNOWN -> null
        }

        return balance?.amount ?: 0.0
    }
}
