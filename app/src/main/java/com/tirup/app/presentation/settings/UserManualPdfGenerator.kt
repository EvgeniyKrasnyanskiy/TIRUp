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
                        if (isRu) "Широковещательные передачи xDrip" else "Local Broadcast Toggle",
                        if (isRu) "Шаг 1" else "Step 1",
                        if (isRu) "xDrip+ ➔ Настройки xDrip+ ➔ «Межпрограммная интеграция» ➔ включите «Широковещательные передачи xDrip» и «Совместимый широковещатель». Приём работает полностью автономно без интернета при каждом замере."
                        else "xDrip+ ➔ Settings ➔ 'Inter-app settings' ➔ enable 'Broadcast locally' and 'Compatible Broadcast'. Runs 100% offline without cloud servers."
                    ),
                    Triple(
                        if (isRu) "Широковещательная служба и веб-сервер (IoB)" else "Broadcast Service & Web Server (IoB)",
                        if (isRu) "Шаг 2" else "Step 2",
                        if (isRu) "В «Межпрограммная интеграция» включите «Поддержка широковещательной службы», а в «Локальный веб-сервер» включите «Включить сообщения об IoB в конечной точке Web Service API...» для передачи активного инсулина (IoB)."
                        else "In 'Inter-app settings' enable 'Broadcast service support' and under 'Local Web Server' enable 'Show IOB in Web Service API endpoints' for active insulin (IoB)."
                    ),
                    Triple(
                        if (isRu) "Бесперебойная работа в фоне (Батарея)" else "Unrestricted Background Running (Battery)",
                        if (isRu) "Шаг 3" else "Step 3",
                        if (isRu) "В настройках Android для TIRUp и источника (xDrip+) отключите оптимизацию батареи и разрешите работу в фоне без ограничений. Это гарантирует надёжный приём данных и тревог."
                        else "In Android settings for TIRUp and xDrip+, disable 'Battery Optimization' and allow unrestricted background execution. Guarantees uninterrupted data sync and instant alarms."
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
                        if (isRu) "<3.9 ммоль/л — красный; 3.9..7.8 — бледно-зелёный; 7.9..10.0 — насыщенный изумрудный; 10.1..13.9 — оранжевый; >13.9 — красный."
                        else "<3.9 mmol/L Red; 3.9..7.8 Pale Green; 7.9..10.0 Saturated Emerald; 10.1..13.9 Orange; >13.9 Red. Syncs across all widgets."
                    ),
                    Triple(
                        if (isRu) "Бейджи IoB/CoB и Стрик (🔥 X д.)" else "IoB/CoB Badges & Streak (🔥)",
                        if (isRu) "Индикаторы" else "Badges",
                        if (isRu) "Отображают дозы активного инсулина (💉) и углеводов (🍞). Счётчик 🔥 X д. показывает серию дней в цели TIR."
                        else "Display active insulin (💉) & carbs (🍞). Streak badge 🔥 shows consecutive days reaching TIR target."
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
            drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "3. Суточная математика компенсатора цели (TIR ≥70% / TING ≥50%)"
                else "3. Strict 24-Hour Target Compensator (TIR ≥70% / TING ≥50%)",
                items = listOf(
                    Triple(
                        if (isRu) "Принцип строгих суток (00:00:00 – 23:59:59)" else "24-Hour Daily Strict Calculus",
                        if (isRu) "Математика" else "Math Engine",
                        if (isRu) "Компенсатор рассчитывает точное время в часах и минутах, которое необходимо провести в норме до конца суток. Виджет показывает: «Осталось 2ч 15м в норме» или «Цель 100%»."
                        else "Calculates exact remaining hours/minutes needed in target before midnight. Formatted concisely on widgets: 'In range 2h 15m' or 'Goal reached! (100%)'."
                    ),
                    Triple(
                        if (isRu) "Уведомление «Последний шанс для TIR»" else "'Last Chance TIR' Proactive Alert",
                        if (isRu) "Предупреждение" else "Warning",
                        if (isRu) "Срабатывает за 1ч, 1.5ч или 2ч до точки невозврата, когда суммарное оставшееся время суток становится меньше времени, необходимого для достижения целевого TIR ≥70%."
                        else "Alerts 1h, 1.5h or 2h before mathematical point of no return when remaining day time cannot mathematically rescue the daily 70% TIR target."
                    ),
                    Triple(
                        if (isRu) "Воскресный аналитический дайджест (20:00)" else "Sunday Compensation Digest (20:00)",
                        if (isRu) "Дайджест недели" else "Weekly Review",
                        if (isRu) "Каждое воскресенье формирует интерактивный отчёт: средний сахар, TIR, вариабельность и динамику к прошлой неделе (±Δ%). Сохраняется в архиве отчётов."
                        else "Every Sunday generates an interactive review: avg glucose, TIR, variability, and week-over-week comparison (±Δ%). Saved to persistent reports archive."
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
                        if (isRu) "Уровень 1: Предиктивный прогноз и тренд на графике" else "Tier 1: Predictive Forecast & Chart Trend",
                        if (isRu) "Прогноз 25м" else "Forecast 25m",
                        if (isRu) "Фиолетовые точки и пунктир на суточном графике экстраполируют сахар на 25 мин вперёд без необходимости ввода ФЧИ/УК. Мягкий сигнал тревоги за 15 мин до выхода из нормы предупреждает гипогликемию."
                        else "Purple dots and dashed trajectory extrapolate glucose 25 min ahead without ISF/ICR. Gentle chime 15m prior to target breach prevents hypoglycemia."
                    ),
                    Triple(
                        if (isRu) "Уровень 2: Основная тревога (кастомный диапазон, 3–5 точек)" else "Tier 2: Confirmed Departure (Custom Range, 3–5 pts)",
                        if (isRu) "Тройной тон" else "Triple Tone",
                        if (isRu) "Подтверждённый выход 3 точек (15м для 5-мин) или 5 точек (5м для 1-мин). Тройной сигнал. При падающем сахаре и наличии болюса (IoB) тревога гипергликемии глушится. Повтор: 15м (гипо) и 45м (гипер, 60м с IoB)."
                        else "Confirmed departure: 3 pts (15m for 5-min) or 5 pts (5m for 1-min). Triple tone. Muted on falling glucose with IoB. Repeats 15m (hypo) and 45m (hyper, 60m with IoB)."
                    ),
                    Triple(
                        if (isRu) "Уровень 3: Критическая сирена («кричащая»)" else "Tier 3: Critical Siren (Extremes & Prolonged)",
                        if (isRu) "Сирена + Вспышка" else "Alarm + Strobe",
                        if (isRu) "DND-обход, громкость ≥80%, вспышка при <3.0 / >13.9. Без реакции: гипо каждые 5м, гипер каждые 15м. Снуз: 15м (гипо) / 45–60м (гипер с IoB). Глушение тапом/кнопками."
                        else "DND bypass, volume ≥80%, camera strobe for <3.0 / >13.9. Unanswered: hypo every 5m, hyper every 15m. Snooze: 15m (hypo) / 45-60m (hyper with IoB). Mute via tap/buttons."
                    ),
                    Triple(
                        if (isRu) "Уровень 4: Потеря сигнала (20–25 мин, будильник + расписание дня/ночи)" else "Tier 4: Sleep-Aware Signal Loss Alarm (20–25 min)",
                        if (isRu) "Будильник DND" else "Bypass DND",
                        if (isRu) "Приравнена к будильнику (USAGE_ALARM + Bypass DND). Срабатывает через 20–25 мин (с учётом шага сенсора 5 мин). Ночью: 6x5м ➔ 6x10м ➔ 6x20м ➔ каждые 30м. Днём: 3x5м ➔ 3x20м ➔ каждый 1 час."
                        else "Treated as alarm (USAGE_ALARM + Bypass DND). Triggers in 20-25 min (factoring 5-min cadence). Night: 6x5m ➔ 6x10m ➔ 6x20m ➔ 30m. Day: 3x5m ➔ 3x20m ➔ 60m."
                    ),
                    Triple(
                        if (isRu) "Экстренное SOS SMS и Офлайн-запрос (при потере сознания / шатдауне)" else "Emergency SOS SMS & Offline SMS Query",
                        if (isRu) "Близкие & SOS" else "SOS & Offline",
                        if (isRu) "При тяжелой гипо (<3.0) отправляет SMS близким с координатами. При шатдауне интернета контакт запрашивает сахар по SMS («сахар»). Важно: в Android выдайте право SMS (если ответ не идёт, переключите Разрешить/Запретить)."
                        else "Sends single-segment SMS (≤67 chars) with GPS if severe hypo (<3.0) siren is ignored for 5m. Trusted contact can query real-time glucose & TIR offline via SMS."
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
                        if (isRu) "Полупрозрачный кружок поверх экрана. Показывается ТОЛЬКО когда сахар вне нормы (<3.9 или >10.0). При сахаре в диапазоне 3.9..10.0 пузырёк автоматически скрыт."
                        else "Strictly circular 60x60dp overlay. Visible ONLY when glucose is out of range (<3.9 or >10.0). Automatically hidden during normal range (3.9..10.0)."
                    ),
                    Triple(
                        if (isRu) "Умный снуз (15м гипо / 45м гипер) и круги на воде" else "Smart Snooze (15m hypo / 45m hyper) & Water Ripple Wave",
                        if (isRu) "Снуз & Волна" else "Tap Snooze",
                        if (isRu) "Тап по пузырьку глушит звук и скрывает его на 15 мин (гипо) или 45 мин (гипер). Повторный показ из паузы беззвучен. При гипо (<3.9) воспроизводит пульсацию «круги на воде»."
                        else "Tapping bubble silences sound and snoozes for 15m (hypo) or 45m (hyper). Quiet re-emergence. Hypoglycemia (<3.9) triggers pulsating outward water ripple waves."
                    ),
                    Triple(
                        if (isRu) "Клинический протокол снуза тревог" else "Clinical Snooze Safety Protocol",
                        if (isRu) "Безопасность" else "Safety Guard",
                        if (isRu) "При гипо — 15м снуза с защитой от комы (сброс при критически низком сахаре (<2.8) или быстром падении). При гипер — пауза 45м, а при активном инсулине (IoB ≥0.2 / ≥0.5 при >13.9) — авто-продление до 60м."
                        else "Hypo: 15m snooze with coma guard (resets if <2.8 or drop rate ≤-0.3). Hyper: 45m pause, extended to 60m with active bolus (IoB ≥0.2 / ≥0.5 if >13.9)."
                    )
                ),
                cardHeight = 41f
            )

            // Section 6: Clinical AGP & Automated Backups
            drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "6. Клиническая аналитика AGP, паттерны и Автобэкап"
                else "6. Clinical AGP Analytics, Patterns & Daily Auto-Backup",
                items = listOf(
                    Triple(
                        if (isRu) "Амбулаторный гликемический профиль (AGP по стандарту ATTD/ADA)" else "Official Ambulatory Glucose Profile (AGP)",
                        if (isRu) "AGP Отчёт" else "AGP PDF",
                        if (isRu) "Генерация медицинского PDF-отчёта для эндокринолога в один клик. Расчёт 12 параметров: GRI, GVI, PGS, TIR, TBR, TAR, CV, SD, eA1c. Импорт файлов xDrip+ (CSV/ZIP)."
                        else "One-click generation of official clinical PDF report. Computes 12 parameters (GRI, GVI, PGS, TIR, CV, SD, eA1c). Direct import of xDrip+ (CSV/ZIP) archives."
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
                        if (isRu) "Автоматический бэкап в полночь: настройки и база данных сохраняются в изолированную песочницу без запроса опасных системных разрешений на доступ к файлам."
                        else "Midnight auto-backup: settings and database are saved into app sandbox without dangerous storage permissions."
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
                    if (isRu) "• Всегда перепроверяйте показания глюкометром по капле крови перед инъекцией доз инсулина."
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

