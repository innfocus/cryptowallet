package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.models.NFTItem
import com.lybia.cryptowallet.models.TokenInfo
import com.lybia.cryptowallet.enums.ACTCoin
import com.lybia.cryptowallet.models.toTransactionDatas
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.models.ton.TonTransaction
import com.lybia.cryptowallet.wallets.ton.TonManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * TON-specific implementation of [TokenService] and [NFTService].
 *
 * Wraps [TonManager] (KMP / suspend-based) and exposes callback methods.
 * Coroutines run on the provided [scope] so lifecycle and cancellation
 * are managed centrally.
 *
 * @param mnemonicProvider lambda — always returns the current mnemonic so that
 *   a single TonService instance stays valid after mnemonic changes.
 * @param scope coroutine scope from the parent CoinsManager singleton.
 */
class TonService(
    private val mnemonicProvider: () -> String,
    private val scope: CoroutineScope
) : TokenService, NFTService {

    private val coinNetwork = CoinNetwork(name = NetworkName.TON)

    private fun manager() = TonManager(mnemonicProvider())

    // ─── TokenService ─────────────────────────────────────────────────────────

    override fun getTokenBalance(
        address: String,
        contractAddress: String,
        completionHandler: TokenBalanceHandle
    ) {
        scope.launch {
            try {
                val mgr = manager()
                val balance = mgr.getBalanceToken(address, contractAddress, coinNetwork)
                val metadata = mgr.getJettonMetadata(contractAddress, coinNetwork)
                val info = TokenInfo(
                    coin = ACTCoin.TON,
                    contractAddress = contractAddress,
                    name = metadata?.name,
                    symbol = metadata?.symbol,
                    decimals = metadata?.decimals ?: 9,
                    balance = balance,
                    imageUrl = metadata?.image
                )
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(info, true, "")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, false, e.message ?: "Error")
                }
            }
        }
    }

    override fun getTokenTransactions(
        address: String,
        contractAddress: String,
        completionHandler: TokenTransactionsHandle
    ) {
        scope.launch {
            try {
                val history = manager().getTransactionHistoryToken(address, contractAddress, coinNetwork)
                @Suppress("UNCHECKED_CAST")
                val txList = (history as? List<TonTransaction>) ?: emptyList()
                val mapped = txList.toTransactionDatas(address)
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(mapped, "")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, e.message ?: "Error")
                }
            }
        }
    }

    override fun sendToken(
        toAddress: String,
        contractAddress: String,
        amount: Double,
        decimals: Int,
        memo: String?,
        completionHandler: SendTokenHandle
    ) {
        scope.launch {
            try {
                val mgr = manager()
                val seqno = mgr.getSeqno(coinNetwork)
                val amountNano = (amount * 10.0.pow(decimals)).toLong()
                val boc = mgr.signJettonTransaction(
                    jettonMasterAddress = contractAddress,
                    toAddress = toAddress,
                    jettonAmountNano = amountNano,
                    seqno = seqno,
                    coinNetwork = coinNetwork,
                    memo = memo?.takeIf { it.isNotEmpty() }
                )
                val txHash = mgr.TransferToken(boc, coinNetwork)
                withContext(Dispatchers.Main) {
                    if (txHash != null) {
                        completionHandler.completionHandler(txHash, true, "")
                    } else {
                        completionHandler.completionHandler("", false, "Broadcast failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler("", false, e.message ?: "Error")
                }
            }
        }
    }

    // ─── NFTService ───────────────────────────────────────────────────────────

    override fun getNFTs(address: String, completionHandler: NFTListHandle) {
        scope.launch {
            try {
                val items = TonApiService.INSTANCE.getNFTItems(coinNetwork, address)
                val nfts = items?.map { item ->
                    NFTItem(
                        coin = ACTCoin.TON,
                        address = item.address,
                        collectionAddress = item.collectionAddress,
                        index = item.index?.toLongOrNull() ?: 0L,
                        name = item.content?.name,
                        description = item.content?.description,
                        imageUrl = item.content?.image,
                        attributes = item.content?.attributes
                            ?.associate { it.traitType to it.value }
                    )
                }?.toTypedArray() ?: emptyArray()
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(nfts, "")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler(null, e.message ?: "Error")
                }
            }
        }
    }

    override fun transferNFT(
        nftAddress: String,
        toAddress: String,
        memo: String?,
        completionHandler: NFTTransferHandle
    ) {
        scope.launch {
            try {
                val mgr = manager()
                val seqno = mgr.getSeqno(coinNetwork)
                val boc = mgr.signNFTTransfer(
                    nftAddress = nftAddress,
                    toAddress = toAddress,
                    seqno = seqno,
                    memo = memo?.takeIf { it.isNotEmpty() }
                )
                val result = mgr.transfer(boc, coinNetwork)
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        completionHandler.completionHandler(result.txHash ?: "pending", true, "")
                    } else {
                        completionHandler.completionHandler("", false, result.error ?: "Failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    completionHandler.completionHandler("", false, e.message ?: "Error")
                }
            }
        }
    }
}
