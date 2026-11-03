package com.tirup.app.domain.model

import java.util.UUID

data class AiDailyQuotaUsage(
    val requestsUsed: Int = 0,
    val maxRequests: Int = 1500,
    val tokensUsed: Int = 0,
    val isQuotaExhausted: Boolean = false
) {
    val remainingRequests: Int get() = (maxRequests - requestsUsed).coerceAtLeast(0)
    val progressFraction: Float get() = if (isQuotaExhausted) 1.0f else if (maxRequests > 0) (requestsUsed.toFloat() / maxRequests).coerceIn(0f, 1f) else 0f
}

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

