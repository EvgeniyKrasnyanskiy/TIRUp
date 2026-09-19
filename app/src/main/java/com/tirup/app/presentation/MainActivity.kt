package com.tirup.app.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableLongStateOf
import com.tirup.app.domain.model.BleBridgeRole
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.formatDeviceRemainingTime
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorTight
import com.tirup.app.presentation.theme.ColorVeryHigh
import com.tirup.app.presentation.theme.ColorVeryLow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import com.tirup.app.domain.model.millisRemaining
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.receiver.DexdripBroadcastReceiver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tirup.app.TirupApplication
import com.tirup.app.domain.model.ThemeMode
import com.tirup.app.presentation.components.HelpAndDisclaimerDialog
import com.tirup.app.presentation.focus.FocusScreen
import com.tirup.app.presentation.focus.FocusViewModel
import com.tirup.app.presentation.reports.ReportsScreen
import com.tirup.app.presentation.reports.ReportsViewModel
import com.tirup.app.presentation.settings.SettingsScreen
import com.tirup.app.presentation.settings.SettingsViewModel
import com.tirup.app.presentation.settings.dialogs.Hba1cHistoryDialog
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.PrimaryEmerald
import com.tirup.app.presentation.theme.TIRUpTheme
import com.tirup.app.presentation.trends.TrendsScreen
import com.tirup.app.presentation.trends.TrendsViewModel
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.backup.BackupSummary
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                GlucoseAlertManager.silenceCurrentSoundOnly()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {}

        val app = application as TirupApplication
        val database = app.database
        val glucoseRepo = app.glucoseRepository
        val settingsRepo = app.settingsRepository
        val importer = app.streamingImporter

        val focusViewModel = androidx.lifecycle.ViewModelProvider(
            this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return FocusViewModel(application, glucoseRepo, settingsRepo) as T
                }
            }
        )[FocusViewModel::class.java]

        val trendsViewModel = androidx.lifecycle.ViewModelProvider(
            this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return TrendsViewModel(glucoseRepo, settingsRepo, applicationContext) as T
                }
            }
        )[TrendsViewModel::class.java]

        val reportsViewModel = androidx.lifecycle.ViewModelProvider(
            this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return ReportsViewModel(applicationContext, glucoseRepo, settingsRepo, importer, database) as T
                }
            }
        )[ReportsViewModel::class.java]

        val settingsViewModel = androidx.lifecycle.ViewModelProvider(
            this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(application, settingsRepo, glucoseRepo, database) as T
                }
            }
        )[SettingsViewModel::class.java]

        // Proactively clean up any historical duplicates (<60s jitter between xDrip and BLE)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                glucoseRepo.purgeDuplicateReadings()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Duplicate purge skipped: ${e.message}")
            }
            try {
                DexdripBroadcastReceiver.sendXdripBroadcastServiceHandshake(this@MainActivity)
            } catch (_: Exception) {}
        }

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val languageCode = settingsState.userSettings.language
            val themeMode = settingsState.userSettings.themeMode
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            ProvideLocalizedApp(languageCode = languageCode) {
                TIRUpTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigationRoot(
                            focusViewModel = focusViewModel,
                            trendsViewModel = trendsViewModel,
                            reportsViewModel = reportsViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_GOTO_FOCUS = "com.tirup.app.GOTO_FOCUS"
        const val EXTRA_GOTO_WEEKLY_DIGEST = "com.tirup.app.GOTO_WEEKLY_DIGEST"
        const val EXTRA_GOTO_HBA1C = "com.tirup.app.GOTO_HBA1C"
        const val EXTRA_GOTO_YEAR_END = "com.tirup.app.GOTO_YEAR_END"
        val navigateToFocusEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val navigateToDigestEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val navigateToHba1cEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val navigateToYearEndEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_GOTO_FOCUS, false) == true) {
            navigateToFocusEvent.tryEmit(System.currentTimeMillis())
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_WEEKLY_DIGEST, false) == true) {
            navigateToDigestEvent.tryEmit(System.currentTimeMillis())
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_HBA1C, false) == true) {
            navigateToHba1cEvent.tryEmit(System.currentTimeMillis())
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_YEAR_END, false) == true) {
            navigateToYearEndEvent.tryEmit(System.currentTimeMillis())
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra(EXTRA_GOTO_FOCUS, false) == true) {
            navigateToFocusEvent.tryEmit(System.currentTimeMillis())
            intent.removeExtra(EXTRA_GOTO_FOCUS)
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_WEEKLY_DIGEST, false) == true) {
            navigateToDigestEvent.tryEmit(System.currentTimeMillis())
            intent.removeExtra(EXTRA_GOTO_WEEKLY_DIGEST)
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_HBA1C, false) == true) {
            navigateToHba1cEvent.tryEmit(System.currentTimeMillis())
            intent.removeExtra(EXTRA_GOTO_HBA1C)
        }
        if (intent?.getBooleanExtra(EXTRA_GOTO_YEAR_END, false) == true) {
            navigateToYearEndEvent.tryEmit(System.currentTimeMillis())
            intent.removeExtra(EXTRA_GOTO_YEAR_END)
        }
        GlucoseAlertManager.dismissCriticalAlarm(this, fromUser = true)

        // Ensure floating bubble is active if enabled in settings
        val app = applicationContext as? TirupApplication
        app?.let { application ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val settings = application.settingsRepository.getSettings().first()
                    if (settings.isFloatingBubbleEnabled && android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                        com.tirup.app.presentation.overlay.FloatingBubbleService.start(applicationContext)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
        ) {
            GlucoseAlertManager.silenceCurrentSoundOnly()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}
    }

    @Composable
    private fun ProvideLocalizedApp(
        languageCode: String,
        content: @Composable () -> Unit
    ) {
        val targetLocale = if (languageCode.equals("EN", ignoreCase = true)) Locale.ENGLISH else Locale("ru")
        val currentConfiguration = LocalConfiguration.current

        val localizedConfiguration = remember(languageCode, currentConfiguration) {
            Configuration(currentConfiguration).apply {
                setLocale(targetLocale)
                setLayoutDirection(targetLocale)
            }
        }

        LaunchedEffect(languageCode) {
            Locale.setDefault(targetLocale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(localizedConfiguration, resources.displayMetrics)
        }

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
        ) {
            androidx.compose.runtime.key(languageCode) {
                content()
            }
        }
    }
}

@Composable
fun AppNavigationRoot(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var backupSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var hasCheckedBackup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as TirupApplication

    LaunchedEffect(Unit) {
        if (!settingsState.userSettings.hasSeenOnboarding && !hasCheckedBackup) {
            withContext(Dispatchers.IO) {
                backupSummary = AutoBackupManager.getBackupSummary(context)
            }
            hasCheckedBackup = true
        }
    }

    LaunchedEffect(Unit) {
        MainActivity.navigateToFocusEvent.collect {
            navController.popBackStack("main_pager", inclusive = false)
        }
    }

    if (backupSummary != null) {
        val summary = backupSummary!!
        val isRu = settingsState.userSettings.language.equals("RU", ignoreCase = true)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val dateStr = if (summary.exportedAt > 0L) dateFormat.format(Date(summary.exportedAt)) else ""

        AlertDialog(
            onDismissRequest = { backupSummary = null },
            title = {
                Text(
                    text = if (isRu) "Найдена резервная копия" else "Backup Found",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isRu) "В папке TIRUp/Backups обнаружена сохранённая история мониторинга:"
                               else "Found saved monitoring history in TIRUp/Backups:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary.patientName.isNotBlank()) {
                        Text(
                            text = "• ${if (isRu) "Пациент" else "Patient"}: ${summary.patientName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "• ${if (isRu) "Измерений сахара" else "Glucose readings"}: ${summary.readingsCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (dateStr.isNotBlank()) {
                        Text(
                            text = "• ${if (isRu) "Последнее сохранение" else "Last backup"}: $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRu) "Вы действительно хотите заменить текущие настройки и данные конфигурацией из резервной копии?" else "Restore data and profile settings?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = AutoBackupManager.restoreBackup(context, app.database, app.settingsRepository)
                            if (res.isSuccess) {
                                Toast.makeText(
                                    context,
                                    if (isRu) "Данные успешно восстановлены (${res.getOrNull()} записей)" else "Data restored successfully (${res.getOrNull()} readings)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            backupSummary = null
                            settingsViewModel.setHasSeenOnboarding(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Text(
                        text = if (isRu) "Восстановить" else "Restore",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        backupSummary = null
                    }
                ) {
                    Text(
                        text = if (isRu) "Начать с нуля" else "Start fresh",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    } else if (!settingsState.userSettings.hasSeenOnboarding) {
        val isRu = settingsState.userSettings.language.equals("RU", ignoreCase = true)
        HelpAndDisclaimerDialog(
            isRu = isRu,
            onSaveManual = { settingsViewModel.saveUserManualToDownloads() },
            onDismiss = {
                settingsViewModel.setHasSeenOnboarding(true)
            }
        )
    }

    LaunchedEffect(Unit) {
        MainActivity.navigateToYearEndEvent.collect {
            navController.navigate("settings?target=year_end")
        }
    }

    var bleSignalBannerText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bleSignalBannerText) {
        if (bleSignalBannerText != null) {
            delay(6000L) // 6 seconds duration for open-field range test visibility
            bleSignalBannerText = null
        }
    }

    LaunchedEffect(Unit) {
        com.tirup.app.data.ble.BleObserverManager.packetReceivedEvent.collect { pair ->
            val (packet, rssi) = pair
            val currentSettings = settingsViewModel.uiState.value.userSettings
            val ble = currentSettings.bleBridgeSettings
            val isSearchOrTest = (com.tirup.app.data.ble.BleObserverManager.boostRemainingSec.value > 0) ||
                                 (packet.timestamp == 0L || packet.valueMmol <= 0.1)

            if (ble.showPacketBanner || isSearchOrTest) {
                val signalDot = if (rssi >= -75) "🟢" else if (rssi >= -85) "🟡" else "🔴"
                val batStr = if (packet.batteryPercent in 0..100) ", 🔋${packet.batteryPercent}%" else ""
                val isRussian = currentSettings.language.equals("RU", ignoreCase = true)
                val msg = if (packet.valueMmol <= 0.1 || packet.timestamp == 0L) {
                    if (isRussian) "$signalDot BLE: 📡 Тест связи (нет данных сенсора)$batStr (RSSI: $rssi dBm)"
                    else "$signalDot BLE: 📡 Range test (no sensor data)$batStr (RSSI: $rssi dBm)"
                } else {
                    val iobStr = if (packet.iob > 0.0) ", 💉${String.format(java.util.Locale.US, "%.1f", packet.iob)}" else ""
                    "$signalDot BLE: 🩸${String.format(java.util.Locale.US, "%.1f", packet.valueMmol)} ${packet.trendArrow}$iobStr$batStr (RSSI: $rssi dBm)"
                }
                bleSignalBannerText = msg
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main_pager"
        ) {
            composable("main_pager") {
                MainPagerScaffold(
                    focusViewModel = focusViewModel,
                    trendsViewModel = trendsViewModel,
                    reportsViewModel = reportsViewModel,
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = { target ->
                        if (!target.isNullOrBlank()) {
                            navController.navigate("settings?target=$target")
                        } else {
                            navController.navigate("settings")
                        }
                    }
                )
            }

            composable(
                route = "settings?target={target}",
                arguments = listOf(
                    navArgument("target") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val target = backStackEntry.arguments?.getString("target")
                SettingsScreen(
                    viewModel = settingsViewModel,
                    target = target,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Global Floating BLE Range Signal Banner (Visible on any screen in app)
        AnimatedVisibility(
            visible = bleSignalBannerText != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .zIndex(999f)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                border = BorderStroke(1.5.dp, PrimaryEmerald),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bleSignalBannerText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScaffold(
    focusViewModel: FocusViewModel,
    trendsViewModel: TrendsViewModel,
    reportsViewModel: ReportsViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: (String?) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var showHba1cDialog by remember { mutableStateOf(false) }
    var showQuickHud by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val focusState by focusViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val userSettings = settingsState.userSettings
    val isRu = userSettings.language.equals("RU", ignoreCase = true)
    val isMmol = userSettings.unit == GlucoseUnit.MMOL_L

    fun markUserActivity() {
        lastInteractionTime = System.currentTimeMillis()
        if (!isBottomBarVisible) {
            isBottomBarVisible = true
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        markUserActivity()
    }

    LaunchedEffect(lastInteractionTime, isBottomBarVisible, showQuickHud) {
        if (isBottomBarVisible && !showQuickHud) {
            delay(2200L)
            if (!showQuickHud) {
                isBottomBarVisible = false
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            MainActivity.navigateToFocusEvent.collect {
                pagerState.animateScrollToPage(1)
            }
        }
        launch {
            MainActivity.navigateToDigestEvent.collect {
                pagerState.animateScrollToPage(0)
                trendsViewModel.openWeeklyDigest()
            }
        }
        launch {
            MainActivity.navigateToHba1cEvent.collect {
                showHba1cDialog = true
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                markUserActivity()
                val delta = available.y
                if (delta < -12f) {
                    isBottomBarVisible = false
                } else if (delta > 12f) {
                    isBottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val latestReading = focusState.latestReading
    val glucoseValStr = if (latestReading != null) {
        if (isMmol) String.format(Locale.US, "%.1f", latestReading.valueMmol)
        else String.format(Locale.US, "%.0f", latestReading.getValue(GlucoseUnit.MG_DL))
    } else "--"
    val unitStr = if (isMmol) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")
    val trendArrow = latestReading?.trendArrow ?: ""

    val glucoseColor = if (latestReading != null) {
        val v = latestReading.valueMmol
        when {
            v < 3.0 -> ColorVeryLow
            v < 3.9 -> ColorVeryLow
            v <= 7.8 -> ColorTight
            v <= 10.0 -> PrimaryEmerald
            v <= 13.9 -> ColorHigh
            else -> ColorVeryHigh
        }
    } else ActionBlue

    val rateStr = remember(focusState.recentReadings, isMmol, isRu) {
        if (focusState.recentReadings.size >= 2) {
            val r0 = focusState.recentReadings[0]
            val r1 = focusState.recentReadings[1]
            val delta = r0.valueMmol - r1.valueMmol
            val dtMin = ((r0.timestamp - r1.timestamp) / 60000.0).coerceIn(0.5, 15.0)
            val rate = delta / dtMin
            val sign = if (rate >= 0) "+" else ""
            val rateVal = if (isMmol) rate else rate * 18.0182
            val u = if (isMmol) (if (isRu) "ммоль/мин" else "mmol/min") else (if (isRu) "мг/мин" else "mg/min")
            String.format(Locale.US, "%s%.2f %s", sign, rateVal, u)
        } else ""
    }

    val iobStr = latestReading?.iob?.let { if (it > 0.0) String.format(Locale.US, "%.1f %s", it, if (isRu) "ед" else "u") else "0.0" } ?: "—"
    val cobStr = latestReading?.cob?.let { if (it > 0.0) String.format(Locale.US, "%.0f %s", it, if (isRu) "г" else "g") else "0" } ?: "—"
    val tirFormatted = String.format(Locale.US, "%.0f%%", focusState.statistics.tirPercent)
    val sensorRemaining = formatDeviceRemainingTime(
        millisRemaining = focusState.sensorStatus.millisRemaining,
        installedAt = focusState.sensorStatus.installedAt,
        isRu = isRu,
        isCompact = true
    )

    val bleSettings = userSettings.bleBridgeSettings
    val batteryStr = remember(bleSettings, context) {
        val bat = if (bleSettings.role == BleBridgeRole.OBSERVER && bleSettings.lastMasterBattery in 0..100) {
            bleSettings.lastMasterBattery
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }
        if (bat in 0..100) "🔋 $bat%" else ""
    }
    val rssiStr = if (bleSettings.role == BleBridgeRole.OBSERVER && bleSettings.lastRssi != 0) "📶 ${bleSettings.lastRssi} dBm" else ""

    val tabs = listOf(
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_trends),
            icon = Icons.AutoMirrored.Filled.TrendingUp
        ),
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_focus),
            icon = Icons.Default.Adjust
        ),
        NavigationItem(
            title = androidx.compose.ui.res.stringResource(com.tirup.app.R.string.nav_reports),
            icon = Icons.Default.Description
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    shadowElevation = 4.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.height(48.dp)
                    ) {
                        tabs.forEachIndexed { index, item ->
                            val selected = pagerState.currentPage == index
                            val isCenterHome = index == 1
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    markUserActivity()
                                    if (!isCenterHome) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                icon = {
                                    if (isCenterHome) {
                                        // Premium Accent FAB-style Center Button with Quick Glance HUD gesture (> 1.1s)
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .shadow(
                                                    elevation = if (selected) 6.dp else 2.dp,
                                                    shape = CircleShape
                                                )
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) ActionBlue
                                                    else ActionBlue.copy(alpha = 0.14f)
                                                )
                                                .border(
                                                    BorderStroke(
                                                        width = if (selected) 2.dp else 1.5.dp,
                                                        color = if (selected) Color.White.copy(alpha = 0.4f) else ActionBlue.copy(alpha = 0.45f)
                                                    ),
                                                    CircleShape
                                                )
                                                .pointerInput(Unit) {
                                                    awaitEachGesture {
                                                        awaitFirstDown(requireUnconsumed = false)
                                                        markUserActivity()
                                                        var isLongPressed = false

                                                        val longPressJob = coroutineScope.launch {
                                                            delay(330L) // 0.33 seconds hold
                                                            isLongPressed = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            showQuickHud = true
                                                        }

                                                        try {
                                                            var pointerUp = false
                                                            while (!pointerUp) {
                                                                val event = awaitPointerEvent()
                                                                if (event.changes.all { !it.pressed }) {
                                                                    pointerUp = true
                                                                }
                                                            }
                                                        } finally {
                                                            longPressJob.cancel()
                                                            if (showQuickHud) {
                                                                showQuickHud = false
                                                                markUserActivity()
                                                            } else if (!isLongPressed) {
                                                                coroutineScope.launch {
                                                                    pagerState.animateScrollToPage(1)
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                tint = if (selected) Color.White else ActionBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else {
                                        // Standard Side Buttons
                                        Box(
                                            modifier = Modifier
                                                .size(width = 44.dp, height = 30.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (selected) ActionBlue.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.title,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                label = null,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ActionBlue,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        markUserActivity()
                    }
                }
                .nestedScroll(nestedScrollConnection)
                .padding(bottom = if (isBottomBarVisible) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> TrendsScreen(viewModel = trendsViewModel, onOpenSettings = { onOpenSettings(null) })
                    1 -> FocusScreen(viewModel = focusViewModel, onOpenSettings = onOpenSettings)
                    2 -> ReportsScreen(
                        viewModel = reportsViewModel,
                        onOpenSettings = { onOpenSettings(null) },
                        onOpenHba1c = { showHba1cDialog = true }
                    )
                }
            }

            // Floating Centered Quick Glance HUD (60% screen height, 3x larger typography)
            AnimatedVisibility(
                visible = showQuickHud,
                enter = fadeIn() + scaleIn(initialScale = 0.82f),
                exit = fadeOut() + scaleOut(targetScale = 0.82f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(100f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.60f),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    border = BorderStroke(2.dp, glucoseColor.copy(alpha = 0.7f)),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1. Header: TIR % and Rate of Change
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = PrimaryEmerald.copy(alpha = 0.16f),
                                border = BorderStroke(1.5.dp, PrimaryEmerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "🎯 $tirFormatted TIR",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PrimaryEmerald,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }

                            if (rateStr.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ActionBlue.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = rateStr,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionBlue,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // 2. Huge Central Hero: Glucose value + Arrow on top, Unit centered below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = glucoseValStr,
                                    fontSize = 108.sp,
                                    fontWeight = FontWeight.Black,
                                    color = glucoseColor,
                                    letterSpacing = (-3).sp,
                                    lineHeight = 108.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (trendArrow.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = trendArrow,
                                        fontSize = 78.sp,
                                        fontWeight = FontWeight.Black,
                                        color = glucoseColor,
                                        lineHeight = 78.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = unitStr,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // 3. IOB & COB Big Cards (Vertical layout inside each card)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "💉", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isRu) "Инсулин" else "Insulin",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = iobStr,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🥖", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isRu) "Углеводы" else "Carbs",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cobStr,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 4. Telemetry Footer: Sensor Remaining & Battery / RSSI
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(text = "⏱️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${if (isRu) "Сенсор" else "Sensor"}: $sensorRemaining",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            val telemetryParts = listOfNotNull(
                                batteryStr.ifBlank { null },
                                rssiStr.ifBlank { null }
                            )
                            if (telemetryParts.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = telemetryParts.joinToString("  "),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHba1cDialog) {
        val currentSettingsState by settingsViewModel.uiState.collectAsState()
        Hba1cHistoryDialog(
            records = currentSettingsState.userSettings.hba1cRecords,
            sensorGmi90d = currentSettingsState.sensorGmi90d,
            meanGlucose90dMmol = currentSettingsState.meanGlucose90dMmol,
            tirPercent90d = currentSettingsState.tirPercent90d,
            skippedQuarterTimestamp = currentSettingsState.userSettings.hba1cSkippedQuarterTimestamp,
            isRu = isRu,
            onAddRecord = { value, timestamp, lab, notes ->
                settingsViewModel.addHba1cRecord(valuePercent = value, timestamp = timestamp, labName = lab, notes = notes)
            },
            onDeleteRecord = { id ->
                settingsViewModel.deleteHba1cRecord(id)
            },
            onSkipQuarter = {
                settingsViewModel.skipHba1cQuarter()
            },
            onExportPdf = { onSaved ->
                settingsViewModel.exportHba1cReportToPdf(onSaved)
            },
            onDismiss = { showHba1cDialog = false }
        )
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector
)
