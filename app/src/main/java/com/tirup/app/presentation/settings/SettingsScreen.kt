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
import com.tirup.app.presentation.settings.sections.*
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
    var showNightscoutDialog by rememberSaveable { mutableStateOf(false) }
    var showXdripLanDialog by rememberSaveable { mutableStateOf(false) }
    val isDevTestsUnlocked by viewModel.isDevTestsUnlocked.collectAsState()
    var devTapCount by remember { mutableStateOf(0) }
    var lastDevTapTime by remember { mutableStateOf(0L) }
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

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val listState = rememberLazyListState()
    var highlightBle by remember { mutableStateOf(false) }
    var highlightLan by remember { mutableStateOf(false) }


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
            highlightLan = true
            delay(2800L)
            highlightLan = false
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

    var testCaregiverSosCountdownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testCaregiverSosCountdownSec) {
        if (testCaregiverSosCountdownSec > 0) {
            delay(1000L)
            testCaregiverSosCountdownSec -= 1
        }
    }

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
            DisplayPreferencesCard(
                settings = settings,
                isRu = isRu,
                onSetLanguage = { viewModel.setLanguage(it) },
                onSetUnit = { viewModel.setUnit(it) },
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetShowTreatmentsOnChart = { viewModel.setShowTreatmentsOnChart(it) },
                onSetShowPredictionOnChart = { viewModel.setShowPredictionOnChart(it) }
            )
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
                            XdripLanFollowerCard(
                                settings = settings,
                                isRu = isRu,
                                isLanCardExpanded = isLanCardExpanded,
                                onToggleExpanded = { isLanCardExpanded = !isLanCardExpanded },
                                highlightLan = highlightLan,
                                highlightBorderAlpha = highlightBorderAlpha,
                                onUpdateLanSettings = { viewModel.updateXdripLanSettings(it) },
                                onShowLanSettingsDialog = { showXdripLanDialog = true }
                            )

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

                            AlwaysOnDisplayCard(
                                settings = settings,
                                isRu = isRu,
                                onUpdateAodSettings = { viewModel.updateAodSettings(it) }
                            )

                            WeeklyDigestCard(
                                settings = settings,
                                isRu = isRu,
                                onSetWeeklyDigestEnabled = { viewModel.setWeeklyDigestEnabled(it) },
                                onTriggerImmediately = {
                                    com.tirup.app.data.worker.WeeklyDigestWorker.triggerImmediately(context)
                                    Toast.makeText(
                                        context,
                                        if (isRu) "Формируем отчёт дайджеста... Протяните шторку уведомлений"
                                        else "Generating weekly digest... Check notifications shade",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            DeviceRemindersCard(
                                settings = settings,
                                isRu = isRu,
                                onSetDeviceRemindersEnabled = { viewModel.setDeviceRemindersEnabled(it) },
                                onSetSensorReminderEnabled = { viewModel.setSensorReminderEnabled(it) },
                                onSetPumpReminderEnabled = { viewModel.setPumpReminderEnabled(it) },
                                onSetLancetReminderEnabled = { viewModel.setLancetReminderEnabled(it) },
                                onSetHba1cReminderEnabled = { viewModel.setHba1cReminderEnabled(it) }
                            )

                            ClinicalStandardsCard(
                                settings = settings,
                                isRu = isRu,
                                onUpdateNightHours = { start, end ->
                                    viewModel.autoUpdateNightHours(nightStart = start, nightEnd = end)
                                }
                            )

                            LockscreenNotificationCard(
                                settings = settings,
                                isRu = isRu,
                                onSetLockscreenNotificationEnabled = { viewModel.setLockscreenNotificationEnabled(it) }
                            )

                            FloatingGlucoseBubbleCard(
                                settings = settings,
                                isRu = isRu,
                                onToggleFloatingBubble = { viewModel.toggleFloatingBubble(it) },
                                onToggleFloatingBubbleAlwaysVisible = { viewModel.toggleFloatingBubbleAlwaysVisible(it) }
                            )

            WidgetPreviewCard(
                settings = settings,
                isRu = isRu,
                onUpdateWidgetBackgroundOpacity = { viewModel.updateWidgetBackgroundOpacity(it) }
            )

            AutoBackupCard(
                settings = settings,
                backupSummary = state.backupSummary,
                isBackupInProgress = state.isBackupInProgress,
                isRestoreInProgress = state.isRestoreInProgress,
                isRu = isRu,
                onToggleAutoBackup = { viewModel.toggleAutoBackup(it) },
                onCreateBackupNow = { viewModel.createBackupNow() },
                onShareBackup = { viewModel.shareBackup() },
                onExportZip = { fileName -> createBackupLauncher.launch(fileName) },
                onShowRestoreOptionsModal = { showRestoreOptionsModal = true },
                onShowYearEndDialog = { year -> viewModel.setShowYearEndDialog(true, year) }
            )

            ClearDataCard(
                infoMessage = state.infoMessage,
                onShowClearConfirm = { viewModel.showClearConfirm(true) }
            )

            DeveloperTestingCard(
                settings = settings,
                isRu = isRu,
                isDevTestsUnlocked = isDevTestsUnlocked,
                smsPermissionsLauncher = smsPermissionsLauncher,
                testCaregiverSosCountdownSec = testCaregiverSosCountdownSec,
                onStartCaregiverSosTest = {
                    testCaregiverSosCountdownSec = 5
                    viewModel.startCaregiverSosTestCountdown(5)
                },
                onCancelCaregiverSosTest = {
                    testCaregiverSosCountdownSec = 0
                    viewModel.cancelCaregiverSosTest()
                },
                onStartPatientRescueTest = { viewModel.startPatientRescueTestCountdown(it) },
                onCancelPatientRescueTest = { viewModel.cancelPatientRescueTest() },
                onStartHeadsUpTest = { viewModel.startHeadsUpTestCountdown(it) },
                onCancelHeadsUpTest = { viewModel.cancelHeadsUpTest() },
                onSendCaregiverSosTestSms = { viewModel.sendCaregiverSosTestSms() },
                onStartBleRangeTest = { viewModel.startBleRangeTest(it) },
                onCheckAndRequestBlePermissions = { checkAndRequestBlePermissions(it) }
            )

            // Nightscout sync: hidden until dev-unlocked OR already configured by user.
            // Once disabled by user, it retreats back to the hidden (dev) section.
            if (isDevTestsUnlocked || settings.nightscoutSettings.isEnabled) {
                NightscoutSyncCard(
                    settings = settings,
                    isRu = isRu,
                    onUpdateNightscoutSettings = { viewModel.updateNightscoutSettings(it) },
                    onShowNightscoutDialog = { showNightscoutDialog = true }
                )
            }

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
        RestoreOptionsModal(
            backupSummary = state.backupSummary,
            isRu = isRu,
            onDismiss = { showRestoreOptionsModal = false },
            onRestoreLatestAutoBackup = { viewModel.restoreLatestAutoBackup() },
            onSelectBackupFile = {
                restoreBackupLauncher.launch(
                    arrayOf("application/zip", "application/json", "text/csv", "text/comma-separated-values", "*/*")
                )
            }
        )
    }

    state.pendingRestoreSummary?.let { summary ->
        PendingRestoreDialog(
            pendingRestore = summary,
            isRestoreInProgress = state.isRestoreInProgress,
            isRu = isRu,
            onConfirmRestore = { viewModel.confirmRestore() },
            onDismiss = { viewModel.dismissRestoreDialog() }
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
        ClearDataConfirmDialog(
            onConfirm = { viewModel.clearAllData() },
            onDismiss = { viewModel.showClearConfirm(false) }
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
fun LanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = com.tirup.app.presentation.settings.sections.LanguageChip(
    label = label,
    isSelected = isSelected,
    onClick = onClick,
    modifier = modifier
)

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
