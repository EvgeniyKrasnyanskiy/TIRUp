package com.tirup.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightscoutSettingsTest {

    @Test
    fun testUrlValidation() {
        val empty = NightscoutSettings()
        assertFalse(empty.isValidUrl)

        val invalid = NightscoutSettings(serverUrl = "ftp://192.168.1.1:8080")
        assertFalse(invalid.isValidUrl)

        val validHttp = NightscoutSettings(serverUrl = "http://192.168.1.50:8085")
        assertTrue(validHttp.isValidUrl)

        val validHttps = NightscoutSettings(serverUrl = "https://myserver.cloud:8085/")
        assertTrue(validHttps.isValidUrl)
    }

    @Test
    fun testCleanBaseUrl() {
        val settings = NightscoutSettings(serverUrl = "  http://10.0.0.5:8080///  ")
        assertEquals("http://10.0.0.5:8080", settings.getCleanBaseUrl())
    }

    @Test
    fun testDefaultConfirmationEnabled() {
        val settings = NightscoutSettings()
        assertTrue(settings.requireXdripConfirmation)
        assertTrue(settings.downloadGlucose)
        assertFalse(settings.isEnabled)
    }
}
