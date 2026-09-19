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
 * Generates an exhaustive 3-page A4 manual with classic book typography (2 chapters per page,
 * in-depth explanatory paragraphs, non-overlapping bullet lists with bold titles, and dynamic clinical callout boxes).
 */
class UserManualPdfGenerator(private val context: Context) {

    enum class CalloutType {
        INFO, TIP, WARNING, CRITICAL
    }

    suspend fun generateUserManualPdf(isRu: Boolean): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val totalPages = 3
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.1"
            } catch (_: Exception) {
                "2.2.1"
            }

            // Typography Paints
            val docHeaderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val docHeaderSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.0f
            }
            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.8f
            }
            val docTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 12.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val chapterTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val chapterSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 7.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val sectionHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85) // Slate 700
                textSize = 8.0f
            }
            val bulletSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235) // ActionBlue
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bulletTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tocTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 64, 175)
                textSize = 7.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tocBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 6.2f
            }
            val calloutTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 7.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val calloutBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 7.0f
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

            // Running header & footer: strictly non-overlapping
            fun drawRunningHeaderAndFooter(canvas: Canvas, pageNum: Int) {
                val headerDocTitle = if (isRu) "TIRUp • Руководство пользователя и клинический справочник"
                else "TIRUp • User Manual & Clinical Reference"
                canvas.drawText(headerDocTitle, 36f, 24f, docHeaderTitlePaint)

                val pageStr = if (isRu) "Стр. $pageNum из $totalPages" else "Page $pageNum of $totalPages"
                val pageStrWidth = docHeaderSubPaint.measureText(pageStr)
                canvas.drawText(pageStr, 559f - pageStrWidth, 24f, docHeaderSubPaint)
                canvas.drawLine(36f, 30f, 559f, 30f, rulePaint)

                // Footer
                canvas.drawLine(36f, 814f, 559f, 814f, rulePaint)
                val footerDisclaimer = if (isRu) "TIRUp v$appVersion • 100% Автономный медицинский спутник"
                else "TIRUp v$appVersion • 100% Offline Medical Companion"
                canvas.drawText(footerDisclaimer, 36f, 826f, docHeaderSubPaint)
                val appStamp = "github.com/EvgeniyKrasnyanskiy/TIRUp"
                val stampWidth = docHeaderSubPaint.measureText(appStamp)
                canvas.drawText(appStamp, 559f - stampWidth, 826f, docHeaderSubPaint)
            }

            fun drawChapterHeading(canvas: Canvas, startY: Float, title: String, subtitle: String): Float {
                var y = startY + 3f
                canvas.drawText(title, 36f, y + 9f, chapterTitlePaint)
                y += 12.0f
                canvas.drawText(subtitle, 36f, y + 7f, chapterSubtitlePaint)
                return y + 11.5f
            }

            fun drawSectionHeading(canvas: Canvas, startY: Float, heading: String): Float {
                val y = startY + 2f
                canvas.drawText(heading, 36f, y + 8f, sectionHeadingPaint)
                return y + 11.0f
            }

            fun drawParagraph(canvas: Canvas, startY: Float, text: String, maxWidth: Float = 523f, lineHeight: Float = 10.4f): Float {
                val words = text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = ""
                for (w in words) {
                    val test = if (currentLine.isEmpty()) w else "$currentLine $w"
                    if (bodyPaint.measureText(test) <= maxWidth) {
                        currentLine = test
                    } else {
                        lines.add(currentLine)
                        currentLine = w
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }

                var y = startY + 8.0f
                for (l in lines) {
                    canvas.drawText(l, 36f, y, bodyPaint)
                    y += lineHeight
                }
                return (y - lineHeight) + 4.0f
            }

            fun drawBulletPoint(
                canvas: Canvas,
                startY: Float,
                title: String,
                desc: String,
                maxWidth: Float = 523f,
                lineHeight: Float = 10.4f
            ): Float {
                var y = startY + 8.0f

                // Bullet dot
                canvas.drawText("•", 38f, y, bulletSymbolPaint)

                // Title
                val titleColon = "$title: "
                canvas.drawText(titleColon, 47f, y, bulletTitlePaint)
                val titleWidth = bulletTitlePaint.measureText(titleColon)

                val words = desc.split(" ")
                val firstLineAvail = maxWidth - 11f - titleWidth
                var wordIdx = 0
                var firstLine = ""

                if (firstLineAvail > 45f) {
                    while (wordIdx < words.size) {
                        val w = words[wordIdx]
                        val test = if (firstLine.isEmpty()) w else "$firstLine $w"
                        if (bodyPaint.measureText(test) <= firstLineAvail) {
                            firstLine = test
                            wordIdx++
                        } else {
                            break
                        }
                    }
                }

                if (firstLine.isNotEmpty()) {
                    canvas.drawText(firstLine, 47f + titleWidth, y, bodyPaint)
                }

                // Subsequent wrapped lines indented to 47f
                val subAvail = maxWidth - 11f
                var subLine = ""
                while (wordIdx < words.size) {
                    val w = words[wordIdx]
                    val test = if (subLine.isEmpty()) w else "$subLine $w"
                    if (bodyPaint.measureText(test) <= subAvail) {
                        subLine = test
                        wordIdx++
                    } else {
                        y += lineHeight
                        canvas.drawText(subLine, 47f, y, bodyPaint)
                        subLine = w
                        wordIdx++
                    }
                }

                if (subLine.isNotEmpty()) {
                    y += lineHeight
                    canvas.drawText(subLine, 47f, y, bodyPaint)
                }

                return y + 3.0f
            }

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

                val textMaxWidth = boxWidth - 20f
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

                val lineHeight = 9.0f
                val totalHeight = 13f + (lines.size * lineHeight) + 3f
                val boxRect = RectF(36f, y, 36f + boxWidth, y + totalHeight)
                canvas.drawRoundRect(boxRect, 4f, 4f, calloutBgPaint)
                canvas.drawRoundRect(boxRect, 4f, 4f, calloutBorderPaint)

                // Left accent bar
                val barRect = RectF(36f, y, 40f, y + totalHeight)
                canvas.drawRoundRect(barRect, 2f, 2f, calloutAccentBarPaint)

                canvas.drawText(title, 46f, y + 9.5f, calloutTitlePaint)

                var lineY = y + 19.0f
                for (l in lines) {
                    canvas.drawText(l, 46f, lineY, calloutBodyPaint)
                    lineY += lineHeight
                }

                return y + totalHeight + 4.2f
            }

            // =========================================================================
            // PAGE 1: Введение, Содержание, Быстрый старт + ГЛАВА 1 + ГЛАВА 2
            // =========================================================================
            val page1 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c1 = page1.canvas
            drawRunningHeaderAndFooter(c1, 1)

            var y1 = 44f
            c1.drawText(if (isRu) "TIRUp • РУКОВОДСТВО ПОЛЬЗОВАТЕЛЯ" else "TIRUp • USER MANUAL", 36f, y1 + 10f, docTitlePaint)
            y1 += 13.5f

            c1.drawText(
                if (isRu) "Клинический справочник, автономная архитектура, тревоги и защита безопасности"
                else "Clinical reference, zero-cloud architecture, safety alarms and device integration",
                36f, y1 + 7f, chapterSubtitlePaint
            )
            y1 += 11.5f

            // TOC Box
            val tocRect = RectF(36f, y1, 559f, y1 + 29f)
            calloutBgPaint.color = Color.rgb(248, 250, 252)
            calloutBorderPaint.color = Color.rgb(203, 213, 225)
            c1.drawRoundRect(tocRect, 4f, 4f, calloutBgPaint)
            c1.drawRoundRect(tocRect, 4f, 4f, calloutBorderPaint)
            c1.drawText(if (isRu) "СОДЕРЖАНИЕ РУКОВОДСТВА:" else "TABLE OF CONTENTS:", 44f, y1 + 9.5f, tocTitlePaint)
            c1.drawText(
                if (isRu) "Гл. 1: Связь и BLE-мост • Гл. 2: Надежность в фоне и OEM (Стр. 1) | Гл. 3: Тревоги и Спасение • Гл. 4: SOS SMS и Опекун (Стр. 2)"
                else "Ch. 1: Connectivity & BLE • Ch. 2: Background Reliability & OEM (p. 1) | Ch. 3: Safety Alarms & Rescue • Ch. 4: Emergency SOS & Caregiver (p. 2)",
                44f, y1 + 17.5f, tocBodyPaint
            )
            c1.drawText(
                if (isRu) "Гл. 5: Клиническая аналитика AGP и HbA1c • Гл. 6: HUD 108sp, Виджеты, Расходники и Архивация (Стр. 3)"
                else "Ch. 5: Clinical AGP & HbA1c • Ch. 6: Quick Glance HUD, Widgets, Supplies & Maintenance (p. 3)",
                44f, y1 + 25.5f, tocBodyPaint
            )
            y1 += 33.5f

            // Quick Start Callout
            y1 = drawCallout(
                c1, y1, CalloutType.TIP,
                if (isRu) "🚀 БЫСТРЫЙ СТАРТ ЗА 3 ШАГА (ДЛЯ НОВЫХ ПОЛЬЗОВАТЕЛЕЙ):" else "🚀 3-STEP QUICK START GUIDE:",
                if (isRu) "1) В xDrip+ («Настройки ➔ Межпрограммная интеграция») включите «Широковещательные передачи». 2) В настройках Android отключите оптимизацию батареи («Без ограничений»). 3) Задайте целевой диапазон сахара в Настройках TIRUp."
                else "1) In xDrip+ enable 'Broadcast locally'. 2) Disable Android battery optimization for TIRUp ('Unrestricted'). 3) Set your target glucose range in TIRUp Settings."
            )

            // CHAPTER 1
            y1 = drawChapterHeading(
                c1, y1,
                if (isRu) "ГЛАВА 1. АРХИТЕКТУРА СВЯЗИ, ИСТОЧНИКИ ДАННЫХ И BLE-МОСТ" else "CHAPTER 1. CONNECTIVITY, DATA SOURCES & FAMILY BLE BRIDGE",
                if (isRu) "Zero-Cloud принцип, локальные интенты и автономный семейный Bluetooth-мост" else "Zero-Cloud principle, local Android intents and offline Family BLE Bridge"
            )
            y1 = drawParagraph(
                c1, y1,
                if (isRu) "Приложение TIRUp создано по принципу максимальной автономности (Zero-Cloud). В отличие от облачных CGM-систем, TIRUp никогда не требует подключения к интернету, внешних серверов или передачи личных данных. Все вычисления компенсации и тревоги выполняются на 100% локально на смартфоне."
                else "TIRUp operates on the Zero-Cloud principle without internet dependencies, external servers, or data uploads. All calculations and alarms execute 100% locally on your smartphone."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "xDrip+, GDH и Juggluco" else "xDrip+, GDH & Juggluco",
                if (isRu) "В настройках источника перейдите в «Межпрограммная интеграция» и включите «Широковещательные передачи» и «Поддержку широковещательной службы» (для мгновенной передачи доз IoB/CoB). TIRUp принимает замеры через локальные Intents с задержкой <0.1 с."
                else "Enable 'Broadcast locally' and 'Broadcast service support' for IoB/CoB in your source. TIRUp captures streaming readings via low-latency Android Intents (<0.1s)."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Семейный BLE-мост (Вещатель — Приёмник)" else "Family BLE Bridge",
                if (isRu) "Передача сахара ребёнка родителю по Bluetooth LE без Wi-Fi и SIM. Телефон ребёнка настраивается как «Вещатель» (импульс 15 с на замер, микропотребление <0.8% батареи/сутки), телефон родителя — как «Приёмник». Пакет шифруется 4-значным Family PIN."
                else "Stream patient glucose to parent over BLE without Wi-Fi or SIM. Broadcaster pulses 15s bursts (<0.8% battery/day), Observer receives. Secured with 4-digit PIN."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Режим Long Range (LE Coded PHY)" else "Long Range Mode (LE Coded PHY)",
                if (isRu) "На чипсетах Bluetooth 5.0+ режим Coded PHY (S=8) повышает потенциал радиолинии на 8–10 dBm, расширяя дальность в 2–4 раза (до 30–50 м сквозь стены). При отсутствии поддержки аппаратно откатывается на Legacy 1M. Сканер приёмника слушает оба диапазона автоматически."
                else "On Bluetooth 5.0+, Coded PHY (S=8) boosts link budget by 8-10 dBm, extending range 2-4x (up to 30-50m through walls). Observer operates in dual-mode automatically."
            )
            y1 = drawCallout(
                c1, y1, CalloutType.INFO,
                if (isRu) "📡 ДИАГНОСТИКА СИГНАЛА И БАТАРЕИ ПЕРЕДАТЧИКА:" else "📡 TRANSMITTER SIGNAL & BATTERY:",
                if (isRu) "В шторке уведомлений и в HUD приёмника отображаются уровень радиосигнала (RSSI dBm) и процент заряда батареи подопечного. Для быстрой проверки используйте «Тест дальности (5с)»."
                else "Notification and HUD widget display real-time transmitter RSSI (dBm) and battery %. Use 'Range Test (5s)' in Observer settings for immediate verification."
            )

            // 3x CHAPTER SPACING
            y1 += 22f

            // CHAPTER 2
            y1 = drawChapterHeading(
                c1, y1,
                if (isRu) "ГЛАВА 2. НАДЕЖНОСТЬ В ФОНЕ И НАСТРОЙКА OEM-ПРОШИВОК" else "CHAPTER 2. BACKGROUND RELIABILITY & OEM-SPECIFIC GUIDES",
                if (isRu) "Преодоление Doze Mode, App Standby и пошаговые чек-листы для вендоров" else "Overcoming Doze Mode, App Standby and step-by-step checklists for major OEMs"
            )
            y1 = drawSectionHeading(c1, y1, if (isRu) "2.1. Чек-листы фоновой работы для популярных смартфонов" else "2.1. Background Reliability Checklists for Major OEMs")
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Samsung (OneUI)" else "Samsung (OneUI)",
                if (isRu) "1) Настройки ➔ Приложения ➔ TIRUp ➔ Батарея ➔ «Не ограничено». 2) Настройки ➔ Батарея ➔ «Ограничения в фоновом режиме» ➔ «Никогда не спящие приложения» ➔ добавьте TIRUp и источник. 3) Закрепите замочком в меню недавних."
                else "1) Settings ➔ Apps ➔ TIRUp ➔ Battery ➔ 'Unrestricted'. 2) Battery ➔ Background limits ➔ 'Never sleeping apps' ➔ Add TIRUp. 3) Lock app card in Recents."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Xiaomi, Redmi, POCO (MIUI / HyperOS)" else "Xiaomi (MIUI / HyperOS)",
                if (isRu) "1) Включите «Автозапуск» и «Разрешить запуск другими приложениями». 2) «Контроль активности» ➔ «Нет ограничений». 3) «Другие разрешения» ➔ «Экран блокировки» и «Всплывающие окна». 4) Закрепите замочком в Недавних."
                else "1) Enable 'Autostart'. 2) Battery saver ➔ 'No restrictions'. 3) Other permissions ➔ Allow 'Lock screen' & 'Pop-up windows'. 4) Lock app in Recents."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "BBK: Realme, Oppo, OnePlus (ColorOS)" else "BBK: Realme, Oppo, OnePlus",
                if (isRu) "1) Управление приложениями ➔ TIRUp ➔ «Расход батареи» ➔ разрешите фоновую активность и автозапуск. 2) «Оптимизация режима ожидания» ➔ отключите «Ультра-режим ожидания». 3) Заблокируйте в Недавних."
                else "1) App management ➔ TIRUp ➔ Battery ➔ Allow background & auto-launch. 2) Disable sleep standby optimization. 3) Lock app in Recents."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Huawei, Honor (EMUI / MagicOS)" else "Huawei, Honor (EMUI / MagicOS)",
                if (isRu) "Настройки ➔ Приложения ➔ Запуск приложений ➔ для TIRUp отключите автоуправление и включите: «Автозапуск», «Косвенный запуск», «Работа в фоне». Закрепите замочком в Недавних."
                else "Apps ➔ App launch ➔ Disable automatic for TIRUp, enable 'Auto-launch', 'Secondary launch', 'Run in background'. Lock in Recents."
            )
            y1 = drawBulletPoint(
                c1, y1,
                if (isRu) "Google Pixel (Stock Android)" else "Google Pixel (Stock Android)",
                if (isRu) "Настройки ➔ Батарея ➔ Адаптивные настройки ➔ отключите «Адаптивный расход». В свойствах TIRUp выберите «Использование батареи ➔ Без ограничений»."
                else "Battery ➔ Adaptive preferences ➔ Disable 'Adaptive Battery'. In TIRUp app info set 'Battery ➔ Unrestricted'."
            )
            y1 = drawSectionHeading(c1, y1, if (isRu) "2.2. Полноэкранные интенты и пробуждение дисплея" else "2.2. Full-Screen Intents & Display Wakeup")
            y1 = drawParagraph(
                c1, y1,
                if (isRu) "При критической ночной гипогликемии дисплей заблокирован. Экран спасения использует аппаратный WakeLock (ACQUIRE_CAUSES_WAKEUP) и флаги FLAG_SHOW_WHEN_LOCKED, гарантированно включая дисплей с сиреной. Предоставьте права «Поверх других приложений»."
                else "During severe low, Rescue Screen utilizes hardware WakeLock and FLAG_SHOW_WHEN_LOCKED to reliably awaken the screen. Grant 'Display over other apps' permission."
            )
            drawCallout(
                c1, y1, CalloutType.WARNING,
                if (isRu) "⚠️ КОНТРОЛЬНЫЙ ЧЕК-ЛИСТ ПЕРЕД ПЕРВОЙ НОЧЬЮ:" else "⚠️ PRE-FLIGHT CHECKLIST BEFORE NIGHT:",
                if (isRu) "Внизу экрана настроек выполните 5 быстрых тапов по строке «TIRUp • Версия ...» для открытия скрытого блока тестирования. Нажмите «Тест экрана спасения» и заблокируйте телефон. Через 5 с дисплей должен сам проснуться с сиреной."
                else "Tap 'TIRUp • Version ...' at the bottom of Settings 5 times to reveal tests. Run 'Test Patient Rescue Screen' and lock phone. Within 5s display must wake up with siren."
            )
            document.finishPage(page1)

            // =========================================================================
            // PAGE 2: ГЛАВА 3 + ГЛАВА 4
            // =========================================================================
            val page2 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
            val c2 = page2.canvas
            drawRunningHeaderAndFooter(c2, 2)

            var y2 = 44f
            y2 = drawChapterHeading(
                c2, y2,
                if (isRu) "ГЛАВА 3. МНОГОУРОВНЕВАЯ СИСТЕМА ТРЕВОГ И ЭКРАН СПАСЕНИЯ" else "CHAPTER 3. 4-TIER ALARMS, COMA GUARD & RESCUE SCREEN",
                if (isRu) "Клиническая градация Tier 1–4, предиктивный тренд на 25 минут, обход DND и Coma Guard" else "Tier 1-4 escalation, 25-min predictive trend, DND bypass and Coma Guard protocol"
            )
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "Одной из главных проблем пользователей CGM является «усталость от тревог» (Alarm Fatigue), когда частые ложные сигналы приводят к отключению звука. В TIRUp реализована адаптивная 4-уровневая система безопасности, которая отсекает шум и включает сирены только тогда, когда существует реальная клиническая угроза здоровью:"
                else "To resolve CGM alarm fatigue, TIRUp incorporates an adaptive 4-tier safety model that filters transient sensor artifacts and escalates alerts only for verified clinical hazards:"
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Уровень 1: Предиктивный тренд на 25 минут" else "Tier 1: 25-Min Predictive Trend",
                if (isRu) "Регрессионный анализ скользящего окна точек с экспоненциальным затуханием проецирует траекторию на 25 мин вперёд (фиолетовые точки на суточном графике). Мягкий сигнал звучит за 15 мин до расчётной гипогликемии, позволяя принять углеводы заранее."
                else "Linear regression with exponential damping projects glucose 25 mins ahead (purple dots on daily chart). Gentle chime warns 15 mins before calculated low, allowing timely carb intake."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Уровень 2: Подтверждённое отклонение (3–5 точек)" else "Tier 2: Confirmed Departure (3-5 Points)",
                if (isRu) "Классический тройной сигнал при выходе за пределы нормы. Адаптивный порог: 3 точки подряд для 5-минутных датчиков или 5 точек для 1-минутных датчиков. При гипергликемии сигнал глушится, если сахар падает и есть активный инсулин (IoB)."
                else "Triple tone when outside target. Adaptively requires 3 points (5-min CGM) or 5 points (1-min CGM). Auto-mutes on high if glucose is dropping with active IoB onboard."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Уровень 3: Критическая сирена и стробоскоп вспышки" else "Tier 3: Critical Siren & LED Strobe",
                if (isRu) "Срабатывает при опасных порогах (<3.0 или >13.9 ммоль/л) и затяжной гипо/гипергликемии. Обходит режим «Не беспокоить» (DND bypass), играет через USAGE_ALARM на громкости ≥80% и включает стробоскоп вспышки камеры."
                else "Fires upon critical values (<3.0 or >13.9 mmol/L) or prolonged breaches. Bypasses DND, sounds via ALARM stream at ≥80% volume, and pulses camera LED flash."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Уровень 4: Потеря сигнала (20–25 мин) и ночной профиль" else "Tier 4: Signal Loss & Night Profile",
                if (isRu) "При отсутствии точек >20 мин подаётся сигнал будильника. В Настройках задаются «Часы ночного сна» (по умолчанию 23:00–07:00), в этот период действуют отдельные ночные пороги тревог и строгий контроль связи."
                else "Alarm sounds if readings stop for >20 mins. Configurable Night Sleep Window (default 23:00-07:00) applies dedicated nocturnal thresholds and strict signal checks."
            )
            y2 = drawSectionHeading(c2, y2, if (isRu) "3.2. Экран спасения и защита от комы (Coma Guard <2.8 ммоль/л)" else "3.2. Patient Rescue Screen & Coma Guard (<2.8 mmol/L)")
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "При глубокой гипогликемии запускается полноэкранный интерфейс спасения с гигантским сахаром, стрелкой падения, таймером до отправки SOS SMS и кнопкой купирования. При сахаре ниже 2.8 ммоль/л включается протокол Coma Guard: пациенту запрещается снуз более 5 минут, сирена повторяется каждые 5 мин до подтверждения купирования."
                else "Severe low launches Patient Rescue Screen with giant sugar, drop arrow, and SOS timer. Below 2.8 mmol/L Coma Guard caps snooze to 5 mins, repeating siren until safe recovery is confirmed."
            )
            y2 = drawCallout(
                c2, y2, CalloutType.CRITICAL,
                if (isRu) "🚨 КЛИНИЧЕСКИЙ ПРОТОКОЛ КУПИРОВАНИЯ (ПРАВИЛО 15):" else "🚨 CLINICAL HYPO PROTOCOL (RULE OF 15):",
                if (isRu) "При гипогликемии примите 15 г быстрых углеводов (сок, декстроза). Нажмите кнопку «Углеводы приняты» на экране спасения — это заглушит сирену и отменит отправку экстренного SOS SMS родственникам."
                else "Take 15g fast-acting carbs (juice, dextrose). Press 'Carbs Taken' on Rescue Screen to silence siren and cancel emergency SOS SMS dispatch."
            )

            // 3x CHAPTER SPACING
            y2 += 24f

            // CHAPTER 4
            y2 = drawChapterHeading(
                c2, y2,
                if (isRu) "ГЛАВА 4. ЭКСТРЕННЫЕ SMS, РЕЖИМ ОПЕКУНА И ТЕЛЕМЕТРИЯ" else "CHAPTER 4. EMERGENCY SOS SMS, CAREGIVER MODE & TELEMETRY",
                if (isRu) "Автономные оповещения с GPS, двусторонний запрос сахара и сирена на телефоне родителя" else "Offline GPS distress SMS, two-way glucose queries and caregiver alarm sirens"
            )
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "Если пациент находится в состоянии тяжёлой гипогликемии и не отключает сирену в течение заданного времени (по умолчанию 3 мин), TIRUp расценивает это как возможную потерю сознания. Приложение запрашивает координаты GPS и автоматически отправляет экстренное SMS доверенным лицам:"
                else "If hypo alarm is unacknowledged for the delay (default 3 mins), TIRUp retrieves device GPS and transmits emergency distress SMS to caregiver phones:"
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Содержание тревожного SOS SMS" else "Distress SOS SMS Format",
                if (isRu) "«SOS! У [Имя] критический сахар: 2.5 ммоль/л ⇊. Нет реакции на сирену 3 мин. Геолокация: maps.google.com/?q=55.75,37.61». Близкие получают координаты и могут оперативно вызвать скорую помощь."
                else "'SOS! [Name] critical glucose: 2.5 mmol/L ⇊. Unresponsive 3 min. Location: maps.google.com/?q=55.75,37.61'. Caregivers can instantly dispatch emergency medical services."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Двусторонняя оффлайн-телеметрия" else "Two-Way Offline Telemetry",
                if (isRu) "Родственник может отправить обычное SMS «сахар», «?», «tir» со своего доверенного номера на телефон подопечного без интернета. TIRUp мгновенно ответит в фоне: «[Имя]: 6.4 ммоль/л → (TIR 89%, 15м назад, батарея 85%)»."
                else "Caregivers can text 'sugar', '?', or 'tir' from whitelisted phones without internet. TIRUp replies immediately: '[Name]: 6.4 mmol/L → (TIR 89%, 15m ago, battery 85%)'."
            )
            y2 = drawBulletPoint(
                c2, y2,
                if (isRu) "Режим опекуна (активация и белый список)" else "Caregiver Mode & Anti-Spam Whitelist",
                if (isRu) "Активируется выбором «📻 Приёмник» в блоке BLE-моста. В блоке «Экстренное SMS» укажите телефон(ы) подопечных: сирена сработает только с этих номеров. Требуются права RECEIVE_SMS и «Поверх других приложений». Кнопка «Проверить сирену и экран» тестирует тревогу без SMS."
                else "Enabled by choosing '📻 Observer' in BLE Bridge. In Emergency SMS set patient numbers: siren triggers only from them. Requires RECEIVE_SMS & 'Display over other apps'. Use 'Test Caregiver Siren & Screen' to verify."
            )
            y2 = drawSectionHeading(c2, y2, if (isRu) "4.2. Настройка разрешений SMS и экрана блокировки" else "4.2. SMS & Lockscreen Permissions")
            y2 = drawParagraph(
                c2, y2,
                if (isRu) "Пациенту требуется разрешение SEND_SMS, опекуну — RECEIVE_SMS и показ поверх других окон. В TIRUp встроен алгоритм автоматической перепроверки системных дескрипторов при возврате из настроек Android."
                else "Patient requires SEND_SMS; caregiver requires RECEIVE_SMS and overlay permission. TIRUp incorporates automatic descriptor re-verification upon returning from Android Settings."
            )
            drawCallout(
                c2, y2, CalloutType.TIP,
                if (isRu) "📱 БЕЗОПАСНОСТЬ И ПРОВЕРКА ЭКСТРЕННОГО SMS:" else "📱 EMERGENCY SMS VERIFICATION:",
                if (isRu) "Пациент может нажать «Отправить тестовое SMS» для проверки отправки. Опекун нажимает «Проверить сирену и экран» и блокирует телефон для проверки пробуждения дисплея."
                else "Patient taps 'Send Test SMS' to test sending. Caregiver taps 'Test Siren & Screen' and locks phone to verify screen wakeup."
            )
            document.finishPage(page2)

            // =========================================================================
            // PAGE 3: ГЛАВА 5 + ГЛАВА 6
            // =========================================================================
            val page3 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 3).create())
            val c3 = page3.canvas
            drawRunningHeaderAndFooter(c3, 3)

            var y3 = 44f
            y3 = drawChapterHeading(
                c3, y3,
                if (isRu) "ГЛАВА 5. КЛИНИЧЕСКАЯ АНАЛИТИКА, AGP, ПАТТЕРНЫ И HbA1c" else "CHAPTER 5. CLINICAL AGP, PATTERNS, HbA1c & COMPENSATOR",
                if (isRu) "Стандарты ATTD/ADA, 12 параметров AGP, лабораторный HbA1c и суточный компенсатор TIR" else "ATTD/ADA consensus, 12 AGP metrics, laboratory HbA1c and daily target compensator"
            )
            y3 = drawSectionHeading(c3, y3, if (isRu) "5.1. Амбулаторный гликемический профиль (AGP) и 12 параметров" else "5.1. Ambulatory Glucose Profile (AGP) & 12 Clinical Metrics")
            y3 = drawParagraph(
                c3, y3,
                if (isRu) "В разделе «Отчёты» формируется стандартизированный отчёт AGP по стандартам консенсуса ATTD/ADA за 7, 14, 30 или 90 дней с расчётом ключевых биомаркеров для эндокринолога:"
                else "The Reports tab generates standardized AGP reports compliant with ATTD/ADA consensus across 7, 14, 30, or 90 days with core biomarkers:"
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "TIR, TBR, TAR" else "TIR, TBR, TAR",
                if (isRu) "Время в целевом диапазоне (TIR 3.9–10.0, норма ≥70%), время ниже диапазона (TBR <3.9, норма <4%, из них <3.0 <1%), время выше диапазона (TAR >10.0, норма <25%)."
                else "Time in Range (TIR 3.9-10.0, target ≥70%), Time Below Range (TBR <3.9, target <4%, severe <3.0 <1%), Time Above Range (TAR >10.0, target <25%)."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Вариабельность (CV, SD)" else "Variability (CV, SD)",
                if (isRu) "Коэффициент вариации CV (целевой ≤36%) и стандартное отклонение SD отражают стабильность сахаров и защиту от внезапных ночных гипогликемий."
                else "Coefficient of Variation CV (target ≤36%) and SD quantify glycemic stability and nocturnal resilience."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "GRI, GVI, PGS, eA1c / GMI" else "GRI, GVI, PGS, eA1c / GMI",
                if (isRu) "Индекс риска GRI (0–100), индекс гликемической вариабельности GVI (отношение длины кривой к идеальной; адаптивен к пропускам броадкаста), статус PGS и расчётный GMI."
                else "Glycemia Risk Index GRI (0-100), Glycemic Variability Index GVI (curve length to ideal line ratio; adaptive to missing samples), PGS, and estimated GMI."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Детектор скрытых клинических паттернов" else "Hidden Patterns Recognition",
                if (isRu) "Алгоритм выявляет ночные провалы в часы сна, феномен утренней зари и постпрандиальные всплески. Тревожные карточки скрываются через 48 ч, информационные — через 24 ч. Доступен раскрывающийся архив скрытых событий."
                else "Detects nocturnal dips, dawn phenomenon, and meal spikes. High-priority cards auto-expire after 48h, informational after 24h, with an expandable archive."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Журнал лабораторного HbA1c и 4 уровня нормы" else "Laboratory HbA1c Journal",
                if (isRu) "Журнал сопоставляет анализы крови с датчиком (GMI). Классификация: <6.1% (норма), 6.1–7.0% (цель), 7.0–8.0% (субкомпенсация), >8.0% (риск). Кнопка подсвечивает свежесть сдачи (<30д — светло-зелёный, 30–360д — зелёный, >360д — жёлтый)."
                else "Logs laboratory HbA1c vs sensor GMI: <6.1% (norm), 6.1-7.0% (target), 7.0-8.0% (sub), >8.0% (risk). Button highlights test freshness."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Суточный компенсатор и точка невозврата" else "Daily Compensator & Point of No Return",
                if (isRu) "Компенсатор рассчитывает точное время удержания диапазона для достижения суточной цели (TIR ≥70% / TING ≥50%). За 1–2 часа до точки невозврата выводится предупреждение «Последний шанс для TIR»."
                else "Calculates strict in-range minutes needed for daily goals (TIR ≥70% / TING ≥50%). Emits 'Last Chance for TIR' 1-2h before mathematical point of no return."
            )
            y3 = drawCallout(
                c3, y3, CalloutType.INFO,
                if (isRu) "📊 ЭКСПОРТ AGP ОТЧЁТА ДЛЯ ВРАЧА:" else "📊 EXPORTING AGP REPORTS FOR PHYSICIANS:",
                if (isRu) "На вкладке «Отчёты» выберите период (например, 14 дней) и нажмите «Создать AGP отчёт (PDF)». Файл можно сохранить в память или мгновенно отправить лечащему врачу в Telegram, WhatsApp или по почте."
                else "On Reports tab select period and tap 'Create AGP Report (PDF)'. Share directly with your endocrinologist via Telegram, WhatsApp or email."
            )

            // 3x CHAPTER SPACING
            y3 += 24f

            // CHAPTER 6
            y3 = drawChapterHeading(
                c3, y3,
                if (isRu) "ГЛАВА 6. ИНТЕРФЕЙС HUD, ВИДЖЕТЫ, УЧЁТ РАСХОДНИКОВ И АРХИВЫ" else "CHAPTER 6. QUICK GLANCE HUD, WIDGETS, SUPPLIES & MAINTENANCE",
                if (isRu) "Quick Glance HUD 108sp, виджеты рабочего стола, учёт расходников и Zero-Lag база" else "Quick Glance HUD 108sp, desktop widgets, supplies tracking and Zero-Lag database"
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Quick Glance HUD и автоскрытие" else "Quick Glance HUD & Auto-Hiding",
                if (isRu) "Удержание центральной кнопки 0.33 с открывает центрированный HUD: сахар 108sp, стрелка 78sp, единицы по центру, карточки инсулина/углеводов (34sp) и телеметрия в одну строку (19sp). Нижняя панель автоскрывается через 2.2 с и просыпается по тапу."
                else "Hold center button 0.33s to summon centered HUD: 108sp glucose, 78sp arrow, centered units, 34sp insulin/carb tiles, and 19sp single-line telemetry. Navigation auto-hides after 2.2s."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Виджеты рабочего стола, экран блокировки/AOD и Пузырёк" else "Widgets, Lockscreen & Floating Bubble",
                if (isRu) "5 форматов виджетов рабочего стола (полоса 5х1, Canvas-график 4х2/3х2, квадрат 2х2, стек 1х2). На экране блокировки доступна регулировка прозрачности (0–100%). Плавающий «Пузырёк» виден поверх всех приложений и пульсирует волнами при гипо."
                else "5 homescreen widget sizes (strip 5x1, Canvas graph 4x2/3x2, square 2x2, vertical 1x2). Lockscreen opacity slider 0-100%. Floating Bubble pulses ripple waves during hypo."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Сроки службы устройств и отрицательные счётчики" else "Supplies Lifespan & Negative Counters",
                if (isRu) "Раздельный учёт датчика CGM (10–14д), канюли помпы (3д) и ланцета. При просрочке счётчик переходит в отрицательные значения: до 24 ч (-Xч), 1–30 дней (-Xд), >30 дней (-Xм)."
                else "Separate tracking for CGM sensor (10-14d), cannula (3d), and lancet. Expired supplies display negative counters: <24h (-Xh), 1-30d (-Xd), >30d (-Xm)."
            )
            y3 = drawBulletPoint(
                c3, y3,
                if (isRu) "Архитектура Zero-Lag и ежедневные резервные копии" else "Zero-Lag Engine & Automated Backups",
                if (isRu) "В конце каждого года замеры архивируются в tirup_readings_YYYY.csv. База данных всегда работает мгновенно без лагов за любые годы. Каждую полночь в 23:59:59 создаётся локальный автобэкап, доступен ручной ZIP-экспорт."
                else "Past years are archived into tirup_readings_YYYY.csv. Active database stays ultra-fast. Daily midnight auto-backup at 23:59:59 plus manual ZIP export."
            )
            drawCallout(
                c3, y3, CalloutType.INFO,
                if (isRu) "⚖️ ЮРИДИЧЕСКИЙ МЕДИЦИНСКИЙ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ:" else "⚖️ LEGAL MEDICAL DISCLAIMER NOTICE:",
                if (isRu) "TIRUp является программным средством для информационного самоконтроля образа жизни при диабете. Приложение не является сертифицированным медицинским прибором. Всегда проверяйте показания глюкометром по капле крови перед принятием решений о дозах инсулина."
                else "TIRUp is an auxiliary lifestyle self-monitoring tool. It is not an officially certified medical device. Always verify CGM readings with a capillary blood meter prior to therapeutic insulin adjustments."
            )
            document.finishPage(page3)

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
