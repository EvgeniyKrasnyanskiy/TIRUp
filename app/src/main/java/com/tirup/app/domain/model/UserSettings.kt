package com.tirup.app.domain.model

import java.util.Calendar

data class PatientProfile(
    val fullName: String = "",
    val birthYear: Int = 1990,
    val birthMonth: Int = 1,
    val heightCm: String = "",
    val weightKg: String = "",
    val diabetesType: String = "СД1", // "СД1", "СД2", "LADA", "MODY", "ГСД"
    val diagnosisYear: Int = 2018,
    val therapyType: String = "Инсулиновая помпа" // "Инсулиновая помпа", "Шприц-ручки (МДИ)", "Таблетки", "Диета"
) {
    val calculatedAge: Int
        get() {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            return (currentYear - birthYear).coerceAtLeast(0)
        }

    val calculatedDuration: Int
        get() {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            return (currentYear - diagnosisYear).coerceAtLeast(0)
        }
}

data class UserSettings(
    val language: String = "RU", // "RU" or "EN"
    val unit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val targetMode: TargetMode = TargetMode.TIR,
    val targetRanges: TargetRanges = TargetRanges(),
    val periodDays: Int = 14,
    val nightStartHour: Int = 0,
    val nightEndHour: Int = 6,
    val patientProfile: PatientProfile = PatientProfile()
)
