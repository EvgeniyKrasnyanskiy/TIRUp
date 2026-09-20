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
                textSize = 8.2f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129) // PrimaryEmerald
                strokeWidth = 1.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 185, 129) // PrimaryEmerald
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(5, 150, 105) // Emerald 600
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 7.7f
            }
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val warningBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(254, 243, 199) // Amber 100
                style = Paint.Style.FILL
            }
            val warningBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 158, 11) // Amber 500
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
            }
            val warningTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(146, 64, 14) // Amber 800
                textSize = 7.3f
            }

            var y = 44f

            // 1. Header Banner
            canvas.drawText(
                if (isRu) "TIRUp • Справочник параметров CGM" else "TIRUp • CGM Parameters Guide",
                30f,
                y,
                titlePaint
            )
            y += 14f
            canvas.drawText(
                if (isRu) "Международные клинические ориентиры непрерывного мониторинга глюкозы (консенсус ATTD / ADA)"
                else "International clinical benchmarks for continuous glucose monitoring (ATTD / ADA consensus)",
                30f,
                y,
                subtitlePaint
            )
            y += 8f
            canvas.drawLine(30f, y, 565f, y, linePaint)
            y += 14f

            var isFirstSection = true
            fun drawSection(title: String, items: List<Triple<String, String, String>>, cardHeight: Float = 38.5f) {
                if (!isFirstSection) {
                    y += 11f // Generous gap above section header, separating from previous block
                }
                isFirstSection = false

                canvas.drawText(title, 30f, y, sectionPaint)
                y += 7.5f // Tight gap below section header, keeping it attached to its cards

                for ((itemTitle, itemTarget, itemDesc) in items) {
                    val rect = RectF(30f, y, 565f, y + cardHeight)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBorderPaint)

                    // Title and Target Badge
                    canvas.drawText(itemTitle, 38f, y + 12.5f, itemTitlePaint)
                    if (itemTarget.isNotEmpty()) {
                        val targetWidth = itemTargetPaint.measureText(itemTarget)
                        canvas.drawText(itemTarget, 557f - targetWidth, y + 12.5f, itemTargetPaint)
                    }

                    // Word wrap description across 2 lines
                    val words = itemDesc.split(" ")
                    var line = ""
                    var lineY = y + 24.5f
                    for (w in words) {
                        val testLine = if (line.isEmpty()) w else "$line $w"
                        if (bodyPaint.measureText(testLine) < 515f) {
                            line = testLine
                        } else {
                            canvas.drawText(line, 38f, lineY, bodyPaint)
                            line = w
                            lineY += 10f
                            if (lineY > y + cardHeight - 2f) break
                        }
                    }
                    if (line.isNotEmpty() && lineY <= y + cardHeight - 2f) {
                        canvas.drawText(line, 38f, lineY, bodyPaint)
                    }

                    y += cardHeight + 4f
                }
            }

            // Section 1: Core Control Metrics (3 items)
            drawSection(
                if (isRu) "1. Показатели общего гликемического контроля" else "1. Core Glycemic Control Metrics",
                listOf(
                    Triple(
                        if (isRu) "Mean Glucose (Средний сахар)" else "Mean Glucose (Average)",
                        if (isRu) "Цель: ≤7.8 (здоровые 4.5–5.8)" else "Target: ≤7.8 (non-diab 4.5–5.8)",
                        if (isRu) "Среднее всех измерений. У людей без диабета натощак: 3.9–5.5 ммоль/л, после еды ≤7.8. При диабете средний 8.5 ≈ eA1c 7.5%, а 10.0 ≈ eA1c 8.6%."
                        else "Arithmetic mean of all readings. Fasting non-diabetic range is 3.9–5.5 mmol/L, postprandial ≤7.8. Mean 8.5 ≈ eA1c 7.5%, Mean 10.0 ≈ eA1c 8.6%."
                    ),
                    Triple(
                        if (isRu) "eA1c / GMI (Расчётный гликированный гемоглобин)" else "eA1c / GMI (Estimated HbA1c)",
                        if (isRu) "Цель: ≤7.0% (норма 4.0–5.6%)" else "Target: ≤7.0% (normal 4.0–5.6%)",
                        if (isRu) "Проекция HbA1c по формуле GMI = 3.31 + 0.431 × Mean(ммоль/л) за 2–3 мес. Не искажается анемией. Для детей цель может быть <6.5%, для пожилых <7.5–8.0%."
                        else "GMI formula (3.31 + 0.431 × MeanGlucose mmol/L) projection over 2-3 months. Free from anemia distortions. Pediatric/pregnancy target <6.5%, elderly target <7.5-8.0%."
                    ),
                    Triple(
                        if (isRu) "Min / Max (Экстремумы сахара за период)" else "Min / Max (Observed Glycemic Range)",
                        if (isRu) "Коридор: 3.9–10.0 ммоль/л" else "Target Corridor: 3.9–10.0 mmol/L",
                        if (isRu) "Фактический размах колебаний. У здоровых людей 96% времени суток сахар находится строго в коридоре 4.0–7.8 ммоль/л (натощак 3.3–5.5 ммоль/л)."
                        else "Full observed span of values. Healthy individuals spend 96% of the day within 4.0–7.8 mmol/L (fasting 3.3–5.5 mmol/L)."
                    )
                ),
                cardHeight = 39f
            )

            // Section 2: Time in Range (4 items)
            drawSection(
                if (isRu) "2. Время в целевых диапазонах (Time in Range — консенсус ATTD/ADA)" else "2. Time in Range Metrics (ATTD Consensus)",
                listOf(
                    Triple(
                        if (isRu) "TIR (Целевой диапазон 3.9–10.0 ммоль/л)" else "TIR (Target Range 3.9–10.0 mmol/L)",
                        if (isRu) "Цель: ≥70.0% (>16ч 48м/сут)" else "Target: ≥70.0% (>16h 48m/day)",
                        if (isRu) "Золотой стандарт ATTD/ADA. Каждые +10% снижают риск ретинопатии на 64% и нефропатии на 40%! Здоровые люди: 96–99%. Беременность (СД1): ≥70% в 3.5–7.8."
                        else "Gold standard metric. Every +10% TIR reduces microvascular complications risk by 40-64%. Non-diabetic baseline: 96–99%. Pregnancy: ≥70% in 3.5–7.8."
                    ),
                    Triple(
                        if (isRu) "TITR / TING (Узкий диапазон нормы 3.9–7.8 ммоль/л)" else "TITR / TING (Tight Range 3.9–7.8 mmol/L)",
                        if (isRu) "Цель: ≥50.0% (>12 ч/сут)" else "Target: ≥50.0% (>12h/day)",
                        if (isRu) "Time in Tight Range (TITR) — физиологический коридор здоровой поджелудочной железы. Максимальная защита сосудов от окислительного стресса."
                        else "Time in Tight Range (TITR) — physiological tight corridor. Evaluates precision of postprandial glycemic excursions blunting and peak insulin therapy precision."
                    ),
                    Triple(
                        if (isRu) "TBR (Время в гипогликемии <3.9 ммоль/л)" else "TBR (Hypoglycemia <3.9 mmol/L)",
                        if (isRu) "<3.9: <4% (<1ч), <3.0: <1%" else "<3.9: <4% (<1h), <3.0: <1%",
                        if (isRu) "Главный приоритет безопасности. Сахар <3.0 несёт риск аритмий и комы. При сниженном распознавании гипогликемии цель ужесточается до TBR <1% суммарно."
                        else "Critical safety boundary. Hypoglycemia triggers arrhythmias and cognitive impairment. Level 2 (<3.0 mmol/L) must stay <1% (<14 min/day)."
                    ),
                    Triple(
                        if (isRu) "TAR (Время в гипергликемии >10.0 ммоль/л)" else "TAR (Hyperglycemia >10.0 mmol/L)",
                        if (isRu) ">10.0: <25% (<6ч), >13.9: <5%" else ">10.0: <25% (<6h), >13.9: <5%",
                        if (isRu) "Высокий сахар повреждает эндотелий капилляров. Сахар выше 13.9 ммоль/л — критическая зона кетоацидоза и глюкозотоксичности (лимит: <1 ч 12 мин/сут)."
                        else "Prolonged elevation damages microvasculature. Values >13.9 mmol/L present acute DKA risk and should strictly stay below 5% (<1h 12m/day)."
                    )
                ),
                cardHeight = 39f
            )

            // Section 3: Variability & Composite Indices (3 items)
            drawSection(
                if (isRu) "3. Вариабельность и качество кривой" else "3. Glycemic Variability & Quality Indices",
                listOf(
                    Triple(
                        if (isRu) "%CV (Коэффициент вариации) и SD (Разброс)" else "%CV (Variability) & SD (Dispersion)",
                        if (isRu) "CV: ≤36.0%, SD: ≤2.0 ммоль/л" else "CV: ≤36.0%, SD: ≤2.0 mmol/L",
                        if (isRu) "%CV = SD / Mean × 100%. При %CV > 36% риск тяжёлой гипо возрастает в 4 раза. Клиническое правило трети: SD не должно превышать 1/3 от величины среднего сахара."
                        else "%CV = SD / Mean * 100%. Values >36% denote high volatility and quadruple severe hypoglycemia risk. Target SD is less than 1/3 of Mean."
                    ),
                    Triple(
                        if (isRu) "GVI (Индекс лабильности) и GRI (Индекс риска)" else "GVI (Glycemic Variability) & GRI (Risk)",
                        if (isRu) "GVI: ≤1.20, GRI: Зона A/B (≤40)" else "GVI: ≤1.20, GRI: Zone A/B (≤40)",
                        if (isRu) "GVI оценивает «зигзагообразность» кривой (идеал 1.00–1.20). GRI (0–100) штрафует гипогликемию с весом 3.0, а гипергликемию — 1.6 (безопасная зона ≤40)."
                        else "GVI measures glucose curve length (ideal 1.00-1.20). GRI (0-100, Klonoff 2022) weights hypo risk 3.0x and hyper risk 1.6x (target ≤40)."
                    ),
                    Triple(
                        if (isRu) "PGS (Персональный балл стабильности)" else "PGS (Personal Glycemic Score)",
                        if (isRu) "Отлично: ≤35.0, Хорошо: ≤70" else "Optimal: ≤35.0, Good: ≤70",
                        if (isRu) "Интегральный балл качества компенсации, объединяющий средний сахар, вариабельность GVI и TIR. Чем ниже балл, тем ближе профиль к физиологическому идеалу."
                        else "Comprehensive clinical score combining mean glucose, trajectory variability (GVI), and time in target (TIR). Lower score reflects superior control."
                    )
                ),
                cardHeight = 39f
            )

            // Section 4: Night Profile & Clinical Patterns (2 items)
            drawSection(
                if (isRu) "4. Ночной профиль и клинические паттерны" else "4. Nocturnal Profile & Glycemic Patterns",
                listOf(
                    Triple(
                        if (isRu) "Ночной профиль (Окно сна 00:00–06:00)" else "Nocturnal Glycemia (Sleep Window)",
                        if (isRu) "Цель: SD ≤1.5 ммоль/л, TBR = 0%" else "Target: SD ≤27 mg/dL, TBR = 0%",
                        if (isRu) "Ночь — самый физиологически стабильный период суток без влияния еды. Отражает адекватность дозы базального инсулина и защищает от ночных гипогликемий."
                        else "The most basal metabolic phase of 24h. Evaluates background basal insulin coverage and rules out dangerous nocturnal hypoglycemia."
                    ),
                    Triple(
                        if (isRu) "Паттерны: «Утренняя заря» и постпрандиальные пики" else "Patterns: Dawn Phenomenon & Meal Spikes",
                        if (isRu) "Ранняя детекция" else "Early Detection",
                        if (isRu) "Феномен утренней зари — подъём сахара в 4–8 утра из-за выброса кортизола и гормона роста. Постпрандиальный пик — подъём через 60–90 мин после еды (>2.5 ммоль/л)."
                        else "Dawn phenomenon denotes early morning glucose rise driven by cortisol. Postprandial spikes reflect carbohydrate-insulin mismatch."
                    )
                ),
                cardHeight = 39f
            )

            // Footer Disclaimer Banner (dynamic position — fills remaining space)
            val disclaimerHeight = 86f
            val footerTop = maxOf(y + 8f, 842f - 30f - disclaimerHeight) // pin to bottom, but never overlap content
            val warnRect = RectF(30f, footerTop, 565f, footerTop + disclaimerHeight)
            canvas.drawRoundRect(warnRect, 7f, 7f, warningBgPaint)
            canvas.drawRoundRect(warnRect, 7f, 7f, warningBorderPaint)

            canvas.drawText(
                if (isRu) "⚠️ Важные клинические примечания к системам CGM:"
                else "⚠️ Important Clinical Notes Regarding Continuous Glucose Monitoring (CGM):",
                38f,
                footerTop + 13f,
                itemTitlePaint.apply { color = Color.rgb(180, 83, 9) }
            )
            val discText1 = if (isRu) {
                "• Достаточность данных (<70%): если активное время работы сенсора <70% (<10 из 14 дней), отчёт нерепрезентативен; менять терапию нельзя."
            } else {
                "• Data Sufficiency (<70%): if active wear time is <70% (<10 of 14 days), data is non-representative; clinical decisions cannot be made."
            }
            val discText2 = if (isRu) {
                "• Физиологическое запаздывание: сенсоры CGM измеряют глюкозу в межтканевой жидкости; запаздывание от крови составляет 5–15 минут."
            } else {
                "• Physiological Lag: CGM sensors measure interstitial fluid; physiological lag relative to blood glucose is typically 5–15 minutes."
            }
            val discText3 = if (isRu) {
                "• Погрешность MARD: стандартная клиническая погрешность систем CGM составляет 8–10%. Возможны ложные занижения показаний при сдавливании сенсора во сне (compression lows)."
            } else {
                "• MARD Accuracy: standard mean absolute relative difference is 8–10%. Sleep compression lows may occasionally occur."
            }
            val discText4 = if (isRu) {
                "• Принятие решений: при выраженном расхождении самочувствия с показаниями CGM выполните контрольный замер по капле крови."
            } else {
                "• Medical Decisions: verify unexpected sensor readings with a capillary blood glucose fingerstick before corrective action."
            }
            canvas.drawText(discText1, 38f, footerTop + 26f, warningTextPaint)
            canvas.drawText(discText2, 38f, footerTop + 38f, warningTextPaint)
            canvas.drawText(discText3, 38f, footerTop + 50f, warningTextPaint)
            canvas.drawText(discText4, 38f, footerTop + 62f, warningTextPaint)

            document.finishPage(page)

            val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outputFile = File(outputDir, "TIRUp_CGM_Parameters_Guide.pdf")
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
