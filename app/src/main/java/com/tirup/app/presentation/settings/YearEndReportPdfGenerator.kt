package com.tirup.app.presentation.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tirup.app.domain.model.PatientProfile
import com.tirup.app.domain.model.localizeDiabetesType
import com.tirup.app.domain.model.localizeTherapyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class YearEndStats(
    val year: Int,
    val totalReadings: Int,
    val monitoringDays: Int,
    val tirPercent: Double,
    val tarPercent: Double,
    val tbrPercent: Double,
    val meanGlucoseMmol: Double,
    val gmiPercent: Double,
    val bestMonthName: String,
    val bestMonthTir: Double,
    val bestStreakDays: Int,
    val isArchived: Boolean
)

class YearEndReportPdfGenerator(private val context: Context) {

    suspend fun generateYearEndReportPdf(
        profile: PatientProfile,
        stats: YearEndStats,
        isRu: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            // A4 page: 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129) // Emerald festive accent
                strokeWidth = 2.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val heroBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(236, 253, 245) // Emerald-50
                style = Paint.Style.FILL
            }
            val heroBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(110, 231, 183) // Emerald-300
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.5f
            }
            val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 38, 38)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(217, 119, 6)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            var y = 42f
            val margin = 36f
            val contentWidth = 595f - margin * 2

            // Header Banner
            val titleText = if (isRu) "🎄 ИТОГИ ${stats.year} ГОДА — TIRUP" else "🎄 YEAR-END SUMMARY ${stats.year} — TIRUP"
            canvas.drawText(titleText, margin, y, titlePaint)
            y += 13f

            val generatedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            val subtitleText = if (isRu) {
                "Годовой аналитический отчёт непрерывного мониторинга глюкозы (CGM) • Создан: $generatedDate"
            } else {
                "Annual continuous glucose monitoring (CGM) report • Generated: $generatedDate"
            }
            canvas.drawText(subtitleText, margin, y, subtitlePaint)
            y += 8f

            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 18f

            // Patient Information Card
            val patientCardHeight = 44f
            val patientRect = RectF(margin, y, margin + contentWidth, y + patientCardHeight)
            canvas.drawRoundRect(patientRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(patientRect, 6f, 6f, cardBorderPaint)

            val patientName = profile.fullName.ifBlank { if (isRu) "Пациент" else "Patient" }
            val dType = if (isRu) localizeDiabetesType(profile.diabetesType, true) else profile.diabetesType
            val tType = if (isRu) localizeTherapyType(profile.therapyType, true) else profile.therapyType

            canvas.drawText(if (isRu) "Пациент: $patientName" else "Patient: $patientName", margin + 12f, y + 16f, boldTextPaint)
            canvas.drawText(if (isRu) "Тип диабета: $dType" else "Type: $dType", margin + 12f, y + 32f, textPaint)
            canvas.drawText(if (isRu) "Терапия: $tType" else "Therapy: $tType", margin + 260f, y + 32f, textPaint)
            canvas.drawText(if (isRu) "Период: 01.01.${stats.year} — 31.12.${stats.year}" else "Period: 01.01.${stats.year} — 31.12.${stats.year}", margin + 380f, y + 16f, textPaint)

            y += patientCardHeight + 16f

            // Hero Metric Card: Annual TIR
            canvas.drawText(if (isRu) "ГОДОВОЙ ТАРГЕТНЫЙ ДИАПАЗОН (TIR)" else "ANNUAL TIME IN RANGE (TIR)", margin, y, sectionPaint)
            y += 10f

            val heroCardHeight = 65f
            val heroRect = RectF(margin, y, margin + contentWidth, y + heroCardHeight)
            canvas.drawRoundRect(heroRect, 8f, 8f, heroBgPaint)
            canvas.drawRoundRect(heroRect, 8f, 8f, heroBorderPaint)

            val heroTirPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(6, 95, 70) // Dark Emerald
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(String.format(Locale.US, "%.1f%%", stats.tirPercent), margin + 18f, y + 42f, heroTirPaint)

            val heroLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(6, 95, 70)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statusGoal = if (stats.tirPercent >= 70.0) {
                if (isRu) "🎯 Цель ВОЗ / ADA (≥70%) ДОСТИГНУТА!" else "🎯 Target ADA goal (≥70%) ACHIEVED!"
            } else {
                if (isRu) "Внимание к целевому диапазону (цель: ≥70%)" else "Attention to target range (goal: ≥70%)"
            }
            canvas.drawText(if (isRu) "Время в целевом диапазоне 3.9 — 10.0 ммоль/л" else "Time in Target Range 3.9 — 10.0 mmol/L", margin + 140f, y + 26f, heroLabelPaint)
            canvas.drawText(statusGoal, margin + 140f, y + 44f, textPaint)

            y += heroCardHeight + 18f

            // Detailed Glycemic Metrics Grid
            canvas.drawText(if (isRu) "КЛЮЧЕВЫЕ МЕТРИКИ КОМПЕНСАЦИИ" else "KEY GLYCEMIC METRICS", margin, y, sectionPaint)
            y += 10f

            val gridHeight = 85f
            val colW = contentWidth / 4f

            for (col in 0 until 4) {
                val colRect = RectF(margin + col * colW, y, margin + (col + 1) * colW - 6f, y + gridHeight)
                canvas.drawRoundRect(colRect, 6f, 6f, cardBgPaint)
                canvas.drawRoundRect(colRect, 6f, 6f, cardBorderPaint)

                when (col) {
                    0 -> {
                        canvas.drawText(if (isRu) "Гипогликемия (<3.9)" else "Hypoglycemia (<3.9)", colRect.left + 10f, y + 18f, boldTextPaint)
                        canvas.drawText(String.format(Locale.US, "%.1f%%", stats.tbrPercent), colRect.left + 10f, y + 42f, if (stats.tbrPercent <= 4.0) greenPaint else redPaint)
                        canvas.drawText(if (isRu) "Цель: <4.0%" else "Goal: <4.0%", colRect.left + 10f, y + 62f, subtitlePaint)
                        canvas.drawText(if (stats.tbrPercent <= 4.0) if (isRu) "✓ В норме" else "✓ Optimal" else if (isRu) "⚠ Риск гипо" else "⚠ High", colRect.left + 10f, y + 74f, textPaint)
                    }
                    1 -> {
                        canvas.drawText(if (isRu) "Гипергликемия (>10)" else "Hyperglycemia (>10)", colRect.left + 10f, y + 18f, boldTextPaint)
                        canvas.drawText(String.format(Locale.US, "%.1f%%", stats.tarPercent), colRect.left + 10f, y + 42f, if (stats.tarPercent <= 25.0) greenPaint else amberPaint)
                        canvas.drawText(if (isRu) "Цель: <25.0%" else "Goal: <25.0%", colRect.left + 10f, y + 62f, subtitlePaint)
                        canvas.drawText(if (stats.tarPercent <= 25.0) if (isRu) "✓ В норме" else "✓ Optimal" else if (isRu) "Выше цели" else "Elevated", colRect.left + 10f, y + 74f, textPaint)
                    }
                    2 -> {
                        canvas.drawText(if (isRu) "Средний сахар" else "Mean Glucose", colRect.left + 10f, y + 18f, boldTextPaint)
                        canvas.drawText(String.format(Locale.US, "%.1f ммоль/л", stats.meanGlucoseMmol), colRect.left + 10f, y + 42f, boldTextPaint)
                        canvas.drawText(if (isRu) "Цель: <7.5 ммоль/л" else "Goal: <7.5 mmol/L", colRect.left + 10f, y + 62f, subtitlePaint)
                        canvas.drawText(if (isRu) "Суточный профиль" else "Daily Profile", colRect.left + 10f, y + 74f, textPaint)
                    }
                    3 -> {
                        canvas.drawText(if (isRu) "Расчётный HbA1c" else "Sensor GMI", colRect.left + 10f, y + 18f, boldTextPaint)
                        canvas.drawText(String.format(Locale.US, "%.1f%%", stats.gmiPercent), colRect.left + 10f, y + 42f, greenPaint)
                        canvas.drawText(if (isRu) "GMI формула ADA" else "ADA GMI Formula", colRect.left + 10f, y + 62f, subtitlePaint)
                        canvas.drawText(if (isRu) "Годовой ориентир" else "Annual Estimate", colRect.left + 10f, y + 74f, textPaint)
                    }
                }
            }

            y += gridHeight + 18f

            // Annual Milestones & Achievements Card
            canvas.drawText(if (isRu) "ДОСТИЖЕНИЯ И АКТИВНОСТЬ МОНИТОРИНГА" else "MONITORING ACTIVITY & MILESTONES", margin, y, sectionPaint)
            y += 10f

            val achieveHeight = 110f
            val achieveRect = RectF(margin, y, margin + contentWidth, y + achieveHeight)
            canvas.drawRoundRect(achieveRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(achieveRect, 6f, 6f, cardBorderPaint)

            var achY = y + 20f
            val daysCoveragePercent = (stats.monitoringDays.toDouble() / 365.0 * 100.0).coerceAtMost(100.0)

            canvas.drawText(if (isRu) "📅 Дней активного мониторинга:" else "📅 Days Active CGM:", margin + 14f, achY, boldTextPaint)
            canvas.drawText("${stats.monitoringDays} из 365 дней (${String.format(Locale.US, "%.1f%%", daysCoveragePercent)} покрытие)", margin + 220f, achY, textPaint)
            achY += 20f

            canvas.drawText(if (isRu) "📈 Всего измерений сенсора:" else "📈 Total Sensor Readings:", margin + 14f, achY, boldTextPaint)
            canvas.drawText("${stats.totalReadings} точек в базе данных", margin + 220f, achY, textPaint)
            achY += 20f

            canvas.drawText(if (isRu) "🏆 Лучший месяц года:" else "🏆 Best Month of Year:", margin + 14f, achY, boldTextPaint)
            canvas.drawText(if (stats.bestMonthName.isNotBlank()) "${stats.bestMonthName} (${String.format(Locale.US, "%.1f%%", stats.bestMonthTir)} TIR)" else "—", margin + 220f, achY, greenPaint)
            achY += 20f

            canvas.drawText(if (isRu) "🔥 Рекордная серия (Streak):" else "🔥 Longest Streak:", margin + 14f, achY, boldTextPaint)
            canvas.drawText(if (isRu) "${stats.bestStreakDays} дн. без выраженной гипогликемии" else "${stats.bestStreakDays} days without severe hypoglycemia", margin + 220f, achY, textPaint)
            achY += 20f

            val archiveStatus = if (stats.isArchived) {
                if (isRu) "✓ Запечатан в неизменяемый архив tirup_readings_${stats.year}.csv" else "✓ Sealed in tirup_readings_${stats.year}.csv"
            } else {
                if (isRu) "В активном хранилище приложения" else "Active local storage"
            }
            canvas.drawText(if (isRu) "📦 Статус годового архива:" else "📦 Archive Status:", margin + 14f, achY, boldTextPaint)
            canvas.drawText(archiveStatus, margin + 220f, achY, subtitlePaint)

            y += achieveHeight + 25f

            // Doctor / Clinical Footer
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 14f

            val footerText1 = if (isRu) {
                "Медицинское примечание: Данный отчёт сформирован приложением TIRUp на основе данных CGM сенсора."
            } else {
                "Clinical Note: This report was generated by TIRUp based on continuous glucose sensor readings."
            }
            val footerText2 = if (isRu) {
                "Показатели GMI и TIR не заменяют регулярную консультацию врача-эндокринолога и лабораторный анализ крови на HbA1c."
            } else {
                "Sensor GMI and TIR metrics do not substitute routine consultation with an endocrinologist and laboratory HbA1c test."
            }
            canvas.drawText(footerText1, margin, y, subtitlePaint)
            y += 12f
            canvas.drawText(footerText2, margin, y, subtitlePaint)

            document.finishPage(page)

            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val file = File(reportsDir, "tirup_year_end_${stats.year}.pdf")
            if (file.exists()) file.delete()

            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document.close()
        }
    }
}
