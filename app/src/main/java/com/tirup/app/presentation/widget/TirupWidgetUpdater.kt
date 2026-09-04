package com.tirup.app.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.tirup.app.R
import com.tirup.app.TirupApplication
import com.tirup.app.domain.calculator.TargetCompensatorCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.TargetMode
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object TirupWidgetUpdater {

    private const val TAG = "TirupWidgetUpdater"

    suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@withContext

            val stripIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupStripWidgetProvider::class.java))
            val dashboardIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupDashboardWidgetProvider::class.java))
            val compactIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupCompactWidgetProvider::class.java))
            val minimalIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupMiniWidgetProvider::class.java))
            val verticalIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupVerticalWidgetProvider::class.java))
            val mediumIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TirupMediumWidgetProvider::class.java))

            if (stripIds.isEmpty() && dashboardIds.isEmpty() && compactIds.isEmpty() && minimalIds.isEmpty() && verticalIds.isEmpty() && mediumIds.isEmpty()) {
                return@withContext
            }

            val app = context.applicationContext as? TirupApplication ?: return@withContext
            val settings = app.settingsRepository.getSettings().first()
            val latest = app.glucoseRepository.getLatestReading().first()
            val recent = app.glucoseRepository.getRecentReadings(280).first() // 4+ hours of 1-min readings

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val todayEntities = app.database.glucoseReadingDao().getReadingsBetweenSync(
                startOfDay,
                System.currentTimeMillis() + 60_000L
            )
            val todayReadings = todayEntities.map { it.toDomain() }

            val streakDays = try {
                app.glucoseRepository.getStreakDays().first()
            } catch (_: Exception) {
                0
            }

            val mainPendingIntent = getMainPendingIntent(context)
            val nightstandPendingIntent = getNightstandPendingIntent(context)

            // 1. Update 4x1 / 5x1 Strip Widgets
            if (stripIds.isNotEmpty()) {
                val views = buildStripViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    streakDays = streakDays,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(stripIds, views)
            }

            // 2. Update 4x2 / 5x2 Dashboard Widgets
            if (dashboardIds.isNotEmpty()) {
                val views = buildDashboardViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    streakDays = streakDays,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(dashboardIds, views)
            }

            // 3. Update 2x2 Compact Widgets
            if (compactIds.isNotEmpty()) {
                val views = buildCompactViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    streakDays = streakDays,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(compactIds, views)
            }

            // 4. Update 1x1 Minimal Widgets
            if (minimalIds.isNotEmpty()) {
                val views = buildMinimalViews(
                    context = context,
                    latest = latest,
                    todayReadings = todayReadings,
                    settings = settings,
                    mainIntent = mainPendingIntent
                )
                appWidgetManager.updateAppWidget(minimalIds, views)
            }

            // 5. Update 1x2 Vertical Widgets
            if (verticalIds.isNotEmpty()) {
                val views = buildVerticalViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    streakDays = streakDays,
                    mainIntent = mainPendingIntent
                )
                appWidgetManager.updateAppWidget(verticalIds, views)
            }

            // 6. Update 3x2 Medium Dashboard Widgets
            if (mediumIds.isNotEmpty()) {
                val views = buildMediumDashboardViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    streakDays = streakDays,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(mediumIds, views)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating TIRUp homescreen widgets", e)
        }
    }

    /**
     * Handles dynamic widget resizing (e.g. morphing 4x1 -> 4x2 or 5x2 -> 5x1).
     */
    suspend fun updateAppWidgetForOptions(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as? TirupApplication ?: return@withContext
            val settings = app.settingsRepository.getSettings().first()
            val latest = app.glucoseRepository.getLatestReading().first()
            val recent = app.glucoseRepository.getRecentReadings(280).first()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val todayEntities = app.database.glucoseReadingDao().getReadingsBetweenSync(
                startOfDay,
                System.currentTimeMillis() + 60_000L
            )
            val todayReadings = todayEntities.map { it.toDomain() }
            val streakDays = try { app.glucoseRepository.getStreakDays().first() } catch (_: Exception) { 0 }

            val minHeight = newOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            val minWidth = newOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0

            val mainPendingIntent = getMainPendingIntent(context)
            val nightstandPendingIntent = getNightstandPendingIntent(context)

            // Dynamic layout selection based on current bounding box
            val views = when {
                minHeight >= 100 && minWidth >= 240 -> {
                    // Expanded 4x2 or 5x2 Bento Dashboard with 4h sparkline chart
                    buildDashboardViews(context, latest, recent, todayReadings, settings, streakDays, mainPendingIntent, nightstandPendingIntent)
                }
                minHeight >= 90 && minWidth in 140..239 -> {
                    // 3x2 Compact Dashboard
                    buildMediumDashboardViews(context, latest, recent, todayReadings, settings, streakDays, mainPendingIntent, nightstandPendingIntent)
                }
                minWidth < 100 && minHeight >= 90 -> {
                    // 1x2 Vertical Glance
                    buildVerticalViews(context, latest, recent, todayReadings, settings, streakDays, mainPendingIntent)
                }
                minWidth < 100 && minHeight < 90 -> {
                    // 1x1 micro cell
                    buildMinimalViews(context, latest, todayReadings, settings, mainPendingIntent)
                }
                minWidth in 100..179 && minHeight >= 90 -> {
                    // 2x2 square focus
                    buildCompactViews(context, latest, recent, todayReadings, settings, streakDays, mainPendingIntent, nightstandPendingIntent)
                }
                else -> {
                    // 1-row tall strip (4x1 or 5x1)
                    buildStripViews(context, latest, recent, todayReadings, settings, streakDays, mainPendingIntent, nightstandPendingIntent)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling widget resize options", e)
        }
    }

    private fun buildStripViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        streakDays: Int,
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_strip)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setTextViewText(R.id.widget_compensator_text, "Ожидание данных CGM")
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
            views.setViewVisibility(R.id.widget_iob_cob_layout, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)
        bindCompensator(views, latest, todayReadings, settings, isStrip = true)
        bindIobCob(views, latest)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_badge, if (isRu) "🔥 $streakDays д." else "🔥 ${streakDays}d")
        } else {
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
        }

        return views
    }

    private fun buildDashboardViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        streakDays: Int,
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setTextViewText(R.id.widget_compensator_text, "Ожидание данных CGM")
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)
        bindCompensator(views, latest, todayReadings, settings, isStrip = false)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_text, if (isRu) "🔥 $streakDays д." else "🔥 ${streakDays}d")
        } else {
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
        }

        // IoB badge
        val iob = latest.iob ?: 0.0
        if (iob > 0.05) {
            views.setViewVisibility(R.id.widget_iob_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_iob_text, String.format(Locale.US, "💉 %.1f U", iob))
        } else {
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
        }

        // Render 4-hour HD Canvas Sparkline with corridor and time scale
        val sparklineBitmap = drawSparklineBitmap(
            readings = recent,
            latest = latest,
            widthPx = 640,
            heightPx = 170,
            ranges = settings.targetRanges,
            isRu = isRu
        )
        if (sparklineBitmap != null) {
            views.setImageViewBitmap(R.id.widget_chart_image, sparklineBitmap)
            views.setViewVisibility(R.id.widget_chart_image, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_chart_image, View.GONE)
        }

        return views
    }

    private fun buildCompactViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        streakDays: Int,
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_badge, if (isRu) "🔥 $streakDays д." else "🔥 ${streakDays}d")
        } else {
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
        }

        // TIR Score
        val inRangeCount = todayReadings.count {
            if (settings.targetMode == TargetMode.TIR) {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tirHighMmol
            } else {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tingHighMmol
            }
        }
        val currentPercent = if (todayReadings.isNotEmpty()) {
            (inRangeCount * 100.0 / todayReadings.size).roundToInt()
        } else 0

        val targetName = settings.targetMode.name
        val targetPercent = if (settings.targetMode == TargetMode.TIR) {
            settings.targetRanges.tirGoalPercent.toDouble()
        } else {
            settings.targetRanges.tingGoalPercent.toDouble()
        }
        val tirColor = when {
            currentPercent >= targetPercent -> Color.parseColor("#10B981")
            currentPercent >= (targetPercent - 15.0) -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextViewText(R.id.widget_tir_score, "$targetName: $currentPercent%")
        views.setTextColor(R.id.widget_tir_score, tirColor)

        return views
    }

    private fun buildMinimalViews(
        context: Context,
        latest: GlucoseReading?,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        mainIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_minimal)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_tir_score, "TIR --")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            return views
        }

        val isMmol = settings.unit == GlucoseUnit.MMOL_L
        val now = System.currentTimeMillis()
        val diffMin = ((now - latest.timestamp).coerceAtLeast(0L) / 60_000L).toInt()
        val isStale = diffMin > 5

        // Glucose Value
        val glucoseStr = if (isMmol) {
            String.format(Locale.US, "%.1f", latest.valueMmol)
        } else {
            "${(latest.valueMmol * 18.0182).roundToInt()}"
        }
        views.setTextViewText(R.id.widget_glucose_value, glucoseStr)

        // Range color (synchronized with FocusScreen: 7.9 - 10.0 is Good Blue!)
        val glucoseColor = when {
            isStale -> Color.parseColor("#94A3B8") // Gray when stale!
            latest.valueMmol < 3.0 -> Color.parseColor("#EF4444")
            latest.valueMmol < settings.targetRanges.tirLowMmol -> Color.parseColor("#F59E0B")
            latest.valueMmol <= 7.0 -> Color.parseColor("#10B981")
            latest.valueMmol <= 7.8 -> Color.parseColor("#84CC16")
            latest.valueMmol <= settings.targetRanges.tirHighMmol -> Color.parseColor("#3B82F6") // Blue!
            latest.valueMmol <= 13.9 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextColor(R.id.widget_glucose_value, glucoseColor)
        views.setTextColor(R.id.widget_trend_arrow, glucoseColor)
        views.setTextViewText(R.id.widget_trend_arrow, latest.trendArrow)

        // Time ago (hidden if fresh <= 1m)
        if (diffMin <= 1) {
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_time_ago, View.VISIBLE)
            views.setTextViewText(R.id.widget_time_ago, if (diffMin < 60) "${diffMin}м" else "${diffMin / 60}ч")
        }

        // TIR Score
        val inRangeCount = todayReadings.count {
            if (settings.targetMode == TargetMode.TIR) {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tirHighMmol
            } else {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tingHighMmol
            }
        }
        val currentPercent = if (todayReadings.isNotEmpty()) {
            (inRangeCount * 100.0 / todayReadings.size).roundToInt()
        } else 0

        val targetName = settings.targetMode.name
        val targetPercent = if (settings.targetMode == TargetMode.TIR) {
            settings.targetRanges.tirGoalPercent.toDouble()
        } else {
            settings.targetRanges.tingGoalPercent.toDouble()
        }
        val tirColor = when {
            currentPercent >= targetPercent -> Color.parseColor("#10B981")
            currentPercent >= (targetPercent - 15.0) -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        if (diffMin <= 1) {
            views.setTextViewText(R.id.widget_tir_score, "$targetName $currentPercent%")
        } else {
            val timeAgoStr = if (diffMin < 60) "${diffMin}м" else "${diffMin / 60}ч"
            views.setTextViewText(R.id.widget_tir_score, "$currentPercent% • $timeAgoStr")
        }
        views.setTextColor(R.id.widget_tir_score, tirColor)

        return views
    }

    private fun buildVerticalViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        streakDays: Int,
        mainIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vertical)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_badge, if (isRu) "🔥 $streakDays д." else "🔥 ${streakDays}d")
        } else {
            views.setViewVisibility(R.id.widget_streak_badge, View.GONE)
        }

        // TIR Score & Progress
        val inRangeCount = todayReadings.count {
            if (settings.targetMode == TargetMode.TIR) {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tirHighMmol
            } else {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tingHighMmol
            }
        }
        val currentPercent = if (todayReadings.isNotEmpty()) {
            (inRangeCount * 100.0 / todayReadings.size).roundToInt()
        } else 0

        val targetName = settings.targetMode.name
        val targetPercent = if (settings.targetMode == TargetMode.TIR) {
            settings.targetRanges.tirGoalPercent.toDouble()
        } else {
            settings.targetRanges.tingGoalPercent.toDouble()
        }
        val tirColor = when {
            currentPercent >= targetPercent -> Color.parseColor("#10B981")
            currentPercent >= (targetPercent - 15.0) -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextViewText(R.id.widget_tir_score, "$targetName: $currentPercent%")
        views.setTextColor(R.id.widget_tir_score, tirColor)
        views.setProgressBar(R.id.widget_tir_progress, 100, currentPercent.coerceIn(0, 100), false)

        // IoB badge
        val iob = latest.iob ?: 0.0
        if (iob > 0.05) {
            views.setViewVisibility(R.id.widget_iob_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_iob_text, String.format(Locale.US, "💉 %.1f U", iob))
        } else {
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
        }

        // Time indicator for 1x2 (show compact time if fresh, e.g. 12:45)
        val now = System.currentTimeMillis()
        val diffMin = ((now - latest.timestamp).coerceAtLeast(0L) / 60_000L).toInt()
        views.setViewVisibility(R.id.widget_time_ago, View.VISIBLE)
        if (diffMin <= 1) {
            val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            views.setTextViewText(R.id.widget_time_ago, timeFormat.format(java.util.Date(latest.timestamp)))
        } else {
            views.setTextViewText(R.id.widget_time_ago, if (diffMin < 60) "${diffMin}м" else "${diffMin / 60}ч")
        }

        return views
    }

    private fun buildMediumDashboardViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        streakDays: Int,
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_medium)
        applyWidgetBackground(views, settings)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextColor(R.id.widget_glucose_value, Color.parseColor("#94A3B8"))
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setTextViewText(R.id.widget_compensator_text, "Ожидание данных CGM")
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)
        bindCompensator(views, latest, todayReadings, settings, isStrip = false)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_text, if (isRu) "🔥 $streakDays д." else "🔥 ${streakDays}d")
        } else {
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
        }

        // IoB badge
        val iob = latest.iob ?: 0.0
        if (iob > 0.05) {
            views.setViewVisibility(R.id.widget_iob_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_iob_text, String.format(Locale.US, "💉 %.1f U", iob))
        } else {
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
        }

        // Render 4-hour HD Canvas Sparkline with corridor and time scale
        val sparklineBitmap = drawSparklineBitmap(
            readings = recent,
            latest = latest,
            widthPx = 540,
            heightPx = 160,
            ranges = settings.targetRanges,
            isRu = isRu
        )
        if (sparklineBitmap != null) {
            views.setImageViewBitmap(R.id.widget_chart_image, sparklineBitmap)
            views.setViewVisibility(R.id.widget_chart_image, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_chart_image, View.GONE)
        }

        return views
    }

    private fun bindCommonMetrics(
        views: RemoteViews,
        latest: GlucoseReading,
        recent: List<GlucoseReading>,
        settings: UserSettings
    ) {
        val isMmol = settings.unit == GlucoseUnit.MMOL_L
        val now = System.currentTimeMillis()

        // 1. Glucose Value
        val glucoseStr = if (isMmol) {
            String.format(Locale.US, "%.1f", latest.valueMmol)
        } else {
            "${(latest.valueMmol * 18.0182).roundToInt()}"
        }
        views.setTextViewText(R.id.widget_glucose_value, glucoseStr)

        // Color based on range and age (matching FocusScreen exactly: 7.9-10.0 is Good Blue!)
        val diffMs = (now - latest.timestamp).coerceAtLeast(0L)
        val diffMin = (diffMs / 60_000L).toInt()
        val isStale = diffMin > 5

        val glucoseColor = when {
            isStale -> Color.parseColor("#94A3B8") // Gray when stale!
            latest.valueMmol < 3.0 -> Color.parseColor("#EF4444")
            latest.valueMmol < settings.targetRanges.tirLowMmol -> Color.parseColor("#F59E0B")
            latest.valueMmol <= 7.8 -> Color.parseColor("#4ADE80") // Pale Green 3.9 - 7.8
            latest.valueMmol <= settings.targetRanges.tirHighMmol -> Color.parseColor("#10B981") // Saturated Emerald 7.9 - 10.0
            latest.valueMmol <= 13.9 -> Color.parseColor("#F59E0B") // ColorHigh
            else -> Color.parseColor("#EF4444") // ColorVeryHigh
        }
        views.setTextColor(R.id.widget_glucose_value, glucoseColor)
        views.setTextColor(R.id.widget_trend_arrow, glucoseColor)

        // 2. Trend Arrow
        views.setTextViewText(R.id.widget_trend_arrow, latest.trendArrow)

        // 3. Clinical 5-minute velocity delta (stable across 1m and 5m sensors)
        val deltaMmol = calculate5MinDelta(latest, recent)
        if (deltaMmol != null) {
            val deltaStr = if (isMmol) {
                String.format(Locale.US, "%+.1f", deltaMmol)
            } else {
                val deltaMg = (deltaMmol * 18.0182).roundToInt()
                "${if (deltaMg > 0) "+" else ""}$deltaMg"
            }
            views.setTextViewText(R.id.widget_delta_value, deltaStr)
        } else {
            views.setTextViewText(R.id.widget_delta_value, "--")
        }

        // 4. Time Ago (hidden if <= 1m, compact "2м", "5м", "1ч" without "назад")
        if (diffMin <= 1) {
            views.setViewVisibility(R.id.widget_time_ago, View.GONE)
            views.setTextViewText(R.id.widget_time_ago, "")
        } else {
            views.setViewVisibility(R.id.widget_time_ago, View.VISIBLE)
            val timeAgoStr = if (diffMin < 60) "${diffMin}м" else "${diffMin / 60}ч"
            views.setTextViewText(R.id.widget_time_ago, timeAgoStr)
        }
    }

    private fun bindCompensator(
        views: RemoteViews,
        latest: GlucoseReading,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        isStrip: Boolean = false
    ) {
        val isRu = settings.language.equals("RU", ignoreCase = true)
        val targetPercent = if (settings.targetMode == TargetMode.TIR) {
            settings.targetRanges.tirGoalPercent.toDouble()
        } else {
            settings.targetRanges.tingGoalPercent.toDouble()
        }

        val compensator = TargetCompensatorCalculator.calculateDailyCompensator(
            targetMode = settings.targetMode,
            targetPercent = targetPercent,
            latestReading = latest,
            recentReadings = todayReadings,
            targetRanges = settings.targetRanges,
            language = settings.language
        )

        val targetName = settings.targetMode.name
        val inRangeCount = todayReadings.count {
            if (settings.targetMode == TargetMode.TIR) {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tirHighMmol
            } else {
                it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tingHighMmol
            }
        }
        val currentPercent = if (todayReadings.isNotEmpty()) {
            (inRangeCount * 100.0 / todayReadings.size).roundToInt()
        } else 0

        val tirColor = when {
            currentPercent >= targetPercent -> Color.parseColor("#10B981")
            currentPercent >= (targetPercent - 15.0) -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextViewText(R.id.widget_tir_score, "$targetName: $currentPercent%")
        views.setTextColor(R.id.widget_tir_score, tirColor)
        views.setProgressBar(R.id.widget_tir_progress, 100, currentPercent.coerceIn(0, 100), false)

        val recText = if (isStrip) {
            formatStripCompensator(compensator, isRu)
        } else {
            if (isRu) compensator.recommendationRu else compensator.recommendationEn
        }
        views.setTextViewText(R.id.widget_compensator_text, recText)
    }

    private fun formatStripCompensator(compensator: com.tirup.app.domain.model.CompensatorGoal, isRu: Boolean): String {
        return when {
            compensator.neededMinutesToday <= 0 -> if (isRu) "Цель достигнута! (100%)" else "Goal reached! (100%)"
            compensator.neededMinutesToday > compensator.remainingMinutesToday -> if (isRu) "Недостижимо (< цели)" else "Off target (< goal)"
            else -> {
                val hours = compensator.neededMinutesToday / 60
                val mins = compensator.neededMinutesToday % 60
                if (hours > 0) {
                    if (mins > 0) {
                        if (isRu) "В норме ещё ${hours}ч ${mins}м" else "In range ${hours}h ${mins}m"
                    } else {
                        if (isRu) "В норме ещё ${hours}ч" else "In range ${hours}h"
                    }
                } else {
                    if (isRu) "В норме ещё ${mins}м" else "In range ${mins}m"
                }
            }
        }
    }

    fun getBackgroundResourceForOpacity(opacityPercent: Int): Int {
        return when {
            opacityPercent >= 95 -> R.drawable.widget_background_100
            opacityPercent >= 80 -> R.drawable.widget_background_85
            opacityPercent >= 60 -> R.drawable.widget_background_70
            opacityPercent >= 40 -> R.drawable.widget_background_50
            opacityPercent >= 20 -> R.drawable.widget_background_30
            opacityPercent >= 5 -> R.drawable.widget_background_15
            else -> R.drawable.widget_background_0
        }
    }

    private fun applyWidgetBackground(views: RemoteViews, settings: UserSettings) {
        val bgRes = getBackgroundResourceForOpacity(settings.widgetBackgroundOpacity)
        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
    }

    private fun bindIobCob(views: RemoteViews, latest: GlucoseReading) {
        val iob = latest.iob ?: 0.0
        val cob = latest.cob ?: 0.0

        if (iob > 0.05 || cob > 0.5) {
            views.setViewVisibility(R.id.widget_iob_cob_layout, View.VISIBLE)
            if (iob > 0.05) {
                views.setViewVisibility(R.id.widget_iob_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_iob_text, String.format(Locale.US, "💉 %.1f U", iob))
            } else {
                views.setViewVisibility(R.id.widget_iob_text, View.GONE)
            }

            if (cob > 0.5) {
                views.setViewVisibility(R.id.widget_cob_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_cob_text, String.format(Locale.US, "🍞 %.0f g", cob))
            } else {
                views.setViewVisibility(R.id.widget_cob_text, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_iob_cob_layout, View.GONE)
        }
    }

    /**
     * Calculates clinical 5-minute velocity delta.
     */
    fun calculate5MinDelta(latest: GlucoseReading, recent: List<GlucoseReading>): Double? {
        if (recent.size < 2) return null

        val targetTime = latest.timestamp - 5 * 60 * 1000L
        val minTargetTime = targetTime - 2 * 60 * 1000L // -7 min
        val maxTargetTime = targetTime + 2 * 60 * 1000L // -3 min

        val candidate = recent
            .filter { it.timestamp in minTargetTime..maxTargetTime && it.timestamp != latest.timestamp }
            .minByOrNull { abs(it.timestamp - targetTime) }

        val reference = candidate ?: recent
            .filter { it.timestamp < latest.timestamp }
            .maxByOrNull { it.timestamp }

        return if (reference != null) {
            val delta = latest.valueMmol - reference.valueMmol
            (delta * 10.0).roundToInt() / 10.0
        } else null
    }

    /**
     * Draws a high-definition Sparkline bitmap of glucose history
     * over the last 4 hours with shaded target corridor and time scale (-4ч, -2ч, сейчас).
     */
    fun drawSparklineBitmap(
        readings: List<GlucoseReading>,
        latest: GlucoseReading,
        widthPx: Int,
        heightPx: Int,
        ranges: TargetRanges,
        isRu: Boolean = true
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val windowMs = 14_400_000L // 4 hours window in milliseconds
        val endTime = latest.timestamp
        val startTime = endTime - windowMs

        val windowReadings = readings
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }

        if (windowReadings.isEmpty()) return bitmap

        val low = ranges.tirLowMmol
        val high = ranges.tirHighMmol

        val minGlucose = minOf(windowReadings.minOf { it.valueMmol }, low - 0.6).coerceAtLeast(1.5)
        val maxGlucose = maxOf(windowReadings.maxOf { it.valueMmol }, high + 0.8).coerceAtLeast(11.5)
        val glucoseSpan = (maxGlucose - minGlucose).coerceAtLeast(4.0)

        val paddingLeft = 14f
        val paddingRight = 18f
        val paddingTop = 10f
        val paddingBottom = 24f // Space for bottom time scale labels

        val plotWidth = widthPx - paddingLeft - paddingRight
        val plotHeight = heightPx - paddingTop - paddingBottom

        fun getY(value: Double): Float {
            val fraction = (value - minGlucose) / glucoseSpan
            return (paddingTop + plotHeight * (1.0 - fraction)).toFloat().coerceIn(paddingTop, paddingTop + plotHeight)
        }

        fun getX(timestamp: Long): Float {
            val fraction = (timestamp - startTime).toDouble() / windowMs.toDouble()
            return (paddingLeft + plotWidth * fraction).toFloat().coerceIn(paddingLeft, paddingLeft + plotWidth)
        }

        // 1. Draw Target Range Band (3.9 - 10.0)
        val yTargetLow = getY(low)
        val yTargetHigh = getY(high)
        val bandPaint = Paint().apply {
            color = Color.parseColor("#2610B981") // 15% opacity emerald
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(paddingLeft, yTargetHigh, paddingLeft + plotWidth, yTargetLow, bandPaint)

        // 2. Draw Target Guidelines
        val guidePaint = Paint().apply {
            color = Color.parseColor("#4D10B981") // 30% opacity emerald
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
            isAntiAlias = true
        }
        canvas.drawLine(paddingLeft, yTargetLow, paddingLeft + plotWidth, yTargetLow, guidePaint)
        canvas.drawLine(paddingLeft, yTargetHigh, paddingLeft + plotWidth, yTargetHigh, guidePaint)

        // 3. Draw Bottom Time Scale: -4ч, -2ч, сейчас
        val timeLabelPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 18f
            isAntiAlias = true
        }
        val tickY = paddingTop + plotHeight
        val tickPaint = Paint().apply {
            color = Color.parseColor("#334155")
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        // -4h tick & label
        canvas.drawLine(paddingLeft, tickY, paddingLeft, tickY + 5f, tickPaint)
        timeLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(if (isRu) "-4ч" else "-4h", paddingLeft, heightPx - 4f, timeLabelPaint)

        // -2h tick & label
        val midX = paddingLeft + plotWidth / 2f
        canvas.drawLine(midX, tickY, midX, tickY + 5f, tickPaint)
        timeLabelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (isRu) "-2ч" else "-2h", midX, heightPx - 4f, timeLabelPaint)

        // Now tick & label
        val nowX = paddingLeft + plotWidth
        canvas.drawLine(nowX, tickY, nowX, tickY + 5f, tickPaint)
        timeLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (isRu) "сейчас" else "now", nowX, heightPx - 4f, timeLabelPaint)

        // 4. Draw Trajectory Line (colors: Pale Green 3.9..7.8, Emerald 7.9..10.0)
        val curveColor = when {
            latest.valueMmol < 3.0 -> Color.parseColor("#EF4444")
            latest.valueMmol < low -> Color.parseColor("#F59E0B")
            latest.valueMmol <= 7.8 -> Color.parseColor("#4ADE80") // Pale Green 3.9..7.8
            latest.valueMmol <= high -> Color.parseColor("#10B981") // Emerald 7.9..10.0
            latest.valueMmol <= 13.9 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }

        val solidPaint = Paint().apply {
            color = curveColor
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val gapPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            isAntiAlias = true
        }

        var prevReading: GlucoseReading? = null
        for (reading in windowReadings) {
            val x = getX(reading.timestamp)
            val y = getY(reading.valueMmol)
            if (prevReading != null) {
                val prevX = getX(prevReading.timestamp)
                val prevY = getY(prevReading.valueMmol)
                val dt = reading.timestamp - prevReading.timestamp
                if (dt > 20 * 60_000L) {
                    // Gap > 20 min: draw dashed connection
                    canvas.drawLine(prevX, prevY, x, y, gapPaint)
                } else {
                    canvas.drawLine(prevX, prevY, x, y, solidPaint)
                }
            }
            prevReading = reading
        }

        // 5. Draw Current Glucose Glowing Head Dot
        val lastX = getX(latest.timestamp)
        val lastY = getY(latest.valueMmol)

        val glowPaint = Paint().apply {
            color = curveColor
            alpha = 80
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(lastX, lastY, 10f, glowPaint)

        val dotPaint = Paint().apply {
            color = curveColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(lastX, lastY, 5.5f, dotPaint)

        val dotCenterPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(lastX, lastY, 2.5f, dotCenterPaint)

        return bitmap
    }

    private fun getMainPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNightstandPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.tirup.app.data.receiver.AlertActionReceiver::class.java).apply {
            action = com.tirup.app.data.receiver.AlertActionReceiver.ACTION_LAUNCH_DIANIGHT
        }
        return PendingIntent.getBroadcast(
            context,
            202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
