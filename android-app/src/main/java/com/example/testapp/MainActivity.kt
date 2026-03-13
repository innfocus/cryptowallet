package com.example.testapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import com.lybia.cryptowallet.wallets.bip39.Mnemonics
import com.lybia.cryptowallet.wallets.bip39.MNEMONIC_SIZE

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val output = findViewById<TextView>(R.id.textViewOutput)
        
        try {
            // Configure network
            Config.shared.setNetwork(Network.TESTNET)
            
            // Generate some random mnemonics
            val mnemonicWords = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_12)
            val mnemonicStr = mnemonicWords.joinToString(" ")
            
            // Initialize BitcoinManager
            val bitcoinManager = BitcoinManager(mnemonicStr)
            
            // Get addresses
            val legacyAddress = bitcoinManager.getLegacyAddress()
            val segwitAddress = bitcoinManager.getNativeSegWitAddress()
            
            val sb = StringBuilder()
            sb.append("Network: ").append(Config.shared.getNetwork()).append("\n\n")
            sb.append("Mnemonic: \n").append(mnemonicStr).append("\n\n")
            sb.append("Legacy Address: \n").append(legacyAddress).append("\n\n")
            sb.append("SegWit Address: \n").append(segwitAddress).append("\n")
            
            output.text = sb.toString()
            
        } catch (e: Exception) {
            output.text = "Error: ${e.message}\n${e.stackTraceToString()}"
        }
    }
}
