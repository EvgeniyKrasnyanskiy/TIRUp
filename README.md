# TIRUp (Time-In-Range Up) 🩸📈

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-blueviolet.svg)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20MVVM-orange.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)

**TIRUp** — современное автономное Android-приложение для непрерывного мониторинга гликемии (CGM), углублённого клинического анализа профиля глюкозы и автоматической генерации стандартизированных медицинских AGP-отчётов (Ambulatory Glucose Profile).

[English](#english-summary) • [Возможности](#-ключевые-возможности) • [Настройка xDrip+](#-интеграция-с-xdrip) • [Метрики](#-клинические-метрики-и-алгоритмы) • [Сборка](#-сборка-проекта)

</div>

---

## 🌟 Ключевые возможности

### 1. Автономный приём данных в реальном времени (100% Offline)
- Прямой перехват локальных широковещательных интентов из **xDrip+**, **GlucoDataHandler**, **Juggluco** (`com.eveningoutpost.dexdrip.BgEstimate`).
- Приём активного инсулина (**IoB**), активных углеводов (**CoB**) и истории болюсов/еды через **Broadcast Service API**.
- Работает полностью автономно на смартфоне — **без интернета, облачных серверов и риска утечки медицинских данных**.

### 2. Две системы единиц измерения
- Мгновенное переключение между **ммоль/л (mmol/L)** и **мг/дл (mg/dL)** на всех экранах приложения, графиках, виджетах и в генерируемых PDF-документах.

### 3. Суточная математика компенсатора цели (TIR $\ge 70\%$ / TING $\ge 50\%$)
- Расчёт в часах и минутах точного времени, необходимого провести в целевом диапазоне до конца суток (00:00:00 – 23:59:59).
- Проактивное предупреждение **«Последний шанс для TIR»**, срабатывающее за 1–2 часа до математической точки невозврата.
- Лаконичные статусы: *«В норме ещё 2ч 15м»* или *«Цель 100% достигнута!»*.

### 4. Метки инсулина и приёмов пищи на графике (Treatments Overlay)
- Автоматическое наложение маркеров болюсов 💉 (*сине-голубой пин `X.X U`*) и приёмов пищи 🍽️ (*янтарно-оранжевый пин `XX g`*) прямо на 24-часовой холст Canvas.
- Проекционные пунктирные линии на кривую сахара и синхронизация с жестами зума (pinch) и панорамирования (pan).
- Интерактивный инспектор при тапе на маркер (точное время, доза инсулина, количество углеводов, расчёт ХЕ и заметка).

### 5. Четырёхуровневая система безопасности (Smart 4-Tier Alarms)
- **Уровень 1 (Предиктивный прогноз за 15 мин)**: математическая регрессия скорости изменения сахара с расчётом точного астрономического времени события (*«в 16:42»*) и мягким перезвоном без стресса.
- **Уровень 2 (Подтверждённый выход за границы)**: фиксация 5 замеров подряд вне персональных порогов; отчётливый тройной медицинский тон с паузой 1.5 сек.
- **Уровень 3 (Критическая сирена «кричащая»)**: серия громкой сирены ~12 сек на аудиопотоке будильника (`USAGE_ALARM`), обход DND, авто-буст громкости $\ge 80\%$, стробоскоп вспышки камеры. Мгновенное глушение сирены любой физической кнопкой (громкость, питание) или тапом по пузырьку.
- **Уровень 4 (Потеря сигнала сенсора >20 мин)**: мягкий сигнал потери связи с прогрессивным расписанием день/ночь (в окне сна: серия будильников для надёжного пробуждения; днём: щадящие интервалы).
- **Клинический адаптивный Снуз (Smart Snooze)**:
  - *При гипогликемии*: пауза 15 минут с защитой от комы (мгновенный повтор сирены при сахаре < 2.8 ммоль/л).
  - *При гипергликемии*: пауза 30–45 минут на разворачивание инсулина с повторной тревогой, если сахар не снижается.

### 6. Защита близких: Экстренные SOS SMS и Офлайн-запросы телеметрии
- **Экстренное SOS SMS при потере сознания**:
  - Если критическая сирена гипогликемии (< 3.0 ммоль/л) звучит без подтверждения пользователем более 5 минут (подозрение на кому/нейрогликопению), приложение автоматически отправляет SMS доверенному контакту.
  - Сверхкомпактный формат (ровно 1 сегмент кириллицы $\le 67$ символов): `SOS! [Имя] - критич. гипо: 2.6 ммоль (↓)! Сирена 5м без реакции`.
  - Опциональное прикрепление точных GPS-координат Google Maps отдельным тумблером в настройках.
- **Офлайн SMS-запрос сахара без интернета**:
  - При сетевых шатдаунах доверенный контакт может отправить SMS с ключевым словом (`сахар`, `?`, `sugar`, `bg`, `tir`) с авторизованного номера.
  - TIRUp автоматически отвечает компактным SMS ($\le 70$ символов): `TIRUp: [Имя] 6.4 ммоль (→) в 14:35 (+0.2). TIR: 82%. IoB: 1.2U`.
  - Защита белым списком (сверка последних 10 цифр) и 60-секундный антиспам-кулдаун.

### 7. Плавающий оверлей «Пузырёк» (Floating Bubble)
- Компактный круглый индикатор (60x60dp) поверх всех приложений.
- **Умная видимость**: отображается **только когда сахар вне нормы** (< 3.9 или > 10.0 ммоль/л) и автоматически скрывается, когда гликемия возвращается в норму.
- Пульсирующий эффект «круги на воде» при гипогликемии.
- Тап по пузырьку мгновенно глушит сирену, скрывает пузырёк на 5 минут (снуз) и открывает приложение.

### 8. Виджеты рабочего стола Glance (5 форматов) и DiaNight (🌙)
- **5 форматов виджетов под любую сетку лончера**:
  - **5х1**: компактная горизонтальная полоса (сахар, тренд, дельта, TIR, IoB/CoB, стрик).
  - **4х2 / 3х2**: информативный дашборд с 4-часовым HD Canvas sparkline-графиком с сегментной раскраской точек.
  - **2х2**: эргономичный квадратный виджет-фокус.
  - **1х2**: вертикальный информационный стек.
- **Индикаторы**: бейджи активного инсулина (💉) и углеводов (🍞), стрик дней в цели (🔥 X д.).
- **Кнопка DiaNight (🌙)**: запуск ночных прикроватных часов прямо с виджета (крупный шрифт, дельта, график, защита от выгорания AMOLED).
- **Настройка прозрачности (0%..100%)**: плавный ползунок прозрачности подложки виджетов с живым окном предпросмотра на фоне обоев.

### 9. Воскресный аналитический дайджест (Sunday Digest)
- Каждое воскресенье в 20:00 формирует интерактивный аналитический отчёт за прошедшую неделю.
- Сравнение параметров текущей и предыдущей недели (TIR, TING, TBR, TAR, CV, SD, средний сахар, количество гипогликемий) с наглядными стрелками и процентами динамики ($\pm\Delta\%$).
- Автоматическая генерация клинических выводов и персональных рекомендаций.
- Уведомление в шторку с переходом к отчёту и сохранение в архив.

### 10. 24-часовой амбулаторный профиль глюкозы (AGP) и паттерны
- Почасовое построение суточного профиля с перцентильными полосами: медиана (50%), интерквартильный диапазон (25–75%) и разброс (10–90%).
- Переключатель карточки `[📊 График | 🔢 Параметры]` между перцентильной кривой и сеткой 12 клинических параметров (Mean, eA1c, SD, %CV, TIR, TING, TBR, TAR, GRI, GVI, PGS, Min/Max).
- Детектор скрытых клинических паттернов: распознавание ночных падений в индивидуальные часы сна, феномена утренней зари и постпрандиальных всплесков с возможностью индивидуального скрытия (✕).

### 11. Медицинские PDF-отчёты AGP и Руководство пользователя
- **Клинический AGP-отчёт для эндокринолога**: стандарт ATTD/ADA в один клик за 7, 14, 30 или 90 дней с данными пациента и автоматическим заключением.
- **Справочник параметров CGM (лист А4)**: подробный разбор 12 параметров, формул и клинических норм.
- **Двухстраничное руководство пользователя (PDF)**: печатная иллюстрированная памятка по связке с источниками, виджетам, тревогам, снузу и экстренным SMS.

### 12. Ежедневный автобэкап без системных разрешений
- Точный будильник `AlarmManager.RTC_WAKEUP` сохраняет базу данных и настройки ежедневно строго в **23:59:59** в изолированную песочницу приложения.
- Автоматическое обнаружение резервной копии и восстановление при переустановке приложения.

---

## 📱 Интеграция с источниками данных

TIRUp поддерживает все популярные автономные источники данных диа-экосистемы Android:

### xDrip+:
1. Откройте **xDrip+** ➔ **Настройки** ➔ **«Межпрограммная интеграция»** (Inter-app settings).
2. Включите **«Широковещательный показ данных»** (Broadcast locally) для передачи замеров сахара.
3. Включите **«API службы трансляции»** (Broadcast Service API) для передачи IoB, CoB и истории инъекций/еды (Treatments).

### GlucoDataHandler / Juggluco:
- Включите локальную трансляцию совместимых интентов `com.eveningoutpost.dexdrip.BgEstimate`.

### Настройки Android (Батарея):
- В системных настройках Android для TIRUp и источника данных отключите оптимизацию батареи (**«Без ограничений»** / Unrestricted).
- Закрепите TIRUp замочком в меню недавних приложений для надёжной фоновой работы.

---

## 📊 Клинические метрики и алгоритмы

Все расчёты в TIRUp соответствуют международным рекомендациям **ATTD (Advanced Technologies & Treatments for Diabetes)** и **ADA (American Diabetes Association)**:

| Метрика | Описание | Норма / Цель (ммоль/л) | Норма / Цель (mg/dL) |
| :--- | :--- | :--- | :--- |
| **TIR** | Время в целевом диапазоне | $\ge 70\%$ (3.9 – 10.0 ммоль/л) | $\ge 70\%$ (70 – 180 mg/dL) |
| **TING** | Время в узком идеальном диапазоне | $\ge 50\%$ (3.9 – 7.8 ммоль/л) | $\ge 50\%$ (70 – 140 mg/dL) |
| **TBR Low** | Гипогликемия 1 уровня | $< 4.0\%$ (< 3.9 ммоль/л) | $< 4.0\%$ (< 70 mg/dL) |
| **TBR Very Low**| Гипогликемия 2 уровня (тяжёлая) | $< 1.0\%$ (< 3.0 ммоль/л) | $< 1.0\%$ (< 54 mg/dL) |
| **TAR High** | Гипергликемия 1 уровня | $< 25.0\%$ (> 10.0 ммоль/л) | $< 25.0\%$ (> 180 mg/dL) |
| **TAR Very High**| Гипергликемия 2 уровня | $< 5.0\%$ (> 13.9 ммоль/л) | $< 5.0\%$ (> 250 mg/dL) |
| **%CV** | Коэффициент вариабельности | $\le 36.0\%$ ($SD / Mean \times 100\%$) | $\le 36.0\%$ |
| **eA1c / GMI** | Расчётный гликированный гемоглобин | $\le 7.0\%$ (формула ADAG) | $\le 7.0\%$ |
| **GRI** | Glycemia Risk Index (индекс риска) | $\le 40.0$ ($3.0 \times VLow + 2.4 \times Low + 0.8 \times High + 1.6 \times VHigh$) | $\le 40.0$ |

---

## 🛠 Технологический стек

- **Язык**: Kotlin 2.0.0
- **UI Toolkit**: Jetpack Compose, Material 3 (Bento Grid layout)
- **Виджеты рабочего стола**: Jetpack Glance + RemoteViews
- **Архитектура**: Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
- **Фоновые задачи**: WorkManager, AlarmManager (RTC_WAKEUP), Foreground Service
- **Асинхронность**: Kotlin Coroutines, StateFlow, SharedFlow
- **База данных**: Room Persistence Library (SQLite) с автоматическими миграциями (v1 ➔ v5)
- **Генерация документов**: Android Native Canvas Graphics (векторные PDF высокого разрешения)
- **Звук**: AudioTrack синтезатор чистых медицинских частот без внешних MP3-файлов
- **SMS & Телеком**: SmsManager, Telephony SMS BroadcastReceiver
- **Минимальная версия Android**: Android 8.0 (API level 26) / Target SDK 35

---

## 🏗 Сборка проекта

### Требования:
- JDK 17 (рекомендуется Eclipse Adoptium Temurin 17)
- Android SDK 35 / Build Tools 35.0.0

### Команды для сборки:
```bash
# Клонирование репозитория
git clone git@github.com:EvgeniyKrasnyanskiy/TIRUp.git
cd TIRUp

# Запуск unit-тестов
./gradlew testDebugUnitTest

# Сборка Debug APK
./gradlew assembleDebug

# Установка на подключенное устройство
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚠️ Медицинский отказ от ответственности (Disclaimer)

Приложение **TIRUp** создано исключительно для информационных целей, аналитики и личного самоконтроля. 
- Приложение **не является сертифицированным медицинским изделием** и не ставит клинических диагнозов.
- Информация в приложении не заменяет очной консультации врача-эндокринолога.
- Любые изменения дозировок инсулина, лекарственных средств и схем терапии должны производиться строго под контролем лечащего врача.

---

<a name="english-summary"></a>
## 🌐 English Summary

**TIRUp** is an advanced, privacy-first, 100% offline open-source Android application for Continuous Glucose Monitoring (CGM) analytics, emergency safety alerts, and automated clinical AGP reporting.

### Core Features:
- **Direct Offline Broadcast**: Intercepts readings locally via `com.eveningoutpost.dexdrip.BgEstimate` from xDrip+, GlucoDataHandler, and Juggluco without internet or third-party servers.
- **Treatments Overlay**: Visualizes bolus insulin doses (💉) and meal carbs (🍽️) on the 24-hour Canvas sparkline with interactive inspect tooltips.
- **Smart 4-Tier Alarms**: Tier 1 predictive trend alert with exact departure timestamp (*"at 16:42"*), Tier 2 confirmed tone, Tier 3 loud critical siren (~12s on `USAGE_ALARM` with instant physical button muting), and Tier 4 sleep-aware signal loss alarm (>20 min).
- **Emergency Safety SMS & Offline Queries**:
  - Automatically dispatches an ultra-compact single-segment SMS ($\le 67$ chars) with optional GPS coordinates to a trusted contact when severe hypo (< 3.0 mmol/L) sirens remain unacknowledged for 5 minutes.
  - Whitelisted offline SMS query: trusted contacts can text `sugar`, `?`, `bg`, or `tir` to receive real-time glucose and TIR without internet access during network shutdowns.
- **Floating Glucose Bubble (60x60dp)**: Automatically emerges only when glucose exits the target range (< 3.9 or > 10.0 mmol/L) with hypo water ripple wave effect and 5-min tap snooze.
- **Glance Desktop & Lockscreen Widgets (5 Formats)**: Horizontal 5x1 strip, 4x2/3x2 Canvas chart dashboard, 2x2 focus square, and 1x2 vertical glance with customizable background opacity slider (0%..100%).
- **DiaNight Nightstand Clock (🌙)**: High-contrast nightstand dock mode with AMOLED burn-in shift protection.
- **Sunday Compensation Digest**: Automated weekly review delivered every Sunday at 20:00 with week-over-week dynamic delta comparison ($\pm\Delta\%$) and clinical insights.
- **Clinical AGP Reports**: Generates official Ambulatory Glucose Profile PDF sheets with 12 core clinical parameters (TIR, TING, TBR, TAR, CV, eA1c, GRI, GVI, PGS) matching ATTD/ADA standards.
- **Permissionless Daily Auto-Backup**: Exact RTC AlarmManager backs up settings and database into the app sandbox daily at 23:59:59 without dangerous external storage permissions.
- **Dual Units & Localization**: Seamless one-tap switching between `mmol/L` and `mg/dL`, full Russian and English localization.

---

## 👥 Сообщество и обратная связь

- Telegram-канал проекта: [@diakia](https://t.me/diakia)
- Автор: Евгений Краснянский ([EvgeniyKrasnyanskiy](https://github.com/EvgeniyKrasnyanskiy))
