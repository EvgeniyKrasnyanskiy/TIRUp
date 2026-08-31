package com.tirup.app.presentation.trends

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.HeatmapCell
import com.tirup.app.domain.model.UserSettings

enum class TrendPeriod(val days: Int, val stringResId: Int) {
    PERIOD_7D(7, com.tirup.app.R.string.period_7d),
    PERIOD_14D(14, com.tirup.app.R.string.period_14d),
    PERIOD_30D(30, com.tirup.app.R.string.period_30d),
    PERIOD_90D(90, com.tirup.app.R.string.period_90d),
    PERIOD_YEAR(365, com.tirup.app.R.string.period_year),
    PERIOD_ALL(-1, com.tirup.app.R.string.period_all)
}

data class TrendsUiState(
    val selectedPeriod: TrendPeriod = TrendPeriod.PERIOD_14D,
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val percentileBins: List<AGPPercentileBin> = emptyList(),
    val heatmapData: List<List<HeatmapCell>> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val isLoading: Boolean = false
)
