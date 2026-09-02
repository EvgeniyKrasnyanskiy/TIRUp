package com.tirup.app.presentation.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GuidebookPdfGenerator(private val context: Context) {

    suspend fun generateGuidebookPdf(isRu: Boolean): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            // A4 dimensions: 595 x 842 points
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
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129) // PrimaryEmerald
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 8f
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
            val warningBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(254, 243, 199) // Amber 100
                style = Paint.Style.FILL
            }
            val warningBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 158, 11) // Amber 500
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val warningTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(146, 64, 14) // Amber 800
                textSize = 7.5f
            }

            var y = 35f

            // Header Banner
            canvas.drawText(
                if (isRu) "TIRUp • Справочник параметров гликемического профиля" else "TIRUp • Glycemic Profile Parameters Guide",
                30f,
                y,
                titlePaint
            )
            y += 14f
            canvas.drawText(
                if (isRu) "Клиническое руководство по интерпретации данных непрерывного мониторинга (CGM)" else "Clinical interpretation guide for continuous glucose monitoring data (CGM)",
                30f,
                y,
                subtitlePaint
            )
            y += 18f

            fun drawSection(title: String, items: List<Pair<String, String>>) {
                canvas.drawText(title, 30f, y, sectionPaint)
                y += 12f

                for ((itemTitle, itemDesc) in items) {
                    val rect = RectF(30f, y, 565f, y + 34f)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBorderPaint)

                    canvas.drawText(itemTitle, 38f, y + 12f, itemTitlePaint)

                    // Word wrap description up to 2 lines
                    val words = itemDesc.split(" ")
                    var line = ""
                    var lineY = y + 23f
                    for (w in words) {
                        val testLine = if (line.isEmpty()) w else "$line $w"
                        if (bodyPaint.measureText(testLine) < 515f) {
                            line = testLine
                        } else {
                            canvas.drawText(line, 38f, lineY, bodyPaint)
                            line = w
                            lineY += 9f
                            if (lineY > y + 32f) break
                        }
                    }
                    if (line.isNotEmpty() && lineY <= y + 32f) {
                        canvas.drawText(line, 38f, lineY, bodyPaint)
                    }

                    y += 38f
                }
                y += 4f
            }

            // Section 1
            drawSection(
                if (isRu) "1. Основные показатели контроля" else "1. Core Glycemic Control Metrics",
                listOf(
                    Pair(
                        if (isRu) "Mean BG (Средняя гликемия)" else "Mean BG (Average Glucose)",
                        if (isRu) "Средний уровень глюкозы за анализируемый период (цель: ≤7.8 ммоль/л / ≤140 мг/дл). Показывает общий генеральный тренд гликемии."
                        else "Average glucose over the period (target: ≤7.8 mmol/L / ≤140 mg/dL). Reflects overall glycemic trajectory."
                    ),
                    Pair(
                        if (isRu) "eA1c (Расчётный гликированный гемоглобин)" else "eA1c (Estimated A1c)",
                        if (isRu) "Математическая экстраполяция лабораторного HbA1c по формуле ADAG (цель: ≤7.0%). Отражает средний уровень сахара за последние недели."
                        else "Mathematical extrapolation of laboratory HbA1c (target: ≤7.0%). Correlates with multi-week average glucose."
                    )
                )
            )

            // Section 2
            drawSection(
                if (isRu) "2. Время в целевых диапазонах (Time in Range)" else "2. Time in Range Metrics",
                listOf(
                    Pair(
                        if (isRu) "TIR (3.9–10.0 ммоль/л, норма ≥70%)" else "TIR (70–180 mg/dL, target ≥70%)",
                        if (isRu) "Главный маркер компенсации. Увеличение TIR на каждые 10% существенно снижает риск ретинопатии, нефропатии и нейропатии."
                        else "Primary clinical target. Every 10% increase in TIR substantially lowers vascular complication risks."
                    ),
                    Pair(
                        if (isRu) "TING (3.9–7.8 ммоль/л, норма ≥50%)" else "TING (70–140 mg/dL, target ≥50%)",
                        if (isRu) "Время в идеальной норме здорового человека (Tight in Normal Glycemia). Помогает оценить тонкую ювелирную настройку терапии."
                        else "Time in tight physiological range. Helps assess advanced fine-tuning of insulin regimens and diets."
                    ),
                    Pair(
                        if (isRu) "TBR (<3.9 ммоль/л: норма <4%, <3.0 ммоль/л: <1%)" else "TBR (<70 mg/dL: <4%, <54 mg/dL: <1%)",
                        if (isRu) "Критический параметр безопасности. Гипогликемия вызывает аритмии и когнитивные расстройства. Время <3.0 должно быть сведено к нулю."
                        else "Safety-critical index. Hypoglycemia directly triggers cardiac arrhythmias and neurological deficits."
                    ),
                    Pair(
                        if (isRu) "TAR (>10.0 ммоль/л: норма <25%, >13.9 ммоль/л: <5%)" else "TAR (>180 mg/dL: <25%, >250 mg/dL: <5%)",
                        if (isRu) "Время в гипергликемии. Высокий сахар повреждает эндотелий сосудов, ведёт к дегидратации и накоплению токсичных метаболитов."
                        else "Time in hyperglycemia. Sustained high glucose drives oxidative stress, endothelial damage, and microvascular decline."
                    )
                )
            )

            // Section 3
            drawSection(
                if (isRu) "3. Вариабельность и качество кривой" else "3. Variability & Curve Smoothness",
                listOf(
                    Pair(
                        if (isRu) "Вариабельность (%CV ≤36%, SD ≤2.0 ммоль/л)" else "Variability (%CV ≤36%, SD ≤36 mg/dL)",
                        if (isRu) "Коэффициент вариации и стандартное отклонение. Высокий %CV (>36%) создаёт опасный окислительный стресс эндотелия сосудов."
                        else "Coefficient of variation and standard deviation. High CV (>36%) drives profound vascular endothelial stress."
                    ),
                    Pair(
                        if (isRu) "GVI (Лабильность, идеал ≤1.20) и GRI (Индекс риска ≤20)" else "GVI (Lability ≤1.20) & GRI (Risk Index ≤20)",
                        if (isRu) "GVI оценивает извилистость графика (скачки). GRI (0–100) — композитный индекс риска осложнений (Зона А: ≤20 баллов)."
                        else "GVI evaluates glucose zigzag lability. GRI (0–100) reflects composite clinical risk (Zone A low risk: ≤20)."
                    )
                )
            )

            // Footer Disclaimer Banner
            y = 750f
            val warnRect = RectF(30f, y, 565f, 815f)
            canvas.drawRoundRect(warnRect, 8f, 8f, warningBgPaint)
            canvas.drawRoundRect(warnRect, 8f, 8f, warningBorderPaint)

            canvas.drawText(
                if (isRu) "⚠️ Важное примечание о точности данных и непрерывном мониторинге (CGM):"
                else "⚠️ Clinical Notice Regarding Continuous Glucose Monitoring (CGM) Accuracy:",
                38f,
                y + 14f,
                itemTitlePaint.apply { color = Color.rgb(180, 83, 9) }
            )
            val discText1 = if (isRu) {
                "• Физиологическое запаздывание: датчики CGM измеряют сахар в межтканевой жидкости, отставание от крови составляет 5–15 минут."
            } else {
                "• Physiological Lag: CGM sensors measure interstitial fluid; physiological lag relative to capillary blood is 5–15 minutes."
            }
            val discText2 = if (isRu) {
                "• Погрешность MARD: стандартная средняя погрешность систем CGM составляет 8–10%. Не является диагнозом."
            } else {
                "• MARD Accuracy: standard mean absolute relative difference of CGM systems is 8–10%. Not a standalone diagnosis."
            }
            val discText3 = if (isRu) {
                "• Принятие решений: при выраженном расхождении самочувствия с показаниями выполните замер по капле крови и проконсультируйтесь с врачом."
            } else {
                "• Medical Decisions: verify unexpected symptoms with capillary blood testing before clinical dosing decisions."
            }
            canvas.drawText(discText1, 38f, y + 27f, warningTextPaint)
            canvas.drawText(discText2, 38f, y + 38f, warningTextPaint)
            canvas.drawText(discText3, 38f, y + 49f, warningTextPaint)

            document.finishPage(page)

            val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outputFile = File(outputDir, "TIRUp_Guidebook.pdf")
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document.close()
        }
    }
}
