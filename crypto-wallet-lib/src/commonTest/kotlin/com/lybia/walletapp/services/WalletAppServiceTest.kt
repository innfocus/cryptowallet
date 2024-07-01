package com.lybia.walletapp.services

import com.lybia.walletapp.exceptions.RequestException
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletAppServiceTest {
    private lateinit var serverUrl :String
    private lateinit var walletAppService: WalletAppService
    private var authToken: String? = null

    @BeforeTest
    fun setup() {
        serverUrl = "https://staging-api.ai-staking.io"
        walletAppService = WalletAppService(serverUrl, authToken)
    }

    @Test
    fun testSendOtpSuccess() = runTest {

        // Call the method to test
        val response = walletAppService.sendOtp("test@gmail.com")

        // Assertions
        assertEquals(true, response.success)
        assertEquals("Success", response.message)
        assertEquals(200, response.code)

        print("Response: $response")
    }

    @Test
    fun testCheckOtpSuccess() = runTest {

        // Call the method to test
        val response = walletAppService.verifyOtp("test@gmail.com", "3894")

        // Assertions
        assertEquals(true, response.success)
        assertEquals("Success", response.message)
        assertNotNull(response.authToken)

        authToken = response.authToken
        print("Response: $response")
    }

    @Test
    fun testCheckOTPFail() = runTest {


      val exception = assertFailsWith<RequestException> {
          walletAppService.verifyOtp("test@gmail.com", "1234")
      }

        assertEquals(exception.code, 422)
        assertEquals(exception.message, "Verified failed")
    }

    @Test
    fun testGetProfileSuccess() = runTest {
        authToken = ""
        walletAppService = WalletAppService(serverUrl, authToken)
        val response = walletAppService.getProfile()

        assertNotNull(response)
        println("Response: $response")
    }

    @Test
    fun testGetProfileFail() = runTest {

        val exception = assertFailsWith<RequestException> {
            walletAppService.getProfile()
        }

        assertEquals(exception.code, 401)
        assertEquals(exception.message, "Missing Authorization Header")


    }


}