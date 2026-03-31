package com.lybia.cryptowallet.enums

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
    Bitcoin {
        override fun feeDefault() = 0.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin() = "Bitcoin"
        override fun symbolName() = "BTC"
        override fun minimumValue() = 0.00001
        override fun unitValue() = 100_000_000.0
        override fun regex() = "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))"
        override fun algorithm() = Algorithm.Secp256k1
        override fun baseApiUrl(): String = "https://blockchain.info"
        override fun allowNewAddress() = true
    },
    Ethereum {
        override fun feeDefault() = 0.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin() = "Ethereum"
        override fun symbolName() = "ETH"
        override fun minimumValue() = 0.0001
        override fun unitValue() = 1_000_000_000_000_000_000.0
        override fun regex() = "(?:((0x|0X|)[a-fA-F0-9]{40,}))"
        override fun algorithm() = Algorithm.Secp256k1
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    },
    Cardano {
        override fun feeDefault() = 0.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin() = "Cardano"
        override fun symbolName() = "ADA"
        override fun minimumValue() = 1.0
        override fun unitValue() = 1_000_000.0
        override fun regex() = "(?:([a-km-zA-HJ-NP-Z1-9]{25,}))"
        override fun algorithm() = Algorithm.Ed25519
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = true
    },
    XCoin {
        override fun feeDefault() = 0.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin() = "X-Coin"
        override fun symbolName() = "XCOIN"
        override fun minimumValue() = 0.0001
        override fun unitValue() = 1_000_000_000_000_000_000.0
        override fun regex() = "(?:((0x|0X|)[a-fA-F0-9]{40,}))"
        override fun algorithm() = Algorithm.Secp256k1
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    },
    Ripple {
        override fun feeDefault() = 0.000012
        override fun minimumAmount() = 1.0
        override fun supportMemo() = true
        override fun nameCoin() = "Ripple"
        override fun symbolName() = "XRP"
        override fun minimumValue() = 0.00001
        override fun unitValue() = 1_000_000.0
        override fun regex() = "(?:([a-km-zA-HJ-NP-Z1-9]{26,35}))"
        override fun algorithm() = Algorithm.Secp256k1
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    },
    Centrality {
        override fun feeDefault() = 15287.0
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin(): String = when (assetId) {
            1 -> "CENNZnet"; 2 -> "CPAY"; else -> "Centrality"
        }
        override fun symbolName(): String = when (assetId) {
            1 -> "CENNZ"; 2 -> "CPAY"; else -> "CENNZ"
        }
        override fun minimumValue() = 0.01
        override fun unitValue() = 10_000.0
        override fun regex() = "(?:(5|[a-km-zA-HJ-NP-Z1-9]{47,}))"
        override fun algorithm() = Algorithm.Sr25519
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    },
    TON {
        override fun feeDefault() = 0.01
        override fun minimumAmount() = 0.0
        override fun supportMemo() = true
        override fun nameCoin() = "TON"
        override fun symbolName() = "TON"
        override fun minimumValue() = 0.01
        override fun unitValue() = 1_000_000_000.0
        override fun regex() = "(?:([a-km-zA-HJ-NP-Z1-9]{48,}))"
        override fun algorithm() = Algorithm.Ed25519
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    },
    Midnight {
        override fun feeDefault() = 0.01
        override fun minimumAmount() = 0.0
        override fun supportMemo() = false
        override fun nameCoin() = "Midnight"
        override fun symbolName() = "tDUST"
        override fun minimumValue() = 0.01
        override fun unitValue() = 1_000_000.0
        override fun regex() = "(?:(midnight1[a-z0-9]{38,}))"
        override fun algorithm() = Algorithm.Ed25519
        override fun baseApiUrl() = ""
        override fun allowNewAddress() = false
    };

    abstract fun nameCoin(): String
    abstract fun symbolName(): String
    abstract fun minimumValue(): Double
    abstract fun regex(): String
    abstract fun algorithm(): Algorithm
    abstract fun baseApiUrl(): String
    abstract fun unitValue(): Double
    abstract fun feeDefault(): Double
    abstract fun minimumAmount(): Double
    abstract fun supportMemo(): Boolean
    abstract fun allowNewAddress(): Boolean
    fun assetId(): Int = assetId
}
