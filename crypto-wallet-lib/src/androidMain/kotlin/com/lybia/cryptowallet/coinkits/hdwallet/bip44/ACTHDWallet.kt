package com.lybia.cryptowallet.coinkits.hdwallet.bip44

import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTBIP32Error
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTBIP32Exception
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTNetwork
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPrivateKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.ACTPublicKey
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.Algorithm
import com.lybia.cryptowallet.coinkits.hdwallet.bip32.Change
import com.lybia.cryptowallet.coinkits.hdwallet.bip39.ACTBIP39
import com.lybia.cryptowallet.coinkits.hdwallet.bip39.ACTBIP39Exception
import com.lybia.cryptowallet.coinkits.hdwallet.bip39.ACTLanguages
import com.lybia.cryptowallet.coinkits.hdwallet.core.ACTDerivationNode
import com.lybia.cryptowallet.coinkits.hdwallet.core.crypto.ACTCryto
import com.lybia.cryptowallet.coinkits.hdwallet.core.helpers.fromHexToByteArray


class ACTHDWallet @Throws(ACTBIP39Exception::class) constructor(mnemonic: String) {

    private val seed        : ByteArray
    private val entropy     : ByteArray
    private val language    : ACTLanguages

    init {
        try{
            val seedString      = ACTBIP39.deterministicSeedString(mnemonic)
            val entropyString   = ACTBIP39.entropyString(mnemonic, true)
            this.seed           = seedString.fromHexToByteArray()
            this.entropy        = entropyString.fromHexToByteArray()
            this.language       = ACTLanguages.detectTypeWithMnemonic(mnemonic)!!
        }catch (e: ACTBIP39Exception) {
            throw e
        }
    }

    fun calculateSeed(network: ACTNetwork): ByteArray {
        return when (network.coin.algorithm()) {
            Algorithm.Secp256k1 -> {
                seed
            }
            Algorithm.Sr25519 -> {
                return ACTCryto.pbkdf2SHA512(
                    entropy,
                    "mnemonic".toByteArray(),
                    2048,
                    32
                )
            }
            else -> {
                ACTCryto.pbkdf2SHA512(byteArrayOf(), entropy, 4096, 96)
            }
        }
    }

    /*
    * Standard APIs
    */

    @Throws(ACTBIP39Exception::class)
    fun generateExternalPrivateKey(index: Int, network: ACTNetwork): ACTPrivateKey {
        return privateKeyStandard(Change.External, network).derived(ACTDerivationNode(index, false))
    }

    @Throws(ACTBIP39Exception::class)
    fun generateInternalPrivateKey(index: Int, network: ACTNetwork): ACTPrivateKey {
        return privateKeyStandard(Change.Internal, network).derived(ACTDerivationNode(index, false))
    }

    @Throws(ACTBIP39Exception::class)
    fun generateExternalPublickKey(index: Int, network: ACTNetwork): ACTPublicKey {
        return generateExternalPrivateKey(index, network).publicKey()
    }

    @Throws(ACTBIP39Exception::class)
    fun generateInternalPublickKey(index: Int, network: ACTNetwork): ACTPublicKey {
        return generateInternalPrivateKey(index, network).publicKey()
    }

    data class Result(val extKeys: List<ACTPrivateKey>, val intKeys: List<ACTPrivateKey>)
    @Throws(ACTBIP39Exception::class)
    fun generateStandardPrivateKeys(network: ACTNetwork): Result {
        return generatePrivateKeys( extNumber = network.derivateIdxMax(Change.External),
                                    intNumber = network.derivateIdxMax(Change.Internal),
                                    network   = network)
    }

    @Throws(ACTBIP39Exception::class)
    fun xPubStandardBIP32(network: ACTNetwork): ACTPublicKey {
        val derivationPath = network.derivationPath()
        return privateKeyWith(derivationPath, network).publicKey()
    }

    @Throws(ACTBIP39Exception::class)
    fun privateKeyStandard(change: Change, network: ACTNetwork): ACTPrivateKey {
        val derivationPath = network.derivationPath() + "/" + change.value.toString()
        return privateKeyWith(derivationPath, network)
    }

    /*
    * Custom APIs
    */

    @Throws(ACTBIP39Exception::class)
    fun xPubBIP32With(derivationPath: String, network: ACTNetwork): ACTPublicKey {
        return privateKeyWith(derivationPath, network).publicKey()
    }

    fun generatePrivateKeys(extNumber   : Int,
                            fromExtIdx  : Int = 0,
                            intNumber   : Int,
                            fromInIdx   : Int = 0,
                            network     : ACTNetwork
    ): Result {
        var extKeys     = mutableListOf<ACTPrivateKey>()
        var intKeys     = mutableListOf<ACTPrivateKey>()
        val extPrvKey   = privateKeyStandard(Change.External, network)
        val intPrvKey   = privateKeyStandard(Change.Internal, network)
        for (i in 0 until extNumber) {
            extKeys.add(extPrvKey.derived(ACTDerivationNode(i + fromExtIdx, false)))
        }
        for (i in 0 until intNumber) {
            intKeys.add(intPrvKey.derived(ACTDerivationNode(i + fromInIdx, false)))
        }
        return Result(extKeys, intKeys)
    }

    @Throws(ACTBIP39Exception::class)
    fun privateKeyWith(derivationPath: String, network: ACTNetwork): ACTPrivateKey {
        val masterPrivateKey    = ACTPrivateKey(calculateSeed(network), network)
        val derivations         = derivationPath.trim().split("/")
        var privateKey          = masterPrivateKey
        try {
            derivations.forEach {
                val current = it.trim()
                if (current.isNotEmpty()) {
                    val isHard  = current.contains("'")
                    val item    = current.replace("'", "")
                    val idx     = item.toIntOrNull() ?: -1
                    when (idx > -1) {
                        true    -> {privateKey = privateKey.derived(ACTDerivationNode(idx, isHard))}
                        false   -> {/* SKIP */}
                    }
                }
            }
            return when(privateKey == masterPrivateKey) {
                true    -> {throw ACTBIP32Exception(ACTBIP32Error.KeyDerivateionFailed.message)}
                false   -> {privateKey}
            }
        }catch (e: ACTBIP32Exception) {
            return throw e
        }
    }
}