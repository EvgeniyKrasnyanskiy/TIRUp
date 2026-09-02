package com.tirup.app.domain.model

data class AlertSettings(
    // Tier 1: Predictive (Soft / Умные упреждающие)
    val isPredictiveEnabled: Boolean = true,
    val predictiveMinutesAhead: Int = 15,
    val isPredictiveVibrate: Boolean = true,
    val isPredictiveFlash: Boolean = false,

    // Tier 2: Main (Confirmed 5 points out of range / Основные)
    val isMainEnabled: Boolean = true,
    val mainConsecutivePoints: Int = 5,
    val isMainVibrate: Boolean = true,
    val isMainFlash: Boolean = false,

    // Tier 3: Critical (Prolonged out of range / Затяжные «кричащие»)
    val isCriticalEnabled: Boolean = true,
    val criticalHypoMinutes: Int = 20,
    val criticalHyperMinutes: Int = 90,
    val isCriticalVibrate: Boolean = true,
    val isCriticalFlash: Boolean = true,

    // Snooze / Anti-spam intervals in minutes
    val snoozeHypoMinutes: Int = 15,
    val snoozeHyperMinutes: Int = 45
)
