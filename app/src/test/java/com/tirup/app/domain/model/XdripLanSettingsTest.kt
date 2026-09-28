package com.tirup.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripLanSettingsTest {

    @Test
    fun defaultSettings_areDisabledAndInvalidHost() {
        val settings = XdripLanSettings()
        assertFalse(settings.isEnabled)
        assertEquals("", settings.masterHost)
        assertEquals(17580, settings.port)
        assertEquals("", settings.apiSecret)
        assertEquals(60, settings.pollIntervalSeconds)
        assertFalse(settings.isValidHost)
        assertNull(settings.hashedSecret)
    }

    @Test
    fun validHost_validatesProperly() {
        val settings = XdripLanSettings(masterHost = "192.168.1.100", port = 17580)
        assertTrue(settings.isValidHost)
        assertEquals("192.168.1.100", settings.cleanHost)
        assertEquals("http://192.168.1.100:17580", settings.baseUrl)
    }

    @Test
    fun cleanHost_stripsHttpPrefixAndSlashes() {
        val s1 = XdripLanSettings(masterHost = "http://192.168.43.1:17580/", port = 17580)
        assertEquals("192.168.43.1:17580", s1.cleanHost)
        assertEquals("http://192.168.43.1:17580", s1.baseUrl)

        val s2 = XdripLanSettings(masterHost = "https://master.local/", port = 17580)
        assertEquals("master.local", s2.cleanHost)
        assertEquals("http://master.local:17580", s2.baseUrl)
    }

    @Test
    fun hashedSecret_computesCorrectSha1() {
        // "password" in SHA-1 is 5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8
        val settings = XdripLanSettings(apiSecret = "password")
        assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", settings.hashedSecret)
    }
}
