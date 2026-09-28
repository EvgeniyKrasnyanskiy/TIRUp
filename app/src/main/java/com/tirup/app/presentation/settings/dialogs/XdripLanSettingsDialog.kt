package com.tirup.app.presentation.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import com.tirup.app.data.network.XdripLanClient
import com.tirup.app.domain.model.XdripLanSettings
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import kotlinx.coroutines.launch

@Composable
fun XdripLanSettingsDialog(
    initialSettings: XdripLanSettings,
    isRu: Boolean,
    onDismiss: () -> Unit,
    onSave: (XdripLanSettings) -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialSettings.isEnabled) }
    var isAutoDiscovery by remember { mutableStateOf(initialSettings.isAutoDiscovery) }
    var masterHost by remember { mutableStateOf(initialSettings.masterHost) }
    var portStr by remember { mutableStateOf(initialSettings.port.toString()) }
    var apiSecret by remember { mutableStateOf(initialSettings.apiSecret) }
    var pollIntervalStr by remember { mutableStateOf(initialSettings.pollIntervalSeconds.toString()) }
    var isSecretVisible by remember { mutableStateOf(false) }

    var isDiscovering by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📡", fontSize = 22.sp)
                Text(
                    text = if (isRu) "Wi-Fi LAN Follower (xDrip+)" else "Wi-Fi LAN Follower",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isRu) "Включить Wi-Fi приём" else "Enable Wi-Fi Follower",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu)
                                "Прямое чтение сахара, IoB, CoB и батареи мастера по локальной сети без интернета"
                            else
                                "Direct local reading of BG, IoB, CoB and master battery without internet",
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

                // Auto-discovery toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isRu) "Автопоиск мастера" else "Auto-discover master",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu)
                                "Автоматическое сканирование подсети и точки доступа Hotspot"
                            else
                                "Auto-scan local subnet and master Hotspot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoDiscovery,
                        onCheckedChange = { isAutoDiscovery = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryEmerald
                        )
                    )
                }

                // Discover button if auto-discovery is on
                if (isAutoDiscovery) {
                    OutlinedButton(
                        onClick = {
                            isDiscovering = true
                            testResultText = null
                            scope.launch {
                                val p = portStr.toIntOrNull() ?: 17580
                                val res = XdripLanClient.discoverMaster(context, p, apiSecret.trim())
                                isDiscovering = false
                                if (res.isSuccess) {
                                    val ip = res.getOrNull()!!
                                    masterHost = ip
                                    isTestSuccess = true
                                    testResultText = if (isRu) "✓ Мастер найден: $ip" else "✓ Master found: $ip"
                                } else {
                                    isTestSuccess = false
                                    val err = res.exceptionOrNull()?.message ?: "Не найден"
                                    testResultText = "⚠️ $err"
                                }
                            }
                        },
                        enabled = !isDiscovering,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ActionBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRu) "Сканирование подсети..." else "Scanning subnet...")
                        } else {
                            Text(
                                text = if (masterHost.isBlank())
                                    (if (isRu) "🔍 Найти мастера в подсети" else "🔍 Scan for Master")
                                else
                                    (if (isRu) "🔍 Найти снова (текущий: $masterHost)" else "🔍 Scan Again ($masterHost)"),
                                color = ActionBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Master IP Address (editable or manual)
                if (!isAutoDiscovery || masterHost.isNotBlank()) {
                    OutlinedTextField(
                        value = masterHost,
                        onValueChange = {
                            masterHost = it
                            testResultText = null
                        },
                        label = {
                            Text(if (isAutoDiscovery) {
                                if (isRu) "IP мастера (найден автоматически)" else "Master IP (Discovered)"
                            } else {
                                if (isRu) "Статический IP мастера" else "Static Master IP"
                            })
                        },
                        placeholder = { Text("192.168.1.150 или 192.168.43.1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Port & Interval Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRu) "Порт" else "Port") },
                        placeholder = { Text("17580") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = pollIntervalStr,
                        onValueChange = { pollIntervalStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRu) "Опрос (сек)" else "Interval (s)") },
                        placeholder = { Text("60") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // API Secret (Optional)
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = {
                        apiSecret = it
                        testResultText = null
                    },
                    label = { Text(if (isRu) "API Secret мастера (если включен)" else "API Secret (Optional)") },
                    placeholder = { Text(if (isRu) "Оставьте пустым, если не задан" else "Leave blank if not set") },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isSecretVisible) "Hide secret" else "Show secret"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // Test Connection Button
                OutlinedButton(
                    onClick = {
                        isTestingConnection = true
                        testResultText = null
                        val parsedPort = portStr.toIntOrNull() ?: 17580
                        val parsedInterval = pollIntervalStr.toIntOrNull() ?: 60
                        val testSettings = XdripLanSettings(
                            isEnabled = true,
                            masterHost = masterHost.trim(),
                            port = parsedPort,
                            apiSecret = apiSecret.trim(),
                            pollIntervalSeconds = parsedInterval
                        )

                        scope.launch {
                            val res = XdripLanClient.testConnection(testSettings)
                            isTestingConnection = false
                            if (res.isSuccess) {
                                val data = res.getOrNull()!!
                                isTestSuccess = true
                                testResultText = data.message
                            } else {
                                isTestSuccess = false
                                val msg = res.exceptionOrNull()?.message ?: "Ошибка связи"
                                testResultText = "⚠️ $msg"
                            }
                        }
                    },
                    enabled = !isTestingConnection && masterHost.isNotBlank(),
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
                            imageVector = Icons.Default.Wifi,
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
                    val parsedPort = portStr.toIntOrNull() ?: 17580
                    val parsedInterval = (pollIntervalStr.toIntOrNull() ?: 60).coerceIn(15, 300)
                    onSave(
                        XdripLanSettings(
                            isEnabled = isEnabled,
                            isAutoDiscovery = isAutoDiscovery,
                            masterHost = masterHost.trim(),
                            port = parsedPort,
                            apiSecret = apiSecret.trim(),
                            pollIntervalSeconds = parsedInterval
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
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}
