package com.lybia.cryptowallet.coinkits.ripple.networking.jsonRPCSimple

class ACTIDGenerator {
    private var currentId = 0
    fun next(): Int {
        currentId += 1
        return currentId
    }
}