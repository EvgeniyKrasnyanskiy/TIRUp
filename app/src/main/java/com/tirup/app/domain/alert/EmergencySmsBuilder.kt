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
        // Strip any test or meta tags like [ТЕСТ], [TEST], (Тест)
        val clean = fullName.replace(Regex("^(\\[.*?\\]|\\(.*?\\))\\s*"), "").trim()
        if (clean.isBlank()) {
            return if (isRu) "пациент" else "patient"
        }
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
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
        unit: GlucoseUnit = GlucoseUnit.MMOL_L,
        isTest: Boolean = false
    ): String {
        val name = extractShortName(patientName, isRu)

        val glucoseStr = if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f ммоль", glucoseValueMmol)
        } else {
            "${(glucoseValueMmol * 18.0182).roundToInt()} mg/dL"
        }

        val arrowPart = if (trendArrow.isNotBlank()) " ($trendArrow)" else ""

        if (isTest) {
            val testMsg = if (isRu) {
                "SOS! [ТЕСТ] $name - критич. гипо: $glucoseStr$arrowPart! Сирена ${delayMinutes}м"
            } else {
                "SOS! [TEST] $name - critical hypo: $glucoseStr$arrowPart! Alarm ${delayMinutes}m"
            }
            return if (testMsg.length > MAX_SINGLE_SMS_CHARS) {
                testMsg.take(MAX_SINGLE_SMS_CHARS)
            } else testMsg
        }

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

    const val MAX_QUERY_SMS_CHARS = 160

    /**
     * Builds an auto-reply SMS with current glucose telemetry when requested via SMS by a trusted contact.
     * Fits strictly within a single GSM 7-bit SMS segment (<= 160 chars) guaranteed.
     * Transliterates patient name and maps trend arrows to ASCII (->, ^, v) to prevent UCS-2 encoding fallback.
     */
    fun buildQueryReplyMessage(
        patientName: String,
        glucoseValueMmol: Double,
        trendArrow: String = "",
        deltaMmol: Double? = null,
        readingTimestamp: Long,
        todayTirPercent: Int? = null,
        iob: Double? = null,
        cob: Double? = null,
        batteryPercent: Int? = null,
        historyPoints: List<Double> = emptyList(),
        isRu: Boolean = true,
        unit: GlucoseUnit = GlucoseUnit.MMOL_L
    ): String {
        val rawName = extractShortName(patientName, isRu)
        var name = Gsm7Transliteration.transliterate(rawName)

        val glucoseStr = if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f", glucoseValueMmol)
        } else {
            "${(glucoseValueMmol * 18.0182).roundToInt()}"
        }

        val safeArrow = Gsm7Transliteration.normalizeArrow(trendArrow)
        var arrowPart = if (safeArrow.isNotBlank()) " ($safeArrow)" else ""

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

        var historyPart = if (historyPoints.isNotEmpty()) {
            val pointsStr = historyPoints.joinToString(" > ") {
                if (unit == GlucoseUnit.MMOL_L) {
                    String.format(Locale.US, "%.1f", it)
                } else {
                    "${(it * 18.0182).roundToInt()}"
                }
            }
            " [$pointsStr]"
        } else ""

        var tirPart = if (todayTirPercent != null) {
            " TIR: $todayTirPercent%"
        } else ""

        var iobPart = if (iob != null && iob > 0.0) {
            String.format(Locale.US, " IoB: %.1fU", iob)
        } else ""

        var cobPart = if (cob != null && cob > 0.0) {
            String.format(Locale.US, " CoB: %.0fg", cob)
        } else ""

        var batPart = if (batteryPercent != null && batteryPercent in 0..100) {
            " BAT: $batteryPercent%"
        } else ""

        fun assemble(): String {
            return "$name $glucoseStr$arrowPart at $timeStr$deltaStr$historyPart$tirPart$iobPart$cobPart$batPart".trim()
        }

        var result = Gsm7Transliteration.toGsm7Safe(assemble())

        // Dynamic pruning to strictly guarantee <= 160 characters (1 standard GSM SMS)
        if (result.length > MAX_QUERY_SMS_CHARS && cobPart.isNotEmpty()) {
            cobPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && historyPart.isNotEmpty()) {
            historyPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && deltaStr.isNotEmpty()) {
            deltaStr = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && iobPart.isNotEmpty()) {
            iobPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && batPart.isNotEmpty()) {
            batPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && tirPart.isNotEmpty()) {
            tirPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && arrowPart.isNotEmpty()) {
            arrowPart = ""
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS && name.length > 5) {
            name = name.take(4) + "."
            result = Gsm7Transliteration.toGsm7Safe(assemble())
        }
        if (result.length > MAX_QUERY_SMS_CHARS) {
            result = result.take(MAX_QUERY_SMS_CHARS)
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
