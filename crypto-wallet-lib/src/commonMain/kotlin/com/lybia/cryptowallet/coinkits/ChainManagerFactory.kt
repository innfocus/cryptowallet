package com.lybia.cryptowallet.coinkits

import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.base.IBridgeManager
import com.lybia.cryptowallet.base.IFeeEstimator
import com.lybia.cryptowallet.base.INFTManager
import com.lybia.cryptowallet.base.IStakingManager
import com.lybia.cryptowallet.base.ITokenManager
import com.lybia.cryptowallet.base.IWalletManager
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.bridge.BridgeManagerFactory
import com.lybia.cryptowallet.services.CardanoApiService
import com.lybia.cryptowallet.services.MidnightApiService
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.cardano.CardanoManager
import com.lybia.cryptowallet.wallets.ethereum.EthereumManager
import com.lybia.cryptowallet.wallets.midnight.MidnightManager
import com.lybia.cryptowallet.wallets.ripple.RippleManager
import com.lybia.cryptowallet.wallets.ton.TonManager
import com.lybia.cryptowallet.wallets.centrality.CentralityManager
import com.lybia.cryptowallet.services.CentralityApiService

/**
 * Configuration for a chain manager.
 */
data class ChainConfig(
    val apiBaseUrl: String,
    val apiKey: String? = null,
    val isTestnet: Boolean = false
) {
    companion object {
        fun default(coin: NetworkName): ChainConfig {
            val coinNetwork = CoinNetwork(coin)
            return when (coin) {
                NetworkName.CARDANO -> ChainConfig(coinNetwork.getBlockfrostUrl())
                NetworkName.MIDNIGHT -> ChainConfig(coinNetwork.getMidnightApiUrl())
                else -> ChainConfig("")
            }
        }
    }
}

/**
 * Factory for creating chain managers.
 *
 * Open/Closed: adding a new chain only requires adding a case here.
 */
object ChainManagerFactory {

    fun createWalletManager(
        coin: NetworkName,
        mnemonic: String,
        config: ChainConfig = ChainConfig.default(coin)
    ): IWalletManager {
        return when (coin) {
            NetworkName.BTC -> BitcoinManager(mnemonic).also { it.getNativeSegWitAddress() }
            NetworkName.ETHEREUM, NetworkName.ARBITRUM -> EthereumManager()
            NetworkName.CARDANO -> CardanoManager(
                mnemonic = mnemonic,
                apiService = CardanoApiService(baseUrl = config.apiBaseUrl)
            )
            NetworkName.TON -> TonManager(mnemonic)
            NetworkName.MIDNIGHT -> MidnightManager(
                mnemonic = mnemonic,
                apiService = MidnightApiService(baseUrl = config.apiBaseUrl)
            )
            NetworkName.XRP -> RippleManager(mnemonic)
            NetworkName.CENTRALITY -> CentralityManager(
                mnemonic = mnemonic,
                apiService = CentralityApiService()
            )
        }
    }

    /** Type-safe accessor for token-capable managers. */
    fun createTokenManager(coin: NetworkName, mnemonic: String): ITokenManager? {
        val manager = createWalletManager(coin, mnemonic)
        return manager as? ITokenManager
    }

    /** Type-safe accessor for NFT-capable managers. */
    fun createNFTManager(coin: NetworkName, mnemonic: String): INFTManager? {
        val manager = createWalletManager(coin, mnemonic)
        return manager as? INFTManager
    }

    /** Type-safe accessor for fee-estimation-capable managers. */
    fun createFeeEstimator(coin: NetworkName, mnemonic: String): IFeeEstimator? {
        val manager = createWalletManager(coin, mnemonic)
        return manager as? IFeeEstimator
    }

    /** Type-safe accessor for staking-capable managers (CARDANO, TON). */
    fun createStakingManager(
        coin: NetworkName,
        mnemonic: String,
        config: ChainConfig = ChainConfig.default(coin)
    ): IStakingManager? {
        val manager = createWalletManager(coin, mnemonic, config)
        return manager as? IStakingManager
    }

    /** Delegates to [BridgeManagerFactory] for bridge-capable chain pairs. */
    fun createBridgeManager(
        fromChain: NetworkName,
        toChain: NetworkName,
        mnemonic: String,
        configs: Map<NetworkName, ChainConfig> = emptyMap()
    ): IBridgeManager? {
        return BridgeManagerFactory.createBridgeManager(fromChain, toChain, mnemonic, configs)
    }
}
