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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    onSaveManual: () -> Unit = {},
    onRestoreBackup: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    onDismiss: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
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
                    text = if (isRu) "Справка и отказ от ответственности" else "Help & Disclaimer",
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

                // 2. Data Sources & Synchronization Guide
                HelpSectionCard(
                    icon = Icons.Default.SettingsInputAntenna,
                    iconTint = PrimaryEmerald,
                    title = if (isRu) "Источники данных и синхронизация" else "Data Sources & Synchronization",
                    content = if (isRu) {
                        "TIRUp поддерживает 4 уровня источников данных с автоматической дедупликацией:\n" +
                        "1. Локальный xDrip+ (высший приоритет): приём интентов и службы Broadcast Service (<0.1 с) без интернета;\n" +
                        "2. Семейный BLE-мост (Bluetooth LE): прямая связь между смартфонами ребёнка и родителя без Wi-Fi и SIM (радиус до 25–50 м с Long Range Coded PHY, метрики качества PDR%, поиск 60 с и эко-режим);\n" +
                        "3. Wi-Fi LAN Follower: прямой опрос локального веб-сервера xDrip+ мастера (порт 17580) в домашней сети или точке доступа;\n" +
                        "4. Nightscout Cloud Follower: фоновое получение замеров сахара, IoB, CoB и батареи мастера из облачного REST API Nightscout."
                    } else {
                        "TIRUp supports 4 stratified data sources with automatic deduplication:\n" +
                        "1. Local xDrip+ (highest priority): instant local intents & Broadcast Service (<0.1s) offline;\n" +
                        "2. Family BLE Bridge (Bluetooth LE): direct link between phones without Wi-Fi or SIM (up to 25-50m with Long Range Coded PHY, live PDR% metrics, 60s radar search and eco mode);\n" +
                        "3. Wi-Fi LAN Follower: direct local polling of master's xDrip+ server (port 17580) over Wi-Fi/Hotspot;\n" +
                        "4. Nightscout Cloud Follower: background retrieval of glucose, IoB, CoB, and master battery via REST API."
                    }
                )

                // 3. Historical Reports & AGP
                HelpSectionCard(
                    icon = Icons.Default.Summarize,
                    iconTint = ActionBlue,
                    title = if (isRu) "Отчёты, периоды и клинический AGP" else "AGP Reports & Custom Periods",
                    content = if (isRu) {
                        "• Произвольный период: кнопка-шестерёнка [⚙️] в селекторе периодов позволяет выбрать любое число дней от 1 до 365 (чипы 2, 3, 5, 20, 60 дней или ручной ввод).\n" +
                        "• Амбулаторный профиль AGP: клинический отчёт по консенсусу ATTD/ADA за выбранный период (TIR, TING, TBR, TAR, CV, SD, GRI, GMI) с печатью стандартизированного PDF-документа для врача.\n" +
                        "• Импорт архивов: поддержка многофайловой загрузки баз данных xDrip+ (ZIP и CSV)."
                    } else {
                        "• Custom Period: gear icon [⚙️] in period selector enables any duration from 1 to 365 days (quick chips 2, 3, 5, 20, 60d or custom input).\n" +
                        "• Clinical AGP Profile: standardized report compliant with ATTD/ADA consensus (TIR, TING, TBR, TAR, CV, SD, GRI, GMI) with export to PDF for endocrinologists.\n" +
                        "• Database Archive Import: supports multi-file upload of xDrip+ ZIP and CSV archives simultaneously."
                    }
                )

                // 4. Smart 4-Tier Alarms & Emergency SMS Card
                HelpSectionCard(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = ColorHigh,
                    title = if (isRu) "Умные тревоги и Экстренная безопасность" else "Smart 4-Tier Alarms & Emergency Guard",
                    content = if (isRu) {
                        "• Уровни 1–4: предиктивный прогноз выхода за диапазон, тройной тон, экстренная сирена (~12 сек) и тревога при потере связи с сенсором (>20 мин).\n" +
                        "• Независимая громкость: отдельный слайдер (20% – 100%) в настройках звука с проверкой тестовой мелодии.\n" +
                        "• Быстрая пауза всех тревог: выбор интервала («Snooze All» от 10м до 8ч, по умолчанию 1ч) прямо на экране тревог.\n" +
                        "• Экстренные SOS SMS: если сахар упал ниже 3.0 ммоль/л и тревога не подтверждена в течение 5 мин отправляет доверенному лицу SMS (≤67 симв.) с координатами.\n" +
                        "• Офлайн SMS-запрос: доверенный контакт может запросить текущий сахар и TIR по SMS без интернета («сахар», «?», «bg»).\n" +
                        "• Плавающий кружок поверх окон: в норме (3.9–10.0) компактный (50%), тап открывает TIRUp. Вне нормы — тревожный режим, короткий тап глушит/снузит алерт, долгое нажатие открывает приложение."
                    } else {
                        "• Tiers 1–4: predictive target departure, confirmed boundary tone, critical 12s siren, and sensor signal loss (>20 min).\n" +
                        "• Independent Volume: custom alert volume slider (20% – 100%) with realistic preview melody.\n" +
                        "• Quick Alarm Pause: selectable interval ('Snooze All' from 10m to 8h, default 1h) on the alert screen.\n" +
                        "• Emergency SOS SMS: sends compact SMS (≤67 chars) with GPS to trusted contact if severe hypo (<3.0) is unacknowledged for 5m.\n" +
                        "• Offline SMS Query: trusted contact can query real-time glucose & TIR via SMS without internet ('sugar', '?', 'bg').\n" +
                        "• Floating Bubble: in target (3.9–10.0) compact 50%, tap opens TIRUp. Out of range — alarm mode, tap silences/snoozes, hold opens the app."
                    }
                )

                // 5. Sunday Digest & Automated Daily Backup Card
                HelpSectionCard(
                    icon = Icons.Default.Backup,
                    iconTint = PrimaryEmerald,
                    title = if (isRu) "Воскресный дайджест и Автобэкап" else "Sunday Digest & Daily Auto-Backup",
                    content = if (isRu) {
                        "• Воскресный дайджест: каждое воскресенье в 20:00 формирует недельный обзор компенсации со сравнением с прошлой неделей.\n" +
                        "• Автоматический бэкап в полночь: настройки и базу данных в защищённую изолированную папку приложения.\n" +
                        "• Не требует опасных разрешений на доступ ко всей файловой системе смартфона.\n" +
                        "• При переустановке приложение автоматически обнаружит копию и восстановит историю."
                    } else {
                        "• Sunday Digest: every Sunday at 20:00 generates a weekly compensation review comparing trends with the previous week.\n" +
                        "• Exact RTC AlarmManager (23:59:59) backs up settings and database into the isolated app sandbox.\n" +
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
                if (snackbarHostState != null) {
                    SnackbarHost(hostState = snackbarHostState)
                }
                if (onRestoreBackup != null) {
                    Button(
                        onClick = onRestoreBackup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRu) "Восстановить историю из бэкапа" else "Restore history from backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSaveManual,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ActionBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRu) "Сохранить руководство PDF" else "Save User Manual PDF",
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
                        text = if (isRu) "Начать работу" else "Start using app",
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
