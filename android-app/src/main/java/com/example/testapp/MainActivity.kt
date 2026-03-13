package com.example.testapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.CoinNetwork
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.enums.NetworkName
import com.lybia.cryptowallet.wallets.ton.TonManager
import com.lybia.cryptowallet.wallets.bip39.Mnemonics
import com.lybia.cryptowallet.wallets.bip39.MNEMONIC_SIZE
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etMnemonic: EditText
    private lateinit var etToAddress: EditText
    private lateinit var etAmount: EditText
    private lateinit var etMemo: EditText
    private lateinit var tvOutput: TextView
    private lateinit var radioGroupNetwork: RadioGroup
    
    private lateinit var tonManager: TonManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etMnemonic = findViewById(R.id.etMnemonic)
        etToAddress = findViewById(R.id.etToAddress)
        etAmount = findViewById(R.id.etAmount)
        etMemo = findViewById(R.id.etMemo)
        tvOutput = findViewById(R.id.textViewOutput)
        radioGroupNetwork = findViewById(R.id.radioGroupNetwork)

        generateRandomMnemonic()

        findViewById<Button>(R.id.btnGenerateMnemonic).setOnClickListener {
            generateRandomMnemonic()
        }

        findViewById<Button>(R.id.btnGetAddress).setOnClickListener {
            runTest {
                initManagerFromInput()
                val address = tonManager.getAddress()
                log("Address: $address")
            }
        }

        findViewById<Button>(R.id.btnGetBalance).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                val balance = tonManager.getBalance(null, coinNetwork)
                log("Balance: $balance TON")
            }
        }

        findViewById<Button>(R.id.btnGetChainId).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                val chainId = tonManager.getChainId(coinNetwork)
                log("Chain ID: $chainId")
            }
        }

        findViewById<Button>(R.id.btnGetHistory).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                val history = tonManager.getTransactionHistory(null, coinNetwork)
                log("History: $history")
            }
        }

        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                
                val toAddr = etToAddress.text.toString().trim()
                val amountStr = etAmount.text.toString().trim()
                val memo = etMemo.text.toString().takeIf { it.isNotEmpty() }
                
                if (toAddr.isEmpty() || amountStr.isEmpty()) {
                    log("Error: Destination and Amount are required")
                    return@runTest
                }
                
                val amountNano = (amountStr.toDouble() * 1_000_000_000).toLong()
                
                log("1. Fetching seqno...")
                val seqno = tonManager.getSeqno(coinNetwork)
                log("   Seqno: $seqno")
                
                log("2. Signing transaction...")
                val bocBase64 = tonManager.signTransaction(toAddr, amountNano, seqno, memo)
                log("   BOC length: ${bocBase64.length}")
                
                log("3. Estimating fee...")
                val fee = tonManager.estimateFee(coinNetwork, toAddr, bocBase64)
                log("   Estimated Fee: $fee TON")
                
                log("4. Broadcasting...")
                val result = tonManager.transfer(bocBase64, coinNetwork)
                log("Transfer Result: success=${result.success}, error=${result.error}, status=${result.txHash}")
            }
        }
    }

    private fun generateRandomMnemonic() {
        val words = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_12)
        val mnemonicStr = words.joinToString(" ")
        etMnemonic.setText(mnemonicStr)
        log("Random mnemonic generated.")
    }

    private fun initManagerFromInput() {
        val input = etMnemonic.text.toString().trim()
        if (input.isEmpty()) {
            throw Exception("Mnemonic input is empty!")
        }
        updateConfig()
        tonManager = TonManager(input)
    }

    private fun updateConfig() {
        val selectedId = radioGroupNetwork.checkedRadioButtonId
        if (selectedId == R.id.radioMainnet) {
            Config.shared.setNetwork(Network.MAINNET)
            log("Network set to MAINNET")
        } else {
            Config.shared.setNetwork(Network.TESTNET)
            log("Network set to TESTNET")
        }
    }

    private fun getTonCoinNetwork(): CoinNetwork {
        // IMPORTANT: Replace with real API keys if testing actual transfers
        return CoinNetwork(
            name = NetworkName.TON,
            apiKeyExplorer = "mock_explorer_key",
            apiKeyInfura = "mock_infura_key" 
        )
    }

    private fun runTest(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                log("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun log(message: String) {
        val currentText = tvOutput.text.toString()
        tvOutput.text = "> $message\n$currentText"
    }
}
