package com.lybia.walletapp.services

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletAppServiceTest {

    @Test
    fun testSendOtpSuccess() = runTest {
        val serverUrl = "https://staging-api.ai-staking.io"
        val walletAppService = WalletAppService(serverUrl)

        // Call the method to test
        val response = walletAppService.sendOtp("test@gmail.com")

        // Assertions
        assertEquals(true, response.success)
        assertEquals("Success", response.message)
        assertEquals(200, response.code)
    }

    @Test
    fun testCheckOtpSuccess() = runTest {
        val serverUrl = "https://staging-api.ai-staking.io"
        val walletAppService = WalletAppService(serverUrl)

        // Call the method to test
        val response = walletAppService.verifyOtp("test@gmail.com", "0000")

        // Assertions
        assertEquals(true, response.success)
        assertEquals("Success", response.message)
        assertNotNull(response.authToken)
    }
}