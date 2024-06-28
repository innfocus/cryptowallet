package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName

class CoinNetwork(
    var name: NetworkName,
    var apiKeyExplorer: String? = null,
    private var apiKeyInfura: String? = null,
    var apiKeyOwlRacle: String? = null
) {

    fun getInfuraRpcUrl(): String {
        require(apiKeyInfura != null) { "API key Infura is null" }
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-mainnet.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://mainnet.infura.io/v3/${apiKeyInfura}"
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-sepolia.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://sepolia.infura.io/v3/${apiKeyInfura}"
                }
            }
        }
    }

    fun getExplorerEndpoint(): String {
        require(apiKeyExplorer != null) { "API KEY Explorer is null"}
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://api.arbiscan.io/api"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://api.etherscan.io/api"
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://api-sepolia.arbiscan.io/api"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://api-sepolia.etherscan.io/api"
                }
            }
        }
    }

    fun getOwlRacleUrl(): String {
        return when(name){
            NetworkName.ARBITRUM -> "https://api.owlracle.info/v4/arb/gas"
            NetworkName.BTC -> return ""
            NetworkName.ETHEREUM -> "https://api.owlracle.info/v4/eth/gas"
        }
    }


}