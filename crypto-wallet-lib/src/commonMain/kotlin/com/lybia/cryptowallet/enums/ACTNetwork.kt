package com.lybia.cryptowallet.enums

class ACTNetwork(val coin: ACTCoin, val isTestNet: Boolean) {

    fun coinType(): Int = when (coin) {
        ACTCoin.Bitcoin -> if (isTestNet) 1 else 0
        ACTCoin.Ethereum -> 60
        ACTCoin.Cardano -> 1815
        ACTCoin.Ripple -> 144
        ACTCoin.Centrality -> 392
        ACTCoin.XCoin -> 868
        ACTCoin.TON -> 607
        ACTCoin.Midnight -> 1815
    }

    fun privateKeyPrefix(): Int = when {
        isTestNet -> 0x0488ADE4.toInt()
        else -> 0x0488ADE4.toInt()
    }

    fun publicKeyPrefix(): Int = when {
        isTestNet -> 0x043587cf
        else -> 0x0488b21e
    }

    fun pubkeyhash(): Byte = when {
        !isTestNet -> 0x00
        else -> when (coin) {
            ACTCoin.Bitcoin -> 0x6f
            else -> 0x00
        }
    }

    fun addressPrefix(): String =
        if (coin == ACTCoin.Ethereum) "0x" else ""

    fun derivationPath(): String = when {
        coin == ACTCoin.Bitcoin && isTestNet -> "${coinType()}'"
        else -> "44'/${coinType()}'/0'"
    }

    fun derivateIdxMax(chain: Change): Int = when (coin) {
        ACTCoin.Bitcoin -> if (chain == Change.Internal) 10 else 100
        ACTCoin.Ethereum -> if (chain == Change.Internal) 0 else 1
        ACTCoin.Cardano -> if (chain == Change.Internal) 0 else 50
        ACTCoin.Ripple -> if (chain == Change.Internal) 0 else 1
        ACTCoin.Centrality -> if (chain == Change.Internal) 0 else 1
        ACTCoin.XCoin -> if (chain == Change.Internal) 0 else 1
        ACTCoin.TON -> if (chain == Change.Internal) 0 else 1
        ACTCoin.Midnight -> if (chain == Change.Internal) 0 else 1
    }

    fun extendAddresses(chain: Change): Int = when (coin) {
        ACTCoin.Bitcoin -> if (chain == Change.Internal) 0 else 10
        else -> 0
    }

    fun explorer(): String = when (isTestNet) {
        false -> when (coin) {
            ACTCoin.Bitcoin -> "https://www.blockchain.com/btc"
            ACTCoin.Ethereum -> "https://etherscan.io"
            ACTCoin.Cardano -> "https://cardanoexplorer.com"
            ACTCoin.Ripple -> "https://bithomp.com"
            ACTCoin.Centrality -> "https://uncoverexplorer.com"
            ACTCoin.XCoin -> "Explorer XCoin"
            ACTCoin.TON -> "https://tonscan.org"
            ACTCoin.Midnight -> "https://explorer.midnight.network"
        }
        true -> when (coin) {
            ACTCoin.Bitcoin -> "https://testnet.blockchain.info"
            ACTCoin.Ethereum -> "https://goerli.etherscan.io"
            ACTCoin.Cardano -> "https://cardanoexplorer.com"
            ACTCoin.Ripple -> "https://test.bithomp.com"
            ACTCoin.Centrality -> "https://uncoverexplorer.com"
            ACTCoin.XCoin -> "Explorer XCoin"
            ACTCoin.TON -> "https://testnet.tonscan.org"
            ACTCoin.Midnight -> "https://explorer.testnet.midnight.network"
        }
    }

    fun explorerForTX(): String = when (coin) {
        ACTCoin.Centrality -> "https://uncoverexplorer.com/extrinsic/"
        else -> explorer() + if (coin == ACTCoin.Ripple) "/explorer/" else "/tx/"
    }
}
