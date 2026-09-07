package com.tirup.app.domain.model

data class SensorStatus(
    val installedAt: Long = 0L,      // epoch ms, 0 = not set
    val durationDays: Int = 14,       // user-selected lifespan
    val lastUsedDurationDays: Int = 14 // remember last used duration
)

data class PumpSetStatus(
    val installedAt: Long = 0L,
    val durationDays: Int = 3,
    val lastUsedDurationDays: Int = 3
)

// Computed helper extensions
val SensorStatus.expiresAt: Long get() = installedAt + durationDays * 86_400_000L
val SensorStatus.daysRemaining: Int get() {
    if (installedAt == 0L) return -1
    return ((expiresAt - System.currentTimeMillis()) / 86_400_000L).toInt()
}
val SensorStatus.isExpired: Boolean get() = installedAt > 0L && daysRemaining < 0

val PumpSetStatus.expiresAt: Long get() = installedAt + durationDays * 86_400_000L
val PumpSetStatus.daysRemaining: Int get() {
    if (installedAt == 0L) return -1
    return ((expiresAt - System.currentTimeMillis()) / 86_400_000L).toInt()
}
val PumpSetStatus.isExpired: Boolean get() = installedAt > 0L && daysRemaining < 0
