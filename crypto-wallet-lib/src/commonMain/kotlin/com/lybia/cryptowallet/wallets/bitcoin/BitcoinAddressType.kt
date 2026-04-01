package com.lybia.cryptowallet.wallets.bitcoin

/**
 * Enum representing the supported Bitcoin address types.
 *
 * Each type corresponds to a specific BIP standard, script type, and address prefix format.
 */
enum class BitcoinAddressType {
    /**
     * Native SegWit (BIP-84) — P2WPKH script type.
     *
     * Derivation path: `m/84'/coin_type'/account'/change/index`
     * Mainnet prefix: `bc1q...` (Bech32)
     * Testnet prefix: `tb1q...` (Bech32)
     *
     * Lowest transaction fees among all address types.
     */
    NATIVE_SEGWIT,

    /**
     * Nested SegWit (BIP-49) — P2SH-P2WPKH script type.
     *
     * Derivation path: `m/49'/coin_type'/account'/change/index`
     * Mainnet prefix: `3...`
     * Testnet prefix: `2...`
     *
     * Backward compatible with services that do not yet support Native SegWit.
     */
    NESTED_SEGWIT,

    /**
     * Legacy (BIP-44) — P2PKH script type.
     *
     * Derivation path: `m/44'/coin_type'/account'/change/index`
     * Mainnet prefix: `1...`
     * Testnet prefix: `m...` / `n...`
     *
     * Original Bitcoin address format with the widest compatibility.
     */
    LEGACY
}
