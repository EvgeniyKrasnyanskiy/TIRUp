package com.tirup.app.domain.model

enum class AodDisplayMode(val labelRu: String, val labelEn: String) {
    PULSE_ON_UPDATE(
        "Просыпаться при обновлении (0% батареи)",
        "Wake on glucose update (0% battery)"
    ),
    ALWAYS_ON(
        "Всегда включен (1% яркости)",
        "Always on (1% brightness)"
    )
}

data class AodSettings(
    val isEnabled: Boolean = false,
    val displayMode: AodDisplayMode = AodDisplayMode.PULSE_ON_UPDATE,
    val pulseDurationSeconds: Int = 5,
    val autoChargeEnabled: Boolean = false,
    val autoChargeStartHour: Int = 23,
    val autoChargeStartMinute: Int = 0,
    val autoChargeEndHour: Int = 7,
    val autoChargeEndMinute: Int = 0,
    val customBrightness: Float = 0.01f
)
