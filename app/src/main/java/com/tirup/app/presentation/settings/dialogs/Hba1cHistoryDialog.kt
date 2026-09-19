package com.tirup.app.presentation.settings.dialogs

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.domain.model.LabHba1cRecord
import com.tirup.app.presentation.reports.openSavedFileFolder
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun Hba1cHistoryDialog(
    records: List<LabHba1cRecord>,
    sensorGmi90d: Double?,
    meanGlucose90dMmol: Double?,
    tirPercent90d: Int?,
    skippedQuarterTimestamp: Long,
    isRu: Boolean,
    onAddRecord: (value: Double, timestamp: Long, lab: String, notes: String) -> Unit,
    onDeleteRecord: (id: Long) -> Unit,
    onSkipQuarter: () -> Unit,
    onExportPdf: (((String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var valueText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())) }
    var labText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmId by remember { mutableStateOf<Long?>(null) }

    val localSnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var lastExportedPath by remember { mutableStateOf<String?>(null) }

    val openDatePicker = {
        val calendar = Calendar.getInstance()
        val currentParsed = try {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText.trim())
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateText.trim())
        } catch (_: Exception) { null }
        if (currentParsed != null) {
            calendar.time = currentParsed
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, y, m, d ->
                dateText = String.format(Locale.getDefault(), "%02d.%02d.%04d", d, m + 1, y)
                inputError = null
            },
            year,
            month,
            day
        ).show()
    }

    val sortedRecords = remember(records) { records.sortedByDescending { it.timestamp } }
    val latestRecord = sortedRecords.firstOrNull()
    val scrollState = rememberScrollState()

    val latestRecordTime = records.maxOfOrNull { it.timestamp } ?: 0L
    val baseTime = maxOf(latestRecordTime, skippedQuarterTimestamp)
    val now = System.currentTimeMillis()
    val elapsedDays = if (baseTime > 0L) ((now - baseTime) / 86_400_000L).toInt() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Журнал HbA1c" else "HbA1c Journal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Clinical Comparison Card (90 days)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isRu) "Клинический рубеж (90 дней)" else "Clinical Horizon (90 days)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRu) "Сенсорный GMI" else "Sensor GMI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (sensorGmi90d != null) String.format(Locale.US, "%.1f%%", sensorGmi90d) else "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (meanGlucose90dMmol != null && tirPercent90d != null) {
                                    Text(
                                        text = if (isRu) {
                                            String.format(Locale.US, "ср. %.1f • TIR %d%%", meanGlucose90dMmol, tirPercent90d)
                                        } else {
                                            String.format(Locale.US, "mean %.1f • TIR %d%%", meanGlucose90dMmol, tirPercent90d)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isRu) "Лаб. HbA1c" else "Lab HbA1c",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (latestRecord != null) String.format(Locale.US, "%.1f%%", latestRecord.valuePercent) else "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                if (latestRecord != null) {
                                    val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(latestRecord.timestamp))
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (sensorGmi90d != null && latestRecord != null) {
                            val delta = latestRecord.valuePercent - sensorGmi90d
                            val deltaSign = if (delta > 0) "+" else ""
                            val absDelta = abs(delta)

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (absDelta <= 0.4) PrimaryEmerald.copy(alpha = 0.15f) else ColorHigh.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, if (absDelta <= 0.4) PrimaryEmerald else ColorHigh)
                                ) {
                                    Text(
                                        text = "Δ ${deltaSign}${String.format(Locale.US, "%.1f%%", delta)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (absDelta <= 0.4) PrimaryEmerald else ColorHigh,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = when {
                                        absDelta <= 0.4 -> if (isRu) "Отличная сходимость сенсора и лаборатории" else "Excellent correlation between sensor and lab"
                                        delta > 0.4 -> if (isRu) "Лаб. выше GMI (возможны постпрандиальные пики)" else "Lab higher than GMI (check postprandials)"
                                        else -> if (isRu) "GMI выше лаб. (проверьте калибровку сенсора)" else "GMI higher than lab (check sensor calibration)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        if (elapsedDays != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Квартальный рубеж: прошло $elapsedDays дн. из 90"
                                               else "Quarterly milestone: $elapsedDays of 90 days",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (elapsedDays >= 90) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (elapsedDays >= 90) {
                                        Text(
                                            text = if (isRu) "Не планируете сдавать сейчас?"
                                                   else "Not taking a test now?",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (elapsedDays >= 90) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = onSkipQuarter,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isRu) "Пропустить (+90д)" else "Skip (+90d)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Add New Record Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isRu) "Внести результат анализа" else "Add Test Result",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = valueText,
                                onValueChange = {
                                    valueText = it
                                    inputError = null
                                },
                                label = { Text("HbA1c %") },
                                placeholder = { Text("6.4", color = placeholderColor) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            Box(modifier = Modifier.weight(1.3f)) {
                                OutlinedTextField(
                                    value = dateText,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (isRu) "Дата" else "Date") },
                                    placeholder = { Text("14.09.2026", color = placeholderColor) },
                                    trailingIcon = {
                                        IconButton(onClick = openDatePicker) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = if (isRu) "Выбрать дату" else "Pick date",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(onClick = openDatePicker)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = labText,
                            onValueChange = { labText = it },
                            label = { Text(if (isRu) "Лаборатория (необязательно)" else "Laboratory (optional)") },
                            placeholder = { Text(if (isRu) "Инвитро, Гемотест..." else "Lab name", color = placeholderColor) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text(if (isRu) "Заметка (необязательно)" else "Note (optional)") },
                            placeholder = { Text(if (isRu) "Натощак / плановый контроль" else "Routine check", color = placeholderColor) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (inputError != null) {
                            Text(
                                text = inputError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                val parsedVal = valueText.trim().replace(',', '.').toDoubleOrNull()
                                if (parsedVal == null || parsedVal < 3.0 || parsedVal > 20.0) {
                                    inputError = if (isRu) "Введите значение от 3.0 до 20.0%" else "Enter value between 3.0 and 20.0%"
                                    return@Button
                                }
                                val parsedDate = try {
                                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText.trim())?.time
                                        ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateText.trim())?.time
                                        ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateText.trim())?.time
                                        ?: SimpleDateFormat("dd.MM.yy", Locale.getDefault()).parse(dateText.trim())?.time
                                } catch (_: Exception) {
                                    null
                                }
                                if (parsedDate == null) {
                                    inputError = if (isRu) "Укажите корректную дату (например, 14.09.2026)" else "Enter a valid date (e.g., 14.09.2026)"
                                    return@Button
                                }

                                onAddRecord(parsedVal, parsedDate, labText.trim(), notesText.trim())
                                valueText = ""
                                dateText = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                                labText = ""
                                notesText = ""
                                inputError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isRu) "Сохранить анализ" else "Save Result")
                        }
                    }
                }

                // 3. History Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isRu) "История анализов (${records.size})" else "Test History (${records.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (sortedRecords.isEmpty()) {
                        Text(
                            text = if (isRu) "Нет сохранённых анализов. Внесите данные выше." else "No records saved. Enter data above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sortedRecords.forEach { record ->
                                    val recordDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(record.timestamp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val badgeColor = when {
                                                    record.valuePercent < 6.1 -> ColorTight
                                                    record.valuePercent <= 7.0 -> PrimaryEmerald
                                                    record.valuePercent <= 8.0 -> ColorHigh
                                                    else -> ColorVeryLow
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                                                    color = badgeColor.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.1f%%", record.valuePercent),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = badgeColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = recordDate + if (record.labName.isNotBlank()) " • ${record.labName}" else "",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (record.notes.isNotBlank()) {
                                                        Text(
                                                            text = record.notes,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = { showDeleteConfirmId = record.id },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
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
                SnackbarHost(hostState = localSnackbarHostState)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isExporting && onExportPdf != null) {
                                isExporting = true
                                onExportPdf { savedPath ->
                                    isExporting = false
                                    lastExportedPath = savedPath
                                    val fileName = savedPath.substringAfterLast('/')
                                    val msg = if (isRu) "Выписка сохранена в Загрузки: $fileName" else "Report saved to Downloads: $fileName"
                                    coroutineScope.launch {
                                        val actionLabel = if (isRu) "Открыть" else "Open"
                                        val result = localSnackbarHostState.showSnackbar(
                                            message = msg,
                                            actionLabel = actionLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            openSavedFileFolder(context, savedPath)
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isExporting && onExportPdf != null,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ActionBlue)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ActionBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isRu) "Экспорт..." else "Exporting...", color = ActionBlue)
                        } else {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isRu) "Выписка PDF" else "PDF Report", color = ActionBlue)
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = if (isRu) "Закрыть" else "Close", color = ActionBlue)
                    }
                }
            }
        },
        dismissButton = null
    )

    if (showDeleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmId = null },
            title = { Text(if (isRu) "Удалить анализ?" else "Delete Record?") },
            text = { Text(if (isRu) "Вы уверены, что хотите удалить эту запись из журнала?" else "Are you sure you want to delete this record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = showDeleteConfirmId
                        if (id != null) {
                            onDeleteRecord(id)
                        }
                        showDeleteConfirmId = null
                    }
                ) {
                    Text(text = if (isRu) "Удалить" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmId = null }) {
                    Text(text = if (isRu) "Отмена" else "Cancel")
                }
            }
        )
    }
}
