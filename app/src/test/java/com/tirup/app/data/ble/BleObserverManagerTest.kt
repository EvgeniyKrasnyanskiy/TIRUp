package com.tirup.app.data.ble

import android.bluetooth.BluetoothAdapter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleObserverManagerTest {

    @Test
    fun testIsLongRangeScanSupportedWhenSupported() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeExtendedAdvertisingSupported } returns true

        val supported = BleObserverManager.isLongRangeScanSupported(adapter)
        assertTrue(supported)
    }

    @Test
    fun testIsLongRangeScanSupportedWhenNotSupported() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeExtendedAdvertisingSupported } returns false

        val supported = BleObserverManager.isLongRangeScanSupported(adapter)
        assertFalse(supported)
    }

    @Test
    fun testIsLongRangeScanSupportedWhenExceptionThrown() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isLeExtendedAdvertisingSupported } throws RuntimeException("Driver dead")

        val supported = BleObserverManager.isLongRangeScanSupported(adapter)
        assertFalse(supported)
    }
}
