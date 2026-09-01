package com.tirup.app.presentation.reports

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
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
        val patient = userSettings.patientProfile

        try {
            // Standard A4 dimensions: 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Paints
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 9.5f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 15f
                isFakeBoldText = true
            }
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139) // Slate 500
                textSize = 8.5f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 250, 252) // Slate 50
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240) // Slate 200
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val margin = 32f
            val contentWidth = 595f - (2 * margin)

            // Date Range
            val minTs = readings.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val maxTs = readings.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val dateRangeFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val dateRangeStr = "${dateRangeFmt.format(Date(minTs))} — ${dateRangeFmt.format(Date(maxTs))}"

            val periodName = when (selectedPeriod) {
                TrendPeriod.PERIOD_7D -> if (isRu) "7 Дней" else "7 Days"
                TrendPeriod.PERIOD_14D -> if (isRu) "14 Дней (AGP Стандарт)" else "14 Days (Standard AGP)"
                TrendPeriod.PERIOD_30D -> if (isRu) "30 Дней" else "30 Days"
                TrendPeriod.PERIOD_90D -> if (isRu) "90 Дней" else "90 Days"
                TrendPeriod.PERIOD_YEAR -> if (isRu) "1 Год" else "1 Year"
                TrendPeriod.PERIOD_ALL -> if (isRu) "Всё время" else "All Time"
            }

            // 1. Header Banner
            val headerHeight = 84f
            val headerRect = RectF(margin, 20f, 595f - margin, 20f + headerHeight)
            canvas.drawRoundRect(headerRect, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(headerRect, 8f, 8f, borderPaint)

            val headerTitle = if (isRu) "АМБУЛАТОРНЫЙ ГЛЮКОЗНЫЙ ПРОФИЛЬ (AGP)" else "AMBULATORY GLUCOSE PROFILE (AGP) REPORT"
            canvas.drawText(headerTitle, margin + 12f, 38f, titlePaint)

            // Patient details line
            val pName = if (patient.fullName.isNotBlank()) patient.fullName else "___________________________________"
            val pAge = if (patient.fullName.isNotBlank() || patient.birthYear != 1990) "${patient.calculatedAge} лет" else "_______"
            val pWeight = if (patient.weightKg.isNotBlank()) "${patient.weightKg} кг" else "_______"
            val pHeight = if (patient.heightCm.isNotBlank()) "${patient.heightCm} см" else "_______"
            val pType = patient.diabetesType
            val pDur = "${patient.calculatedDuration} лет"
            val pTherapy = patient.therapyType

            val patientLine = if (isRu) {
                "Пациент: $pName • Возраст: $pAge • Вес: $pWeight • Рост: $pHeight • $pType (стаж $pDur) • $pTherapy"
            } else {
                "Patient: $pName • Age: $pAge • Weight: $pWeight • Height: $pHeight • $pType ($pDur) • $pTherapy"
            }
            canvas.drawText(patientLine, margin + 12f, 55f, subTextPaint)

            val metaLine = if (isRu) {
                "Период: $periodName ($dateRangeStr) • Активное время CGM: ${String.format(Locale.US, "%.1f%%", statistics.activeTimePercent)} • Дней: ${statistics.daysCount} • Измерений: ${statistics.totalCount}"
            } else {
                "Period: $periodName ($dateRangeStr) • Active CGM Time: ${String.format(Locale.US, "%.1f%%", statistics.activeTimePercent)} • Days: ${statistics.daysCount} • Readings: ${statistics.totalCount}"
            }
            canvas.drawText(metaLine, margin + 12f, 70f, subTextPaint)

            val metaLine2 = if (isRu) {
                "Сформировано: ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("ru")).format(Date())} • Локальный оффлайн-движок TIRUp"
            } else {
                "Generated: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date())} • TIRUp Offline Engine"
            }
            canvas.drawText(metaLine2, margin + 12f, 84f, subTextPaint)

            // 2. Metrics & Targets Panel
            val panelTop = 110f
            val panelHeight = 208f
            val col1Width = contentWidth * 0.44f
            val col2Width = contentWidth * 0.53f
            val col2Left = margin + col1Width + (contentWidth * 0.03f)

            // Left: Time in Ranges
            val tirBox = RectF(margin, panelTop, margin + col1Width, panelTop + panelHeight)
            canvas.drawRoundRect(tirBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(tirBox, 8f, 8f, borderPaint)

            val boxTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10.5f
                isFakeBoldText = true
            }

            val tirBoxTitle = if (isRu) "ВРЕМЯ В ДИАПАЗОНАХ (TIR / TING)" else "TIME IN RANGES (TIR / TING)"
            canvas.drawText(tirBoxTitle, margin + 12f, panelTop + 18f, boxTitlePaint)

            val ranges = listOf(
                Pair(if (isRu) "Очень высокий (≥14.0 ммоль/л)" else "Very High (≥14.0 mmol/L)", Pair(statistics.tarVeryHighPercent, Color.rgb(239, 68, 68))),
                Pair(if (isRu) "Высокий (10.1 - 13.9 ммоль/л)" else "High (10.1 - 13.9 mmol/L)", Pair(statistics.tarHighPercent, Color.rgb(245, 158, 11))),
                Pair(if (isRu) "В целевом диапазоне (3.9 - 10.0)" else "Target Range (3.9 - 10.0)", Pair(statistics.tirPercent, Color.rgb(16, 185, 129))),
                Pair(if (isRu) "В узком диапазоне (3.9 - 7.8)" else "Tight Range (3.9 - 7.8)", Pair(statistics.tingPercent, Color.rgb(20, 184, 166))),
                Pair(if (isRu) "Низкий (3.0 - 3.8 ммоль/л)" else "Low (3.0 - 3.8 mmol/L)", Pair(statistics.tbrLowPercent, Color.rgb(245, 158, 11))),
                Pair(if (isRu) "Очень низкий (<3.0 ммоль/л)" else "Very Low (<3.0 mmol/L)", Pair(statistics.tbrVeryLowPercent, Color.rgb(239, 68, 68)))
            )

            var rowY = panelTop + 36f
            ranges.forEach { (label, data) ->
                val (pct, colorInt) = data
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorInt
                    style = Paint.Style.FILL
                }

                canvas.drawText(label, margin + 12f, rowY + 9f, subTextPaint)
                canvas.drawText(String.format(Locale.US, "%.1f%%", pct), margin + col1Width - 42f, rowY + 9f, boxTitlePaint)

                val barWidth = (col1Width - 24f) * (pct.toFloat() / 100f).coerceIn(0.01f, 1f)
                canvas.drawRoundRect(RectF(margin + 12f, rowY + 13f, margin + 12f + barWidth, rowY + 18f), 2.5f, 2.5f, barPaint)

                rowY += 27f
            }

            // Right: Summary Statistics Box (Without duplicated Active Time)
            val summaryBox = RectF(col2Left, panelTop, col2Left + col2Width, panelTop + panelHeight)
            canvas.drawRoundRect(summaryBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(summaryBox, 8f, 8f, borderPaint)

            val summaryTitle = if (isRu) "СТАТИСТИКА ГЛЮКОЗЫ И ЦЕЛИ" else "GLUCOSE STATISTICS & TARGETS"
            canvas.drawText(summaryTitle, col2Left + 12f, panelTop + 18f, boxTitlePaint)

            val nightStr = if (statistics.nightStability.isStable) {
                "Стабильный (TIR ${String.format(Locale.US, "%.1f", statistics.nightStability.tirPercent)}%)"
            } else {
                "Колебания (TIR ${String.format(Locale.US, "%.1f", statistics.nightStability.tirPercent)}%)"
            }

            val statRows = listOf(
                Pair("Средний сахар (Mean):", String.format(Locale.US, "%.1f ммоль/л (Медиана %.1f)", statistics.meanMmol, statistics.medianMmol)),
                Pair("Вариабельность глюкозы (%CV):", String.format(Locale.US, "%.1f%% (Цель ≤36.0%%) • SD: %.2f", statistics.cvPercent, statistics.sdMmol)),
                Pair("Расчётный eA1c (GMI):", String.format(Locale.US, "%.1f%% (%d mmol/mol)", statistics.gmiPercent, statistics.hba1cMmolMol)),
                Pair("Индекс риска GRI (Klonoff 2022):", String.format(Locale.US, "%.1f (%s, цель ≤40.0)", statistics.gri, statistics.griLabel)),
                Pair("Индексы GVI / PGS:", String.format(Locale.US, "GVI %.2f (≤1.20) • PGS %.1f (≤35.0)", statistics.gvi, statistics.pgs)),
                Pair("Ночной профиль (${String.format(Locale.US, "%02d:00", userSettings.nightStartHour)}–${String.format(Locale.US, "%02d:00", userSettings.nightEndHour)}):", nightStr)
            )

            var statY = panelTop + 38f
            statRows.forEach { (title, value) ->
                canvas.drawText(title, col2Left + 12f, statY, subTextPaint)
                canvas.drawText(value, col2Left + 12f, statY + 12f, textPaint)
                statY += 26f
            }

            // 3. AGP 24-Hour Modal Day Chart with 3-Hour Perpendiculars
            val chartTop = 326f
            val chartHeight = 236f
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
            canvas.drawText(agpChartTitle, margin + 12f, chartTop + 18f, boxTitlePaint)
            canvas.drawText(agpChartSub, margin + 12f, chartTop + 30f, subTextPaint)

            val graphLeft = margin + 30f
            val graphRight = 595f - margin - 18f
            val graphTop = chartTop + 42f
            val graphBottom = chartTop + chartHeight - 26f
            val graphWidth = graphRight - graphLeft
            val graphH = graphBottom - graphTop

            val maxMmol = 16f
            fun yPoint(mmol: Double): Float = (graphBottom - (mmol.coerceIn(0.0, maxMmol.toDouble()) / maxMmol) * graphH).toFloat()

            // Target Band (3.9 - 10.0)
            val y39 = yPoint(3.9)
            val y100 = yPoint(10.0)
            val targetBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(209, 250, 229)
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
            canvas.drawText("3.9", margin + 10f, y39 + 4f, subTextPaint)

            // Vertical 3-Hour Perpendiculars
            val vGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 0.8f
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            }
            val hours3 = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
            hours3.forEach { hr ->
                val x = graphLeft + (hr.toFloat() / 24f) * graphWidth
                canvas.drawLine(x, graphTop, x, graphBottom, vGridPaint)
            }

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

            // X Axis 3-hour time ticks
            val hourLabels = listOf("00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00", "21:00", "24:00")
            hourLabels.forEachIndexed { idx, lbl ->
                val x = graphLeft + (idx.toFloat() / 8f) * graphWidth
                canvas.drawText(lbl, x - 10f, graphBottom + 13f, subTextPaint)
            }

            // 4. Clinical Assessment
            val notesTop = 570f
            val notesBox = RectF(margin, notesTop, 595f - margin, 796f)
            canvas.drawRoundRect(notesBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(notesBox, 8f, 8f, borderPaint)

            val notesTitle = if (isRu) "КЛИНИЧЕСКАЯ ОЦЕНКА" else "CLINICAL ASSESSMENT"
            canvas.drawText(notesTitle, margin + 12f, notesTop + 18f, boxTitlePaint)

            val passPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(5, 150, 105)
                textSize = 9f
                isFakeBoldText = true
            }
            val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(217, 119, 6)
                textSize = 9f
                isFakeBoldText = true
            }
            val failPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 38, 38)
                textSize = 9f
                isFakeBoldText = true
            }

            var noteY = notesTop + 34f
            statistics.clinicalSummary.evaluatedMetrics.forEach { item ->
                val pnt = if (item.isMet) passPaint else if (item.isWarning) warnPaint else failPaint
                val line = "${item.symbol} ${item.title}: ${item.valueStr} (${item.targetStr})"
                canvas.drawText(line, margin + 12f, noteY, pnt)
                noteY += 15f
            }

            noteY += 2f
            canvas.drawText("• Заключение: ${statistics.clinicalSummary.overallStatus}", margin + 12f, noteY, textPaint)
            noteY += 15f
            canvas.drawText("• Рекомендация: ${statistics.clinicalSummary.recommendation}", margin + 12f, noteY, subTextPaint)

            // Footer with telegram channel link
            val footerText = "TIRUp Ambulatory Glucose Profile Report • Сгенерировано для медицинских консультаций и самоконтроля • Telegram: @diakia"
            canvas.drawText(footerText, margin, 814f, subTextPaint)

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
