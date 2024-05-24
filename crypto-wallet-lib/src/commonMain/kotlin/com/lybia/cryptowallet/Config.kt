package com.lybia.cryptowallet

import com.lybia.cryptowallet.enums.Network

class Config {
    private var network = Network.MAINNET
    companion object {
        val shared: Config = Config()
    }

    fun setNetwork(network: Network){
        this.network = network
    }

    fun getNetwork(): Network {
        return network
    }
}