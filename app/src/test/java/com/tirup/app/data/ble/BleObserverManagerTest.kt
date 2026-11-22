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

    @Test
    fun testMetricsCalculation1MinuteCadence() {
        val baseTime = 1_000_000_000L
        BleObserverManager.clearRecordsForTest(startTimestampMs = baseTime - 3600_000L, cadenceMs = 60_000L)

        // Simulate 60 readings received every 1 minute
        for (i in 0 until 60) {
            val ts = baseTime - 3600_000L + (i * 60_000L)
            BleObserverManager.addPacketRecordForTest(arrivalMs = ts, readingTimestampMs = ts, rssi = -70)
        }

        val metrics = BleObserverManager.calculateMetrics(baseTime)
        org.junit.Assert.assertEquals(60, metrics.receivedCountLastHour)
        org.junit.Assert.assertEquals(60, metrics.expectedCountLastHour)
        org.junit.Assert.assertEquals(100, metrics.pdrPercent)
        org.junit.Assert.assertEquals(-70, metrics.avgRssi)
        org.junit.Assert.assertEquals(60_000L, BleObserverManager.detectedCadenceIntervalMs)
    }

    @Test
    fun testMetricsCalculation5MinuteCadence() {
        val baseTime = 1_000_000_000L
        BleObserverManager.clearRecordsForTest(startTimestampMs = baseTime - 3600_000L, cadenceMs = 300_000L)

        // Simulate 12 readings received every 5 minutes
        for (i in 0 until 12) {
            val ts = baseTime - 3600_000L + (i * 300_000L)
            BleObserverManager.addPacketRecordForTest(arrivalMs = ts, readingTimestampMs = ts, rssi = -65)
        }

        val metrics = BleObserverManager.calculateMetrics(baseTime)
        org.junit.Assert.assertEquals(12, metrics.receivedCountLastHour)
        org.junit.Assert.assertEquals(12, metrics.expectedCountLastHour)
        org.junit.Assert.assertEquals(100, metrics.pdrPercent)
        org.junit.Assert.assertEquals(-65, metrics.avgRssi)
        org.junit.Assert.assertEquals(300_000L, BleObserverManager.detectedCadenceIntervalMs)
    }

    @Test
    fun testMetricsCalculationPacketLoss() {
        val baseTime = 1_000_000_000L
        BleObserverManager.clearRecordsForTest(startTimestampMs = baseTime - 3600_000L, cadenceMs = 60_000L)

        // Simulate 45 readings received out of 60 (1-min cadence)
        for (i in 0 until 45) {
            val ts = baseTime - 3600_000L + (i * 60_000L)
            BleObserverManager.addPacketRecordForTest(arrivalMs = ts, readingTimestampMs = ts, rssi = -80)
        }

        val metrics = BleObserverManager.calculateMetrics(baseTime)
        org.junit.Assert.assertEquals(45, metrics.receivedCountLastHour)
        org.junit.Assert.assertEquals(60, metrics.expectedCountLastHour)
        org.junit.Assert.assertEquals(75, metrics.pdrPercent)
        org.junit.Assert.assertEquals(-80, metrics.avgRssi)
    }

    @Test
    fun testStaleReadingPacketRejection() {
        val now = 1_795_387_500_000L
        val lastHandled = now - 60_000L // 1 minute ago

        // Older packet than lastHandled (e.g. zombie advertiser from 2 days ago or 5 minutes ago) -> stale
        assertTrue(BleObserverManager.isStaleReadingPacket(now - 2 * 86_400_000L, lastHandled, now))
        assertTrue(BleObserverManager.isStaleReadingPacket(lastHandled - 1_000L, lastHandled, now))

        // Repeat packet from current burst / fallback heartbeat (same timestamp) -> NOT stale
        assertFalse(BleObserverManager.isStaleReadingPacket(lastHandled, lastHandled, now))

        // Newer reading -> NOT stale
        assertFalse(BleObserverManager.isStaleReadingPacket(now, lastHandled, now))

        // Cold start (lastHandled == 0L): reject multi-hour zombie packet, accept fresh packet
        assertTrue(BleObserverManager.isStaleReadingPacket(now - 3 * 3_600_000L, 0L, now))
        assertFalse(BleObserverManager.isStaleReadingPacket(now - 5 * 60_000L, 0L, now))

        // Pure range test ping (timestamp == 0L) -> NOT stale
        assertFalse(BleObserverManager.isStaleReadingPacket(0L, lastHandled, now))
    }
}
