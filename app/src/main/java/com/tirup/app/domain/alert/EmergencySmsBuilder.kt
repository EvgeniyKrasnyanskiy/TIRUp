package com.tirup.app.domain.alert

import com.tirup.app.domain.model.GlucoseUnit
import java.util.Locale
import kotlin.math.roundToInt

object EmergencySmsBuilder {

    const val MAX_SINGLE_SMS_CHARS = 70

    /**
     * Extracts a short first name from a full name (e.g. "Иванов Иван Иванович" -> "Иван").
     * If multiple words (Фамилия Имя [Отчество]): takes the 2nd word (Имя).
     * If single word: takes that word.
     * Fallbacks to default if blank.
     */
    fun extractShortName(fullName: String, isRu: Boolean = true): String {
        val trimmed = fullName.trim()
        if (trimmed.isBlank()) {
            return if (isRu) "пациент" else "patient"
        }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val name = when {
            words.size >= 2 -> words[1]
            words.size == 1 -> words[0]
            else -> if (isRu) "пациент" else "patient"
        }
        return if (name.length > 12) name.take(11) + "." else name
    }

    /**
     * Builds the emergency SMS text sent when a critical hypoglycemia alarm is unacknowledged.
     */
    fun buildEmergencyMessage(
        patientName: String,
        glucoseValueMmol: Double,
        trendArrow: String = "",
        delayMinutes: Int = 5,
        latitude: Double? = null,
        longitude: Double? = null,
        isRu: Boolean = true,
        unit: GlucoseUnit = GlucoseUnit.MMOL_L
    ): String {
        val name = extractShortName(patientName, isRu)

        val glucoseStr = if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f ммоль", glucoseValueMmol)
        } else {
            "${(glucoseValueMmol * 18.0182).roundToInt()} mg/dL"
        }

        val arrowPart = if (trendArrow.isNotBlank()) " ($trendArrow)" else ""

        val locationPart = if (latitude != null && longitude != null) {
            val mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
            "\n$mapsUrl"
        } else ""

        val baseMsg = if (isRu) {
            "SOS! $name - критич. гипо: $glucoseStr$arrowPart! Сирена ${delayMinutes}м без реакции"
        } else {
            "SOS! $name - critical hypo: $glucoseStr$arrowPart! Alarm ${delayMinutes}m no reaction"
        }

        return baseMsg + locationPart
    }

    /**
     * Builds a verification SMS text for manual test sending from Settings.
     */
    fun buildTestMessage(patientName: String, isRu: Boolean): String {
        val name = extractShortName(patientName, isRu)
        val msg = if (isRu) {
            "TIRUp: Тест SMS ($name). Канал экстренной связи работает штатно."
        } else {
            "TIRUp: Test SMS ($name). Emergency alert channel verified."
        }
        return if (msg.length > MAX_SINGLE_SMS_CHARS) {
            if (isRu) "TIRUp: Тест SMS ($name). Канал связи активен." else "TIRUp: Test SMS ($name). Channel active."
        } else msg
    }

    /**
     * Builds an auto-reply SMS with current glucose telemetry when requested via SMS by a trusted contact.
     * Fits strictly within a single SMS segment (<= 70 chars) guaranteed.
     */
    fun buildQueryReplyMessage(
        patientName: String,
        glucoseValueMmol: Double,
        trendArrow: String = "",
        deltaMmol: Double? = null,
        readingTimestamp: Long,
        todayTirPercent: Int? = null,
        iob: Double? = null,
        isRu: Boolean = true,
        unit: GlucoseUnit = GlucoseUnit.MMOL_L
    ): String {
        var name = extractShortName(patientName, isRu)

        val glucoseStr = if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f ммоль", glucoseValueMmol)
        } else {
            "${(glucoseValueMmol * 18.0182).roundToInt()} mg/dL"
        }

        var arrowPart = if (trendArrow.isNotBlank()) " ($trendArrow)" else ""

        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.US)
        val timeStr = timeFormat.format(java.util.Date(readingTimestamp))

        var deltaStr = if (deltaMmol != null) {
            val sign = if (deltaMmol > 0) "+" else ""
            if (unit == GlucoseUnit.MMOL_L) {
                String.format(Locale.US, " (%s%.1f)", sign, deltaMmol)
            } else {
                val deltaMg = (deltaMmol * 18.0182).roundToInt()
                " ($sign$deltaMg)"
            }
        } else ""

        var tirPart = if (todayTirPercent != null) {
            " TIR: $todayTirPercent%."
        } else ""

        var iobPart = if (iob != null && iob > 0.0) {
            String.format(Locale.US, " IoB: %.1fU", iob)
        } else ""

        fun assemble(): String {
            val sep = if (isRu) "в" else "at"
            return "TIRUp: $name $glucoseStr$arrowPart $sep $timeStr$deltaStr.$tirPart$iobPart".trim()
        }

        var result = assemble()

        // Dynamic pruning to strictly guarantee <= 70 characters
        if (result.length > MAX_SINGLE_SMS_CHARS && deltaStr.isNotEmpty()) {
            deltaStr = ""
            result = assemble()
        }
        if (result.length > MAX_SINGLE_SMS_CHARS && iobPart.isNotEmpty()) {
            iobPart = ""
            result = assemble()
        }
        if (result.length > MAX_SINGLE_SMS_CHARS && tirPart.isNotEmpty()) {
            tirPart = ""
            result = assemble()
        }
        if (result.length > MAX_SINGLE_SMS_CHARS && arrowPart.isNotEmpty()) {
            arrowPart = ""
            result = assemble()
        }
        if (result.length > MAX_SINGLE_SMS_CHARS && name.length > 5) {
            name = name.take(4) + "."
            result = assemble()
        }

        return result
    }

    /**
     * Builds an auto-reply message when sensor signal is lost or no data is available.
     * Fits strictly in single SMS (<= 70 chars).
     */
    fun buildNoDataReplyMessage(patientName: String, isRu: Boolean): String {
        val name = extractShortName(patientName, isRu)
        val msg = if (isRu) {
            "TIRUp: $name - нет свежих данных (>20м). Потеря сигнала сенсора."
        } else {
            "TIRUp: $name - no fresh data (>20m). Sensor signal lost."
        }
        return if (msg.length > MAX_SINGLE_SMS_CHARS) {
            if (isRu) "TIRUp: $name - нет данных (>20м). Потеря сигнала." else "TIRUp: $name - no data (>20m). Signal lost."
        } else msg
    }
}
