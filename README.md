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

7. **Импорт ретроспективных данных**:
   - Загрузка архивов баз данных xDrip+ (`.zip`, `.sqlite`, `.csv`) для построения отчётов за 7, 14, 30, 90 дней или за всё время наблюдения.

8. **Локализация и темы**:
   - Полная поддержка русского (`RU`) и английского (`EN`) языков интерфейса и отчётов.
   - Тёмная (Dark Bento), светлая (Light) и системная (Auto) темы оформления.

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
- **Full Localization**: Russian and English language support.
- **Modern UI**: Clean Material 3 Bento Card design with Dark/Light mode support.

---

## 👥 Сообщество и обратная связь

- Telegram-канал проекта: [@diakia](https://t.me/diakia)
- Автор: Евгений Краснянский ([EvgeniyKrasnyanskiy](https://github.com/EvgeniyKrasnyanskiy))
