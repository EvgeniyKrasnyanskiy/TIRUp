package com.tirup.app.presentation.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Professional Clinical & Technical User Manual PDF Generator.
 * Generates an exhaustive 6-page A4 manual with classic book typography (chapters, sections,
 * in-depth explanatory paragraphs, bullet lists with bold titles, and dynamic clinical callout boxes).
 */
class UserManualPdfGenerator(private val context: Context) {

    enum class CalloutType {
        INFO, TIP, WARNING, CRITICAL
    }

    suspend fun generateUserManualPdf(isRu: Boolean): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val totalPages = 6
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.0"
            } catch (_: Exception) {
                "2.2.0"
            }

            // Typography Paints
            val docHeaderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val docHeaderChapterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.0f
            }
            val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.8f
            }
            val chapterTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 12.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val chapterSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val sectionHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85) // Slate 700
                textSize = 7.4f
            }
            val bulletSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bulletTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 7.4f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tocTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 64, 175)
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tocBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 7.0f
            }
            val calloutTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 7.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val calloutBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 7.1f
            }
            val calloutBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val calloutBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val calloutAccentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            // Helper: Running header & footer on each page
            fun drawRunningHeaderAndFooter(canvas: Canvas, pageNum: Int, chapterTitle: String) {
                val headerDocTitle = if (isRu) "TIRUp • Руководство пользователя и клинический справочник"
                else "TIRUp • User Manual & Clinical Reference"
                canvas.drawText(headerDocTitle, 36f, 26f, docHeaderTitlePaint)

                val chapterStr = if (chapterTitle.isNotBlank()) "— $chapterTitle" else ""
                val chapterX = 36f + docHeaderTitlePaint.measureText(headerDocTitle) + 6f
                if (chapterStr.isNotEmpty()) {
                    canvas.drawText(chapterStr, chapterX, 26f, docHeaderChapterPaint)
                }

                val pageStr = if (isRu) "Стр. $pageNum из $totalPages" else "Page $pageNum of $totalPages"
                val pageStrWidth = pageNumPaint.measureText(pageStr)
                canvas.drawText(pageStr, 559f - pageStrWidth, 26f, pageNumPaint)
                canvas.drawLine(36f, 32f, 559f, 32f, rulePaint)

                // Footer
                canvas.drawLine(36f, 812f, 559f, 812f, rulePaint)
                val footerDisclaimer = if (isRu) "TIRUp v$appVersion • Архитектура Zero-Lag • 100% Автономный медицинский спутник"
                else "TIRUp v$appVersion • Zero-Lag Architecture • 100% Offline Medical Companion"
                canvas.drawText(footerDisclaimer, 36f, 824f, docHeaderChapterPaint)
                val appStamp = "https://github.com/EvgeniyKrasnyanskiy/TIRUp"
                val stampWidth = docHeaderChapterPaint.measureText(appStamp)
                canvas.drawText(appStamp, 559f - stampWidth, 824f, docHeaderChapterPaint)
            }

            // Helper: draw body paragraph with automatic word wrapping
            fun drawParagraph(canvas: Canvas, startY: Float, text: String, maxWidth: Float = 523f, lineHeight: Float = 10.0f): Float {
                var y = startY
                val words = text.split(" ")
                var line = ""
                for (w in words) {
                    val test = if (line.isEmpty()) w else "$line $w"
                    if (bodyPaint.measureText(test) <= maxWidth) {
                        line = test
                    } else {
                        canvas.drawText(line, 36f, y, bodyPaint)
                        line = w
                        y += lineHeight
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 36f, y, bodyPaint)
                    y += lineHeight
                }
                return y + 2f
            }

            // Helper: section heading
            fun drawSectionHeading(canvas: Canvas, startY: Float, heading: String): Float {
                var y = startY + 3f
                canvas.drawText(heading, 36f, y, sectionHeadingPaint)
                y += 9.5f
                return y
            }

            // Helper: bullet point with BOLD title and normal description, with proper indentation
            fun drawBulletPoint(
                canvas: Canvas,
                startY: Float,
                title: String,
                desc: String,
                maxWidth: Float = 523f,
                lineHeight: Float = 10.0f
            ): Float {
                var y = startY
                canvas.drawText("•", 38f, y, bulletSymbolPaint)

                val titleWithColon = "$title: "
                val titleWidth = bulletTitlePaint.measureText(titleWithColon)

                val words = desc.split(" ")
                var line = ""
                val firstLineAvail = maxWidth - 16f - titleWidth
                var wordIndex = 0

                while (wordIndex < words.size) {
                    val w = words[wordIndex]
                    val test = if (line.isEmpty()) w else "$line $w"
                    if (bodyPaint.measureText(test) <= firstLineAvail) {
                        line = test
                        wordIndex++
                    } else {
                        break
                    }
                }

                // Draw first line: Title (bold) + start of desc
                canvas.drawText(titleWithColon, 48f, y, bulletTitlePaint)
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 48f + titleWidth, y, bodyPaint)
                }

                // Subsequent wrapped lines (indented to 48f)
                val subLineAvail = maxWidth - 12f
                line = ""
                while (wordIndex < words.size) {
                    val w = words[wordIndex]
                    val test = if (line.isEmpty()) w else "$line $w"
                    if (bodyPaint.measureText(test) <= subLineAvail) {
                        line = test
                        wordIndex++
                    } else {
                        y += lineHeight
                        canvas.drawText(line, 48f, y, bodyPaint)
                        line = w
                        wordIndex++
                    }
                }
                if (line.isNotEmpty()) {
                    y += lineHeight
                    canvas.drawText(line, 48f, y, bodyPaint)
                }

                return y + 3f
            }

            // Helper: dynamic callout box (height computed automatically from wrapped text)
            fun drawCallout(
                canvas: Canvas,
                startY: Float,
                type: CalloutType,
                title: String,
                text: String,
                boxWidth: Float = 523f
            ): Float {
                val y = startY + 2f
                val (bgColor, borderColor, accentColor, textColor) = when (type) {
                    CalloutType.INFO -> listOf(Color.rgb(239, 246, 255), Color.rgb(191, 219, 254), Color.rgb(37, 99, 235), Color.rgb(30, 64, 175))
                    CalloutType.TIP -> listOf(Color.rgb(236, 253, 245), Color.rgb(167, 243, 208), Color.rgb(16, 185, 129), Color.rgb(6, 95, 70))
                    CalloutType.WARNING -> listOf(Color.rgb(254, 252, 232), Color.rgb(254, 240, 138), Color.rgb(245, 158, 11), Color.rgb(146, 64, 14))
                    CalloutType.CRITICAL -> listOf(Color.rgb(254, 242, 242), Color.rgb(254, 202, 202), Color.rgb(239, 68, 68), Color.rgb(153, 27, 27))
                }
                calloutBgPaint.color = bgColor
                calloutBorderPaint.color = borderColor
                calloutAccentBarPaint.color = accentColor
                calloutTitlePaint.color = textColor
                calloutBodyPaint.color = textColor

                val textMaxWidth = boxWidth - 24f
                val words = text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = ""
                for (w in words) {
                    val test = if (currentLine.isEmpty()) w else "$currentLine $w"
                    if (calloutBodyPaint.measureText(test) <= textMaxWidth) {
                        currentLine = test
                    } else {
                        lines.add(currentLine)
                        currentLine = w
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }

                val lineHeight = 9.4f
                val totalHeight = 15f + (lines.size * lineHeight) + 6f
                val boxRect = RectF(36f, y, 36f + boxWidth, y + totalHeight)
                canvas.drawRoundRect(boxRect, 4f, 4f, calloutBgPaint)
                canvas.drawRoundRect(boxRect, 4f, 4f, calloutBorderPaint)

                // Left accent bar
                val barRect = RectF(36f, y, 40f, y + totalHeight)
                canvas.drawRoundRect(barRect, 2f, 2f, calloutAccentBarPaint)

                canvas.drawText(title, 46f, y + 11.5f, calloutTitlePaint)

                var lineY = y + 22f
                for (l in lines) {
                    canvas.drawText(l, 46f, lineY, calloutBodyPaint)
                    lineY += lineHeight
                }

                return y + totalHeight + 5f
            }

            // =========================================================================
            // PAGE 1: Введение, Быстрый старт, Оглавление и Глава 1 (Связь и BLE-мост)
            // =========================================================================
            val page1 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c1 = page1.canvas
            drawRunningHeaderAndFooter(c1, 1, if (isRu) "Введение и Глава 1" else "Intro & Chapter 1")

            var y1 = 48f
            c1.drawText(if (isRu) "TIRUp • ПОЛНОЕ РУКОВОДСТВО ПОЛЬЗОВАТЕЛЯ" else "TIRUp • COMPREHENSIVE USER MANUAL", 36f, y1, chapterTitlePaint)
            y1 += 12f
            c1.drawText(if (isRu) "Клинический справочник, автономная архитектура, тревоги и защита безопасности" else "Clinical reference, zero-cloud architecture, safety alarms and device integration", 36f, y1, chapterSubtitlePaint)
            y1 += 14f

            // Table of Contents Box
            val tocRect = RectF(36f, y1, 559f, y1 + 38f)
            calloutBgPaint.color = Color.rgb(248, 250, 252)
            calloutBorderPaint.color = Color.rgb(203, 213, 225)
            c1.drawRoundRect(tocRect, 4f, 4f, calloutBgPaint)
            c1.drawRoundRect(tocRect, 4f, 4f, calloutBorderPaint)
            c1.drawText(if (isRu) "СОДЕРЖАНИЕ РУКОВОДСТВА:" else "TABLE OF CONTENTS:", 44f, y1 + 12f, tocTitlePaint)
            c1.drawText(
                if (isRu) "Гл. 1: Связь и BLE-мост (стр. 1) • Гл. 2: Надежность в фоне и OEM (стр. 2) • Гл. 3: Система тревог и Экран спасения (стр. 3)"
                else "Ch. 1: Linking & BLE (p. 1) • Ch. 2: Background Reliability & OEM (p. 2) • Ch. 3: Safety Alarms & Rescue (p. 3)",
                44f, y1 + 22f, tocBodyPaint
            )
            c1.drawText(
                if (isRu) "Гл. 4: SOS SMS и Режим опекуна (стр. 4) • Гл. 5: Аналитика AGP и HbA1c (стр. 5) • Гл. 6: HUD, Виджеты и Архивы (стр. 6)"
                else "Ch. 4: Emergency SOS & Caregiver (p. 4) • Ch. 5: Clinical AGP & HbA1c (p. 5) • Ch. 6: HUD, Widgets & Maintenance (p. 6)",
                44f, y1 + 32f, tocBodyPaint
            )
            y1 += 46f

            // Quick Start Callout
            y1 = drawCallout(
                c1, y1, CalloutType.TIP,
                if (isRu) "🚀 БЫСТРЫЙ СТАРТ ЗА 3 ШАГА (ДЛЯ НОВЫХ ПОЛЬЗОВАТЕЛЕЙ):" else "🚀 3-STEP QUICK START GUIDE:",
                if (isRu) "1) В xDrip+ («Настройки ➔ Межпрограммная интеграция») включите «Широковещательные передачи». 2) В настройках Android отключите оптимизацию батареи для TIRUp («Без ограничений»). 3) Задайте ваш индивидуальный целевой диапазон сахара в Настройках TIRUp."
                else "1) In xDrip+ ('Settings ➔ Inter-app settings') enable 'Broadcast locally'. 2) In Android settings disable battery optimization for TIRUp ('Unrestricted'). 3) Set your personalized target range in TIRUp Settings."
            )

            y1 = drawSectionHeading(c1, y1, if (isRu) "ГЛАВА 1. АРХИТЕКТУРА СВЯЗИ, ИСТОЧНИКИ ДАННЫХ И BLE-МОСТ" else "CHAPTER 1. CONNECTIVITY, DATA SOURCES & FAMILY BLE BRIDGE")
            y1 = drawParagraph(
                c1, y1,
                if (isRu) "Приложение TIRUp создано по принципу максимальной автономности (Zero-Cloud). В отличие от стандартных облачных систем непрерывного мониторинга глюкозы (CGM), TIRUp никогда не требует подключения к интернету, авторизации на внешних веб-серверах или передачи персональных данных в сторонние дата-центры. Весь математический анализ, расчет коэффициентов компенсации и предиктивные тревоги выполняются на 100% локально на вашем смартфоне."
                else "TIRUp is built on the Zero-Cloud principle. Unlike traditional cloud-dependent CGM systems, TIRUp never requires an internet connection, remote web login, or transmission of personal medical data to third-party servers. All glycemic calculus, statistical compensation metrics, and predictive alarms execute 100% locally on your device."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "xDrip+, GDH и Juggluco" else "xDrip+, GDH & Juggluco",
                if (isRu) "В настройках источника перейдите в «Межпрограммная интеграция» и активируйте опции «Широковещательные передачи xDrip» и «Поддержка широковещательной службы» (для мгновенной передачи доз активного инсулина IoB и углеводов CoB). TIRUp принимает замеры через локальные системные Intents с задержкой менее 0.1 с."
                else "In xDrip+ enable 'Broadcast locally' and 'Broadcast service support' (for active insulin IoB and carbs CoB). TIRUp receives streaming glucose readings via low-latency Android Intents (<0.1s delay)."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Семейный BLE-мост (Вещатель — Приёмник)" else "Family BLE Bridge (Broadcaster — Observer)",
                if (isRu) "Передача сахара ребёнка родителю по Bluetooth Low Energy без Wi-Fi и SIM-карт. Смартфон подопечного настраивается как «Вещатель», а телефон родителя — как «Приёмник». При получении замера вещатель даёт 15-секундный импульс и уходит в глубокий сон (микропотребление <0.8% батареи в сутки). Пакет шифруется 4-значным Family PIN."
                else "Direct patient-to-parent glucose streaming over Bluetooth LE without Wi-Fi or SIM cards. Broadcaster pulses 15s bursts per reading then sleeps (<0.8% battery daily). Frame is encrypted via a 4-digit Family PIN."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Режим Long Range (LE Coded PHY)" else "Long Range Mode (LE Coded PHY)",
                if (isRu) "На чипсетах Bluetooth 5.0+ режим Coded PHY (S=8) повышает потенциал радиолинии на 8–10 dBm, расширяя дальность в 2–4 раза (до 30–50 метров сквозь стены). При отсутствии поддержки аппаратно откатывается на Legacy 1M. Сканер приёмника слушает оба диапазона (Dual) автоматически."
                else "On Bluetooth 5.0+ chipsets, Coded PHY (S=8) boosts link budget by 8-10 dBm, expanding through-wall range 2-4x (up to 30-50m). Safely falls back to Legacy 1M if unsupported. Observer scanner operates dual-mode automatically."
            )
            y1 += 3f

            drawCallout(
                c1, y1, CalloutType.INFO,
                if (isRu) "📡 ДИАГНОСТИКА СИГНАЛА И БАТАРЕИ ПЕРЕДАТЧИКА:" else "📡 TRANSMITTER SIGNAL & BATTERY DIAGNOSTICS:",
                if (isRu) "В шторке уведомлений и в HUD виджете приёмника в реальном времени отображается уровень радиосигнала (RSSI в dBm) и процент заряда батареи смартфона подопечного. Для быстрой проверки используйте кнопку «Тест дальности (5с)»."
                else "Ongoing notification and HUD widget display real-time transmitter RSSI (dBm) and battery percentage. Use 'Range Test (5s)' in Observer settings for instantaneous signal confirmation."
            )

            document.finishPage(page1)

            // =========================================================================
            // PAGE 2: Энергосбережение Android и OEM-руководство по стабильности
            // =========================================================================
            val page2 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
            val c2 = page2.canvas
            drawRunningHeaderAndFooter(c2, 2, if (isRu) "Глава 2. Стабильность на Android и OEM" else "Chapter 2. Android OEM Stability")

            var y2 = 48f
            c2.drawText(if (isRu) "ГЛАВА 2. НАДЕЖНОСТЬ В ФОНЕ И НАСТРОЙКА OEM-ПРОШИВОК" else "CHAPTER 2. BACKGROUND RELIABILITY & OEM-SPECIFIC GUIDES", 36f, y2, chapterTitlePaint)
            y2 += 12f
            c2.drawText(if (isRu) "Преодоление Doze Mode, App Standby и пошаговые чек-листы для Samsung, Xiaomi, BBK и Huawei" else "Overcoming Doze Mode, App Standby & step-by-step checklists for Samsung, Xiaomi, BBK and Huawei", 36f, y2, chapterSubtitlePaint)
            y2 += 14f

            y2 = drawSectionHeading(c2, y2, if (isRu) "2.1. Механика ограничений Android: Doze Mode и App Standby" else "2.1. Android Background Restrictions: Doze Mode & Standby")
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "Начиная с Android 10, система применяет агрессивные политики энергосбережения: отключает фоновые таймеры, блокирует сканирование Bluetooth и задерживает широковещательные сигналы. Для медицинского мониторинга диабета задержка тревоги на 5–10 минут недопустима. Чтобы гарантировать непрерывный приём замеров и громкие сигналы сирен ночью, необходимо предоставить приложению статус исключения из энергосбережения."
                else "Starting with Android 10, aggressive power-saving engines throttle background timers, restrict Bluetooth scans, and delay alarm broadcasts. For diabetes management, a 5-10 minute alert delay is unacceptable. Whitelisting TIRUp from power optimizations is mandatory for nocturnal safety."
            )
            y2 += 4f

            y2 = drawSectionHeading(c2, y2, if (isRu) "2.2. Пошаговые настройки для популярных производителей смартфонов" else "2.2. Step-by-Step Settings for Major OEM Smartphones")
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Samsung (OneUI 5 / 6)" else "Samsung (OneUI 5 / 6)",
                if (isRu) "1) Настройки ➔ Приложения ➔ TIRUp ➔ Батарея ➔ выберите «Не ограничено». 2) Настройки ➔ Батарея ➔ «Ограничения в фоновом режиме» ➔ «Никогда не спящие приложения» ➔ добавьте TIRUp и источник (xDrip+). 3) В меню недавних задач нажмите на иконку TIRUp и выберите «Закрепить приложение» (замочек)."
                else "1) Settings ➔ Apps ➔ TIRUp ➔ Battery ➔ Select 'Unrestricted'. 2) Settings ➔ Battery ➔ Background usage limits ➔ 'Never sleeping apps' ➔ Add TIRUp. 3) In Recent Apps switcher tap TIRUp icon and select 'Lock this app'."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Xiaomi, Redmi, POCO (MIUI / HyperOS)" else "Xiaomi, Redmi, POCO (MIUI / HyperOS)",
                if (isRu) "1) Настройки ➔ Приложения ➔ Все приложения ➔ TIRUp ➔ включите «Автозапуск» (и «Разрешить запуск другими приложениями»). 2) В пункте «Контроль активности» выберите «Нет ограничений». 3) В разделе «Другие разрешения» включите «Экран блокировки» и «Всплывающие окна». 4) Закрепите замочком в окне недавних."
                else "1) Settings ➔ Apps ➔ Manage Apps ➔ TIRUp ➔ Enable 'Autostart'. 2) Battery saver ➔ Select 'No restrictions'. 3) Other permissions ➔ Allow 'Show on Lock screen' & 'Pop-up windows'. 4) Lock app card in Recents."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "BBK: Realme, Oppo, OnePlus, Vivo (ColorOS / RealmeUI)" else "BBK: Realme, Oppo, OnePlus, Vivo (ColorOS / RealmeUI)",
                if (isRu) "1) Настройки ➔ Приложения ➔ Управление приложениями ➔ TIRUp ➔ «Расход батареи» ➔ разрешите фоновую активность и автозапуск. 2) Настройки ➔ Батарея ➔ «Оптимизация режима ожидания» ➔ отключите «Ультра-режим ожидания». 3) Заблокируйте приложение в недавних задачах."
                else "1) Settings ➔ Apps ➔ App management ➔ TIRUp ➔ Battery usage ➔ Allow background activity & auto-launch. 2) Settings ➔ Battery ➔ Advanced ➔ Sleep standby optimization ➔ Disable. 3) Lock app in Recents."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Huawei, Honor (EMUI / MagicOS)" else "Huawei, Honor (EMUI / MagicOS)",
                if (isRu) "1) Настройки ➔ Приложения ➔ Запуск приложений ➔ для TIRUp отключите «Автоматическое управление» и включите все три тумблера: «Автозапуск», «Косвенный запуск» и «Работа в фоновом режиме». 2) Заблокируйте приложение замочком в меню недавних задач."
                else "1) Settings ➔ Apps ➔ App launch ➔ For TIRUp toggle off 'Manage automatically' and enable 'Auto-launch', 'Secondary launch' and 'Run in background'. 2) Lock app card in Recent Apps."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Google Pixel (Чистый Android)" else "Google Pixel (Stock Android)",
                if (isRu) "Настройки ➔ Батарея ➔ Адаптивные настройки ➔ отключите «Адаптивный расход батареи». В свойствах приложения TIRUp выберите «Использование батареи ➔ Без ограничений»."
                else "Settings ➔ Battery ➔ Adaptive preferences ➔ Disable 'Adaptive Battery'. In TIRUp app info set 'Battery usage ➔ Unrestricted'."
            )
            y2 += 4f

            y2 = drawSectionHeading(c2, y2, if (isRu) "2.3. Полноэкранные интенты и пробивка экрана блокировки" else "2.3. Full-Screen Intents & Lockscreen Wakeup Permissions")
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "При критической ночной гипогликемии дисплей заблокирован. Экран спасения (Patient Rescue Screen) использует аппаратный WakeLock (ACQUIRE_CAUSES_WAKEUP) и флаги FLAG_SHOW_WHEN_LOCKED, гарантированно включая дисплей. Обязательно предоставьте приложению системное разрешение «Отображение поверх других приложений» и «Показ на экране блокировки»."
                else "During severe nocturnal hypoglycemia the screen is locked. The Rescue Screen combines hardware WakeLock (ACQUIRE_CAUSES_WAKEUP) with FLAG_SHOW_WHEN_LOCKED, waking the display with siren. Grant 'Display over other apps' permission in Android Settings."
            )
            y2 += 3f

            drawCallout(
                c2, y2, CalloutType.WARNING,
                if (isRu) "⚠️ КОНТРОЛЬНЫЙ ЧЕК-ЛИСТ ПЕРЕД ПЕРВОЙ НОЧЬЮ:" else "⚠️ PRE-FLIGHT CHECKLIST BEFORE NIGHT SLEEP:",
                if (isRu) "В самом низу экрана настроек выполните 5 быстрых тапов по строке «TIRUp • Версия ...» для открытия скрытого блока тестирования. Нажмите «Тест экрана спасения» и заблокируйте экран. Через 5 секунд дисплей должен сам включиться с полноэкранной сиреной."
                else "At the very bottom of Settings tap the 'TIRUp • Version ...' text 5 times rapidly to reveal the hidden test suite. Trigger 'Test Patient Rescue Screen' and lock your phone. Within 5 seconds display must automatically turn on with full-screen siren."
            )

            document.finishPage(page2)

            // =========================================================================
            // PAGE 3: Четырёхуровневая система тревог и Экран спасения
            // =========================================================================
            val page3 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 3).create())
            val c3 = page3.canvas
            drawRunningHeaderAndFooter(c3, 3, if (isRu) "Глава 3. Система безопасности и тревог" else "Chapter 3. Safety Alarms & Rescue")

            var y3 = 48f
            c3.drawText(if (isRu) "ГЛАВА 3. МНОГОУРОВНЕВАЯ СИСТЕМА ТРЕВОГ И ЭКРАН СПАСЕНИЯ" else "CHAPTER 3. 4-TIER ALARMS, COMA GUARD & RESCUE SCREEN", 36f, y3, chapterTitlePaint)
            y3 += 12f
            c3.drawText(if (isRu) "Клиническая градация Tier 1–4, предиктивная экстраполяция, обход DND и защита от комы" else "Tier 1-4 escalation, predictive trend extrapolation, DND bypass & Coma Guard protocol", 36f, y3, chapterSubtitlePaint)
            y3 += 14f

            y3 = drawSectionHeading(c3, y3, if (isRu) "3.1. Клиническая концепция четырёх уровней безопасности" else "3.1. Clinical Rationale of the 4-Tier Safety Model")
            y3 = drawParagraph(
                c3, y3,
                if (isRu) "Одной из главных проблем пользователей CGM является «усталость от тревог» (Alarm Fatigue), когда частые ложные сигналы приводят к отключению звука. В TIRUp реализована адаптивная 4-уровневая система тревог, которая отсекает шум и включает агрессивные сигналы только тогда, когда существует реальная угроза здоровью."
                else "Alarm fatigue causes patients to disable alerts, leading to severe unmonitored hypoglycemia. TIRUp solves this via an adaptive 4-tier model that suppresses unnecessary noise while ensuring escalating alerts for real clinical hazards."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Уровень 1: Предиктивный тренд на 25 минут" else "Tier 1: 25-Min Predictive Trend",
                if (isRu) "Регрессионный анализ скользящего окна точек с затуханием наклона проецирует траекторию на 25 минут вперёд (фиолетовые точки на суточном графике). При риске пересечения нижней границы раздаётся мягкий мелодичный тон за 15 минут до расчётной гипогликемии, позволяя принять углеводы заранее."
                else "Regression analysis with exponential damping extrapolates glucose 25 minutes ahead (purple dots on daily chart). A gentle melodic chime sounds 15 minutes before reaching low threshold, allowing early carbohydrate intake."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Уровень 2: Подтверждённое отклонение (адаптивно 3–5 точек)" else "Tier 2: Confirmed Departure (Adaptive 3-5 Points)",
                if (isRu) "Классический тройной сигнал при выходе за пределы нормы. Система адаптивно выбирает порог подтверждения: 3 точки подряд для 5-минутных датчиков или 5 точек для 1-минутных, надёжно отсекая шумы сенсора. При гипергликемии сигнал глушится, если сахар падает и есть активный инсулин (IoB)."
                else "Triple tone when confirmed outside target. The engine adaptively enforces 3 consecutive points (5-min CGM) or 5 points (1-min CGM) to eliminate sensor noise spikes. Hyperglycemia alarms auto-mute if glucose is falling with active IoB onboard."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Уровень 3: Критическая сирена и стробоскоп вспышки" else "Tier 3: Critical Siren & Strobe Light",
                if (isRu) "Срабатывает при опасных порогах (<3.0 или >13.9 ммоль/л), а также при затяжной гипо/гипергликемии. Обходит режим «Не беспокоить» (DND bypass), играет через канал USAGE_ALARM на громкости ≥80% и включает стробоскоп вспышки камеры."
                else "Fires upon critical values (<3.0 or >13.9 mmol/L) or prolonged breaches. Bypasses Android Do-Not-Disturb (DND), plays via ALARM audio stream at ≥80% volume, and strobes camera LED light."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Уровень 4: Потеря сигнала (20–25 мин) и ночной профиль" else "Tier 4: Sleep-Aware Signal Loss",
                if (isRu) "При отсутствии точек более 20 минут подаётся сигнал будильника. В Настройках задаются «Часы ночного сна» (по умолчанию 23:00–07:00), в этот период действуют отдельные ночные пороги тревог и активируется ночной контроль потери сигнала."
                else "Sounds alarm if no readings received for >20 mins. Configurable 'Night Sleep Window' (default 23:00-07:00) applies dedicated nocturnal target ranges and strict night-time signal-loss monitoring."
            )
            y3 += 4f

            y3 = drawSectionHeading(c3, y3, if (isRu) "3.2. Экран спасения и защита от комы (Coma Guard <2.8 ммоль/л)" else "3.2. Patient Rescue Screen & Coma Guard Protocol (<2.8 mmol/L)")
            y3 = drawParagraph(
                c3, y3,
                if (isRu) "При критической гипогликемии запускается полноэкранный интерфейс спасения (Patient Rescue Screen) с гигантским сахаром, стрелкой падения, таймером до SOS SMS и кнопкой купирования. При сахаре ниже 2.8 ммоль/л активируется Coma Guard: пациенту запрещается длинный снуз (более 5 минут), сирена повторяется каждые 5 минут, пока уровень глюкозы не поднимется выше порога безопасности."
                else "Critical low triggers the dedicated Patient Rescue Activity displaying giant glucose, drop arrow, emergency SMS countdown timer, and carb intake confirmation button. Below 2.8 mmol/L, Coma Guard locks snooze to maximum 5 minutes, repeating siren until safe recovery is confirmed."
            )
            y3 += 3f

            drawCallout(
                c3, y3, CalloutType.CRITICAL,
                if (isRu) "🚨 КЛИНИЧЕСКИЙ ПРОТОКОЛ КУПИРОВАНИЯ (ПРАВИЛО 15):" else "🚨 CLINICAL HYPO PROTOCOL (RULE OF 15):",
                if (isRu) "При гипогликемии примите 15 г быстрых углеводов (сок, декстроза, сахар в воде). Нажмите кнопку «Углеводы приняты» на экране спасения — это заглушит сирену и отменит отправку SOS SMS родственникам."
                else "Take 15g fast-acting carbohydrates (juice, dextrose, sugar). Press 'Carbs Taken' on Rescue Screen to silence alarm and abort emergency SOS SMS dispatch to caregivers."
            )

            document.finishPage(page3)

            // =========================================================================
            // PAGE 4: Экстренная безопасность близких и Режим опекуна
            // =========================================================================
            val page4 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 4).create())
            val c4 = page4.canvas
            drawRunningHeaderAndFooter(c4, 4, if (isRu) "Глава 4. Экстренные SMS и Опекун" else "Chapter 4. Emergency SMS & Caregiver")

            var y4 = 48f
            c4.drawText(if (isRu) "ГЛАВА 4. ЭКСТРЕННЫЕ SMS, РЕЖИМ ОПЕКУНА И ТЕЛЕМЕТРИЯ" else "CHAPTER 4. EMERGENCY SOS SMS, CAREGIVER MODE & TELEMETRY", 36f, y4, chapterTitlePaint)
            y4 += 12f
            c4.drawText(if (isRu) "Автономное оповещение родственников с GPS, двусторонний запрос сахара и режим сирены опекуна" else "Offline GPS distress SMS, two-way SMS status queries and loud Caregiver SOS alerts", 36f, y4, chapterSubtitlePaint)
            y4 += 14f

            y4 = drawSectionHeading(c4, y4, if (isRu) "4.1. Автоматическая отправка экстренных SMS с геолокацией" else "4.1. Automated Distress SOS SMS with GPS Location")
            y4 = drawParagraph(
                c4, y4,
                if (isRu) "Если пациент находится в состоянии тяжёлой гипогликемии и не отключает сирену в течение заданного времени (по умолчанию 3 минуты), TIRUp расценивает это как возможную потерю сознания. Приложение автоматически обращается к GPS-модулю смартфона и отправляет экстренное SMS-сообщение на доверенные номера родственников (основной и резервный)."
                else "If severe hypoglycemia siren is ignored for the delay period (default: 3 minutes), TIRUp presumes loss of consciousness. It queries device GPS and transmits an emergency SMS to trusted primary and secondary caregiver phones."
            )
            y4 = drawBulletPoint(
                c4, y4,
                if (isRu) "Содержание тревожного SMS" else "Distress SMS Format",
                if (isRu) "«SOS! У [Имя] критический сахар: 2.5 ммоль/л ⇊. Нет реакции на сирену 3 мин. Геолокация: maps.google.com/?q=55.75,37.61». Близкие получают координаты и могут вызвать скорую помощь."
                else "'SOS! [Name] critical glucose: 2.5 mmol/L ⇊. Unresponsive to siren for 3 min. Location: maps.google.com/?q=55.75,37.61'. Caregivers can instantly dispatch emergency medical services."
            )
            y4 += 4f

            y4 = drawSectionHeading(c4, y4, if (isRu) "4.2. Двусторонняя оффлайн-телеметрия по SMS" else "4.2. Two-Way Offline SMS Glucose Telemetry")
            y4 = drawParagraph(
                c4, y4,
                if (isRu) "Родственник или опекун может в любой момент узнать сахар подопечного, даже если у обоих отключён мобильный интернет. Достаточно отправить обычное SMS с текстом «сахар», «?», «tir» или «sugar» с доверенного номера телефона на смартфон подопечного. TIRUp распознаёт команду в фоне и мгновенно отправляет ответное SMS: «[Имя]: 6.4 ммоль/л → (TIR 89%, 15м назад, батарея 85%)»."
                else "Caregivers can check patient's glucose anytime without cellular internet. Sending an SMS containing 'sugar', '?', or 'tir' from a whitelisted phone prompts TIRUp to reply: '[Name]: 6.4 mmol/L → (TIR 89%, 15m ago, battery 85%)'."
            )
            y4 += 4f

            y4 = drawSectionHeading(c4, y4, if (isRu) "4.3. Режим опекуна (Caregiver SOS Alert): Громкая сирена на телефоне родителя" else "4.3. Caregiver SOS Mode: Loud Alarm on Parent's Phone")
            y4 = drawParagraph(
                c4, y4,
                if (isRu) "Если на телефоне опекуна также установлен TIRUp, входящее SMS от подопечного с префиксом «SOS! У...» активирует специальный экран спасения опекуна (CaregiverSosActivity). Телефон начинает громко проигрывать тревожную сирену будильника с непрерывной вибрацией, выводя имя подопечного, сахар, время замера и кнопку мгновенного перехода в навигатор по полученным GPS-координатам. Сирена пробивает беззвучный режим смартфона опекуна."
                else "If TIRUp is installed on caregiver's phone, an incoming 'SOS! ...' SMS triggers CaregiverSosActivity. The phone sounds a loud alarm siren bypassing silent mode, displaying patient name, glucose, time, and one-tap button to open Google Maps navigation."
            )
            y4 += 4f

            y4 = drawSectionHeading(c4, y4, if (isRu) "4.4. Настройка прав SMS и решение проблемы кэширования Android" else "4.4. SMS Permissions & Resolving Android Permission Caching")
            y4 = drawParagraph(
                c4, y4,
                if (isRu) "Для работы функции требуются разрешения SEND_SMS и RECEIVE_SMS. В некоторых версиях Android после ручного отзыва и повторной выдачи прав в системе возникает «кэширование отказа». В TIRUp встроен алгоритм автоматической повторной верификации: если отправка не удалась, приложение перепроверяет системные дескрипторы и выводит адресную подсказку."
                else "Features require SEND_SMS and RECEIVE_SMS permissions. On some Android versions, toggling permissions causes permission caching bugs. TIRUp incorporates automatic descriptor re-verification, providing inline recovery guidance."
            )
            y4 += 3f

            drawCallout(
                c4, y4, CalloutType.TIP,
                if (isRu) "📱 БЕЗОПАСНОСТЬ И ПРОВЕРКА ЭКСТРЕННОГО SMS:" else "📱 EMERGENCY SMS VERIFICATION:",
                if (isRu) "В настройках приложения в блоке «Экстренное SMS» нажмите «Отправить тестовое SMS». На указанный телефон придёт тестовое сообщение. Убедитесь, что номер указан в международном формате (+7...)."
                else "In Settings ➔ 'Emergency SMS' tap 'Send Test SMS'. Ensure phone numbers include country code (+1...). Verify receipt on caregiver's handset."
            )

            document.finishPage(page4)

            // =========================================================================
            // PAGE 5: Клиническая аналитика AGP, Паттерны, HbA1c и Компенсатор
            // =========================================================================
            val page5 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 5).create())
            val c5 = page5.canvas
            drawRunningHeaderAndFooter(c5, 5, if (isRu) "Глава 5. Клиническая аналитика и AGP" else "Chapter 5. Clinical Analytics & AGP")

            var y5 = 48f
            c5.drawText(if (isRu) "ГЛАВА 5. КЛИНИЧЕСКАЯ АНАЛИТИКА, AGP, ПАТТЕРНЫ И HbA1c" else "CHAPTER 5. CLINICAL AGP, PATTERNS, HbA1c & COMPENSATOR", 36f, y5, chapterTitlePaint)
            y5 += 12f
            c5.drawText(if (isRu) "Стандарты ATTD/ADA, 12 параметров AGP, лабораторный HbA1c, распознавание скрытых гипогликемий и суточный компенсатор" else "ATTD/ADA consensus standards, 12 AGP metrics, laboratory HbA1c, pattern detection & compensator", 36f, y5, chapterSubtitlePaint)
            y5 += 14f

            y5 = drawSectionHeading(c5, y5, if (isRu) "5.1. Амбулаторный гликемический профиль (AGP) и 12 клинических параметров" else "5.1. Ambulatory Glucose Profile (AGP) & 12 Clinical Metrics")
            y5 = drawParagraph(
                c5, y5,
                if (isRu) "В разделе «Отчёты» формируется стандартизированный медицинский отчёт AGP (Ambulatory Glucose Profile) в соответствии с международным консенсусом ATTD/ADA. Врач-эндокринолог получает полную клиническую картину за 7, 14, 30 или 90 дней с расчётом ключевых биомаркеров:"
                else "The Reports tab generates standardized AGP reports compliant with international ATTD/ADA consensus. It calculates 12 core clinical parameters across 7, 14, 30, or 90 days:"
            )
            y5 = drawBulletPoint(
                c5, y5,
                if (isRu) "TIR, TBR, TAR" else "TIR, TBR, TAR",
                if (isRu) "Время в целевом диапазоне (TIR 3.9–10.0 ммоль/л, норма ≥70%), время ниже диапазона (TBR <3.9, норма <4%, из них <3.0 <1%), время выше диапазона (TAR >10.0, норма <25%)."
                else "Time in Range (TIR 3.9-10.0 mmol/L, target ≥70%), Time Below Range (TBR <3.9, target <4%, severe <3.0 <1%), Time Above Range (TAR >10.0, target <25%)."
            )
            y5 = drawBulletPoint(
                c5, y5,
                if (isRu) "Вариабельность (CV, SD)" else "Variability (CV, SD)",
                if (isRu) "Коэффициент вариации CV (норма ≤36%) и стандартное отклонение SD отражают стабильность гликемии и защищённость от внезапных ночных гипогликемий."
                else "Coefficient of Variation CV (target ≤36%) and SD quantify glucose stability and nocturnal hypo resilience."
            )
            y5 = drawBulletPoint(
                c5, y5,
                if (isRu) "GRI, GVI, PGS, eA1c / GMI" else "GRI, GVI, PGS, eA1c / GMI",
                if (isRu) "Индекс гликемического риска GRI (0–100), индекс гликемической вариабельности GVI, показатель суточного профиля PGS и расчётный гликированный гемоглобин GMI."
                else "Glycemia Risk Index GRI (0-100), Glycemic Variability Index GVI, Personal Glycemic State PGS, and estimated GMI."
            )
            y5 += 4f

            y5 = drawSectionHeading(c5, y5, if (isRu) "5.2. Детектор скрытых клинических паттернов и правила автозакрытия" else "5.2. Pattern Recognition Engine & Auto-Dismissal")
            y5 = drawParagraph(
                c5, y5,
                if (isRu) "Интеллектуальный алгоритм анализирует скользящие окна данных и выявляет скрытые паттерны: ночные провалы в часы индивидуального сна, феномен утренней зари (рост перед пробуждением) и постпрандиальные всплески. Карточки паттернов имеют тайм-аут жизни: тревожные скрываются через 48 часов, информационные — через 24 часа. Доступен архив скрытых событий."
                else "The pattern engine identifies nocturnal dips during personal sleep hours, dawn phenomenon, and meal spikes. Cards auto-expire: high-priority after 48h, informational after 24h, with an expandable archive."
            )
            y5 += 4f

            y5 = drawSectionHeading(c5, y5, if (isRu) "5.3. Журнал лабораторного HbA1c и 4-уровневая шкала" else "5.3. Laboratory HbA1c Journal & 4-Tier Target Scale")
            y5 = drawParagraph(
                c5, y5,
                if (isRu) "Журнал позволяет вносить результаты анализов крови из лаборатории и сопоставлять их с данными датчика (GMI). Анализы классифицируются по 4 уровням: <6.1% (норма здорового), 6.1–7.0% (целевая компенсация), 7.0–8.0% (субкомпенсация), >8.0% (риск осложнений). Кнопка HbA1c меняет цвет по свежести анализа (<30д — светло-зелёный, 30–360д — зелёный, >360д — жёлтый)."
                else "Log laboratory venous HbA1c tests to track vs sensor GMI. Color-coded into 4 tiers: <6.1% (healthy norm), 6.1-7.0% (target control), 7.0-8.0% (subcompensation), >8.0% (complications risk). Button tracks freshness (<30d, 30-360d, >360d)."
            )
            y5 += 4f

            y5 = drawSectionHeading(c5, y5, if (isRu) "5.4. Суточный компенсатор цели и точка невозврата" else "5.4. Daily Target Compensator & Point of No Return")
            y5 = drawParagraph(
                c5, y5,
                if (isRu) "Компенсатор рассчитывает строгое время удержания диапазона от 00:00 до 23:59 для достижения суточной цели (TIR ≥70% / TING ≥50%). За 1–2 часа до математической точки невозврата выдаётся предупреждение «Последний шанс для TIR»."
                else "The compensator strictly tracks target time from 00:00 to 23:59 to reach daily goals (TIR ≥70% / TING ≥50%). Emits a 'Last Chance for TIR' warning 1-2 hours before reaching the mathematical point of no return."
            )
            y5 += 3f

            drawCallout(
                c5, y5, CalloutType.INFO,
                if (isRu) "📊 ЭКСПОРТ AGP ОТЧЁТА ДЛЯ ВРАЧА:" else "📊 EXPORTING AGP REPORTS FOR PHYSICIANS:",
                if (isRu) "На вкладке «Отчёты» выберите период (например, 14 дней) и нажмите «Создать AGP отчёт (PDF)». Файл можно сохранить в память или мгновенно отправить лечащему врачу в Telegram, WhatsApp или по почте."
                else "On the Reports tab select period (e.g. 14 days) and tap 'Create AGP Report (PDF)'. Share directly with your endocrinologist via Telegram, email or WhatsApp."
            )

            document.finishPage(page5)

            // =========================================================================
            // PAGE 6: Быстрый обзор (HUD), Виджеты, Расходники и Zero-Lag архивы
            // =========================================================================
            val page6 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 6).create())
            val c6 = page6.canvas
            drawRunningHeaderAndFooter(c6, 6, if (isRu) "Глава 6. Виджеты, HUD и Архивация" else "Chapter 6. Widgets, HUD & Maintenance")

            var y6 = 48f
            c6.drawText(if (isRu) "ГЛАВА 6. ИНТЕРФЕЙС HUD, ВИДЖЕТЫ, УЧЁТ РАСХОДНИКОВ И АРХИВЫ" else "CHAPTER 6. QUICK GLANCE HUD, WIDGETS, SUPPLIES & ARCHIVES", 36f, y6, chapterTitlePaint)
            y6 += 12f
            c6.drawText(if (isRu) "Плавающий HUD 108sp, виджеты рабочего стола, учёт срока службы сенсора и Zero-Lag база данных" else "Quick Glance HUD 108sp, desktop widgets, supplies lifespan tracking & Zero-Lag annual database", 36f, y6, chapterSubtitlePaint)
            y6 += 14f

            y6 = drawSectionHeading(c6, y6, if (isRu) "6.1. Всплывающий Quick Glance HUD и автоскрытие навигации" else "6.1. Quick Glance HUD & Auto-Hiding Navigation")
            y6 = drawParagraph(
                c6, y6,
                if (isRu) "При удержании центральной кнопки навигации в течение 0.33 секунды по центру экрана открывается монументальный виджет быстрого обзора (Quick Glance HUD). Сахар увеличен до 108sp со стрелкой 78sp, единицы отцентрованы снизу, крупные карточки инсулина и углеводов (34sp) и телеметрия в одну строку (19sp). Нижняя панель навигации автоматически скрывается через 2.2 секунды и мгновенно возвращается при первом касании экрана в любом месте."
                else "Holding the center bottom navigation button for 0.33s summons the centered Quick Glance HUD. Features monumental 108sp glucose, 78sp trend arrow, centered units, vertical 34sp insulin/carb stat tiles, and 19sp single-line telemetry. Bottom navigation bar auto-hides after 2.2s and wakes on first touch."
            )
            y6 += 4f

            y6 = drawSectionHeading(c6, y6, if (isRu) "6.2. Виджеты рабочего стола, экран блокировки/AOD и Пузырёк" else "6.2. Desktop Widgets, Lockscreen/AOD & Floating Bubble")
            y6 = drawParagraph(
                c6, y6,
                if (isRu) "TIRUp предлагает 5 форматов виджетов рабочего стола: широкая полоса (5х1), дашборд с 4-часовым графиком Canvas (4х2/3х2), компактный квадрат (2х2) и вертикальный стек (1х2). Статус сахара на экране блокировки поддерживает плавную регулировку прозрачности подложки от 0% до 100%. Плавающий оверлей «Пузырёк» виден поверх всех приложений, при гипогликемии увеличивается и излучает пульсирующие круги на воде."
                else "TIRUp provides 5 homescreen widget sizes: horizontal strip (5x1), 4-hour Canvas graph dashboard (4x2/3x2), compact square (2x2), and vertical stack (1x2). Lockscreen ongoing notification features smooth 0%-100% opacity slider. The floating bubble overlay stays above all apps, expanding with ripple waves during hypo."
            )
            y6 += 4f

            y6 = drawSectionHeading(c6, y6, if (isRu) "6.3. Контроль сроков службы устройств и расходников" else "6.3. Supplies Lifespan Tracking & Negative Counters")
            y6 = drawParagraph(
                c6, y6,
                if (isRu) "В приложении встроен раздельный учёт сроков службы сенсора CGM (10–14 дней), канюли инфузионного набора помпы (3 дня) и ланцета прокалывателя. При просрочке счётчик переходит в наглядные отрицательные градации: до 24 часов (-Xч), от 1 до 30 дней (-Xд), более 30 дней (-Xм)."
                else "Dedicated tracking for CGM sensor (10-14 days), pump cannula (3 days), and lancet. Expired devices transition into negative counters: up to 24h (-Xh), 1 to 30 days (-Xd), >30 days (-Xm)."
            )
            y6 += 4f

            y6 = drawSectionHeading(c6, y6, if (isRu) "6.4. Архитектура Zero-Lag и ежедневные резервные копии" else "6.4. Zero-Lag Database Engine & Automated Backups")
            y6 = drawParagraph(
                c6, y6,
                if (isRu) "В конце каждого календарного года завершённые замеры архивируются в отдельные компактные файлы tirup_readings_YYYY.csv. Основная база данных всегда остаётся легковесной, обеспечивая мгновенный запуск и прокрутку без зависаний даже при ведении диабета на протяжении многих лет. Каждую полночь в 23:59:59 создаётся локальный автобэкап настроек и базы данных, также доступен ручной экспорт полного архива в ZIP."
                else "Past calendar years are automatically sealed into separate tirup_readings_YYYY.csv archives. The active database remains ultra-lightweight, ensuring zero-lag responsiveness across years of records. Midnight auto-backups run at 23:59:59 into private storage, alongside manual full ZIP exports."
            )
            y6 += 3f

            drawCallout(
                c6, y6, CalloutType.INFO,
                if (isRu) "⚖️ ЮРИДИЧЕСКИЙ МЕДИЦИНСКИЙ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ:" else "⚖️ LEGAL MEDICAL DISCLAIMER NOTICE:",
                if (isRu) "TIRUp является программным средством для информационного самоконтроля образа жизни при диабете. Приложение не является сертифицированным медицинским прибором. Всегда проверяйте показания глюкометром по капле крови перед принятием решений о дозах инсулина."
                else "TIRUp is an auxiliary lifestyle self-monitoring tool. It is not an officially certified medical device. Always verify CGM readings with a capillary blood meter prior to therapeutic insulin adjustments."
            )

            document.finishPage(page6)

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
