package com.tirup.app.presentation.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun HelpAndDisclaimerDialog(
    isRu: Boolean,
    onPrintManual: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(24.dp)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ActionBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRu) "Справка и Дисклеймер" else "Help & Disclaimer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
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
                // 1. Medical Disclaimer Card
                HelpSectionCard(
                    icon = Icons.Default.HealthAndSafety,
                    iconTint = ColorHigh,
                    title = if (isRu) "Медицинский отказ от ответственности" else "Medical Disclaimer",
                    content = if (isRu) {
                        "• Приложение TIRUp предназначено исключительно для личного самоконтроля и аналитики гликемического профиля.\n" +
                        "• Приложение НЕ является медицинским изделием и НЕ ставит медицинских диагнозов.\n" +
                        "• Никакая информация в приложении не заменяет консультации лечащего врача-эндокринолога.\n" +
                        "• Любая коррекция дозировок инсулина и терапии должна согласовываться со специалистом."
                    } else {
                        "• TIRUp is intended solely for personal self-monitoring and glycemic analytics.\n" +
                        "• It is NOT a medical device and does NOT make clinical diagnoses.\n" +
                        "• Information in this app is not a substitute for professional consultation with your doctor.\n" +
                        "• Always consult your healthcare provider before modifying insulin dosages or therapy."
                    }
                )

                // 2. xDrip+ Integration Guide
                HelpSectionCard(
                    icon = Icons.Default.SettingsInputAntenna,
                    iconTint = PrimaryEmerald,
                    title = if (isRu) "Настройка связи с xDrip+" else "xDrip+ Connection Guide",
                    content = if (isRu) {
                        "Для полной интеграции выполните настройку в xDrip+ и Android:\n" +
                        "1. Настройки ➔ Межпрограммная интеграция:\n" +
                        "   • Включите «Широковещательный показ данных» (Broadcast locally) для приёма сахара;\n" +
                        "   • Включите «API службы трансляции» (Broadcast Service API) для получения активного инсулина (IoB) и углеводов (CoB).\n" +
                        "2. Настройки системы Android ➔ Батарея:\n" +
                        "   • Отключите оптимизацию батареи для TIRUp и xDrip+ (выберите «Без ограничений»).\n" +
                        "3. TIRUp будет автоматически принимать замеры, стрелки тренда и IoB/CoB в фоне без интернета."
                    } else {
                        "For complete integration, configure the following in xDrip+ and Android:\n" +
                        "1. Settings ➔ Inter-app settings:\n" +
                        "   • Enable 'Broadcast locally' for real-time glucose readings;\n" +
                        "   • Enable 'Broadcast Service API' to receive Insulin on Board (IoB) & Carbs (CoB).\n" +
                        "2. Android System Settings ➔ Battery:\n" +
                        "   • Disable battery optimization for TIRUp and xDrip+ (select 'Unrestricted').\n" +
                        "3. TIRUp will automatically receive readings, trend arrows, and IoB/CoB in the background locally."
                    }
                )

                // 3. Historical Reports & AGP
                HelpSectionCard(
                    icon = Icons.Default.Summarize,
                    iconTint = ActionBlue,
                    title = if (isRu) "Отчёты и файлы баз данных" else "AGP Reports & Database Import",
                    content = if (isRu) {
                        "• Для построения амбулаторного профиля (AGP) за 7, 14, 30 или 90 дней выгрузите архив базы данных из xDrip+ (ZIP или CSV).\n" +
                        "• Во вкладке «Отчёты» нажмите «Загрузить файл» и выберите экспортированный архив.\n" +
                        "• Приложение рассчитает клинические показатели (TIR, TING, GRI, eA1c) и сформирует готовый PDF-отчёт."
                    } else {
                        "• To build an AGP profile for 7, 14, 30, or 90 days, export your database archive from xDrip+ (ZIP or CSV).\n" +
                        "• Go to the 'Reports' tab, tap 'Upload File', and select the file.\n" +
                        "• TIRUp will calculate clinical indices (TIR, TING, GRI, eA1c) and generate a printable PDF report."
                    }
                )

                // 4. Smart 4-Tier Alarms Card
                HelpSectionCard(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = ColorHigh,
                    title = if (isRu) "Умные тревоги (4 уровня) и Снуз" else "Smart 4-Tier Alarms & Snooze",
                    content = if (isRu) {
                        "• Уровень 1: прогноз выхода за диапазон с точным временем («в 16:42»).\n" +
                        "• Уровень 2: тройной сигнал подтверждённого выхода 5 точек за границы.\n" +
                        "• Уровень 3: критическая сирена 12 сек. Мгновенно глушится любой кнопкой громкости или питания.\n" +
                        "• Уровень 4: потеря сигнала >20 мин с прогрессивным бэкоффом (20 ➔ 40 ➔ 80 мин).\n" +
                        "• Умный Снуз: 15 мин при гипо (защита <2.8 ммоль/л); 30–45 мин при гипер на действие инсулина."
                    } else {
                        "• Tier 1: predictive forecast with exact timestamp ('at 16:42').\n" +
                        "• Tier 2: confirmed boundary exit (triple beep with 1.5s pause).\n" +
                        "• Tier 3: critical 12s siren. Instant mute with volume or power buttons.\n" +
                        "• Tier 4: sensor signal loss >20 min with geometric backoff.\n" +
                        "• Smart Snooze: 15 min for hypo (<2.8 coma guard); 30-45 min for hyper."
                    }
                )

                // 5. Automated Daily Backup Card
                HelpSectionCard(
                    icon = Icons.Default.Backup,
                    iconTint = PrimaryEmerald,
                    title = if (isRu) "Ежедневный автобэкап в 23:59:59" else "Daily Auto-Backup at 23:59:59",
                    content = if (isRu) {
                        "• Точный будильник сохраняет настройки и базу данных в защищённую изолированную папку приложения.\n" +
                        "• Не требует опасных разрешений на доступ ко всем файлам смартфона.\n" +
                        "• При переустановке приложение автоматически обнаружит копию и восстановит историю."
                    } else {
                        "• Exact RTC AlarmManager backs up settings and database into app sandbox.\n" +
                        "• Operates without dangerous storage permissions.\n" +
                        "• Automatically detects and restores your history upon reinstallation."
                    }
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrintManual,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ActionBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRu) "📄 Распечатать руководство" else "📄 Print User Manual",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ActionBlue
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                ) {
                    Text(
                        text = if (isRu) "Понятно и согласен" else "I Understand & Agree",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    )
}

@Composable
private fun HelpSectionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    content: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
