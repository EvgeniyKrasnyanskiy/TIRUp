package com.tirup.app.domain.model

enum class MasterBatterySource {
    BLE,
    WIFI_LAN,
    NIGHTSCOUT
}

data class ResolvedMasterBattery(
    val percent: Int,
    val timestamp: Long,
    val source: MasterBatterySource,
    val isStale: Boolean
)

object MasterBatteryResolver {

    /**
     * Resolves the master device battery percentage from all available telemetry channels
     * (BLE Observer, xDrip Wi-Fi LAN, and micro-Nightscout REST API).
     *
     * Prioritizes the freshest and most direct signal:
     * 1. BLE Observer broadcast (if received within 7 minutes)
     * 2. xDrip Wi-Fi LAN follower (if received within 15 minutes)
     * 3. Micro-Nightscout server devicestatus (if received within 30 minutes)
     */
    fun resolve(
        userSettings: UserSettings,
        lanStatus: XdripLanStatus? = null,
        nightscoutStatus: NightscoutStatus? = null,
        now: Long = System.currentTimeMillis()
    ): ResolvedMasterBattery? {
        val candidates = mutableListOf<ResolvedMasterBattery>()

        // 1. BLE Observer
        val ble = userSettings.bleBridgeSettings
        if (ble.role == BleBridgeRole.OBSERVER && ble.lastMasterBattery in 0..100) {
            val ageMins = if (ble.lastPacketTimestamp > 0L) (now - ble.lastPacketTimestamp) / 60_000L else 999L
            if (ageMins < 120L) {
                candidates.add(
                    ResolvedMasterBattery(
                        percent = ble.lastMasterBattery,
                        timestamp = ble.lastPacketTimestamp,
                        source = MasterBatterySource.BLE,
                        isStale = ageMins > 7L
                    )
                )
            }
        }

        // 2. Wi-Fi LAN Follower
        val lan = userSettings.xdripLanSettings
        val lanBat = lanStatus?.masterBattery
        if (lan.isEnabled && lanBat != null && lanBat in 0..100) {
            val ts = lanStatus.lastSuccessTimestamp
            val ageMins = if (ts > 0L) (now - ts) / 60_000L else 999L
            if (ageMins < 120L) {
                candidates.add(
                    ResolvedMasterBattery(
                        percent = lanBat,
                        timestamp = ts,
                        source = MasterBatterySource.WIFI_LAN,
                        isStale = ageMins > 15L
                    )
                )
            }
        }

        // 3. Micro-Nightscout Server
        val ns = userSettings.nightscoutSettings
        val nsBat = nightscoutStatus?.masterBattery
        if (ns.isEnabled && ns.isValidUrl && nsBat != null && nsBat in 0..100) {
            val ts = if (nightscoutStatus.lastBatteryTimestamp > 0L) {
                nightscoutStatus.lastBatteryTimestamp
            } else {
                nightscoutStatus.lastSuccessTimestamp
            }
            val ageMins = if (ts > 0L) (now - ts) / 60_000L else 999L
            if (ageMins < 120L) {
                candidates.add(
                    ResolvedMasterBattery(
                        percent = nsBat,
                        timestamp = ts,
                        source = MasterBatterySource.NIGHTSCOUT,
                        isStale = ageMins > 30L
                    )
                )
            }
        }

        if (candidates.isEmpty()) return null

        // First look for non-stale candidates by priority (BLE -> LAN -> NIGHTSCOUT)
        val freshCandidate = candidates.firstOrNull { !it.isStale }
        if (freshCandidate != null) return freshCandidate

        // If all are stale, pick the one with the freshest timestamp
        return candidates.maxByOrNull { it.timestamp }
    }
}
