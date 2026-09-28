package com.tirup.app.presentation.settings

import com.tirup.app.presentation.settings.dialogs.BleBridgeHelpDialog
import com.tirup.app.presentation.settings.dialogs.BleFamilyPinDialog
import com.tirup.app.presentation.settings.dialogs.BleLongRangeConfirmDialog
import com.tirup.app.presentation.settings.dialogs.BleRangeHelpDialog
import com.tirup.app.presentation.settings.dialogs.CriticalHypoSafetyDialog
import com.tirup.app.presentation.settings.dialogs.CriticalThresholdDialog
import com.tirup.app.presentation.settings.dialogs.Hba1cHistoryDialog
import com.tirup.app.presentation.settings.dialogs.MainThresholdDialog
import com.tirup.app.presentation.settings.dialogs.PatientProfileEditDialog
import com.tirup.app.presentation.settings.dialogs.PatientProfileSummaryCard
import com.tirup.app.presentation.settings.dialogs.PredictiveHorizonDialog
import com.tirup.app.presentation.settings.dialogs.PredictiveInfoDialog
import com.tirup.app.presentation.settings.dialogs.YearEndDigestDialog
import com.tirup.app.presentation.settings.dialogs.NightscoutSettingsDialog
import com.tirup.app.presentation.settings.dialogs.XdripLanSettingsDialog
import com.tirup.app.data.network.NightscoutUploadManager
import com.tirup.app.data.network.XdripLanClient
import com.tirup.app.data.network.XdripLanManager
import com.tirup.app.presentation.settings.sections.AlertsConfigSection
import com.tirup.app.presentation.settings.sections.DeviceRoleCard
import com.tirup.app.presentation.settings.sections.EmergencySmsCard
import com.tirup.app.presentation.settings.sections.BleBridgeCard
import com.tirup.app.presentation.settings.sections.NightscoutSyncCard
import com.tirup.app.presentation.settings.sections.XdripLanFollowerCard
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wifi



import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import kotlin.math.abs
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.scale
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tirup.app.presentation.reports.openSavedFileFolder
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
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tirup.app.R
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.ble.BleBroadcaster
import com.tirup.app.data.ble.BleObserverManager
import com.tirup.app.data.ble.BlePacketCodec
import com.tirup.app.domain.calculator.CarbRecommendationCalculator
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.BmiCategory
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.LabHba1cRecord
import com.tirup.app.domain.model.PatientProfile
import androidx.compose.ui.text.style.TextOverflow
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.localizeDiabetesType
import com.tirup.app.domain.model.localizeTherapyType
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.components.HelpAndDisclaimerDialog
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    target: String? = null,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.userSettings
    val isRu = settings.language.equals("RU", ignoreCase = true)
    val profile = settings.patientProfile
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var isBleCardExpanded by rememberSaveable { mutableStateOf(false) }
    var isLanCardExpanded by rememberSaveable { mutableStateOf(false) }
    var isSmsCardExpanded by rememberSaveable { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showCriticalHypoSafetyDialog by rememberSaveable { mutableStateOf(false) }
    var showCriticalThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var showMainThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var showPredictiveHorizonDialog by rememberSaveable { mutableStateOf(false) }
    var showPredictiveInfoDialog by rememberSaveable { mutableStateOf(false) }
    var masterOffHintVisible by rememberSaveable { mutableStateOf(false) }
    var showBleHelpModal by rememberSaveable { mutableStateOf(false) }
    var showBlePinDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreOptionsModal by rememberSaveable { mutableStateOf(false) }
    var showBleRangeHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showNightscoutDialog by rememberSaveable { mutableStateOf(false) }
    var showXdripLanDialog by rememberSaveable { mutableStateOf(false) }
    val isDevTestsUnlocked by viewModel.isDevTestsUnlocked.collectAsState()
    var devTapCount by remember { mutableStateOf(0) }
    var lastDevTapTime by remember { mutableStateOf(0L) }
    var bleRangeCooldownSec by remember { mutableStateOf(0) }
    val currentlyPlayingTag by com.tirup.app.data.alert.MedicalSoundPlayer.currentlyPlayingTag.collectAsState()
    var lastSoundClickTime by remember { mutableStateOf(0L) }
    val handleSoundClick: (String, () -> Unit) -> Unit = { tag, action ->
        val now = System.currentTimeMillis()
        if (now - lastSoundClickTime >= 400L) {
            lastSoundClickTime = now
            if (currentlyPlayingTag == tag) {
                viewModel.stopAlertSounds()
            } else {
                viewModel.stopAlertSounds()
                action()
            }
        }
    }

    DisposableEffect(Unit) {
        viewModel.checkDevTestsLockOnResume()
        onDispose {
            viewModel.onSettingsScreenDisposed()
        }
    }

    LaunchedEffect(bleRangeCooldownSec) {
        if (bleRangeCooldownSec > 0) {
            delay(1000L)
            bleRangeCooldownSec -= 1
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val listState = rememberLazyListState()
    var highlightBle by remember { mutableStateOf(false) }


    LaunchedEffect(target) {
        if (target == "ble_bridge") {
            showAdvancedSettings = true
            isBleCardExpanded = true
            delay(150L)
            listState.animateScrollToItem(4)
            highlightBle = true
            delay(2800L)
            highlightBle = false
        } else if (target == "wifi_lan" || target == "xdrip_lan") {
            showAdvancedSettings = true
            isLanCardExpanded = true
            delay(150L)
            listState.animateScrollToItem(5)
        } else if (target == "hba1c") {
            viewModel.toggleHba1cDialog(true)
        }
        if (target == "year_end") {
            viewModel.setShowYearEndDialog(true)
        }
    }

    val highlightAnim = rememberInfiniteTransition(label = "ble_highlight")
    val highlightBorderAlpha by highlightAnim.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ble_border"
    )

    var hasSendSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasReceiveSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }
    var hasAttemptedSmsRequest by rememberSaveable { mutableStateOf(false) }

    fun refreshSmsPermissions() {
        hasSendSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        hasReceiveSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }

    val lifecycleOwner = context as? LifecycleOwner
    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshSmsPermissions()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            if (hasOverlayPermission) {
                Toast.makeText(context, if (isRu) "Разрешение «Поверх других приложений» получено" else "Overlay permission granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasAttemptedSmsRequest = true
        refreshSmsPermissions()
        val sendGranted = perms[Manifest.permission.SEND_SMS] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
        val receiveGranted = perms[Manifest.permission.RECEIVE_SMS] ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)

        if (sendGranted && receiveGranted) {
            Toast.makeText(context, if (isRu) "Разрешения на отправку и приём SMS предоставлены" else "SMS send and receive permissions granted", Toast.LENGTH_SHORT).show()
        } else if (sendGranted && !receiveGranted) {
            Toast.makeText(context, if (isRu) "Предоставлено только разрешение на отправку SMS. Приём SMS отклонён." else "Only SMS send granted. SMS receive was denied.", Toast.LENGTH_LONG).show()
        } else if (!sendGranted && receiveGranted) {
            Toast.makeText(context, if (isRu) "Предоставлено только разрешение на приём SMS. Отправка SMS отклонена." else "Only SMS receive granted. SMS send was denied.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, if (isRu) "Разрешения на SMS отклонены" else "SMS permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, if (isRu) "Доступ к геопозиции предоставлен" else "Location permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(context, if (isRu) "Bluetooth-разрешения предоставлены" else "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.restartBleSync()
        } else {
            Toast.makeText(context, if (isRu) "Для работы BLE-моста требуется доступ к Bluetooth и геолокации" else "Bluetooth and Location permissions required for BLE Bridge", Toast.LENGTH_LONG).show()
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val backupFolderUri = remember {
        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FTIRUp%2FBackups")
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, backupFolderUri)
                }
                return intent
            }
        }
    ) { uri ->
        if (uri != null) {
            viewModel.prepareRestoreFromUri(uri)
        }
    }

    fun checkAndRequestBlePermissions(role: BleBridgeRole) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (role == BleBridgeRole.BROADCASTER) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            } else if (role == BleBridgeRole.OBSERVER) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_SCAN)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            if (needed.isNotEmpty()) {
                blePermissionLauncher.launch(needed.toTypedArray())
            }
        } else if (role == BleBridgeRole.OBSERVER) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                blePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    val latestReading by viewModel.latestReading.collectAsState()

    var testSosSmsCooldownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testSosSmsCooldownSec) {
        if (testSosSmsCooldownSec > 0) {
            delay(1000L)
            testSosSmsCooldownSec -= 1
        }
    }

    var testRescueCountdownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testRescueCountdownSec) {
        if (testRescueCountdownSec > 0) {
            delay(1000L)
            testRescueCountdownSec -= 1
        }
    }

    var testCaregiverSosCountdownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testCaregiverSosCountdownSec) {
        if (testCaregiverSosCountdownSec > 0) {
            delay(1000L)
            testCaregiverSosCountdownSec -= 1
        }
    }

    var testHeadsUpCountdownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testHeadsUpCountdownSec) {
        if (testHeadsUpCountdownSec > 0) {
            delay(1000L)
            testHeadsUpCountdownSec -= 1
        }
    }

    var showSosSmsSendConfirmDialog by remember { mutableStateOf(false) }
    var isRoleSectionExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.infoMessage) {
        val msg = state.infoMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearInfoMessage()
        }
    }

    LaunchedEffect(masterOffHintVisible) {
        if (masterOffHintVisible) {
            delay(3000L)
            masterOffHintVisible = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.SavedToDownloads -> {
                    val msg = event.message ?: if (isRu) "Файл сохранён в Загрузки" else "File saved to Downloads"
                    val actionLabel = if (isRu) "Открыть" else "Open"
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = actionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openSavedFileFolder(context, event.filePath)
                    }
                }
                is SettingsEvent.Info -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SettingsEvent.ShareFile -> {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            event.file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, event.title).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(if (isRu) "Ошибка отправки: ${e.message}" else "Share failed: ${e.message}")
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Top Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Настройки сохраняются автоматически" else "Settings are saved automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = "Help",
                        tint = ActionBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // Top Card: Patient Profile Summary (Opens Edit Dialog on Tap)
        item {
            PatientProfileSummaryCard(
                profile = profile,
                isRu = isRu,
                onEditClick = { showProfileDialog = true }
            )
        }

        // Device Role Card: Master vs Follower
        item {
            DeviceRoleCard(
                settings = settings,
                isRu = isRu,
                isRoleSectionExpanded = isRoleSectionExpanded,
                onToggleExpanded = { isRoleSectionExpanded = !isRoleSectionExpanded },
                onUpdateAlertSettings = { viewModel.updateAlertSettings(it) }
            )
        }

        // Section 1: Display Settings (Always Visible at the Top)
        item {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.section_preferences),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Language Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.pref_language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = "Русский",
                                isSelected = settings.language.equals("RU", ignoreCase = true),
                                onClick = { viewModel.setLanguage("RU") }
                            )
                            LanguageChip(
                                label = "English",
                                isSelected = settings.language.equals("EN", ignoreCase = true),
                                onClick = { viewModel.setLanguage("EN") }
                            )
                        }
                    }

                    // Unit Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.pref_unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = "mmol/L",
                                isSelected = settings.unit == GlucoseUnit.MMOL_L,
                                onClick = { viewModel.setUnit(GlucoseUnit.MMOL_L) }
                            )
                            LanguageChip(
                                label = "mg/dL",
                                isSelected = settings.unit == GlucoseUnit.MG_DL,
                                onClick = { viewModel.setUnit(GlucoseUnit.MG_DL) }
                            )
                        }
                    }

                    // Theme Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isRu) "Тема" else "Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LanguageChip(
                                label = if (isRu) "🌙 Тёмная" else "🌙 Dark",
                                isSelected = settings.themeMode == com.tirup.app.domain.model.ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.DARK) }
                            )
                            LanguageChip(
                                label = if (isRu) "☀️ Светлая" else "☀️ Light",
                                isSelected = settings.themeMode != com.tirup.app.domain.model.ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(com.tirup.app.domain.model.ThemeMode.LIGHT) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Show Treatments On Chart Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text("💉🍽️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isRu) "Метки болюсов и еды на графике" else "Insulin & Meal Marks on Chart",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                            }
                        }
                        Switch(
                            checked = settings.showTreatmentsOnChart,
                            onCheckedChange = { viewModel.setShowTreatmentsOnChart(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text("🔮", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isRu) "Линия прогноза на графике (25 мин)" else "Trend Forecast on Chart (25m)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "Фиолетовые точки и пунктир экстраполяции" else "Purple points & extrapolation trajectory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.showPredictionOnChart,
                            onCheckedChange = { viewModel.setShowPredictionOnChart(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }
                }
            }
        }



        // Section 2: Alerts & Tiers Configuration
        item {
            AlertsConfigSection(
                settings = settings,
                isRu = isRu,
                currentlyPlayingTag = currentlyPlayingTag,
                onUpdateAlertSettings = { viewModel.updateAlertSettings(it) },
                onTestAlert = { viewModel.testAlert(it) },
                onTestCaregiverSosScreen = { viewModel.testCaregiverSosScreen() },
                onPlayTestSound = { viewModel.playTestSound(it) },
                onSoundClick = handleSoundClick,
                onShowCriticalHypoSafetyDialog = { showCriticalHypoSafetyDialog = true },
                onShowCriticalThresholdDialog = { showCriticalThresholdDialog = true },
                onShowMainThresholdDialog = { showMainThresholdDialog = true },
                onShowPredictiveHorizonDialog = { showPredictiveHorizonDialog = true },
                onMasterOffHint = { masterOffHintVisible = true }
            )
        }

        // Section 3: Grouped Additional Settings Frame
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ActionBlue.copy(alpha = 0.04f),
                border = BorderStroke(1.4.dp, ActionBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedSettings = !showAdvancedSettings }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = ActionBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isRu) "Дополнительные настройки" else "Advanced Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRu) "BLE-мост, экстренное SMS, дайджест недели, время сна, автобэкап"
                                           else "BLE Bridge, emergency SMS, weekly digest, sleep window, auto-backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAdvancedSettings) "Collapse" else "Expand",
                            tint = ActionBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (showAdvancedSettings) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            BleBridgeCard(
                                settings = settings,
                                isRu = isRu,
                                isBleCardExpanded = isBleCardExpanded,
                                onToggleExpanded = { isBleCardExpanded = !isBleCardExpanded },
                                highlightBle = highlightBle,
                                highlightBorderAlpha = highlightBorderAlpha,
                                latestReading = latestReading,
                                blePermissionsLauncher = blePermissionLauncher,
                                onUpdateBleSettings = { viewModel.updateBleBridgeSettings(it) },
                                onSendBleTestPing = { viewModel.sendBleTestPing() },
                                onTriggerBleObserverBoost = { viewModel.boostBleObserverScan() },
                                onShowBleHelpModal = { showBleHelpModal = true },
                                onShowBlePinDialog = { showBlePinDialog = true }
                            )

                            XdripLanFollowerCard(
                                settings = settings,
                                isRu = isRu,
                                isLanCardExpanded = isLanCardExpanded,
                                onToggleExpanded = { isLanCardExpanded = !isLanCardExpanded },
                                onUpdateLanSettings = { viewModel.updateXdripLanSettings(it) },
                                onShowLanSettingsDialog = { showXdripLanDialog = true }
                            )

            EmergencySmsCard(
                settings = settings,
                isRu = isRu,
                isSmsCardExpanded = isSmsCardExpanded,
                onToggleExpanded = { isSmsCardExpanded = !isSmsCardExpanded },
                hasSendSmsPermission = hasSendSmsPermission,
                hasReceiveSmsPermission = hasReceiveSmsPermission,
                hasOverlayPermission = hasOverlayPermission,
                smsPermissionsLauncher = smsPermissionsLauncher,
                overlayPermissionLauncher = overlayPermissionLauncher,
                locationPermissionLauncher = locationPermissionLauncher,
                testCaregiverSosCountdownSec = testCaregiverSosCountdownSec,
                onStartCaregiverSosTest = {
                    testCaregiverSosCountdownSec = 5
                    viewModel.startCaregiverSosTestCountdown(5)
                },
                onCancelCaregiverSosTest = {
                    testCaregiverSosCountdownSec = 0
                    viewModel.cancelCaregiverSosTest()
                },
                onSendTestEmergencySms = { viewModel.sendTestEmergencySms() },
                onUpdateAlertSettings = { viewModel.updateAlertSettings(it) }
            )

                // Section: Weekly Sunday Digest
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isRu) "📅 Воскресный дайджест" else "📅 Sunday Digest",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRu) "Еженедельный клинический отчёт каждое воскресенье в 20:00 (динамика TIR/TING, вариабельность CV, гипо, сравнение с прошлой неделей)"
                            else "Weekly clinical summary every Sunday at 8:00 PM (TIR/TING dynamics, CV, hypos, and week-over-week comparison)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = settings.isWeeklyDigestEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.setWeeklyDigestEnabled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

                if (settings.isWeeklyDigestEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            com.tirup.app.data.worker.WeeklyDigestWorker.triggerImmediately(context)
                            Toast.makeText(
                                context,
                                if (isRu) "Формируем отчёт дайджеста... Протяните шторку уведомлений"
                                else "Generating weekly digest... Check notifications shade",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                    ) {
                        Text(
                            text = if (isRu) "Сформировать сейчас вручную" else "Generate digest now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section: Device Reminders
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isRu) "Напоминания об устройствах" else "Device Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRu) "Уведомления о замене сенсора CGM, инфузионного набора и ланцета"
                            else "Notifications for CGM sensor, infusion set, and lancet changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = settings.isDeviceRemindersEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.setDeviceRemindersEnabled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }

                if (settings.isDeviceRemindersEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Sensor checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.setSensorReminderEnabled(!settings.isSensorReminderEnabled) }
                        ) {
                            Checkbox(
                                checked = settings.isSensorReminderEnabled,
                                onCheckedChange = { viewModel.setSensorReminderEnabled(it) },
                                colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                                modifier = Modifier
                                    .scale(0.85f)
                                    .size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isRu) "Сенсор" else "Sensor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Infusion set checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.setPumpReminderEnabled(!settings.isPumpReminderEnabled) }
                        ) {
                            Checkbox(
                                checked = settings.isPumpReminderEnabled,
                                onCheckedChange = { viewModel.setPumpReminderEnabled(it) },
                                colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                                modifier = Modifier
                                    .scale(0.85f)
                                    .size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isRu) "Инф. набор" else "Inf. set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Lancet checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.setLancetReminderEnabled(!settings.isLancetReminderEnabled) }
                        ) {
                            Checkbox(
                                checked = settings.isLancetReminderEnabled,
                                onCheckedChange = { viewModel.setLancetReminderEnabled(it) },
                                colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                                modifier = Modifier
                                    .scale(0.85f)
                                    .size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isRu) "Ланцет" else "Lancet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // HbA1c 90-day Checkup Reminder Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isRu) "Контроль HbA1c (раз в 90 дней)" else "HbA1c Checkup (every 90 days)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Напоминание о сдаче крови на гликированный гемоглобин и сверка с 90-дневным GMI"
                                   else "Quarterly reminder to test lab HbA1c and correlate with 90-day sensor GMI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = settings.isHba1cReminderEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.setHba1cReminderEnabled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionBlue
                        )
                    )
                }
            }
        }


        // Section 3: Clinical Targets & Sleep Window
        BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isRu) "Клинические стандарты (ATTD / ADA)" else "Clinical Standards (ATTD / ADA)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Informational standard badge
                    val isMmol = settings.unit == GlucoseUnit.MMOL_L
                    val tirRangeStr = if (isMmol) (if (isRu) "3.9 — 10.0 ммоль/л" else "3.9 — 10.0 mmol/L") else "70 — 180 mg/dL"
                    val tingRangeStr = if (isMmol) (if (isRu) "3.9 — 7.8 ммоль/л" else "3.9 — 7.8 mmol/L") else "70 — 140 mg/dL"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isRu) "TIR (цель ≥70%):" else "TIR (target ≥70%):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tirRangeStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isRu) "TING (цель ≥50%):" else "TING (target ≥50%):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tingRangeStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ColorTight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isRu) "Ночной профиль (окно сна)" else "Night Profile (Sleep Window)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRu) "Приблизительные часы сна (с шагом в 1 час)" else "Approximate sleep hours (1-hour step)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Night Profile Hours (Sleep window)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DropdownHourSelector(
                            label = if (isRu) "Начало сна" else "Sleep Start",
                            selectedHour = settings.nightStartHour,
                            isRu = isRu,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newStart ->
                                viewModel.autoUpdateNightHours(
                                    nightStart = newStart,
                                    nightEnd = settings.nightEndHour
                                )
                            }
                        )

                        DropdownHourSelector(
                            label = if (isRu) "Конец сна" else "Sleep End",
                            selectedHour = settings.nightEndHour,
                            isRu = isRu,
                            modifier = Modifier.weight(1f),
                            onHourSelected = { newEnd ->
                                viewModel.autoUpdateNightHours(
                                    nightStart = settings.nightStartHour,
                                    nightEnd = newEnd
                                )
                            }
                        )
                    }
                }
            }

        // Section: Lockscreen Notification (Постоянное уведомление на экране блокировки)
        BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Уведомление на экране блокировки" else "Lockscreen Notification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Постоянный статус с сахаром, стрелкой тренда и TIR на экране блокировки и в панели уведомлений"
                                else "Ongoing status with current glucose, trend arrow and TIR on lockscreen and notification shade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = settings.isLockscreenNotificationEnabled,
                            onCheckedChange = { viewModel.setLockscreenNotificationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }
                }
            }

        // Section: Floating Glucose Bubble (Плавающий пузырёк поверх всех окон)
        BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Плавающий пузырёк с сахаром" else "Floating Glucose Bubble",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Появляется только вне нормы (<3.9 или >10.0). При гипо (<3.9) пульсирует волнами. Тап глушит звук и скрывает на 15 мин (гипо) / 45 мин (гипер, до 60 мин при IoB). Свободно перемещается"
                                else "Shown only out of range (<3.9 or >10.0). Ripple pulse waves on hypo (<3.9). Tap silences and snoozes for 15m (hypo) / 45m (hyper, up to 60m with IoB). Draggable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = settings.isFloatingBubbleEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.toggleFloatingBubble(true)
                                    }
                                } else {
                                    viewModel.toggleFloatingBubble(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }

                    if (settings.isFloatingBubbleEnabled) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRu) "Отображать постоянно" else "Always visible",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRu) "В норме (3.9–10.0) — мини-кружок (50%), тап открывает TIRUp, удержание 3 сек отключает. Вне нормы — тревожный режим (тап глушит/снузит, удержание открывает TIRUp)"
                                    else "In target (3.9–10.0) — mini-circle (50%), tap opens TIRUp, 3s hold turns off. Out of range — alarm mode (tap silences/snoozes, hold opens TIRUp)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.isFloatingBubbleAlwaysVisible,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleFloatingBubbleAlwaysVisible(isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ActionBlue
                                )
                            )
                        }
                    }
                }
            }

        // Section: Always-On Display (AOD / Ночной монитор)
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            var isAodExpanded by rememberSaveable { mutableStateOf(false) }
            val aod = settings.aodSettings

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAodExpanded = !isAodExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🌙", fontSize = 22.sp)
                        Column {
                            Text(
                                text = if (isRu) "Ночной экран (AoD)" else "Night screen (AoD)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (aod.isEnabled) {
                                    if (isRu) "Активен • ${if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) "Пробуждение при замере (0% батареи)" else "Всегда включен (1% яркости)"}"
                                    else "Enabled • ${if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) "Pulse on update (0% battery)" else "Always on (1% brightness)"}"
                                } else {
                                    if (isRu) "Выключен" else "Disabled"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (aod.isEnabled) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Switch(
                            checked = aod.isEnabled,
                            onCheckedChange = { isEnabled ->
                                viewModel.updateAodSettings(aod.copy(isEnabled = isEnabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                        IconButton(
                            onClick = { isAodExpanded = !isAodExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAodExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isAodExpanded) "Свернуть" else "Развернуть",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isAodExpanded) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // OLED Notice Alert (Adaptive to light/dark themes)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("💡", fontSize = 16.sp)
                            Text(
                                text = if (isRu) "Режим оптимизирован под экраны AMOLED/OLED: абсолютно черный фон (#000000) полностью отключает пиксели матрицы, а микро-смещение Anti-Burn-In защищает экран от выгорания."
                                else "Optimized for AMOLED/OLED: true black background (#000000) turns off matrix pixels, and Anti-Burn-In micro-jitter protects display.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Display Mode Selector
                    Text(
                        text = if (isRu) "Режим работы дисплея:" else "Display Mode:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.updateAodSettings(aod.copy(displayMode = com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE))
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) 1.5.dp else 0.8.dp,
                                color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isRu) "⚡ Просыпаться" else "⚡ Pulse Wake",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.PULSE_ON_UPDATE) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRu) "Экран 0% света. Зажигается на 5 сек при новом сахаре или тапе" else "0% light. Turns on for 5s on new reading or tap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.updateAodSettings(aod.copy(displayMode = com.tirup.app.domain.model.AodDisplayMode.ALWAYS_ON))
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.ALWAYS_ON) ActionBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.ALWAYS_ON) 1.5.dp else 0.8.dp,
                                color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.ALWAYS_ON) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isRu) "👁️ Всегда включен" else "👁️ Always On",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (aod.displayMode == com.tirup.app.domain.model.AodDisplayMode.ALWAYS_ON) ActionBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRu) "Непрерывно горит на минимальной физической яркости (1%)" else "Continuously visible at minimum physical brightness (1%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Auto-start on charger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "🔌 Автозапуск при ночной зарядке" else "🔌 Auto-start on night charge",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRu) "Активировать AOD при подключении зарядного устройства с ${aod.autoChargeStartHour}:00 до ${aod.autoChargeEndHour}:00"
                                else "Launch AOD automatically when plugged in between ${aod.autoChargeStartHour}:00 and ${aod.autoChargeEndHour}:00",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aod.autoChargeEnabled,
                            onCheckedChange = { autoCharge ->
                                viewModel.updateAodSettings(aod.copy(autoChargeEnabled = autoCharge))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }

                    // Launch Test Button
                    Button(
                        onClick = {
                            val aodIntent = Intent(context, com.tirup.app.presentation.aod.AodActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(aodIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isRu) "🌙 Запустить AOD сейчас для проверки" else "🌙 Launch AOD now for test",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Section: Widget Background Opacity with Live Interactive Preview
        BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Прозрачность подложки виджетов" else "Widget Background Opacity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Плавная регулировка прозрачности под ваши обои" else "Adjust transparency to match your home wallpaper",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${settings.widgetBackgroundOpacity}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Live Interactive Preview Box on simulated wallpaper (5x1 Strip Widget)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0F2027),
                                        Color(0xFF203A43),
                                        Color(0xFF2C5364)
                                    )
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A).copy(alpha = settings.widgetBackgroundOpacity / 100f),
                            border = BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = (settings.widgetBackgroundOpacity / 100f) * 0.22f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Left: Glucose + stacked [ Arrow / Delta ]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "5.8",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "→",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                        Text(
                                            text = "+0.2",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                // Divider
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                // 2. TIR & Compensator
                                Column(verticalArrangement = Arrangement.Center) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "TIR: 84%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (isRu) "+1ч 45м" else "+1h 45m",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // 3. Ranges TBR / TAR
                                Column(verticalArrangement = Arrangement.Center) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "TBR: 1%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "TAR: 15%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // 4. Treatments IoB & CoB
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "💉 1.2 U",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        text = "🍞 25g",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }

                                // 5. Telemetry: Age & Battery
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isRu) "2м наз." else "2m ago",
                                        fontSize = 9.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = "🔋 95%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    }

                    // Slider from 0 to 100%
                    Slider(
                        value = settings.widgetBackgroundOpacity.toFloat(),
                        onValueChange = { newVal ->
                            viewModel.updateWidgetBackgroundOpacity(newVal.toInt())
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = ActionBlue,
                            activeTrackColor = ActionBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isRu) "0% (Текст)" else "0% (Text)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isRu) "85% (Стандарт)" else "85% (Default)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isRu) "100% (Глубокий)" else "100% (Solid)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

        // Section 4: Auto-Backup
        BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                            onCheckedChange = { viewModel.toggleAutoBackup(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }

                    // Status and stats
                    val summary = state.backupSummary
                    if (summary != null && summary.readingsCount > 0) {
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
                                val lastDateStr = if (summary.exportedAt > 0L) fmt.format(Date(summary.exportedAt)) else "—"
                                Text(
                                    text = if (isRu) "📦 Сохранённая копия: $lastDateStr" else "📦 Saved backup: $lastDateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryEmerald
                                )
                                Text(
                                    text = if (isRu) "🩸 ${summary.readingsCount} замеров | 💉 ${summary.treatmentsCount} меток терапии"
                                    else "🩸 ${summary.readingsCount} readings | 💉 ${summary.treatmentsCount} treatments",
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

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val hasAllFilesAccess = android.os.Environment.isExternalStorageManager()
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
                                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
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

                    if (state.isBackupInProgress) {
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
                            onClick = { viewModel.createBackupNow() },
                            enabled = !state.isBackupInProgress,
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
                            onClick = { viewModel.shareBackup() },
                            enabled = !state.isBackupInProgress,
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
                                createBackupLauncher.launch("tirup_backup_$dateStr.zip")
                            },
                            enabled = !state.isBackupInProgress,
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
                            onClick = {
                                showRestoreOptionsModal = true
                            },
                            enabled = !state.isRestoreInProgress,
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
                    val activeYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    OutlinedButton(
                        onClick = { viewModel.setShowYearEndDialog(true, activeYear) },
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

        // Section 5: Data Management (Clear Data)
        BentoCard(modifier = Modifier.fillMaxWidth()) {
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
                        onClick = { viewModel.showClearConfirm(true) },
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



                    if (state.infoMessage != null) {
                        Text(
                            text = state.infoMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryEmerald
                        )
                    }
                }
            }

            // Section: Developer Mode - System Testing Block
            if (isDevTestsUnlocked) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🛠️", fontSize = 18.sp)
                            Text(
                                text = if (isRu) "Тестирование систем" else "System Testing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue
                            )
                        }

                        Text(
                            text = if (isRu)
                                "Инструменты проверки тревог и каналов связи:"
                            else
                                "Alert and communication channel testing tools:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val TestButtonAmber = Color(0xFFEAB308)

                        // Test 1: Patient Rescue Screen (5 sec delay with Cancel)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (testRescueCountdownSec == 0) {
                                        Toast.makeText(
                                            context,
                                            if (isRu) "Заблокируйте экран! Экран спасения появится через 5 секунд..."
                                            else "Lock your screen! Rescue screen in 5 seconds...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        testRescueCountdownSec = 5
                                        viewModel.startPatientRescueTestCountdown(5)
                                    }
                                },
                                enabled = testRescueCountdownSec == 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
                            ) {
                                Text(text = "🚨", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (testRescueCountdownSec > 0) {
                                        if (isRu) "Запуск через ${testRescueCountdownSec}с..." else "Starting in ${testRescueCountdownSec}s..."
                                    } else {
                                        if (isRu) "Экран спасения (5 сек)" else "Rescue Screen (5s)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (testRescueCountdownSec == 0) TestButtonAmber
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }

                            if (testRescueCountdownSec > 0) {
                                OutlinedButton(
                                    onClick = {
                                        testRescueCountdownSec = 0
                                        viewModel.cancelPatientRescueTest()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = if (isRu) "Отмена" else "Cancel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Test 2: Follower SOS Screen & Siren preview (5s countdown)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (testCaregiverSosCountdownSec == 0) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                            Toast.makeText(
                                                context,
                                                if (isRu) "⚠️ Разрешение «Поверх других приложений» не дано — экран может не открыться!"
                                                else "⚠️ 'Display over other apps' not granted — screen may not open!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (isRu) "Заблокируйте экран! SOS-сирена включится через 5 секунд..."
                                            else "Lock your screen! SOS siren in 5 seconds...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        testCaregiverSosCountdownSec = 5
                                        viewModel.startCaregiverSosTestCountdown(5)
                                    }
                                },
                                enabled = testCaregiverSosCountdownSec == 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (testCaregiverSosCountdownSec == 0) TestButtonAmber.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (testCaregiverSosCountdownSec == 0) TestButtonAmber
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (testCaregiverSosCountdownSec > 0) {
                                        if (isRu) "🔴 SOS через ${testCaregiverSosCountdownSec}с..."
                                        else "🔴 SOS in ${testCaregiverSosCountdownSec}s..."
                                    } else {
                                        if (isRu) "Экран SOS фоловера (5 сек)" else "Follower SOS screen (5s)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (testCaregiverSosCountdownSec == 0) TestButtonAmber
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            if (testCaregiverSosCountdownSec > 0) {
                                OutlinedButton(
                                    onClick = {
                                        testCaregiverSosCountdownSec = 0
                                        viewModel.cancelCaregiverSosTest()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = if (isRu) "Отмена" else "Cancel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Test 3: Heads-Up Message Screen preview (5s countdown) — moved above SOS-SMS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (testHeadsUpCountdownSec == 0) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                            Toast.makeText(
                                                context,
                                                if (isRu) "⚠️ Без разрешения «Полноэкранные уведомления» сообщение может не появиться!"
                                                else "⚠️ Without 'Full-screen notifications' permission the overlay may not show!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (isRu) "Заблокируйте экран! Важное SMS-сообщение появится через 5 секунд..."
                                            else "Lock your screen! Heads-Up SMS-message in 5 seconds...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        testHeadsUpCountdownSec = 5
                                        viewModel.startHeadsUpTestCountdown(5)
                                    }
                                },
                                enabled = testHeadsUpCountdownSec == 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (testHeadsUpCountdownSec == 0) TestButtonAmber.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = "💬",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (testHeadsUpCountdownSec > 0) {
                                        if (isRu) "Сообщение через ${testHeadsUpCountdownSec}с..."
                                        else "Message in ${testHeadsUpCountdownSec}s..."
                                    } else {
                                        if (isRu) "Экран важное SMS (5 сек)" else "Important SMS screen (5s)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (testHeadsUpCountdownSec == 0) TestButtonAmber
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            if (testHeadsUpCountdownSec > 0) {
                                OutlinedButton(
                                    onClick = {
                                        testHeadsUpCountdownSec = 0
                                        viewModel.cancelHeadsUpTest()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = if (isRu) "Отмена" else "Cancel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Test 4: Caregiver SOS SMS (60 sec cooldown) — with confirmation dialog
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                    smsPermissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                                } else {
                                    showSosSmsSendConfirmDialog = true
                                }
                            },
                            enabled = testSosSmsCooldownSec == 0,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
                        ) {
                            Text(text = "✉️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (testSosSmsCooldownSec > 0) {
                                    if (isRu) "Отправить SOS-SMS (${testSosSmsCooldownSec}с)" else "Send Follower SOS SMS (${testSosSmsCooldownSec}s)"
                                } else {
                                    if (isRu) "Отправить SOS-SMS" else "Send Follower SOS SMS"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (testSosSmsCooldownSec == 0) TestButtonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        // Confirmation dialog for SOS SMS sending
                        if (showSosSmsSendConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showSosSmsSendConfirmDialog = false },
                                icon = { Text(text = "⚠️", fontSize = 28.sp) },
                                title = {
                                    Text(
                                        text = if (isRu) "Отправить тестовое SOS-SMS?" else "Send test SOS SMS?",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        text = if (isRu) "На номера доверенных контактов будут отправлены реальные SMS-сообщения. Убедитесь, что контакты предупреждены о тесте."
                                               else "Real SMS messages will be sent to trusted contact numbers. Make sure contacts are aware this is a test."
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showSosSmsSendConfirmDialog = false
                                            testSosSmsCooldownSec = 60
                                            viewModel.sendCaregiverSosTestSms()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text(if (isRu) "Отправить" else "Send", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showSosSmsSendConfirmDialog = false }) {
                                        Text(if (isRu) "Отмена" else "Cancel")
                                    }
                                }
                            )
                        }

                        Text(
                            text = if (isRu) "💡 Если окно не появляется на заблокированном экране — дайте разрешения: Приложения → Спец. доступ → Полноэкранные уведомления и Всплывающие окна в фоне."
                                   else "💡 If the screen doesn't appear on lockscreen — grant: Apps → Special Access → Full-screen notifications & Background pop-ups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Test 4: BLE Bridge range test (5 sec)
                        val bleRole = settings.bleBridgeSettings.role
                        val bleRangeButtonText = when {
                            bleRangeCooldownSec > 0 -> {
                                if (isRu) "Тест дальности ($bleRangeCooldownSec с)..." else "Range testing (${bleRangeCooldownSec}s)..."
                            }
                            bleRole == BleBridgeRole.OBSERVER -> {
                                if (isRu) "Тест дальности: приём" else "Range Test: Receive"
                            }
                            bleRole == BleBridgeRole.BROADCASTER -> {
                                if (isRu) "Тест дальности: передача" else "Range Test: Broadcast"
                            }
                            else -> {
                                if (isRu) "Тест дальности BLE-моста" else "BLE Bridge Range Test"
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val isBt = com.tirup.app.data.ble.BleBroadcaster.isBluetoothEnabled(context)
                                    if (!isBt) {
                                        Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (bleRole == BleBridgeRole.OBSERVER) {
                                            checkAndRequestBlePermissions(BleBridgeRole.OBSERVER)
                                        } else {
                                            checkAndRequestBlePermissions(BleBridgeRole.BROADCASTER)
                                        }
                                        bleRangeCooldownSec = 5
                                        viewModel.startBleRangeTest(5)
                                    }
                                },
                                enabled = bleRangeCooldownSec == 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TestButtonAmber.copy(alpha = 0.7f))
                            ) {
                                Text(text = "📡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bleRangeButtonText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bleRangeCooldownSec == 0) TestButtonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }

                            IconButton(
                                onClick = { showBleRangeHelpDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = if (isRu) "Информация о тесте дальности" else "Range test info",
                                    tint = ActionBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                NightscoutSyncCard(
                    settings = settings,
                    isRu = isRu,
                    onUpdateNightscoutSettings = { viewModel.updateNightscoutSettings(it) },
                    onShowNightscoutDialog = { showNightscoutDialog = true }
                )

}

        // Section: Collapse Advanced Settings Footer
        Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedSettings = false },
                shape = RoundedCornerShape(12.dp),
                color = ActionBlue.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "Collapse Advanced Settings",
                        tint = ActionBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRu) "Свернуть дополнительные настройки" else "Collapse Advanced Settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = ActionBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
                        }
                    }
                }
            }
        }

        // Section 5: Community Telegram Text Link
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRu) "Telegram-канал проекта — @diakia" else "Project Telegram channel — @diakia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0284C7),
                    style = TextStyle(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/diakia"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                )
            }
        }

        // Section 6: Bottom Back Button
        item {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRu) "Назад" else "Back",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section 7: App Version & Build Information
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - lastDevTapTime > 500L) {
                            devTapCount = 0
                        }
                        lastDevTapTime = now

                        if (!isDevTestsUnlocked) {
                            devTapCount += 1
                            if (devTapCount >= 5) {
                                devTapCount = 0
                                viewModel.unlockDevTests()
                                Toast.makeText(
                                    context,
                                    if (isRu) "🛠️ Режим тестирования систем активирован" else "🛠️ System testing mode activated",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (devTapCount == 4) {
                                // Show hint only on the last tap before unlock
                                Toast.makeText(
                                    context,
                                    if (isRu) "Ещё 1 тап..." else "1 more tap...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            // No toast for taps 1-3 to avoid blocking the tap area
                        }
                    }
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val appVersion = remember(context) { getAppVersionName(context) }
                Text(
                    text = if (isRu) "TIRUp • Версия $appVersion"
                           else "TIRUp • Version $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRu) "Автономный диа-мост и аналитика CGM"
                           else "Autonomous CGM Analytics & Telemetry Bridge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
    }

    if (!state.showYearEndDialog && !state.showHba1cDialog) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        )
    }

    if (showRestoreOptionsModal) {
        val backupSummary = state.backupSummary
        AlertDialog(
            onDismissRequest = { showRestoreOptionsModal = false },
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
                                        showRestoreOptionsModal = false
                                        viewModel.restoreLatestAutoBackup()
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
                            showRestoreOptionsModal = false
                            restoreBackupLauncher.launch(
                                arrayOf("application/zip", "application/json", "text/csv", "text/comma-separated-values", "*/*")
                            )
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
                TextButton(onClick = { showRestoreOptionsModal = false }) {
                    Text(if (isRu) "Закрыть" else "Close")
                }
            }
        )
    }

    val pendingRestore = state.pendingRestoreSummary
    if (pendingRestore != null) {
        val summary = pendingRestore
        AlertDialog(
            onDismissRequest = {
                if (!state.isRestoreInProgress) {
                    viewModel.dismissRestoreDialog()
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
                            if (summary.patientName.isNotBlank()) {
                                Text(
                                    text = if (isRu) "👤 Профиль: ${summary.patientName} (${summary.diabetesType})"
                                    else "👤 Profile: ${summary.patientName} (${summary.diabetesType})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = if (isRu) "🩸 Замеров сахара: ${summary.readingsCount}"
                                else "🩸 Glucose readings: ${summary.readingsCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (isRu) "💉 Записей терапии: ${summary.treatmentsCount}"
                                else "💉 Treatments: ${summary.treatmentsCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (summary.hasSettings) {
                                Text(
                                    text = if (isRu) "⚙️ Настройки и пороги тревог включены"
                                    else "⚙️ Settings & alerts included",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryEmerald
                                )
                            }
                            if (summary.exportedAt > 0L) {
                                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                Text(
                                    text = if (isRu) "📅 Дата бэкапа: ${fmt.format(Date(summary.exportedAt))}"
                                    else "📅 Backup date: ${fmt.format(Date(summary.exportedAt))}",
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

                    if (state.isRestoreInProgress) {
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
                    onClick = { viewModel.confirmRestore() },
                    enabled = !state.isRestoreInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Text(if (isRu) "Восстановить" else "Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissRestoreDialog() },
                    enabled = !state.isRestoreInProgress
                ) {
                    Text(if (isRu) "Отмена" else "Cancel")
                }
            }
        )
    }

    if (showHelpDialog) {
        HelpAndDisclaimerDialog(
            isRu = isRu,
            onSaveManual = { viewModel.saveUserManualToDownloads() },
            snackbarHostState = snackbarHostState,
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showMainThresholdDialog) {
        MainThresholdDialog(
            initialLow = settings.alertSettings.mainLowThresholdMmol,
            initialHigh = settings.alertSettings.mainHighThresholdMmol,
            isRu = isRu,
            onSave = { low, high ->
                viewModel.updateAlertSettings(
                    settings.alertSettings.copy(
                        mainLowThresholdMmol = low,
                        mainHighThresholdMmol = high
                    )
                )
            },
            onResetDefault = {
                viewModel.updateAlertSettings(
                    settings.alertSettings.copy(
                        mainLowThresholdMmol = 3.9,
                        mainHighThresholdMmol = 10.0
                    )
                )
            },
            onDismiss = { showMainThresholdDialog = false }
        )
    }

    if (showCriticalThresholdDialog) {
        CriticalThresholdDialog(
            initialLow = settings.alertSettings.criticalLowThresholdMmol,
            initialHigh = settings.alertSettings.criticalHighThresholdMmol,
            isRu = isRu,
            onSave = { low, high ->
                viewModel.updateAlertSettings(
                    settings.alertSettings.copy(
                        criticalLowThresholdMmol = low,
                        criticalHighThresholdMmol = high
                    )
                )
            },
            onResetDefault = {
                viewModel.updateAlertSettings(
                    settings.alertSettings.copy(
                        criticalLowThresholdMmol = 3.0,
                        criticalHighThresholdMmol = 13.9
                    )
                )
            },
            onTestRescueScreen = {
                if (settings.alertSettings.isCaregiverRole) {
                    viewModel.testCaregiverSosScreen()
                } else {
                    viewModel.testAlert(com.tirup.app.data.alert.AlertTier.CRITICAL)
                }
            },
            onDismiss = { showCriticalThresholdDialog = false }
        )
    }

    if (showBleRangeHelpDialog) {
        BleRangeHelpDialog(
            isRu = isRu,
            onDismiss = { showBleRangeHelpDialog = false }
        )
    }

    if (showPredictiveHorizonDialog) {
        PredictiveHorizonDialog(
            currentMinutesAhead = settings.alertSettings.predictiveMinutesAhead,
            isRu = isRu,
            onSelectMinutes = { minutes ->
                viewModel.updateAlertSettings(settings.alertSettings.copy(predictiveMinutesAhead = minutes))
            },
            onInfoClick = { showPredictiveInfoDialog = true },
            onDismiss = { showPredictiveHorizonDialog = false }
        )
    }

    if (showPredictiveInfoDialog) {
        PredictiveInfoDialog(
            isRu = isRu,
            onDismiss = { showPredictiveInfoDialog = false }
        )
    }

    if (showCriticalHypoSafetyDialog) {
        val alerts = settings.alertSettings
        CriticalHypoSafetyDialog(
            alertSettings = alerts,
            isRu = isRu,
            onResumeAndEnable = {
                viewModel.updateAlertSettings(
                    alerts.copy(
                        isAlertsMasterEnabled = true,
                        isCriticalEnabled = true,
                        criticalHypoPauseUntilTimestamp = 0L,
                        isCriticalHypoPermanentDisabled = false
                    )
                )
            },
            onPauseTwoHours = {
                viewModel.updateAlertSettings(
                    alerts.copy(
                        isCriticalEnabled = false,
                        criticalHypoPauseUntilTimestamp = System.currentTimeMillis() + 2 * 3600 * 1000L,
                        isCriticalHypoPermanentDisabled = false
                    )
                )
            },
            onDisablePermanently = {
                viewModel.updateAlertSettings(
                    alerts.copy(
                        isCriticalEnabled = false,
                        isCriticalHypoPermanentDisabled = true,
                        criticalHypoPauseUntilTimestamp = 0L
                    )
                )
            },
            onDismiss = { showCriticalHypoSafetyDialog = false }
        )
    }


    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearConfirm(false) },
            title = { Text(text = stringResource(R.string.clear_data), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(text = stringResource(R.string.clear_data_confirm), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllData() }
                ) {
                    Text(text = stringResource(R.string.action_confirm), color = ColorVeryLow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showClearConfirm(false) }
                ) {
                    Text(text = stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }


    if (showProfileDialog) {
        PatientProfileEditDialog(
            profile = profile,
            isRu = isRu,
            currentYear = currentYear,
            onProfileChange = { updated ->
                viewModel.autoUpdatePatientProfile(updated)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (state.showHba1cDialog) {
        Hba1cHistoryDialog(
            records = settings.hba1cRecords,
            sensorGmi90d = state.sensorGmi90d,
            meanGlucose90dMmol = state.meanGlucose90dMmol,
            tirPercent90d = state.tirPercent90d,
            skippedQuarterTimestamp = settings.hba1cSkippedQuarterTimestamp,
            isRu = isRu,
            onAddRecord = { value, timestamp, lab, notes ->
                viewModel.addHba1cRecord(valuePercent = value, timestamp = timestamp, labName = lab, notes = notes)
            },
            onDeleteRecord = { id ->
                viewModel.deleteHba1cRecord(id)
            },
            onSkipQuarter = {
                viewModel.skipHba1cQuarter()
            },
            onExportPdf = { onSaved ->
                viewModel.exportHba1cReportToPdf(onSaved)
            },
            onDismiss = { viewModel.toggleHba1cDialog(false) }
        )
    }

    if (state.showYearEndDialog) {
        val stats = state.yearEndStats
        YearEndDigestDialog(
            stats = stats,
            isRu = isRu,
            snackbarHostState = snackbarHostState,
            onExportPdf = { s -> viewModel.exportYearEndReportToPdf(s) },
            onArchiveYear = { year -> viewModel.archiveYearArchive(year) },
            onYearChange = { year -> viewModel.setYearEndDigestYear(year) },
            onDismiss = { viewModel.setShowYearEndDialog(false) }
        )
    }

    if (showBleHelpModal) {
        BleBridgeHelpDialog(
            isRu = isRu,
            onDismiss = { showBleHelpModal = false }
        )
    }

    if (showBlePinDialog) {
        val ble = settings.bleBridgeSettings
        BleFamilyPinDialog(
            currentPin = ble.familyPin,
            isRu = isRu,
            onSavePin = { newPin ->
                viewModel.updateBleBridgeSettings(ble.copy(familyPin = newPin))
            },
            onDismiss = { showBlePinDialog = false }
        )
    }

    if (showNightscoutDialog) {
        NightscoutSettingsDialog(
            initialSettings = settings.nightscoutSettings,
            isRu = isRu,
            onDismiss = { showNightscoutDialog = false },
            onSave = { updated ->
                viewModel.updateNightscoutSettings(updated)
            }
        )
    }

    if (showXdripLanDialog) {
        XdripLanSettingsDialog(
            initialSettings = settings.xdripLanSettings,
            isRu = isRu,
            onDismiss = { showXdripLanDialog = false },
            onSave = { updated ->
                viewModel.updateXdripLanSettings(updated)
            }
        )
    }

    // Centered 3-second floating HUD banner on turning Master alerts off
    AnimatedVisibility(
        visible = masterOffHintVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.88f),
        exit = fadeOut() + scaleOut(targetScale = 0.88f),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF00F172A),
            shadowElevation = 16.dp,
            border = BorderStroke(1.2.dp, ColorVeryLow.copy(alpha = 0.85f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ColorVeryLow,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isRu) "Оповещения отключены.\nКритическая сирена (<3.0) на паузе 2 часа для вашей безопасности."
                           else "Alerts turned off.\nCritical siren (<3.0) paused for 2h for your safety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
            }
        }
    }
    }
}

@Composable
fun DropdownHourSelector(
    label: String,
    selectedHour: Int,
    isRu: Boolean = true,
    modifier: Modifier = Modifier,
    onHourSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hourSuffix = if (isRu) " ч" else " h"

    Box(modifier = modifier) {
        OutlinedTextField(
            value = "$selectedHour$hourSuffix",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (0..23).forEach { hr ->
                DropdownMenuItem(
                    text = { Text("$hr$hourSuffix") },
                    onClick = {
                        onHourSelected(hr)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ActionBlue else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) ActionBlue else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}



private fun getAppVersionName(context: android.content.Context): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName ?: "2.2.1"
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.1"
        }
    } catch (_: Exception) {
        "2.2.1"
    }
}
