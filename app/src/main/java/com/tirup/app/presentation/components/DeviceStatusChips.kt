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
 * Compact status chips for sensor and (optionally) pump infusion set.
 * Placed in the top header row of FocusScreen, before the StreakBadge.
 */
@Composable
fun DeviceStatusChips(
    sensorStatus: SensorStatus,
    pumpSetStatus: PumpSetStatus,
    showPump: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        // Sensor chip — always shown
        DeviceChip(
            emoji = "📡",
            daysRemaining = sensorStatus.daysRemaining,
            isSet = sensorStatus.installedAt > 0L
        )

        // Pump chip — only if pump user
        if (showPump) {
            Spacer(Modifier.width(4.dp))
            DeviceChip(
                emoji = "💉",
                daysRemaining = pumpSetStatus.daysRemaining,
                isSet = pumpSetStatus.installedAt > 0L
            )
        }
    }
}

@Composable
private fun DeviceChip(
    emoji: String,
    daysRemaining: Int,
    isSet: Boolean
) {
    val color: Color = when {
        !isSet -> Color(0xFF9CA3AF) // grey
        daysRemaining < 0 -> MaterialTheme.colorScheme.error
        daysRemaining <= 2 -> Color(0xFFF59E0B) // amber
        else -> Color(0xFF22C55E) // green
    }

    val label = when {
        !isSet -> "?"
        daysRemaining < 0 -> "!"
        else -> "${daysRemaining}d"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 12.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
