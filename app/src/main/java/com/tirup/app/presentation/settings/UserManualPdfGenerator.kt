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
                color = Color.rgb(37, 99, 235) // ActionBlue
                strokeWidth = 1.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
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
            val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(239, 246, 255) // Blue 50
                style = Paint.Style.FILL
            }
            val noteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(96, 165, 250) // Blue 400
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
            }
            val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 64, 175) // Blue 800
                textSize = 7.3f
            }

            var y = 44f

            // 1. Header Banner
            canvas.drawText(
                if (isRu) "TIRUp • Руководство пользователя" else "TIRUp • User Manual",
                30f,
                y,
                titlePaint
            )
            y += 14f
            canvas.drawText(
                if (isRu) "Пошаговая инструкция: связка с xDrip+, мониторинг, 4 уровня тревог, снуз и экспорт"
                else "Step-by-step user guide: xDrip+ linking, live monitoring, 4-tier alarms, snooze & data export",
                30f,
                y,
                subtitlePaint
            )
            y += 8f
            canvas.drawLine(30f, y, 565f, y, linePaint)
            y += 14f

            fun drawSection(title: String, items: List<Triple<String, String, String>>, cardHeight: Float = 44f) {
                canvas.drawText(title, 30f, y, sectionPaint)
                y += 13f

                for ((itemTitle, itemBadge, itemDesc) in items) {
                    val rect = RectF(30f, y, 565f, y + cardHeight)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, cardBorderPaint)

                    // Title and Badge
                    canvas.drawText(itemTitle, 38f, y + 12.5f, itemTitlePaint)
                    if (itemBadge.isNotEmpty()) {
                        val badgeWidth = itemBadgePaint.measureText(itemBadge)
                        canvas.drawText(itemBadge, 557f - badgeWidth, y + 12.5f, itemBadgePaint)
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

                    y += cardHeight + 4.5f
                }
                y += 6f
            }

            // Section 1: xDrip+ Setup (2 items)
            drawSection(
                if (isRu) "1. Интеграция с xDrip+ (Локальный приём без интернета)" else "1. Linking with xDrip+ (Local Broadcast)",
                listOf(
                    Triple(
                        if (isRu) "Настройка в приложении xDrip+" else "Settings in xDrip+ App",
                        if (isRu) "Шаг 1" else "Step 1",
                        if (isRu) "Откройте xDrip+ ➔ Настройки (Settings) ➔ «Межпрограммная интеграция» (Inter-app settings) ➔ включите «Широковещательный показ данных» (Broadcast locally)."
                        else "Open xDrip+ ➔ Settings ➔ 'Inter-app settings' ➔ enable 'Broadcast locally' toggle. Requires no cloud or internet."
                    ),
                    Triple(
                        if (isRu) "Автономный фоновый приём в TIRUp" else "Zero-latency Background Capture",
                        if (isRu) "Шаг 2" else "Step 2",
                        if (isRu) "TIRUp перехватывает замеры напрямую через системный интент Android при каждом замере сенсора (раз в 5 минут). Работает в фоне без интернета и серверов."
                        else "TIRUp captures CGM broadcasts via internal Android intents every 5 minutes. Operates 100% locally with zero battery drain."
                    )
                ),
                cardHeight = 42f
            )

            // Section 2: Core Screens (3 items)
            drawSection(
                if (isRu) "2. Экраны приложения и суточный контроль" else "2. Application Screens & Daily Management",
                listOf(
                    Triple(
                        if (isRu) "Экран «Текущие сутки» и Компенсатор цели" else "Screen: 'Today's Focus' & Daily Compensator",
                        if (isRu) "Главный экран" else "Home Screen",
                        if (isRu) "Отображает текущий сахар, стрелку тренда и динамику. Суточный компенсатор (00:00–23:59) показывает точное время в часах для достижения цели TIR ≥70% или TING ≥50%."
                        else "Shows live glucose, velocity trend, and strict daily compensator (00:00–23:59) calculating exact remaining hours needed for target."
                    ),
                    Triple(
                        if (isRu) "Экран «Аналитика и тренды» (AGP)" else "Screen: 'Analytics & Trends' (AGP Profile)",
                        if (isRu) "Аналитика" else "Analytics",
                        if (isRu) "Детектор скрытых паттернов (утренняя заря, ночные гипо) с индивидуальным скрытием (✕). Единая карточка AGP с тумблером «[📊 График | 🔢 Параметры]» (12 метрик)."
                        else "Clinical pattern detection with per-event dismiss (✕). Unified AGP card with '[📊 Chart | 🔢 Metrics]' toggle for all 12 parameters."
                    ),
                    Triple(
                        if (isRu) "Экран «Отчёты» (AGP для эндокринолога)" else "Screen: 'Reports' (Official AGP PDF)",
                        if (isRu) "Отчёты" else "Reports",
                        if (isRu) "Генерация стандартизированного амбулаторного отчёта AGP для врача в один клик. Поддержка импорта файлов баз данных SiDiary (CSV / ZIP) за 7, 14, 30, 90 дней."
                        else "One-click generation of official AGP PDF reports for doctors. Direct import of xDrip+ SiDiary database archives (CSV/ZIP)."
                    )
                ),
                cardHeight = 44f
            )

            // Section 3: Smart Alarms (4 items)
            drawSection(
                if (isRu) "3. Интеллектуальные тревоги (4 уровня) и Снуз" else "3. Smart 4-Tier Alarms & Safety Snooze",
                listOf(
                    Triple(
                        if (isRu) "Уровень 1 (Предиктивный прогноз)" else "Tier 1 (Predictive Forecast)",
                        if (isRu) "Мягкий сигнал" else "Soft Chime",
                        if (isRu) "Математическая регрессия вычисляет скорый выход за границы диапазона с точным временем события (напр., «в 16:42»). Мягкий перезвон без испуга."
                        else "Weighted regression predicts impending boundary departures with exact calculated time (e.g. 'at 16:42'). Soft chime."
                    ),
                    Triple(
                        if (isRu) "Уровень 2 (Основной подтверждённый)" else "Tier 2 (Confirmed Boundary Departure)",
                        if (isRu) "Тройной бип" else "Triple Tone",
                        if (isRu) "Срабатывает при подтверждённом выходе 5 точек за границы. Воспроизводит отчётливый тройной медицинский сигнал с паузой 1.5 секунды между бипами."
                        else "Triggers upon 5 consecutive points outside target. Emits a distinct triple medical tone with 1.5s pause intervals."
                    ),
                    Triple(
                        if (isRu) "Уровень 3 (Критическая сирена) и глушение кнопками" else "Tier 3 (Critical Alarm) & Hardware Mute",
                        if (isRu) "Сирена + Вспышка" else "Siren + Strobe",
                        if (isRu) "Серия сирены ~12 сек на аудиопотоке будильника со стробоскопом вспышки при экстремальных сахарах (<3.0 / >13.9). Мгновенное глушение любой кнопкой громкости/питания."
                        else "High-priority siren on alarm stream with camera strobe. Instant muting via hardware volume or power buttons without clearing snooze."
                    ),
                    Triple(
                        if (isRu) "Уровень 4 (Потеря сигнала сенсора >20 мин)" else "Tier 4 (Signal Loss Alarm >20 min)",
                        if (isRu) "Нисходящий тон" else "Descending Tone",
                        if (isRu) "Звучит при отсутствии свежих замеров более 20 минут. Повторяется с прогрессивным геометрическим интервалом (20 ➔ 40 ➔ 80 ➔ 160 мин), не раздражая пользователя."
                        else "Alerts if no fresh readings arrive for >20 min. Features progressive geometric backoff (20 ➔ 40 ➔ 80 min) to prevent fatigue."
                    )
                ),
                cardHeight = 43f
            )

            // Section 4: Snooze & Backup (2 items)
            drawSection(
                if (isRu) "4. Клинический Снуз и Автобэкап" else "4. Clinical Snooze & Automated Backup",
                listOf(
                    Triple(
                        if (isRu) "Адаптивный Снуз (Защита от комы и гипер)" else "Adaptive Clinical Snooze Protocol",
                        if (isRu) "Умная защита" else "Smart Safety",
                        if (isRu) "При гипо — 15 мин снуза с предохранителем (повтор сирены при сахаре <2.8 ммоль/л). При гипер — пауза 30–45 мин на действие инсулина с реэскалацией сирены при застое."
                        else "Hypo: 15-min snooze with coma guard (instant alert if <2.8 mmol/L). Hyper: 30-45 min pause for insulin onset, re-escalating if glucose stalls."
                    ),
                    Triple(
                        if (isRu) "Ежедневный автобэкап в 23:59:59" else "Daily Auto-Backup at 23:59:59",
                        if (isRu) "Без разрешений" else "Isolated Sandbox",
                        if (isRu) "Точный будильник сохраняет настройки и базу данных в защищённую изолированную папку приложения без запроса опасных системных разрешений на доступ к файлам."
                        else "Exact RTC AlarmManager backs up settings and database into app sandbox at 23:59:59 without dangerous storage permissions."
                    )
                ),
                cardHeight = 42f
            )

            // Footer Note Banner
            val noteRect = RectF(30f, 736f, 565f, 808f)
            canvas.drawRoundRect(noteRect, 7f, 7f, noteBgPaint)
            canvas.drawRoundRect(noteRect, 7f, 7f, noteBorderPaint)

            canvas.drawText(
                if (isRu) "💡 Безопасность, автономность и медицинская справка:"
                else "💡 Privacy, Offline Autonomy & Medical Reference Notice:",
                38f,
                749f,
                itemTitlePaint.apply { color = Color.rgb(30, 64, 175) }
            )
            val nText1 = if (isRu) {
                "• 100% Автономность: приложение TIRUp работает локально, не требует интернета и никогда не передаёт личные медицинские данные."
            } else {
                "• 100% Offline: TIRUp operates entirely locally on your device with zero cloud tracking and no remote data leakage."
            }
            val nText2 = if (isRu) {
                "• Экспорт SiDiary: Меню xDrip+ (3 точки) ➔ «Функции импорта/экспорта» ➔ «Экспорт CSV (формат SiDiary)» для загрузки архива в TIRUp."
            } else {
                "• SiDiary Export: xDrip+ Menu (3 dots) ➔ 'Import/Export features' ➔ 'Export CSV (SiDiary format)' to load data into TIRUp."
            }
            val nText3 = if (isRu) {
                "• Дисклеймер: TIRUp не является медицинским прибором. Всегда проверяйте спорные замеры глюкометром перед инъекцией инсулина."
            } else {
                "• Disclaimer: TIRUp is for self-monitoring. Always verify anomalous readings with a capillary blood meter before insulin dosing."
            }
            canvas.drawText(nText1, 38f, 763f, noteTextPaint)
            canvas.drawText(nText2, 38f, 775f, noteTextPaint)
            canvas.drawText(nText3, 38f, 787f, noteTextPaint)

            document.finishPage(page)

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

