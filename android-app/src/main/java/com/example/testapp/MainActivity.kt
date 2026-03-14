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
import com.lybia.cryptowallet.coinkits.BalanceHandle
import com.lybia.cryptowallet.coinkits.CoinsManager
import com.lybia.cryptowallet.coinkits.SendCoinHandle
import com.lybia.cryptowallet.coinkits.TransationData
import com.lybia.cryptowallet.coinkits.TransactionsHandle
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTCoin
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etMnemonic: EditText
    private lateinit var etToAddress: EditText
    private lateinit var etAmount: EditText
    private lateinit var etMemo: EditText
    private lateinit var tvOutput: TextView
    private lateinit var radioGroupNetwork: RadioGroup
    
    // For advanced TON features not yet in CoinsManager
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

        etMnemonic.setText("push dawn mercy parade famous armor saddle caught profit gauge sunny bonus verify grape involve ensure reject duty pottery soap surround have napkin magnet")

        findViewById<Button>(R.id.btnGenerateMnemonic).setOnClickListener {
            generateRandomMnemonic()
        }

        findViewById<Button>(R.id.btnGetAddress).setOnClickListener {
            initManagerFromInput()
            val address = CoinsManager.shared.firstAddress(ACTCoin.TON)?.rawAddressString()
            log("Address (via CoinsManager): $address")
        }

        findViewById<Button>(R.id.btnGetBalance).setOnClickListener {
            initManagerFromInput()
            CoinsManager.shared.getBalance(ACTCoin.TON, object : BalanceHandle {
                override fun completionHandler(balance: Double, success: Boolean) {
                    runOnUiThread {
                        if (success) {
                            log("Balance (via CoinsManager): $balance TON")
                        } else {
                            log("Failed to get balance via CoinsManager")
                        }
                    }
                }
            })
        }

        findViewById<Button>(R.id.btnGetJettonBalance).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                // USDT Master Address on TON Mainnet
                val usdtMaster = "EQCxE6mUtVrWBMD77QCWnv9sh9SZAInAsS9ZebNo09pInOCp"
                val address = tonManager.getAddress()
                val balance = tonManager.getBalanceToken(address, usdtMaster, coinNetwork)
                log("Jetton Balance (USDT): $balance")
            }
        }

        findViewById<Button>(R.id.btnGetJettonHistory).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                val usdtMaster = "EQCxE6mUtVrWBMD77QCWnv9sh9SZAInAsS9ZebNo09pInOCp"
                val address = tonManager.getAddress()
                val history = tonManager.getTransactionHistoryToken(address, usdtMaster, coinNetwork)
                log("Jetton History (USDT): $history")
            }
        }

        findViewById<Button>(R.id.btnGetJettonMetadata).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                val usdtMaster = "EQCxE6mUtVrWBMD77QCWnv9sh9SZAInAsS9ZebNo09pInOCp"
                val metadata = tonManager.getJettonMetadata(usdtMaster, coinNetwork)
                log("Jetton Metadata: Name=${metadata?.name}, Symbol=${metadata?.symbol}, Decimals=${metadata?.decimals}")
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
            initManagerFromInput()
            CoinsManager.shared.getTransactions(ACTCoin.TON, null, object : TransactionsHandle {
                override fun completionHandler(transactions: Array<TransationData>?, moreParam: JsonObject?, errStr: String) {
                    runOnUiThread {
                        if (transactions != null) {
                            log("History (via CoinsManager): ${transactions.size} transactions")
                            transactions.take(3).forEach { 
                                log(" - TX: ${it.iD.take(8)}... Amount: ${it.amount}")
                            }
                        } else {
                            log("Failed to get history: $errStr")
                        }
                    }
                }
            })
        }

        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            runTest {
                initManagerFromInput()
                val fromAddress = CoinsManager.shared.firstAddress(ACTCoin.TON)
                if (fromAddress == null) {
                    log("Error: Could not derive address")
                    return@runTest
                }

                val toAddr = etToAddress.text.toString().trim()
                val amountStr = etAmount.text.toString().trim()
                
                if (toAddr.isEmpty() || amountStr.isEmpty()) {
                    log("Error: Destination and Amount are required")
                    return@runTest
                }

                log("Initiating transfer via CoinsManager...")
                CoinsManager.shared.sendCoin(
                    fromAddress = fromAddress,
                    toAddressStr = toAddr,
                    serAddressStr = "",
                    amount = amountStr.toDouble(),
                    networkFee = 0.05, // Mock fee
                    serviceFee = 0.0,
                    completionHandler = object : SendCoinHandle {
                        override fun completionHandler(transID: String, success: Boolean, errStr: String) {
                            runOnUiThread {
                                if (success) {
                                    log("Transfer Success (via CoinsManager)! TX ID: $transID")
                                } else {
                                    log("Transfer Failed: $errStr")
                                }
                            }
                        }
                    }
                )
            }
        }

        findViewById<Button>(R.id.btnJettonTransfer).setOnClickListener {
            runTest {
                initManagerFromInput()
                val coinNetwork = getTonCoinNetwork()
                
                val toAddr = etToAddress.text.toString().trim()
                val amountStr = etAmount.text.toString().trim()
                val memo = etMemo.text.toString().takeIf { it.isNotEmpty() }
                val usdtMaster = "EQCxE6mUtVrWBMD77QCWnv9sh9SZAInAsS9ZebNo09pInOCp"
                
                if (toAddr.isEmpty() || amountStr.isEmpty()) {
                    log("Error: Destination and Amount are required")
                    return@runTest
                }
                
                // Giả định USDT có 6 decimals (thực tế USDT trên TON có 6 decimals)
                val jettonAmountNano = (amountStr.toDouble() * 1_000_000).toLong()
                
                log("1. Fetching seqno...")
                val seqno = tonManager.getSeqno(coinNetwork)
                
                log("2. Signing Jetton transaction...")
                val bocBase64 = tonManager.signJettonTransaction(
                    usdtMaster, toAddr, jettonAmountNano, seqno, coinNetwork, memo = memo
                )
                
                log("3. Broadcasting Jetton Transfer...")
                val status = tonManager.TransferToken(bocBase64, coinNetwork)
                log("Jetton Transfer Status: $status")
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

        // Update CoinsManager (Primary)
        CoinsManager.shared.mnemonic = input

        // Update TonManager (For advanced features)
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
