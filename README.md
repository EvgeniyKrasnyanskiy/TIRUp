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

1. **Автономный приём данных в реальном времени (Live Broadcast)**:
   - Прямой перехват локальных широковещательных интентов из **xDrip+** (`com.eveningoutpost.dexdrip.BgEstimate`).
   - Работает на 100% локально на устройстве — **без интернета, внешних серверов и риска утечки персональных данных**.

2. **Две системы единиц измерения**:
   - Мгновенное переключение между **ммоль/л (mmol/L)** и **мг/дл (mg/dL)** на всех экранах приложения, графиках и в генерируемых PDF-документах.

3. **Компенсатор цели (Goal Compensator)**:
   - Яркий интерактивный индикатор достижения целевого процента TIR / TING с динамическим прогнозом и настраиваемым временным окном.

4. **Анализ стабильности ночного профиля**:
   - Автоматический подсчёт TIR и стандартного отклонения (SD) в окно сна (по умолчанию 00:00–06:00).
   - Выявление ночных скрытых гипогликемий и повышенной вариабельности.

5. **24-часовой амбулаторный профиль глюкозы (AGP)**:
   - Почасовое построение суточного профиля с перцентильными полосами: медиана (50%), интерквартильный диапазон (25–75%) и внешние границы разброса (10–90%).

6. **Медицинские PDF-отчёты AGP**:
   - Формирование стандартизированного амбулаторного отчёта AGP для эндокринолога в один клик.
   - Поддержка данных пациента (ФИО, возраст, стаж диабета, вид инсулинотерапии) и автоматического клинического заключения.
   - Предпросмотр перед печатью, сохранение в файл и отправка через системный диалог Android.

7. **4-уровневая система тревог (Smart 4-Tier Alarms)**:
   - **Уровень 1 (Предиктивный)**: математический регрессионный прогноз выхода за пределы диапазона с расчётом точного астрономического времени события (например, *«в 16:42»*) и мягким перезвоном.
   - **Уровень 2 (Основной)**: подтверждённый выход 5 точек за границы диапазона, выразительный тройной медицинский сигнал с паузой 1.5 сек.
   - **Уровень 3 (Критический «кричащий»)**: серия громкой сирены ~12 сек на аудиопотоке будильника (`USAGE_ALARM`) со стробоскопом вспышки при экстремальных значениях (<3.0 / >13.9) или затяжном гипо/гипер. Мгновенное глушение сирены любой аппаратной кнопкой телефона (громкость, питание) без сброса медицинского снуза.
   - **Уровень 4 (Потеря сигнала сенсора)**: мягкий нисходящий сигнал при отсутствии свежих замеров из xDrip+ более 20 минут с прогрессивным геометрическим интервалом повторов (20 ➔ 40 ➔ 80 ➔ 160 мин), не раздражающим пользователя.
   - **Адаптивный клинический Снуз (Smart Adaptive Snooze)**:
     - *При гипо*: 15 минут с защитой от комы (мгновенный повтор сирены, если сахар падает ниже 2.8 или продолжает обваливаться).
     - *При гипер*: 30 минут начальной паузы для разворачивания инсулина; продление еще на 15 минут, если сахар начал снижаться; реэскалация сирены через 30–45 минут, если сахар не падает (подозрение на загиб канюли/нехватку болюса).
   - Кнопка глушения прямо в шторке «✓ Принято», а также автоглушение при взятии в руки/разблокировке экрана.
   - Программный синтезатор медицинских звуков (чистый синус `AudioTrack`, одинаковое качественное звучание на любом смартфоне).

8. **Аналитика и тренды (Ambulatory Glucose Profile & Patterns)**:
   - **Детектор клинических паттернов**: автоматическое распознавание скрытых ночных падений, феномена «утренней зари», постпрандиальных всплесков и высокой вариабельности с отправкой уведомлений в шторку Android и возможностью индивидуального скрытия каждого тренда (✕).
   - **Единый блок AGP с переключателем `[📊 График | 🔢 Параметры]`**: интерактивное переключение между 24-часовой перцентильной кривой и сеткой 12 ключевых клинических параметров (Mean, eA1c, SD, %CV, TIR, TING, TBR, TAR, GRI, GVI, PGS, Min/Max) со справочными модальными подсказками.

9. **Автоматический ежедневный бэкап без системных разрешений**:
   - Ежедневное резервное копирование базы данных строго в **23:59:59** через точный будильник `AlarmManager.RTC_WAKEUP`.
   - Сохранение в защищённую изолированную директорию приложения `Android/data/com.tirup.app/files/Backups` без запроса опасных системных разрешений хранилища.

10. **Импорт ретроспективных данных и справка по экспорту**:
   - Встроенная иллюстрированная инструкция по экспорту данных из xDrip+ (кнопка `?` в блоке отчётов).
   - Загрузка архивов баз данных xDrip+ (`.zip`, `.sqlite`, `.csv`) для построения отчётов за 7, 14, 30, 90 дней или за всё время наблюдения.

11. **Эргономика интерфейса, локализация и темы**:
   - Ультратонкая нижняя панель навигации (44dp) с плавным авто-скрытием при прокрутке списков.
   - Полная поддержка русского (`RU`) и английского (`EN`) языков интерфейса и отчётов.
   - Тёмная (Dark Bento), светлая (Light) и системная (Auto) темы оформления.

12. **Справочник параметров CGM и печать Руководства пользователя**:
    - **Справочник параметров CGM (PDF на лист А4)**: подробный разбор 12 клинических параметров (Mean BG, eA1c, Min/Max, TIR, TING, TBR, TAR, %CV, SD, GVI, GRI, PGS, окно сна, «утренняя заря») с нормами ATTD/ADA и дисклеймером.
    - **Руководство пользователя TIRUp (PDF)**: печатная памятка прямо из настроек по связке с xDrip+, 4 уровням тревог, снузу, аппаратным кнопкам глушения и автобэкапу.

---

## 📱 Интеграция с xDrip+

Для автоматического приёма сахара в TIRUp:
1. Откройте приложение **xDrip+** на смартфоне.
2. Перейдите в: **Настройки (Settings)** ➔ **Межпрограммная интеграция (Inter-app settings)**.
3. Включите переключатель **«Широковещательный показ данных» (Broadcast locally)**.
4. Откройте **TIRUp** — показания сахара с направлением тренда начнут поступать автоматически при каждом замере сенсора.

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
- **Архитектура**: Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
- **Асинхронность**: Kotlin Coroutines, StateFlow, SharedFlow
- **База данных**: Room Persistence Library (SQLite)
- **Генерация PDF**: Android Native Canvas Graphics (векторная отрисовка графиков и отчётов высокой чёткости)
- **Приём данных**: Android BroadcastReceiver (`com.eveningoutpost.dexdrip.BgEstimate`)
- **Минимальная версия Android**: Android 8.0 (API level 26)

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

**TIRUp** is an advanced open-source Android application for Continuous Glucose Monitoring (CGM) analytics, clinical glycemic insights, and automated AGP report generation.

- **Direct xDrip+ Integration**: Receives local broadcasts via `com.eveningoutpost.dexdrip.BgEstimate` without internet or third-party servers.
- **Dual Units**: Seamless switching between `mmol/L` and `mg/dL`.
- **Clinical Metrics**: Real-time TIR, TING, TBR, TAR, %CV, eA1c, GRI (Glycemia Risk Index), GVI, and PGS according to international ATTD consensus.
- **AGP PDF Reports**: One-click generation of official Ambulatory Glucose Profile sheets with patient profiles, therapy info, and 24-hour percentile curves.
- **Smart 4-Tier Alarms**: Predictive alerts with astronomical timestamps, confirmed alerts (triple beep with 1.5s pause), screaming critical sirens (~12s on `USAGE_ALARM` with instant physical button muting), and signal loss alarm (>20 min with geometric backoff).
- **Clinical Pattern Detection & AGP Card Toggle**: Automated detection of dawn phenomenon, night drops, postprandial spikes, and dual-mode AGP card with 24h percentile curves & 12 core clinical parameters.
- **CGM Clinical Guidebook & Printable User Manual**: One-page full A4 printable reference sheets for ATTD/ADA parameters and step-by-step app manual.
- **Permissionless Auto-Backup**: Daily automated database backup at 23:59:59 via exact RTC AlarmManager directly into app sandbox without asking dangerous storage permissions.
- **Full Localization & Compact UI**: Russian and English language support, ultra-thin auto-hiding navigation bar (44dp).
- **Modern UI**: Clean Material 3 Bento Card design with Dark/Light mode support.

---

## 👥 Сообщество и обратная связь

- Telegram-канал проекта: [@diakia](https://t.me/diakia)
- Автор: Евгений Краснянский ([EvgeniyKrasnyanskiy](https://github.com/EvgeniyKrasnyanskiy))
