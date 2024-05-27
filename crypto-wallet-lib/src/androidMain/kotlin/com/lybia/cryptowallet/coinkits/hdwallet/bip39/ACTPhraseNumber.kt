package com.lybia.cryptowallet.coinkits.hdwallet.bip39


enum class ACTPhraseNumber(val value: Int) {
    Phrase12(12) {
        override fun bitsNumber(): Int      {return 128}
        override fun namePhrase(): String   {return "12 Phrase"}
    },
    Phrase15(15){
        override fun bitsNumber(): Int      {return 160}
        override fun namePhrase(): String   {return "15 Phrase"}
    },
    Phrase18(18){
        override fun bitsNumber(): Int      {return 192}
        override fun namePhrase(): String   {return "18 Phrase"}
    },
    Phrase21(21){
        override fun bitsNumber(): Int      {return 224}
        override fun namePhrase(): String   {return "21 Phrase"}
    },
    Phrase24(24){
        override fun bitsNumber(): Int      {return 256}
        override fun namePhrase(): String   {return "24 Phrase"}
    };
    abstract fun bitsNumber()   : Int
    abstract fun namePhrase()   : String
    companion object {
        fun all(): Array<ACTPhraseNumber> = arrayOf(ACTPhraseNumber.Phrase12, ACTPhraseNumber.Phrase15, ACTPhraseNumber.Phrase18, ACTPhraseNumber.Phrase21, ACTPhraseNumber.Phrase24)
    }
}