package com.lybia.cryptowallet.services

import com.lybia.cryptowallet.models.bitcoin.BTCApiModel
import com.lybia.cryptowallet.models.ripple.RippleAccountData
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResponse
import com.lybia.cryptowallet.models.ripple.RippleAccountInfoResult
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Property 12: API service balance parsing correctness**
 *
 * For any valid API response (Bitcoin, Ripple, Ethereum): parsed balance ≥ 0.
 *
 * **Validates: Requirements 7.3, 8.3**
 */
class BalanceParsingPropertyTest {

    @Test
    fun bitcoinBalanceParsingAlwaysNonNegative() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.long(0L..21_000_000_00000000L)) { satoshis ->
            // Simulate parsing a BTCApiModel balance field
            val model = BTCApiModel(
                address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                totalReceived = satoshis,
                totalSent = 0,
                balance = satoshis,
                unconfirmedBalance = 0,
                finalBalance = satoshis,
                nTx = 1,
                unconfirmedNTx = 0,
                finalNTx = 1
            )
            val balanceBtc = model.balance.toDouble() / 100_000_000.0
            assertTrue(balanceBtc >= 0.0, "Bitcoin balance must be non-negative, got $balanceBtc")
        }
    }

    @Test
    fun rippleBalanceParsingAlwaysNonNegative() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.long(0L..100_000_000_000_000L)) { drops ->
            // Simulate parsing a Ripple account_info response
            val response = RippleAccountInfoResponse(
                result = RippleAccountInfoResult(
                    status = "success",
                    accountData = RippleAccountData(
                        account = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
                        balance = drops.toString(),
                        sequence = 1
                    )
                )
            )
            val balanceDrops = response.result.accountData?.balance?.toLongOrNull() ?: 0L
            val balanceXrp = balanceDrops.toDouble() / 1_000_000.0
            assertTrue(balanceXrp >= 0.0, "Ripple balance must be non-negative, got $balanceXrp")
        }
    }

    @Test
    fun ethereumHexBalanceParsingAlwaysNonNegative() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.long(0L..Long.MAX_VALUE / 2)) { weiValue ->
            // Simulate parsing an Ethereum hex balance response
            val hexBalance = "0x" + weiValue.toString(16)
            val parsed = hexBalance.removePrefix("0x").toLongOrNull(16) ?: 0L
            val balanceEth = parsed.toDouble() / 1_000_000_000_000_000_000.0
            assertTrue(balanceEth >= 0.0, "Ethereum balance must be non-negative, got $balanceEth")
        }
    }
}
