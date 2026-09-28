package com.tirup.app.domain.model

enum class LanConnectionState {
    DISABLED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class XdripLanStatus(
    val state: LanConnectionState = LanConnectionState.DISABLED,
    val masterIp: String = "",
    val masterBattery: Int? = null,
    val lastSuccessTimestamp: Long = 0L,
    val lastGlucoseMmol: Double? = null,
    val lastIob: Double? = null,
    val lastCob: Double? = null,
    val lastTrendArrow: String? = null,
    val errorMessage: String? = null,
    val isWifiConnected: Boolean = true
) {
    val isOnline: Boolean
        get() = state == LanConnectionState.CONNECTED && (System.currentTimeMillis() - lastSuccessTimestamp < 15 * 60_000L)
}

data class XdripLanTestResult(
    val isSuccess: Boolean,
    val responseTimeMs: Long = 0L,
    val masterBattery: Int? = null,
    val glucoseMmol: Double? = null,
    val trendArrow: String? = null,
    val iob: Double? = null,
    val cob: Double? = null,
    val message: String = ""
)
