package com.tirup.app.domain.alert

import java.util.regex.Pattern

data class SosAlertData(
    val rawText: String,
    val senderPhone: String,
    val patientName: String,
    val glucoseDisplay: String,
    val trendArrow: String,
    val delayMinutes: Int,
    val mapsUrl: String?,
    val isTest: Boolean
)

object SosSmsParser {

    private val MAPS_URL_PATTERN = Pattern.compile("https?://(?:maps\\.google\\.com|goo\\.gl|maps\\.app\\.goo\\.gl)[^\\s]*", Pattern.CASE_INSENSITIVE)

    /**
     * Checks if the incoming message is a TIRUp emergency SOS alert.
     */
    fun isSosMessage(body: String): Boolean {
        val trimmed = body.trim()
        if (!trimmed.startsWith("SOS!", ignoreCase = true)) return false
        val lower = trimmed.lowercase()
        return lower.contains("гипо") || lower.contains("hypo") || lower.contains("сирена") || lower.contains("alarm")
    }

    /**
     * Robustly parses SOS SMS body into structured SosAlertData.
     */
    fun parse(body: String, senderPhone: String): SosAlertData? {
        if (!isSosMessage(body)) return null

        val trimmed = body.trim()
        val isTest = trimmed.contains("ТЕСТ", ignoreCase = true) || trimmed.contains("TEST", ignoreCase = true)

        // 1. Extract Maps URL if present
        var mapsUrl: String? = null
        val urlMatcher = MAPS_URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            mapsUrl = urlMatcher.group()
        }

        // 2. Extract Patient Name: between "SOS!" (and optional [ТЕСТ]) and the first "-"
        // e.g. "SOS! [ТЕСТ] Ваня - критич. гипо..." or "SOS! Ваня - критич. гипо..."
        var patientName = ""
        val namePattern = Pattern.compile("SOS!\\s*(?:\\[[^\\]]+\\]\\s*)?([^-:!]+?)\\s*[-:]", Pattern.CASE_INSENSITIVE)
        val nameMatcher = namePattern.matcher(trimmed)
        if (nameMatcher.find()) {
            patientName = nameMatcher.group(1)?.trim() ?: ""
        }
        if (patientName.isBlank()) {
            patientName = "Пациент"
        }

        // 3. Extract Glucose and Trend Arrow
        // e.g. "критич. гипо: 2.6 ммоль (↓)!" or "critical hypo: 47 mg/dL (↓)!"
        var glucoseDisplay = ""
        var trendArrow = ""
        val glucosePattern = Pattern.compile("(?:критич\\.?\\s*гипо|critical\\s*hypo):\\s*([0-9.,]+\\s*(?:ммоль(?:/л)?|mmol(?:/l)?|mg/dL)?)", Pattern.CASE_INSENSITIVE)
        val glucoseMatcher = glucosePattern.matcher(trimmed)
        if (glucoseMatcher.find()) {
            glucoseDisplay = glucoseMatcher.group(1)?.trim() ?: ""
        }

        val arrowPattern = Pattern.compile("\\(([↑↗→↘↓⇈⇊?]+)\\)")
        val arrowMatcher = arrowPattern.matcher(trimmed)
        if (arrowMatcher.find()) {
            trendArrow = arrowMatcher.group(1)?.trim() ?: ""
        }

        // 4. Extract Delay Minutes
        // e.g. "Сирена 5м без реакции" or "Alarm 5m no reaction"
        var delayMinutes = 5
        val delayPattern = Pattern.compile("(?:Сирена|Alarm)\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        val delayMatcher = delayPattern.matcher(trimmed)
        if (delayMatcher.find()) {
            delayMinutes = delayMatcher.group(1)?.toIntOrNull() ?: 5
        }

        return SosAlertData(
            rawText = trimmed,
            senderPhone = senderPhone,
            patientName = patientName,
            glucoseDisplay = if (glucoseDisplay.isNotBlank()) glucoseDisplay else "Критич. гипо",
            trendArrow = trendArrow,
            delayMinutes = delayMinutes,
            mapsUrl = mapsUrl,
            isTest = isTest
        )
    }
}
