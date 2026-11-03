package com.tirup.app.domain.model

import java.util.UUID

data class AiFoodEstimation(
    val title: String = "",
    val carbsGrams: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val breadUnits: Double? = null,
    val comment: String = "",
    val isAddedToChart: Boolean = false,
    val isDismissed: Boolean = false
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "model"
    val text: String,
    val modelBadge: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val foodEstimation: AiFoodEstimation? = null,
    val photoUri: String? = null
) {
    val isFromUser: Boolean get() = sender.equals("user", ignoreCase = true)
    val isFromModel: Boolean get() = sender.equals("model", ignoreCase = true)
}

