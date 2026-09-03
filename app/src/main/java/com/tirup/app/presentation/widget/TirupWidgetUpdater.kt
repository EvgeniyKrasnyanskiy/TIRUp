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

            if (stripIds.isEmpty() && dashboardIds.isEmpty() && compactIds.isEmpty()) {
                return@withContext
            }

            val app = context.applicationContext as? TirupApplication ?: return@withContext
            val settings = app.settingsRepository.getSettings().first()
            val latest = app.glucoseRepository.getLatestReading().first()
            val recent = app.glucoseRepository.getRecentReadings(120).first()

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

            // Update 4x1 / 5x1 Strip Widgets
            if (stripIds.isNotEmpty()) {
                val views = buildStripViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(stripIds, views)
            }

            // Update 4x2 / 5x2 Dashboard Widgets
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

            // Update 2x2 Compact Widgets
            if (compactIds.isNotEmpty()) {
                val views = buildCompactViews(
                    context = context,
                    latest = latest,
                    recent = recent,
                    todayReadings = todayReadings,
                    settings = settings,
                    mainIntent = mainPendingIntent,
                    nightstandIntent = nightstandPendingIntent
                )
                appWidgetManager.updateAppWidget(compactIds, views)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating TIRUp homescreen widgets", e)
        }
    }

    private fun buildStripViews(
        context: Context,
        latest: GlucoseReading?,
        recent: List<GlucoseReading>,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings,
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_strip)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setTextViewText(R.id.widget_time_ago, "Нет данных")
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setTextViewText(R.id.widget_compensator_text, "Ожидание данных CGM")
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_iob_cob_layout, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)
        bindCompensator(views, latest, todayReadings, settings)

        // IoB / CoB for 5x1
        bindIobCob(views, latest)

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
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setTextViewText(R.id.widget_time_ago, "Нет данных")
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            views.setTextViewText(R.id.widget_compensator_text, "Ожидание данных CGM")
            views.setProgressBar(R.id.widget_tir_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)
        bindCompensator(views, latest, todayReadings, settings)

        // Streak badge
        val isRu = settings.language.equals("RU", ignoreCase = true)
        if (streakDays > 0) {
            views.setViewVisibility(R.id.widget_streak_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_streak_text, if (isRu) "🔥 $streakDays дн" else "🔥 $streakDays d")
        } else {
            views.setViewVisibility(R.id.widget_streak_text, View.GONE)
        }

        // IoB
        val iob = latest.iob ?: 0.0
        if (iob > 0.05) {
            views.setViewVisibility(R.id.widget_iob_text, View.VISIBLE)
            views.setTextViewText(R.id.widget_iob_text, String.format(Locale.US, "💉 %.1f U", iob))
        } else {
            views.setViewVisibility(R.id.widget_iob_text, View.GONE)
        }

        // Sparkline Canvas Chart
        val chartBitmap = drawSparklineBitmap(
            readings = recent,
            latest = latest,
            widthPx = 640,
            heightPx = 160,
            ranges = settings.targetRanges
        )
        if (chartBitmap != null) {
            views.setImageViewBitmap(R.id.widget_chart_image, chartBitmap)
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
        mainIntent: PendingIntent,
        nightstandIntent: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact)
        views.setOnClickPendingIntent(R.id.widget_root, mainIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_nightstand, nightstandIntent)

        if (latest == null) {
            views.setTextViewText(R.id.widget_glucose_value, "--")
            views.setTextViewText(R.id.widget_trend_arrow, "")
            views.setTextViewText(R.id.widget_delta_value, "--")
            views.setTextViewText(R.id.widget_time_ago, "Нет данных")
            views.setTextViewText(R.id.widget_tir_score, "TIR: --")
            return views
        }

        bindCommonMetrics(views, latest, recent, settings)

        // Calculate TIR percent for badge
        val inRangeCount = todayReadings.count {
            it.valueMmol in settings.targetRanges.tirLowMmol..settings.targetRanges.tirHighMmol
        }
        val tirPercent = if (todayReadings.isNotEmpty()) {
            (inRangeCount * 100.0 / todayReadings.size).roundToInt()
        } else 0
        views.setTextViewText(R.id.widget_tir_score, "TIR: $tirPercent%")

        return views
    }

    private fun bindCommonMetrics(
        views: RemoteViews,
        latest: GlucoseReading,
        recent: List<GlucoseReading>,
        settings: UserSettings
    ) {
        val isMmol = settings.unit == GlucoseUnit.MMOL_L
        val isRu = settings.language.equals("RU", ignoreCase = true)
        val now = System.currentTimeMillis()

        // 1. Glucose Value
        val glucoseStr = if (isMmol) {
            String.format(Locale.US, "%.1f", latest.valueMmol)
        } else {
            "${(latest.valueMmol * 18.0182).roundToInt()}"
        }
        views.setTextViewText(R.id.widget_glucose_value, glucoseStr)

        // Color based on range and age
        val diffMs = (now - latest.timestamp).coerceAtLeast(0L)
        val diffMin = (diffMs / 60_000L).toInt()
        val isStale = diffMin > 5

        val glucoseColor = when {
            isStale -> Color.parseColor("#94A3B8") // Slate gray
            latest.valueMmol < 3.0 -> Color.parseColor("#EF4444") // Critical hypo
            latest.valueMmol < settings.targetRanges.tirLowMmol -> Color.parseColor("#F87171") // Mild hypo
            latest.valueMmol <= settings.targetRanges.tirHighMmol -> Color.parseColor("#10B981") // In range
            latest.valueMmol >= 14.0 -> Color.parseColor("#EF4444") // Critical hyper
            else -> Color.parseColor("#F59E0B") // High
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

        // 4. Time Ago
        val timeAgoStr = when {
            diffMin <= 1 -> if (isRu) "Только что" else "Just now"
            diffMin < 60 -> if (isRu) "${diffMin}м назад" else "${diffMin}m ago"
            else -> {
                val h = diffMin / 60
                val m = diffMin % 60
                if (isRu) "${h}ч ${m}м назад" else "${h}h ${m}m ago"
            }
        }
        views.setTextViewText(R.id.widget_time_ago, timeAgoStr)
    }

    private fun bindCompensator(
        views: RemoteViews,
        latest: GlucoseReading,
        todayReadings: List<GlucoseReading>,
        settings: UserSettings
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

        views.setTextViewText(R.id.widget_tir_score, "$targetName: $currentPercent%")
        views.setProgressBar(R.id.widget_tir_progress, 100, currentPercent.coerceIn(0, 100), false)

        val recText = if (isRu) compensator.recommendationRu else compensator.recommendationEn
        views.setTextViewText(R.id.widget_compensator_text, recText)
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
     * Calculates the clinical 5-minute velocity delta.
     * Looks for reading in a window of 3.5 to 7.5 minutes ago to ensure stability
     * for both 1-minute and 5-minute sensors.
     */
    fun calculate5MinDelta(latest: GlucoseReading, readings: List<GlucoseReading>): Double? {
        if (readings.size < 2) return null
        val targetTimestamp = latest.timestamp - 5 * 60_000L

        // Look for reading closest to targetTimestamp in window [3.5 min .. 7.5 min]
        val candidate = readings
            .filter { it.timestamp in (targetTimestamp - 120_000L)..(targetTimestamp + 120_000L) && it.timestamp != latest.timestamp }
            .minByOrNull { abs(it.timestamp - targetTimestamp) }

        val referenceReading = candidate ?: readings
            .filter { it.timestamp < latest.timestamp }
            .maxByOrNull { it.timestamp }

        return if (referenceReading != null) {
            latest.valueMmol - referenceReading.valueMmol
        } else null
    }

    /**
     * Draws a high-definition, anti-aliased Sparkline bitmap of glucose history
     * over the last 3.5 hours with a shaded green target corridor.
     */
    fun drawSparklineBitmap(
        readings: List<GlucoseReading>,
        latest: GlucoseReading,
        widthPx: Int,
        heightPx: Int,
        ranges: TargetRanges
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val windowMs = 12_600_000L // 3.5 hours window in milliseconds
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

        val paddingLeft = 12f
        val paddingRight = 18f
        val paddingTop = 10f
        val paddingBottom = 10f

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

        // 3. Draw Trajectory Line
        val curveColor = when {
            latest.valueMmol < low -> Color.parseColor("#F87171")
            latest.valueMmol > high -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#10B981")
        }

        val linePaint = Paint().apply {
            color = curveColor
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val path = Path()
        var first = true
        for (reading in windowReadings) {
            val x = getX(reading.timestamp)
            val y = getY(reading.valueMmol)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, linePaint)

        // 4. Draw Current Glucose Glowing Head Dot
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
        val pm = context.packageManager
        val nightstandIntent = pm.getLaunchIntentForPackage("com.diaclock.nightstand")
        val intent = if (nightstandIntent != null) {
            nightstandIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        } else {
            Intent(context, MainActivity::class.java).apply {
                putExtra("EXTRA_OPEN_NIGHTSTAND", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        return PendingIntent.getActivity(
            context,
            202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
