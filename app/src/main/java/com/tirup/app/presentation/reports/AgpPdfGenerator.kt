package com.tirup.app.presentation.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.trends.TrendPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgpPdfGenerator(private val context: Context) {

    suspend fun generateAgpReport(
        readings: List<GlucoseReading>,
        statistics: GlucoseStatistics,
        userSettings: UserSettings,
        selectedPeriod: TrendPeriod = TrendPeriod.PERIOD_14D
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val isRu = userSettings.language.equals("RU", ignoreCase = true)

        try {
            // Standard A4 dimensions in PostScript points: 595 x 842
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Paints
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 11.5f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 17f
                isFakeBoldText = true
            }
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139) // Slate 500
                textSize = 9.5f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(241, 245, 249) // Slate 100
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225) // Slate 300
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val margin = 36f
            val contentWidth = 595f - (2 * margin)

            // Calculate date range of readings
            val minTs = readings.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val maxTs = readings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val dateRangeFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val dateRangeStr = "${dateRangeFmt.format(Date(minTs))} — ${dateRangeFmt.format(Date(maxTs))}"

            val periodName = when (selectedPeriod) {
                TrendPeriod.PERIOD_7D -> if (isRu) "7 Дней" else "7 Days"
                TrendPeriod.PERIOD_14D -> if (isRu) "14 Дней (Стандарт AGP)" else "14 Days (Standard AGP)"
                TrendPeriod.PERIOD_30D -> if (isRu) "30 Дней" else "30 Days"
                TrendPeriod.PERIOD_90D -> if (isRu) "90 Дней" else "90 Days"
                TrendPeriod.PERIOD_YEAR -> if (isRu) "1 Год" else "1 Year"
                TrendPeriod.PERIOD_ALL -> if (isRu) "Всё время" else "All Time"
            }

            // 1. Header Banner
            canvas.drawRoundRect(RectF(margin, 26f, 595f - margin, 96f), 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(RectF(margin, 26f, 595f - margin, 96f), 8f, 8f, borderPaint)

            val headerTitle = if (isRu) "АМБУЛАТОРНЫЙ ГЛЮКОЗНЫЙ ПРОФИЛЬ (AGP)" else "AMBULATORY GLUCOSE PROFILE (AGP) REPORT"
            canvas.drawText(headerTitle, margin + 14f, 48f, titlePaint)

            val metaLine1 = if (isRu) {
                "Период отчёта: $periodName ($dateRangeStr) • Активных дней: ${statistics.daysCount} • Всего точек: ${statistics.totalCount}"
            } else {
                "Report Period: $periodName ($dateRangeStr) • Active Days: ${statistics.daysCount} • Total Points: ${statistics.totalCount}"
            }
            canvas.drawText(metaLine1, margin + 14f, 68f, subTextPaint)

            val metaLine2 = if (isRu) {
                "Сформировано: ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("ru")).format(Date())} | Локальный оффлайн-движок TIRUp"
            } else {
                "Generated: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date())} | Local Offline Engine TIRUp"
            }
            canvas.drawText(metaLine2, margin + 14f, 84f, subTextPaint)

            // 2. Metrics & Targets Panel (Left: TIR Breakdown, Right: Summary Numbers)
            val panelTop = 108f
            val panelHeight = 220f
            val col1Width = contentWidth * 0.45f
            val col2Width = contentWidth * 0.52f
            val col2Left = margin + col1Width + (contentWidth * 0.03f)

            // Left: Time in Range Breakdown Box
            val tirBox = RectF(margin, panelTop, margin + col1Width, panelTop + panelHeight)
            canvas.drawRoundRect(tirBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(tirBox, 8f, 8f, borderPaint)

            val boxTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11.5f
                isFakeBoldText = true
            }

            val tirBoxTitle = if (isRu) "ВРЕМЯ В ДИАПАЗОНАХ (TIR / TING)" else "TIME IN RANGES (TIR / TING)"
            canvas.drawText(tirBoxTitle, margin + 12f, panelTop + 24f, boxTitlePaint)

            val ranges = if (isRu) {
                listOf(
                    Pair("Очень высокий (≥14.0 ммоль/л)", Pair(statistics.tarVeryHighPercent, Color.rgb(239, 68, 68))),
                    Pair("Высокий (10.1 - 13.9 ммоль/л)", Pair(statistics.tarHighPercent, Color.rgb(245, 158, 11))),
                    Pair("В целевом диапазоне (3.9 - 10.0)", Pair(statistics.tirPercent, Color.rgb(16, 185, 129))),
                    Pair("В узком диапазоне (3.9 - 7.8)", Pair(statistics.tingPercent, Color.rgb(20, 184, 166))),
                    Pair("Низкий (3.0 - 3.8 ммоль/л)", Pair(statistics.tbrLowPercent, Color.rgb(245, 158, 11))),
                    Pair("Очень низкий (<3.0 ммоль/л)", Pair(statistics.tbrVeryLowPercent, Color.rgb(239, 68, 68)))
                )
            } else {
                listOf(
                    Pair("Very High (≥14.0 mmol/L)", Pair(statistics.tarVeryHighPercent, Color.rgb(239, 68, 68))),
                    Pair("High (10.1 - 13.9 mmol/L)", Pair(statistics.tarHighPercent, Color.rgb(245, 158, 11))),
                    Pair("In Target Range (3.9 - 10.0)", Pair(statistics.tirPercent, Color.rgb(16, 185, 129))),
                    Pair("Tight Range (3.9 - 7.8)", Pair(statistics.tingPercent, Color.rgb(20, 184, 166))),
                    Pair("Low (3.0 - 3.8 mmol/L)", Pair(statistics.tbrLowPercent, Color.rgb(245, 158, 11))),
                    Pair("Very Low (<3.0 mmol/L)", Pair(statistics.tbrVeryLowPercent, Color.rgb(239, 68, 68)))
                )
            }

            var rowY = panelTop + 46f
            ranges.forEach { (label, data) ->
                val (pct, colorInt) = data
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorInt
                    style = Paint.Style.FILL
                }

                canvas.drawText(label, margin + 12f, rowY + 10f, subTextPaint)
                canvas.drawText(String.format(Locale.US, "%.1f%%", pct), margin + col1Width - 45f, rowY + 10f, boxTitlePaint)

                // Bar
                val barWidth = (col1Width - 24f) * (pct.toFloat() / 100f).coerceIn(0.01f, 1f)
                canvas.drawRoundRect(RectF(margin + 12f, rowY + 14f, margin + 12f + barWidth, rowY + 20f), 3f, 3f, barPaint)

                rowY += 28f
            }

            // Right: Summary Statistics Box
            val summaryBox = RectF(col2Left, panelTop, col2Left + col2Width, panelTop + panelHeight)
            canvas.drawRoundRect(summaryBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(summaryBox, 8f, 8f, borderPaint)

            val summaryTitle = if (isRu) "СТАТИСТИКА ГЛЮКОЗЫ И ЦЕЛИ" else "GLUCOSE STATISTICS & TARGETS"
            canvas.drawText(summaryTitle, col2Left + 12f, panelTop + 24f, boxTitlePaint)

            val statRows = if (isRu) {
                listOf(
                    Pair("Средний сахар (Mean):", String.format(Locale.US, "%.1f ммоль/л (%d мг/дл)", statistics.meanMmol, (statistics.meanMmol * 18.0182).toInt())),
                    Pair("Вариабельность глюкозы (%CV):", String.format(Locale.US, "%.1f%% (Цель ≤36.0%%)", statistics.cvPercent)),
                    Pair("Расчётный HbA1c (GMI):", String.format(Locale.US, "%.1f%%", statistics.gmiPercent)),
                    Pair("Стандартное отклонение (SD):", String.format(Locale.US, "%.2f ммоль/л", statistics.sdMmol)),
                    Pair("Дней данных / Всего измерений:", "${statistics.daysCount} дн. / ${statistics.totalCount} точек"),
                    Pair("Ночной профиль (00:00 - 06:00):", if (statistics.nightStability.isStable) "Стабильный (TIR ${String.format(Locale.US, "%.1f", statistics.nightStability.tirPercent)}%)" else "Обнаружены колебания")
                )
            } else {
                listOf(
                    Pair("Average Glucose (Mean):", String.format(Locale.US, "%.1f mmol/L (%d mg/dL)", statistics.meanMmol, (statistics.meanMmol * 18.0182).toInt())),
                    Pair("Glucose Variability (%CV):", String.format(Locale.US, "%.1f%% (Target ≤36.0%%)", statistics.cvPercent)),
                    Pair("Estimated A1c (GMI):", String.format(Locale.US, "%.1f%%", statistics.gmiPercent)),
                    Pair("Standard Deviation (SD):", String.format(Locale.US, "%.2f mmol/L", statistics.sdMmol)),
                    Pair("Days of Data / Total Readings:", "${statistics.daysCount} days / ${statistics.totalCount} readings"),
                    Pair("Night Profile (00:00 - 06:00):", if (statistics.nightStability.isStable) "Stable (TIR ${String.format(Locale.US, "%.1f", statistics.nightStability.tirPercent)}%)" else "Fluctuations detected")
                )
            }

            var statY = panelTop + 50f
            statRows.forEach { (title, value) ->
                canvas.drawText(title, col2Left + 12f, statY, subTextPaint)
                canvas.drawText(value, col2Left + 12f, statY + 14f, textPaint)
                statY += 30f
            }

            // 3. AGP 24-Hour Modal Percentile Plot (Chart Canvas)
            val chartTop = 344f
            val chartHeight = 240f
            val chartBox = RectF(margin, chartTop, 595f - margin, chartTop + chartHeight)
            canvas.drawRoundRect(chartBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(chartBox, 8f, 8f, borderPaint)

            val agpChartTitle = if (isRu) {
                "СВОДНЫЙ СУТОЧНЫЙ ПРОФИЛЬ AGP (НАЛОЖЕНИЕ ${statistics.daysCount} ДНЕЙ ЗА 24 ЧАСА)"
            } else {
                "COMPOSITE 24-HOUR AGP MODAL PROFILE (${statistics.daysCount} DAYS SUPERIMPOSED)"
            }
            val agpChartSub = if (isRu) {
                "Медиана 50% (зелёная линия), 25–75% межквартильный диапазон и 10–90% перцентильное облако"
            } else {
                "50% Median curve (green), 25–75% interquartile band, and 10–90% percentile cloud"
            }
            canvas.drawText(agpChartTitle, margin + 12f, chartTop + 22f, boxTitlePaint)
            canvas.drawText(agpChartSub, margin + 12f, chartTop + 36f, subTextPaint)

            // Draw AGP Graph inside chart box
            val graphLeft = margin + 35f
            val graphRight = 595f - margin - 20f
            val graphTop = chartTop + 50f
            val graphBottom = chartTop + chartHeight - 30f
            val graphWidth = graphRight - graphLeft
            val graphH = graphBottom - graphTop

            val maxMmol = 16f
            fun yPoint(mmol: Double): Float = (graphBottom - (mmol.coerceIn(0.0, maxMmol.toDouble()) / maxMmol) * graphH).toFloat()

            // Target Band (3.9 - 10.0)
            val y39 = yPoint(3.9)
            val y100 = yPoint(10.0)
            val targetBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(209, 250, 229) // Emerald light
                style = Paint.Style.FILL
            }
            canvas.drawRect(graphLeft, y100, graphRight, y39, targetBandPaint)

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
            }
            canvas.drawLine(graphLeft, y39, graphRight, y39, gridPaint)
            canvas.drawLine(graphLeft, y100, graphRight, y100, gridPaint)
            canvas.drawText("10.0", margin + 6f, y100 + 4f, subTextPaint)
            canvas.drawText("3.9", margin + 12f, y39 + 4f, subTextPaint)

            val agpBins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)
            val validBins = agpBins.filter { it.readingsCount > 0 }

            if (validBins.size >= 2) {
                val p1090Path = Path()
                val p2575Path = Path()
                val p50Path = Path()

                fun xPoint(binIdx: Int): Float = graphLeft + (binIdx.toFloat() / 47f) * graphWidth

                // 10-90
                p1090Path.moveTo(xPoint(validBins.first().binIndex), yPoint(validBins.first().p90))
                validBins.forEach { p1090Path.lineTo(xPoint(it.binIndex), yPoint(it.p90)) }
                validBins.reversed().forEach { p1090Path.lineTo(xPoint(it.binIndex), yPoint(it.p10)) }
                p1090Path.close()

                val p1090Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(40, 16, 185, 129)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(p1090Path, p1090Paint)

                // 25-75
                p2575Path.moveTo(xPoint(validBins.first().binIndex), yPoint(validBins.first().p75))
                validBins.forEach { p2575Path.lineTo(xPoint(it.binIndex), yPoint(it.p75)) }
                validBins.reversed().forEach { p2575Path.lineTo(xPoint(it.binIndex), yPoint(it.p25)) }
                p2575Path.close()

                val p2575Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(80, 16, 185, 129)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(p2575Path, p2575Paint)

                // 50 Median
                p50Path.moveTo(xPoint(validBins.first().binIndex), yPoint(validBins.first().p50))
                validBins.forEach { p50Path.lineTo(xPoint(it.binIndex), yPoint(it.p50)) }

                val p50Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(5, 150, 105)
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawPath(p50Path, p50Paint)
            }

            // X Axis time ticks
            val hourLabels = listOf("00:00", "06:00", "12:00", "18:00", "24:00")
            hourLabels.forEachIndexed { idx, lbl ->
                val x = graphLeft + (idx.toFloat() / 4f) * graphWidth
                canvas.drawText(lbl, x - 12f, graphBottom + 16f, subTextPaint)
            }

            // 4. Clinical Recommendations & Notes Box
            val notesTop = 600f
            val notesBox = RectF(margin, notesTop, 595f - margin, 780f)
            canvas.drawRoundRect(notesBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(notesBox, 8f, 8f, borderPaint)

            val notesTitle = if (isRu) "КЛИНИЧЕСКАЯ ОЦЕНКА И ПРИМЕЧАНИЯ ВРАЧА" else "CLINICAL ASSESSMENT & PHYSICIAN NOTES"
            canvas.drawText(notesTitle, margin + 12f, notesTop + 24f, boxTitlePaint)

            val notes = if (isRu) {
                listOf(
                    "• Целевой диапазон TIR (≥70% в 3.9–10.0 ммоль/л): " + if (statistics.tirPercent >= 70.0) "ДОСТИГНУТ (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})" else "НИЖЕ ЦЕЛИ (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})",
                    "• Риск гипогликемий (<3.9 ммоль/л TBR, норма <4%): " + if ((statistics.tbrLowPercent + statistics.tbrVeryLowPercent) <= 4.0) "БЕЗОПАСНО (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})" else "ПОВЫШЕННЫЙ РИСК (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})",
                    "• Вариабельность сахара (%CV, норма ≤36.0%): " + if (statistics.cvPercent <= 36.0) "СТАБИЛЬНЫЙ (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})" else "ВЫСОКИЕ КОЛЕБАНИЯ (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})"
                )
            } else {
                listOf(
                    "• Primary TIR target (≥70% in 3.9–10.0 mmol/L): " + if (statistics.tirPercent >= 70.0) "ACHIEVED (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})" else "UNDER TARGET (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})",
                    "• Hypoglycemia safety (<3.9 mmol/L TBR target <4%): " + if ((statistics.tbrLowPercent + statistics.tbrVeryLowPercent) <= 4.0) "SAFE (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})" else "ELEVATED RISK (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})",
                    "• Glucose Variability (%CV target ≤36.0%): " + if (statistics.cvPercent <= 36.0) "STABLE (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})" else "HIGH FLUCTUATIONS (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})"
                )
            }

            var noteY = notesTop + 48f
            notes.forEach { note ->
                canvas.drawText(note, margin + 14f, noteY, textPaint)
                noteY += 22f
            }

            // Footer
            val footerText = if (isRu) {
                "Оффлайн-движок TIRUp • 100% локально и конфиденциально • Не заменяет консультацию врача"
            } else {
                "TIRUp Local Offline Engine • 100% Private Health Intelligence • Not a medical diagnosis replacement"
            }
            canvas.drawText(footerText, margin, 810f, subTextPaint)

            document.finishPage(page)

            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val pdfFile = File(reportsDir, "TIRUp_AGP_Report_${System.currentTimeMillis()}.pdf")
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }

            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document.close()
        }
    }
}
