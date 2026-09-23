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
    val isDevTestsUnlocked by viewModel.isDevTestsUnlocked.collectAsState()
    var devTapCount by remember { mutableStateOf(0) }
    var lastDevTapTime by remember { mutableStateOf(0L) }
    var bleRangeCooldownSec by remember { mutableStateOf(0) }
    val isSoundPlaying by com.tirup.app.data.alert.MedicalSoundPlayer.isPlaying.collectAsState()
    var lastSoundClickTime by remember { mutableStateOf(0L) }
    val handleSoundClick: (() -> Unit) -> Unit = { action ->
        val now = System.currentTimeMillis()
        if (now - lastSoundClickTime >= 400L) {
            lastSoundClickTime = now
            if (isSoundPlaying) {
                viewModel.stopAlertSounds()
            } else {
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

    var localPrimaryPhone by rememberSaveable { mutableStateOf(settings.alertSettings.emergencyContactPhone) }
    var localPrimaryName by rememberSaveable { mutableStateOf(settings.alertSettings.emergencyContactName) }
    var localSecondaryPhone by rememberSaveable { mutableStateOf(settings.alertSettings.secondaryEmergencyContactPhone) }
    var localSecondaryName by rememberSaveable { mutableStateOf(settings.alertSettings.secondaryEmergencyContactName) }

    LaunchedEffect(settings.alertSettings.emergencyContactPhone) {
        if (localPrimaryPhone != settings.alertSettings.emergencyContactPhone) {
            localPrimaryPhone = settings.alertSettings.emergencyContactPhone
        }
    }
    LaunchedEffect(settings.alertSettings.emergencyContactName) {
        if (localPrimaryName != settings.alertSettings.emergencyContactName) {
            localPrimaryName = settings.alertSettings.emergencyContactName
        }
    }
    LaunchedEffect(settings.alertSettings.secondaryEmergencyContactPhone) {
        if (localSecondaryPhone != settings.alertSettings.secondaryEmergencyContactPhone) {
            localSecondaryPhone = settings.alertSettings.secondaryEmergencyContactPhone
        }
    }
    LaunchedEffect(settings.alertSettings.secondaryEmergencyContactName) {
        if (localSecondaryName != settings.alertSettings.secondaryEmergencyContactName) {
            localSecondaryName = settings.alertSettings.secondaryEmergencyContactName
        }
    }

    LaunchedEffect(target) {
        if (target == "ble_bridge") {
            showAdvancedSettings = true
            isBleCardExpanded = true
            delay(150L)
            listState.animateScrollToItem(4)
            highlightBle = true
            delay(2800L)
            highlightBle = false
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

    val isBroadcasting by viewModel.isBleBroadcasting.collectAsState()
    val broadcastRemaining by viewModel.bleBroadcastRemaining.collectAsState()
    val isScanning by viewModel.isBleScanning.collectAsState()
    val boostRemaining by viewModel.bleBoostRemaining.collectAsState()
    val latestReading by viewModel.latestReading.collectAsState()

    var testSmsCooldownSec by remember { mutableStateOf(0) }
    LaunchedEffect(testSmsCooldownSec) {
        if (testSmsCooldownSec > 0) {
            delay(1000L)
            testSmsCooldownSec -= 1
        }
    }

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
            val alerts = settings.alertSettings
            val isFollower = alerts.isCaregiverRole
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isRoleSectionExpanded = !isRoleSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Роль устройства в системе" else "Device Role in TIRUp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isFollower) {
                                    if (isRu) "Текущая: 👁️ Фоловер (Наблюдатель)"
                                    else "Active: 👁️ Follower (Observer)"
                                } else {
                                    if (isRu) "Текущая: 👑 Мастер (Сенсор)"
                                    else "Active: 👑 Master (Sensor)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFollower) PrimaryEmerald else ActionBlue
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isFollower) PrimaryEmerald else ActionBlue).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (isFollower) PrimaryEmerald else ActionBlue).copy(alpha = 0.35f)),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isFollower) (if (isRu) "Фоловер" else "Follower") else (if (isRu) "Мастер" else "Master"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFollower) PrimaryEmerald else ActionBlue
                                )
                                Text(
                                    text = if (isRoleSectionExpanded) "▲" else "▼",
                                    fontSize = 11.sp,
                                    color = if (isFollower) PrimaryEmerald else ActionBlue
                                )
                            }
                        }
                    }

                    if (isRoleSectionExpanded) {
                        Text(
                            text = if (isFollower) {
                                if (isRu) "Наблюдатель: приём данных, сирена при ночном SOS"
                                else "Follower: telemetry reception, loud siren on night SOS"
                            } else {
                                if (isRu) "Ведущее: сенсор глюкозы, локальные тревоги, авто-SOS близким"
                                else "Master: CGM sensor, local alarms, auto-SOS to contacts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Role Selectors: Master (👑) vs Follower (👁️)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                        // Master button
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (isFollower) {
                                        viewModel.updateAlertSettings(alerts.copy(isCaregiverRole = false))
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (!isFollower) ActionBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (!isFollower) 1.5.dp else 0.8.dp,
                                color = if (!isFollower) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "👑",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isRu) "Мастер" else "Master",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!isFollower) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isFollower) ActionBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "(Сенсор)" else "(Sensor)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!isFollower) ActionBlue.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Follower button
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isFollower) {
                                        viewModel.updateAlertSettings(alerts.copy(isCaregiverRole = true))
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isFollower) PrimaryEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isFollower) 1.5.dp else 0.8.dp,
                                color = if (isFollower) PrimaryEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "👁️",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isRu) "Фоловер" else "Follower",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isFollower) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isFollower) PrimaryEmerald else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRu) "(Наблюдатель)" else "(Observer)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFollower) PrimaryEmerald.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    }
                }
            }
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
                            modifier = Modifier.weight(1f)
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
                            modifier = Modifier.weight(1f)
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

        // Section 2: Smart Alerts (3 Tiers) - Master Card
        item {
            var isAlertsExpanded by rememberSaveable { mutableStateOf(false) }
            val alerts = settings.alertSettings

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAlertsExpanded = !isAlertsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (alerts.isAlertsMasterEnabled) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = if (isRu) "Тревоги (4 уровня)" else "Alarms (4 Tiers)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (!alerts.isAlertsMasterEnabled) {
                                        if (isRu) "Все тревоги выключены" else "All alarms disabled"
                                    } else {
                                        if (isRu) "Предиктивные, основные, критические, связь" else "Predictive, main, critical, signal loss"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!alerts.isAlertsMasterEnabled) ColorVeryLow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = alerts.isAlertsMasterEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (!isEnabled) {
                                        masterOffHintVisible = true
                                        viewModel.updateAlertSettings(
                                            alerts.copy(
                                                isAlertsMasterEnabled = false,
                                                criticalHypoPauseUntilTimestamp = System.currentTimeMillis() + 2 * 3600 * 1000L
                                            )
                                        )
                                    } else {
                                        masterOffHintVisible = false
                                        viewModel.updateAlertSettings(
                                            alerts.copy(
                                                isAlertsMasterEnabled = true,
                                                criticalHypoPauseUntilTimestamp = 0L
                                            )
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ActionBlue
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isAlertsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isAlertsExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))

                        if (!alerts.isAlertsMasterEnabled) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ColorVeryLow.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ColorVeryLow.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isRu) "⚠️ Оповещения выключены. Вы можете настроить параметры или бессрочно отключить критическую тревогу гипо ниже."
                                           else "⚠️ Master alerts are disabled. You can configure parameters or permanently disable critical hypo below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorVeryLow,
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        val isMaster = alerts.isAlertsMasterEnabled
                        val nowMs = System.currentTimeMillis()
                        val isAlertsPaused = alerts.alertsMuteUntilTimestamp > nowMs
                        val isCriticalPaused = alerts.criticalHypoPauseUntilTimestamp > nowMs || isAlertsPaused
                        val pauseTargetMs = maxOf(alerts.criticalHypoPauseUntilTimestamp, alerts.alertsMuteUntilTimestamp)
                        val remSec = if (isCriticalPaused) {
                            ((pauseTargetMs - nowMs) / 1000L).coerceAtLeast(0)
                        } else 0L
                        val remHours = remSec / 3600
                        val remMin = ((remSec % 3600) / 60).coerceAtLeast(1)
                        val resumeTime = if (isCriticalPaused) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(pauseTargetMs))
                        } else ""

                        val criticalBadge = if (isCriticalPaused) {
                            if (remHours > 0) "⏳ ${remHours}ч ${remMin}м" else "⏳ ${remMin}м"
                        } else null

                        if (isAlertsPaused) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ActionBlue.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isRu) "⏸️ Все тревоги на паузе" else "⏸️ All alarms paused",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ActionBlue
                                        )
                                        Text(
                                            text = if (remHours > 0) {
                                                if (isRu) "Осталось: ${remHours}ч ${remMin}м (до $resumeTime)" else "Remaining: ${remHours}h ${remMin}m (until $resumeTime)"
                                            } else {
                                                if (isRu) "Осталось: ${remMin}м (до $resumeTime)" else "Remaining: ${remMin}m (until $resumeTime)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.updateAlertSettings(
                                                alerts.copy(
                                                    alertsMuteUntilTimestamp = 0L,
                                                    criticalHypoPauseUntilTimestamp = 0L
                                                )
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = if (isRu) "Возобновить" else "Resume",
                                            fontWeight = FontWeight.Bold,
                                            color = ActionBlue
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Independent Volume Control for Tiers 1-2
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isRu) "Громкость упреждающих тревог" else "Alert Volume (Tiers 1–2)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isRu) "Независима от звука уведомлений телефона" else "Independent of phone ringtone volume",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.playTestSound(alerts.alertVolumePercent)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (isRu) "Тест 🔔" else "Test 🔔",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Slider(
                                        value = alerts.alertVolumePercent.toFloat(),
                                        onValueChange = { newVal ->
                                            val stepped = (kotlin.math.round(newVal / 5f) * 5f).toInt().coerceIn(20, 100)
                                            if (stepped != alerts.alertVolumePercent) {
                                                viewModel.updateAlertSettings(alerts.copy(alertVolumePercent = stepped))
                                            }
                                        },
                                        valueRange = 20f..100f,
                                        steps = 15,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = ActionBlue,
                                            activeTrackColor = ActionBlue
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${alerts.alertVolumePercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue,
                                        modifier = Modifier.width(44.dp)
                                    )
                                }

                                Text(
                                    text = if (isRu) "ℹ️ Критические тревоги (затяжная гипогликемия, потеря связи) всегда звучат на максимальной громкости (100%)."
                                           else "ℹ️ Critical alarms (prolonged hypo, signal loss) always sound at maximum volume (100%).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tier 1: Predictive (Soft)
                        AlertTierConfigRow(
                            title = if (isRu) "1. Предиктивные (умные за ${alerts.predictiveMinutesAhead} мин)" else "1. Predictive (Smart ~${alerts.predictiveMinutesAhead} min)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Мягкий сигнал прогноза до выхода за диапазон" else "Soft early warning before crossing limits",
                            enabled = isMaster && alerts.isPredictiveEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isPredictiveEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isPredictiveEnabled = false))
                                }
                            },
                            vibrate = alerts.isPredictiveVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isPredictiveVibrate = it)) },
                            flash = alerts.isPredictiveFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isPredictiveFlash = it)) },
                            accentColor = ActionBlue,
                            onTestClick = { handleSoundClick { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.PREDICTIVE) } },
                            isTesting = isSoundPlaying,
                            isRu = isRu,
                            thresholdBadge = if (isRu) "⏱️ Горизонт: ${alerts.predictiveMinutesAhead} мин" else "⏱️ Horizon: ${alerts.predictiveMinutesAhead} min",
                            onThresholdClick = { showPredictiveHorizonDialog = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tier 2: Main (3-5 points confirmed)
                        AlertTierConfigRow(
                            title = if (isRu) "2. Основные (3–5 точек вне нормы)" else "2. Main (3–5 points confirmed)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Тройной сигнал (3 точки для 5-мин / 5 точек для 1-мин). Глушится при падении с IoB" else "Triple beep (3 pts for 5-min / 5 pts for 1-min). Muted on drop with IoB",
                            enabled = isMaster && alerts.isMainEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isMainEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isMainEnabled = false))
                                }
                            },
                            vibrate = alerts.isMainVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isMainVibrate = it)) },
                            flash = alerts.isMainFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isMainFlash = it)) },
                            accentColor = ColorHigh,
                            onTestClick = { handleSoundClick { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.MAIN) } },
                            isTesting = isSoundPlaying,
                            isRu = isRu,
                            thresholdBadge = "< ${String.format(Locale.US, "%.1f", alerts.mainLowThresholdMmol)}  |  > ${String.format(Locale.US, "%.1f", alerts.mainHighThresholdMmol)}",
                            onThresholdClick = { showMainThresholdDialog = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val criticalSub = when {
                            isCriticalPaused -> {
                                if (remHours > 0) {
                                    if (isRu) "⏳ Пауза: ещё ${remHours} ч ${remMin} мин (авто-возобновление в $resumeTime)"
                                    else "⏳ Paused: ${remHours}h ${remMin}m left (auto-resumes at $resumeTime)"
                                } else {
                                    if (isRu) "⏳ Пауза: ещё ${remMin} мин (авто-возобновление в $resumeTime)"
                                    else "⏳ Paused: ${remMin}m left (auto-resumes at $resumeTime)"
                                }
                            }
                            alerts.isCriticalHypoPermanentDisabled -> {
                                if (isRu) "⚠️ Отключено осознанно под вашу ответственность" else "⚠️ Permanently disabled at own risk"
                            }
                            !isMaster -> {
                                if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)"
                            }
                            !alerts.isCriticalEnabled -> {
                                if (isRu) "Выключено пользователем" else "Disabled by user"
                            }
                            else -> if (isRu) "Экстра-ГИПО (50с) <${String.format(Locale.US, "%.1f", alerts.criticalLowThresholdMmol)}, Экстра-ГИПЕР (16с) >${String.format(Locale.US, "%.1f", alerts.criticalHighThresholdMmol)}, затяжные (12с)"
                                    else "Extra-HYPO (50s) <${String.format(Locale.US, "%.1f", alerts.criticalLowThresholdMmol)}, Extra-HYPER (16s) >${String.format(Locale.US, "%.1f", alerts.criticalHighThresholdMmol)}, prolonged (12s)"
                        }

                        // Tier 3: Critical (Prolonged / Extreme)
                        val isCriticalInPauseState = isCriticalPaused && !alerts.isCriticalHypoPermanentDisabled
                        val isCriticalEffectiveEnabled = if (isCriticalInPauseState) true else (isMaster && alerts.isCriticalEnabled && !alerts.isCriticalHypoPermanentDisabled)

                        AlertTierConfigRow(
                            title = if (isRu) "3. Экстренные сирены (критические и затяжные)" else "3. Critical & Prolonged (Alarms)",
                            subtitle = criticalSub,
                            enabled = isCriticalEffectiveEnabled,
                            onEnabledChange = { isEnabled ->
                                if (!isEnabled) {
                                    showCriticalHypoSafetyDialog = true
                                } else {
                                    viewModel.updateAlertSettings(
                                        alerts.copy(
                                            isAlertsMasterEnabled = true,
                                            isCriticalEnabled = true,
                                            criticalHypoPauseUntilTimestamp = 0L,
                                            isCriticalHypoPermanentDisabled = false
                                        )
                                    )
                                }
                            },
                            vibrate = alerts.isCriticalVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isCriticalVibrate = it)) },
                            flash = alerts.isCriticalFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isCriticalFlash = it)) },
                            accentColor = if (isCriticalInPauseState) ColorHigh else ColorVeryLow,
                            onTestClick = {
                                handleSoundClick {
                                    if (alerts.isCaregiverRole) {
                                        viewModel.testCaregiverSosScreen()
                                    } else {
                                        viewModel.testAlert(com.tirup.app.data.alert.AlertTier.CRITICAL)
                                    }
                                }
                            },
                            isTesting = isSoundPlaying,
                            isRu = isRu,
                            timerBadge = criticalBadge,
                            isPaused = isCriticalInPauseState,
                            thresholdBadge = "< ${String.format(Locale.US, "%.1f", alerts.criticalLowThresholdMmol)}  |  > ${String.format(Locale.US, "%.1f", alerts.criticalHighThresholdMmol)}",
                            extraBadge = if (isRu) "🔊 Экстра-звуки" else "🔊 Extra Sounds",
                            onThresholdClick = { showCriticalThresholdDialog = true }
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        // Tier 4: Signal Loss (20-25 min)
                        AlertTierConfigRow(
                            title = if (isRu) "4. Потеря сигнала сенсора (20–25 мин)" else "4. Signal Loss (20–25 min)",
                            subtitle = if (!isMaster) (if (isRu) "Выключено (общий тумблер выключен)" else "Disabled (master switch off)")
                                       else if (isRu) "Нисходящий сигнал через 20–25 мин с нарастающим интервалом (➔ 40 ➔ 80 мин)" else "Descending tone after 20–25 min with increasing interval (➔ 40 ➔ 80 min)",
                            enabled = isMaster && alerts.isSignalLossEnabled,
                            onEnabledChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.updateAlertSettings(alerts.copy(isAlertsMasterEnabled = true, isSignalLossEnabled = true))
                                } else {
                                    viewModel.updateAlertSettings(alerts.copy(isSignalLossEnabled = false))
                                }
                            },
                            vibrate = alerts.isSignalLossVibrate,
                            onVibrateChange = { viewModel.updateAlertSettings(alerts.copy(isSignalLossVibrate = it)) },
                            flash = alerts.isSignalLossFlash,
                            onFlashChange = { viewModel.updateAlertSettings(alerts.copy(isSignalLossFlash = it)) },
                            accentColor = Color(0xFF8B5CF6),
                            onTestClick = { handleSoundClick { viewModel.testAlert(com.tirup.app.data.alert.AlertTier.SIGNAL_LOSS) } },
                            isTesting = isSoundPlaying,
                            isRu = isRu
                        )
                    }
                }
            }
        }

        // Section: Daily Compensator (Last Chance TIR) - Separate from Alerts
        item {
            val alerts = settings.alertSettings
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "⏳ Последний шанс для TIR" else "⏳ Last Chance for Daily TIR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRu) "Предупреждать вечером, если сахар вне нормы и запас времени до срыва цели на исходе (1 раз в сутки)"
                                else "Alert in evening when out of range and margin before target failure is running out (once a day)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = alerts.isLastChanceAlertEnabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updateAlertSettings(alerts.copy(isLastChanceAlertEnabled = isChecked))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ActionBlue
                            )
                        )
                    }

                    // 3 segmented buttons: 1ч, 1.5ч, 2ч
                    if (alerts.isLastChanceAlertEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isRu) "Запас времени:" else "Time margin:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val options = listOf(
                                    Triple(60, if (isRu) "1 ч" else "1 h", "red"),
                                    Triple(90, if (isRu) "1.5 ч" else "1.5 h", "pale_green"),
                                    Triple(120, if (isRu) "2 ч" else "2 h", "green")
                                )
                                options.forEach { (mins, label, colorType) ->
                                    val isSelected = alerts.lastChanceBufferMinutes == mins
                                    val (bg, textColor, borderColor) = when (colorType) {
                                        "red" -> if (isSelected) {
                                            Triple(Color(0x33EF4444), Color(0xFFF87171), Color(0x80EF4444))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                        "pale_green" -> if (isSelected) {
                                            Triple(Color(0x2E10B981), Color(0xFF34D399), Color(0x6610B981))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                        else -> if (isSelected) {
                                            Triple(Color(0xFF059669), Color.White, Color(0xFF10B981))
                                        } else {
                                            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.clickable {
                                            viewModel.updateAlertSettings(alerts.copy(lastChanceBufferMinutes = mins))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = bg,
                                        border = BorderStroke(1.dp, borderColor)
                                    ) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                            // Section: Local BLE Bridge (Broadcaster / Observer)
                            val ble = settings.bleBridgeSettings
                            val isBridgeActive = ble.isEnabled && ble.role != BleBridgeRole.DISABLED
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (highlightBle) ActionBlue.copy(alpha = highlightBorderAlpha) else MaterialTheme.colorScheme.outline,
                        borderWidth = if (highlightBle) 2.2.dp else 1.dp,
                        backgroundColor = if (highlightBle) ActionBlue.copy(alpha = 0.08f * highlightBorderAlpha) else MaterialTheme.colorScheme.surface
                    ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBleCardExpanded = !isBleCardExpanded },
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
                                        text = if (isRu) "Локальный BLE-мост" else "Local BLE Bridge",
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
                            Surface(
                                shape = CircleShape,
                                color = ActionBlue.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { showBleHelpModal = true }
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
                            // Informational banner plate (duplicate ℹ️ removed)
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
                    // Role Selector: 3 options (Off, Broadcaster, Observer)
                    val roles = listOf(
                        Triple(BleBridgeRole.DISABLED, if (isRu) "✖️ Выкл" else "✖️ Off", "gray"),
                        Triple(BleBridgeRole.BROADCASTER, if (isRu) "📡 Вещатель" else "📡 Broadcaster", "blue"),
                        Triple(BleBridgeRole.OBSERVER, if (isRu) "📻 Приёмник" else "📻 Observer", "emerald")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.forEach { (role, label, colorType) ->
                            val isSelected = if (role == BleBridgeRole.DISABLED) !isBridgeActive else (isBridgeActive && ble.role == role)
                            val (bg, textColor, borderColor) = when (colorType) {
                                "blue" -> if (isSelected) {
                                    Triple(ActionBlue.copy(alpha = 0.2f), ActionBlue, ActionBlue)
                                } else {
                                    Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                                "emerald" -> if (isSelected) {
                                    Triple(PrimaryEmerald.copy(alpha = 0.2f), PrimaryEmerald, PrimaryEmerald)
                                } else {
                                    Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                                else -> if (isSelected) {
                                    Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outline)
                                } else {
                                    Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (role != BleBridgeRole.DISABLED) {
                                            checkAndRequestBlePermissions(role)
                                        }
                                        val newEnabled = role != BleBridgeRole.DISABLED
                                        val newPin = if (newEnabled && (ble.familyPin.length != 3 || !ble.familyPin.all { it in 'A'..'Z' })) {
                                            BlePacketCodec.generateRandomPin()
                                        } else {
                                            ble.familyPin
                                        }
                                        viewModel.updateBleBridgeSettings(ble.copy(role = role, isEnabled = newEnabled, familyPin = newPin))
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = bg,
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (isBridgeActive) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Family PIN code (Compact row with masked PIN & modal trigger)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBlePinDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔑", fontSize = 18.sp)
                                    Column {
                                        Text(
                                            text = if (isRu) "PIN-код семьи" else "Family PIN",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val displayPin = if (ble.familyPin.isNotBlank()) ble.familyPin else (if (isRu) "Не задан" else "Not set")
                                        Text(
                                            text = if (isRu) "Код: $displayPin (нажмите для смены)" else "Code: $displayPin (tap to change)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "›",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Bluetooth Hardware Check Warning
                        val isBtOn = com.tirup.app.data.ble.BleBroadcaster.isBluetoothEnabled(context)
                        if (!isBtOn) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x22EF4444),
                                border = BorderStroke(1.dp, Color(0x66EF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Text(
                                        text = if (isRu) "Bluetooth выключен на смартфоне. Включите Bluetooth в шторке Android для работы радиомоста."
                                               else "Bluetooth is disabled on device. Enable Bluetooth in Android settings for BLE bridge.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (ble.role == BleBridgeRole.BROADCASTER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Передавать заряд батареи" else "Transmit Battery Level",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRu) "Приёмник увидит процент заряда смартфона ребёнка" else "Follower will see phone battery percentage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = ble.transmitBattery,
                                    onCheckedChange = { isChecked ->
                                        viewModel.updateBleBridgeSettings(ble.copy(transmitBattery = isChecked))
                                    }
                                )
                            }

                            val isLongRangeSupported = remember(isBtOn) {
                                BleBroadcaster.isLongRangeSupported(context)
                            }
                            var showLongRangeConfirmDialog by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
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
                                            viewModel.updateBleBridgeSettings(ble.copy(useLongRange = false))
                                        }
                                    }
                                )
                            }

                            if (showLongRangeConfirmDialog) {
                                BleLongRangeConfirmDialog(
                                    isRu = isRu,
                                    onConfirm = {
                                        viewModel.updateBleBridgeSettings(ble.copy(useLongRange = true))
                                    },
                                    onDismiss = { showLongRangeConfirmDialog = false }
                                )
                            }

                            // Broadcaster Status Banner with Pulse Animation & Idle Countdown
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

                            // Master Test Ping Button
                            Button(
                                onClick = {
                                    if (!isBtOn) {
                                        Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        checkAndRequestBlePermissions(BleBridgeRole.BROADCASTER)
                                        viewModel.sendBleTestPing()
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
                            // Observer Status & Diagnostics
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
                                            else -> if (isRu) "слабый" else "weak"
                                        }
                                        val isStaleBattery = ageMinutes >= 5
                                        val batteryInfo = when {
                                            ble.lastMasterBattery < 0 -> ""
                                            isStaleBattery -> if (isRu) "\n• Батарея вещателя: ? (нет связи ≥ 5 мин)" else "\n• Master battery: ? (stale ≥ 5m)"
                                            else -> "\n• ${if (isRu) "Батарея вещателя" else "Master battery"}: ${ble.lastMasterBattery}%"
                                        }

                                        val isObserverLongRangeActive by BleObserverManager.isLongRangeScanActive.collectAsState()
                                        val scanModeStr = if (isObserverLongRangeActive) {
                                            if (isRu) "\n• Сканер: Dual (1M + Long Range)" else "\n• Scanner: Dual (1M + Long Range)"
                                        } else {
                                            if (isRu) "\n• Сканер: Standard (1M Legacy)" else "\n• Scanner: Standard (1M Legacy)"
                                        }
                                        val isSilenceAlert = ageMinutes >= 6 && !isObserverLongRangeActive
                                        val silenceWarning = if (isSilenceAlert) {
                                            if (isRu) "\n\n⚠️ Нет сигнала > 6 мин. Данный телефон принимает только стандартный Bluetooth. Если на смартфоне пациента включен Long Range — отключите его там для восстановления связи."
                                            else "\n\n⚠️ No signal > 6m. This phone supports Standard BLE only. If patient phone has Long Range enabled, disable it there."
                                        } else ""

                                        Text(
                                            text = if (isRu) "• Радиосигнал: $ageStr\n• Сигнал: ${ble.lastRssi} dBm ($signalQuality)$batteryInfo$scanModeStr$silenceWarning"
                                                   else "• Radio signal: $ageStr\n• Signal: ${ble.lastRssi} dBm ($signalQuality)$batteryInfo$scanModeStr$silenceWarning",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSilenceAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = if (isRu) "Ожидание первого радиосигнала от вещателя..." else "Waiting for first beacon from master...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Background stability recommendation tip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("💡", fontSize = 16.sp)
                                    Text(
                                        text = if (isRu) {
                                            "Для непрерывного приёма данных при заблокированном экране рекомендуется держать включённым постоянное уведомление сахара в настройках."
                                        } else {
                                            "For uninterrupted background tracking when the screen is locked, keep the permanent glucose notification enabled."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            // Checkbox: Show incoming packet toast/banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateBleBridgeSettings(
                                            ble.copy(showPacketBanner = !ble.showPacketBanner)
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = ble.showPacketBanner,
                                    onCheckedChange = { isChecked ->
                                        viewModel.updateBleBridgeSettings(
                                            ble.copy(showPacketBanner = isChecked)
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryEmerald)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Всплывающий баннер при приёме пакета" else "Pop-up banner on packet receipt",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRu) "Показывать плашку с сахаром и сигналом RSSI вверху экрана (всегда активен при поиске и тесте)"
                                               else "Show top banner with glucose and RSSI (always active during search & test)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Follower Boost Scan Button
                            Button(
                                onClick = {
                                    if (!isBtOn) {
                                        Toast.makeText(context, if (isRu) "Включите Bluetooth на смартфоне" else "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        checkAndRequestBlePermissions(BleBridgeRole.OBSERVER)
                                        viewModel.boostBleObserverScan()
                                    }
                                },
                                enabled = boostRemaining <= 0,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActionBlue.copy(alpha = 0.85f),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (boostRemaining > 0) {
                                        if (isRu) "⚡ Активный поиск (${boostRemaining}с)..." else "⚡ Boosting Scan (${boostRemaining}s)..."
                                    } else {
                                        if (isRu) "🔍 Поиск вещателя (60 сек)" else "🔍 Master Search (60s)"
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

            // Section: Emergency SMS (Role-dependent: Patient vs Caregiver)
            val alerts = settings.alertSettings
            val isCaregiver = alerts.isCaregiverRole
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSmsCardExpanded = !isSmsCardExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🚨",
                                fontSize = 22.sp
                            )
                            Column {
                                Text(
                                    text = if (isRu) "Экстренное SMS" else "Emergency SMS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCaregiver) {
                                        if (isRu) "Приём SOS и сирена фоловера" else "Follower SOS reception & siren"
                                    } else {
                                        if (isRu) "Авто-отправка SMS близким при гипогликемии" else "Auto-send SMS to contacts"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isMasterChecked = if (isCaregiver) alerts.isCaregiverSosWakeupEnabled else alerts.isEmergencySmsEnabled
                            Switch(
                                checked = isMasterChecked,
                                onCheckedChange = { isChecked ->
                                    if (isCaregiver) {
                                        if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                            smsPermissionsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                                        }
                                        viewModel.updateAlertSettings(alerts.copy(isCaregiverSosWakeupEnabled = isChecked))
                                    } else {
                                        if (isChecked) {
                                            val needed = mutableListOf<String>()
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                                needed.add(Manifest.permission.SEND_SMS)
                                            }
                                            if (alerts.isSmsQueryReplyEnabled &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                                needed.add(Manifest.permission.RECEIVE_SMS)
                                            }
                                            if (needed.isNotEmpty()) {
                                                smsPermissionsLauncher.launch(needed.toTypedArray())
                                            }
                                        }
                                        viewModel.updateAlertSettings(alerts.copy(isEmergencySmsEnabled = isChecked))
                                    }
                                    if (isChecked) isSmsCardExpanded = true
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = if (isCaregiver) PrimaryEmerald else Color(0xFFEF4444)
                                )
                            )

                            Icon(
                                imageVector = if (isSmsCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isSmsCardExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSmsCardExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Permissions Check & Warnings
                            val missingSend = !isCaregiver && alerts.isEmergencySmsEnabled && !hasSendSmsPermission
                            val missingReceive = (if (isCaregiver) alerts.isCaregiverSosWakeupEnabled else (alerts.isSmsQueryReplyEnabled || alerts.isCaregiverSosWakeupEnabled)) && !hasReceiveSmsPermission
                            val hasMissingSms = missingSend || missingReceive

                            if (hasMissingSms) {
                                val needed = mutableListOf<String>()
                                if (missingSend) needed.add(Manifest.permission.SEND_SMS)
                                if (missingReceive) needed.add(Manifest.permission.RECEIVE_SMS)

                                val activity = context as? android.app.Activity
                                val isPermanentlyDenied = hasAttemptedSmsRequest && activity != null && needed.any { perm ->
                                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) &&
                                    ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
                                }

                                val bannerTitle = if (missingSend && missingReceive) {
                                    if (isRu) "Требуется доступ к SMS (отправка и приём)" else "SMS Permissions Required (Send & Receive)"
                                } else if (missingSend) {
                                    if (isRu) "Требуется разрешение на отправку SMS" else "SMS Sending Permission Required"
                                } else {
                                    if (isRu) "Требуется разрешение на приём SMS" else "SMS Receiving Permission Required"
                                }

                                val bannerDesc = if (isPermanentlyDenied) {
                                    if (isRu) "Доступ заблокирован системой Android. Нажмите кнопку ниже, чтобы включить доступ к SMS в настройках приложения."
                                    else "Permission was permanently denied. Tap below to enable SMS permission in App Settings."
                                } else if (missingSend && missingReceive) {
                                    if (isRu) "Для авто-отправки экстренных сообщений близким и пробуждения фоловера при входящем SOS требуются разрешения на отправку и приём SMS."
                                    else "SMS send and receive permissions required for auto-sending alerts to contacts and waking follower on SOS."
                                } else if (missingSend) {
                                    if (isRu) "Для автоматической отправки экстренных сообщений близким при тяжёлой гипогликемии предоставьте системное разрешение на отправку SMS."
                                    else "To automatically send emergency SMS to trusted contacts on severe low, grant SMS sending permission."
                                } else {
                                    if (isRu) "Для распознавания входящих SOS-сообщений подопечного и включения сирены фоловера предоставьте разрешение на приём SMS."
                                    else "To read incoming patient SOS messages and sound follower siren, grant SMS receiving permission."
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("⚠️", fontSize = 16.sp)
                                            Text(
                                                text = bannerTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                        Text(
                                            text = bannerDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                        if (isPermanentlyDenied) {
                                            Button(
                                                onClick = {
                                                    val intent = Intent(
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.fromParts("package", context.packageName, null)
                                                    )
                                                    context.startActivity(intent)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isRu) "Открыть настройки приложения" else "Open App Settings",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        hasAttemptedSmsRequest = true
                                                        smsPermissionsLauncher.launch(needed.toTypedArray())
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = if (isRu) "Предоставить разрешение" else "Grant Permission",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val intent = Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.fromParts("package", context.packageName, null)
                                                        )
                                                        context.startActivity(intent)
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = if (isRu) "Настройки" else "Settings",
                                                        color = Color(0xFFEF4444),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Caregiver Overlay Permission Warning Plate
                            val missingOverlay = isCaregiver && alerts.isCaregiverSosWakeupEnabled && !hasOverlayPermission
                            if (missingOverlay) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("⚠️", fontSize = 16.sp)
                                            Text(
                                                text = if (isRu) "Разрешите показ поверх других приложений" else "Overlay Permission Required",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                        Text(
                                            text = if (isRu) "Без разрешения «Поверх других окон» экран тревоги фоловера не сможет пробить экран блокировки и включить дисплей при ночном SOS."
                                                   else "Without 'Draw over other apps' permission, follower rescue screen cannot wake the device and bypass lockscreen during night SOS.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                        Button(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    overlayPermissionLauncher.launch(intent)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (isRu) "Разрешить «Поверх других приложений»" else "Grant Overlay Permission",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Primary phone input
                            OutlinedTextField(
                                value = localPrimaryPhone,
                                onValueChange = { phone ->
                                    localPrimaryPhone = phone
                                    viewModel.updateAlertSettings(alerts.copy(emergencyContactPhone = phone))
                                },
                                label = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Номер подопечного (белый список)" else "Patient phone (whitelist)"
                                        } else {
                                            if (isRu) "Основной телефон близкого (+...)" else "Primary contact phone (+...)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(if (isCaregiver) "+7 900 123-45-67 (подопечный)" else "+7 900 123-45-67")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isCaregiver) Icons.Default.Person else Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (isCaregiver) PrimaryEmerald else ActionBlue
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Primary contact name
                            OutlinedTextField(
                                value = localPrimaryName,
                                onValueChange = { name ->
                                    localPrimaryName = name
                                    viewModel.updateAlertSettings(alerts.copy(emergencyContactName = name))
                                },
                                label = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Имя подопечного (необязательно)" else "Patient name (optional)"
                                        } else {
                                            if (isRu) "Имя основного контакта (необязательно)" else "Primary contact name (optional)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Сын, Дочь, Мама..." else "Son, Daughter, Relative..."
                                        } else {
                                            if (isRu) "Мама, Муж, Доктор..." else "Mom, Spouse, Doctor..."
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Secondary phone input
                            OutlinedTextField(
                                value = localSecondaryPhone,
                                onValueChange = { phone ->
                                    localSecondaryPhone = phone
                                    viewModel.updateAlertSettings(alerts.copy(secondaryEmergencyContactPhone = phone))
                                },
                                label = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Второй номер подопечного (или 2-й подопечный)" else "Secondary patient phone"
                                        } else {
                                            if (isRu) "Резервный телефон близкого (+...)" else "Secondary contact phone (+...)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(if (isCaregiver) "+7 900 765-43-21 (номер 2)" else "+7 900 765-43-21 (резерв)")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isCaregiver) Icons.Default.Person else Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (isCaregiver) PrimaryEmerald.copy(alpha = 0.8f) else ActionBlue
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Secondary contact name
                            OutlinedTextField(
                                value = localSecondaryName,
                                onValueChange = { name ->
                                    localSecondaryName = name
                                    viewModel.updateAlertSettings(alerts.copy(secondaryEmergencyContactName = name))
                                },
                                label = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Имя (подопечный 2 или запасной контакт)" else "Name (2nd patient or backup contact)"
                                        } else {
                                            if (isRu) "Имя резервного контакта (необязательно)" else "Secondary contact name (optional)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(
                                        if (isCaregiver) {
                                            if (isRu) "Второй ребёнок, Папа..." else "Second child, Dad..."
                                        } else {
                                            if (isRu) "Папа, Бабушка..." else "Dad, Grandma..."
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (!isCaregiver) {
                                // ===== PATIENT-ONLY CONTROLS =====
                                // Delay picker (3 min / 5 min / 10 min)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isRu) "Ожидание реакции:" else "Reaction timeout:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val delayOptions = listOf(
                                            3 to if (isRu) "3 мин" else "3 min",
                                            5 to if (isRu) "5 мин" else "5 min",
                                            10 to if (isRu) "10 мин" else "10 min"
                                        )
                                        delayOptions.forEach { (mins, label) ->
                                            val isSelected = alerts.emergencySmsDelayMinutes == mins
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    viewModel.updateAlertSettings(alerts.copy(emergencySmsDelayMinutes = mins))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) Color(0x33EF4444) else Color.Transparent,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                )
                                            ) {
                                                Text(
                                                    text = label,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Attach coordinates switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isRu) "Прикреплять геопозицию (GPS)" else "Attach GPS Location",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isRu) "Ссылка на Google Maps в SMS для экстренного поиска" else "Google Maps link in SMS for swift finding",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = alerts.includeLocationInEmergencySms,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                            viewModel.updateAlertSettings(alerts.copy(includeLocationInEmergencySms = isChecked))
                                        }
                                    )
                                }

                                // SMS Query auto-reply toggle (offline internet fallback)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isRu) "Отвечать на SMS-запросы близких" else "Reply to SMS queries from contact",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isRu) "При отсутствии интернета отправляет сахар и TIR в ответ на SMS («сахар», «?»)"
                                            else "Sends glucose and TIR via SMS when internet is down in reply to 'sugar' or '?'",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = alerts.isSmsQueryReplyEnabled,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                                                smsPermissionsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                                            }
                                            viewModel.updateAlertSettings(alerts.copy(isSmsQueryReplyEnabled = isChecked))
                                        }
                                    )
                                }

                                // Send test SMS button
                                OutlinedButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                            smsPermissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                                        } else {
                                            viewModel.sendTestEmergencySms()
                                            testSmsCooldownSec = 60
                                        }
                                    },
                                    enabled = testSmsCooldownSec == 0,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (testSmsCooldownSec == 0) ActionBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (testSmsCooldownSec == 0) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (testSmsCooldownSec > 0) {
                                            if (isRu) "Отправить тестовое SMS (${testSmsCooldownSec}с)" else "Send test SMS (${testSmsCooldownSec}s)"
                                        } else {
                                            if (isRu) "Отправить тестовое SMS" else "Send test SMS"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (testSmsCooldownSec == 0) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            } else {
                                // ===== CAREGIVER-ONLY CONTROLS =====
                                // Test Caregiver Screen & Siren Button with 5s countdown so caregiver can lock phone
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (testCaregiverSosCountdownSec == 0) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                                    Toast.makeText(
                                                        context,
                                                        if (isRu) "Внимание: для показа поверх заблокированного экрана включите «Поверх других приложений»!"
                                                        else "Notice: Enable 'Display over other apps' to wake lockscreen!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (isRu) "Заблокируйте экран! Сирена включится через 5 секунд..."
                                                    else "Lock your screen! Siren will sound in 5 seconds...",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                testCaregiverSosCountdownSec = 5
                                                viewModel.startCaregiverSosTestCountdown(5)
                                            }
                                        },
                                        enabled = testCaregiverSosCountdownSec == 0,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryEmerald,
                                            contentColor = Color.White,
                                            disabledContainerColor = PrimaryEmerald.copy(alpha = 0.5f),
                                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (testCaregiverSosCountdownSec > 0) {
                                                if (isRu) "Запуск через ${testCaregiverSosCountdownSec}с (заблокируйте экран)..."
                                                else "Starting in ${testCaregiverSosCountdownSec}s (lock screen)..."
                                            } else {
                                                if (isRu) "Проверить SOS-режим (5 сек)"
                                                else "Test SOS mode (5s)"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
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

                                Text(
                                    text = if (isRu) "💡 Тест сирены 100% и окна поверх экрана блокировки без отправки SMS. Заблокируйте телефон сразу после нажатия."
                                           else "💡 Tests 100% volume siren and lockscreen window without SMS. Lock your phone immediately after tapping.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Heads-Up SMS in TIRUp toggle (shared for both roles)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "📲 Важные SMS в TIRUp" else "📲 Important SMS in TIRUp",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRu) "Показывать входящие SMS от фоловера/мастера в оверлее TIRUp"
                                               else "Show incoming SMS from follower/master in TIRUp overlay",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                                Checkbox(
                                    checked = alerts.isHeadsUpMessagingEnabled,
                                    onCheckedChange = { isChecked ->
                                        viewModel.updateAlertSettings(alerts.copy(isHeadsUpMessagingEnabled = isChecked))
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ActionBlue,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

                // Section: Weekly Sunday Digest
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    Column(modifier = Modifier.weight(1f)) {
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
                    Column(modifier = Modifier.weight(1f)) {
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

                        // Test 5: Direct Sound Previews (Extra-HYPO 50s & Extra-HYPER 16s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { handleSoundClick { viewModel.playExtraHypoTestSound() } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSoundPlaying) MaterialTheme.colorScheme.error
                                    else ColorVeryLow.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(text = if (isSoundPlaying) "⏹️" else "🚨", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSoundPlaying) (if (isRu) "Стоп" else "Stop")
                                           else (if (isRu) "Экстра-ГИПО (50с)" else "Extra-HYPO (50s)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSoundPlaying) MaterialTheme.colorScheme.error else ColorVeryLow
                                )
                            }

                            OutlinedButton(
                                onClick = { handleSoundClick { viewModel.playExtraHyperTestSound() } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSoundPlaying) MaterialTheme.colorScheme.error
                                    else ColorHigh.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(text = if (isSoundPlaying) "⏹️" else "⚠️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSoundPlaying) (if (isRu) "Стоп" else "Stop")
                                           else (if (isRu) "Экстра-ГИПЕР (16с)" else "Extra-HYPER (16s)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSoundPlaying) MaterialTheme.colorScheme.error else ColorHigh
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopAlertSounds() },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Text(text = "⏹️", fontSize = 14.sp)
                            }
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

@Composable
private fun AlertTierConfigRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    vibrate: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    flash: Boolean,
    onFlashChange: (Boolean) -> Unit,
    accentColor: Color,
    onTestClick: () -> Unit,
    isRu: Boolean,
    timerBadge: String? = null,
    isPaused: Boolean = false,
    thresholdBadge: String? = null,
    extraBadge: String? = null,
    onThresholdClick: (() -> Unit)? = null,
    isTesting: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.35f))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) ColorHigh else accentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thresholdBadge != null && onThresholdClick != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onThresholdClick() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = thresholdBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            if (extraBadge != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { onThresholdClick() }
                                ) {
                                    Text(
                                        text = extraBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (timerBadge != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, (if (isPaused) ColorHigh else accentColor).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = timerBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) ColorHigh else accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        thumbContent = if (isPaused) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Paused",
                                    tint = ColorHigh,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (isPaused) ColorHigh else accentColor
                        )
                    )
                }
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onVibrateChange(!vibrate) }
                        ) {
                            Checkbox(
                                checked = vibrate,
                                onCheckedChange = onVibrateChange,
                                colors = CheckboxDefaults.colors(checkedColor = accentColor)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вибро" else "Vibrate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onFlashChange(!flash) }
                        ) {
                            Checkbox(
                                checked = flash,
                                onCheckedChange = onFlashChange,
                                colors = CheckboxDefaults.colors(checkedColor = accentColor)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isRu) "Вспышка" else "Flash",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val btnColor = if (isTesting) MaterialTheme.colorScheme.error else accentColor
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = btnColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, btnColor.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onTestClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isTesting) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = btnColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isTesting) (if (isRu) "Стоп" else "Stop")
                                       else (if (isRu) "Тест" else "Test"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = btnColor
                            )
                        }
                    }
                }
            }
        }
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
            String.format(java.util.Locale.US, "%d:%02d", diffSec / 60, diffSec % 60)
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



