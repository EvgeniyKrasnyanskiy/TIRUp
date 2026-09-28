package com.tirup.app.presentation.settings.sections

import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.data.backup.BackupSummary
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AutoBackupCard(
    settings: UserSettings,
    backupSummary: BackupSummary?,
    isBackupInProgress: Boolean,
    isRestoreInProgress: Boolean,
    isRu: Boolean,
    onToggleAutoBackup: (Boolean) -> Unit,
    onCreateBackupNow: () -> Unit,
    onShareBackup: () -> Unit,
    onExportZip: (suggestedFileName: String) -> Unit,
    onShowRestoreOptionsModal: () -> Unit,
    onShowYearEndDialog: (year: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRu) "Ежедневный автобэкап" else "Daily Auto-Backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRu) "В Документы/TIRUp/Backups (2 CSV + JSON)" else "In Documents/TIRUp/Backups (2 CSV + JSON)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.isAutoBackupEnabled,
                    onCheckedChange = onToggleAutoBackup,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActionBlue
                    )
                )
            }

            // Status and stats
            if (backupSummary != null && backupSummary.readingsCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val fmt = SimpleDateFormat("dd.MM.yyyy 'в' HH:mm", Locale.getDefault())
                        val lastDateStr = if (backupSummary.exportedAt > 0L) fmt.format(Date(backupSummary.exportedAt)) else "—"
                        Text(
                            text = if (isRu) "📦 Сохранённая копия: $lastDateStr" else "📦 Saved backup: $lastDateStr",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryEmerald
                        )
                        Text(
                            text = if (isRu) "🩸 ${backupSummary.readingsCount} замеров | 💉 ${backupSummary.treatmentsCount} меток терапии"
                            else "🩸 ${backupSummary.readingsCount} readings | 💉 ${backupSummary.treatmentsCount} treatments",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isRu) "📁 Папка: Documents/TIRUp/Backups/"
                            else "📁 Folder: Documents/TIRUp/Backups/",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else if (settings.isAutoBackupEnabled) {
                Text(
                    text = if (isRu) "Запланирован на сегодня в 00:00" else "Scheduled for today at 00:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = ActionBlue
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasAllFilesAccess = Environment.isExternalStorageManager()
                if (!hasAllFilesAccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ActionBlue.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isRu) "ℹ️ Для бэкапа в общедоступную папку Documents/TIRUp/Backups предоставьте доступ к файлам"
                                else "ℹ️ To access backups in public Documents/TIRUp/Backups grant all files access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val intent = android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isRu) "Предоставить доступ к файлам" else "Grant all files access",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryEmerald.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✓",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isRu) "Доступ к файлам разрешён" else "All files access granted",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryEmerald
                            )
                        }
                    }
                }
            }

            if (isBackupInProgress) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (isRu) "Создание резервной копии..." else "Creating backup...",
                        style = MaterialTheme.typography.bodySmall,
                        color = ActionBlue
                    )
                }
            }

            // Action buttons (Row 1: Create & Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateBackupNow,
                    enabled = !isBackupInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRu) "Создать" else "Backup",
                        fontSize = 13.sp,
                        color = ActionBlue,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onShareBackup,
                    enabled = !isBackupInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRu) "Поделиться" else "Share",
                        fontSize = 13.sp,
                        color = ActionBlue,
                        maxLines = 1
                    )
                }
            }

            // Action buttons (Row 2: Save to zip file & Restore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        onExportZip("tirup_backup_$dateStr.zip")
                    },
                    enabled = !isBackupInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRu) "В Zip-файл" else "To Zip file",
                        fontSize = 13.sp,
                        color = ActionBlue,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onShowRestoreOptionsModal,
                    enabled = !isRestoreInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRu) "Восстановить" else "Restore",
                        fontSize = 13.sp,
                        color = ActionBlue,
                        maxLines = 1
                    )
                }
            }

            // Row 3: Year-End Digest & Annual Archives
            val activeYear = Calendar.getInstance().get(Calendar.YEAR)
            OutlinedButton(
                onClick = { onShowYearEndDialog(activeYear) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ActionBlue.copy(alpha = 0.05f)
                )
            ) {
                Text(
                    text = "🎄",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRu) "Итоги года и архив" else "Year-End Digest & Archive",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActionBlue
                )
            }
        }
    }
}

@Composable
fun ClearDataCard(
    infoMessage: String?,
    onShowClearConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.clear_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.clear_data_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onShowClearConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ColorVeryLow),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ColorVeryLow
                )
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.clear_data))
            }

            if (infoMessage != null) {
                Text(
                    text = infoMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryEmerald
                )
            }
        }
    }
}

@Composable
fun RestoreOptionsModal(
    backupSummary: BackupSummary?,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onRestoreLatestAutoBackup: () -> Unit,
    onSelectBackupFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = ActionBlue
                )
                Text(
                    text = if (isRu) "Восстановление данных" else "Restore Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (backupSummary != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PrimaryEmerald.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isRu) "⚡ Автоматическая копия найдена" else "⚡ Local Auto-Backup Available",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                            val fmt = SimpleDateFormat("dd.MM.yyyy 'в' HH:mm", Locale.getDefault())
                            val dateStr = if (backupSummary.exportedAt > 0L) fmt.format(Date(backupSummary.exportedAt)) else "—"
                            Text(
                                text = if (isRu) "📅 Дата: $dateStr" else "📅 Date: $dateStr",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (isRu) "🩸 Замеров: ${backupSummary.readingsCount} • 💉 Меток: ${backupSummary.treatmentsCount}"
                                else "🩸 Readings: ${backupSummary.readingsCount} • 💉 Treatments: ${backupSummary.treatmentsCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onRestoreLatestAutoBackup()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isRu) "Восстановить в 1 клик" else "Restore in 1 Click",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = if (isRu) "📁 Папка с копиями:\nDocuments/TIRUp/Backups/"
                    else "📁 Backup directory:\nDocuments/TIRUp/Backups/",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ActionBlue
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRu) "Шпаргалка по выбору файла:" else "File picker guide:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRu)
                                "• tirup_backup_*.zip или *.json — ПОЛНОЕ восстановление (замеры + метки + профиль и настройки).\n" +
                                "• tirup_readings.csv — только замеры сахара (настройки не заменяются).\n" +
                                "• tirup_treatments.csv — только метки инсулина и углеводов.\n" +
                                "• tirup_settings.json — только профиль и пороги тревог."
                            else
                                "• tirup_backup_*.zip or *.json — FULL restore (readings + treatments + settings).\n" +
                                "• tirup_readings.csv — readings only.\n" +
                                "• tirup_treatments.csv — treatments only.\n" +
                                "• tirup_settings.json — settings only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSelectBackupFile()
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ActionBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRu) "Выбрать файл в папке..." else "Select file from folder...",
                        color = ActionBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Закрыть" else "Close")
            }
        }
    )
}

@Composable
fun PendingRestoreDialog(
    pendingRestore: BackupSummary,
    isRestoreInProgress: Boolean,
    isRu: Boolean,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isRestoreInProgress) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = if (isRu) "Восстановление данных" else "Restore Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRu)
                        "Обнаружена резервная копия TIRUp со следующими данными:"
                    else
                        "Found TIRUp backup with the following details:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (pendingRestore.patientName.isNotBlank()) {
                            Text(
                                text = if (isRu) "👤 Профиль: ${pendingRestore.patientName} (${pendingRestore.diabetesType})"
                                else "👤 Profile: ${pendingRestore.patientName} (${pendingRestore.diabetesType})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = if (isRu) "🩸 Замеров сахара: ${pendingRestore.readingsCount}"
                            else "🩸 Glucose readings: ${pendingRestore.readingsCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (isRu) "💉 Записей терапии: ${pendingRestore.treatmentsCount}"
                            else "💉 Treatments: ${pendingRestore.treatmentsCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (pendingRestore.hasSettings) {
                            Text(
                                text = if (isRu) "⚙️ Настройки и пороги тревог включены"
                                else "⚙️ Settings & alerts included",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryEmerald
                            )
                        }
                        if (pendingRestore.exportedAt > 0L) {
                            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            Text(
                                text = if (isRu) "📅 Дата бэкапа: ${fmt.format(Date(pendingRestore.exportedAt))}"
                                else "📅 Backup date: ${fmt.format(Date(pendingRestore.exportedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = if (isRu)
                        "⚠️ Существующие замеры и отметки будут объединены без дублирования. Настройки профиля и тревог будут обновлены из архива."
                    else
                        "⚠️ Existing readings and treatments will be merged without duplicates. Profile and alert settings will be restored.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ActionBlue
                )

                if (isRestoreInProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (isRu) "Идёт восстановление..." else "Restoring...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRestore,
                enabled = !isRestoreInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
            ) {
                Text(if (isRu) "Восстановить" else "Restore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRestoreInProgress
            ) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}

@Composable
fun ClearDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.clear_data), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(text = stringResource(R.string.clear_data_confirm), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_confirm), color = ColorVeryLow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
