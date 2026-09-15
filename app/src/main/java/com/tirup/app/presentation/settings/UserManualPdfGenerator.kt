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

            // Section 1: Data Sources & Family BLE Bridge
            y1 = drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "1. Интеграция с источниками данных и Семейный BLE-мост"
                else "1. Linking Data Sources & Family BLE Bridge",
                items = listOf(
                    Triple(
                        if (isRu) "Широковещательные передачи (xDrip+, GDH, Juggluco)" else "Local Broadcast Toggle (xDrip+, GDH)",
                        if (isRu) "Шаг 1" else "Step 1",
                        if (isRu) "В xDrip+ ➔ Настройки ➔ «Межпрограммная интеграция» включите «Широковещательные передачи xDrip» и «Поддержка широковещательной службы» (для передачи IoB/CoB). Приём работает на 100% автономно без интернета."
                        else "In xDrip+ ➔ Settings ➔ 'Inter-app settings' enable 'Broadcast locally' and 'Broadcast service support' (for IoB/CoB). Runs 100% offline without cloud servers."
                    ),
                    Triple(
                        if (isRu) "Бесперебойная работа в фоне (Батарея)" else "Unrestricted Background Running (Battery)",
                        if (isRu) "Шаг 2" else "Step 2",
                        if (isRu) "В настройках Android для TIRUp и источника отключите оптимизацию батареи («Без ограничений») и закрепите TIRUp замком в недавних задачах для надёжного приёма данных и мгновенных тревог."
                        else "In Android settings for TIRUp and source app, disable 'Battery Optimization' and allow unrestricted background execution. Lock TIRUp in recent apps to guarantee uninterrupted sync."
                    ),
                    Triple(
                        if (isRu) "Семейный BLE-мост (без интернета, радиус 10–15м)" else "Family BLE Bridge (100% Offline, 10–15m Range)",
                        if (isRu) "BLE-мост" else "BLE Bridge",
                        if (isRu) "Прямая связь родитель–ребёнок по BLE (импульс 12–15с) без сопряжения и интернета с защитой Family PIN. Для 100% стабильности в фоне держите включённым «Постоянный статус» (Foreground Service)."
                        else "Direct parent-child link over BLE (12-15s bursts) without pairing or internet, secured by Family PIN. For 24/7 background stability keep ongoing 'Lockscreen Status' notification enabled."
                    )
                ),
                cardHeight = 41f
            )

            // Section 2: Homescreen, Lockscreen Widgets & Floating Bubble
            y1 = drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "2. Виджеты рабочего стола, экран блокировки и Пузырёк" else "2. Desktop, Lockscreen Widgets & Floating Bubble",
                items = listOf(
                    Triple(
                        if (isRu) "5 форматов виджетов (5х1, 4х2, 3х2, 2х2, 1х2)" else "5 Widget Formats (5x1, 4x2, 3x2, 2x2, 1x2)",
                        if (isRu) "Рабочий стол" else "Homescreen",
                        if (isRu) "Полоса (5х1), дашборд с 4-часовым HD графиком Canvas (4х2/3х2), фокус (2х2) и вертикальный стек (1х2). Цветовая шкала гликемии, индикаторы IoB (💉), CoB (🍞) и стрик дней в норме (🔥)."
                        else "Horizontal strip (5x1), 4-hour Canvas dashboard (4x2/3x2), compact square (2x2) and vertical glance (1x2). Color-coded glucose scale, IoB (💉), CoB (🍞) and daily streak (🔥)."
                    ),
                    Triple(
                        if (isRu) "Экран блокировки и прозрачность подложки (0%..100%)" else "Lockscreen Status & Background Opacity",
                        if (isRu) "AOD & Шторка" else "AOD & Slider",
                        if (isRu) "Постоянный статус сахара и TIR на экране блокировки/AOD. В настройках доступен плавный ползунок прозрачности 0%..100% с живым окном предпросмотра на фоне обоев рабочего стола."
                        else "Ongoing glucose & TIR notification on Lockscreen/AOD. Features smooth 0%..100% background opacity slider with live desktop wallpaper preview."
                    ),
                    Triple(
                        if (isRu) "Плавающий оверлей «Пузырёк» и Адаптивный снуз" else "Floating Glucose Bubble & Adaptive Snooze",
                        if (isRu) "Пузырёк" else "Bubble",
                        if (isRu) "Кружок поверх экрана (мини 50% в норме, 60dp при тревоге с «кругами на воде» при гипо). Тап глушит сирену и снузит на 15/45м, удержание (≥0.5с) открывает TIRUp."
                        else "Circular overlay (compact 50% in target, 60dp alarm mode with pulsating ripple waves on hypo). Short tap silences alarm & snoozes for 15/45m, hold (≥0.5s) opens TIRUp."
                    )
                ),
                cardHeight = 41f
            )

            // Section 3: Daily Compensator, Weekly & New Year Digest
            drawSection(
                canvas = canvas1,
                startY = y1,
                title = if (isRu) "3. Суточный компенсатор цели, Воскресный и Новогодний дайджест"
                else "3. Daily Target Compensator, Weekly & New Year Digest",
                items = listOf(
                    Triple(
                        if (isRu) "Принцип строгих суток (00:00:00 – 23:59:59)" else "24-Hour Daily Strict Calculus",
                        if (isRu) "Математика" else "Math Engine",
                        if (isRu) "Компенсатор рассчитывает точное время в часах и минутах, которое необходимо провести в норме до конца суток (TIR ≥70% / TING ≥50%). Виджет показывает: «Осталось 2ч 15м в норме» или «Цель 100%»."
                        else "Calculates exact remaining hours/minutes needed in target before midnight (TIR ≥70% / TING ≥50%). Concisely formatted on widgets: 'In range 2h 15m' or 'Goal reached! (100%)'."
                    ),
                    Triple(
                        if (isRu) "Уведомление «Последний шанс для TIR»" else "'Last Chance TIR' Proactive Alert",
                        if (isRu) "Предупреждение" else "Warning",
                        if (isRu) "Срабатывает за 1–2ч до точки математического невозврата, когда оставшееся время суток уже не позволяет достичь суточной цели TIR ≥70% без немедленного возврата в норму."
                        else "Alerts 1h or 2h before the mathematical point of no return when remaining day time cannot mathematically rescue the daily 70% TIR target."
                    ),
                    Triple(
                        if (isRu) "Воскресный дайджест (20:00) и Итоги года 31 декабря" else "Sunday Digest (20:00) & Year-End Digest (Dec 31)",
                        if (isRu) "Дайджесты" else "Digests",
                        if (isRu) "Еженедельный разбор в воскресенье в 20:00 со сравнением динамики (±Δ%). 31 декабря в 20:00 формируется праздничный новогодний дайджест за год с экспортом открытки в PDF."
                        else "Weekly review every Sunday at 20:00 with week-over-week dynamic delta comparison (±Δ%). On Dec 31 at 20:00 delivers an annual review modal dialog and postcard PDF."
                    )
                ),
                cardHeight = 41f
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
            // PAGE 2: Smart Alarms, Clinical Analytics, HbA1c & Zero-Lag Backups
            // =========================================================================
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = document.startPage(pageInfo2)
            val canvas2 = page2.canvas

            var y2 = drawHeader(
                canvas = canvas2,
                pageNum = 2,
                totalPages = 2,
                pageTitle = if (isRu) "TIRUp • Руководство пользователя (Часть 2)" else "TIRUp • User Manual (Part 2)",
                pageSubtitle = if (isRu) "Система тревог, экстренные SMS, клинический AGP, журнал HbA1c и Zero-Lag архивы"
                else "Safety alarms, emergency SMS, clinical AGP, HbA1c journal & Zero-Lag archives"
            )

            // Section 4: 4-Tier Safety Alarms & Signal Loss
            y2 = drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "4. Четырёхуровневая система тревог и экстренная безопасность (Tier 1–4)"
                else "4. 4-Tier Safety Alarms & Emergency Protocols (Tier 1–4)",
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
                        if (isRu) "DND-обход, громкость ≥80%, вспышка при <3.0 / >13.9. Без реакции: гипо каждые 5м, гипер каждые 15м. Снуз: 15м (гипо с защитой от комы при <2.8) / 45–60м (гипер с IoB). Глушение кнопками громкости."
                        else "DND bypass, volume ≥80%, camera strobe for <3.0 / >13.9. Unanswered: hypo every 5m, hyper every 15m. Snooze: 15m (coma guard <2.8) / 45-60m (hyper with IoB). Mute via volume buttons."
                    ),
                    Triple(
                        if (isRu) "Уровень 4: Потеря сигнала (20–25 мин) и Экстренные SOS SMS" else "Tier 4: Sleep-Aware Signal Loss & Emergency SOS SMS",
                        if (isRu) "Будильник & SOS" else "Alarm & SOS",
                        if (isRu) "Сигнал будильника (USAGE_ALARM + Bypass DND) с расписанием день/ночь. При игноре сирены гипо 5м отправляет близким SOS SMS с GPS. При шатдауне интернета близкие запрашивают сахар по SMS («сахар»)."
                        else "Treated as alarm with day/night wake schedule. Sends SMS with GPS if severe hypo siren ignored for 5m. Trusted contact can query glucose offline via SMS ('sugar')."
                    )
                ),
                cardHeight = 41f
            )

            // Section 5: Clinical AGP, Patterns & Laboratory HbA1c
            y2 = drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "5. Клиническая аналитика AGP, паттерны и Журнал HbA1c"
                else "5. Clinical AGP, Patterns & Laboratory HbA1c Journal",
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
                        if (isRu) "Журнал лабораторного HbA1c и квартальный контроль" else "Laboratory HbA1c Journal & Quarterly Tracking",
                        if (isRu) "HbA1c & GMI" else "HbA1c & GMI",
                        if (isRu) "Ввод анализов крови, сопоставление с 90-дневным расчётным GMI сенсора и TIR. Напоминания раз в 90 дней (защита от спама, кнопка пропуска) и экспорт 1-страничной выписки в PDF."
                        else "Logging lab blood tests, comparison with 90-day sensor GMI and TIR. Quarterly reminders every 90 days (anti-spam guard, skip button) and 1-page PDF export."
                    )
                ),
                cardHeight = 41f
            )

            // Section 6: Device Supplies, Zero-Lag Archives & Backups
            drawSection(
                canvas = canvas2,
                startY = y2,
                title = if (isRu) "6. Расходники, Zero-Lag архивы и Резервное копирование"
                else "6. Device Supplies, Zero-Lag Archives & Backups",
                items = listOf(
                    Triple(
                        if (isRu) "Счётчики смены устройств и расходников (сенсор, канюля, ланцет)" else "Device & Supplies Change Trackers (Sensor, Cannula, Lancet)",
                        if (isRu) "Расходники" else "Supplies",
                        if (isRu) "Учёт срока службы сенсора CGM, канюли помпы и ланцета. Автоопределение смены из заметок («канюля»), цветовая индикация (зелёный/жёлтый/красный) и предупреждения об истечении."
                        else "Tracks remaining lifespan of CGM sensor, infusion set/cannula and lancet. Auto-detects changes from notes ('cannula'), color alerts (green/amber/red) & timely replacement reminders."
                    ),
                    Triple(
                        if (isRu) "Zero-Lag годовая архивация данных" else "Zero-Lag Annual History Archiving",
                        if (isRu) "Архив года" else "Year Archive",
                        if (isRu) "Автоматическое сохранение завершённых лет в архивы tirup_readings_YYYY.csv. База данных остаётся компактной и летает мгновенно даже при непрерывном ведении 1–5 лет."
                        else "Automatically seals past calendar years into tirup_readings_YYYY.csv files. Database remains lightweight and snappy even across 1–5 years of continuous records."
                    ),
                    Triple(
                        if (isRu) "Автобэкап в полночь и ручной экспорт в ZIP" else "Midnight Auto-Backup & Manual ZIP Export",
                        if (isRu) "Бэкап & ZIP" else "Backup & ZIP",
                        if (isRu) "Ежедневный ночной автобэкап в изолированную песочницу в 23:59:59 без запроса системных прав. Экспорт полного архива настроек и базы данных в ZIP в Documents/TIRUp/Backups/."
                        else "Daily midnight auto-backup into app sandbox at 23:59:59 without storage permissions. Manual export of full database & settings ZIP into Documents/TIRUp/Backups/."
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

