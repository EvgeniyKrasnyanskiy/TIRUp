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
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.daysRemaining
import com.tirup.app.domain.model.isExpired

/**
 * Unified compact status chip for pump infusion set and sensor.
 * Placed in the top header row of FocusScreen, before the StreakBadge.
 */
@Composable
fun DeviceStatusChips(
    sensorStatus: SensorStatus,
    pumpSetStatus: PumpSetStatus,
    showPump: Boolean,
    isRu: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Pump part (if pump user)
            if (showPump) {
                DeviceTextPart(
                    emoji = "▣",
                    daysRemaining = pumpSetStatus.daysRemaining,
                    isSet = pumpSetStatus.installedAt > 0L,
                    isRu = isRu
                )
                
                // Separator
                Text(
                    text = " / ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Sensor part (always shown)
            DeviceTextPart(
                emoji = "◉",
                daysRemaining = sensorStatus.daysRemaining,
                isSet = sensorStatus.installedAt > 0L,
                isRu = isRu
            )
        }
    }
}

@Composable
private fun DeviceTextPart(
    emoji: String,
    daysRemaining: Int,
    isSet: Boolean,
    isRu: Boolean
) {
    val color: Color = when {
        !isSet -> Color(0xFF9CA3AF) // grey
        daysRemaining < 0 -> MaterialTheme.colorScheme.error
        daysRemaining <= 2 -> Color(0xFFF59E0B) // amber
        else -> Color(0xFF22C55E) // green
    }

    val dayLabel = if (isRu) "д" else "d"

    val label = when {
        !isSet -> "?"
        daysRemaining < 0 -> "!"
        else -> "${daysRemaining}$dayLabel"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 19.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
