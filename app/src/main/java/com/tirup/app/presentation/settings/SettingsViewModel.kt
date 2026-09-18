package com.tirup.app.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.LabHba1cRecord
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import android.net.Uri
import com.tirup.app.data.backup.BackupSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface SettingsEvent {
    data class SavedToDownloads(val filePath: String, val message: String? = null) : SettingsEvent
    data class Info(val message: String) : SettingsEvent
    data class ShareFile(val file: File, val mimeType: String, val title: String) : SettingsEvent
}

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val showClearDialog: Boolean = false,
    val infoMessage: String? = null,
    val backupSummary: BackupSummary? = null,
    val pendingRestoreSummary: BackupSummary? = null,
    val pendingRestoreUri: Uri? = null,
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val sensorGmi90d: Double? = null,
    val meanGlucose90dMmol: Double? = null,
    val tirPercent90d: Int? = null,
    val showHba1cDialog: Boolean = false,
    val yearEndStats: YearEndStats? = null,
    val showYearEndDialog: Boolean = false
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val glucoseRepository: GlucoseRepository,
    private val database: AppDatabase
) : AndroidViewModel(application) {

    private val context: android.content.Context get() = getApplication<Application>()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val latestReading: StateFlow<GlucoseReading?> = glucoseRepository.getLatestReading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(userSettings = settings) }
            }
        }
        loadBackupSummary()
        load90dMetrics()
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(language = language)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context)
        }
    }

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(unit = unit)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context)
        }
    }

    fun setThemeMode(mode: com.tirup.app.domain.model.ThemeMode) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(themeMode = mode)
            settingsRepository.updateSettings(updated)
        }
    }

    fun setDeviceRemindersEnabled(enabled: Boolean) {
        val current = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(current.copy(isDeviceRemindersEnabled = enabled))
        }
    }

    fun setSensorReminderEnabled(enabled: Boolean) {
        val current = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(current.copy(isSensorReminderEnabled = enabled))
        }
    }

    fun setPumpReminderEnabled(enabled: Boolean) {
        val current = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(current.copy(isPumpReminderEnabled = enabled))
        }
    }

    fun setLancetReminderEnabled(enabled: Boolean) {
        val current = _uiState.value.userSettings
        viewModelScope.launch {
            settingsRepository.updateSettings(current.copy(isLancetReminderEnabled = enabled))
        }
    }

    fun setWeeklyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isWeeklyDigestEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (enabled) {
                com.tirup.app.data.worker.WeeklyDigestWorker.schedule(context)
            }
        }
    }

    fun setLockscreenNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isLockscreenNotificationEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (enabled) {
                val latest = glucoseRepository.getLatestReading().firstOrNull()
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val todayDomain = withContext(Dispatchers.IO) {
                    val todayEntities = database.glucoseReadingDao().getReadingsBetweenSync(
                        calendar.timeInMillis,
                        System.currentTimeMillis() + 60_000L
                    )
                    todayEntities.map { it.toDomain() }
                }
                com.tirup.app.data.alert.GlucoseAlertManager.updateLockscreenNotification(
                    context = context,
                    latestReading = latest,
                    todayReadings = todayDomain,
                    settings = updated,
                    streakDays = glucoseRepository.getStreakDays().firstOrNull() ?: 0
                )
            } else {
                com.tirup.app.data.alert.GlucoseAlertManager.dismissLockscreenNotification(context)
            }
        }
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(hasSeenOnboarding = hasSeen)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdateNightHours(nightStart: Int, nightEnd: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(
                nightStartHour = nightStart,
                nightEndHour = nightEnd
            )
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdateThresholds(
        tirLow: Double,
        tirHigh: Double,
        tingHigh: Double,
        tirGoal: Int,
        tingGoal: Int,
        nightStart: Int,
        nightEnd: Int
    ) {
        viewModelScope.launch {
            val updatedRanges = TargetRanges(
                tirLowMmol = tirLow,
                tirHighMmol = tirHigh,
                tingHighMmol = tingHigh,
                tirGoalPercent = tirGoal,
                tingGoalPercent = tingGoal
            )
            val updated = _uiState.value.userSettings.copy(
                targetRanges = updatedRanges,
                nightStartHour = nightStart,
                nightEndHour = nightEnd
            )
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun autoUpdatePatientProfile(profile: PatientProfile) {
        viewModelScope.launch {
            val current = _uiState.value.userSettings
            val pumpReset = if (!com.tirup.app.domain.model.isPumpTherapy(profile.therapyType)) {
                com.tirup.app.domain.model.PumpSetStatus()
            } else {
                current.pumpSetStatus
            }
            val updated = current.copy(patientProfile = profile, pumpSetStatus = pumpReset)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            AutoBackupManager.maybeTriggerAutoBackup(context, database, settingsRepository, force = true)
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isAutoBackupEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (enabled) {
                AutoBackupManager.scheduleNextDailyBackup(context)
                AutoBackupManager.maybeTriggerAutoBackup(context, database, settingsRepository, force = true)
            } else {
                AutoBackupManager.cancelDailyBackup(context)
            }
        }
    }

    fun loadBackupSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = AutoBackupManager.getBackupSummary(context)
            _uiState.update { it.copy(backupSummary = summary) }
        }
    }

    fun createBackupNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupInProgress = true) }
            val current = _uiState.value.userSettings
            val res = AutoBackupManager.performBackup(context, database, current)
            val now = System.currentTimeMillis()
            val isRu = current.language.equals("RU", ignoreCase = true)
            if (res.isSuccess) {
                settingsRepository.updateSettings(current.copy(lastBackupTimestamp = now))
                _uiState.update { it.copy(userSettings = current.copy(lastBackupTimestamp = now)) }
                loadBackupSummary()
                _events.emit(SettingsEvent.Info(if (isRu) "Резервная копия сохранена в Документы/TIRUp/Backups/" else "Backup saved to Documents/TIRUp/Backups/"))
            } else {
                _events.emit(SettingsEvent.Info(if (isRu) "Ошибка создания бэкапа: ${res.exceptionOrNull()?.message}" else "Backup failed: ${res.exceptionOrNull()?.message}"))
            }
            _uiState.update { it.copy(isBackupInProgress = false) }
        }
    }

    fun shareBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupInProgress = true) }
            try {
                val current = _uiState.value.userSettings
                val zipFile = AutoBackupManager.createZipBackup(context, database, current)
                val isRu = current.language.equals("RU", ignoreCase = true)
                _events.emit(
                    SettingsEvent.ShareFile(
                        file = zipFile,
                        mimeType = "application/zip",
                        title = if (isRu) "Поделиться резервной копией TIRUp" else "Share TIRUp Backup"
                    )
                )
            } catch (e: Exception) {
                val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
                _events.emit(SettingsEvent.Info(if (isRu) "Ошибка архивации: ${e.message}" else "Archive failed: ${e.message}"))
            } finally {
                _uiState.update { it.copy(isBackupInProgress = false) }
            }
        }
    }

    fun exportBackupToUri(targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBackupInProgress = true) }
            try {
                val current = _uiState.value.userSettings
                val zipFile = AutoBackupManager.createZipBackup(context, database, current)
                context.contentResolver.openOutputStream(targetUri)?.use { os ->
                    zipFile.inputStream().use { it.copyTo(os) }
                }
                val isRu = current.language.equals("RU", ignoreCase = true)
                _events.emit(SettingsEvent.Info(if (isRu) "Резервная копия успешно экспортирована" else "Backup exported successfully"))
            } catch (e: Exception) {
                val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
                _events.emit(SettingsEvent.Info(if (isRu) "Ошибка экспорта: ${e.message}" else "Export failed: ${e.message}"))
            } finally {
                _uiState.update { it.copy(isBackupInProgress = false) }
            }
        }
    }

    fun prepareRestoreFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = AutoBackupManager.inspectBackupUri(context, uri)
            if (summary != null) {
                _uiState.update {
                    it.copy(
                        pendingRestoreSummary = summary,
                        pendingRestoreUri = uri
                    )
                }
            } else {
                val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
                _events.emit(SettingsEvent.Info(if (isRu) "Не удалось прочитать выбранный файл бэкапа" else "Could not read chosen backup file"))
            }
        }
    }

    fun dismissRestoreDialog() {
        _uiState.update {
            it.copy(pendingRestoreSummary = null, pendingRestoreUri = null)
        }
    }

    fun confirmRestore() {
        val uri = _uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoreInProgress = true) }
            val res = AutoBackupManager.restoreFromUri(context, uri, database, settingsRepository)
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            if (res.isSuccess) {
                val result = res.getOrNull()!!
                val msg = if (isRu) {
                    "Восстановлено: ${result.readingsRestored} замеров, ${result.treatmentsRestored} меток" +
                            (if (result.settingsRestored) ", настройки" else "")
                } else {
                    "Restored: ${result.readingsRestored} readings, ${result.treatmentsRestored} treatments" +
                            (if (result.settingsRestored) ", settings" else "")
                }
                _events.emit(SettingsEvent.Info(msg))
                loadBackupSummary()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                _events.emit(SettingsEvent.Info(if (isRu) "Ошибка восстановления: $err" else "Restore failed: $err"))
            }
            _uiState.update {
                it.copy(
                    isRestoreInProgress = false,
                    pendingRestoreSummary = null,
                    pendingRestoreUri = null
                )
            }
        }
    }

    fun restoreLatestAutoBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoreInProgress = true) }
            val res = AutoBackupManager.restoreLatestAutoBackup(context, database, settingsRepository)
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            if (res.isSuccess) {
                val result = res.getOrNull()!!
                val msg = if (isRu) {
                    "Восстановлено: ${result.readingsRestored} замеров, ${result.treatmentsRestored} меток" +
                            (if (result.settingsRestored) ", настройки" else "")
                } else {
                    "Restored: ${result.readingsRestored} readings, ${result.treatmentsRestored} treatments" +
                            (if (result.settingsRestored) ", settings" else "")
                }
                _events.emit(SettingsEvent.Info(msg))
                loadBackupSummary()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                _events.emit(SettingsEvent.Info(if (isRu) "Ошибка восстановления: $err" else "Restore failed: $err"))
            }
            _uiState.update {
                it.copy(
                    isRestoreInProgress = false,
                    pendingRestoreSummary = null,
                    pendingRestoreUri = null
                )
            }
        }
    }

    fun updateAlertSettings(alerts: AlertSettings) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(alertSettings = alerts)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    val isBleBroadcasting = com.tirup.app.data.ble.BleBroadcaster.isBroadcasting
    val bleBroadcastRemaining = com.tirup.app.data.ble.BleBroadcaster.broadcastRemainingSec
    val isBleScanning = com.tirup.app.data.ble.BleObserverManager.isScanningFlow
    val bleBoostRemaining = com.tirup.app.data.ble.BleObserverManager.boostRemainingSec

    fun updateBleBridgeSettings(ble: com.tirup.app.domain.model.BleBridgeSettings) {
        viewModelScope.launch {
            val prevRole = _uiState.value.userSettings.bleBridgeSettings.role
            if (prevRole == com.tirup.app.domain.model.BleBridgeRole.BROADCASTER && ble.role != com.tirup.app.domain.model.BleBridgeRole.BROADCASTER) {
                com.tirup.app.data.ble.BleBroadcaster.stopAdvertising()
            }
            val updated = _uiState.value.userSettings.copy(bleBridgeSettings = ble)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            com.tirup.app.data.ble.BleObserverManager.syncWithSettings(context, settingsRepository, glucoseRepository)
        }
    }

    fun sendBleTestPing() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val ble = _uiState.value.userSettings.bleBridgeSettings
            val latest = glucoseRepository.getLatestReading().firstOrNull()
            com.tirup.app.data.ble.BleBroadcaster.broadcastTestPing(
                context = context,
                reading = latest,
                settings = ble
            ) { success, message ->
                val text = if (success) {
                    if (isRu) "📡 $message" else "📡 BLE pulse started (30s)"
                } else {
                    if (isRu) "⚠️ $message" else "⚠️ BLE error: $message"
                }
                _uiState.update { it.copy(infoMessage = text) }
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var devTestsUnlockedTimestamp: Long = 0L
    private var settingsExitedTimestamp: Long = 0L
    private val _isDevTestsUnlocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isDevTestsUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean> = _isDevTestsUnlocked

    fun unlockDevTests() {
        devTestsUnlockedTimestamp = System.currentTimeMillis()
        settingsExitedTimestamp = 0L
        _isDevTestsUnlocked.value = true
    }

    fun checkDevTestsLockOnResume() {
        if (settingsExitedTimestamp > 0L && System.currentTimeMillis() - settingsExitedTimestamp > 5 * 60_000L) {
            _isDevTestsUnlocked.value = false
            settingsExitedTimestamp = 0L
        }
    }

    fun onSettingsScreenDisposed() {
        if (_isDevTestsUnlocked.value) {
            settingsExitedTimestamp = System.currentTimeMillis()
        }
    }

    fun startBleRangeTest(durationSec: Int = 5) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val ble = _uiState.value.userSettings.bleBridgeSettings
            if (ble.role == com.tirup.app.domain.model.BleBridgeRole.OBSERVER) {
                com.tirup.app.data.ble.BleObserverManager.boostScanForDuration(
                    context = context,
                    settingsRepository = settingsRepository,
                    glucoseRepository = glucoseRepository,
                    durationSec = durationSec
                )
                val msg = if (isRu) "🔍 Тест дальности: активный приём ($durationSec сек)..." else "🔍 Range test: active scan ($durationSec s)..."
                _uiState.update { it.copy(infoMessage = msg) }
            } else {
                val latest = glucoseRepository.getLatestReading().firstOrNull()
                com.tirup.app.data.ble.BleBroadcaster.broadcastRangeTestPing(
                    context = context,
                    reading = latest,
                    settings = ble,
                    durationSec = durationSec,
                    isRu = isRu
                ) { success, message ->
                    val text = if (success) {
                        if (isRu) "📡 Тест дальности: $message" else "📡 Range test: $message"
                    } else {
                        if (isRu) "⚠️ $message" else "⚠️ BLE error: $message"
                    }
                    _uiState.update { it.copy(infoMessage = text) }
                }
            }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun boostBleObserverScan() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            com.tirup.app.data.ble.BleObserverManager.boostScanFor60Sec(context, settingsRepository, glucoseRepository)
            val msg = if (isRu) "🔍 Активный поиск запущен (60 сек)" else "🔍 Boost scan active (60s)"
            _uiState.update { it.copy(infoMessage = msg) }
        }
    }

    fun restartBleSync() {
        com.tirup.app.data.ble.BleObserverManager.syncWithSettings(context, settingsRepository, glucoseRepository)
    }

    fun updateWidgetBackgroundOpacity(opacity: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(widgetBackgroundOpacity = opacity)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            com.tirup.app.presentation.widget.TirupWidgetUpdater.updateAllWidgets(context)
        }
    }

    fun toggleFloatingBubble(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isFloatingBubbleEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (enabled) {
                com.tirup.app.presentation.overlay.FloatingBubbleService.start(context)
            } else {
                com.tirup.app.presentation.overlay.FloatingBubbleService.stop(context)
            }
        }
    }

    fun toggleFloatingBubbleAlwaysVisible(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(isFloatingBubbleAlwaysVisible = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
            if (updated.isFloatingBubbleEnabled) {
                com.tirup.app.presentation.overlay.FloatingBubbleService.start(context)
            }
        }
    }

    fun testAlert(tier: com.tirup.app.data.alert.AlertTier) {
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        val vol = _uiState.value.userSettings.alertSettings.alertVolumePercent
        val alerts = _uiState.value.userSettings.alertSettings
        com.tirup.app.data.alert.GlucoseAlertManager.sendTestAlert(
            context,
            tier,
            isRu,
            vol,
            primaryPhone = alerts.emergencyContactPhone,
            primaryName = alerts.emergencyContactName
        )
    }

    private var rescueCountdownJob: kotlinx.coroutines.Job? = null

    fun startPatientRescueTestCountdown(delaySec: Int = 5) {
        rescueCountdownJob?.cancel()
        rescueCountdownJob = viewModelScope.launch {
            val alerts = _uiState.value.userSettings.alertSettings
            kotlinx.coroutines.delay(delaySec * 1000L)
            com.tirup.app.data.alert.GlucoseAlertManager.launchPatientRescueScreen(
                context = context,
                glucoseDisplay = String.format(java.util.Locale.US, "%.1f", alerts.criticalLowThresholdMmol),
                trendArrow = "⇊",
                isTest = true,
                primaryPhone = alerts.emergencyContactPhone,
                primaryName = alerts.emergencyContactName
            )
        }
    }

    fun cancelPatientRescueTest() {
        rescueCountdownJob?.cancel()
        rescueCountdownJob = null
    }

    fun testCaregiverSosScreen() {
        val alerts = _uiState.value.userSettings.alertSettings
        val name = com.tirup.app.domain.alert.EmergencySmsBuilder.extractShortName(
            _uiState.value.userSettings.patientProfile.fullName,
            isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        )
        val testData = com.tirup.app.domain.alert.SosAlertData(
            rawText = "SOS! [ТЕСТ] $name - критич. гипо: 2.8 ммоль (⇊)! Сирена 5м без реакции",
            senderPhone = alerts.emergencyContactPhone.ifBlank { "+79990000000" },
            patientName = name,
            glucoseDisplay = "2.8 ммоль",
            trendArrow = "⇊",
            delayMinutes = 5,
            mapsUrl = "https://maps.google.com/?q=55.755800,37.617300",
            isTest = true
        )
        com.tirup.app.data.alert.CaregiverSosAlarmManager.triggerCaregiverSos(context, testData)
    }

    fun playTestSound(volumePercent: Int) {
        com.tirup.app.data.alert.MedicalSoundPlayer.playTestSound(volumePercent)
    }

    fun showClearConfirm(show: Boolean) {
        _uiState.update { it.copy(showClearDialog = show) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            glucoseRepository.clearAllData()
            val msg = if (isRu) "Все данные трансляции успешно очищены." else "All broadcast data cleared."
            _uiState.update { it.copy(showClearDialog = false, infoMessage = msg) }
        }
    }

    fun setShowTreatmentsOnChart(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(showTreatmentsOnChart = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun setShowPredictionOnChart(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(showPredictionOnChart = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun clearTreatments() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            glucoseRepository.clearTreatments()
            val msg = if (isRu) "Метки болюсов и еды очищены." else "Insulin & meal marks cleared."
            _uiState.update { it.copy(infoMessage = msg) }
        }
    }

    private val manualPdfGenerator = UserManualPdfGenerator(context)

    fun saveUserManualToDownloads() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            manualPdfGenerator.generateUserManualPdf(isRu).onSuccess { pdfFile ->
                val fileName = "TIRUp_User_Manual_${System.currentTimeMillis()}.pdf"
                try {
                    var savedPath = ""
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                java.io.FileInputStream(pdfFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            savedPath = "Downloads/$fileName"
                        }
                    } else {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val destFile = java.io.File(downloadsDir, fileName)
                        java.io.FileInputStream(pdfFile).use { input ->
                            java.io.FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        savedPath = destFile.absolutePath
                    }
                    _events.emit(SettingsEvent.SavedToDownloads(
                        filePath = savedPath,
                        message = if (isRu) "Руководство сохранено в Загрузки" else "Manual saved to Downloads"
                    ))
                } catch (e: Exception) {
                    _events.emit(SettingsEvent.Info("Save failed: ${e.message}"))
                }
            }.onFailure { error ->
                _events.emit(SettingsEvent.Info("Error: ${error.localizedMessage}"))
            }
        }
    }

    fun printOrShareUserManual() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            manualPdfGenerator.generateUserManualPdf(isRu).onSuccess { pdfFile ->
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, if (isRu) "TIRUp • Руководство пользователя" else "TIRUp User Manual")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = android.content.Intent.createChooser(intent, if (isRu) "Инструкция к TIRUp (PDF)" else "TIRUp User Manual").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }.onFailure { error ->
                _uiState.update { it.copy(infoMessage = "Error: ${error.localizedMessage}") }
            }
        }
    }

    fun sendTestEmergencySms() {
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        val alerts = _uiState.value.userSettings.alertSettings
        val patientName = _uiState.value.userSettings.patientProfile.fullName
        val phones = listOf(alerts.emergencyContactPhone.trim(), alerts.secondaryEmergencyContactPhone.trim()).filter { it.isNotBlank() }

        if (phones.isEmpty()) {
            val msg = if (isRu) "Номер телефона не указан" else "Phone number is empty"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        var sentCount = 0
        for (phone in phones) {
            com.tirup.app.data.alert.EmergencySmsManager.sendTestSms(
                context = context,
                phone = phone,
                patientName = patientName,
                isRu = isRu
            ).fold(
                onSuccess = {
                    sentCount++
                },
                onFailure = { error ->
                    val msg = if (isRu) "Ошибка отправки на $phone: ${error.localizedMessage}" else "SMS send error to $phone: ${error.localizedMessage}"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
        if (sentCount > 0) {
            val msg = if (isRu) "Тестовое SMS успешно отправлено ($sentCount ном.)" else "Test SMS successfully sent to $sentCount number(s)"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            _uiState.update { it.copy(infoMessage = msg) }
        }
    }

    fun sendCaregiverSosTestSms() {
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        val alerts = _uiState.value.userSettings.alertSettings
        val patientName = _uiState.value.userSettings.patientProfile.fullName
        val phones = listOf(alerts.emergencyContactPhone.trim(), alerts.secondaryEmergencyContactPhone.trim()).filter { it.isNotBlank() }

        if (phones.isEmpty()) {
            val msg = if (isRu) "Номер телефона не указан" else "Phone number is empty"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        var sentCount = 0
        for (phone in phones) {
            com.tirup.app.data.alert.EmergencySmsManager.sendCaregiverSosTestSms(
                context = context,
                phone = phone,
                patientName = patientName,
                isRu = isRu
            ).fold(
                onSuccess = {
                    sentCount++
                },
                onFailure = { error ->
                    val msg = if (isRu) "Ошибка отправки на $phone: ${error.localizedMessage}" else "SMS send error to $phone: ${error.localizedMessage}"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
        if (sentCount > 0) {
            val msg = if (isRu) "🚨 Тестовое SOS-SMS отправлено опекуну ($sentCount ном.)" else "🚨 Test SOS SMS sent to caregiver ($sentCount number(s))"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            _uiState.update { it.copy(infoMessage = msg) }
        }
    }

    fun load90dMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val start = now - 90L * 24 * 3600 * 1000L
            val readings = glucoseRepository.getReadingsBetween(start, now).firstOrNull() ?: emptyList()
            if (readings.isNotEmpty()) {
                val mean = readings.map { it.valueMmol }.average()
                val gmi = 3.31 + (0.431 * mean)
                val targetLow = _uiState.value.userSettings.targetRanges.tirLowMmol
                val targetHigh = _uiState.value.userSettings.targetRanges.tirHighMmol
                val tirCount = readings.count { it.valueMmol in targetLow..targetHigh }
                val tirPct = (tirCount * 100) / readings.size
                _uiState.update {
                    it.copy(
                        sensorGmi90d = gmi,
                        meanGlucose90dMmol = mean,
                        tirPercent90d = tirPct
                    )
                }
            }
        }
    }

    fun addHba1cRecord(valuePercent: Double, timestamp: Long = System.currentTimeMillis(), labName: String = "", notes: String = "") {
        viewModelScope.launch {
            val current = _uiState.value.userSettings
            val newRec = LabHba1cRecord(
                id = System.currentTimeMillis(),
                timestamp = timestamp,
                valuePercent = valuePercent,
                labName = labName.trim(),
                notes = notes.trim()
            )
            val updatedList = (current.hba1cRecords + newRec).sortedByDescending { it.timestamp }
            val updatedSettings = current.copy(
                hba1cRecords = updatedList,
                hba1cRemindersCountInCycle = 0,
                lastHba1cReminderTimestamp = 0L
            )
            settingsRepository.updateSettings(updatedSettings)
            _uiState.update { it.copy(userSettings = updatedSettings) }
            val isRu = current.language.equals("RU", ignoreCase = true)
            val msg = if (isRu) "Анализ HbA1c ${valuePercent}% сохранён" else "HbA1c test ${valuePercent}% saved"
            _events.emit(SettingsEvent.Info(msg))
            load90dMetrics()
        }
    }

    fun deleteHba1cRecord(recordId: Long) {
        viewModelScope.launch {
            val current = _uiState.value.userSettings
            val updatedList = current.hba1cRecords.filterNot { it.id == recordId }
            val updatedSettings = current.copy(hba1cRecords = updatedList)
            settingsRepository.updateSettings(updatedSettings)
            _uiState.update { it.copy(userSettings = updatedSettings) }
            val isRu = current.language.equals("RU", ignoreCase = true)
            _events.emit(SettingsEvent.Info(if (isRu) "Запись анализа удалена" else "Test record deleted"))
        }
    }

    fun setHba1cReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.userSettings
            val updated = current.copy(isHba1cReminderEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun skipHba1cQuarter() {
        viewModelScope.launch {
            settingsRepository.skipHba1cQuarter()
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val current = _uiState.value.userSettings
            val updated = current.copy(
                hba1cSkippedQuarterTimestamp = System.currentTimeMillis(),
                hba1cRemindersCountInCycle = 0
            )
            _uiState.update { it.copy(userSettings = updated) }
            _events.emit(SettingsEvent.Info(if (isRu) "Квартальный контроль отложен на 90 дней" else "Quarterly checkup postponed for 90 days"))
        }
    }

    fun toggleHba1cDialog(show: Boolean) {
        _uiState.update { it.copy(showHba1cDialog = show) }
        if (show) {
            load90dMetrics()
        }
    }

    private val hba1cPdfGenerator = Hba1cReportPdfGenerator(context)

    fun exportHba1cReportToPdf(onSuccess: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val settings = _uiState.value.userSettings
            hba1cPdfGenerator.generateHba1cReportPdf(
                profile = settings.patientProfile,
                records = settings.hba1cRecords,
                sensorGmi = _uiState.value.sensorGmi90d,
                meanGlucoseMmol = _uiState.value.meanGlucose90dMmol,
                tirPercent = _uiState.value.tirPercent90d,
                isRu = isRu
            ).onSuccess { pdfFile ->
                val fileName = "TIRUp_HbA1c_Report_${System.currentTimeMillis()}.pdf"
                try {
                    var savedPath = ""
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                java.io.FileInputStream(pdfFile).use { input -> input.copyTo(out) }
                            }
                            savedPath = "Downloads/$fileName"
                        }
                    } else {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val destFile = java.io.File(downloadsDir, fileName)
                        java.io.FileInputStream(pdfFile).use { input ->
                            java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        savedPath = destFile.absolutePath
                    }
                    _events.emit(SettingsEvent.SavedToDownloads(
                        filePath = savedPath,
                        message = if (isRu) "Выписка HbA1c сохранена в Загрузки" else "HbA1c summary saved to Downloads"
                    ))
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke(savedPath)
                    }
                } catch (e: Exception) {
                    _events.emit(SettingsEvent.Info("Save failed: ${e.message}"))
                }
            }.onFailure { error ->
                _events.emit(SettingsEvent.Info("Error: ${error.localizedMessage}"))
            }
        }
    }

    private val yearEndPdfGenerator = YearEndReportPdfGenerator(context)

    fun setShowYearEndDialog(show: Boolean, year: Int? = null) {
        _uiState.update { it.copy(showYearEndDialog = show) }
        if (show) {
            val targetYear = year ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            loadYearEndStats(targetYear)
        }
    }

    fun loadYearEndStats(year: Int) {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                calculateYearEndStats(year)
            }
            _uiState.update { it.copy(yearEndStats = stats) }
        }
    }

    private suspend fun calculateYearEndStats(year: Int): YearEndStats {
        val startOfYear = java.util.Calendar.getInstance().apply {
            set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfYear = java.util.Calendar.getInstance().apply {
            set(year, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        val readings = database.glucoseReadingDao().getReadingsBetweenSync(startOfYear, endOfYear)
        val isArchived = AutoBackupManager.getArchivedYears(context).contains(year)

        if (readings.isEmpty()) {
            return YearEndStats(
                year = year,
                totalReadings = 0,
                monitoringDays = 0,
                tirPercent = 0.0,
                tarPercent = 0.0,
                tbrPercent = 0.0,
                meanGlucoseMmol = 0.0,
                gmiPercent = 0.0,
                bestMonthName = "",
                bestMonthTir = 0.0,
                bestStreakDays = _uiState.value.userSettings.bestStreakDays,
                isArchived = isArchived
            )
        }

        val totalCount = readings.size
        val inRangeCount = readings.count { it.valueMmol in 3.9..10.0 }
        val hypoCount = readings.count { it.valueMmol < 3.9 }
        val hyperCount = readings.count { it.valueMmol > 10.0 }

        val tir = (inRangeCount.toDouble() / totalCount) * 100.0
        val tbr = (hypoCount.toDouble() / totalCount) * 100.0
        val tar = (hyperCount.toDouble() / totalCount) * 100.0
        val mean = readings.map { it.valueMmol }.average()
        val gmi = 3.31 + 0.431 * mean

        val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val activeDays = readings.map { dayFormat.format(java.util.Date(it.timestamp)) }.distinct().size

        // Calculate best month
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        val monthCal = java.util.Calendar.getInstance()
        val monthGroups = readings.groupBy {
            monthCal.timeInMillis = it.timestamp
            monthCal.get(java.util.Calendar.MONTH)
        }
        var bestMonthName = ""
        var bestMonthTir = 0.0
        for ((m, list) in monthGroups) {
            if (list.size >= 50) {
                val mTir = (list.count { it.valueMmol in 3.9..10.0 }.toDouble() / list.size) * 100.0
                if (mTir > bestMonthTir) {
                    bestMonthTir = mTir
                    bestMonthName = when (m) {
                        0 -> if (isRu) "Январь" else "January"
                        1 -> if (isRu) "Февраль" else "February"
                        2 -> if (isRu) "Март" else "March"
                        3 -> if (isRu) "Апрель" else "April"
                        4 -> if (isRu) "Май" else "May"
                        5 -> if (isRu) "Июнь" else "June"
                        6 -> if (isRu) "Июль" else "July"
                        7 -> if (isRu) "Август" else "August"
                        8 -> if (isRu) "Сентябрь" else "September"
                        9 -> if (isRu) "Октябрь" else "October"
                        10 -> if (isRu) "Ноябрь" else "November"
                        else -> if (isRu) "Декабрь" else "December"
                    }
                }
            }
        }

        return YearEndStats(
            year = year,
            totalReadings = totalCount,
            monitoringDays = activeDays,
            tirPercent = tir,
            tarPercent = tar,
            tbrPercent = tbr,
            meanGlucoseMmol = mean,
            gmiPercent = gmi,
            bestMonthName = bestMonthName,
            bestMonthTir = bestMonthTir,
            bestStreakDays = _uiState.value.userSettings.bestStreakDays,
            isArchived = isArchived
        )
    }

    fun setYearEndDigestYear(year: Int) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val validYear = year.coerceIn(2025, currentYear)
        loadYearEndStats(validYear)
    }

    fun archiveYearArchive(year: Int) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val success = AutoBackupManager.archiveYear(year, context, database)
            if (success) {
                loadBackupSummary()
                loadYearEndStats(year)
                val archiveFile = File(AutoBackupManager.getBackupDirectory(context), "tirup_readings_$year.csv")
                _events.emit(SettingsEvent.SavedToDownloads(
                    filePath = archiveFile.absolutePath,
                    message = if (isRu) "Архив $year года сохранён в TIRUp/Backups" else "Annual archive for $year saved"
                ))
            } else {
                _events.emit(SettingsEvent.Info(if (isRu) "Нет данных или ошибка создания архива" else "No data or archiving failed"))
            }
        }
    }

    fun exportYearEndReportToPdf(stats: YearEndStats) {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            val settings = _uiState.value.userSettings
            yearEndPdfGenerator.generateYearEndReportPdf(
                profile = settings.patientProfile,
                stats = stats,
                isRu = isRu
            ).onSuccess { pdfFile ->
                val fileName = "TIRUp_Year_End_${stats.year}_${System.currentTimeMillis()}.pdf"
                try {
                    var savedPath = ""
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                java.io.FileInputStream(pdfFile).use { input -> input.copyTo(out) }
                            }
                            savedPath = "Downloads/$fileName"
                        }
                    } else {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val destFile = java.io.File(downloadsDir, fileName)
                        java.io.FileInputStream(pdfFile).use { input ->
                            java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        savedPath = destFile.absolutePath
                    }
                    _events.emit(SettingsEvent.SavedToDownloads(
                        filePath = savedPath,
                        message = if (isRu) "Итоги года сохранены в Загрузки" else "Year-end report saved to Downloads"
                    ))
                } catch (e: Exception) {
                    _events.emit(SettingsEvent.Info("Save failed: ${e.message}"))
                }
            }.onFailure { error ->
                _events.emit(SettingsEvent.Info("Error: ${error.localizedMessage}"))
            }
        }
    }
}
