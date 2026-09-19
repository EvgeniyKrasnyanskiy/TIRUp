import sys
import os
from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import (
    QPdfWriter, QPainter, QPageSize, QColor, QFont, 
    QFontMetricsF, QPen, QBrush, QImage
)
from PyQt6.QtCore import QMarginsF, QRectF, Qt

app = QApplication.instance()
if not app:
    app = QApplication(sys.argv)

def run():
    is_ru = True
    total_pages = 3
    app_version = "2.2.0"

    # Fonts
    font_header_title = QFont("Arial", 8, QFont.Weight.Bold)
    font_header_sub = QFont("Arial", 8)
    font_doc_title = QFont("Arial", 11, QFont.Weight.Bold)
    font_chapter_title = QFont("Arial", 9, QFont.Weight.Bold)
    font_chapter_subtitle = QFont("Arial", 7, QFont.Weight.Bold)
    font_section_heading = QFont("Arial", 7, QFont.Weight.Bold)
    font_body = QFont("Arial", 7)
    font_bullet_symbol = QFont("Arial", 7, QFont.Weight.Bold)
    font_bullet_title = QFont("Arial", 7, QFont.Weight.Bold)
    font_toc_title = QFont("Arial", 7, QFont.Weight.Bold)
    font_toc_body = QFont("Arial", 6)
    font_callout_title = QFont("Arial", 7, QFont.Weight.Bold)
    font_callout_body = QFont("Arial", 7)

    # Colors
    c_doc_header = QColor(30, 41, 59)
    c_muted = QColor(100, 116, 139)
    c_rule = QColor(226, 232, 240)
    c_chapter_title = QColor(15, 23, 42)
    c_action_blue = QColor(37, 99, 235)
    c_section_heading = QColor(30, 41, 59)
    c_body = QColor(51, 65, 85)
    c_bullet_title = QColor(15, 23, 42)

    def draw_running_header_and_footer(p: QPainter, page_num: int):
        # Header text: strictly left title and right page number, never overlapping
        p.setPen(c_doc_header)
        p.setFont(font_header_title)
        hdr_title = "TIRUp • Руководство пользователя и клинический справочник" if is_ru else "TIRUp • User Manual & Clinical Reference"
        p.drawText(36, 24, hdr_title)

        page_str = f"Стр. {page_num} из {total_pages}" if is_ru else f"Page {page_num} of {total_pages}"
        p.setPen(c_muted)
        p.setFont(font_header_sub)
        fm_sub = QFontMetricsF(font_header_sub)
        pw = fm_sub.horizontalAdvance(page_str)
        p.drawText(int(559 - pw), 24, page_str)

        # Header rule
        p.setPen(QPen(c_rule, 0.8))
        p.drawLine(36, 30, 559, 30)

        # Footer rule & text
        p.drawLine(36, 814, 559, 814)
        p.setFont(font_header_sub)
        p.setPen(c_muted)
        foot = f"TIRUp v{app_version} • 100% Автономный медицинский спутник" if is_ru else f"TIRUp v{app_version} • 100% Offline Medical Companion"
        p.drawText(36, 826, foot)
        stamp = "github.com/EvgeniyKrasnyanskiy/TIRUp"
        sw = fm_sub.horizontalAdvance(stamp)
        p.drawText(int(559 - sw), 826, stamp)

    def draw_chapter_heading(p: QPainter, start_y: float, title: str, subtitle: str) -> float:
        y = start_y + 3
        p.setPen(c_chapter_title)
        p.setFont(font_chapter_title)
        p.drawText(36, int(y + 8), title)
        y += 11.0

        p.setPen(c_action_blue)
        p.setFont(font_chapter_subtitle)
        p.drawText(36, int(y + 7), subtitle)
        return y + 11.0

    def draw_section_heading(p: QPainter, start_y: float, heading: str) -> float:
        y = start_y + 2
        p.setPen(c_section_heading)
        p.setFont(font_section_heading)
        p.drawText(36, int(y + 7.5), heading)
        return y + 10.5

    def draw_paragraph(p: QPainter, start_y: float, text: str, max_w: float = 523.0, line_h: float = 9.6) -> float:
        p.setPen(c_body)
        p.setFont(font_body)
        fm = QFontMetricsF(font_body)

        words = text.split()
        lines = []
        cur_line = ""
        for w in words:
            test = w if not cur_line else f"{cur_line} {w}"
            if fm.horizontalAdvance(test) <= max_w:
                cur_line = test
            else:
                lines.append(cur_line)
                cur_line = w
        if cur_line:
            lines.append(cur_line)

        y = start_y + 7.2
        for l in lines:
            p.drawText(36, int(y), l)
            y += line_h
        return (y - line_h) + 4.0

    def draw_bullet_point(p: QPainter, start_y: float, title: str, desc: str, max_w: float = 523.0, line_h: float = 9.6) -> float:
        y = start_y + 7.2

        # Bullet icon
        p.setPen(c_action_blue)
        p.setFont(font_bullet_symbol)
        p.drawText(38, int(y), "•")

        # Title
        title_colon = f"{title}: "
        p.setPen(c_bullet_title)
        p.setFont(font_bullet_title)
        p.drawText(47, int(y), title_colon)

        fm_title = QFontMetricsF(font_bullet_title)
        title_w = fm_title.horizontalAdvance(title_colon)

        # Description
        p.setPen(c_body)
        p.setFont(font_body)
        fm_body = QFontMetricsF(font_body)

        words = desc.split()
        first_line_avail = max_w - 11.0 - title_w

        word_idx = 0
        first_line = ""
        if first_line_avail > 45.0:
            while word_idx < len(words):
                w = words[word_idx]
                test = w if not first_line else f"{first_line} {w}"
                if fm_body.horizontalAdvance(test) <= first_line_avail:
                    first_line = test
                    word_idx += 1
                else:
                    break

        if first_line:
            p.drawText(int(47 + title_w), int(y), first_line)

        # Remaining lines indented at 47
        sub_avail = max_w - 11.0
        sub_line = ""
        while word_idx < len(words):
            w = words[word_idx]
            test = w if not sub_line else f"{sub_line} {w}"
            if fm_body.horizontalAdvance(test) <= sub_avail:
                sub_line = test
                word_idx += 1
            else:
                y += line_h
                p.drawText(47, int(y), sub_line)
                sub_line = w
                word_idx += 1

        if sub_line:
            y += line_h
            p.drawText(47, int(y), sub_line)

        return y + 3.0

    def draw_callout(p: QPainter, start_y: float, c_type: str, title: str, text: str, box_w: float = 523.0) -> float:
        y = start_y + 2
        if c_type == "INFO":
            bg, border, accent, text_c = QColor(239, 246, 255), QColor(191, 219, 254), QColor(37, 99, 235), QColor(30, 64, 175)
        elif c_type == "TIP":
            bg, border, accent, text_c = QColor(236, 253, 245), QColor(167, 243, 208), QColor(16, 185, 129), QColor(6, 95, 70)
        elif c_type == "WARNING":
            bg, border, accent, text_c = QColor(254, 252, 232), QColor(254, 240, 138), QColor(245, 158, 11), QColor(146, 64, 14)
        else: # CRITICAL
            bg, border, accent, text_c = QColor(254, 242, 242), QColor(254, 202, 202), QColor(239, 68, 68), QColor(153, 27, 27)

        p.setFont(font_callout_body)
        fm = QFontMetricsF(font_callout_body)
        text_max_w = box_w - 20.0
        words = text.split()
        lines = []
        cur_line = ""
        for w in words:
            test = w if not cur_line else f"{cur_line} {w}"
            if fm.horizontalAdvance(test) <= text_max_w:
                cur_line = test
            else:
                lines.append(cur_line)
                cur_line = w
        if cur_line:
            lines.append(cur_line)

        line_h = 9.0
        total_h = 13.0 + (len(lines) * line_h) + 3.0

        p.setPen(QPen(border, 0.8))
        p.setBrush(QBrush(bg))
        p.drawRoundedRect(QRectF(36, y, box_w, total_h), 4, 4)

        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QBrush(accent))
        p.drawRoundedRect(QRectF(36, y, 4, total_h), 2, 2)

        p.setPen(text_c)
        p.setFont(font_callout_title)
        p.drawText(46, int(y + 9.5), title)

        p.setFont(font_callout_body)
        line_y = y + 19.0
        for l in lines:
            p.drawText(46, int(line_y), l)
            line_y += line_h

        return y + total_h + 4.2

    # =========================================================================
    # PAGE 1: Введение, Содержание, Быстрый старт + ГЛАВА 1 + ГЛАВА 2
    # =========================================================================
    def render_page_1(painter):
        draw_running_header_and_footer(painter, 1)
        y = 44.0
        painter.setPen(c_chapter_title)
        painter.setFont(font_doc_title)
        painter.drawText(36, int(y + 10), "TIRUp • РУКОВОДСТВО ПОЛЬЗОВАТЕЛЯ" if is_ru else "TIRUp • USER MANUAL")
        y += 13.5

        painter.setPen(c_action_blue)
        painter.setFont(font_chapter_subtitle)
        painter.drawText(36, int(y + 7), "Клинический справочник, автономная архитектура, тревоги и защита безопасности" if is_ru else "Clinical reference, zero-cloud architecture, safety alarms and device integration")
        y += 11.5

        # TOC Box
        painter.setPen(QPen(QColor(203, 213, 225), 0.8))
        painter.setBrush(QBrush(QColor(248, 250, 252)))
        painter.drawRoundedRect(QRectF(36, y, 523, 29), 4, 4)

        painter.setPen(QColor(30, 64, 175))
        painter.setFont(font_toc_title)
        painter.drawText(44, int(y + 9.5), "СОДЕРЖАНИЕ РУКОВОДСТВА:" if is_ru else "TABLE OF CONTENTS:")

        painter.setPen(QColor(71, 85, 105))
        painter.setFont(font_toc_body)
        painter.drawText(44, int(y + 17.5), "Гл. 1: Связь и BLE-мост • Гл. 2: Надежность в фоне и OEM (Стр. 1) | Гл. 3: Тревоги и Спасение • Гл. 4: SOS SMS и Опекун (Стр. 2)" if is_ru else "Ch. 1: Connectivity & BLE • Ch. 2: Background Reliability & OEM (p. 1) | Ch. 3: Safety Alarms & Rescue • Ch. 4: Emergency SOS & Caregiver (p. 2)")
        painter.drawText(44, int(y + 25.5), "Гл. 5: Клиническая аналитика AGP и HbA1c • Гл. 6: HUD 108sp, Виджеты, Расходники и Архивация (Стр. 3)" if is_ru else "Ch. 5: Clinical AGP & HbA1c • Ch. 6: Quick Glance HUD, Widgets, Supplies & Maintenance (p. 3)")
        y += 33.5

        y = draw_callout(
            painter, y, "TIP",
            "🚀 БЫСТРЫЙ СТАРТ ЗА 3 ШАГА (ДЛЯ НОВЫХ ПОЛЬЗОВАТЕЛЕЙ):" if is_ru else "🚀 3-STEP QUICK START GUIDE:",
            "1) В xDrip+ («Настройки ➔ Межпрограммная интеграция») включите «Широковещательные передачи». 2) В настройках Android отключите оптимизацию батареи («Без ограничений»). 3) Задайте целевой диапазон сахара в Настройках TIRUp." if is_ru else "1) In xDrip+ enable 'Broadcast locally'. 2) Disable Android battery optimization for TIRUp ('Unrestricted'). 3) Set your target glucose range in TIRUp Settings."
        )

        # CHAPTER 1
        y = draw_chapter_heading(painter, y, "ГЛАВА 1. АРХИТЕКТУРА СВЯЗИ, ИСТОЧНИКИ ДАННЫХ И BLE-МОСТ" if is_ru else "CHAPTER 1. CONNECTIVITY, DATA SOURCES & FAMILY BLE BRIDGE", "Zero-Cloud принцип, локальные интенты и автономный семейный Bluetooth-мост" if is_ru else "Zero-Cloud principle, local Android intents and offline Family BLE Bridge")
        y = draw_paragraph(
            painter, y,
            "Приложение TIRUp создано по принципу максимальной автономности (Zero-Cloud). В отличие от облачных CGM-систем, TIRUp никогда не требует подключения к интернету, внешних серверов или передачи личных данных. Все вычисления компенсации и тревоги выполняются на 100% локально на смартфоне." if is_ru else "TIRUp operates on the Zero-Cloud principle without internet dependencies, external servers, or data uploads. All calculations and alarms execute 100% locally on your smartphone."
        )
        y = draw_bullet_point(
            painter, y,
            "xDrip+, GDH и Juggluco" if is_ru else "xDrip+, GDH & Juggluco",
            "В настройках источника перейдите в «Межпрограммная интеграция» и включите «Широковещательные передачи» и «Поддержку широковещательной службы» (для мгновенной передачи доз IoB/CoB). TIRUp принимает замеры через локальные Intents с задержкой <0.1 с." if is_ru else "Enable 'Broadcast locally' and 'Broadcast service support' for IoB/CoB in your source. TIRUp captures streaming readings via low-latency Android Intents (<0.1s)."
        )
        y = draw_bullet_point(
            painter, y,
            "Семейный BLE-мост (Вещатель — Приёмник)" if is_ru else "Family BLE Bridge",
            "Передача сахара ребёнка родителю по Bluetooth LE без Wi-Fi и SIM. Телефон ребёнка настраивается как «Вещатель» (импульс 15 с на замер, микропотребление <0.8% батареи/сутки), телефон родителя — как «Приёмник». Пакет шифруется 4-значным Family PIN." if is_ru else "Stream patient glucose to parent over BLE without Wi-Fi or SIM. Broadcaster pulses 15s bursts (<0.8% battery/day), Observer receives. Secured with 4-digit PIN."
        )
        y = draw_bullet_point(
            painter, y,
            "Режим Long Range (LE Coded PHY)" if is_ru else "Long Range Mode (LE Coded PHY)",
            "На чипсетах Bluetooth 5.0+ режим Coded PHY (S=8) повышает потенциал радиолинии на 8–10 dBm, расширяя дальность в 2–4 раза (до 30–50 м сквозь стены). При отсутствии поддержки аппаратно откатывается на Legacy 1M. Сканер приёмника слушает оба диапазона автоматически." if is_ru else "On Bluetooth 5.0+, Coded PHY (S=8) boosts link budget by 8-10 dBm, extending range 2-4x (up to 30-50m through walls). Observer operates in dual-mode automatically."
        )
        y = draw_callout(
            painter, y, "INFO",
            "📡 ДИАГНОСТИКА СИГНАЛА И БАТАРЕИ ПЕРЕДАТЧИКА:" if is_ru else "📡 TRANSMITTER SIGNAL & BATTERY:",
            "В шторке уведомлений и в HUD приёмника отображаются уровень радиосигнала (RSSI dBm) и процент заряда батареи подопечного. Для быстрой проверки используйте «Тест дальности (5с)»." if is_ru else "Notification and HUD widget display real-time transmitter RSSI (dBm) and battery %. Use 'Range Test (5s)' in Observer settings for immediate verification."
        )

        # CHAPTER 2
        y = draw_chapter_heading(painter, y, "ГЛАВА 2. НАДЕЖНОСТЬ В ФОНЕ И НАСТРОЙКА OEM-ПРОШИВОК" if is_ru else "CHAPTER 2. BACKGROUND RELIABILITY & OEM-SPECIFIC GUIDES", "Преодоление Doze Mode, App Standby и пошаговые чек-листы для вендоров" if is_ru else "Overcoming Doze Mode, App Standby and step-by-step checklists for major OEMs")
        y = draw_section_heading(painter, y, "2.1. Чек-листы фоновой работы для популярных смартфонов" if is_ru else "2.1. Background Reliability Checklists for Major OEMs")
        y = draw_bullet_point(
            painter, y,
            "Samsung (OneUI)" if is_ru else "Samsung (OneUI)",
            "1) Настройки ➔ Приложения ➔ TIRUp ➔ Батарея ➔ «Не ограничено». 2) Настройки ➔ Батарея ➔ «Ограничения в фоновом режиме» ➔ «Никогда не спящие приложения» ➔ добавьте TIRUp и источник. 3) Закрепите замочком в меню недавних." if is_ru else "1) Settings ➔ Apps ➔ TIRUp ➔ Battery ➔ 'Unrestricted'. 2) Battery ➔ Background limits ➔ 'Never sleeping apps' ➔ Add TIRUp. 3) Lock app card in Recents."
        )
        y = draw_bullet_point(
            painter, y,
            "Xiaomi, Redmi, POCO (MIUI / HyperOS)" if is_ru else "Xiaomi (MIUI / HyperOS)",
            "1) Включите «Автозапуск» и «Разрешить запуск другими приложениями». 2) «Контроль активности» ➔ «Нет ограничений». 3) «Другие разрешения» ➔ «Экран блокировки» и «Всплывающие окна». 4) Закрепите замочком в Недавних." if is_ru else "1) Enable 'Autostart'. 2) Battery saver ➔ 'No restrictions'. 3) Other permissions ➔ Allow 'Lock screen' & 'Pop-up windows'. 4) Lock app in Recents."
        )
        y = draw_bullet_point(
            painter, y,
            "BBK: Realme, Oppo, OnePlus (ColorOS)" if is_ru else "BBK: Realme, Oppo, OnePlus",
            "1) Управление приложениями ➔ TIRUp ➔ «Расход батареи» ➔ разрешите фоновую активность и автозапуск. 2) «Оптимизация режима ожидания» ➔ отключите «Ультра-режим ожидания». 3) Заблокируйте в Недавних." if is_ru else "1) App management ➔ TIRUp ➔ Battery ➔ Allow background & auto-launch. 2) Disable sleep standby optimization. 3) Lock app in Recents."
        )
        y = draw_bullet_point(
            painter, y,
            "Huawei, Honor (EMUI / MagicOS)" if is_ru else "Huawei, Honor (EMUI / MagicOS)",
            "Настройки ➔ Приложения ➔ Запуск приложений ➔ для TIRUp отключите автоуправление и включите: «Автозапуск», «Косвенный запуск», «Работа в фоне». Закрепите замочком в Недавних." if is_ru else "Apps ➔ App launch ➔ Disable automatic for TIRUp, enable 'Auto-launch', 'Secondary launch', 'Run in background'. Lock in Recents."
        )
        y = draw_bullet_point(
            painter, y,
            "Google Pixel (Stock Android)" if is_ru else "Google Pixel (Stock Android)",
            "Настройки ➔ Батарея ➔ Адаптивные настройки ➔ отключите «Адаптивный расход». В свойствах TIRUp выберите «Использование батареи ➔ Без ограничений»." if is_ru else "Battery ➔ Adaptive preferences ➔ Disable 'Adaptive Battery'. In TIRUp app info set 'Battery ➔ Unrestricted'."
        )
        y = draw_section_heading(painter, y, "2.2. Полноэкранные интенты и пробуждение дисплея" if is_ru else "2.2. Full-Screen Intents & Display Wakeup")
        y = draw_paragraph(
            painter, y,
            "При критической ночной гипогликемии дисплей заблокирован. Экран спасения использует аппаратный WakeLock (ACQUIRE_CAUSES_WAKEUP) и флаги FLAG_SHOW_WHEN_LOCKED, гарантированно включая дисплей с сиреной. Предоставьте права «Поверх других приложений»." if is_ru else "During severe low, Rescue Screen utilizes hardware WakeLock and FLAG_SHOW_WHEN_LOCKED to reliably awaken the screen. Grant 'Display over other apps' permission."
        )
        y = draw_callout(
            painter, y, "WARNING",
            "⚠️ КОНТРОЛЬНЫЙ ЧЕК-ЛИСТ ПЕРЕД ПЕРВОЙ НОЧЬЮ:" if is_ru else "⚠️ PRE-FLIGHT CHECKLIST BEFORE NIGHT:",
            "Внизу экрана настроек выполните 5 быстрых тапов по строке «TIRUp • Версия ...» для открытия скрытого блока тестирования. Нажмите «Тест экрана спасения» и заблокируйте телефон. Через 5 с дисплей должен сам проснуться с сиреной." if is_ru else "Tap 'TIRUp • Version ...' at the bottom of Settings 5 times to reveal tests. Run 'Test Patient Rescue Screen' and lock phone. Within 5s display must wake up with siren."
        )
        return y

    # =========================================================================
    # PAGE 2: ГЛАВА 3 + ГЛАВА 4
    # =========================================================================
    def render_page_2(painter):
        draw_running_header_and_footer(painter, 2)
        y = 44.0
        y = draw_chapter_heading(painter, y, "ГЛАВА 3. МНОГОУРОВНЕВАЯ СИСТЕМА ТРЕВОГ И ЭКРАН СПАСЕНИЯ" if is_ru else "CHAPTER 3. 4-TIER ALARMS, COMA GUARD & RESCUE SCREEN", "Клиническая градация Tier 1–4, предиктивный тренд на 25 минут, обход DND и Coma Guard" if is_ru else "Tier 1-4 escalation, 25-min predictive trend, DND bypass and Coma Guard protocol")
        y = draw_paragraph(
            painter, y,
            "Одной из главных проблем пользователей CGM является «усталость от тревог» (Alarm Fatigue), когда частые ложные сигналы приводят к отключению звука. В TIRUp реализована адаптивная 4-уровневая система безопасности, которая отсекает шум и включает сирены только тогда, когда существует реальная клиническая угроза здоровью:" if is_ru else "To resolve CGM alarm fatigue, TIRUp incorporates an adaptive 4-tier safety model that filters transient sensor artifacts and escalates alerts only for verified clinical hazards:"
        )
        y = draw_bullet_point(
            painter, y,
            "Уровень 1: Предиктивный тренд на 25 минут" if is_ru else "Tier 1: 25-Min Predictive Trend",
            "Регрессионный анализ скользящего окна точек с экспоненциальным затуханием проецирует траекторию на 25 мин вперёд (фиолетовые точки на суточном графике). Мягкий сигнал звучит за 15 мин до расчётной гипогликемии, позволяя принять углеводы заранее." if is_ru else "Linear regression with exponential damping projects glucose 25 mins ahead (purple dots on daily chart). Gentle chime warns 15 mins before calculated low, allowing timely carb intake."
        )
        y = draw_bullet_point(
            painter, y,
            "Уровень 2: Подтверждённое отклонение (3–5 точек)" if is_ru else "Tier 2: Confirmed Departure (3-5 Points)",
            "Классический тройной сигнал при выходе за пределы нормы. Адаптивный порог: 3 точки подряд для 5-минутных датчиков или 5 точек для 1-минутных датчиков. При гипергликемии сигнал глушится, если сахар падает и есть активный инсулин (IoB)." if is_ru else "Triple tone when outside target. Adaptively requires 3 points (5-min CGM) or 5 points (1-min CGM). Auto-mutes on high if glucose is dropping with active IoB onboard."
        )
        y = draw_bullet_point(
            painter, y,
            "Уровень 3: Критическая сирена и стробоскоп вспышки" if is_ru else "Tier 3: Critical Siren & LED Strobe",
            "Срабатывает при опасных порогах (<3.0 или >13.9 ммоль/л) и затяжной гипо/гипергликемии. Обходит режим «Не беспокоить» (DND bypass), играет через USAGE_ALARM на громкости ≥80% и включает стробоскоп вспышки камеры." if is_ru else "Fires upon critical values (<3.0 or >13.9 mmol/L) or prolonged breaches. Bypasses DND, sounds via ALARM stream at ≥80% volume, and pulses camera LED flash."
        )
        y = draw_bullet_point(
            painter, y,
            "Уровень 4: Потеря сигнала (20–25 мин) и ночной профиль" if is_ru else "Tier 4: Signal Loss & Night Profile",
            "При отсутствии точек >20 мин подаётся сигнал будильника. В Настройках задаются «Часы ночного сна» (по умолчанию 23:00–07:00), в этот период действуют отдельные ночные пороги тревог и строгий контроль связи." if is_ru else "Alarm sounds if readings stop for >20 mins. Configurable Night Sleep Window (default 23:00-07:00) applies dedicated nocturnal thresholds and strict signal checks."
        )
        y = draw_section_heading(painter, y, "3.2. Экран спасения и защита от комы (Coma Guard <2.8 ммоль/л)" if is_ru else "3.2. Patient Rescue Screen & Coma Guard (<2.8 mmol/L)")
        y = draw_paragraph(
            painter, y,
            "При глубокой гипогликемии запускается полноэкранный интерфейс спасения с гигантским сахаром, стрелкой падения, таймером до отправки SOS SMS и кнопкой купирования. При сахаре ниже 2.8 ммоль/л включается протокол Coma Guard: пациенту запрещается снуз более 5 минут, сирена повторяется каждые 5 мин до подтверждения купирования." if is_ru else "Severe low launches Patient Rescue Screen with giant sugar, drop arrow, and SOS timer. Below 2.8 mmol/L Coma Guard caps snooze to 5 mins, repeating siren until safe recovery is confirmed."
        )
        y = draw_callout(
            painter, y, "CRITICAL",
            "🚨 КЛИНИЧЕСКИЙ ПРОТОКОЛ КУПИРОВАНИЯ (ПРАВИЛО 15):" if is_ru else "🚨 CLINICAL HYPO PROTOCOL (RULE OF 15):",
            "При гипогликемии примите 15 г быстрых углеводов (сок, декстроза). Нажмите кнопку «Углеводы приняты» на экране спасения — это заглушит сирену и отменит отправку экстренного SOS SMS родственникам." if is_ru else "Take 15g fast-acting carbs (juice, dextrose). Press 'Carbs Taken' on Rescue Screen to silence siren and cancel emergency SOS SMS dispatch."
        )

        # CHAPTER 4
        y = draw_chapter_heading(painter, y, "ГЛАВА 4. ЭКСТРЕННЫЕ SMS, РЕЖИМ ОПЕКУНА И ТЕЛЕМЕТРИЯ" if is_ru else "CHAPTER 4. EMERGENCY SOS SMS, CAREGIVER MODE & TELEMETRY", "Автономные оповещения с GPS, двусторонний запрос сахара и сирена на телефоне родителя" if is_ru else "Offline GPS distress SMS, two-way glucose queries and caregiver alarm sirens")
        y = draw_paragraph(
            painter, y,
            "Если пациент находится в состоянии тяжёлой гипогликемии и не отключает сирену в течение заданного времени (по умолчанию 3 мин), TIRUp расценивает это как возможную потерю сознания. Приложение запрашивает координаты GPS и автоматически отправляет экстренное SMS доверенным лицам:" if is_ru else "If hypo alarm is unacknowledged for the delay (default 3 mins), TIRUp retrieves device GPS and transmits emergency distress SMS to caregiver phones:"
        )
        y = draw_bullet_point(
            painter, y,
            "Содержание тревожного SOS SMS" if is_ru else "Distress SOS SMS Format",
            "«SOS! У [Имя] критический сахар: 2.5 ммоль/л ⇊. Нет реакции на сирену 3 мин. Геолокация: maps.google.com/?q=55.75,37.61». Близкие получают координаты и могут оперативно вызвать скорую помощь." if is_ru else "'SOS! [Name] critical glucose: 2.5 mmol/L ⇊. Unresponsive 3 min. Location: maps.google.com/?q=55.75,37.61'. Caregivers can instantly dispatch emergency medical services."
        )
        y = draw_bullet_point(
            painter, y,
            "Двусторонняя оффлайн-телеметрия" if is_ru else "Two-Way Offline Telemetry",
            "Родственник может отправить обычное SMS «сахар», «?», «tir» со своего доверенного номера на телефон подопечного без интернета. TIRUp мгновенно ответит в фоне: «[Имя]: 6.4 ммоль/л → (TIR 89%, 15м назад, батарея 85%)»." if is_ru else "Caregivers can text 'sugar', '?', or 'tir' from whitelisted phones without internet. TIRUp replies immediately: '[Name]: 6.4 mmol/L → (TIR 89%, 15m ago, battery 85%)'."
        )
        y = draw_bullet_point(
            painter, y,
            "Режим опекуна (Caregiver SOS Alert)" if is_ru else "Caregiver SOS Alert Mode",
            "Если на телефоне родителя установлен TIRUp, входящее SMS с префиксом «SOS! У...» активирует экран спасения опекуна (CaregiverSosActivity). Телефон громко проигрывает сирену в обход беззвучного режима, выводя имя, сахар, время и кнопку навигатора к месту происшествия." if is_ru else "If TIRUp is installed on parent's phone, incoming 'SOS! ...' SMS launches CaregiverSosActivity. Sounds a loud siren bypassing silent mode, showing patient name, glucose, and GPS navigation button."
        )
        y = draw_section_heading(painter, y, "4.2. Настройка разрешений SMS и кэширование Android" if is_ru else "4.2. SMS Permissions & Cache Recovery")
        y = draw_paragraph(
            painter, y,
            "Для работы требуются системные разрешения SEND_SMS и RECEIVE_SMS. При повторной выдаче прав в Android может возникать кэширование отказа: в TIRUp встроен алгоритм автоматической перепроверки системных дескрипторов." if is_ru else "Requires SEND_SMS and RECEIVE_SMS permissions. TIRUp incorporates automatic descriptor re-verification to bypass Android permission caching issues."
        )
        y = draw_callout(
            painter, y, "TIP",
            "📱 БЕЗОПАСНОСТЬ И ПРОВЕРКА ЭКСТРЕННОГО SMS:" if is_ru else "📱 EMERGENCY SMS VERIFICATION:",
            "В настройках в блоке «Экстренное SMS» нажмите «Отправить тестовое SMS». Убедитесь, что номера доверенных контактов внесены в международном формате (+7...)." if is_ru else "In Settings ➔ 'Emergency SMS' tap 'Send Test SMS'. Ensure caregiver phone numbers include country code (+1...)."
        )
        return y

    # =========================================================================
    # PAGE 3: ГЛАВА 5 + ГЛАВА 6
    # =========================================================================
    def render_page_3(painter):
        draw_running_header_and_footer(painter, 3)
        y = 44.0
        y = draw_chapter_heading(painter, y, "ГЛАВА 5. КЛИНИЧЕСКАЯ АНАЛИТИКА, AGP, ПАТТЕРНЫ И HbA1c" if is_ru else "CHAPTER 5. CLINICAL AGP, PATTERNS, HbA1c & COMPENSATOR", "Стандарты ATTD/ADA, 12 параметров AGP, лабораторный HbA1c и суточный компенсатор TIR" if is_ru else "ATTD/ADA consensus, 12 AGP metrics, laboratory HbA1c and daily target compensator")
        y = draw_section_heading(painter, y, "5.1. Амбулаторный гликемический профиль (AGP) и 12 параметров" if is_ru else "5.1. Ambulatory Glucose Profile (AGP) & 12 Clinical Metrics")
        y = draw_paragraph(
            painter, y,
            "В разделе «Отчёты» формируется стандартизированный отчёт AGP по стандартам консенсуса ATTD/ADA за 7, 14, 30 или 90 дней с расчётом ключевых биомаркеров для эндокринолога:" if is_ru else "The Reports tab generates standardized AGP reports compliant with ATTD/ADA consensus across 7, 14, 30, or 90 days with core biomarkers:"
        )
        y = draw_bullet_point(
            painter, y,
            "TIR, TBR, TAR" if is_ru else "TIR, TBR, TAR",
            "Время в целевом диапазоне (TIR 3.9–10.0, норма ≥70%), время ниже диапазона (TBR <3.9, норма <4%, из них <3.0 <1%), время выше диапазона (TAR >10.0, норма <25%)." if is_ru else "Time in Range (TIR 3.9-10.0, target ≥70%), Time Below Range (TBR <3.9, target <4%, severe <3.0 <1%), Time Above Range (TAR >10.0, target <25%)."
        )
        y = draw_bullet_point(
            painter, y,
            "Вариабельность (CV, SD)" if is_ru else "Variability (CV, SD)",
            "Коэффициент вариации CV (целевой ≤36%) и стандартное отклонение SD отражают стабильность сахаров и защиту от внезапных ночных гипогликемий." if is_ru else "Coefficient of Variation CV (target ≤36%) and SD quantify glycemic stability and nocturnal resilience."
        )
        y = draw_bullet_point(
            painter, y,
            "GRI, GVI, PGS, eA1c / GMI" if is_ru else "GRI, GVI, PGS, eA1c / GMI",
            "Индекс гликемического риска GRI (0–100), индекс гликемической вариабельности GVI, показатель суточного профиля PGS и расчётный гликированный гемоглобин GMI." if is_ru else "Glycemia Risk Index GRI (0-100), Glycemic Variability Index GVI, Personal Glycemic State PGS, and estimated GMI."
        )
        y = draw_bullet_point(
            painter, y,
            "Детектор скрытых клинических паттернов" if is_ru else "Hidden Patterns Recognition",
            "Алгоритм выявляет ночные провалы в часы сна, феномен утренней зари и постпрандиальные всплески. Тревожные карточки скрываются через 48 ч, информационные — через 24 ч. Доступен раскрывающийся архив скрытых событий." if is_ru else "Detects nocturnal dips, dawn phenomenon, and meal spikes. High-priority cards auto-expire after 48h, informational after 24h, with an expandable archive."
        )
        y = draw_bullet_point(
            painter, y,
            "Журнал лабораторного HbA1c и 4 уровня нормы" if is_ru else "Laboratory HbA1c Journal",
            "Журнал сопоставляет анализы крови с датчиком (GMI). Классификация: <6.1% (норма), 6.1–7.0% (цель), 7.0–8.0% (субкомпенсация), >8.0% (риск). Кнопка подсвечивает свежесть сдачи (<30д — светло-зелёный, 30–360д — зелёный, >360д — жёлтый)." if is_ru else "Logs laboratory HbA1c vs sensor GMI: <6.1% (norm), 6.1-7.0% (target), 7.0-8.0% (sub), >8.0% (risk). Button highlights test freshness."
        )
        y = draw_bullet_point(
            painter, y,
            "Суточный компенсатор и точка невозврата" if is_ru else "Daily Compensator & Point of No Return",
            "Компенсатор рассчитывает точное время удержания диапазона для достижения суточной цели (TIR ≥70% / TING ≥50%). За 1–2 часа до точки невозврата выводится предупреждение «Последний шанс для TIR»." if is_ru else "Calculates strict in-range minutes needed for daily goals (TIR ≥70% / TING ≥50%). Emits 'Last Chance for TIR' 1-2h before mathematical point of no return."
        )
        y = draw_callout(
            painter, y, "INFO",
            "📊 ЭКСПОРТ AGP ОТЧЁТА ДЛЯ ВРАЧА:" if is_ru else "📊 EXPORTING AGP REPORTS FOR PHYSICIANS:",
            "На вкладке «Отчёты» выберите период (например, 14 дней) и нажмите «Создать AGP отчёт (PDF)». Файл можно сохранить в память или мгновенно отправить лечащему врачу в Telegram, WhatsApp или по почте." if is_ru else "On Reports tab select period and tap 'Create AGP Report (PDF)'. Share directly with your endocrinologist via Telegram, WhatsApp or email."
        )

        # CHAPTER 6
        y = draw_chapter_heading(painter, y, "ГЛАВА 6. ИНТЕРФЕЙС HUD, ВИДЖЕТЫ, УЧЁТ РАСХОДНИКОВ И АРХИВЫ" if is_ru else "CHAPTER 6. QUICK GLANCE HUD, WIDGETS, SUPPLIES & MAINTENANCE", "Quick Glance HUD 108sp, виджеты рабочего стола, учёт расходников и Zero-Lag база" if is_ru else "Quick Glance HUD 108sp, desktop widgets, supplies tracking and Zero-Lag database")
        y = draw_bullet_point(
            painter, y,
            "Quick Glance HUD и автоскрытие" if is_ru else "Quick Glance HUD & Auto-Hiding",
            "Удержание центральной кнопки 0.33 с открывает центрированный HUD: сахар 108sp, стрелка 78sp, единицы по центру, карточки инсулина/углеводов (34sp) и телеметрия в одну строку (19sp). Нижняя панель автоскрывается через 2.2 с и просыпается по тапу." if is_ru else "Hold center button 0.33s to summon centered HUD: 108sp glucose, 78sp arrow, centered units, 34sp insulin/carb tiles, and 19sp single-line telemetry. Navigation auto-hides after 2.2s."
        )
        y = draw_bullet_point(
            painter, y,
            "Виджеты рабочего стола, экран блокировки/AOD и Пузырёк" if is_ru else "Widgets, Lockscreen & Floating Bubble",
            "5 форматов виджетов рабочего стола (полоса 5х1, Canvas-график 4х2/3х2, квадрат 2х2, стек 1х2). На экране блокировки доступна регулировка прозрачности (0–100%). Плавающий «Пузырёк» виден поверх всех приложений и пульсирует волнами при гипо." if is_ru else "5 homescreen widget sizes (strip 5x1, Canvas graph 4x2/3x2, square 2x2, vertical 1x2). Lockscreen opacity slider 0-100%. Floating Bubble pulses ripple waves during hypo."
        )
        y = draw_bullet_point(
            painter, y,
            "Сроки службы устройств и отрицательные счётчики" if is_ru else "Supplies Lifespan & Negative Counters",
            "Раздельный учёт датчика CGM (10–14д), канюли помпы (3д) и ланцета. При просрочке счётчик переходит в отрицательные значения: до 24 ч (-Xч), 1–30 дней (-Xд), >30 дней (-Xм)." if is_ru else "Separate tracking for CGM sensor (10-14d), cannula (3d), and lancet. Expired supplies display negative counters: <24h (-Xh), 1-30d (-Xd), >30d (-Xm)."
        )
        y = draw_bullet_point(
            painter, y,
            "Архитектура Zero-Lag и ежедневные резервные копии" if is_ru else "Zero-Lag Engine & Automated Backups",
            "В конце каждого года замеры архивируются в tirup_readings_YYYY.csv. База данных всегда работает мгновенно без лагов за любые годы. Каждую полночь в 23:59:59 создаётся локальный автобэкап, доступен ручной ZIP-экспорт." if is_ru else "Past years are archived into tirup_readings_YYYY.csv. Active database stays ultra-fast. Daily midnight auto-backup at 23:59:59 plus manual ZIP export."
        )
        y = draw_callout(
            painter, y, "INFO",
            "⚖️ ЮРИДИЧЕСКИЙ МЕДИЦИНСКИЙ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ:" if is_ru else "⚖️ LEGAL MEDICAL DISCLAIMER NOTICE:",
            "TIRUp является программным средством для информационного самоконтроля образа жизни при диабете. Приложение не является сертифицированным медицинским прибором. Всегда проверяйте показания глюкометром по капле крови перед принятием решений о дозах инсулина." if is_ru else "TIRUp is an auxiliary lifestyle self-monitoring tool. It is not an officially certified medical device. Always verify CGM readings with a capillary blood meter prior to therapeutic insulin adjustments."
        )
        return y

    # 1. Generate PDF
    pdf_path = "TIRUp_User_Manual_Test.pdf"
    writer = QPdfWriter(pdf_path)
    writer.setPageSize(QPageSize(QPageSize.PageSizeId.A4))
    writer.setResolution(72)
    writer.setPageMargins(QMarginsF(0, 0, 0, 0))

    painter = QPainter(writer)
    y1 = render_page_1(painter)
    writer.newPage()
    y2 = render_page_2(painter)
    writer.newPage()
    y3 = render_page_3(painter)
    painter.end()

    print(f"PDF Generated: {pdf_path}")
    print(f"  Page 1 final y: {y1:.1f} / 814 (Remaining: {814-y1:.1f} pt)")
    print(f"  Page 2 final y: {y2:.1f} / 814 (Remaining: {814-y2:.1f} pt)")
    print(f"  Page 3 final y: {y3:.1f} / 814 (Remaining: {814-y3:.1f} pt)")

    # 2. Render High-Resolution PNGs for inspection
    scale = 2.0  # 144 DPI
    img_w = int(595 * scale)
    img_h = int(842 * scale)

    for p_idx, render_func in [(1, render_page_1), (2, render_page_2), (3, render_page_3)]:
        img = QImage(img_w, img_h, QImage.Format.Format_RGB32)
        img.fill(QColor(255, 255, 255))
        p_img = QPainter(img)
        p_img.scale(scale, scale)
        render_func(p_img)
        p_img.end()
        img_file = f"manual_page_{p_idx}.png"
        img.save(img_file)
        print(f"Rendered preview image: {img_file}")

if __name__ == "__main__":
    run()
