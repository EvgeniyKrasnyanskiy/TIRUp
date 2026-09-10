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

data class LancetStatus(
    val installedAt: Long = 0L,
    val durationDays: Int = 7,
    val lastUsedDurationDays: Int = 7
)

// Computed helper extensions
val SensorStatus.expiresAt: Long get() = installedAt + durationDays * 86_400_000L
val SensorStatus.millisRemaining: Long get() {
    if (installedAt == 0L) return 0L
    return expiresAt - System.currentTimeMillis()
}
val SensorStatus.daysRemaining: Int get() {
    if (installedAt == 0L) return -1
    return (millisRemaining / 86_400_000L).toInt()
}
val SensorStatus.isExpired: Boolean get() = installedAt > 0L && millisRemaining <= 0L

val PumpSetStatus.expiresAt: Long get() = installedAt + durationDays * 86_400_000L
val PumpSetStatus.millisRemaining: Long get() {
    if (installedAt == 0L) return 0L
    return expiresAt - System.currentTimeMillis()
}
val PumpSetStatus.daysRemaining: Int get() {
    if (installedAt == 0L) return -1
    return (millisRemaining / 86_400_000L).toInt()
}
val PumpSetStatus.isExpired: Boolean get() = installedAt > 0L && millisRemaining <= 0L

val LancetStatus.expiresAt: Long get() = installedAt + durationDays * 86_400_000L
val LancetStatus.millisRemaining: Long get() {
    if (installedAt == 0L) return 0L
    return expiresAt - System.currentTimeMillis()
}
val LancetStatus.daysRemaining: Int get() {
    if (installedAt == 0L) return -1
    return (millisRemaining / 86_400_000L).toInt()
}
val LancetStatus.isExpired: Boolean get() = installedAt > 0L && millisRemaining <= 0L
