package com.lybia.cryptowallet.coinkits.hdwallet.bip32

import java.math.BigDecimal


enum class Change(val value: Int) {
    External(0),
    Internal(1)
}

enum class Algorithm {
    Ed25519,
    Secp256k1,
    Sr25519
}

enum class ACTCoin(val assetId: Int = 0) {
    Bitcoin{
        override fun feeDefault()       = 0.0
        override fun minimumAmount()    = 0.0
        override fun supportMemo()      = false
        override fun nameCoin()         = "Bitcoin"
        override fun symbolName()       = "BTC"
        override fun minimumValue()     = 0.00001
        override fun unit()             = BigDecimal(100000000)
        override fun regex()            = "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))"
        override fun algorithm()        = Algorithm.Secp256k1
        override fun baseApiUrl()       : String{
            return  "https://blockchain.info"
        }
        override fun allowNewAddress()  = true
    },
    Ethereum{
        override fun feeDefault()       = 0.0
        override fun minimumAmount()    = 0.0
        override fun supportMemo()      = false
        override fun nameCoin()         = "Ethereum"
        override fun symbolName()       = "ETH"
        override fun minimumValue()     = 0.0001
        override fun unit()             = BigDecimal(1000000000000000000)
        override fun regex()            = "(?:((0x|0X|)[a-fA-F0-9]{40,}))"
        override fun algorithm()        = Algorithm.Secp256k1
        override fun baseApiUrl()       = ""
        override fun allowNewAddress()  = false
    },
    Cardano{
        override fun feeDefault()       = 0.0
        override fun minimumAmount()    = 0.0
        override fun supportMemo()      = false
        override fun nameCoin()         = "Cardano"
        override fun symbolName()       = "ADA"
        override fun minimumValue()     = 1.0
        override fun unit()             = BigDecimal(1000000)
        override fun regex()            = "(?:([a-km-zA-HJ-NP-Z1-9]{25,}))"
        override fun algorithm()        = Algorithm.Ed25519
        override fun baseApiUrl()       = ""
        override fun allowNewAddress()  = true
    },
    XCoin {
        override fun feeDefault()       = 0.0
        override fun minimumAmount()    = 0.0
        override fun supportMemo()      = false
        override fun nameCoin()         = "X-Coin"
        override fun symbolName()       = "XCOIN"
        override fun minimumValue()     = 0.0001
        override fun unit()             = BigDecimal(1000000000000000000)
        override fun regex()            = "(?:((0x|0X|)[a-fA-F0-9]{40,}))"
        override fun algorithm()        = Algorithm.Secp256k1
        override fun baseApiUrl()       = ""
        override fun allowNewAddress()  = false
    },
    Ripple{
        override fun feeDefault()       = 0.000012
        override fun minimumAmount()    = 10.0
        override fun supportMemo()      = true
        override fun nameCoin()         = "Ripple"
        override fun symbolName()       = "XRP"
        override fun minimumValue()     = 0.00001
        override fun unit()             = BigDecimal(1000000)
        override fun regex()            = "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))"
        override fun algorithm()        = Algorithm.Secp256k1
        override fun baseApiUrl()       = ""
        override fun allowNewAddress()  = false
    },
    Centrality {
        override fun feeDefault() = 15287.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin(): String {
            return when (assetId) {
                1 -> "CENNZnet"
                2 -> "CPAY"
                else -> "Centrality"
            }
        }

        override fun symbolName(): String {
            return when (assetId) {
                1 -> "CENNZ"
                2 -> "CPAY"
                else -> "CENNZ"
            }
        }

        override fun minimumValue() = 0.01
        override fun unit() = BigDecimal(10000)
        override fun regex() = "(?:(5|[a-km-zA-HJ-NP-Z1-9]{47,}))"
        override fun algorithm() = Algorithm.Sr25519
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    };
    abstract fun nameCoin()         : String
    abstract fun symbolName()       : String
    abstract fun minimumValue()     : Double
    abstract fun regex()            : String
    abstract fun algorithm()        : Algorithm
    abstract fun baseApiUrl()       : String
    abstract fun unit()             : BigDecimal
    abstract fun feeDefault()       : Double
    abstract fun minimumAmount()    : Double
    abstract fun supportMemo()      : Boolean
    abstract fun allowNewAddress()  : Boolean
    fun assetId(): Int {
        return assetId
    }
}

class ACTNetwork constructor(val coin: ACTCoin, val isTestNet: Boolean) {

    fun coinType(): Int {
        return when(coin) {
            ACTCoin.Bitcoin     -> if (isTestNet) 1 else 0
            ACTCoin.Ethereum    -> 60
            ACTCoin.Cardano     -> 1815
            ACTCoin.Ripple      -> 144
            ACTCoin.Centrality -> 392
            ACTCoin.XCoin       -> 868
        }
    }

    fun privateKeyPrefix(): Int {
        return when{
            isTestNet   -> 0x0488ADE4
            else        -> 0x0488ADE4
        }
    }

    fun publicKeyPrefix(): Int {
        return when{
            isTestNet   -> 0x043587cf
            else        -> 0x0488b21e
        }
    }

    fun pubkeyhash(): Byte {
        return when{
            !isTestNet          -> 0x00
            else -> when(coin) {
                ACTCoin.Bitcoin -> 0x6f
                else            -> 0x00
            }
        }
    }

    fun addressPrefix() : String {
        return if (coin == ACTCoin.Ethereum) "0x" else ""
    }

    fun derivationPath(): String {

        when {
            coin == ACTCoin.Bitcoin && isTestNet    -> return (coinType().toString() + "'")
            else                                    -> return ("44'/" + coinType().toString() + "'/0'")
        }
    }

    fun derivateIdxMax(chain: Change): Int {
        return when(coin) {
            ACTCoin.Bitcoin     -> if (chain == Change.Internal) 10 else 100
            ACTCoin.Ethereum    -> if (chain == Change.Internal) 0  else 1
            ACTCoin.Cardano     -> if (chain == Change.Internal) 0  else 50
            ACTCoin.Ripple      -> if (chain == Change.Internal) 0  else 1
            ACTCoin.Centrality  -> if (chain == Change.Internal) 0  else 1
            ACTCoin.XCoin       -> if (chain == Change.Internal) 0  else 1
        }
    }

    fun extendAddresses(chain: Change): Int {
        return when(coin) {
            ACTCoin.Bitcoin     -> if (chain == Change.Internal) 0 else 10
            ACTCoin.Ethereum    -> if (chain == Change.Internal) 0 else 0
            ACTCoin.Cardano     -> if (chain == Change.Internal) 0 else 0
            ACTCoin.Ripple      -> if (chain == Change.Internal) 0 else 0
            ACTCoin.Centrality -> if (chain == Change.Internal) 0 else 0
            ACTCoin.XCoin       -> if (chain == Change.Internal) 0 else 0
        }
    }

    fun explorer(): String {
        return when(isTestNet) {
            false -> {
                when(coin) {
                    ACTCoin.Bitcoin     -> "https://www.blockchain.com/btc"
                    ACTCoin.Ethereum    -> "https://etherscan.io"
                    ACTCoin.Cardano     -> "https://cardanoexplorer.com"
                    ACTCoin.Ripple      -> "https://bithomp.com"
                    ACTCoin.Centrality -> "https://uncoverexplorer.com"
                    ACTCoin.XCoin       -> "Explorer XCoin"
                }
            }
            true -> {
                when(coin) {
                    ACTCoin.Bitcoin     -> "https://testnet.blockchain.info"
                    ACTCoin.Ethereum    -> "https://goerli.etherscan.io"
                    ACTCoin.Cardano     -> "https://cardanoexplorer.com"
                    ACTCoin.Ripple      -> "https://test.bithomp.com"
                    ACTCoin.Centrality -> "https://uncoverexplorer.com"
                    ACTCoin.XCoin       -> "Explorer XCoin"
                }
            }
        }

    }

    fun explorerForTX(): String {
        return when (coin) {
            ACTCoin.Centrality -> "https://uncoverexplorer.com/extrinsic/"
            else -> explorer() + if (coin == ACTCoin.Ripple) "/explorer/" else "/tx/"
        }
    }
}