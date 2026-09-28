package com.tirup.app.presentation.focus

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TreatmentPresetsManager {
    private const val PREFS_NAME = "treatment_input_presets"
    private const val KEY_INSULIN = "recent_insulin"
    private const val KEY_CARBS = "recent_carbs"

    private val DEFAULT_INSULIN = listOf(0.5, 1.0, 2.0, 3.0, 5.0)
    private val DEFAULT_CARBS = listOf(10.0, 15.0, 20.0, 30.0, 50.0)

    fun getRecentInsulin(context: Context): List<Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_INSULIN, null) ?: return DEFAULT_INSULIN
        val parsed = raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0.0 }.distinct().take(5)
        return if (parsed.isNotEmpty()) parsed else DEFAULT_INSULIN
    }

    fun getRecentCarbs(context: Context): List<Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CARBS, null) ?: return DEFAULT_CARBS
        val parsed = raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0.0 }.distinct().take(5)
        return if (parsed.isNotEmpty()) parsed else DEFAULT_CARBS
    }

    fun recordInsulin(context: Context, dose: Double) {
        if (dose <= 0.0) return
        val current = getRecentInsulin(context).toMutableList()
        current.remove(dose)
        current.add(0, dose)
        val updated = current.distinct().take(5)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_INSULIN, updated.joinToString(","))
            .apply()
    }

    fun recordCarbs(context: Context, carbs: Double) {
        if (carbs <= 0.0) return
        val current = getRecentCarbs(context).toMutableList()
        current.remove(carbs)
        current.add(0, carbs)
        val updated = current.distinct().take(5)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CARBS, updated.joinToString(","))
            .apply()
    }
}

enum class TreatmentInputType {
    NOTE,
    CARBS,
    INSULIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentInputBottomSheet(
    initialType: TreatmentInputType,
    @Suppress("UNUSED_PARAMETER") unit: GlucoseUnit,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (insulin: Double?, carbs: Double?, bg: Double?, notes: String?, timestamp: Long) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(initialType) }

    var insulinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var customTimestamp by remember { mutableStateOf<Long?>(null) }

    val recentInsulin = remember(context) { TreatmentPresetsManager.getRecentInsulin(context) }
    val recentCarbs = remember(context) { TreatmentPresetsManager.getRecentCarbs(context) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val quickNotes = remember(isRu) {
        if (isRu) {
            listOf("Завтрак", "Обед", "Ужин", "Канюля", "Ланцет", "Сенсор")
        } else {
            listOf("Breakfast", "Lunch", "Dinner", "Cannula", "Lancet", "Sensor")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row with Type Switcher & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini segmented switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TreatmentTabChip(
                        icon = "💬",
                        label = if (isRu) "Заметки" else "Notes",
                        isSelected = selectedType == TreatmentInputType.NOTE,
                        activeColor = Color(0xFF8B5CF6),
                        onClick = { selectedType = TreatmentInputType.NOTE }
                    )
                    TreatmentTabChip(
                        icon = "🍞",
                        label = if (isRu) "Углеводы" else "Carbs",
                        isSelected = selectedType == TreatmentInputType.CARBS,
                        activeColor = ColorHigh,
                        onClick = { selectedType = TreatmentInputType.CARBS }
                    )
                    TreatmentTabChip(
                        icon = "💉",
                        label = if (isRu) "Инсулин" else "Insulin",
                        isSelected = selectedType == TreatmentInputType.INSULIN,
                        activeColor = ActionBlue,
                        onClick = { selectedType = TreatmentInputType.INSULIN }
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (selectedType) {
                TreatmentInputType.INSULIN -> {
                    Text(
                        text = if (isRu) "💉 Ввод дозы инсулина" else "💉 Log Insulin Dose",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = insulinText,
                        onValueChange = { insulinText = it.replace(',', '.') },
                        label = { Text(if (isRu) "Доза (Ед.)" else "Dose (U)") },
                        placeholder = { Text("0.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ActionBlue,
                            focusedLabelColor = ActionBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Adaptive recent insulin presets (5 options)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentInsulin) { preset ->
                            val textLabel = if (preset % 1.0 == 0.0) "${preset.toInt()}" else String.format(Locale.US, "%.1f", preset)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ActionBlue.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable {
                                    val cur = insulinText.toDoubleOrNull()
                                    if (cur == null || cur == 0.0) {
                                        insulinText = textLabel
                                    } else if (cur == preset) {
                                        val next = cur + preset
                                        insulinText = if (next % 1.0 == 0.0) "${next.toInt()}" else String.format(Locale.US, "%.1f", next)
                                    } else {
                                        insulinText = textLabel
                                    }
                                }
                            ) {
                                Text(
                                    text = "$textLabel " + (if (isRu) "Ед" else "U"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionBlue,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional note field
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(if (isRu) "Примечание (необязательно)" else "Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TreatmentInputType.CARBS -> {
                    Text(
                        text = if (isRu) "🍞 Ввод углеводов (еды)" else "🍞 Log Carbohydrates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = carbsText,
                            onValueChange = { carbsText = it.replace(',', '.') },
                            label = { Text(if (isRu) "Углеводы (г)" else "Carbs (g)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorHigh,
                                focusedLabelColor = ColorHigh
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Accompanying insulin bolus
                        OutlinedTextField(
                            value = insulinText,
                            onValueChange = { insulinText = it.replace(',', '.') },
                            label = { Text(if (isRu) "Инсулин (Ед)" else "Insulin (U)") },
                            placeholder = { Text("0.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ActionBlue,
                                focusedLabelColor = ActionBlue
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Adaptive recent carb presets (5 options)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentCarbs) { preset ->
                            val textLabel = if (preset % 1.0 == 0.0) "${preset.toInt()}" else String.format(Locale.US, "%.1f", preset)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ColorHigh.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ColorHigh.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable {
                                    val cur = carbsText.toDoubleOrNull()
                                    if (cur == null || cur == 0.0) {
                                        carbsText = textLabel
                                    } else if (cur == preset) {
                                        val next = cur + preset
                                        carbsText = if (next % 1.0 == 0.0) "${next.toInt()}" else String.format(Locale.US, "%.1f", next)
                                    } else {
                                        carbsText = textLabel
                                    }
                                }
                            ) {
                                Text(
                                    text = "$textLabel " + (if (isRu) "г" else "g"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorHigh,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(if (isRu) "Блюдо / Заметка" else "Meal / Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TreatmentInputType.NOTE -> {
                    Text(
                        text = if (isRu) "💬 Заметка события" else "💬 Event Note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(if (isRu) "Текст заметки" else "Note text") },
                        placeholder = { Text(if (isRu) "Например: смена датчика, пробежка..." else "e.g. sensor change, jogging...") },
                        trailingIcon = if (notesText.isNotEmpty()) {
                            {
                                IconButton(onClick = { notesText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (isRu) "Очистить" else "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRu) "Быстрые теги:" else "Quick tags:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick note tags
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickNotes) { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    notesText = if (notesText.isBlank()) tag else "$notesText, $tag"
                                }
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (notesText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable { notesText = "" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isRu) "Очистить поле" else "Clear field",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Selector Row with Standard Clock Dial Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🕒",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isRu) "Время события" else "Event Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val timeSub = if (customTimestamp == null) {
                            if (isRu) "Текущее время (сейчас)" else "Current time (now)"
                        } else {
                            val fmt = SimpleDateFormat("dd MMM, HH:mm", if (isRu) Locale("ru") else Locale.US)
                            fmt.format(Date(customTimestamp!!))
                        }
                        Text(
                            text = timeSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customTimestamp != null) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (customTimestamp != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { customTimestamp = null }
                        ) {
                            Text(
                                text = if (isRu) "Сброс" else "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ActionBlue.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            val now = Calendar.getInstance()
                            val initialCal = Calendar.getInstance().apply {
                                if (customTimestamp != null) timeInMillis = customTimestamp!!
                            }
                            android.app.TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                        // Midnight wrap-around: if selected time is far ahead in the future (> 6 hours), assume it was yesterday evening
                                        if (timeInMillis - now.timeInMillis > 6 * 3600_000L) {
                                            add(Calendar.DAY_OF_YEAR, -1)
                                        }
                                    }
                                    customTimestamp = newCal.timeInMillis
                                },
                                initialCal.get(Calendar.HOUR_OF_DAY),
                                initialCal.get(Calendar.MINUTE),
                                true // 24-hour mode with dial
                            ).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            val displayTime = remember(customTimestamp) {
                                val cal = Calendar.getInstance()
                                if (customTimestamp != null) cal.timeInMillis = customTimestamp!!
                                String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = ActionBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayTime,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            val canSubmit = when (selectedType) {
                TreatmentInputType.INSULIN -> (insulinText.toDoubleOrNull() ?: 0.0) > 0.0 || notesText.isNotBlank()
                TreatmentInputType.CARBS -> (carbsText.toDoubleOrNull() ?: 0.0) > 0.0 || (insulinText.toDoubleOrNull() ?: 0.0) > 0.0
                TreatmentInputType.NOTE -> notesText.isNotBlank()
            }

            Button(
                onClick = {
                    val ins = insulinText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val carbs = carbsText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val note = notesText.trim().takeIf { it.isNotBlank() }
                    val ts = customTimestamp ?: System.currentTimeMillis()

                    ins?.let { TreatmentPresetsManager.recordInsulin(context, it) }
                    carbs?.let { TreatmentPresetsManager.recordCarbs(context, it) }

                    onSubmit(ins, carbs, null, note, ts)
                    onDismiss()
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Сохранить и передать в xDrip+" else "Save & Send to xDrip+",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun QuickActionStrip(
    modifier: Modifier = Modifier,
    isRu: Boolean,
    onOpenTreatment: (TreatmentInputType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Note (Left)
            QuickActionButton(
                icon = "💬",
                label = if (isRu) "Заметки" else "Notes",
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f),
                onClick = { onOpenTreatment(TreatmentInputType.NOTE) }
            )

            // 2. Carbs (Middle)
            QuickActionButton(
                icon = "🍞",
                label = if (isRu) "Углеводы" else "Carbs",
                tint = ColorHigh,
                modifier = Modifier.weight(1f),
                onClick = { onOpenTreatment(TreatmentInputType.CARBS) }
            )

            // 3. Insulin (Right)
            QuickActionButton(
                icon = "💉",
                label = if (isRu) "Инсулин" else "Insulin",
                tint = ActionBlue,
                modifier = Modifier.weight(1f),
                onClick = { onOpenTreatment(TreatmentInputType.INSULIN) }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TreatmentTabChip(
    icon: String,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp
            )
        }
    }
}
