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

/**
 * Unified formatter for remaining lifespan of CGM sensors, infusion sets, and lancets.
 * Avoids single-day rounding gap (24h..48h) by displaying decimals (e.g. 1.5d) or hours (1d 12h).
 */
fun formatDeviceRemainingTime(
    millisRemaining: Long,
    installedAt: Long,
    isRu: Boolean,
    isCompact: Boolean = false
): String {
    if (installedAt <= 0L) {
        return if (isCompact) "?" else if (isRu) "Нет данных" else "No data"
    }
    if (millisRemaining <= 0L) {
        val expiredHours = (-millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
        val hUnit = if (isRu) "ч" else "h"
        return if (isCompact) "-$expiredHours$hUnit"
        else if (isRu) "Просрочен: -$expiredHours ч" else "Expired: -$expiredHours h"
    }
    val mUnit = if (isRu) "м" else "m"
    val hUnit = if (isRu) "ч" else "h"
    val dUnit = if (isRu) "д" else "d"

    return when {
        millisRemaining < 3600_000L -> {
            val mins = (millisRemaining / 60_000L).toInt().coerceAtLeast(1)
            if (isCompact) "$mins$mUnit"
            else if (isRu) "Осталось: $mins мин" else "Remaining: $mins min"
        }
        millisRemaining < 24 * 3600_000L -> {
            val hours = (millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
            if (isCompact) "$hours$hUnit"
            else if (isRu) "Осталось: $hours ч" else "Remaining: $hours h"
        }
        millisRemaining < 48 * 3600_000L -> {
            val totalHours = millisRemaining / 3600_000.0
            val daysFrac = totalHours / 24.0
            val roundedFrac = kotlin.math.round(daysFrac * 10.0) / 10.0
            if (isCompact) {
                java.lang.String.format(java.util.Locale.US, "%.1f$dUnit", roundedFrac)
            } else {
                val remHours = (totalHours.toInt() % 24)
                if (isRu) "Осталось: 1д $remHours ч" else "Remaining: 1d $remHours h"
            }
        }
        else -> {
            val days = (millisRemaining / 86_400_000L).toInt()
            if (isCompact) "$days$dUnit"
            else if (isRu) "Осталось: $days дн." else "Remaining: $days d"
        }
    }
}
