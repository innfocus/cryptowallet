package com.lybia.cryptowallet.utils

/**
 * Unicode NFKD normalization.
 *
 * Required by BIP-39 before PBKDF2-HMAC-SHA512 — without this, Japanese
 * (and other CJK) mnemonics can produce different seeds across platforms
 * when the input arrives in different normalization forms (NFC vs NFD,
 * e.g. げ as U+3052 vs U+3051 U+3099).
 */
expect fun String.nfkd(): String
