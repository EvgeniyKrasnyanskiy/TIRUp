package com.tirup.app.presentation.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tirup.app.data.ai.AiImageCompressor
import com.tirup.app.data.ai.AiPreferencesManager
import com.tirup.app.data.ai.GeminiClient
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.local.entity.TreatmentEntity
import com.tirup.app.data.network.NightscoutUploadManager
import com.tirup.app.domain.ai.AiContextPeriod
import com.tirup.app.domain.ai.AiGlycemicContextBuilder
import com.tirup.app.domain.model.AiChatMessage
import com.tirup.app.domain.model.AiDailyQuotaUsage
import com.tirup.app.domain.model.AiFoodEstimation
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AiAssistantUiState(
    val hasApiKey: Boolean = false,
    val draftApiKey: String = "",
    val modelStatuses: Map<String, Int> = emptyMap(),
    val isTestingModels: Boolean = false,
    val testStatusMessage: String? = null,
    val isTestSuccessful: Boolean = false,
    val selectedPeriod: AiContextPeriod = AiContextPeriod.TODAY,
    val customDayTimestamp: Long? = null,
    val messages: List<AiChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val inputPrompt: String = "",
    val attachedPhotoUri: Uri? = null,
    val attachedPhotoBase64: String? = null,
    val activeModelBadge: String? = null,
    val dailyQuotaUsage: AiDailyQuotaUsage = AiDailyQuotaUsage(),
    val showKeyOnboarding: Boolean = false,
    val showDatePicker: Boolean = false,
    val snackbarMessage: String? = null
)

class AiAssistantViewModel(
    application: Application,
    private val glucoseRepository: GlucoseRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val aiPrefs = AiPreferencesManager(application)
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    fun loadInitialState() {
        val apiKey = aiPrefs.getApiKey()
        val hasKey = apiKey.isNotBlank()
        val cachedMessages = aiPrefs.loadChatMessages()
        val statuses = aiPrefs.getModelStatuses()
        val lastModel = aiPrefs.getLastSuccessfulModel()
        val quotaUsage = aiPrefs.getDailyQuotaUsage()

        _uiState.update {
            it.copy(
                hasApiKey = hasKey,
                draftApiKey = apiKey,
                modelStatuses = statuses,
                activeModelBadge = lastModel,
                messages = cachedMessages,
                dailyQuotaUsage = quotaUsage,
                showKeyOnboarding = !hasKey
            )
        }
    }

    fun openKeySettings() {
        _uiState.update {
            it.copy(
                showKeyOnboarding = true,
                draftApiKey = aiPrefs.getApiKey(),
                testStatusMessage = null
            )
        }
    }

    fun closeKeySettings() {
        _uiState.update { it.copy(showKeyOnboarding = false) }
    }

    fun updateDraftApiKey(key: String) {
        _uiState.update { it.copy(draftApiKey = key, testStatusMessage = null) }
    }

    fun testAndSaveKey(keyToTest: String) {
        val trimmed = keyToTest.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    testStatusMessage = "Введите API-ключ",
                    isTestSuccessful = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingModels = true, testStatusMessage = null) }
            val results = GeminiClient.testModels(trimmed)
            val anyOk = results.values.any { it == 200 }

            aiPrefs.saveModelStatuses(results)

            if (anyOk) {
                aiPrefs.saveApiKey(trimmed)
                val workingModel = results.entries.firstOrNull { it.value == 200 }?.key
                if (workingModel != null) {
                    aiPrefs.saveLastSuccessfulModel(workingModel)
                }

                _uiState.update {
                    it.copy(
                        isTestingModels = false,
                        hasApiKey = true,
                        draftApiKey = trimmed,
                        modelStatuses = results,
                        activeModelBadge = workingModel ?: it.activeModelBadge,
                        isTestSuccessful = true,
                        testStatusMessage = "Ключ успешно проверен и сохранён!"
                    )
                }
            } else {
                val hasLocationBlock = results.values.any { it == 400 }
                val msg = if (hasLocationBlock) {
                    "Google AI Studio недоступен в регионе без VPN/прокси (HTTP 400)"
                } else {
                    "Ни одна модель не ответила 200 OK. Проверьте правильность ключа."
                }
                _uiState.update {
                    it.copy(
                        isTestingModels = false,
                        modelStatuses = results,
                        isTestSuccessful = false,
                        testStatusMessage = msg
                    )
                }
            }
        }
    }

    fun selectPeriod(period: AiContextPeriod, customDayTimestamp: Long? = null) {
        _uiState.update {
            it.copy(
                selectedPeriod = period,
                customDayTimestamp = customDayTimestamp,
                showDatePicker = false
            )
        }
    }

    fun toggleDatePicker(show: Boolean) {
        _uiState.update { it.copy(showDatePicker = show) }
    }

    fun updateInputPrompt(text: String) {
        _uiState.update { it.copy(inputPrompt = text) }
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val compressed = AiImageCompressor.compressFromUri(getApplication(), uri)
            withContext(Dispatchers.Main) {
                if (compressed != null) {
                    _uiState.update {
                        it.copy(
                            attachedPhotoUri = uri,
                            attachedPhotoBase64 = compressed.base64
                        )
                    }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Не удалось загрузить или сжать фото") }
                }
            }
        }
    }

    fun removeAttachedPhoto() {
        _uiState.update {
            it.copy(attachedPhotoUri = null, attachedPhotoBase64 = null)
        }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        val text = currentState.inputPrompt.trim()
        val photoBase64 = currentState.attachedPhotoBase64
        val photoUri = currentState.attachedPhotoUri

        if (text.isEmpty() && photoBase64 == null) return
        if (currentState.isGenerating) return

        val apiKey = aiPrefs.getApiKey()
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(showKeyOnboarding = true) }
            return
        }

        val userMessage = AiChatMessage(
            sender = "user",
            text = text.ifEmpty { "Анализ блюда по фото" },
            photoUri = photoUri?.toString()
        )

        val updatedMessages = currentState.messages + userMessage
        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputPrompt = "",
                attachedPhotoUri = null,
                attachedPhotoBase64 = null,
                isGenerating = true,
                snackbarMessage = null
            )
        }

        viewModelScope.launch {
            val userSettings = settingsRepository.getSettings().first()
            val contextString = withContext(Dispatchers.IO) {
                val bounds = AiGlycemicContextBuilder.getTimeBounds(currentState.selectedPeriod, currentState.customDayTimestamp)
                if (bounds != null) {
                    val readings = glucoseRepository.getReadingsBetween(bounds.first, bounds.second).first()
                    val treatments = db.treatmentDao().getTreatmentsBetweenSync(bounds.first, bounds.second).map { it.toDomain() }
                    AiGlycemicContextBuilder.buildContext(
                        period = currentState.selectedPeriod,
                        readings = readings,
                        treatments = treatments,
                        userSettings = userSettings
                    )
                } else ""
            }

            val systemPrompt = AiGlycemicContextBuilder.buildSystemPrompt(userSettings, contextString)
            val result = GeminiClient.generateContent(
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = text,
                imageBase64 = photoBase64,
                preferredModel = currentState.activeModelBadge
            )

            result.fold(
                onSuccess = { response ->
                    val assistantMsg = AiChatMessage(
                        sender = "model",
                        text = response.text,
                        modelBadge = response.modelUsed,
                        foodEstimation = response.foodEstimation
                    )
                    val newHistory = updatedMessages + assistantMsg
                    val updatedQuota = aiPrefs.recordRequestUsage(response.totalTokens)
                    aiPrefs.saveChatMessages(newHistory)
                    aiPrefs.saveLastSuccessfulModel(response.modelUsed)

                    _uiState.update {
                        it.copy(
                            messages = newHistory,
                            isGenerating = false,
                            activeModelBadge = response.modelUsed,
                            dailyQuotaUsage = updatedQuota
                        )
                    }
                },
                onFailure = { error ->
                    val errorMsg = AiChatMessage(
                        sender = "model",
                        text = "⚠️ Ошибка при обращении к ИИ: ${error.message ?: "Не удалось получить ответ"}"
                    )
                    val newHistory = updatedMessages + errorMsg
                    aiPrefs.saveChatMessages(newHistory)
                    val is429 = error is GeminiClient.QuotaExceededException || (error.message?.contains("429") == true)
                    val updatedQuota = if (is429) aiPrefs.markQuotaExhausted(true) else aiPrefs.getDailyQuotaUsage()

                    _uiState.update {
                        it.copy(
                            messages = newHistory,
                            isGenerating = false,
                            snackbarMessage = error.message,
                            dailyQuotaUsage = updatedQuota
                        )
                    }
                }
            )
        }
    }

    fun addTreatmentToChart(messageId: String, carbsGrams: Double, foodTitle: String, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val cleanTitle = foodTitle.ifBlank { "Приём пищи (ИИ)" }
            val noteText = if (comment.isNotBlank()) "🍽️ $cleanTitle ($comment)" else "🍽️ $cleanTitle"
            val itemUuid = UUID.randomUUID().toString()

            val entity = TreatmentEntity(
                timestamp = timestamp,
                carbsGrams = carbsGrams,
                notes = noteText,
                source = "AI",
                uuid = itemUuid
            )
            db.treatmentDao().insert(entity)

            val settings = settingsRepository.getSettings().first()
            if (settings.nightscoutSettings.isEnabled) {
                NightscoutUploadManager.uploadTreatment(
                    settings = settings.nightscoutSettings,
                    carbs = carbsGrams,
                    notes = noteText,
                    timestamp = timestamp
                )
            }

            withContext(Dispatchers.Main) {
                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == messageId && msg.foodEstimation != null) {
                        msg.copy(foodEstimation = msg.foodEstimation.copy(isAddedToChart = true))
                    } else msg
                }
                aiPrefs.saveChatMessages(updatedMessages)
                _uiState.update {
                    it.copy(
                        messages = updatedMessages,
                        snackbarMessage = "Запись добавлена на график: ${String.format("%.0f", carbsGrams)}г углев"
                    )
                }
            }
        }
    }

    fun dismissFoodCard(messageId: String) {
        val updatedMessages = _uiState.value.messages.map { msg ->
            if (msg.id == messageId && msg.foodEstimation != null) {
                msg.copy(foodEstimation = msg.foodEstimation.copy(isDismissed = true))
            } else msg
        }
        aiPrefs.saveChatMessages(updatedMessages)
        _uiState.update { it.copy(messages = updatedMessages) }
    }

    fun clearChatHistory() {
        aiPrefs.clearChatHistory()
        _uiState.update { it.copy(messages = emptyList(), snackbarMessage = "История чата очищена") }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

