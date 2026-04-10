package com.lybia.cryptowallet.utils

import java.text.Normalizer

actual fun String.nfkd(): String = Normalizer.normalize(this, Normalizer.Form.NFKD)
