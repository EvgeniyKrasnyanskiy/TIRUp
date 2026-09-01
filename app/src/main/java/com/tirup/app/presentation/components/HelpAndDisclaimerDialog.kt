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
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                        "1. Откройте приложение xDrip+ на смартфоне.\n" +
                        "2. Перейдите в: Настройки (Settings) ➔ Межпрограммная интеграция (Inter-app settings).\n" +
                        "3. Включите опцию: «Широковещательный показ данных» (Broadcast locally).\n" +
                        "4. TIRUp начнёт мгновенно принимать каждое новое измерение в фоне без интернета."
                    } else {
                        "1. Open the xDrip+ application on your phone.\n" +
                        "2. Navigate to: Settings ➔ Inter-app settings.\n" +
                        "3. Enable: 'Broadcast locally' toggle.\n" +
                        "4. TIRUp will automatically capture every glucose reading in the background locally."
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
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(
                    text = if (isRu) "Понятно и согласен" else "I Understand & Agree",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
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
