package com.tirup.app.domain.model

import java.util.concurrent.atomic.AtomicLongArray

enum class DataSourcePriority(val rank: Int) {
    LOCAL_XDRIP(4),      // Прямой broadcast от локального xDrip+ / GDH на этом же устройстве (наивысший)
    WIFI_LAN(3),         // Прямой опрос мастера по локальной сети Wi-Fi / Hotspot
    NIGHTSCOUT_CLOUD(2), // Опрос облачного сервера Nightscout через интернет
    BLE_BRIDGE(1);       // Прямой локальный радиопакет от аппаратного BLE радиомоста (резервный)

    companion object {
        fun isHigherOrEqual(p1: DataSourcePriority, p2: DataSourcePriority): Boolean {
            return p1.rank >= p2.rank
        }
    }
}

/**
 * Thread-safe in-memory tracker of the latest reading timestamp delivered by each data source.
 * Prevents lower-priority channels (e.g. BLE_BRIDGE) from suppressing higher-priority polling
 * (e.g. WIFI_LAN or NIGHTSCOUT_CLOUD) when deciding whether fresh data is already available.
 */
object DataSourceFreshnessTracker {

    private val latestTimestamps = AtomicLongArray(DataSourcePriority.entries.size)

    fun recordReading(priority: DataSourcePriority, readingTimestampMs: Long) {
        if (readingTimestampMs <= 0L) return
        val idx = priority.ordinal
        while (true) {
            val prev = latestTimestamps.get(idx)
            if (readingTimestampMs <= prev) return
            if (latestTimestamps.compareAndSet(idx, prev, readingTimestampMs)) return
        }
    }

    fun latestTimestampFor(priority: DataSourcePriority): Long = latestTimestamps.get(priority.ordinal)

    /**
     * Returns the newest reading timestamp delivered by any source with strictly higher rank than [forPriority].
     */
    fun latestHigherPriorityTimestamp(forPriority: DataSourcePriority): Long {
        var maxTs = 0L
        for (source in DataSourcePriority.entries) {
            if (source.rank > forPriority.rank) {
                val ts = latestTimestamps.get(source.ordinal)
                if (ts > maxTs) maxTs = ts
            }
        }
        return maxTs
    }

    fun hasFreshHigherPriorityReading(
        forPriority: DataSourcePriority,
        nowMs: Long,
        maxAgeMs: Long
    ): Boolean {
        val higherTs = latestHigherPriorityTimestamp(forPriority)
        return higherTs > 0L && (nowMs - higherTs) < maxAgeMs
    }

    internal fun resetForTest() {
        for (i in 0 until latestTimestamps.length()) {
            latestTimestamps.set(i, 0L)
        }
    }
}
