package com.tirup.app.data.network

import com.tirup.app.domain.model.DataSourcePriority
import com.tirup.app.domain.model.LanConnectionState
import com.tirup.app.domain.model.XdripLanStatus
import com.tirup.app.domain.model.XdripLanTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripLanClientTest {

    @Test
    fun dataSourcePriority_ordersCorrectly() {
        assertTrue(DataSourcePriority.LOCAL_XDRIP.rank > DataSourcePriority.BLE_BRIDGE.rank)
        assertTrue(DataSourcePriority.BLE_BRIDGE.rank > DataSourcePriority.WIFI_LAN.rank)
        assertTrue(DataSourcePriority.WIFI_LAN.rank > DataSourcePriority.NIGHTSCOUT_CLOUD.rank)

        assertTrue(DataSourcePriority.isHigherOrEqual(DataSourcePriority.LOCAL_XDRIP, DataSourcePriority.WIFI_LAN))
        assertTrue(DataSourcePriority.isHigherOrEqual(DataSourcePriority.WIFI_LAN, DataSourcePriority.NIGHTSCOUT_CLOUD))
    }

    @Test
    fun xdripLanStatus_isOnline_evaluatesBasedOnStateAndFreshness() {
        val now = System.currentTimeMillis()

        // 1. Fresh connected status -> isOnline = true
        val freshStatus = XdripLanStatus(
            state = LanConnectionState.CONNECTED,
            lastSuccessTimestamp = now - 60_000L
        )
        assertTrue(freshStatus.isOnline)

        // 2. Stale connected status (> 15 min) -> isOnline = false
        val staleStatus = XdripLanStatus(
            state = LanConnectionState.CONNECTED,
            lastSuccessTimestamp = now - 20 * 60_000L
        )
        assertFalse(staleStatus.isOnline)

        // 3. Error state -> isOnline = false
        val errorStatus = XdripLanStatus(
            state = LanConnectionState.ERROR,
            lastSuccessTimestamp = now
        )
        assertFalse(errorStatus.isOnline)

        // 4. Disabled state -> isOnline = false
        val disabledStatus = XdripLanStatus(
            state = LanConnectionState.DISABLED
        )
        assertFalse(disabledStatus.isOnline)
    }

    @Test
    fun xdripLanTestResult_storesFieldsCorrectly() {
        val testResult = XdripLanTestResult(
            isSuccess = true,
            responseTimeMs = 45L,
            masterBattery = 88,
            glucoseMmol = 6.4,
            trendArrow = "→",
            iob = 1.2,
            cob = 10.0,
            message = "Связь установлена"
        )

        assertTrue(testResult.isSuccess)
        assertEquals(45L, testResult.responseTimeMs)
        assertEquals(88, testResult.masterBattery)
        assertEquals(6.4, testResult.glucoseMmol!!, 0.001)
        assertEquals("→", testResult.trendArrow)
        assertEquals(1.2, testResult.iob!!, 0.001)
        assertEquals(10.0, testResult.cob!!, 0.001)
        assertEquals("Связь установлена", testResult.message)
    }

    @Test
    fun calculateNextPollIntervalMs_adaptsBasedOnSensorCadence() {
        val now = System.currentTimeMillis()
        val configuredSec = 30

        // 1. No readings yet -> returns configured
        assertEquals(30_000L, XdripLanManager.calculateNextPollIntervalMs(0L, configuredSec))

        // 2. Just received (< 20s) -> rest 35s to save CPU
        val justReceived = now - 5_000L
        assertEquals(35_000L, XdripLanManager.calculateNextPollIntervalMs(justReceived, configuredSec))

        // 3. Approaching 1-min mark (20s..75s) -> poll rapidly 15s
        val near1Min = now - 45_000L
        assertEquals(15_000L, XdripLanManager.calculateNextPollIntervalMs(near1Min, configuredSec))

        // 4. In between 1m and 4.2m (75s..250s) -> 5-min sensor, sleep 60s
        val between1And4Min = now - 120_000L
        assertEquals(60_000L, XdripLanManager.calculateNextPollIntervalMs(between1And4Min, configuredSec))

        // 5. Approaching 5-min mark (250s..330s) -> poll rapidly 15s
        val near5Min = now - 280_000L
        assertEquals(15_000L, XdripLanManager.calculateNextPollIntervalMs(near5Min, configuredSec))

        // 6. Signal loss / warmup (> 10 min) -> backoff 60s
        val signalLost = now - 15 * 60_000L
        assertEquals(60_000L, XdripLanManager.calculateNextPollIntervalMs(signalLost, configuredSec))
    }
}
