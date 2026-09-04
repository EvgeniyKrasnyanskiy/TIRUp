package com.tirup.app.presentation.settings

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

class UserManualPdfGenerator(private val context: Context) {

    suspend fun generateUserManualPdf(isRu: Boolean): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            // A4 standard dimensions: 595 x 842 points
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 14.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.0f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                strokeWidth = 1.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 10.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 7.4f
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
            val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(239, 246, 255) // Blue 50
                style = Paint.Style.FILL
            }
            val noteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(96, 165, 250) // Blue 400
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 64, 175) // Blue 800
                textSize = 7.2f
            }
            val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 7.5f
            }

            fun drawHeader(canvas: android.graphics.Canvas, pageNum: Int, totalPages: Int, pageTitle: String, pageSubtitle: String): Float {
                var y = 38f
                canvas.drawText(pageTitle, 30f, y, titlePaint)
                val pageStr = if (isRu) "Стр. $pageNum из $totalPages" else "Page $pageNum of $totalPages"
                val pageStrWidth = pageNumPaint.measureText(pageStr)
                canvas.drawText(pageStr, 565f - pageStrWidth, y, pageNumPaint)
                y += 13f

                canvas.drawText(pageSubtitle, 30f, y, subtitlePaint)
                y += 7f
                canvas.drawLine(30f, y, 565f, y, linePaint)
                y += 13f
                return y
            }

            fun drawSection(
                canvas: android.graphics.Canvas,
                startY: Float,
                title: String,
                items: List<Triple<String, String, String>>,
                cardHeight: Float = 40f
            ): Float {
                var y = startY
                canvas.drawText(title, 30f, y, sectionPaint)
                y += 7f

                for ((itemTitle, itemBadge, itemDesc) in items) {
                    val rect = RectF(30f, y, 565f, y + cardHeight)
                    canvas.drawRoundRect(rect, 5f, 5f, cardBgPaint)
                    canvas.drawRoundRect(rect, 5f, 5f, cardBorderPaint)

                    // Title and Badge
                    canvas.drawText(itemTitle, 38f, y + 11.5f, itemTitlePaint)
                    if (itemBadge.isNotEmpty()) {
                        val badgeWidth = itemBadgePaint.measureText(itemBadge)
                        canvas.drawText(itemBadge, 557f - badgeWidth, y + 11.5f, itemBadgePaint)
                    }

                    // Word wrap description
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
                            lineY += 9.5f
                            if (lineY > y + cardHeight - 2f) break
                        }
                    }
                    if (line.isNotEmpty() && lineY <= y + cardHeight - 2f) {
                        canvas.drawText(line, 38f, lineY, bodyPaint)
                    }

                    y += cardHeight + 4f
                }
                return y + 6f
            }

            fun drawFooterNote(canvas: android.graphics.Canvas, topY: Float, height: Float, noteTitle: String, bulletPoints: List<String>) {
                val noteRect = RectF(30f, topY, 565f, topY + height)
                canvas.drawRoundRect(noteRect, 6f, 6f, noteBgPaint)
                canvas.drawRoundRect(noteRect, 6f, 6f, noteBorderPaint)

                canvas.drawText(
                    noteTitle,
                    38f,
                    topY + 12f,
                    itemTitlePaint.apply { color = Color.rgb(30, 64, 175) }
                )
                var lineY = topY + 24f
                for (pt in bulletPoints) {
                    canvas.drawText(pt, 38f, lineY, noteTextPaint)
                    lineY += 11f
                }
            }

            // =========================================================================
            // PAGE 1: Data Sources, Desktop Widgets, Daily Compensator
            // =========================================================================
            val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page1 = document.startPage(pageInfo1)
            val canvas1 = page1.canvas

            var y1 = drawHeader(
                canvas = canvas1,
                pageNum = 1,
                totalPages = 2,
                pageTitle = if (isRu) "TIRUp • Руководство пользователя (Часть 1)" else "TIRUp • User Manual (Part 1)",
                pageSubtitle = if (isRu) "Связка с источниками данных, API трансляции, виджеты рабочего стола и компенсатор TIR"
                else "Data linking, Broadcast Service API, desktop widgets & daily target compensator"
            )

            // Section 1: Data Sources & Broadcast API
            y1 = drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "1. Интеграция с источниками данных (xDrip+, GlucoDataHandler, Juggluco)"
                else "1. Linking Data Sources (xDrip+, GlucoDataHandler, Juggluco)",
                items = listOf(
                    Triple(
                        if (isRu) "Локальная трансляция (Broadcast locally)" else "Local Broadcast Toggle",
                        if (isRu) "Шаг 1" else "Step 1",
                        if (isRu) "xDrip+ ➔ Настройки ➔ «Межпрограммная интеграция» ➔ включите «Широковещательный показ данных». Приём работает полностью автономно без интернета и серверов при каждом замере сенсора."
                        else "xDrip+ ➔ Settings ➔ 'Inter-app settings' ➔ enable 'Broadcast locally'. Runs 100% offline without cloud servers upon every new sensor measurement."
                    ),
                    Triple(
                        if (isRu) "Служба трансляции API (IoB / CoB)" else "Broadcast Service API (IoB / CoB)",
                        if (isRu) "Шаг 2" else "Step 2",
                        if (isRu) "xDrip+ ➔ «Межпрограммная интеграция» ➔ включите «API службы трансляции» (Broadcast Service API). Это позволяет передавать в TIRUp активный инсулин (IoB) и активные углеводы (CoB)."
                        else "xDrip+ ➔ 'Inter-app settings' ➔ enable 'Broadcast Service API'. Enables real-time transmission of active insulin (IoB) and active carbs (CoB) to TIRUp."
                    ),
                    Triple(
                        if (isRu) "Локальная веб-служба Pebble (порт 17580)" else "Local Pebble Web Service (Port 17580)",
                        if (isRu) "Шаг 3" else "Step 3",
                        if (isRu) "xDrip+ ➔ «Службы данных часов» ➔ «Служба Pebble» ➔ включите веб-службу на порту 17580. Обеспечивает мгновенный доступ к метрикам компенсации для внешних модулей."
                        else "xDrip+ ➔ 'Smart Watch Features' ➔ 'Pebble Watch Integration' ➔ enable Pebble web server on port 17580 for zero-latency metric sync."
                    )
                ),
                cardHeight = 39f
            )

            // Section 2: Homescreen & Lockscreen Widgets
            y1 = drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "2. Виджеты рабочего стола и экран блокировки" else "2. Desktop & Lockscreen Widgets",
                items = listOf(
                    Triple(
                        if (isRu) "5 форматов виджетов (5х1, 4х2, 3х2, 2х2, 1х2)" else "5 Widget Formats (5x1, 4x2, 3x2, 2x2, 1x2)",
                        if (isRu) "Рабочий стол" else "Homescreen",
                        if (isRu) "Горизонтальная полоса (5х1), дашборд с 4-часовым графиком Canvas (4х2/3х2), квадратный фокус (2х2) и вертикальный стек (1х2). Все виджеты адаптируются под сетку лончера."
                        else "Horizontal strip (5x1), HD 4-hour Canvas sparkline dashboard (4x2/3x2), compact square (2x2) and vertical glance (1x2). Auto-scale to launcher grid."
                    ),
                    Triple(
                        if (isRu) "Цветовое кодирование гликемии" else "Glycemic Range Color Standards",
                        if (isRu) "Цвета" else "Colors",
                        if (isRu) "<3.9 ммоль/л — красный; 3.9..7.8 — бледно-зелёный (#4ADE80); 7.9..10.0 — насыщенный изумрудный (#10B981); 10.1..13.9 — оранжевый; >13.9 — фиолетовый."
                        else "<3.9 mmol/L Red; 3.9..7.8 Pale Green (#4ADE80); 7.9..10.0 Saturated Emerald (#10B981); 10.1..13.9 Orange; >13.9 Purple. Syncs across all widgets."
                    ),
                    Triple(
                        if (isRu) "Бейджи IoB/CoB, Стрик (🔥 X д.) и кнопка DiaNight (🌙)" else "IoB/CoB Badges, Streak (🔥) & DiaNight (🌙)",
                        if (isRu) "Индикаторы" else "Badges",
                        if (isRu) "Отображают дозы активного инсулина (💉) и углеводов (🍞). Счётчик 🔥 X д. показывает серию дней в цели TIR. Кнопка 🌙 запускает ночные прикроватные часы DiaNight."
                        else "Display active insulin (💉) & carbs (🍞). Streak badge 🔥 shows consecutive days reaching TIR target. 🌙 button opens DiaNight nightstand clock."
                    ),
                    Triple(
                        if (isRu) "Экран блокировки и прозрачность подложки (0%..100%)" else "Lockscreen Status & Background Opacity",
                        if (isRu) "AOD & Шторка" else "AOD & Slider",
                        if (isRu) "Постоянный статус сахара и TIR на экране блокировки/AOD. В настройках доступен плавный ползунок прозрачности 0%..100% с живым окном предпросмотра на фоне обоев."
                        else "Ongoing glucose & TIR notification on Lockscreen/AOD. Features smooth 0%..100% background opacity slider with live desktop wallpaper preview."
                    )
                ),
                cardHeight = 40f
            )

            // Section 3: Daily Compensator
            y1 = drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "3. Суточная математика компенсатора цели (TIR ≥70% / TING ≥50%)"
                else "3. Strict 24-Hour Target Compensator (TIR ≥70% / TING ≥50%)",
                items = listOf(
                    Triple(
                        if (isRu) "Принцип строгих суток (00:00:00 – 23:59:59)" else "24-Hour Daily Strict Calculus",
                        if (isRu) "Математика" else "Math Engine",
                        if (isRu) "Компенсатор рассчитывает точное время в часах и минутах, которое необходимо провести в норме до конца суток. На виджетах отображается лаконично: «В норме ещё 2ч 15м» или «Цель 100%»."
                        else "Calculates exact remaining hours/minutes needed in target before midnight. Formatted concisely on widgets: 'In range 2h 15m' or 'Goal reached! (100%)'."
                    ),
                    Triple(
                        if (isRu) "Уведомление «Последний шанс для TIR»" else "'Last Chance TIR' Proactive Alert",
                        if (isRu) "Предупреждение" else "Warning",
                        if (isRu) "Срабатывает за 1ч, 1.5ч или 2ч до точки невозврата, когда суммарное оставшееся время суток становится меньше времени, необходимого для достижения целевого TIR ≥70%."
                        else "Alerts 1h, 1.5h or 2h before mathematical point of no return when remaining day time cannot mathematically rescue the daily 70% TIR target."
                    )
                ),
                cardHeight = 40f
            )

            // Page 1 Footer Note
            drawFooterNote(
                canvas = canvas1,
                topY = 744f,
                height = 56f,
                noteTitle = if (isRu) "⚡ Рекомендация Android по фоновой работе:" else "⚡ Android Background Battery Optimization Note:",
                bulletPoints = listOf(
                    if (isRu) "• Отключите оптимизацию расхода батареи для TIRUp и xDrip+ («Настройки ➔ Приложения ➔ Без ограничений»)."
                    else "• Exclude TIRUp and xDrip+ from Android battery optimizations ('Settings ➔ Apps ➔ Battery ➔ Unrestricted').",
                    if (isRu) "• Закрепите приложение TIRUp замочком в меню недавних задач для предотвращения выгрузки системой."
                    else "• Lock TIRUp in the Recent Apps switcher to prevent aggressive OEM background process kills."
                )
            )

            document.finishPage(page1)

            // =========================================================================
            // PAGE 2: Smart Alarms, Sleep Window Signal Loss, Bubble & AGP Reports
            // =========================================================================
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = document.startPage(pageInfo2)
            val canvas2 = page2.canvas

            var y2 = drawHeader(
                canvas = canvas2,
                pageNum = 2,
                totalPages = 2,
                pageTitle = if (isRu) "TIRUp • Руководство пользователя (Часть 2)" else "TIRUp • User Manual (Part 2)",
                pageSubtitle = if (isRu) "Четырёхуровневые тревоги, умное пробуждение ночью, пузырёк сахара и клинический AGP"
                else "4-Tier alarms, sleep-aware wake schedule, floating bubble & clinical AGP reporting"
            )

            // Section 4: 4-Tier Safety Alarms & Signal Loss
            y2 = drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "4. Четырёхуровневая система тревог и потеря сенсора (Tier 1–4)"
                else "4. 4-Tier Safety Alarms & Sleep-Aware Signal Loss (Tier 1–4)",
                items = listOf(
                    Triple(
                        if (isRu) "Уровень 1: Предиктивный прогноз (за 15 мин)" else "Tier 1: Predictive Forecast (15 min)",
                        if (isRu) "Мягкий сигнал" else "Soft Chime",
                        if (isRu) "Математическая регрессия рассчитывает скорость изменения сахара и предупреждает о скором выходе за рамки с указанием точного расчетного времени. Мягкий сигнал без стресса."
                        else "Weighted velocity regression calculates impending target departure with exact estimated time. Gentle dual-tone chime without startling."
                    ),
                    Triple(
                        if (isRu) "Уровень 2: Основная тревога (кастомный диапазон, 5 точек)" else "Tier 2: Confirmed Departure (Custom Range, 5 pts)",
                        if (isRu) "Тройной тон" else "Triple Tone",
                        if (isRu) "Срабатывает при подтверждённом выходе 5 замеров подряд за персональные пороги (напр. 3.9..10.0). Воспроизводит отчётливый тройной сигнал с паузой 1.5 сек между бипами."
                        else "Triggers upon 5 consecutive points beyond user thresholds (e.g. 3.9..10.0). Emits distinct triple medical tone with 1.5s pauses."
                    ),
                    Triple(
                        if (isRu) "Уровень 3: Критическая сирена («кричащая»)" else "Tier 3: Critical Siren (Extremes & Prolonged)",
                        if (isRu) "Сирена + Вспышка" else "Alarm + Strobe",
                        if (isRu) "USAGE_ALARM, обход режима «Не беспокоить» (DND), авто-буст громкости ≥80%, стробоскоп вспышки при экстремальных сахарах (<3.0 / >13.9). Мгновенное глушение аппаратными кнопками."
                        else "High-priority alarm stream, bypasses DND, boosts volume to ≥80%, pulses camera strobe. Instant muting via hardware volume/power buttons."
                    ),
                    Triple(
                        if (isRu) "Уровень 4: Потеря сигнала (>20 мин, будильник + расписание дня/ночи)" else "Tier 4: Sleep-Aware Signal Loss Alarm (>20 min)",
                        if (isRu) "Будильник DND" else "Bypass DND",
                        if (isRu) "Приравнена к будильнику (USAGE_ALARM + Bypass DND). Ночью (в окне сна): 6 раз по 5м ➔ 6 раз по 10м ➔ 6 раз по 20м ➔ каждые 30м до утра для пробуждения. Днём: 3x5м ➔ 3x20м ➔ каждый 1 час."
                        else "Treated as alarm (USAGE_ALARM + Bypass DND). Night (sleep window): 6x5m ➔ 6x10m ➔ 6x20m ➔ 30m until morning to wake user. Day: 3x5m ➔ 3x20m ➔ 60m. Resets on new point."
                    )
                ),
                cardHeight = 42f
            )

            // Section 5: Floating Bubble & Adaptive Snooze
            y2 = drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "5. Плавающий оверлей «Пузырёк» и Адаптивный снуз"
                else "5. Floating Glucose Bubble & Adaptive Snooze",
                items = listOf(
                    Triple(
                        if (isRu) "Плавающий круглый оверлей (60x60dp)" else "Floating Circular Bubble (60x60dp)",
                        if (isRu) "Оверлей" else "Overlay",
                        if (isRu) "Фиксированный круг 60x60dp поверх всех приложений. Показывается ТОЛЬКО когда сахар вне нормы (<3.9 или >10.0). При сахаре в диапазоне 3.9..10.0 пузырёк автоматически скрыт."
                        else "Strictly circular 60x60dp overlay. Visible ONLY when glucose is out of range (<3.9 or >10.0). Automatically hidden during normal range (3.9..10.0)."
                    ),
                    Triple(
                        if (isRu) "Быстрый снуз на 5 минут и круги на воде" else "5-Min Snooze Tap & Water Ripple Wave",
                        if (isRu) "Снуз & Волна" else "Tap Snooze",
                        if (isRu) "Тап по пузырьку скрывает его ровно на 5 минут и открывает приложение. При гипогликемии (<3.9) пузырёк воспроизводит пульсирующий эффект «круги на воде»."
                        else "Tapping bubble snoozes it for exactly 5 minutes and launches MainActivity. Hypoglycemia (<3.9) triggers pulsating outward water ripple waves."
                    ),
                    Triple(
                        if (isRu) "Клинический протокол снуза тревог" else "Clinical Snooze Safety Protocol",
                        if (isRu) "Безопасность" else "Safety Guard",
                        if (isRu) "При гипогликемии — 15 мин снуза с барьером комы (мгновенный повтор сирены при сахаре <2.8 ммоль/л). При гипергликемии — пауза 30–45 мин на действие введённого инсулина."
                        else "Hypo: 15-min snooze with coma guard (immediate alarm if <2.8 mmol/L). Hyper: 30-45 min pause allowing insulin onset with re-escalation if stalled."
                    )
                ),
                cardHeight = 41f
            )

            // Section 6: Clinical AGP & Automated Backups
            y2 = drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "6. Клиническая аналитика AGP, паттерны и Автобэкап"
                else "6. Clinical AGP Analytics, Patterns & Daily Auto-Backup",
                items = listOf(
                    Triple(
                        if (isRu) "Амбулаторный профиль глюкозы (AGP по стандарту ATTD/ADA)" else "Official Ambulatory Glucose Profile (AGP)",
                        if (isRu) "AGP Отчёт" else "AGP PDF",
                        if (isRu) "Генерация медицинского PDF-отчёта для эндокринолога в один клик. Расчёт 12 параметров: GRI, GVI, PGS, TIR, TBR, TAR, CV, SD, eA1c. Импорт архивов баз данных SiDiary (CSV/ZIP)."
                        else "One-click generation of official clinical PDF report. Computes 12 parameters (GRI, GVI, PGS, TIR, CV, SD, eA1c). Direct import of SiDiary CSV/ZIP archives."
                    ),
                    Triple(
                        if (isRu) "Детектор скрытых клинических паттернов" else "Clinical Pattern Recognition Engine",
                        if (isRu) "Паттерны" else "Patterns",
                        if (isRu) "Автоматически распознаёт скрытые ночные провалы в индивидуальные часы сна, феномен утренней зари и вечернюю вариабельность с возможностью скрытия единичных событий (✕)."
                        else "Detects hidden nocturnal dips during user sleep window, dawn phenomenon, and post-meal spikes with individual per-event dismiss (✕)."
                    ),
                    Triple(
                        if (isRu) "Ежедневный автономный автобэкап в 00:00" else "Daily Exact Auto-Backup at 00:00",
                        if (isRu) "Автобэкап" else "Backup",
                        if (isRu) "Точный будильник сохраняет настройки, пороги тревог и базу данных в изолированную песочницу без запроса опасных системных разрешений на доступ к файлам."
                        else "Exact RTC AlarmManager backs up settings, alert thresholds, and database into app sandbox at 23:59:59 without dangerous storage permissions."
                    )
                ),
                cardHeight = 41f
            )

            // Page 2 Footer Note: Medical Disclaimer
            drawFooterNote(
                canvas = canvas2,
                topY = 746f,
                height = 54f,
                noteTitle = if (isRu) "⚖️ Медицинский отказ от ответственности (Дисклеймер):" else "⚖️ Medical Disclaimer Notice:",
                bulletPoints = listOf(
                    if (isRu) "• Приложение TIRUp разработано исключительно для вспомогательного информационного самоконтроля."
                    else "• TIRUp is intended strictly for supplementary lifestyle informational self-monitoring.",
                    if (isRu) "• Всегда перепроверяйте показания инвазивным глюкометром перед инъекцией доз инсулина."
                    else "• Always verify unexpected CGM values with a blood capillary meter prior to insulin dosing decisions."
                )
            )

            document.finishPage(page2)

            val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outputFile = File(outputDir, "TIRUp_User_Manual.pdf")
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

