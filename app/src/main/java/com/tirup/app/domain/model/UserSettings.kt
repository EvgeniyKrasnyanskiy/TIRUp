package com.tirup.app.domain.model

data class PatientProfile(
    val fullName: String = "",
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val diabetesType: String = "", // e.g. "СД1", "СД2", "LADA", "MODY"
    val diabetesDurationYears: String = "",
    val therapyType: String = "" // e.g. "Помпа", "Шприц-ручки", "Таблетки"
)

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
