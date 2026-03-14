package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName

class CoinNetwork(
    var name: NetworkName,
    var apiKeyExplorer: String? = null,
    var apiKeyInfura: String? = null,
    var apiKeyOwlRacle: String? = null
) {

    fun getApiKey(): String? = apiKeyInfura

    fun getInfuraRpcUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-mainnet.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://mainnet.infura.io/v3/${apiKeyInfura}"
                    NetworkName.TON -> "https://toncenter.com/api/v2/jsonRPC"
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-sepolia.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://sepolia.infura.io/v3/${apiKeyInfura}"
                    NetworkName.TON -> "https://testnet.toncenter.com/api/v2/jsonRPC"
                }
            }
        }
    }

    fun getExplorerEndpoint(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://api.arbiscan.io/api"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://api.etherscan.io/api"
                    NetworkName.TON -> "https://tonscan.org"
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://api-sepolia.arbiscan.io/api"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://api-sepolia.etherscan.io/api"
                    NetworkName.TON -> "https://testnet.tonscan.org"
                }
            }
        }
    }

    fun getToncenterV3Url(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://toncenter.com/api/v3"
            Network.TESTNET -> "https://testnet.toncenter.com/api/v3"
        }
    }

    fun getOwlRacleUrl(): String {
        return when(name){
            NetworkName.ARBITRUM -> "https://api.owlracle.info/v4/arb/gas"
            NetworkName.BTC -> return ""
            NetworkName.ETHEREUM -> "https://api.owlracle.info/v4/eth/gas"
            NetworkName.TON -> ""
        }
    }


}
