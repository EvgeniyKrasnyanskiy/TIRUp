package com.tirup.app.presentation.focus

import com.tirup.app.domain.model.CompensatorGoal
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.SensorStatus
import com.tirup.app.domain.model.PumpSetStatus

data class FocusUiState(
    val latestReading: GlucoseReading? = null,
    val recentReadings: List<GlucoseReading> = emptyList(),
    val treatments: List<com.tirup.app.domain.model.Treatment> = emptyList(),
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val compensatorGoal: CompensatorGoal = CompensatorGoal(),
    val streakDays: Int = 0,
    val userSettings: UserSettings = UserSettings(),
    val activeAlertBanner: com.tirup.app.data.alert.ActiveAlertBanner? = null,
    val recentDailySummaries: List<com.tirup.app.domain.model.DailySummary> = emptyList(),
    val isLoading: Boolean = false,
    val sensorStatus: SensorStatus = SensorStatus(),
    val pumpSetStatus: PumpSetStatus = PumpSetStatus(),
    val lancetStatus: com.tirup.app.domain.model.LancetStatus = com.tirup.app.domain.model.LancetStatus()
)
