package com.lybia.cryptowallet.utils

object Urls {

    fun getBitcoinApiBalance(network: String, address: String) :String{
        return "https://api.blockcypher.com/v1/btc/${network}/addrs/${address}/balance"
    }

    fun getBitcoinApiTransaction(network: String, address: String): String{
        return "https://api.blockcypher.com/v1/btc/${network}/addrs/${address}/full"
    }

    fun getBitcoinApiCreateNewTransaction(network: String): String{
        return "https://api.blockcypher.com/v1/btc/${network}/txs/new"
    }

}