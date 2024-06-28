import com.lybia.cryptowallet.wallets.bip39.MNEMONIC_SIZE
import com.lybia.cryptowallet.wallets.bip39.Mnemonics
import fr.acinq.bitcoin.MnemonicCode
import kotlin.test.Test
import kotlin.test.assertTrue

class MnemonicTest {

    @Test
    fun testGenerateRandomSeed(){

        val mnemonic15 = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_15)
        val mnemonic18 = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_18)
        val mnemonic21 = Mnemonics.generateRandomSeed(MNEMONIC_SIZE.WORDS_21)

        assertTrue(mnemonic15.size == 15)
        assertTrue(mnemonic18.size == 18)
        assertTrue(mnemonic21.size == 21)


        println("mnemonic15: $mnemonic15")
        println("mnemonic18: $mnemonic18")
        println("mnemonic21: $mnemonic21")

        for (seed in listOf(mnemonic15,mnemonic18,mnemonic21)){
           MnemonicCode.validate(seed)
        }

    }


}