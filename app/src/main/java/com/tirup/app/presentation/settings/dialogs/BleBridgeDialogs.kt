package com.tirup.app.presentation.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.data.ble.BlePacketCodec
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald

@Composable
fun BleBridgeHelpDialog(
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔵", fontSize = 22.sp)
                Text(
                    text = if (isRu) "Локальный BLE-мост" else "Local BLE Bridge",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = if (isRu)
                            "Прямая трансляция сахара, тренда, скорости изменения, активного инсулина (IOB) и уровня батареи напрямую со смартфона ребёнка на смартфоны родителей."
                        else
                            "Direct streaming of glucose, trend, rate of change, active insulin (IOB), and phone battery directly between smartphones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Text(
                        text = if (isRu)
                            "Чтобы отключить BLE-мост, выберите «✖️ Выкл» в разделе «Дополнительные настройки»."
                        else
                            "To disable the BLE Bridge, select '✖️ Off' in Advanced Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isRu) "⚡ Ключевые преимущества:" else "⚡ Key Advantages:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isRu)
                                "• Без интернета: работает через Bluetooth Low Energy на расстоянии 10–25 метров. Идеально в школе, самолёте, за городом или при сбоях сотовой связи.\n\n" +
                                "• Один-ко-многим: один смартфон ребёнка вещает данные сразу на неограниченное число приёмников (мама, папа, бабушка, учитель) одновременно.\n\n" +
                                "• Безопасно для батареи: импульс длится всего 5–10 секунд при каждом новом замере (5 сек при интервале 1 мин, 10 сек — при 5 мин). В остальное время радиомодуль полностью спит (<0.3% батареи в сутки).\n\n" +
                                "• Аппаратный фильтр: приёмник сканирует эфир с аппаратной фильтрацией BLE, просыпаясь только при наличии пакета TIRUp."
                            else
                                "• No Internet Needed: operates via Bluetooth Low Energy over 10–25 meters. Ideal for school, travel, flights, or cellular outages.\n\n" +
                                "• One-to-Many Architecture: a single broadcaster transmits simultaneously to mother, father, and caregivers.\n\n" +
                                "• Battery Safe: pulsed broadcast lasts only 5–10 seconds per reading (5s for 1-min sensors, 10s for 5-min sensors). The radio module sleeps the rest of the time (<0.3% battery/day).\n\n" +
                                "• Hardware Filtered: follower uses low-power hardware scanning, waking only when a valid TIRUp packet is received.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryEmerald.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRu) "🔒 Защита и PIN-код семьи:" else "🔒 Privacy & Family PIN:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryEmerald
                        )
                        Text(
                            text = if (isRu)
                                "Каждый пакет шифруется и защищён 3-буквенным случайным PIN-кодом семьи (например, «WKV»). " +
                                "Чужие пакеты или пакеты с повреждённой защитой от искажений моментально отбрасываются."
                            else
                                "Each packet is protected with a 3-letter uppercase family PIN (e.g. 'WKV'). " +
                                "Foreign packets or corrupted checksums (CRC-8) are discarded immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ActionBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRu) "📱 Как настроить связку:" else "📱 How to Pair Devices:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ActionBlue
                        )
                        Text(
                            text = if (isRu)
                                "1. На телефоне ребёнка (с сенсором/xDrip) включите роль «📡 Вещатель».\n" +
                                "2. Запомните сгенерированный 3-буквенный PIN-код (или смените кнопкой случайного выбора).\n" +
                                "3. На телефоне родителя включите роль «📻 Приёмник» и укажите точно такой же PIN-код.\n" +
                                "4. Готово! При каждом замере данные мгновенно отобразятся на экране и в виджетах родителя."
                            else
                                "1. On the patient's phone (with CGM/xDrip), enable '📡 Broadcaster'.\n" +
                                "2. Note the generated 3-letter PIN (or regenerate with the shuffle button).\n" +
                                "3. On the follower's phone, enable '📻 Observer' and type the exact same PIN.\n" +
                                "4. Done! Every reading will seamlessly appear on the follower's screen and widgets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isRu) "Понятно" else "Got it",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun BleFamilyPinDialog(
    currentPin: String,
    isRu: Boolean,
    onSavePin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinText by remember(currentPin) {
        mutableStateOf(if (currentPin.length == 3 && currentPin.all { it in 'A'..'Z' }) currentPin else BlePacketCodec.generateRandomPin())
    }
    var isVisible by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔑", fontSize = 22.sp)
                Text(
                    text = if (isRu) "PIN-код семьи" else "Family PIN Code",
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
                Text(
                    text = if (isRu)
                        "3 заглавные латинские буквы (A–Z). Должен быть одинаковым на смартфоне ребёнка и смартфонах родителей для безопасной фильтрации данных семьи."
                    else
                        "3 uppercase letters (A–Z). Must match on both child and parent smartphones to securely filter your family telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { newPin ->
                            pinText = newPin.uppercase().filter { it in 'A'..'Z' }.take(3)
                        },
                        label = { Text(if (isRu) "PIN (3 буквы)" else "PIN (3 letters)") },
                        placeholder = { Text("ABC") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 6.sp
                        ),
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )

                    // Eye visibility toggle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { isVisible = !isVisible }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Centered Random Dice / Refresh button
                    Surface(
                        shape = CircleShape,
                        color = ActionBlue.copy(alpha = 0.15f),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { pinText = BlePacketCodec.generateRandomPin() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Generate Random PIN",
                                tint = ActionBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validPin = if (pinText.length == 3 && pinText.all { it in 'A'..'Z' }) pinText else BlePacketCodec.generateRandomPin()
                    onSavePin(validPin)
                    onDismiss()
                }
            ) {
                Text(if (isRu) "Сохранить" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Отмена" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun BleLongRangeConfirmDialog(
    isRu: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isRu) "Включить Long Range?" else "Enable Long Range?")
        },
        text = {
            Text(
                if (isRu) "Режим повышенной дальности (LE Coded PHY) увеличивает радиус связи до 4 раз.\n\n" +
                          "Рекомендуется включать, только если смартфон наблюдателя также современный и поддерживает Bluetooth 5.0 Long Range.\n\n" +
                          "Если второй телефон не поддерживает эту технологию, показания сахара могут перестать поступать.\n\n" +
                          "После включения рекомендуем нажать «Тест связи (30 сек)» рядом с приёмником для проверки."
                else "Long Range mode (LE Coded PHY) extends transmission distance up to 4x.\n\n" +
                     "Enable only if observer phone supports Bluetooth 5.0 Long Range.\n\n" +
                     "If observer phone does not support it, readings may not be received.\n\n" +
                     "We recommend using 'Test Link' to verify connection."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Включить" else "Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "Отмена" else "Cancel")
            }
        }
    )
}

@Composable
fun BleRangeHelpDialog(
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ActionBlue,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (isRu) "Тест дальности BLE-моста" else "BLE Bridge Range Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isRu)
                        "Как тестировать дальность связи на открытой местности:\n\n" +
                        "1. На смартфоне пациента (Вещатель) запустите «Тест дальности: передача».\n\n" +
                        "2. На смартфоне опекуна (Приёмник) запустите «Тест дальности: приём».\n\n" +
                        "3. Отойдите друг от друга на открытом пространстве (до 30–50 метров).\n\n" +
                        "4. При приёме пакета на экране приёмника на 6 секунд появится баннер со значением сахара и мощностью сигнала RSSI:\n" +
                        "   • От -50 до -75 dBm: отличная связь\n" +
                        "   • От -75 до -85 dBm: средний сигнал\n" +
                        "   • Ниже -85 dBm: предел дальности"
                    else
                        "How to test BLE range outdoors:\n\n" +
                        "1. On patient phone (Broadcaster), tap 'Range Test: Broadcast'.\n\n" +
                        "2. On caregiver phone (Observer), tap 'Range Test: Receive'.\n\n" +
                        "3. Walk away from each other in an open area (up to 30–50 meters).\n\n" +
                        "4. When a packet is received, the caregiver phone shows a banner for 6s with glucose and signal strength (RSSI):\n" +
                        "   • -50 to -75 dBm: excellent signal\n" +
                        "   • -75 to -85 dBm: moderate signal\n" +
                        "   • Below -85 dBm: range limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text(if (isRu) "Понятно" else "Got it")
            }
        }
    )
}

