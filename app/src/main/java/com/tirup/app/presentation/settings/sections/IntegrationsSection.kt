package com.tirup.app.presentation.settings.sections

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tirup.app.data.ble.BleBroadcaster
import com.tirup.app.data.ble.BleObserverManager
import com.tirup.app.data.ble.BlePacketCodec
import com.tirup.app.data.network.NightscoutUploadManager
import com.tirup.app.data.network.XdripLanClient
import com.tirup.app.data.network.XdripLanManager
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BleBridgeSettings
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.LanConnectionState
import com.tirup.app.domain.model.NightscoutSettings
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.XdripLanSettings
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.settings.dialogs.BleLongRangeConfirmDialog
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BleBridgeCard(
    settings: UserSettings,
    isRu: Boolean,
    isBleCardExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    highlightBle: Boolean,
    highlightBorderAlpha: Float,
    latestReading: GlucoseReading?,
    blePermissionsLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onUpdateBleSettings: (BleBridgeSettings) -> Unit,
    onSendBleTestPing: () -> Unit,
    onTriggerBleObserverBoost: () -> Unit,
    onShowBleHelpModal: () -> Unit,
    onShowBlePinDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ble = settings.bleBridgeSettings
    val isBridgeActive = ble.isEnabled && ble.role != BleBridgeRole.DISABLED
    val isBroadcasting by BleBroadcaster.isBroadcasting.collectAsState()
    val broadcastRemaining by BleBroadcaster.broadcastRemainingSec.collectAsState()
    val boostRemaining by BleObserverManager.boostRemainingSec.collectAsState()
    val isScanning by BleObserverManager.isScanningFlow.collectAsState()

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val isBtOn = remember(bluetoothManager) { bluetoothManager?.adapter?.isEnabled == true }

    fun checkAndRequestBlePermissions(targetRole: BleBridgeRole): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (targetRole == BleBridgeRole.BROADCASTER) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else if (targetRole == BleBridgeRole.OBSERVER) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missing.isNotEmpty()) {
            blePermissionsLauncher.launch(missing.toTypedArray())
            false
        } else {
            true
        }
    }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (highlightBle) ActionBlue.copy(alpha = highlightBorderAlpha) else MaterialTheme.colorScheme.outline,
        borderWidth = if (highlightBle) 2.2.dp else 1.dp,
        backgroundColor = if (highlightBle) ActionBlue.copy(alpha = 0.08f * highlightBorderAlpha) else MaterialTheme.colorScheme.surface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isRu) "BLE-мост" else "BLE Bridge",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            val (roleBadgeEmoji, roleBadgeColor) = when {
                                !isBridgeActive -> Pair("✖️", MaterialTheme.colorScheme.onSurfaceVariant)
                                ble.role == BleBridgeRole.BROADCASTER -> Pair("📡", ActionBlue)
                                ble.role == BleBridgeRole.OBSERVER -> Pair("📻", PrimaryEmerald)
                                else -> Pair("✖️", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = roleBadgeColor.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, roleBadgeColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = roleBadgeEmoji,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRu) "Прямая связь между смартфонами без интернета (10–25 м)"
                            else "Direct phone-to-phone telemetry without internet (10–25 m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Info button is placed to the LEFT of the switch
                    Surface(
                        shape = CircleShape,
                        color = ActionBlue.copy(alpha = 0.15f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onShowBleHelpModal() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "BLE Info",
                                tint = ActionBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val switchTrackColor = if (ble.role == BleBridgeRole.OBSERVER) PrimaryEmerald else ActionBlue
                    Switch(
                        checked = isBridgeActive,
                        onCheckedChange = { isChecked ->
                            if (!isChecked) {
                                onUpdateBleSettings(ble.copy(isEnabled = false))
                            } else {
                                val targetRole = if (ble.role == BleBridgeRole.DISABLED) BleBridgeRole.BROADCASTER else ble.role
                                val ok = checkAndRequestBlePermissions(targetRole)
                                if (ok) {
                                    onUpdateBleSettings(ble.copy(isEnabled = true, role = targetRole))
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = switchTrackColor
                        ),
                    )

                    Icon(
                        imageVector = if (isBleCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isBleCardExpanded) "Collapse" else "Expand",
                        tint = ActionBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isBleCardExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRu)
                                    "Прямая передача замера импульсом 5–10 сек при каждом новом замере CGM без интернета"
                                else
                                    "Direct telemetry via 5-10s BLE pulse upon each CGM reading without internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    val roles = listOf(
                        Pair(BleBridgeRole.BROADCASTER, if (isRu) "📡 Вещатель" else "📡 Broadcaster"),
                        Pair(BleBridgeRole.OBSERVER, if (isRu) "📻 Приёмник" else "📻 Observer")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roles.forEach { (role, label) ->
                            val isSelected = isBridgeActive && ble.role == role
                            val isBroadcaster = role == BleBridgeRole.BROADCASTER
                            val activeColor = if (isBroadcaster) ActionBlue else PrimaryEmerald

                            val (bg, textColor, borderColor) = if (isSelected) {
                                Triple(activeColor.copy(alpha = 0.18f), activeColor, activeColor)
                            } else {
                                Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val ok = checkAndRequestBlePermissions(role)
                                        if (ok) {
                                            onUpdateBleSettings(ble.copy(isEnabled = true, role = role))
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = bg,
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 11.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // Family PIN row
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowBlePinDialog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isRu) "Семейный PIN-код (4 цифры)" else "Family PIN (4 digits)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "Шифрует пакет. Должен совпадать на обоих телефонах" else "Encrypts packets. Must match on both phones",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (ble.familyPin.isNotBlank()) "••••" else "0000",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionBlue
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit PIN",
                                    tint = ActionBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (ble.role == BleBridgeRole.BROADCASTER) {
                        val isLongRangeSupported = remember(isBtOn) {
                            BleBroadcaster.isLongRangeSupported(context)
                        }
                        var showLongRangeConfirmDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (isRu) "Режим повышенной дальности (Long Range)" else "Long Range Mode (Coded PHY)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isLongRangeSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (!isLongRangeSupported) {
                                        if (isRu) "Не поддерживается чипсетом этого устройства" else "Not supported by this device's chipset"
                                    } else {
                                        if (isRu) "Увеличивает радиус в 2-4 раза. Требуется поддержка на смартфоне наблюдателя"
                                        else "Extends range 2-4x. Requires support on observer's phone"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!isLongRangeSupported) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = ble.useLongRange,
                                enabled = isLongRangeSupported,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        showLongRangeConfirmDialog = true
                                    } else {
                                        onUpdateBleSettings(ble.copy(useLongRange = false))
                                    }
                                }
                            )
                        }

                        if (showLongRangeConfirmDialog) {
                            BleLongRangeConfirmDialog(
                                isRu = isRu,
                                onConfirm = {
                                    onUpdateBleSettings(ble.copy(useLongRange = true))
                                },
                                onDismiss = { showLongRangeConfirmDialog = false }
                            )
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "BlePulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.92f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseScale"
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBroadcasting) ActionBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.2.dp, if (isBroadcasting) ActionBlue.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isBroadcasting) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .scale(pulseScale)
                                            .background(ActionBlue.copy(alpha = pulseAlpha * 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📡", fontSize = 16.sp)
                                    }
                                    Column {
                                        Text(
                                            text = if (isRu) "Идёт передача данных" else "TRANSMITTING TELEMETRY!",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ActionBlue
                                        )
                                        Text(
                                            text = if (isRu) "Активный радиосигнал: осталось ${broadcastRemaining} сек."
                                            else "Active radio pulse: ${broadcastRemaining}s left",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Text("💤", fontSize = 20.sp)
                                    Column {
                                        Text(
                                            text = if (isRu) "Вещатель в ожидании замера" else "Broadcaster idle (radio silent)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        BleCountdownText(
                                            latestReadingTimestamp = latestReading?.timestamp,
                                            isRu = isRu
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (!isBtOn) {
                                    Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                                } else {
                                    checkAndRequestBlePermissions(BleBridgeRole.BROADCASTER)
                                    onSendBleTestPing()
                                }
                            },
                            enabled = !isBroadcasting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ActionBlue,
                                contentColor = Color.White,
                                disabledContainerColor = ActionBlue.copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isRu) "📡 Тест связи (30 сек)" else "📡 Test Link (30s)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (ble.role == BleBridgeRole.OBSERVER) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (boostRemaining > 0) "⚡" else "📻", fontSize = 16.sp)
                                    val scanStatusText = when {
                                        !isBtOn -> if (isRu) "Bluetooth выключен" else "Bluetooth is off"
                                        boostRemaining > 0 -> if (isRu) "Активный поиск вещателя (${boostRemaining}с)" else "Boost scan active (${boostRemaining}s)"
                                        isScanning -> if (isRu) "Приёмник активен (фоновый приём)" else "Observer active (balanced scan)"
                                        else -> if (isRu) "Ожидание разрешений сканера" else "Waiting for scanner permissions"
                                    }
                                    Text(
                                        text = scanStatusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (boostRemaining > 0) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val contactTs = if (ble.lastRadioContactMs > 0L) ble.lastRadioContactMs else ble.lastPacketTimestamp
                                if (contactTs > 0L) {
                                    val ageMinutes = ((System.currentTimeMillis() - contactTs) / 60000L).coerceAtLeast(0)
                                    val ageStr = when {
                                        ageMinutes == 0L -> if (isRu) "только что" else "just now"
                                        ageMinutes < 60L -> if (isRu) "$ageMinutes мин назад" else "${ageMinutes}m ago"
                                        ageMinutes < 1440L -> { val h = ageMinutes / 60; if (isRu) "$h ч назад" else "${h}h ago" }
                                        ageMinutes < 365L * 1440L -> { val d = ageMinutes / 1440; if (isRu) "$d дн назад" else "${d}d ago" }
                                        else -> { val y = ageMinutes / (365L * 1440L); if (isRu) "$y лет назад" else "${y}y ago" }
                                    }
                                    val signalQuality = when {
                                        ble.lastRssi >= -70 -> if (isRu) "отличный" else "excellent"
                                        ble.lastRssi >= -85 -> if (isRu) "хороший" else "good"
                                        ble.lastRssi >= -95 -> if (isRu) "слабый" else "weak"
                                        else -> if (isRu) "на грани потери" else "critical"
                                    }
                                    Text(
                                        text = if (isRu) "Последний радиоконтакт: $ageStr" else "Last radio contact: $ageStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isRu) "Уровень сигнала: ${ble.lastRssi} dBm ($signalQuality)" else "Signal strength: ${ble.lastRssi} dBm ($signalQuality)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (!isBtOn) {
                                    Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                                } else {
                                    checkAndRequestBlePermissions(BleBridgeRole.OBSERVER)
                                    onTriggerBleObserverBoost()
                                }
                            },
                            enabled = boostRemaining <= 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryEmerald,
                                contentColor = Color.White,
                                disabledContainerColor = PrimaryEmerald.copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (boostRemaining > 0) {
                                    if (isRu) "⚡ Активный поиск (${boostRemaining}с)..." else "⚡ Boosting (${boostRemaining}s)..."
                                } else {
                                    if (isRu) "⚡ Ускорить поиск пакета (30 сек)" else "⚡ Boost Scan (30s)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XdripLanFollowerCard(
    settings: UserSettings,
    isRu: Boolean,
    isLanCardExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    highlightLan: Boolean = false,
    highlightBorderAlpha: Float = 1f,
    onUpdateLanSettings: (XdripLanSettings) -> Unit,
    onShowLanSettingsDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val xdripLan = settings.xdripLanSettings
    val lanStatus by XdripLanManager.statusFlow.collectAsState()
    val isLanDiscovering by XdripLanManager.isDiscoveringFlow.collectAsState()
    var isTestingLan by remember { mutableStateOf(false) }
    var lanTestStatus by remember { mutableStateOf<String?>(null) }
    var isLanTestSuccess by remember { mutableStateOf(false) }
    val lanScope = rememberCoroutineScope()

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (highlightLan) PrimaryEmerald.copy(alpha = highlightBorderAlpha) else MaterialTheme.colorScheme.outline,
        borderWidth = if (highlightLan) 2.2.dp else 1.dp,
        backgroundColor = if (highlightLan) PrimaryEmerald.copy(alpha = 0.08f * highlightBorderAlpha) else MaterialTheme.colorScheme.surface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(text = "📡", fontSize = 22.sp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Wi-Fi LAN Follower",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (xdripLan.isEnabled) PrimaryEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.8.dp, if (xdripLan.isEnabled) PrimaryEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (xdripLan.isEnabled) "xDrip+" else "✖️",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (xdripLan.isEnabled) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRu) "Прямой приём от мастера xDrip+ (порт 17580)" else "Direct xDrip+ master feed (port 17580)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Switch(
                        checked = xdripLan.isEnabled,
                        onCheckedChange = { isChecked ->
                            onUpdateLanSettings(xdripLan.copy(isEnabled = isChecked))
                            if (isChecked && !isLanCardExpanded) {
                                onToggleExpanded()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryEmerald
                        )
                    )

                    Icon(
                        imageVector = if (isLanCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isLanCardExpanded) "Collapse" else "Expand",
                        tint = ActionBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isLanCardExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowLanSettingsDialog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRu) "IP адрес мастера" else "Master IP Address",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (xdripLan.masterHost.isNotBlank()) "${xdripLan.masterHost}:${xdripLan.port}"
                                    else if (xdripLan.isAutoDiscovery) (if (isRu) "Автопоиск в подсети..." else "Auto-discovering...")
                                    else (if (isRu) "Не задан (нажмите для ввода)" else "Not set (tap to enter)"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (xdripLan.masterHost.isNotBlank()) MaterialTheme.colorScheme.onSurface else ActionBlue
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Host",
                                tint = ActionBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (lanStatus.state) {
                            LanConnectionState.CONNECTED -> PrimaryEmerald.copy(alpha = 0.12f)
                            LanConnectionState.CONNECTING -> ActionBlue.copy(alpha = 0.12f)
                            LanConnectionState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (lanStatus.state) {
                                LanConnectionState.CONNECTED -> PrimaryEmerald.copy(alpha = 0.3f)
                                LanConnectionState.CONNECTING -> ActionBlue.copy(alpha = 0.3f)
                                LanConnectionState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val stateIcon = when (lanStatus.state) {
                                    LanConnectionState.CONNECTED -> "🟢"
                                    LanConnectionState.CONNECTING -> "🟡"
                                    LanConnectionState.ERROR -> "🔴"
                                    else -> "⚪"
                                }
                                Text(stateIcon, fontSize = 12.sp)
                                Text(
                                    text = when (lanStatus.state) {
                                        LanConnectionState.CONNECTED -> if (isRu) "Связь установлена" else "Connected"
                                        LanConnectionState.CONNECTING -> if (isRu) "Подключение..." else "Connecting..."
                                        LanConnectionState.ERROR -> lanStatus.errorMessage ?: (if (isRu) "Ошибка связи" else "Connection error")
                                        LanConnectionState.DISCONNECTED -> if (isRu) "Ожидание Wi-Fi" else "Awaiting Wi-Fi"
                                        LanConnectionState.DISABLED -> if (isRu) "Приём выключен" else "Disabled"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (lanStatus.state == LanConnectionState.CONNECTED) {
                                if (lanStatus.masterBattery != null) {
                                    Text(
                                        text = if (isRu) "Батарея мастера: ${lanStatus.masterBattery}%" else "Master Battery: ${lanStatus.masterBattery}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (lanStatus.lastSuccessTimestamp > 0L) {
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lanStatus.lastSuccessTimestamp))
                                    Text(
                                        text = if (isRu) "Последний замер получен: $timeStr" else "Last reading received: $timeStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTestingLan = true
                                lanTestStatus = null
                                lanScope.launch {
                                    val res = XdripLanClient.testConnection(xdripLan)
                                    isTestingLan = false
                                    if (res.isSuccess) {
                                        val data = res.getOrNull()!!
                                        isLanTestSuccess = true
                                        lanTestStatus = if (isRu) "OK (${data.responseTimeMs} мс, ${data.masterBattery ?: "?"}%)"
                                        else "OK (${data.responseTimeMs} ms, ${data.masterBattery ?: "?"}%)"
                                    } else {
                                        isLanTestSuccess = false
                                        lanTestStatus = res.exceptionOrNull()?.message ?: (if (isRu) "Ошибка" else "Error")
                                    }
                                }
                            },
                            enabled = !isTestingLan && xdripLan.masterHost.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingLan) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (isRu) "Проверить связь" else "Ping Master", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                lanScope.launch {
                                    XdripLanManager.discoverMaster()
                                }
                            },
                            enabled = !isLanDiscovering,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLanDiscovering) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(if (isRu) "Найти мастера" else "Discover", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (lanTestStatus != null) {
                        Text(
                            text = lanTestStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLanTestSuccess) PrimaryEmerald else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (isRu)
                            "💡 Прямой опрос мастера xDrip+ по Wi-Fi или точке доступа (порт 17580). Автоматический поиск мастера в подсети при подключении к одной сети Wi-Fi/Hotspot."
                        else
                            "💡 Direct xDrip+ master feed via Wi-Fi or Hotspot (port 17580). Automatic master discovery in LAN when sharing the same Wi-Fi/Hotspot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NightscoutSyncCard(
    settings: UserSettings,
    isRu: Boolean,
    onUpdateNightscoutSettings: (NightscoutSettings) -> Unit,
    onShowNightscoutDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nightscout = settings.nightscoutSettings
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var isTestingNs by remember { mutableStateOf(false) }
    var nsTestStatus by remember { mutableStateOf<String?>(null) }
    var isNsTestSuccess by remember { mutableStateOf(false) }
    val nsScope = rememberCoroutineScope()

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(text = "🌐", fontSize = 18.sp)
                    Column {
                        Text(
                            text = if (isRu) "Сервер синхронизации" else "Nightscout Sync Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ActionBlue
                        )
                        Text(
                            text = if (isRu) "Nightscout REST API (микро-бэкенд)" else "Nightscout REST API (micro-backend)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = nightscout.isEnabled,
                        onCheckedChange = { onUpdateNightscoutSettings(nightscout.copy(isEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            tint = ActionBlue
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Адрес сервера:" else "Server URL:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (nightscout.serverUrl.isNotBlank()) nightscout.serverUrl else if (isRu) "Не задан" else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (nightscout.serverUrl.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "API Secret:" else "API Secret:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (nightscout.apiSecret.isNotBlank()) "••••••••" else if (isRu) "Без пароля" else "None",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Подтверждение через xDrip:" else "xDrip confirmation:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (nightscout.requireXdripConfirmation) (if (isRu) "Включено" else "Enabled") else (if (isRu) "Выключено" else "Disabled"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (nightscout.requireXdripConfirmation) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRu) "Получение сахаров:" else "Glucose download:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (nightscout.downloadGlucose) (if (isRu) "Cloud Follower" else "Enabled") else (if (isRu) "Выключено" else "Disabled"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (nightscout.downloadGlucose) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (nsTestStatus != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNsTestSuccess) PrimaryEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isNsTestSuccess) PrimaryEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = nsTestStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isNsTestSuccess) PrimaryEmerald else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onShowNightscoutDialog() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ActionBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRu) "Настроить" else "Configure",
                        style = MaterialTheme.typography.labelLarge,
                        color = ActionBlue
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isTestingNs && nightscout.serverUrl.isNotBlank()) {
                            if (nightscout.serverUrl.isBlank()) {
                                nsTestStatus = if (isRu) "Укажите адрес сервера" else "Set server URL first"
                                isNsTestSuccess = false
                                return@clickable
                            }
                            isTestingNs = true
                            nsTestStatus = null
                            nsScope.launch {
                                val res = NightscoutUploadManager.checkConnection(nightscout.serverUrl, nightscout.apiSecret)
                                isTestingNs = false
                                if (res.isSuccess) {
                                    val version = res.getOrNull() ?: "OK"
                                    nsTestStatus = if (isRu) "Связь успешна (версия: $version)" else "Connected (version: $version)"
                                    isNsTestSuccess = true
                                } else {
                                    nsTestStatus = res.exceptionOrNull()?.message ?: (if (isRu) "Ошибка подключения" else "Connection error")
                                    isNsTestSuccess = false
                                }
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = if (nightscout.serverUrl.isNotBlank()) 0.15f else 0.05f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isTestingNs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Связь..." else "Pinging...",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryEmerald
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRu) "Проверить" else "Ping",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryEmerald
                            )
                        }
                    }
                }
            }

            Text(
                text = if (isRu)
                    "💡 Позволяет использовать TIRUp как Cloud Follower: скачивает сахара, историю и батарейку мастера с сервера Nightscout при отсутствии BLE/Wi-Fi, а также передаёт введённые лечения (инсулин, углеводы, замеры)."
                else
                    "💡 Enables TIRUp as a Cloud Follower: pulls glucose, history, and master battery from Nightscout when BLE/Wi-Fi is unavailable, and dispatches entered treatments (insulin, carbs, BG checks).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
                }
            }
        }
    }
}

@Composable
private fun BleCountdownText(
    latestReadingTimestamp: Long?,
    isRu: Boolean
) {
    val nextTimerStr = if (latestReadingTimestamp != null) {
        val nextDueMs = latestReadingTimestamp + 5 * 60 * 1000L
        var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(latestReadingTimestamp) {
            while (true) {
                delay(1000L)
                nowMs = System.currentTimeMillis()
            }
        }
        val diffSec = ((nextDueMs - nowMs) / 1000L).coerceAtLeast(0L)
        if (diffSec > 0) {
            String.format(Locale.US, "%d:%02d", diffSec / 60, diffSec % 60)
        } else {
            if (isRu) "с минуты на минуту" else "any moment"
        }
    } else {
        if (isRu) "ожидание замера" else "awaiting reading"
    }
    Text(
        text = if (isRu) "Следующий импульс через ~$nextTimerStr (при замере)"
        else "Next pulse in ~$nextTimerStr (upon reading)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
