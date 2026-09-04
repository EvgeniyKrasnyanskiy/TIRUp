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
}
