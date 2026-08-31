package com.tirup.app.presentation.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.HeatmapCell
import com.tirup.app.presentation.components.RangeCategoryColor
import com.tirup.app.presentation.theme.DarkBorder
import com.tirup.app.presentation.theme.DarkSurfaceElevated
import com.tirup.app.presentation.theme.TextMutedDark
import com.tirup.app.presentation.theme.TextSecondaryDark

@Composable
fun HeatmapChart(
    daysData: List<List<HeatmapCell>>,
    modifier: Modifier = Modifier
) {
    if (daysData.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceElevated)
            .padding(14.dp)
    ) {
        // Hour labels header (00, 06, 12, 18, 23)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "00:00", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
            Text(text = "06:00", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
            Text(text = "12:00", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
            Text(text = "18:00", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
            Text(text = "23:00", style = MaterialTheme.typography.labelSmall, color = TextMutedDark)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Days rows
        daysData.forEach { dayCells ->
            val dayLabel = dayCells.firstOrNull()?.dayFormatted ?: ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day label
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark,
                    fontSize = 10.sp,
                    modifier = Modifier.width(52.dp)
                )

                // 24 Hour blocks
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    dayCells.forEach { cell ->
                        val cellColor = if (cell.rangeCategory != null) {
                            RangeCategoryColor(cell.rangeCategory)
                        } else {
                            DarkBorder.copy(alpha = 0.4f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cellColor)
                        )
                    }
                }
            }
        }
    }
}
