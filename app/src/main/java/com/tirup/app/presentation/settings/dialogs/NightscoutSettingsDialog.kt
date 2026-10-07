package com.tirup.app.presentation.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.data.network.NightscoutUploadManager
import com.tirup.app.domain.model.NightscoutSettings
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import kotlinx.coroutines.launch

@Composable
fun NightscoutSettingsDialog(
    initialSettings: NightscoutSettings,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onSave: (NightscoutSettings) -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialSettings.isEnabled) }
    var serverUrl by remember { mutableStateOf(initialSettings.serverUrl) }
    var apiSecret by remember { mutableStateOf(initialSettings.apiSecret) }
    var requireXdripConfirmation by remember { mutableStateOf(initialSettings.requireXdripConfirmation) }
    var downloadGlucose by remember { mutableStateOf(initialSettings.downloadGlucose) }
    var isSecretVisible by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🌐", fontSize = 22.sp)
                Text(
                    text = if (isRu) "Сервер Nightscout / Синхронизация" else "Nightscout Sync Server",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Main toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Включить отправку лечения" else "Enable Treatment Upload",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Активирует кнопки быстрого ввода на главном экране" else "Enables quick treatment buttons on home screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryEmerald
                        )
                    )
                }

                // Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it.trim()
                        testResultText = null
                    },
                    label = { Text(if (isRu) "URL сервера" else "Server Base URL") },
                    placeholder = { Text("http://192.168.1.10:8085") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                // API Secret input
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = {
                        apiSecret = it.trim()
                        testResultText = null
                    },
                    label = { Text(if (isRu) "API Secret (токен)" else "API Secret (token)") },
                    placeholder = { Text(if (isRu) "Секретный ключ бэкенда" else "Server API secret") },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Secret Visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Require xDrip confirmation toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Ждать подтверждения из xDrip+" else "Require xDrip+ confirmation",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu)
                                "Отображать лечение на графике только после того, как xDrip+ скачает его с сервера"
                            else
                                "Show treatment on chart only after xDrip+ syncs it down",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = requireXdripConfirmation,
                        onCheckedChange = { requireXdripConfirmation = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

                // Download Glucose (Cloud Follower) toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Получать сахара (Cloud Follower)" else "Download glucose (Cloud Follower)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu)
                                "Скачивать замеры и историю с сервера, если нет связи по BLE или Wi-Fi"
                            else
                                "Download readings and history from server if BLE or Wi-Fi is unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = downloadGlucose,
                        onCheckedChange = { downloadGlucose = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

                // Test Connection Button & Status
                OutlinedButton(
                    onClick = {
                        isTestingConnection = true
                        testResultText = null
                        scope.launch {
                            val res = NightscoutUploadManager.checkConnection(serverUrl, apiSecret)
                            isTestingConnection = false
                            if (res.isSuccess) {
                                val data = res.getOrNull()!!
                                isTestSuccess = true
                                testResultText = if (isRu)
                                    "✓ ${data.name} v${data.version} (${data.latencyMs} мс)"
                                else
                                    "✓ ${data.name} v${data.version} (${data.latencyMs}ms)"
                            } else {
                                isTestSuccess = false
                                val msg = res.exceptionOrNull()?.message ?: "Unknown error"
                                testResultText = if (isRu) "⚠️ $msg" else "⚠️ $msg"
                            }
                        }
                    },
                    enabled = !isTestingConnection && serverUrl.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ActionBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRu) "Проверка связи..." else "Testing connection...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ActionBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRu) "Проверить подключение" else "Test Connection",
                            color = ActionBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (testResultText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isTestSuccess) PrimaryEmerald else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = testResultText!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isTestSuccess) PrimaryEmerald else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        NightscoutSettings(
                            isEnabled = isEnabled,
                            serverUrl = serverUrl.trim().removeSuffix("/"),
                            apiSecret = apiSecret.trim(),
                            requireXdripConfirmation = requireXdripConfirmation,
                            downloadGlucose = downloadGlucose
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isRu) "Сохранить" else "Save",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}
