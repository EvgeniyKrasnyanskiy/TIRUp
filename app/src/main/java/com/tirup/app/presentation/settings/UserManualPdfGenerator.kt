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
 * Generates an exhaustive A4 manual with classic book typography, natural content flow across pages
 * (no artificial chapter page breaks), non-overlapping bullet lists with bold titles, and dynamic clinical callout boxes.
 */
class UserManualPdfGenerator(private val context: Context) {

    enum class CalloutType {
        INFO, TIP, WARNING, CRITICAL
    }

    private data class ParagraphLayout(
        val lines: List<String>,
        val height: Float
    )

    private data class BulletLayout(
        val titleColon: String,
        val titleWidth: Float,
        val firstLine: String,
        val subLines: List<String>,
        val height: Float
    )

    private data class CalloutLayout(
        val type: CalloutType,
        val title: String,
        val lines: List<String>,
        val boxWidth: Float,
        val totalHeight: Float,
        val advanceY: Float
    )

    suspend fun generateUserManualPdf(isRu: Boolean): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.3"
            } catch (_: Exception) {
                "2.2.3"
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

            fun drawRunningHeaderAndFooter(canvas: Canvas, pageNum: Int, totalPages: Int) {
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

            fun measureParagraph(text: String, maxWidth: Float = 523f, lineHeight: Float = 10.4f): ParagraphLayout {
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
                val height = if (lines.isEmpty()) 0f else 12.0f + (lines.size - 1) * lineHeight
                return ParagraphLayout(lines, height)
            }

            fun layoutBulletPoint(
                title: String,
                desc: String,
                maxWidth: Float = 523f,
                lineHeight: Float = 10.4f
            ): BulletLayout {
                val titleColon = "$title: "
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

                val subAvail = maxWidth - 11f
                val subLines = mutableListOf<String>()
                var subLine = ""
                while (wordIdx < words.size) {
                    val w = words[wordIdx]
                    val test = if (subLine.isEmpty()) w else "$subLine $w"
                    if (bodyPaint.measureText(test) <= subAvail) {
                        subLine = test
                        wordIdx++
                    } else {
                        subLines.add(subLine)
                        subLine = w
                        wordIdx++
                    }
                }
                if (subLine.isNotEmpty()) {
                    subLines.add(subLine)
                }

                val height = 11.0f + (subLines.size * lineHeight)
                return BulletLayout(titleColon, titleWidth, firstLine, subLines, height)
            }

            fun layoutCallout(
                type: CalloutType,
                title: String,
                text: String,
                boxWidth: Float = 523f
            ): CalloutLayout {
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
                val advanceY = 2f + totalHeight + 4.2f
                return CalloutLayout(type, title, lines, boxWidth, totalHeight, advanceY)
            }

            // Dynamic Flow Engine for Natural Content Flow Across Pages
            class ManualFlowContext(
                val isDryRun: Boolean,
                val totalPages: Int,
                val chapterPages: IntArray
            ) {
                var currentPageIndex = 1
                var currentPage: PdfDocument.Page? = null
                var currentCanvas: Canvas? = null
                val startY = 44f
                val maxY = 804f // 10pt safety margin before footer rule at 814f
                var y = startY

                fun startDocument() {
                    currentPageIndex = 1
                    y = startY
                    if (!isDryRun) {
                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPageIndex).create()
                        val page = document.startPage(pageInfo)
                        currentPage = page
                        currentCanvas = page.canvas
                        drawRunningHeaderAndFooter(page.canvas, currentPageIndex, totalPages)
                    }
                }

                fun ensureSpace(neededHeight: Float) {
                    if (y + neededHeight > maxY) {
                        currentPageIndex++
                        if (!isDryRun) {
                            currentPage?.let { document.finishPage(it) }
                            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPageIndex).create()
                            val page = document.startPage(pageInfo)
                            currentPage = page
                            currentCanvas = page.canvas
                            drawRunningHeaderAndFooter(page.canvas, currentPageIndex, totalPages)
                        }
                        y = startY
                    }
                }

                fun chapter(chapNum: Int, title: String, subtitle: String) {
                    val headingHeight = 26.5f
                    val orphanPreview = 40f
                    val spacing = if (y > startY + 1f) 10f else 0f
                    if (y + spacing + headingHeight + orphanPreview > maxY) {
                        ensureSpace(maxY + 100f) // Force clean page break
                    } else {
                        y += spacing
                    }
                    chapterPages[chapNum] = currentPageIndex
                    val canvas = currentCanvas
                    if (canvas != null) {
                        var cy = y + 3f
                        canvas.drawText(title, 36f, cy + 9f, chapterTitlePaint)
                        cy += 12.0f
                        canvas.drawText(subtitle, 36f, cy + 7f, chapterSubtitlePaint)
                    }
                    y += headingHeight
                }

                fun section(heading: String) {
                    val headingHeight = 13.0f
                    val orphanPreview = 35f
                    if (y + headingHeight + orphanPreview > maxY) {
                        ensureSpace(maxY + 100f) // Keep section header with its first content item
                    }
                    val canvas = currentCanvas
                    if (canvas != null) {
                        val cy = y + 2f
                        canvas.drawText(heading, 36f, cy + 8f, sectionHeadingPaint)
                    }
                    y += headingHeight
                }

                fun paragraph(text: String) {
                    val layout = measureParagraph(text)
                    ensureSpace(layout.height)
                    val canvas = currentCanvas
                    if (canvas != null) {
                        var lineY = y + 8.0f
                        for (l in layout.lines) {
                            canvas.drawText(l, 36f, lineY, bodyPaint)
                            lineY += 10.4f
                        }
                    }
                    y += layout.height
                }

                fun bullet(title: String, desc: String) {
                    val layout = layoutBulletPoint(title, desc)
                    ensureSpace(layout.height)
                    val canvas = currentCanvas
                    if (canvas != null) {
                        var lineY = y + 8.0f
                        canvas.drawText("•", 38f, lineY, bulletSymbolPaint)
                        canvas.drawText(layout.titleColon, 47f, lineY, bulletTitlePaint)
                        if (layout.firstLine.isNotEmpty()) {
                            canvas.drawText(layout.firstLine, 47f + layout.titleWidth, lineY, bodyPaint)
                        }
                        for (sub in layout.subLines) {
                            lineY += 10.4f
                            canvas.drawText(sub, 47f, lineY, bodyPaint)
                        }
                    }
                    y += layout.height
                }

                fun callout(type: CalloutType, title: String, text: String) {
                    val layout = layoutCallout(type, title, text)
                    ensureSpace(layout.advanceY)
                    val canvas = currentCanvas
                    if (canvas != null) {
                        val cy = y + 2f
                        val (bgColor, borderColor, accentColor, textColor) = when (layout.type) {
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

                        val boxRect = RectF(36f, cy, 36f + layout.boxWidth, cy + layout.totalHeight)
                        canvas.drawRoundRect(boxRect, 4f, 4f, calloutBgPaint)
                        canvas.drawRoundRect(boxRect, 4f, 4f, calloutBorderPaint)

                        // Left accent bar
                        val barRect = RectF(36f, cy, 40f, cy + layout.totalHeight)
                        canvas.drawRoundRect(barRect, 2f, 2f, calloutAccentBarPaint)

                        canvas.drawText(layout.title, 46f, cy + 9.5f, calloutTitlePaint)

                        var lineY = cy + 19.0f
                        for (l in layout.lines) {
                            canvas.drawText(l, 46f, lineY, calloutBodyPaint)
                            lineY += 9.0f
                        }
                    }
                    y += layout.advanceY
                }

                fun finishDocument() {
                    if (!isDryRun) {
                        currentPage?.let { document.finishPage(it) }
                        currentPage = null
                        currentCanvas = null
                    }
                }
            }

            // Sequential Content Flow
            fun renderManual(flow: ManualFlowContext) {
                // Page 1: Header, Subtitle & TOC
                val canvas = flow.currentCanvas
                if (canvas != null) {
                    canvas.drawText(
                        if (isRu) "TIRUp • РУКОВОДСТВО ПОЛЬЗОВАТЕЛЯ" else "TIRUp • USER MANUAL",
                        36f, flow.y + 10f, docTitlePaint
                    )
                }
                flow.y += 13.5f

                if (canvas != null) {
                    canvas.drawText(
                        if (isRu) "Клинический справочник, автономная архитектура, тревоги и защита безопасности"
                        else "Clinical reference, zero-cloud architecture, safety alarms and device integration",
                        36f, flow.y + 7f, chapterSubtitlePaint
                    )
                }
                flow.y += 11.5f

                // TOC Box
                if (canvas != null) {
                    val tocRect = RectF(36f, flow.y, 559f, flow.y + 29f)
                    calloutBgPaint.color = Color.rgb(248, 250, 252)
                    calloutBorderPaint.color = Color.rgb(203, 213, 225)
                    canvas.drawRoundRect(tocRect, 4f, 4f, calloutBgPaint)
                    canvas.drawRoundRect(tocRect, 4f, 4f, calloutBorderPaint)
                    canvas.drawText(if (isRu) "СОДЕРЖАНИЕ РУКОВОДСТВА:" else "TABLE OF CONTENTS:", 44f, flow.y + 9.5f, tocTitlePaint)

                    val cp = flow.chapterPages
                    canvas.drawText(
                        if (isRu) "Гл. 1: Связь и BLE (с. ${cp[1]}) • Гл. 2: Фон и OEM (с. ${cp[2]}) • Гл. 3: Тревоги и Спасение (с. ${cp[3]})"
                        else "Ch. 1: Connectivity & BLE (p. ${cp[1]}) • Ch. 2: Background & OEM (p. ${cp[2]}) • Ch. 3: Alarms & Rescue (p. ${cp[3]})",
                        44f, flow.y + 17.5f, tocBodyPaint
                    )
                    canvas.drawText(
                        if (isRu) "Гл. 4: SOS SMS и Фоловер (с. ${cp[4]}) • Гл. 5: AGP и HbA1c (с. ${cp[5]}) • Гл. 6: HUD и Расходники (с. ${cp[6]})"
                        else "Ch. 4: SOS SMS & Follower (p. ${cp[4]}) • Ch. 5: Clinical AGP (p. ${cp[5]}) • Ch. 6: HUD & Supplies (p. ${cp[6]})",
                        44f, flow.y + 25.5f, tocBodyPaint
                    )
                }
                flow.y += 33.5f

                // Quick Start Callout
                flow.callout(
                    CalloutType.TIP,
                    if (isRu) "🚀 БЫСТРЫЙ СТАРТ ЗА 3 ШАГА (ДЛЯ НОВЫХ ПОЛЬЗОВАТЕЛЕЙ):" else "🚀 3-STEP QUICK START GUIDE:",
                    if (isRu) "1) В xDrip+ («Настройки ➔ Межпрограммная интеграция») включите «Широковещательные передачи». 2) В настройках Android отключите оптимизацию батареи («Без ограничений»). 3) Задайте целевой диапазон сахара в Настройках TIRUp."
                    else "1) In xDrip+ enable 'Broadcast locally'. 2) Disable Android battery optimization for TIRUp ('Unrestricted'). 3) Set your target glucose range in TIRUp Settings."
                )

                // =========================================================================
                // CHAPTER 1: CONNECTIVITY & BLE BRIDGE
                // =========================================================================
                flow.chapter(
                    1,
                    if (isRu) "ГЛАВА 1. АРХИТЕКТУРА СВЯЗИ, ИСТОЧНИКИ ДАННЫХ И BLE-МОСТ" else "CHAPTER 1. CONNECTIVITY, DATA SOURCES & FAMILY BLE BRIDGE",
                    if (isRu) "Zero-Cloud принцип, локальные интенты и автономный семейный Bluetooth-мост" else "Zero-Cloud principle, local Android intents and offline Family BLE Bridge"
                )
                flow.paragraph(
                    if (isRu) "Приложение TIRUp создано по принципу максимальной автономности (Zero-Cloud). В отличие от облачных CGM-систем, TIRUp никогда не требует подключения к интернету, внешних серверов или передачи личных данных. Все вычисления компенсации и тревоги выполняются на 100% локально на смартфоне."
                    else "TIRUp operates on the Zero-Cloud principle without internet dependencies, external servers, or data uploads. All calculations and alarms execute 100% locally on your smartphone."
                )
                flow.bullet(
                    if (isRu) "xDrip+, GDH и Juggluco" else "xDrip+, GDH & Juggluco",
                    if (isRu) "В настройках источника перейдите в «Межпрограммная интеграция» и включите «Широковещательные передачи» и «Поддержку широковещательной службы» (для мгновенной передачи доз IoB/CoB). TIRUp принимает замеры через локальные Intents с задержкой <0.1 с."
                    else "Enable 'Broadcast locally' and 'Broadcast service support' for IoB/CoB in your source. TIRUp captures streaming readings via low-latency Android Intents (<0.1s)."
                )
                flow.bullet(
                    if (isRu) "Семейный BLE-мост (Вещатель — Приёмник)" else "Family BLE Bridge",
                    if (isRu) "Передача сахара ребёнка родителю по Bluetooth LE без Wi-Fi и SIM. Телефон ребёнка настраивается как «Вещатель» (импульс 15 с на замер, микропотребление <0.8% батареи/сутки), телефон родителя — как «Приёмник». Пакет шифруется 4-значным Family PIN."
                    else "Stream patient glucose to parent over BLE without Wi-Fi or SIM. Broadcaster pulses 15s bursts (<0.8% battery/day), Observer receives. Secured with 4-digit PIN."
                )
                flow.bullet(
                    if (isRu) "Режим Long Range (LE Coded PHY)" else "Long Range Mode (LE Coded PHY)",
                    if (isRu) "На чипсетах Bluetooth 5.0+ режим Coded PHY (S=8) повышает потенциал радиолинии на 8–10 dBm, расширяя дальность в 2–4 раза (до 30–50 м сквозь стены). При отсутствии поддержки аппаратно откатывается на Legacy 1M. Сканер приёмника слушает оба диапазона автоматически."
                    else "On Bluetooth 5.0+, Coded PHY (S=8) boosts link budget by 8-10 dBm, extending range 2-4x (up to 30-50m through walls). Observer operates in dual-mode automatically."
                )
                flow.bullet(
                    if (isRu) "Разница технологий: BLE-мост TIRUp vs Зеркало Juggluco" else "TIRUp BLE vs Juggluco Mirroring",
                    if (isRu) "TIRUp работает как автономный радиомаяк (Broadcast): без сопряжения, расход <1% батареи, свободен BT-канал для помпы и часов (без квитирования ACK). Juggluco держит постоянный сокет (RFCOMM/Wi-Fi) с гарантией доставки ACK и выкачкой всей истории SQLite, но требует сопряжения и держит постоянное соединение."
                    else "TIRUp uses connectionless BLE broadcast: zero-pairing, <1% battery, free BT slot for pump/watch (no ACK). Juggluco maintains a persistent socket (RFCOMM/Wi-Fi) with delivery ACKs and SQLite history backfill, but requires device pairing and holds connection."
                )
                flow.callout(
                    CalloutType.INFO,
                    if (isRu) "📡 ДИАГНОСТИКА СИГНАЛА И БАТАРЕИ ПЕРЕДАТЧИКА:" else "📡 TRANSMITTER SIGNAL & BATTERY:",
                    if (isRu) "В шторке уведомлений и в HUD приёмника отображаются уровень радиосигнала (RSSI dBm) и процент заряда батареи подопечного. Для быстрой проверки используйте «Тест дальности (5с)»."
                    else "Notification and HUD widget display real-time transmitter RSSI (dBm) and battery %. Use 'Range Test (5s)' in Observer settings for immediate verification."
                )

                // =========================================================================
                // CHAPTER 2: BACKGROUND RELIABILITY & OEM-SPECIFIC GUIDES
                // =========================================================================
                flow.chapter(
                    2,
                    if (isRu) "ГЛАВА 2. НАДЕЖНОСТЬ В ФОНЕ И НАСТРОЙКА OEM-ПРОШИВОК" else "CHAPTER 2. BACKGROUND RELIABILITY & OEM-SPECIFIC GUIDES",
                    if (isRu) "Преодоление Doze Mode, App Standby и пошаговые чек-листы для вендоров" else "Overcoming Doze Mode, App Standby and step-by-step checklists for major OEMs"
                )
                flow.section(if (isRu) "2.1. Чек-листы фоновой работы для популярных смартфонов" else "2.1. Background Reliability Checklists for Major OEMs")
                flow.bullet(
                    if (isRu) "Samsung (OneUI)" else "Samsung (OneUI)",
                    if (isRu) "1) Настройки ➔ Приложения ➔ TIRUp ➔ Батарея ➔ «Не ограничено». 2) Настройки ➔ Батарея ➔ «Ограничения в фоновом режиме» ➔ «Никогда не спящие приложения» ➔ добавьте TIRUp и источник. 3) Закрепите замочком в меню недавних."
                    else "1) Settings ➔ Apps ➔ TIRUp ➔ Battery ➔ 'Unrestricted'. 2) Battery ➔ Background limits ➔ 'Never sleeping apps' ➔ Add TIRUp. 3) Lock app card in Recents."
                )
                flow.bullet(
                    if (isRu) "Xiaomi, Redmi, POCO (MIUI / HyperOS)" else "Xiaomi (MIUI / HyperOS)",
                    if (isRu) "1) Включите «Автозапуск» и «Разрешить запуск другими приложениями». 2) «Контроль активности» ➔ «Нет ограничений». 3) «Другие разрешения» ➔ «Экран блокировки» и «Всплывающие окна». 4) Закрепите замочком в Недавних."
                    else "1) Enable 'Autostart'. 2) Battery saver ➔ 'No restrictions'. 3) Other permissions ➔ Allow 'Lock screen' & 'Pop-up windows'. 4) Lock app in Recents."
                )
                flow.bullet(
                    if (isRu) "BBK: Realme, Oppo, OnePlus (ColorOS)" else "BBK: Realme, Oppo, OnePlus",
                    if (isRu) "1) Управление приложениями ➔ TIRUp ➔ «Расход батареи» ➔ разрешите фоновую активность и автозапуск. 2) «Оптимизация режима ожидания» ➔ отключите «Ультра-режим ожидания». 3) Заблокируйте в Недавних."
                    else "1) App management ➔ TIRUp ➔ Battery ➔ Allow background & auto-launch. 2) Disable sleep standby optimization. 3) Lock app in Recents."
                )
                flow.bullet(
                    if (isRu) "Huawei, Honor (EMUI / MagicOS)" else "Huawei, Honor (EMUI / MagicOS)",
                    if (isRu) "Настройки ➔ Приложения ➔ Запуск приложений ➔ для TIRUp отключите автоуправление и включите: «Автозапуск», «Косвенный запуск», «Работа в фоне». Закрепите замочком в Недавних."
                    else "Apps ➔ App launch ➔ Disable automatic for TIRUp, enable 'Auto-launch', 'Secondary launch', 'Run in background'. Lock in Recents."
                )
                flow.bullet(
                    if (isRu) "Google Pixel (Stock Android)" else "Google Pixel (Stock Android)",
                    if (isRu) "Настройки ➔ Батарея ➔ Адаптивные настройки ➔ отключите «Адаптивный расход». В свойствах TIRUp выберите «Использование батареи ➔ Без ограничений»."
                    else "Battery ➔ Adaptive preferences ➔ Disable 'Adaptive Battery'. In TIRUp app info set 'Battery ➔ Unrestricted'."
                )
                flow.section(if (isRu) "2.2. Полноэкранные интенты и пробуждение дисплея" else "2.2. Full-Screen Intents & Display Wakeup")
                flow.paragraph(
                    if (isRu) "При критической ночной гипогликемии дисплей заблокирован. Экран спасения использует аппаратный WakeLock (ACQUIRE_CAUSES_WAKEUP) и флаги FLAG_SHOW_WHEN_LOCKED, гарантированно включая дисплей с сиреной. Предоставьте права «Поверх других приложений»."
                    else "During severe low, Rescue Screen utilizes hardware WakeLock and FLAG_SHOW_WHEN_LOCKED to reliably awaken the screen. Grant 'Display over other apps' permission."
                )
                flow.callout(
                    CalloutType.WARNING,
                    if (isRu) "⚠️ КОНТРОЛЬНЫЙ ЧЕК-ЛИСТ ПЕРЕД ПЕРВОЙ НОЧЬЮ:" else "⚠️ PRE-FLIGHT CHECKLIST BEFORE NIGHT:",
                    if (isRu) "Внизу экрана настроек выполните 5 быстрых тапов по строке «TIRUp • Версия ...» для открытия скрытого блока тестирования. Нажмите «Тест экрана спасения» и заблокируйте телефон. Через 5 с дисплей должен сам проснуться с сиреной."
                    else "Tap 'TIRUp • Version ...' at the bottom of Settings 5 times to reveal tests. Run 'Test Patient Rescue Screen' and lock phone. Within 5s display must wake up with siren."
                )

                // =========================================================================
                // CHAPTER 3: MULTI-TIER ALARMS & RESCUE SCREEN
                // =========================================================================
                flow.chapter(
                    3,
                    if (isRu) "ГЛАВА 3. МНОГОУРОВНЕВАЯ СИСТЕМА ТРЕВОГ И ЭКРАН СПАСЕНИЯ" else "CHAPTER 3. 4-TIER ALARMS, COMA GUARD & RESCUE SCREEN",
                    if (isRu) "Клиническая градация Tier 1–4, предиктивный тренд на 25 минут, обход DND и Coma Guard" else "Tier 1-4 escalation, 25-min predictive trend, DND bypass and Coma Guard protocol"
                )
                flow.paragraph(
                    if (isRu) "Одной из главных проблем пользователей CGM является «усталость от тревог» (Alarm Fatigue), когда частые ложные сигналы приводят к отключению звука. В TIRUp реализована адаптивная 4-уровневая система безопасности, которая отсекает шум и включает сирены только тогда, когда существует реальная клиническая угроза здоровью:"
                    else "To resolve CGM alarm fatigue, TIRUp incorporates an adaptive 4-tier safety model that filters transient sensor artifacts and escalates alerts only for verified clinical hazards:"
                )
                flow.bullet(
                    if (isRu) "Уровень 1: Предиктивный тренд на 25 минут" else "Tier 1: 25-Min Predictive Trend",
                    if (isRu) "Регрессионный анализ скользящего окна точек с экспоненциальным затуханием проецирует траекторию на 25 мин вперёд (фиолетовые точки на суточном графике). Мягкий сигнал звучит за 15 мин до расчётной гипогликемии, позволяя принять углеводы заранее."
                    else "Linear regression with exponential damping projects glucose 25 mins ahead (purple dots on daily chart). Gentle chime warns 15 mins before calculated low, allowing timely carb intake."
                )
                flow.bullet(
                    if (isRu) "Уровень 2: Подтверждённое отклонение (3–5 точек)" else "Tier 2: Confirmed Departure (3-5 Points)",
                    if (isRu) "Классический тройной сигнал при выходе за пределы нормы (3 точки для 5-мин датчиков, 5 точек для 1-мин). Дисплей принудительно НЕ зажигается (только звук и шторка). При гипергликемии сигнал глушится, если сахар падает и есть активный инсулин (IoB)."
                    else "Triple tone when outside target (3 points on 5-min CGM, 5 points on 1-min). Display does NOT forcibly turn on (audio & shade only). Auto-mutes on high if dropping with active IoB."
                )
                flow.bullet(
                    if (isRu) "Уровень 3: Опасные и критические тревоги" else "Tier 3: Dangerous & Critical Alerts",
                    if (isRu) "Затяжные тревоги (>20 мин гипо или >90 мин гипер) звучат 12-сек. медицинским сигналом. При критических порогах (<3.0 или >13.9 ммоль/л) включаются мощные сирены, экран принудительно загорается поверх блокировки (WakeLock), обходится режим «Не беспокоить» (DND bypass) на 100% громкости USAGE_ALARM и пульсирует вспышка камеры. Настройка порогов и прослушивание сирен объединены в единую плашку «🚨 Критические тревоги»."
                    else "Prolonged alerts (>20m hypo or >90m hyper) sound a 12s medical chime. Critical thresholds (<3.0 or >13.9 mmol/L) trigger powerful emergency sirens over lockscreen (WakeLock), bypass DND at 100% volume, and pulse camera flash. Configured via the unified '🚨 Critical Alerts' dialog."
                )
                flow.bullet(
                    if (isRu) "Уровень 4: Потеря сигнала (20–25 мин) и ночной профиль" else "Tier 4: Signal Loss & Night Profile",
                    if (isRu) "При отсутствии точек >20 мин подаётся сигнал будильника. В Настройках задаются «Часы ночного сна» (по умолчанию 23:00–07:00), в этот период действуют отдельные ночные пороги тревог и строгий контроль связи."
                    else "Alarm sounds if readings stop for >20 mins. Configurable Night Sleep Window (default 23:00-07:00) applies dedicated nocturnal thresholds and strict signal checks."
                )
                flow.section(if (isRu) "3.2. Градация сирен: Крит. ГИПО (GDH) и Крит. ГИПЕР" else "3.2. Siren Escalation: Crit. HYPO (GDH) & Crit. HYPER")
                flow.paragraph(
                    if (isRu) "В TIRUp критические тревоги разделены на мгновенные экстремальные и подтверждённые затяжные с уникальным математически синтезированным звуковым оформлением (PCM без утяжеления приложения аудиофайлами):"
                    else "In TIRUp critical alerts are segregated into instant extreme and verified prolonged states with mathematically synthesized PCM tones (zero APK audio bloat):"
                )
                flow.bullet(
                    if (isRu) "🚨 Крит. ГИПО (<3.0 ммоль/л или настроенный порог) — 50-секундная сирена ГО" else "🚨 Crit. HYPO (<3.0 mmol/L) — 50-Second GDH Siren",
                    if (isRu) "Непрерывная мощная сирена гражданской обороны с частотной модуляцией 520–980 Гц, добавлением 2-й, 3-й (1.5–3 кГц) и 4-й гармоник с аналоговым насыщением для максимальной акустической громкости на динамиках смартфона. Срабатывает мгновенно без ожидания повторных точек. Предназначена для гарантированного пробуждения из самого глубокого сна как самого пациента, так и фоловера при поступлении SOS."
                    else "Continuous 50s civil defense air-raid siren sweeping smoothly between 520 Hz and 980 Hz with piercing harmonics and tanh overdrive for maximum phone speaker loudness. Triggers instantly without multi-point delay to awaken patient or follower."
                )
                flow.bullet(
                    if (isRu) "⚠️ Крит. ГИПЕР (>13.9 ммоль/л или настроенный порог) — 16-секундный резкий пульс" else "⚠️ Crit. HYPER (>13.9 mmol/L) — 16-Second Piercing Pulse",
                    if (isRu) "Серия высокочастотных резких пульсирующих сигналов (1760/2349 Гц), резко контрастирующая с сиреной гипогликемии. Предупреждает о критической гипергликемии и необходимости контроля подколки/кетонов."
                    else "High-urgency alternating chime bursts (1760 Hz & 2349 Hz) lasting 16 seconds. Clearly distinguishes extreme hyperglycemia from hypo alarms."
                )
                flow.bullet(
                    if (isRu) "🔔 Затяжные опасные тревоги (ГИПО >20 мин или ГИПЕР >90 мин)" else "🔔 Prolonged Dangerous Alerts (Hypo >20m or Hyper >90m)",
                    if (isRu) "Воспроизводят стандартную 12-секундную медицинскую сирену. При затяжной гипергликемии сигнал глушится, если есть активный болюс (IoB) и зафиксирована динамика падения сахара."
                    else "Standard 12s medical alarm series. Automatically mutes during prolonged hyper if corrective bolus (IoB) is active and glucose is declining."
                )
                flow.section(if (isRu) "3.3. Экран спасения и защита от комы (Coma Guard <2.8 ммоль/л)" else "3.3. Patient Rescue Screen & Coma Guard (<2.8 mmol/L)")
                flow.paragraph(
                    if (isRu) "При сахаре <3.0 ммоль/л (или затяжной гипо >20 мин) TIRUp пробуждает спящий телефон и разворачивает поверх пароля боевой интерфейс спасения с крупными цифрами сахара, стрелкой падения, таймером SOS SMS и кнопкой купирования. При сахаре ниже 2.8 ммоль/л включается Coma Guard: снуз ограничен 5 мин, сирена повторяется каждые 5 мин до подтверждения."
                    else "On glucose <3.0 mmol/L (or prolonged hypo >20m) TIRUp wakes screen over lockscreen with giant glucose, trend arrow, and SOS timer. Below 2.8 mmol/L Coma Guard caps snooze to 5 mins, repeating siren every 5 mins until confirmed."
                )
                flow.callout(
                    CalloutType.CRITICAL,
                    if (isRu) "🚨 КЛИНИЧЕСКИЙ ПРОТОКОЛ КУПИРОВАНИЯ (ПРАВИЛО 15):" else "🚨 CLINICAL HYPO PROTOCOL (RULE OF 15):",
                    if (isRu) "При гипогликемии примите 15 г быстрых углеводов (сок, декстроза). Нажмите кнопку «Углеводы приняты» на экране спасения — это заглушит сирену и отменит отправку экстренного SOS SMS родственникам."
                    else "Take 15g fast-acting carbs (juice, dextrose). Press 'Carbs Taken' on Rescue Screen to silence siren and cancel emergency SOS SMS dispatch."
                )

                // =========================================================================
                // CHAPTER 4: EMERGENCY SOS SMS, FOLLOWER MODE & TELEMETRY
                // =========================================================================
                flow.chapter(
                    4,
                    if (isRu) "ГЛАВА 4. ЭКСТРЕННЫЕ SMS, РЕЖИМ ФОЛОВЕРА И ТЕЛЕМЕТРИЯ" else "CHAPTER 4. EMERGENCY SOS SMS, FOLLOWER MODE & TELEMETRY",
                    if (isRu) "Автономные оповещения с GPS, Heads-Up оверлей сообщений, запрос сахара и сирена фоловера" else "Offline GPS distress SMS, Heads-Up messaging overlay, glucose queries and follower alarms"
                )
                flow.paragraph(
                    if (isRu) "Если мастер находится в состоянии тяжёлой гипогликемии и не отключает сирену в течение заданного времени (по умолчанию 3 мин), TIRUp расценивает это как возможную потерю сознания. Приложение запрашивает координаты GPS и автоматически отправляет экстренное SMS доверенным лицам:"
                    else "If hypo alarm is unacknowledged for the delay (default 3 mins), TIRUp retrieves device GPS and transmits emergency distress SMS to follower phones:"
                )
                flow.bullet(
                    if (isRu) "Содержание тревожного SOS SMS" else "Distress SOS SMS Format",
                    if (isRu) "«SOS! У [Имя] критический сахар: 2.5 ммоль/л ⇊. Нет реакции на сирену 3 мин. Геолокация: maps.google.com/?q=55.75,37.61». Близкие получают координаты и могут оперативно прийти на помощь."
                    else "'SOS! [Name] critical glucose: 2.5 mmol/L ⇊. Unresponsive 3 min. Location: maps.google.com/?q=55.75,37.61'. Followers instantly receive GPS coordinates."
                )
                flow.bullet(
                    if (isRu) "💬 Heads-Up оверлей важных сообщений (Мастер ⇄ Фоловер)" else "💬 Heads-Up Direct Messaging (Master ⇄ Follower)",
                    if (isRu) "При получении важного текстового SMS от доверенного контакта экран смартфона мягко зажигается на 15 с поверх блокировки с пульсирующей синей каймой и тактильной вибрацией (без визжащей сирены!). Показывается крупный текст, кнопка «ОК!» и форма быстрого ответа (до 70 симв.) с готовыми чипами: «Выпил сок», «Уколол», «Принято»."
                    else "When receiving a human text SMS from a trusted contact, the screen wakes for 15s over lockscreen with a pulsing cyan border and gentle haptic vibration. Features large text, 'OK!' button, and a quick-reply dialog (up to 70 chars) with one-tap templates."
                )
                flow.bullet(
                    if (isRu) "Бесшумная телеметрия под капотом" else "Silent Under-The-Hood Telemetry",
                    if (isRu) "Фоловер может отправить служебное SMS «сахар», «?», «tir» со своего номера. Телефон мастера обработает команду полностью бесшумно под капотом (не зажигая экран и не вибрируя) и мгновенно ответит свежими данными сахара, стрелкой тренда и TIR."
                    else "Followers can text service queries like 'sugar', '?', or 'tir'. The master phone processes this silently under the hood without waking screen or vibrating, instantly replying with glucose, trend, and today's TIR."
                )
                flow.bullet(
                    if (isRu) "Роли «Мастер» и «Фоловер» вверху настроек" else "Master & Follower Roles",
                    if (isRu) "Переключатель роли расположен сразу под карточкой «Мой профиль». Для мастера кнопка «Тест» проверяет экран спасения при гипогликемии, а для фоловера — сирену и экран с карточкой подопечного."
                    else "Device role is toggled at the very top of Settings under My Profile. Master role tests hypo rescue, while Follower role tests incoming distress sirens and patient telemetry card."
                )
                flow.section(if (isRu) "4.2. Настройка разрешений SMS и экрана блокировки" else "4.2. SMS & Lockscreen Permissions")
                flow.paragraph(
                    if (isRu) "Мастеру требуется разрешение SEND_SMS, фоловеру — RECEIVE_SMS и показ поверх других окон. В TIRUp встроен алгоритм автоматической перепроверки системных дескрипторов при возврате из настроек Android."
                    else "Master requires SEND_SMS; follower requires RECEIVE_SMS and overlay permission. TIRUp incorporates automatic descriptor re-verification upon returning from Android Settings."
                )
                flow.callout(
                    CalloutType.WARNING,
                    if (isRu) "⚠️ ОБЯЗАТЕЛЬНЫЕ РАЗРЕШЕНИЯ ДЛЯ ЭКРАНА СПАСЕНИЯ И HEADS-UP СООБЩЕНИЙ:" else "⚠️ REQUIRED PERMISSIONS FOR RESCUE SCREEN & HEADS-UP MESSAGES:",
                    if (isRu) "Чтобы экраны спасения и важных сообщений открывались поверх заблокированного экрана, необходимо выдать два специальных разрешения: 1) Настройки Андроид → Приложения → Специальный доступ → Отображать поверх других приложений → TIRUp → включить. 2) Настройки Андроид → Приложения → Специальный доступ → Отправлять полноэкранные уведомления → TIRUp → включить. Точные названия пунктов меню могут незначительно отличаться в зависимости от производителя."
                    else "To allow Rescue and Heads-Up screens to appear over the locked display, grant two special permissions: 1) Android Settings → Apps → Special app access → Display over other apps → TIRUp → Enable. 2) Android Settings → Apps → Special app access → Send full-screen notifications → TIRUp → Enable. Exact menu names may vary by manufacturer."
                )
                flow.callout(
                    CalloutType.TIP,
                    if (isRu) "📱 ПРОВЕРКА ЭКРАНОВ НА ЗАБЛОКИРОВАННОМ УСТРОЙСТВЕ:" else "📱 VERIFY SCREENS ON A LOCKED DEVICE:",
                    if (isRu) "В блоке «Тестирование систем» кнопки «Экран SOS фоловера (5 сек)» и «Экран важное SMS (5 сек)» дают 5 секунд для блокировки телефона перед показом. ВАЖНО: если информация после теста не появилась на заблокированном экране — обязательно проверьте разрешения «Поверх других приложений» и «Полноэкранные уведомления» в настройках Android, затем протестируйте снова."
                    else "In System Testing, 'Follower SOS Screen (5s)' and 'Heads-Up SMS screen (5s)' buttons provide 5 seconds to lock the phone before display. IMPORTANT: if no overlay appeared on the locked screen, check 'Display over other apps' and 'Full-screen notifications' permissions in Android Settings, then test again."
                )
                flow.section(if (isRu) "4.3. Управление отображением SMS в TIRUp" else "4.3. In-App SMS Display Control")
                flow.bullet(
                    if (isRu) "📲 Важные SMS в TIRUp" else "📲 Important SMS in TIRUp",
                    if (isRu) "Чекбокс «Важные SMS в TIRUp» в нижней части блока «Экстренные SMS» управляет отображением входящих текстовых сообщений от фоловера или мастера непосредственно в оверлее TIRUp. Когда включено (по умолчанию): любое нераспознанное служебным ботом SMS от доверенного номера будет показано через Heads-Up экран с пульсирующей каймой и вибрацией. Когда выключено: входящие сообщения от доверенных контактов обрабатываются только как запросы телеметрии (если содержат команду) и не показываются в оверлее."
                    else "The 'Important SMS in TIRUp' checkbox at the bottom of the Emergency SMS block controls whether incoming text messages from the follower or master are displayed directly in the TIRUp overlay. When enabled (default): any human-readable SMS from a trusted contact triggers the Heads-Up screen with a pulsing border and vibration. When disabled: trusted-contact messages are silently handled only as telemetry queries if they match a command keyword, and no overlay is shown."
                )



                // =========================================================================
                // CHAPTER 5: CLINICAL AGP, PATTERNS, HbA1c & COMPENSATOR
                // =========================================================================
                flow.chapter(
                    5,
                    if (isRu) "ГЛАВА 5. КЛИНИЧЕСКАЯ АНАЛИТИКА, AGP, ПАТТЕРНЫ И HbA1c" else "CHAPTER 5. CLINICAL AGP, PATTERNS, HbA1c & COMPENSATOR",
                    if (isRu) "Стандарты ATTD/ADA, 12 параметров AGP, лабораторный HbA1c и суточный компенсатор TIR" else "ATTD/ADA consensus, 12 AGP metrics, laboratory HbA1c and daily target compensator"
                )
                flow.section(if (isRu) "5.1. Амбулаторный гликемический профиль (AGP) и 12 параметров" else "5.1. Ambulatory Glucose Profile (AGP) & 12 Clinical Metrics")
                flow.paragraph(
                    if (isRu) "В разделе «Отчёты» формируется стандартизированный отчёт AGP по стандартам консенсуса ATTD/ADA за 7, 14, 30 или 90 дней с расчётом ключевых биомаркеров для эндокринолога:"
                    else "The Reports tab generates standardized AGP reports compliant with ATTD/ADA consensus across 7, 14, 30, or 90 days with core biomarkers:"
                )
                flow.bullet(
                    if (isRu) "TIR, TBR, TAR" else "TIR, TBR, TAR",
                    if (isRu) "Время в целевом диапазоне (TIR 3.9–10.0, норма ≥70%), время ниже диапазона (TBR <3.9, норма <4%, из них <3.0 <1%), время выше диапазона (TAR >10.0, норма <25%)."
                    else "Time in Range (TIR 3.9-10.0, target ≥70%), Time Below Range (TBR <3.9, target <4%, severe <3.0 <1%), Time Above Range (TAR >10.0, target <25%)."
                )
                flow.bullet(
                    if (isRu) "Вариабельность (CV, SD)" else "Variability (CV, SD)",
                    if (isRu) "Коэффициент вариации CV (целевой ≤36%) и стандартное отклонение SD отражают стабильность сахаров и защиту от внезапных ночных гипогликемий."
                    else "Coefficient of Variation CV (target ≤36%) and SD quantify glycemic stability and nocturnal resilience."
                )
                flow.bullet(
                    if (isRu) "GRI, GVI, PGS, eA1c / GMI" else "GRI, GVI, PGS, eA1c / GMI",
                    if (isRu) "Индекс риска GRI (0–100), индекс гликемической вариабельности GVI (отношение длины кривой к идеальной; адаптивен к пропускам броадкаста), статус PGS и расчётный GMI."
                    else "Glycemia Risk Index GRI (0-100), Glycemic Variability Index GVI (curve length to ideal line ratio; adaptive to missing samples), PGS, and estimated GMI."
                )
                flow.bullet(
                    if (isRu) "Детектор скрытых клинических паттернов" else "Hidden Patterns Recognition",
                    if (isRu) "Алгоритм выявляет ночные провалы в часы сна, феномен утренней зари и постпрандиальные всплески. Тревожные карточки скрываются через 48 ч, информационные — через 24 ч. Доступен раскрывающийся архив скрытых событий."
                    else "Detects nocturnal dips, dawn phenomenon, and meal spikes. High-priority cards auto-expire after 48h, informational after 24h, with an expandable archive."
                )
                flow.bullet(
                    if (isRu) "Сходимость данных с xDrip+ и 1-минутный CGM" else "Data Convergence with xDrip+ & 1-min CGM",
                    if (isRu) "В отличие от старых систем с 5-минутным шагом, для минутных сенсоров (Libre 2/3, Dexcom G7) TIRUp фильтрует паразитный радиодребезг трансмиттера (повторные эхо-пакеты за 2–5 сек), сохраняя ровно 1 замер в минуту. Это даёт математически чистое взвешивание времени без искажения суточного TIR и среднего сахара."
                    else "Unlike legacy 5-minute CGM pipelines, TIRUp applies a 25s deduplication window for 1-minute sensors (Libre 2/3, Dexcom G7) to filter transmitter echo bursts (2-5s deltas). This guarantees 100% genuine point retention and mathematically pure time-weighted metrics."
                )
                flow.bullet(
                    if (isRu) "Журнал HbA1c и квартальные напоминания (раз в 90 дн.)" else "HbA1c Journal & Quarterly Reminders (every 90d)",
                    if (isRu) "Журнал сопоставляет анализы крови с датчиком (GMI). Каждые 90 дней срабатывает квартальное напоминание о сдаче крови (макс. 2 раза за цикл с шагом 14 дней, кнопка «Пропустить +90д»). Цвета: <6.1% (норма), 6.1–7.0% (цель), 7.0–8.0% (суб), >8.0% (риск)."
                    else "Logs HbA1c vs sensor GMI. Quarterly reminder fires every 90 days (max 2 per cycle with 14d step, 'Skip +90d' button). Colors: <6.1% (norm), 6.1-7.0% (target), 7.0-8.0% (sub), >8.0% (risk)."
                )
                flow.bullet(
                    if (isRu) "Суточный компенсатор и воскресный дайджест" else "Daily Compensator & Sunday Digest",
                    if (isRu) "Компенсатор рассчитывает время для цели (TIR ≥70%). За 1–2 ч до точки невозврата звучит «Последний шанс для TIR». Каждое воскресенье в 20:00 формируется аналитический Sunday Digest со сравнением параметров (±Δ%)."
                    else "Calculates in-range time for daily goal (TIR ≥70%). Emits 'Last Chance for TIR' 1-2h before point of no return. Generates Sunday Digest every Sunday at 20:00 (±Δ%)."
                )
                flow.callout(
                    CalloutType.INFO,
                    if (isRu) "📊 ЭКСПОРТ AGP ОТЧЁТА ДЛЯ ВРАЧА:" else "📊 EXPORTING AGP REPORTS FOR PHYSICIANS:",
                    if (isRu) "На вкладке «Отчёты» выберите период (например, 14 дней) и нажмите «Создать AGP отчёт (PDF)». Файл можно сохранить в память или мгновенно отправить лечащему врачу в Telegram, WhatsApp или по почте."
                    else "On Reports tab select period and tap 'Create AGP Report (PDF)'. Share directly with your endocrinologist via Telegram, WhatsApp or email."
                )

                // =========================================================================
                // CHAPTER 6: QUICK GLANCE HUD, WIDGETS, SUPPLIES & MAINTENANCE
                // =========================================================================
                flow.chapter(
                    6,
                    if (isRu) "ГЛАВА 6. ИНТЕРФЕЙС HUD, ВИДЖЕТЫ, УЧЁТ РАСХОДНИКОВ И АРХИВЫ" else "CHAPTER 6. QUICK GLANCE HUD, WIDGETS, SUPPLIES & MAINTENANCE",
                    if (isRu) "Quick Glance HUD 108sp, виджеты рабочего стола, учёт расходников и Zero-Lag база" else "Quick Glance HUD 108sp, desktop widgets, supplies tracking and Zero-Lag database"
                )
                flow.bullet(
                    if (isRu) "Quick Glance HUD и автоскрытие" else "Quick Glance HUD & Auto-Hiding",
                    if (isRu) "Удержание центральной кнопки 0.33 с открывает центрированный HUD: сахар 108sp, стрелка 78sp, единицы по центру, карточки инсулина/углеводов (34sp) и телеметрия в одну строку (19sp). Нижняя панель автоскрывается через 2.2 с и просыпается по тапу."
                    else "Hold center button 0.33s to summon centered HUD: 108sp glucose, 78sp arrow, centered units, 34sp insulin/carb tiles, and 19sp single-line telemetry. Navigation auto-hides after 2.2s."
                )
                flow.bullet(
                    if (isRu) "Виджеты рабочего стола, экран AoD и Пузырёк" else "Widgets, AoD Screen & Floating Bubble",
                    if (isRu) "5 форматов виджетов рабочего стола (5х1, 4х2/3х2, 2х2, 1х2). Энергоэффективный Always-On Display (AoD) с защитой OLED от выгорания: ночные часы, гигантский сахар, стрелка тренда, индикатор батареи, жест яркости и фонарик с паузой. Плавающий «Пузырёк» виден поверх всех приложений."
                    else "5 homescreen widget sizes (5x1, 4x2/3x2, 2x2, 1x2). Ultra-efficient Always-On Display (AoD) with OLED anti-burn-in jitter: bedside clock, giant glucose, battery status, brightness drag, and smooth flashlight with pause. Floating Bubble pulses over apps."
                )
                flow.bullet(
                    if (isRu) "Сроки службы расходников и напоминания о замене" else "Supplies Tracking & Expiry Reminders",
                    if (isRu) "Раздельный учёт датчика (10–14д), канюли (3д) и ланцета. Напоминания о замене приходят за 2 дня, за 1 день и по окончании срока. При просрочке счётчик уходит в минус: до 24 ч (-Xч), 1–30 дней (-Xд), >30 дней (-Xм)."
                    else "Separate tracking for sensor (10-14d), cannula (3d), and lancet. Expiry alerts arrive 2 days before, 1 day before, and when overdue (<24h: -Xh, 1-30d: -Xd, >30d: -Xm)."
                )
                flow.bullet(
                    if (isRu) "Новогодний дайджест 31 декабря, Zero-Lag и автобэкап" else "Dec 31 Year-End Digest, Zero-Lag & Backups",
                    if (isRu) "31 декабря в 20:00 формируется новогодний дайджест 🥂 с PDF-открыткой, а замеры года запечатываются в tirup_readings_YYYY.csv (Zero-Lag база). Каждые сутки в 23:59:59 создаётся локальный автобэкап, доступен экспорт ZIP."
                    else "On Dec 31 at 20:00, Year-End Digest 🥂 exports a PDF holiday card, sealing year readings into tirup_readings_YYYY.csv (Zero-Lag). Nightly auto-backup at 23:59:59 plus ZIP export."
                )
                flow.callout(
                    CalloutType.INFO,
                    if (isRu) "⚖️ ЮРИДИЧЕСКИЙ МЕДИЦИНСКИЙ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ:" else "⚖️ LEGAL MEDICAL DISCLAIMER NOTICE:",
                    if (isRu) "TIRUp является программным средством для информационного самоконтроля образа жизни при диабете. Приложение не является сертифицированным медицинским прибором. Всегда проверяйте показания глюкометром по капле крови перед принятием решений о дозах инсулина."
                    else "TIRUp is an auxiliary lifestyle self-monitoring tool. It is not an officially certified medical device. Always verify CGM readings with a capillary blood meter prior to therapeutic insulin adjustments."
                )
            }

            // Step 1: Pass 1 (Dry run layout to determine totalPages and accurate chapter start pages)
            val pass1Context = ManualFlowContext(
                isDryRun = true,
                totalPages = 1,
                chapterPages = IntArray(7) { 1 }
            )
            pass1Context.startDocument()
            renderManual(pass1Context)
            pass1Context.finishDocument()

            val calculatedTotalPages = pass1Context.currentPageIndex
            val calculatedChapterPages = pass1Context.chapterPages.clone()

            // Step 2: Pass 2 (Actual render to PdfDocument with accurate totalPages & chapterPages in TOC)
            val pass2Context = ManualFlowContext(
                isDryRun = false,
                totalPages = calculatedTotalPages,
                chapterPages = calculatedChapterPages
            )
            pass2Context.startDocument()
            renderManual(pass2Context)
            pass2Context.finishDocument()

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
