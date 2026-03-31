package com.lybia.cryptowallet.enums

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit test verifying NetworkName enum contains CENTRALITY.
 */
class NetworkNameTest {

    @Test
    fun networkNameContainsCentrality() {
        val names = NetworkName.entries.map { it.name }
        assertTrue(names.contains("CENTRALITY"), "NetworkName enum should contain CENTRALITY")
    }

    @Test
    fun centralityEnumValueIsAccessible() {
        val centrality = NetworkName.CENTRALITY
        assertTrue(centrality.name == "CENTRALITY")
    }
}
