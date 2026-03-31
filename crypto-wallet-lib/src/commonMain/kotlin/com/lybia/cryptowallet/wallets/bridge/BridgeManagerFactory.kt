package com.lybia.cryptowallet.wallets.bridge

import com.lybia.cryptowallet.base.IBridgeManager
import com.lybia.cryptowallet.coinkits.ChainConfig
import com.lybia.cryptowallet.enums.NetworkName

/**
 * Factory for creating bridge manager implementations based on chain pairs.
 *
 * Supported pairs:
 * - Cardano ↔ Midnight (CardanoMidnightBridge)
 * - Ethereum ↔ Arbitrum (EthereumArbitrumBridge)
 */
object BridgeManagerFactory {

    private val supportedPairs: Set<Pair<NetworkName, NetworkName>> = setOf(
        NetworkName.CARDANO to NetworkName.MIDNIGHT,
        NetworkName.MIDNIGHT to NetworkName.CARDANO,
        NetworkName.ETHEREUM to NetworkName.ARBITRUM,
        NetworkName.ARBITRUM to NetworkName.ETHEREUM
    )

    /**
     * Returns true if bridging between [fromChain] and [toChain] is supported.
     */
    fun supportsBridge(fromChain: NetworkName, toChain: NetworkName): Boolean =
        (fromChain to toChain) in supportedPairs

    /**
     * Creates the appropriate [IBridgeManager] for the given chain pair,
     * or null if the pair is not supported.
     */
    fun createBridgeManager(
        fromChain: NetworkName,
        toChain: NetworkName,
        mnemonic: String,
        configs: Map<NetworkName, ChainConfig> = emptyMap()
    ): IBridgeManager? {
        val pair = fromChain to toChain
        return when {
            pair in setOf(
                NetworkName.CARDANO to NetworkName.MIDNIGHT,
                NetworkName.MIDNIGHT to NetworkName.CARDANO
            ) -> CardanoMidnightBridge(mnemonic, configs)
            pair in setOf(
                NetworkName.ETHEREUM to NetworkName.ARBITRUM,
                NetworkName.ARBITRUM to NetworkName.ETHEREUM
            ) -> EthereumArbitrumBridge(mnemonic, configs)
            else -> null
        }
    }
}
