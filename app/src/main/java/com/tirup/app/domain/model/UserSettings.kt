package com.tirup.app.domain.model

data class UserSettings(
    val language: String = "RU", // "RU" or "EN"
    val unit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val targetMode: TargetMode = TargetMode.TIR,
    val targetRanges: TargetRanges = TargetRanges(),
    val periodDays: Int = 14
)
