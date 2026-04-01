package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName

class CoinNetwork(
    var name: NetworkName
) {

    fun getInfuraRpcUrl(): String {
        val apiKeyInfura = Config.shared.apiKeyInfura
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-mainnet.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://mainnet.infura.io/v3/${apiKeyInfura}"
                    NetworkName.TON -> "https://toncenter.com/api/v2/jsonRPC"
                    NetworkName.CARDANO -> ""
                    NetworkName.MIDNIGHT -> ""
                    NetworkName.XRP -> ""
                    NetworkName.CENTRALITY -> ""
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://arbitrum-sepolia.infura.io/v3/${apiKeyInfura}"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://sepolia.infura.io/v3/${apiKeyInfura}"
                    NetworkName.TON -> "https://testnet.toncenter.com/api/v2/jsonRPC"
                    NetworkName.CARDANO -> ""
                    NetworkName.MIDNIGHT -> ""
                    NetworkName.XRP -> ""
                    NetworkName.CENTRALITY -> ""
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
                    NetworkName.CARDANO -> "https://cardanoscan.io"
                    NetworkName.MIDNIGHT -> "https://explorer.midnight.network"
                    NetworkName.XRP -> ""
                    NetworkName.CENTRALITY -> ""
                }
            }
            Network.TESTNET -> {
                when(name){
                    NetworkName.ARBITRUM -> "https://api-sepolia.arbiscan.io/api"
                    NetworkName.BTC -> ""
                    NetworkName.ETHEREUM -> "https://api-sepolia.etherscan.io/api"
                    NetworkName.TON -> "https://testnet.tonscan.org"
                    NetworkName.CARDANO -> "https://preprod.cardanoscan.io"
                    NetworkName.MIDNIGHT -> "https://explorer.testnet.midnight.network"
                    NetworkName.XRP -> ""
                    NetworkName.CENTRALITY -> ""
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
            NetworkName.BTC -> ""
            NetworkName.ETHEREUM -> "https://api.owlracle.info/v4/eth/gas"
            NetworkName.TON -> ""
            NetworkName.CARDANO -> ""
            NetworkName.MIDNIGHT -> ""
            NetworkName.XRP -> ""
            NetworkName.CENTRALITY -> ""
        }
    }

    fun getBlockfrostUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://cardano-mainnet.blockfrost.io/api/v0"
            Network.TESTNET -> "https://cardano-preprod.blockfrost.io/api/v0"
        }
    }

    /**
     * Koios API URL — free, no API key required.
     * Koios is a decentralized Cardano API with the same data as Blockfrost.
     */
    fun getKoiosUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://api.koios.rest/api/v1"
            Network.TESTNET -> "https://preprod.koios.rest/api/v1"
        }
    }

    fun getMidnightApiUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://midnight.api.midnight.network/api/v0"
            Network.TESTNET -> "https://midnight-testnet.api.midnight.network/api/v0"
        }
    }

    fun getRippleRpcUrl(): String {
        return when (Config.shared.getNetwork()) {
            Network.MAINNET -> "https://s1.ripple.com:51234/"
            Network.TESTNET -> "https://s.altnet.rippletest.net:51234/"
        }
    }
}
