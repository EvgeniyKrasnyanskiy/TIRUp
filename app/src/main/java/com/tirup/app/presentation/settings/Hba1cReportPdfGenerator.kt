package com.tirup.app.presentation.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tirup.app.domain.model.LabHba1cRecord
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

class Hba1cReportPdfGenerator(private val context: Context) {

    suspend fun generateHba1cReportPdf(
        profile: PatientProfile,
        records: List<LabHba1cRecord>,
        sensorGmi: Double?,
        meanGlucoseMmol: Double?,
        tirPercent: Int?,
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
                color = Color.rgb(220, 38, 38) // Crimson / Red accent for HbA1c
                strokeWidth = 2f
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
            val highlightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(254, 242, 242) // Red-50
                style = Paint.Style.FILL
            }
            val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(252, 165, 165) // Red-300
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8f
            }
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bigValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 38, 38)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tableHeadBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            val tableHeadTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tableRowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.5f
            }
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 7.5f
            }

            var y = 36f

            // 1. Header
            canvas.drawText(
                if (isRu) "TIRUp • Выписка лабораторного контроля гликемии" else "TIRUp • Clinical Glycemia & HbA1c Summary",
                40f, y, titlePaint
            )
            y += 14f
            val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText(
                if (isRu) "Сформировано: $nowStr  |  Автономная диа-система мониторинга"
                else "Generated: $nowStr  |  Autonomous CGM Monitoring System",
                40f, y, subtitlePaint
            )
            y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 18f

            // 2. Patient Profile Card
            val profileCard = RectF(40f, y, 555f, y + 46f)
            canvas.drawRoundRect(profileCard, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(profileCard, 8f, 8f, cardBorderPaint)

            val pY = y + 16f
            val patientDisplayName = if (profile.fullName.isNotBlank()) profile.fullName else (if (isRu) "Пациент не указан" else "Patient not specified")
            val pAge = if (profile.birthYear > 1900) "${profile.calculatedAge} ${if (isRu) "лет" else "y.o."}" else "—"
            val pDiab = if (profile.diabetesType.isNotBlank()) localizeDiabetesType(profile.diabetesType, isRu) else "—"
            val pTherapy = if (profile.therapyType.isNotBlank()) localizeTherapyType(profile.therapyType, isRu) else "—"

            canvas.drawText(if (isRu) "Пациент: $patientDisplayName" else "Patient: $patientDisplayName", 52f, pY, valPaint)
            canvas.drawText(if (isRu) "Возраст: $pAge" else "Age: $pAge", 300f, pY, valPaint)
            canvas.drawText(if (isRu) "Диагноз: $pDiab" else "Diagnosis: $pDiab", 410f, pY, valPaint)

            canvas.drawText(if (isRu) "Тип терапии: $pTherapy" else "Therapy: $pTherapy", 52f, pY + 18f, labelPaint)
            if (profile.heightCm.isNotBlank() && profile.weightKg.isNotBlank()) {
                val bmi = profile.calculatedBmi
                val bmiText = if (bmi != null) String.format(Locale.US, "%.1f", bmi) else "—"
                canvas.drawText(if (isRu) "Рост: ${profile.heightCm} см  |  Вес: ${profile.weightKg} кг  |  ИМТ: $bmiText"
                                else "Height: ${profile.heightCm} cm  |  Weight: ${profile.weightKg} kg  |  BMI: $bmiText",
                    220f, pY + 18f, labelPaint)
            }
            y += 58f

            // 3. Clinical Metrics Comparison Block (Latest Lab HbA1c vs CGM GMI)
            canvas.drawText(if (isRu) "Сопоставление: Лабораторный HbA1c и данные CGM (90 дней)" else "Comparison: Laboratory HbA1c & 90-day CGM Data", 40f, y, sectionPaint)
            y += 12f

            val compareCard = RectF(40f, y, 555f, y + 84f)
            canvas.drawRoundRect(compareCard, 8f, 8f, highlightBgPaint)
            canvas.drawRoundRect(compareCard, 8f, 8f, highlightBorderPaint)

            val sortedRecords = records.sortedByDescending { it.timestamp }
            val latest = sortedRecords.firstOrNull()

            // Left column: Latest Lab HbA1c
            val cY = y + 20f
            canvas.drawText(if (isRu) "ПОСЛЕДНИЙ АНАЛИЗ КРОВИ" else "LATEST LABORATORY TEST", 54f, cY, labelPaint)
            val hba1cStr = if (latest != null && latest.valuePercent > 0.0) "${String.format(Locale.US, "%.1f", latest.valuePercent)}%" else "—"
            canvas.drawText(hba1cStr, 54f, cY + 26f, bigValPaint)

            val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val labDateStr = if (latest != null && latest.timestamp > 0L) dateFmt.format(Date(latest.timestamp)) else "—"
            val labNameStr = if (latest != null && latest.labName.isNotBlank()) " (${latest.labName})" else ""
            canvas.drawText(if (isRu) "Дата: $labDateStr$labNameStr" else "Date: $labDateStr$labNameStr", 54f, cY + 44f, labelPaint)

            // Middle column: Sensor 90-day GMI
            canvas.drawText(if (isRu) "РАСЧЁТНЫЙ GMI (СЕНСОР 90 ДН.)" else "ESTIMATED GMI (90-DAY CGM)", 230f, cY, labelPaint)
            val gmiStr = if (sensorGmi != null && sensorGmi > 0.0) "${String.format(Locale.US, "%.1f", sensorGmi)}%" else "—"
            canvas.drawText(gmiStr, 230f, cY + 26f, bigValPaint)

            val meanStr = if (meanGlucoseMmol != null && meanGlucoseMmol > 0.0) "${String.format(Locale.US, "%.1f", meanGlucoseMmol)} ммоль/л" else "—"
            val tirStr = if (tirPercent != null && tirPercent > 0) "$tirPercent%" else "—"
            canvas.drawText(if (isRu) "Ср. сахар: $meanStr  |  TIR: $tirStr" else "Mean: $meanStr  |  TIR: $tirStr", 230f, cY + 44f, labelPaint)

            // Right column: Convergence delta Δ
            canvas.drawText(if (isRu) "РАСХОЖДЕНИЕ Δ" else "CONVERGENCE Δ", 430f, cY, labelPaint)
            if (latest != null && latest.valuePercent > 0.0 && sensorGmi != null && sensorGmi > 0.0) {
                val delta = latest.valuePercent - sensorGmi
                val deltaSign = if (delta >= 0) "+${String.format(Locale.US, "%.1f", delta)}%" else "${String.format(Locale.US, "%.1f", delta)}%"
                canvas.drawText(deltaSign, 430f, cY + 26f, bigValPaint)

                val absDelta = kotlin.math.abs(delta)
                val statusStr = when {
                    absDelta <= 0.5 -> if (isRu) "Высокая сходимость" else "High agreement"
                    absDelta <= 1.0 -> if (isRu) "Умеренная сходимость" else "Moderate agreement"
                    else -> if (isRu) "Значимое расхождение" else "Notable difference"
                }
                canvas.drawText(statusStr, 430f, cY + 44f, labelPaint)
            } else {
                canvas.drawText("—", 430f, cY + 26f, bigValPaint)
                canvas.drawText(if (isRu) "Недостаточно данных" else "Insufficient data", 430f, cY + 44f, labelPaint)
            }
            y += 98f

            // 4. Clinical Guidance note
            val noteCard = RectF(40f, y, 555f, y + 42f)
            canvas.drawRoundRect(noteCard, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(noteCard, 6f, 6f, cardBorderPaint)
            val guidanceText = if (isRu)
                "Примечание: Лабораторный HbA1c отражает связывание глюкозы с гемоглобином за ~90–120 дней.\n" +
                "GMI (Glucose Management Indicator) рассчитан по формуле GMI = 3.31 + 0.431 * (Ср. сахар, ммоль/л)."
            else
                "Note: Laboratory HbA1c represents glycation of hemoglobin over 90–120 days.\n" +
                "GMI (Glucose Management Indicator) formula: GMI = 3.31 + 0.431 * (Mean Glucose in mmol/L)."
            val lines = guidanceText.split("\n")
            var noteY = y + 14f
            for (line in lines) {
                canvas.drawText(line, 52f, noteY, notePaint)
                noteY += 12f
            }
            y += 56f

            // 5. History Table of Laboratory Tests
            canvas.drawText(if (isRu) "Журнал лабораторных анализов HbA1c" else "Laboratory HbA1c History Log", 40f, y, sectionPaint)
            y += 12f

            // Table Header
            val tableHead = RectF(40f, y, 555f, y + 22f)
            canvas.drawRoundRect(tableHead, 4f, 4f, tableHeadBgPaint)
            val headY = y + 14f
            canvas.drawText(if (isRu) "Дата сдачи" else "Date", 50f, headY, tableHeadTextPaint)
            canvas.drawText(if (isRu) "HbA1c (%)" else "HbA1c (%)", 140f, headY, tableHeadTextPaint)
            canvas.drawText(if (isRu) "Лаборатория" else "Laboratory", 230f, headY, tableHeadTextPaint)
            canvas.drawText(if (isRu) "Заметки и комментарии" else "Notes / Comments", 360f, headY, tableHeadTextPaint)
            y += 24f

            if (sortedRecords.isEmpty()) {
                val emptyRect = RectF(40f, y, 555f, y + 36f)
                canvas.drawRoundRect(emptyRect, 4f, 4f, cardBgPaint)
                canvas.drawText(
                    if (isRu) "Нет сохранённых записей анализов крови" else "No laboratory HbA1c records stored",
                    210f, y + 22f, labelPaint
                )
                y += 44f
            } else {
                for ((index, rec) in sortedRecords.take(15).withIndex()) {
                    val rowY = y + 16f
                    val isEven = index % 2 == 0
                    if (isEven) {
                        val rowRect = RectF(40f, y, 555f, y + 22f)
                        canvas.drawRect(rowRect, cardBgPaint)
                    }

                    val recDateStr = dateFmt.format(Date(rec.timestamp))
                    canvas.drawText(recDateStr, 50f, rowY, tableRowTextPaint)
                    canvas.drawText("${String.format(Locale.US, "%.1f", rec.valuePercent)}%", 140f, rowY, valPaint)
                    canvas.drawText(if (rec.labName.isNotBlank()) rec.labName else "—", 230f, rowY, tableRowTextPaint)
                    canvas.drawText(if (rec.notes.isNotBlank()) rec.notes else "—", 360f, rowY, tableRowTextPaint)
                    y += 22f
                }
            }

            // 6. Footer disclaimer
            val footerY = 810f
            canvas.drawLine(40f, footerY - 8f, 555f, footerY - 8f, cardBorderPaint)
            canvas.drawText(
                if (isRu) "Документ сформирован мобильным приложением TIRUp для предоставления лечащему врачу-эндокринологу."
                else "Document generated by TIRUp application for endocrinologist consultation.",
                40f, footerY + 6f, notePaint
            )
            canvas.drawText("Стр. 1 из 1", 510f, footerY + 6f, notePaint)

            document.finishPage(page)

            // Save to cache/reports folder
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val fileName = "TIRUp_HbA1c_Report_${System.currentTimeMillis()}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document.close()
        }
    }
}
