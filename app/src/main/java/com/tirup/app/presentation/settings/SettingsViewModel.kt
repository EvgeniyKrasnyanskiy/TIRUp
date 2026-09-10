package com.tirup.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.backup.AutoBackupManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val showClearDialog: Boolean = false,
    val infoMessage: String? = null
)

class SettingsViewModel(
    private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val glucoseRepository: GlucoseRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val latestReading: StateFlow<GlucoseReading?> = glucoseRepository.getLatestReading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(userSettings = settings) }
            }
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(language = language)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
        }
    }

    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch {
            val updated = _uiState.value.userSettings.copy(unit = unit)
            settingsRepository.updateSettings(updated)
            _uiState.update { it.copy(userSettings = updated) }
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
                val todayEntities = database.glucoseReadingDao().getReadingsBetweenSync(
                    calendar.timeInMillis,
                    System.currentTimeMillis() + 60_000L
                )
                val todayDomain = todayEntities.map { it.toDomain() }
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

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun boostBleObserverScan() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            com.tirup.app.data.ble.BleObserverManager.boostScanFor30Sec(context, settingsRepository, glucoseRepository)
            val msg = if (isRu) "🔍 Активный поиск мастера запущен (30 сек)" else "🔍 Boost scan active (30s)"
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

    fun testAlert(tier: com.tirup.app.data.alert.AlertTier) {
        val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
        com.tirup.app.data.alert.GlucoseAlertManager.sendTestAlert(context, tier, isRu)
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

    fun clearTreatments() {
        viewModelScope.launch {
            val isRu = _uiState.value.userSettings.language.equals("RU", ignoreCase = true)
            glucoseRepository.clearTreatments()
            val msg = if (isRu) "Метки болюсов и еды очищены." else "Insulin & meal marks cleared."
            _uiState.update { it.copy(infoMessage = msg) }
        }
    }

    private val manualPdfGenerator = UserManualPdfGenerator(context)

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
}
