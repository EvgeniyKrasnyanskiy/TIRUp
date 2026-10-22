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
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.2f
            }
            val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 8.0f
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

            fun drawCards(
                canvas: android.graphics.Canvas,
                startY: Float,
                sectionTitle: String,
                items: List<Triple<String, String, String>>,
                cardHeight: Float = 40f
            ): Float {
                var currentY = startY
                canvas.drawText(sectionTitle, 30f, currentY, sectionPaint)
                currentY += 7.5f

                for ((itemTitle, itemTarget, itemDesc) in items) {
                    val rect = RectF(30f, currentY, 565f, currentY + cardHeight)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBorderPaint)

                    // Title and Target Badge
                    canvas.drawText(itemTitle, 38f, currentY + 12.5f, itemTitlePaint)
                    if (itemTarget.isNotEmpty()) {
                        val targetWidth = itemTargetPaint.measureText(itemTarget)
                        canvas.drawText(itemTarget, 557f - targetWidth, currentY + 12.5f, itemTargetPaint)
                    }

                    // Word wrap description across lines
                    val words = itemDesc.split(" ")
                    var line = ""
                    var lineY = currentY + 24.5f
                    for (w in words) {
                        val testLine = if (line.isEmpty()) w else "$line $w"
                        if (bodyPaint.measureText(testLine) < 515f) {
                            line = testLine
                        } else {
                            canvas.drawText(line, 38f, lineY, bodyPaint)
                            line = w
                            lineY += 9.5f
                            if (lineY > currentY + cardHeight - 2f) break
                        }
                    }
                    if (line.isNotEmpty() && lineY <= currentY + cardHeight - 2f) {
                        canvas.drawText(line, 38f, lineY, bodyPaint)
                    }

                    currentY += cardHeight + 4.5f
                }
                return currentY
            }

            // ================= PAGE 1 =================
            val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page1 = document.startPage(pageInfo1)
            val canvas1 = page1.canvas

            var y1 = 44f
            canvas1.drawText(
                if (isRu) "TIRUp • Справочник параметров CGM" else "TIRUp • CGM Parameters Guide",
                30f,
                y1,
                titlePaint
            )
            val p1Badge = if (isRu) "Стр. 1 из 2" else "Page 1 of 2"
            canvas1.drawText(p1Badge, 565f - pageNumberPaint.measureText(p1Badge), y1, pageNumberPaint)

            y1 += 14f
            canvas1.drawText(
                if (isRu) "Международные клинические ориентиры непрерывного мониторинга глюкозы (консенсус ATTD / ADA)"
                else "International clinical benchmarks for continuous glucose monitoring (ATTD / ADA consensus)",
                30f,
                y1,
                subtitlePaint
            )
            y1 += 8f
            canvas1.drawLine(30f, y1, 565f, y1, linePaint)
            y1 += 14f

            // Section 1: Core Metrics (3 items)
            y1 = drawCards(
                canvas1,
                y1,
                if (isRu) "1. Показатели общего гликемического контроля" else "1. Core Glycemic Control Metrics",
                listOf(
                    Triple(
                        if (isRu) "Mean Glucose (Средний сахар)" else "Mean Glucose (Average)",
                        if (isRu) "Цель: ≤7.8 (здоровые 4.5–5.8)" else "Target: ≤7.8 (non-diab 4.5–5.8)",
                        if (isRu) "Среднее всех измерений. У людей без диабета натощак: 3.9–5.5 ммоль/л, после еды ≤7.8. При диабете средний 8.5 ≈ eA1c 7.0%, а 10.0 ≈ eA1c 7.6%."
                        else "Arithmetic mean of all readings. Fasting non-diabetic range is 3.9–5.5 mmol/L, postprandial ≤7.8. Mean 8.5 ≈ eA1c 7.0%, Mean 10.0 ≈ eA1c 7.6%."
                    ),
                    Triple(
                        if (isRu) "eA1c / GMI (Расчётный гликированный гемоглобин)" else "eA1c / GMI (Estimated HbA1c)",
                        if (isRu) "Цель: ≤7.0% (норма 4.0–5.6%)" else "Target: ≤7.0% (normal 4.0–5.6%)",
                        if (isRu) "Проекция HbA1c по формуле GMI = 3.31 + 0.431 × Mean (адаптация Bergenstal 2018 для ммоль/л) за 2–3 мес. Не искажается анемией. Для детей цель может быть <6.5%, для пожилых <7.5–8.0%."
                        else "GMI formula (3.31 + 0.431 × MeanGlucose mmol/L, Bergenstal 2018 adaptation) projection over 2-3 months. Free from anemia distortions. Pediatric/pregnancy target <6.5%, elderly target <7.5-8.0%."
                    ),
                    Triple(
                        if (isRu) "Min / Max (Экстремумы сахара за период)" else "Min / Max (Observed Glycemic Range)",
                        if (isRu) "Коридор: 3.9–10.0 ммоль/л" else "Target Corridor: 3.9–10.0 mmol/L",
                        if (isRu) "Фактический размах колебаний. У здоровых людей 96% времени суток сахар находится строго в коридоре 4.0–7.8 ммоль/л (натощак 3.9–5.5 ммоль/л)."
                        else "Full observed span of values. Healthy individuals spend 96% of the day within 4.0–7.8 mmol/L (fasting 3.9–5.5 mmol/L)."
                    )
                ),
                cardHeight = 40f
            )

            y1 += 8f

            // Section 2: Time in Range (4 items)
            y1 = drawCards(
                canvas1,
                y1,
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
                cardHeight = 40f
            )

            y1 += 8f

            // Section 3: Variability (3 items)
            y1 = drawCards(
                canvas1,
                y1,
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
                cardHeight = 40f
            )

            document.finishPage(page1)

            // ================= PAGE 2 =================
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = document.startPage(pageInfo2)
            val canvas2 = page2.canvas

            var y2 = 44f
            canvas2.drawText(
                if (isRu) "TIRUp • Паттерны, точность и верификация сенсоров" else "TIRUp • Patterns, Accuracy & Sensor Verification",
                30f,
                y2,
                titlePaint
            )
            val p2Badge = if (isRu) "Стр. 2 из 2" else "Page 2 of 2"
            canvas2.drawText(p2Badge, 565f - pageNumberPaint.measureText(p2Badge), y2, pageNumberPaint)

            y2 += 14f
            canvas2.drawText(
                if (isRu) "Стандарты точности ISO 15197:2013, клиническая погрешность MARD и правила верификации"
                else "ISO 15197:2013 accuracy standards, MARD metrics and clinical verification guidelines",
                30f,
                y2,
                subtitlePaint
            )
            y2 += 8f
            canvas2.drawLine(30f, y2, 565f, y2, linePaint)
            y2 += 14f

            // Section 4: Nocturnal Profile & Clinical Patterns (2 items)
            y2 = drawCards(
                canvas2,
                y2,
                if (isRu) "4. Ночной профиль и клинические паттерны" else "4. Nocturnal Profile & Glycemic Patterns",
                listOf(
                    Triple(
                        if (isRu) "Ночной профиль (Окно сна 00:00–06:00)" else "Nocturnal Glycemia (Sleep Window)",
                        if (isRu) "Цель: SD ≤1.5 ммоль/л, TBR = 0%" else "Target: SD ≤1.5 mmol/L (≤27 mg/dL), TBR = 0%",
                        if (isRu) "Ночь — самый физиологически стабильный период суток без влияния еды. Отражает адекватность дозы базального инсулина и защищает от ночных гипогликемий."
                        else "The most basal metabolic phase of 24h. Evaluates background basal insulin coverage and rules out dangerous nocturnal hypoglycemia."
                    ),
                    Triple(
                        if (isRu) "Паттерны: «Утренняя заря» и постпрандиальные пики" else "Patterns: Dawn Phenomenon & Meal Spikes",
                        if (isRu) "Ранняя детекция" else "Early Detection",
                        if (isRu) "Феномен утренней зари — подъём сахара в 4–8 утра из-за выброса кортизола и гормона роста. Постпрандиальный пик — подъём через 60–90 мин после еды (прирост >2.2 ммоль/л от базы)."
                        else "Dawn phenomenon denotes early morning glucose rise driven by cortisol. Postprandial spikes reflect carbohydrate-insulin mismatch (rise >2.2 mmol/L / >40 mg/dL from baseline)."
                    )
                ),
                cardHeight = 40f
            )

            y2 += 8f

            // Section 5: Sensor Accuracy & ISO 15197:2013 (4 items)
            y2 = drawCards(
                canvas2,
                y2,
                if (isRu) "5. Точность сенсоров и стандарт ISO 15197:2013" else "5. Sensor Accuracy & ISO 15197:2013 Standard",
                listOf(
                    Triple(
                        if (isRu) "Стандарт ISO 15197:2013 и клинические допуски" else "ISO 15197:2013 Standard & Tolerances",
                        if (isRu) "≥95% в допуске • 99% в зонах A/B" else "≥95% in bounds • 99% in Zone A/B",
                        if (isRu) "Международный стандарт точности: при сахаре <5.55 ммоль/л (<100 мг/дл) погрешность ≤±0.83 ммоль/л (±15 мг/дл); при ≥5.55 ммоль/л — погрешность ≤±15%. 99% измерений обязаны попадать в зоны A и B решётки Паркса (EGA)."
                        else "International benchmark: for BG <5.55 mmol/L (<100 mg/dL) tolerance is within ±0.83 mmol/L (±15 mg/dL); for ≥5.55 mmol/L within ±15%. 99% must fall in Zones A & B of Consensus Error Grid."
                    ),
                    Triple(
                        if (isRu) "MARD (Средняя относительная разность)" else "MARD (Mean Absolute Relative Difference)",
                        if (isRu) "Отлично: <10.0% • Норма: 10–14%" else "Optimal: <10.0% • Good: 10–14%",
                        if (isRu) "Золотой эталон точности непрерывных датчиков CGM (|CGM - BG| / BG × 100%). MARD <10% признан достаточным для терапии без подтверждающего прокола пальца. MARD 10–14% — хорошая точность; >18% требует калибровки или замены."
                        else "Gold standard metric of sensor accuracy (|CGM - BG| / BG * 100%). MARD <10% qualifies for non-adjunctive insulin dosing. 10–14% represents good accuracy; >18% signals sensor replacement requirement."
                    ),
                    Triple(
                        if (isRu) "Mean Bias (Систематическое смещение сенсора)" else "Mean Bias (Systematic Sensor Drift)",
                        if (isRu) "Норма: в пределах ±0.50 ммоль/л" else "Target: within ±0.50 mmol/L",
                        if (isRu) "Средняя разность между датчиком и капиллярной кровью. Показывает систематический дрейф: положительное смещение (+) означает постоянное завышение сахара сенсором, отрицательное (-) — занижение."
                        else "Mean difference between CGM and capillary blood. Positive bias (+) indicates consistent overreading, while negative (-) reflects sensor underreading."
                    ),
                    Triple(
                        if (isRu) "Физиологический лаг и правила верификации" else "Physiological Lag & Verification Rules",
                        if (isRu) "Лаг: 5–12 мин • Тренд: <0.12 ммоль/л/мин" else "Lag: 5–12 min • RoC: <0.12 mmol/L/min",
                        if (isRu) "Датчик CGM измеряет глюкозу в межтканевой жидкости, отставая от капиллярной крови на 5–12 минут. Верификация по глюкометру достоверна ТОЛЬКО при стабильном горизонтальном тренде (скорость изменения <0.12 ммоль/л/мин)."
                        else "CGM reads interstitial fluid with 5–12 min physiological lag relative to blood. Meter comparisons are valid ONLY during flat steady trends (rate of change <0.12 mmol/L/min)."
                    )
                ),
                cardHeight = 40f
            )

            // Footer Disclaimer Banner (dynamic position on page 2)
            val disclaimerHeight = 88f
            val footerTop = maxOf(y2 + 10f, 842f - 32f - disclaimerHeight)
            val warnRect = RectF(30f, footerTop, 565f, footerTop + disclaimerHeight)
            canvas2.drawRoundRect(warnRect, 7f, 7f, warningBgPaint)
            canvas2.drawRoundRect(warnRect, 7f, 7f, warningBorderPaint)

            canvas2.drawText(
                if (isRu) "⚠️ Важные клинические примечания к непрерывному мониторингу (CGM):"
                else "⚠️ Important Clinical Notes Regarding Continuous Glucose Monitoring (CGM):",
                38f,
                footerTop + 14f,
                itemTitlePaint.apply { color = Color.rgb(180, 83, 9) }
            )
            val discText1 = if (isRu) {
                "• Достаточность данных (<70%): если активное время работы сенсора <70% (<10 из 14 дней), отчёт нерепрезентативен; менять терапию нельзя."
            } else {
                "• Data Sufficiency (<70%): if active wear time is <70% (<10 of 14 days), data is non-representative; clinical decisions cannot be made."
            }
            val discText2 = if (isRu) {
                "• Стандарты точности: эталонный допуск глюкометров — ISO 15197:2013 (±0.83 ммоль/л при <5.55 и ±15% при ≥5.55 ммоль/л). Стандарт MARD сенсоров CGM — 8–10%."
            } else {
                "• Accuracy Standards: meter tolerances follow ISO 15197:2013 (±0.83 mmol/L at <5.55 and ±15% at ≥5.55 mmol/L). Standard CGM MARD is 8–10%."
            }
            val discText3 = if (isRu) {
                "• Физиологическое запаздывание: датчики измеряют интерстициальную жидкость с задержкой 5–15 мин. Возможны сдавления во сне (compression lows)."
            } else {
                "• Physiological Lag: sensors measure interstitial fluid with 5–15 min lag. False compression lows during sleep may occur."
            }
            val discText4 = if (isRu) {
                "• Принятие решений: при выраженном расхождении самочувствия с показаниями CGM выполните контрольный замер глюкометром по капле крови."
            } else {
                "• Medical Decisions: verify unexpected sensor readings with a capillary blood glucose fingerstick before corrective action."
            }
            canvas2.drawText(discText1, 38f, footerTop + 27f, warningTextPaint)
            canvas2.drawText(discText2, 38f, footerTop + 40f, warningTextPaint)
            canvas2.drawText(discText3, 38f, footerTop + 53f, warningTextPaint)
            canvas2.drawText(discText4, 38f, footerTop + 66f, warningTextPaint)

            document.finishPage(page2)

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
