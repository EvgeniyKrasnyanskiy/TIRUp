package com.tirup.app.presentation.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.presentation.settings.YearEndStats
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.util.Calendar
import java.util.Locale

@Composable
fun YearEndDigestDialog(
    stats: YearEndStats?,
    isRu: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    onExportPdf: (YearEndStats) -> Unit,
    onArchiveYear: (Int) -> Unit,
    onYearChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val year = stats?.year ?: currentYear

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🎄", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Итоги $year года с TIRUp" else "Your $year Year with TIRUp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Годовой дайджест и ротация архива" else "Year-end digest & archive rotation",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryEmerald
                        )
                    }
                }

                // Year selector [ ◀ 2025 | 2026 ▶ ]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onYearChange(year - 1) },
                            enabled = year > 2025,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("◀", color = if (year > 2025) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = "$year",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { onYearChange(year + 1) },
                            enabled = year < currentYear,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("▶", color = if (year < currentYear) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info banner about automatic archive Dec 31 20:00
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ActionBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "ℹ️", fontSize = 14.sp)
                        Text(
                            text = if (isRu)
                                "Итоги года и архив автоматически создаются 31 декабря в 20:00. Если смартфон был выключен, отчёт сформируется при первом включении устройства."
                            else
                                "Year-end digest and archive are automatically created on Dec 31 at 20:00. If the device was off, it will generate on next startup.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (stats == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryEmerald)
                    }
                } else if (stats.totalReadings == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📅",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRu) "За $year год ещё нет сохранённых измерений в базе данных."
                                   else "No saved readings found for year $year in database.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Hero Card: TIR
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.2.dp, PrimaryEmerald.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", stats.tirPercent),
                                    style = TextStyle(
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald
                                    )
                                )
                                Text(
                                    text = if (isRu) "Время в норме (3.9 — 10.0 ммоль/л)" else "Time in Range (3.9 — 10.0 mmol/L)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (stats.tirPercent >= 70.0) {
                                        if (isRu) "🎯 Международная цель ADA (≥70%) достигнута!" else "🎯 Target ADA goal (≥70%) achieved!"
                                    } else {
                                        if (isRu) "Целевой клинический ориентир: ≥70%" else "Clinical target benchmark: ≥70%"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (stats.tirPercent >= 70.0) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Key Glycemic Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f", stats.meanGlucoseMmol),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRu) "Ср. сахар" else "Mean BG",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue
                                    )
                                    Text(
                                        text = if (isRu) "GMI (HbA1c)" else "GMI (HbA1c)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", stats.tbrPercent),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stats.tbrPercent <= 4.0) PrimaryEmerald else Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = if (isRu) "Гипо (<3.9)" else "Low (<3.9)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Achievements List
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val coveragePct = (stats.monitoringDays.toDouble() / 365.0 * 100.0).coerceAtMost(100.0)
                                Text(
                                    text = "📅 ${if (isRu) "Мониторинг:" else "Active CGM:"} ${stats.monitoringDays} / 365 ${if (isRu) "дней" else "days"} (${String.format(Locale.US, "%.1f%%", coveragePct)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "📈 ${if (isRu) "Всего замеров:" else "Total readings:"} ${stats.totalReadings} ${if (isRu) "точек" else "points"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (stats.bestMonthName.isNotBlank()) {
                                    Text(
                                        text = "🏆 ${if (isRu) "Лучший месяц:" else "Best month:"} ${stats.bestMonthName} (${String.format(Locale.US, "%.1f%%", stats.bestMonthTir)} TIR)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryEmerald
                                    )
                                }
                                if (stats.bestStreakDays > 0) {
                                    Text(
                                        text = "🔥 ${if (isRu) "Рекордная серия:" else "Longest streak:"} ${stats.bestStreakDays} ${if (isRu) "дн. без выраженной гипо" else "days without severe low"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Archive Status & Action
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (stats.isArchived) PrimaryEmerald.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, if (stats.isArchived) PrimaryEmerald.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (stats.isArchived) {
                                            if (isRu) "✓ Год сохранён в архиве" else "✓ Year saved in archive"
                                        } else {
                                            if (isRu) "Годовой архив ещё не создан" else "Annual archive not created yet"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (stats.isArchived) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "tirup_readings_${stats.year}.csv",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onArchiveYear(stats.year) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.7f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (stats.isArchived) {
                                            if (isRu) "Обновить" else "Update"
                                        } else {
                                            if (isRu) "В архив" else "Archive"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ActionBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (snackbarHostState != null) {
                    SnackbarHost(hostState = snackbarHostState)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stats != null && stats.totalReadings > 0) {
                        OutlinedButton(
                            onClick = { onExportPdf(stats) },
                            border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = ActionBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Открытка в PDF" else "Save PDF",
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (isRu) "Закрыть" else "Close",
                            color = ActionBlue
                        )
                    }
                }
            }
        },
        dismissButton = null
    )
}
