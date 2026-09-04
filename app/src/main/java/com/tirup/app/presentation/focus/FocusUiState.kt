package com.tirup.app.presentation.focus

import com.tirup.app.domain.model.CompensatorGoal
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings

data class FocusUiState(
    val latestReading: GlucoseReading? = null,
    val recentReadings: List<GlucoseReading> = emptyList(),
    val treatments: List<com.tirup.app.domain.model.Treatment> = emptyList(),
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val compensatorGoal: CompensatorGoal = CompensatorGoal(),
    val streakDays: Int = 0,
    val userSettings: UserSettings = UserSettings(),
    val activeAlertBanner: com.tirup.app.data.alert.ActiveAlertBanner? = null,
    val isLoading: Boolean = false
)
