package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName

class CoinNetwork(
    var name: NetworkName,
    private var infuraApiUrl: String,
    private var infuraTestNetUrl: String,
    private var explorerUrl: String,
    private var explorerTestNetUrl: String,
    var owlRacleUrl: String,
    var apiKeyExplorer: String,
    private var apiKeyInfura: String,
    var apiKeyOwlRacle: String
) {
    fun getInfuraRpcUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "$infuraApiUrl$apiKeyInfura"
            Network.TESTNET -> "$infuraTestNetUrl$apiKeyInfura"
        }
    }

    fun getExplorerEndpoint(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> explorerUrl
            Network.TESTNET -> explorerTestNetUrl
        }
    }


}