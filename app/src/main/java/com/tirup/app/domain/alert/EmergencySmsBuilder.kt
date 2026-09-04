package com.tirup.app.domain.alert

import com.tirup.app.domain.model.GlucoseUnit
import java.util.Locale
import kotlin.math.roundToInt

object EmergencySmsBuilder {

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
        val name = patientName.trim().ifBlank {
            if (isRu) "пациент" else "Patient"
        }

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

        return if (isRu) {
            "SOS! $name - критич. гипо: $glucoseStr$arrowPart! Сирена ${delayMinutes}м без реакции$locationPart"
        } else {
            "SOS! $name - critical hypo: $glucoseStr$arrowPart! Alarm ${delayMinutes}m no reaction$locationPart"
        }
    }

    /**
     * Builds a verification SMS text for manual test sending from Settings.
     */
    fun buildTestMessage(patientName: String, isRu: Boolean): String {
        val name = patientName.trim().ifBlank {
            if (isRu) "пациента" else "patient"
        }
        return if (isRu) {
            "TIRUp: Тест SMS для $name. Канал экстренной связи работает штатно."
        } else {
            "TIRUp: Test SMS for $name. Emergency alert channel verified."
        }
    }

    /**
     * Builds an auto-reply SMS with current glucose telemetry when requested via SMS by a trusted contact.
     * Fits strictly within a single SMS segment (<= 70 chars).
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
        val name = patientName.trim().ifBlank {
            if (isRu) "пациент" else "patient"
        }

        val glucoseStr = if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f ммоль", glucoseValueMmol)
        } else {
            "${(glucoseValueMmol * 18.0182).roundToInt()} mg/dL"
        }

        val arrowPart = if (trendArrow.isNotBlank()) " ($trendArrow)" else ""

        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.US)
        val timeStr = timeFormat.format(java.util.Date(readingTimestamp))

        val deltaStr = if (deltaMmol != null) {
            val sign = if (deltaMmol > 0) "+" else ""
            if (unit == GlucoseUnit.MMOL_L) {
                String.format(Locale.US, " (%s%.1f)", sign, deltaMmol)
            } else {
                val deltaMg = (deltaMmol * 18.0182).roundToInt()
                " ($sign$deltaMg)"
            }
        } else ""

        val tirPart = if (todayTirPercent != null) {
            " TIR: $todayTirPercent%."
        } else ""

        val iobPart = if (iob != null && iob > 0.0) {
            String.format(Locale.US, " IoB: %.1fU", iob)
        } else ""

        return if (isRu) {
            "TIRUp: $name $glucoseStr$arrowPart в $timeStr$deltaStr.$tirPart$iobPart".trim()
        } else {
            "TIRUp: $name $glucoseStr$arrowPart at $timeStr$deltaStr.$tirPart$iobPart".trim()
        }
    }

    /**
     * Builds an auto-reply message when sensor signal is lost or no data is available.
     * Fits in single SMS (<= 70 chars).
     */
    fun buildNoDataReplyMessage(patientName: String, isRu: Boolean): String {
        val name = patientName.trim().ifBlank {
            if (isRu) "пациент" else "patient"
        }
        return if (isRu) {
            "TIRUp: $name - нет свежих данных (>20м). Потеря сигнала сенсора."
        } else {
            "TIRUp: $name - no fresh data (>20m). Sensor signal lost."
        }
    }
}
