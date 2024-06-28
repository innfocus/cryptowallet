import com.lybia.cryptowallet.Config
import com.lybia.cryptowallet.enums.Network
import com.lybia.cryptowallet.wallets.bitcoin.BitcoinManager
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BitcoinManagerTest {

    private lateinit var bitcoinManager: BitcoinManager
    private lateinit var mnemonic: String


    @BeforeTest
    fun setup() {
        mnemonic = "" // Mnemonic seed words
        bitcoinManager = BitcoinManager(mnemonic)
        Config.shared.setNetwork(Network.MAINNET) // Set network

    }

    @Test
    fun testGetLegacyAddressBitcoin() {
        val address = bitcoinManager.getLegacyAddress()
        assertNotNull(address)
        assertTrue(address.isNotEmpty())
        println("Address: $address")
    }

    @Test
    fun testGetSegwitBtc1Address() {
        val address = bitcoinManager.getNativeSegWitAddress()
        assertNotNull(address)
        assertTrue(address.isNotEmpty())
        println("Address: $address")
    }

    @Test
    fun testGetSegwitAdress() {
        val address = bitcoinManager.getSegWitAddress()
        assertNotNull(address)
        assertTrue(address.isNotEmpty())
        println("Address: $address")
    }

    @Test
    fun testGetBalanceAddress() = runBlocking {

        bitcoinManager.getNativeSegWitAddress()
        val balance = bitcoinManager.getBalance()

        assertNotNull(balance)
        assertTrue(balance >= 0)
        println("Balance: $balance")

    }

    @Test
    fun testTransactionHistory() = runBlocking {
        bitcoinManager.getNativeSegWitAddress()
        val transactionHistory = bitcoinManager.getTransactionHistory()
        assertNotNull(transactionHistory)
        assertTrue(transactionHistory is List<*>)
        assertTrue(transactionHistory.size > 0)
        println("Transaction History: $transactionHistory")
    }
}