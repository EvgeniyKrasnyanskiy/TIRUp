package com.tirup.app.domain.model

enum class BleBridgeRole {
    DISABLED,     // Отключено
    BROADCASTER,  // Вещатель (Мастер - смартфон с сенсором)
    OBSERVER      // Приёмник (Фолловер - родитель / наблюдатель)
}

data class BleBridgeSettings(
    val role: BleBridgeRole = BleBridgeRole.DISABLED,
    val isEnabled: Boolean = true,
    val familyPin: String = "",
    val transmitBattery: Boolean = true,
    val lastPacketTimestamp: Long = 0L,
    val lastRssi: Int = 0,
    val lastMasterBattery: Int = -1
)

data class BleGlucosePacket(
    val timestamp: Long,
    val valueMmol: Double,
    val trendArrow: String,
    val rateOfChangeMmolPerMin: Double,
    val iob: Double,
    val batteryPercent: Int,
    val cob: Double = 0.0
)
