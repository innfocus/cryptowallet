package com.lybia.cryptowallet.utils

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCompatibilityMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun String.nfkd(): String =
    (this as NSString).decomposedStringWithCompatibilityMapping
