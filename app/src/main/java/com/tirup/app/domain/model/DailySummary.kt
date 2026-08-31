package com.tirup.app.domain.model

data class DailySummary(
    val dateTimestamp: Long, // Start of day 00:00 UTC/Local
    val meanMmol: Double,
    val tirPercent: Double,
    val tingPercent: Double,
    val tbrVeryLowPercent: Double,
    val tbrLowPercent: Double,
    val tarHighPercent: Double,
    val tarVeryHighPercent: Double,
    val sdMmol: Double,
    val cvPercent: Double,
    val readingsCount: Int
)
