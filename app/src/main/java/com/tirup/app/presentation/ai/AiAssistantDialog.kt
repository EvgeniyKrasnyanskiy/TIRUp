package com.tirup.app.presentation.ai

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.tirup.app.domain.model.AiDailyQuotaUsage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tirup.app.domain.ai.AiContextPeriod
import com.tirup.app.domain.model.AiChatMessage
import com.tirup.app.domain.model.AiFoodEstimation
import com.tirup.app.presentation.components.BentoCard
import com.tirup.app.presentation.theme.ActionBlue
import com.tirup.app.presentation.theme.ColorHigh
import com.tirup.app.presentation.theme.ColorLow
import com.tirup.app.presentation.theme.PrimaryEmerald
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AiAssistantDialog(
    viewModel: AiAssistantViewModel,
    isRu: Boolean,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoSourceMenu by rememberSaveable { mutableStateOf(false) }

    // Camera Uri holder
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            viewModel.attachPhoto(cameraPhotoUri!!)
        }
    }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.attachPhoto(uri)
        }
    }

    // DatePicker for CUSTOM_DAY
    if (state.showDatePicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth, 12, 0, 0)
                viewModel.selectPeriod(AiContextPeriod.CUSTOM_DAY, cal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { viewModel.toggleDatePicker(false) }
            show()
        }
    }

    // Snackbars
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Auto-scroll on new message
    LaunchedEffect(state.messages.size, state.isGenerating) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ActionBlue.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ActionBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isRu) "ИИ-ассистент TIRUp" else "TIRUp AI Assistant",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                state.activeModelBadge?.let { model ->
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ActionBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.messages.isNotEmpty()) {
                                IconButton(onClick = { showClearConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.openKeySettings() }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = ActionBlue
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Bottom input bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Attached photo preview chip
                        if (state.attachedPhotoUri != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ActionBlue.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "📷", fontSize = 14.sp)
                                    Text(
                                        text = if (isRu) "Фото прикреплено" else "Photo attached",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ActionBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeAttachedPhoto() },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = ActionBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera / Gallery attach button
                            Box {
                                IconButton(
                                    onClick = { showPhotoSourceMenu = true },
                                    enabled = !state.isGenerating,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(ActionBlue.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Attach photo",
                                        tint = ActionBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showPhotoSourceMenu,
                                    onDismissRequest = { showPhotoSourceMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isRu) "Сделать фото (Камера)" else "Take photo (Camera)") },
                                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                                        onClick = {
                                            showPhotoSourceMenu = false
                                            val dir = File(context.cacheDir, "ai_photos").apply { mkdirs() }
                                            val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            cameraPhotoUri = uri
                                            cameraLauncher.launch(uri)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isRu) "Выбрать из галереи" else "Choose from gallery") },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                        onClick = {
                                            showPhotoSourceMenu = false
                                            galleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                }
                            }

                            // Text prompt input
                            OutlinedTextField(
                                value = state.inputPrompt,
                                onValueChange = { viewModel.updateInputPrompt(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = if (isRu) "Спросите о сахаре или еде..." else "Ask about glucose or food...",
                                        fontSize = 14.sp
                                    )
                                },
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    keyboardController?.hide()
                                    viewModel.sendMessage()
                                }),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ActionBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            // Send button
                            val canSend = (state.inputPrompt.isNotBlank() || state.attachedPhotoBase64 != null) && !state.isGenerating
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.sendMessage()
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (canSend) ActionBlue else ActionBlue.copy(alpha = 0.2f))
                            ) {
                                if (state.isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (canSend) Color.White else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Period selection strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRu) "Контекст:" else "Context:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    for (period in AiContextPeriod.values()) {
                        val isSelected = state.selectedPeriod == period
                        val label = if (period == AiContextPeriod.CUSTOM_DAY && state.customDayTimestamp != null) {
                            SimpleDateFormat("dd.MM", Locale.US).format(Date(state.customDayTimestamp!!))
                        } else {
                            period.getDisplayName(isRu)
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (period == AiContextPeriod.CUSTOM_DAY) {
                                    viewModel.toggleDatePicker(true)
                                } else {
                                    viewModel.selectPeriod(period)
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (period == AiContextPeriod.CUSTOM_DAY) {
                                { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ActionBlue.copy(alpha = 0.2f),
                                selectedLabelColor = ActionBlue
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                // Daily Quota & Usage Progress Bar
                DailyQuotaProgressBar(
                    quota = state.dailyQuotaUsage,
                    isRu = isRu
                )

                // Chat Messages LazyColumn
                if (state.messages.isEmpty()) {
                    // Empty state with quick suggestions
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "🤖✨", fontSize = 42.sp)
                            Text(
                                text = if (isRu) "Чем я могу вам помочь?" else "How can I help you today?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isRu)
                                    "Задайте вопрос по трендам сахара, прикрепите фото еды для расчета БЖУ или выберите подсказку ниже:"
                                else
                                    "Ask about glucose trends, snap a food photo for carbs estimation, or tap a suggestion below:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val suggestions = if (isRu) listOf(
                                "📸 Рассчитай БЖУ и ХЕ по фото блюда",
                                "📊 Как прошёл мой день по сахарам и TIR?",
                                "🌙 Были ли скрытые ночные гипогликемии?",
                                "⏱️ Проанализируй компенсацию после обеда"
                            ) else listOf(
                                "📸 Estimate carbs & protein from meal photo",
                                "📊 How was my daily glucose and TIR?",
                                "🌙 Any nocturnal hypos in the selected period?",
                                "⏱️ Analyze post-meal glycemic response"
                            )

                            for (sug in suggestions) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ActionBlue.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateInputPrompt(sug.substringAfter(" "))
                                        }
                                ) {
                                    Text(
                                        text = sug,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ActionBlue,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            AiMessageBubble(
                                message = message,
                                isRu = isRu,
                                onAddTreatment = { id, carbs, title, comment ->
                                    viewModel.addTreatmentToChart(id, carbs, title, comment)
                                },
                                onDismissFood = { id ->
                                    viewModel.dismissFoodCard(id)
                                }
                            )
                        }

                        if (state.isGenerating) {
                            item {
                                Row(
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ActionBlue
                                    )
                                    Text(
                                        text = if (isRu) "ИИ анализирует данные..." else "AI is thinking...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Key onboarding sheet
    if (state.showKeyOnboarding) {
        AiKeyOnboardingSheet(
            state = state,
            isRu = isRu,
            onKeyChange = { viewModel.updateDraftApiKey(it) },
            onTestAndSaveKey = { viewModel.testAndSaveKey(it) },
            onDismiss = { viewModel.closeKeySettings() }
        )
    }

    // Clear history confirm dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(if (isRu) "Очистить историю чата?" else "Clear chat history?") },
            text = {
                Text(
                    if (isRu) "Все локальные сообщения будут удалены из памяти приложения. Данные в графике сахара не пострадают."
                    else "All chat messages will be cleared. Your glucose logs and treatments will remain untouched."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearChatHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isRu) "Очистить" else "Clear", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text(if (isRu) "Отмена" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun AiMessageBubble(
    message: AiChatMessage,
    isRu: Boolean,
    onAddTreatment: (String, Double, String, String) -> Unit,
    onDismissFood: (String) -> Unit
) {
    val isUser = message.isFromUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bgShape,
            color = if (isUser) ActionBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(
                0.8.dp,
                if (isUser) ActionBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header badge for model messages
                if (!isUser && message.modelBadge != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ActionBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "✨ ${message.modelBadge}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Message Text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                // Timestamp
                val timeStr = remember(message.timestamp) {
                    SimpleDateFormat("HH:mm", Locale.US).format(Date(message.timestamp))
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End),
                    fontSize = 10.sp
                )
            }
        }

        // Interactive Food Estimation Card
        if (!isUser && message.foodEstimation != null && !message.foodEstimation.isDismissed) {
            Spacer(modifier = Modifier.height(4.dp))
            AiFoodEstimationCard(
                messageId = message.id,
                estimation = message.foodEstimation,
                isRu = isRu,
                onAddTreatment = onAddTreatment,
                onDismiss = onDismissFood
            )
        }
    }
}

@Composable
fun AiFoodEstimationCard(
    messageId: String,
    estimation: AiFoodEstimation,
    isRu: Boolean,
    onAddTreatment: (String, Double, String, String) -> Unit,
    onDismiss: (String) -> Unit
) {
    var carbsText by rememberSaveable(estimation.carbsGrams) {
        mutableStateOf(String.format(Locale.US, "%.0f", estimation.carbsGrams))
    }

    BentoCard(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(top = 2.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🍽️", fontSize = 18.sp)
                    Text(
                        text = estimation.title.ifBlank { if (isRu) "Расчёт БЖУ" else "Nutrition estimate" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald
                    )
                }

                if (!estimation.isAddedToChart) {
                    IconButton(
                        onClick = { onDismiss(messageId) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Macros Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MacroBadge(label = if (isRu) "Углеводы" else "Carbs", value = "${String.format(Locale.US, "%.0f", estimation.carbsGrams)} г", color = ColorHigh)
                MacroBadge(label = if (isRu) "Белки" else "Protein", value = "${String.format(Locale.US, "%.0f", estimation.proteinGrams)} г", color = ActionBlue)
                MacroBadge(label = if (isRu) "Жиры" else "Fat", value = "${String.format(Locale.US, "%.0f", estimation.fatGrams)} г", color = ColorLow)
                estimation.breadUnits?.let { bu ->
                    MacroBadge(label = if (isRu) "ХЕ" else "BU", value = String.format(Locale.US, "%.1f", bu), color = PrimaryEmerald)
                }
            }

            if (estimation.comment.isNotBlank()) {
                Text(
                    text = "💡 ${estimation.comment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action section
            if (estimation.isAddedToChart) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "Добавлено на график гликемии" else "Added to glucose chart",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryEmerald
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick edit carbs field
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text(if (isRu) "Углев (г)" else "Carbs (g)") },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val parsedCarbs = carbsText.toDoubleOrNull() ?: estimation.carbsGrams
                            val comment = buildString {
                                append("Б: ${String.format(Locale.US, "%.0f", estimation.proteinGrams)}г, Ж: ${String.format(Locale.US, "%.0f", estimation.fatGrams)}г")
                                estimation.breadUnits?.let { append(", $it ХЕ") }
                            }
                            onAddTreatment(messageId, parsedCarbs, estimation.title, comment)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRu) "На график" else "Add to chart",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MacroBadge(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}


@Composable
private fun DailyQuotaProgressBar(
    quota: AiDailyQuotaUsage,
    isRu: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = quota.progressFraction
    val barColor = when {
        quota.isQuotaExhausted -> MaterialTheme.colorScheme.error
        progress > 0.85f -> ColorLow
        progress > 0.60f -> ColorHigh
        else -> PrimaryEmerald
    }

    val statusText = if (quota.isQuotaExhausted) {
        if (isRu) "⚠️ Суточный лимит исчерпан (сброс в 00:00)" else "⚠️ Daily quota exhausted (resets at 00:00)"
    } else {
        val tokensFormatted = if (quota.tokensUsed >= 1000) {
            String.format(Locale.US, "%.1fk", quota.tokensUsed / 1000.0)
        } else {
            "${quota.tokensUsed}"
        }
        if (isRu) {
            "⚡ Лимит сегодня: ${quota.remainingRequests} / ${quota.maxRequests} запр. ($tokensFormatted токенов)"
        } else {
            "⚡ Daily quota: ${quota.remainingRequests} / ${quota.maxRequests} reqs ($tokensFormatted tokens)"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (quota.isQuotaExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            if (!quota.isQuotaExhausted) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
