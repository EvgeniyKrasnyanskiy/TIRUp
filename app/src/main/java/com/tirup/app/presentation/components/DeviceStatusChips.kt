package com.tirup.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.LancetStatus
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.daysRemaining
import com.tirup.app.domain.model.isExpired
import com.tirup.app.domain.model.millisRemaining

/**
 * Unified compact status chip for sensor, pump infusion set, and lancet.
 * Placed in the top header row of FocusScreen, before the StreakBadge.
 */
@Composable
fun DeviceStatusChips(
    sensorStatus: SensorStatus,
    pumpSetStatus: PumpSetStatus,
    lancetStatus: LancetStatus,
    showSensor: Boolean = true,
    showPump: Boolean = true,
    showLancet: Boolean = true,
    isRu: Boolean,
    onClick: () -> Unit
) {
    val items = mutableListOf<@Composable () -> Unit>()

    if (showSensor) {
        items.add {
            DeviceTextPart(
                emoji = "◉",
                installedAt = sensorStatus.installedAt,
                millisRemaining = sensorStatus.millisRemaining,
                daysRemaining = sensorStatus.daysRemaining,
                isRu = isRu
            )
        }
    }

    if (showPump) {
        items.add {
            DeviceTextPart(
                emoji = "▣",
                installedAt = pumpSetStatus.installedAt,
                millisRemaining = pumpSetStatus.millisRemaining,
                daysRemaining = pumpSetStatus.daysRemaining,
                isRu = isRu
            )
        }
    }

    if (showLancet) {
        items.add {
            DeviceTextPart(
                emoji = "📍",
                installedAt = lancetStatus.installedAt,
                millisRemaining = lancetStatus.millisRemaining,
                daysRemaining = lancetStatus.daysRemaining,
                isRu = isRu
            )
        }
    }

    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            items.forEachIndexed { index, itemComposable ->
                if (index > 0) {
                    Text(
                        text = " / ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
                itemComposable()
            }
        }
    }
}

@Composable
private fun DeviceTextPart(
    emoji: String,
    installedAt: Long,
    millisRemaining: Long,
    daysRemaining: Int,
    isRu: Boolean
) {
    val isSet = installedAt > 0L
    val isExpired = isSet && millisRemaining <= 0L

    val color: Color = when {
        !isSet -> Color(0xFF9CA3AF) // grey
        isExpired -> MaterialTheme.colorScheme.error // red
        millisRemaining <= 24 * 3600_000L -> Color(0xFFF59E0B) // amber when <= 24h
        else -> Color(0xFF22C55E) // green when > 24h
    }

    val dayLabel = if (isRu) "д" else "d"
    val hourLabel = if (isRu) "ч" else "h"
    val minLabel = if (isRu) "м" else "m"

    val label = when {
        !isSet -> "?"
        isExpired -> {
            val expiredHours = (-millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
            "-${expiredHours}$hourLabel"
        }
        millisRemaining < 3600_000L -> {
            val mins = (millisRemaining / 60_000L).toInt().coerceAtLeast(1)
            "${mins}$minLabel"
        }
        millisRemaining < 24 * 3600_000L -> {
            val hours = (millisRemaining / 3600_000L).toInt().coerceAtLeast(1)
            "${hours}$hourLabel"
        }
        else -> "${daysRemaining}$dayLabel"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 12.sp)
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
