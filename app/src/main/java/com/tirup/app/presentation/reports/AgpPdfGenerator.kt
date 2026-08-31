package com.tirup.app.presentation.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.tirup.app.domain.calculator.AGPPercentilesCalculator
import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.TargetRanges
import com.tirup.app.domain.model.UserSettings
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
        patientName: String = "TIRUp Patient Profile"
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        try {
            // Standard A4 dimensions in PostScript points: 595 x 842
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Paints
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 12f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 18f
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
            val primaryEmeraldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129)
                style = Paint.Style.FILL
            }

            val margin = 36f
            val contentWidth = 595f - (2 * margin)

            // 1. Header Banner
            canvas.drawRoundRect(RectF(margin, 30f, 595f - margin, 90f), 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(RectF(margin, 30f, 595f - margin, 90f), 8f, 8f, borderPaint)

            canvas.drawText("AMBULATORY GLUCOSE PROFILE (AGP) REPORT", margin + 14f, 54f, titlePaint)
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dateStr = "Report Generated: ${dateFormat.format(Date())} | Local Offline Engine"
            canvas.drawText(dateStr, margin + 14f, 74f, subTextPaint)

            // 2. Metrics & Targets Panel (Left: TIR Breakdown, Right: Summary Numbers)
            val panelTop = 104f
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
                textSize = 12f
                isFakeBoldText = true
            }
            canvas.drawText("TIME IN RANGES (TIR / TING)", margin + 12f, panelTop + 24f, boxTitlePaint)

            // Draw range bars & percentages
            val ranges = listOf(
                Pair("Very High (≥14.0 mmol/L)", Pair(statistics.tarVeryHighPercent, Color.rgb(239, 68, 68))),
                Pair("High (10.1 - 13.9 mmol/L)", Pair(statistics.tarHighPercent, Color.rgb(245, 158, 11))),
                Pair("In Target Range (3.9 - 10.0)", Pair(statistics.tirPercent, Color.rgb(16, 185, 129))),
                Pair("Tight Range (3.9 - 7.8)", Pair(statistics.tingPercent, Color.rgb(20, 184, 166))),
                Pair("Low (3.0 - 3.8 mmol/L)", Pair(statistics.tbrLowPercent, Color.rgb(245, 158, 11))),
                Pair("Very Low (<3.0 mmol/L)", Pair(statistics.tbrVeryLowPercent, Color.rgb(239, 68, 68)))
            )

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

            canvas.drawText("GLUCOSE STATISTICS & TARGETS", col2Left + 12f, panelTop + 24f, boxTitlePaint)

            val statRows = listOf(
                Pair("Average Glucose (Mean):", String.format(Locale.US, "%.1f mmol/L (%d mg/dL)", statistics.meanMmol, (statistics.meanMmol * 18.0182).toInt())),
                Pair("Glucose Variability (%CV):", String.format(Locale.US, "%.1f%% (Target ≤36.0%%)", statistics.cvPercent)),
                Pair("Estimated A1c (GMI):", String.format(Locale.US, "%.1f%%", statistics.gmiPercent)),
                Pair("Standard Deviation (SD):", String.format(Locale.US, "%.2f mmol/L", statistics.sdMmol)),
                Pair("Days of Data / Total Readings:", "${statistics.daysCount} days / ${statistics.totalCount} readings"),
                Pair("Night Profile (00:00 - 06:00):", if (statistics.nightStability.isStable) "Stable (TIR ${String.format(Locale.US, "%.1f", statistics.nightStability.tirPercent)}%)" else "Fluctuations detected")
            )

            var statY = panelTop + 50f
            statRows.forEach { (title, value) ->
                canvas.drawText(title, col2Left + 12f, statY, subTextPaint)
                canvas.drawText(value, col2Left + 12f, statY + 14f, textPaint)
                statY += 30f
            }

            // 3. AGP 24-Hour Modal Percentile Plot (Chart Canvas)
            val chartTop = 340f
            val chartHeight = 240f
            val chartBox = RectF(margin, chartTop, 595f - margin, chartTop + chartHeight)
            canvas.drawRoundRect(chartBox, 8f, 8f, headerBgPaint)
            canvas.drawRoundRect(chartBox, 8f, 8f, borderPaint)

            canvas.drawText("24-HOUR AMBULATORY GLUCOSE PROFILE (MODAL DAY)", margin + 12f, chartTop + 22f, boxTitlePaint)
            canvas.drawText("50% Median curve with 25-75% and 10-90% percentile cloud", margin + 12f, chartTop + 36f, subTextPaint)

            // Draw AGP Graph inside chart box
            val graphLeft = margin + 35f
            val graphRight = 595f - margin - 20f
            val graphTop = chartTop + 50f
            val graphBottom = chartTop + chartHeight - 30f
            val graphWidth = graphRight - graphLeft
            val graphH = graphBottom - graphTop

            val maxMmol = 16f
            fun yPoint(mmol: Double): Float = (graphBottom - (mmol.coerceIn(0.0, maxMmol.toDouble()) / maxMmol) * graphH).toFloat()

            // Draw Target Range Band (3.9 - 10.0)
            val y39 = yPoint(3.9)
            val y100 = yPoint(10.0)
            val targetBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(209, 250, 229) // Emerald light
                style = Paint.Style.FILL
            }
            canvas.drawRect(graphLeft, y100, graphRight, y39, targetBandPaint)

            // Grid lines & labels
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
            }
            canvas.drawLine(graphLeft, y39, graphRight, y39, gridPaint)
            canvas.drawLine(graphLeft, y100, graphRight, y100, gridPaint)
            canvas.drawText("10.0", margin + 6f, y100 + 4f, subTextPaint)
            canvas.drawText("3.9", margin + 12f, y39 + 4f, subTextPaint)

            val agpBins = AGPPercentilesCalculator.calculatePercentiles(readings, binsCount = 48)

            if (readings.isNotEmpty()) {
                val p1090Path = Path()
                val p2575Path = Path()
                val p50Path = Path()

                fun xPoint(binIdx: Int): Float = graphLeft + (binIdx.toFloat() / 47f) * graphWidth

                // 10-90
                p1090Path.moveTo(xPoint(0), yPoint(agpBins[0].p90))
                agpBins.forEach { p1090Path.lineTo(xPoint(it.binIndex), yPoint(it.p90)) }
                agpBins.reversed().forEach { p1090Path.lineTo(xPoint(it.binIndex), yPoint(it.p10)) }
                p1090Path.close()

                val p1090Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(40, 16, 185, 129)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(p1090Path, p1090Paint)

                // 25-75
                p2575Path.moveTo(xPoint(0), yPoint(agpBins[0].p75))
                agpBins.forEach { p2575Path.lineTo(xPoint(it.binIndex), yPoint(it.p75)) }
                agpBins.reversed().forEach { p2575Path.lineTo(xPoint(it.binIndex), yPoint(it.p25)) }
                p2575Path.close()

                val p2575Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(80, 16, 185, 129)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(p2575Path, p2575Paint)

                // 50 Median
                p50Path.moveTo(xPoint(0), yPoint(agpBins[0].p50))
                agpBins.forEach { p50Path.lineTo(xPoint(it.binIndex), yPoint(it.p50)) }

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

            canvas.drawText("CLINICAL ASSESSMENT & PHYSICIAN NOTES", margin + 12f, notesTop + 24f, boxTitlePaint)

            val notes = listOf(
                "• Primary TIR target (≥70% in 3.9–10.0 mmol/L): " + if (statistics.tirPercent >= 70.0) "ACHIEVED (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})" else "UNDER TARGET (${String.format(Locale.US, "%.1f%%", statistics.tirPercent)})",
                "• Hypoglycemia safety (<3.9 mmol/L TBR target <4%): " + if ((statistics.tbrLowPercent + statistics.tbrVeryLowPercent) <= 4.0) "SAFE (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})" else "ELEVATED RISK (${String.format(Locale.US, "%.1f%%", statistics.tbrLowPercent + statistics.tbrVeryLowPercent)})",
                "• Glucose Variability (%CV target ≤36.0%): " + if (statistics.cvPercent <= 36.0) "STABLE (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})" else "HIGH FLUCTUATIONS (${String.format(Locale.US, "%.1f%%", statistics.cvPercent)})"
            )

            var noteY = notesTop + 48f
            notes.forEach { note ->
                canvas.drawText(note, margin + 14f, noteY, textPaint)
                noteY += 22f
            }

            // Footer
            canvas.drawText("TIRUp Local Offline Engine • 100% Private Health Intelligence • Not a medical diagnosis replacement", margin, 810f, subTextPaint)

            document.finishPage(page)

            // Save PDF to cache dir for sharing
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
