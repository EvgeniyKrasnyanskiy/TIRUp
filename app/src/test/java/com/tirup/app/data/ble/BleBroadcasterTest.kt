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

    @Test
    fun testBurstAndHardwareTimeoutCalculations() {
        assertEquals(10_000L, BleBroadcaster.BURST_1MIN_MS)
        assertEquals(15_000L, BleBroadcaster.BURST_5MIN_MS)
        assertEquals(2_500L, BleBroadcaster.WAKE_LOCK_START_TIMEOUT_MS)

        // Legacy setTimeout: clamped to [1000..180000] ms
        assertEquals(10_000, BleBroadcaster.computeLegacyTimeoutMs(BleBroadcaster.BURST_1MIN_MS))
        assertEquals(15_000, BleBroadcaster.computeLegacyTimeoutMs(BleBroadcaster.BURST_5MIN_MS))
        assertEquals(1_000, BleBroadcaster.computeLegacyTimeoutMs(200L))
        assertEquals(180_000, BleBroadcaster.computeLegacyTimeoutMs(500_000L))

        // AdvertisingSet duration: in 10ms units, clamped to [100..65535]
        assertEquals(1_000, BleBroadcaster.computeAdvertisingSetDurationTenMs(BleBroadcaster.BURST_1MIN_MS))
        assertEquals(1_500, BleBroadcaster.computeAdvertisingSetDurationTenMs(BleBroadcaster.BURST_5MIN_MS))
        assertEquals(3_000, BleBroadcaster.computeAdvertisingSetDurationTenMs(BleBroadcaster.TEST_PING_BURST_MS))
        assertEquals(100, BleBroadcaster.computeAdvertisingSetDurationTenMs(500L))
        assertEquals(65_535, BleBroadcaster.computeAdvertisingSetDurationTenMs(900_000L))
    }
}
