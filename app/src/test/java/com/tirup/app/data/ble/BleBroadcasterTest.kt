package com.tirup.app.data.ble

import android.bluetooth.BluetoothAdapter
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BleBridgeSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleBroadcasterTest {

    @Test
    fun testBleBridgeSettingsDefaultLongRangeIsFalse() {
        val settings = BleBridgeSettings()
        assertFalse(settings.useLongRange)
        assertEquals(BleBridgeRole.DISABLED, settings.role)
    }

    @Test
    fun testBleBridgeSettingsCopyPreservesLongRange() {
        val settings = BleBridgeSettings(role = BleBridgeRole.BROADCASTER, useLongRange = true)
        assertTrue(settings.useLongRange)

        val updated = settings.copy(familyPin = "XYZ")
        assertTrue(updated.useLongRange)
        assertEquals("XYZ", updated.familyPin)
    }

    @Test
    fun testIsLongRangeSupportedWhenBothSupported() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeCodedPhySupported } returns true
        every { adapter.isLeExtendedAdvertisingSupported } returns true

        val supported = BleBroadcaster.isLongRangeSupported(adapter)
        assertTrue(supported)
    }

    @Test
    fun testIsLongRangeSupportedWhenCodedPhyNotSupported() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeCodedPhySupported } returns false
        every { adapter.isLeExtendedAdvertisingSupported } returns true

        val supported = BleBroadcaster.isLongRangeSupported(adapter)
        assertFalse(supported)
    }

    @Test
    fun testIsLongRangeSupportedWhenExtendedAdvNotSupported() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeCodedPhySupported } returns true
        every { adapter.isLeExtendedAdvertisingSupported } returns false

        val supported = BleBroadcaster.isLongRangeSupported(adapter)
        assertFalse(supported)
    }

    @Test
    fun testIsLongRangeSupportedWhenExceptionThrown() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeCodedPhySupported } throws RuntimeException("Bluetooth HAL dead")

        val supported = BleBroadcaster.isLongRangeSupported(adapter)
        assertFalse(supported)
    }
}
