# План реализации: TIRUp Android Application (v2)

## 1. Архитектурный обзор
TIRUp — полностью оффлайн Android-приложение для умной визуализации данных НМГ (CGM), прогнозирования целевых показателей и генерации медицинских AGP-отчетов.

Приложение строится по принципам Clean Architecture + MVI/MVVM:
- **Domain Layer:** Чистые Kotlin-модели, клинические калькуляторы (TIR, TING, %CV, GMI, AGP Percentiles, Target Compensator), интерфейсы репозиториев и UseCases.
- **Data Layer:** Room SQLite Database с двухтабличной схемой (`glucose_readings`, `daily_summaries`), стриминговый парсер CSV/SQLite для 1M+ записей (чанками по 5000), BroadcastReceiver для `com.eveningoutpost.dexdrip.BgEstimate`.
- **Presentation Layer:** Jetpack Compose с Material 3, Bento Grid стилем (скругления 24.dp), кастомный Canvas/Vico для AGP графиков, runtime переключение языка (RU/EN) и единиц (ммоль/л и мг/дл).
- **Reporting Engine:** Нативный Android `PdfDocument` для формирования A4 AGP отчета.

---

## 2. Поэтапный план разработки

### Этап 1: Инициализация Gradle-проекта и базовой структуры [Completed]
- [x] Создать конфигурацию Gradle (Gradle Wrapper, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`).
- [x] Настроить зависимости: Kotlin, Jetpack Compose, Material 3, Room, Coroutines, WorkManager, JUnit/MockK.
- [x] Создать `AndroidManifest.xml` с объявлением BroadcastReceiver и полным отсутствием `android.permission.INTERNET`.
- [x] Настроить ресурсы локализации (`values/strings.xml`, `values-ru/strings.xml`) и базовую дизайн-систему (тема, цвета, формы со скруглением 24.dp).

### Этап 2: Domain-слой и клинические формулы (с Unit-тестами) [Completed]
- [x] Создать модели предметной области:
  - `GlucoseReading` (timestamp, value in mmol/L, trend).
  - `TargetRanges` (TIR: 3.9–10.0, TING: 3.9–7.8, TBR: <3.0, 3.0–3.8, TAR: 10.1–13.9, >=14.0).
  - `GlucoseStatistics` (Mean, SD, %CV, GMI, TIR%, TING%, TBR%, TAR%).
  - `CompensatorGoal` (целевой TIR/TING, прошлый показатель, необходимое значение для остатка периода).
  - `AGPPercentileBin` (10th, 25th, 50th, 75th, 90th перцентили для 24-часового профиля).
- [x] Реализовать калькуляторы клинической математики:
  - `GlucoseMetricsCalculator` (Mean, SD, %CV = $(SD / Mean) \times 100$, GMI = $3.31 + 0.431 \times \text{Mean}$).
  - `TargetCompensatorCalculator` ($T_{\text{needed}} = \frac{T_{\text{target}} \cdot D_{\text{total}} - T_{\text{past}} \cdot D_{\text{past}}}{D_{\text{rem}}}$).
  - `AGPPercentilesCalculator` (разбиение суток на интервалы, расчет интерполированных перцентилей).
- [x] Написать исчерпывающие Unit-тесты (`MetricsCalculatorTest`, `CompensatorTest`, `AGPPercentilesTest`).

### Этап 3: Data-слой (Room DB, BroadcastReceiver, High-Scale Importer) [Completed]
- [x] Room Entities & DAOs:
  - `GlucoseReadingEntity` (`id`, `timestamp` с индексом, `value_mmol`).
  - `DailySummaryEntity` (`date` с индексом, `mean`, `tir`, `ting`, `tbr_very_low`, `tbr_low`, `tar_high`, `tar_very_high`, `sd`, `cv`, `count`).
  - `AppDatabase` c миграциями/инициализацией.
- [x] Стриминговый импортер (`StreamingGlucoseImporter`):
  - Потоковое чтение CSV/SQLite чанками по 5000 записей через `BufferedReader` на `Dispatchers.IO`.
  - Автоматический пересчет и агрегация `daily_summaries`.
- [x] `DexdripBroadcastReceiver`:
  - Перехват `com.eveningoutpost.dexdrip.BgEstimate`.
  - Парсинг сахара, запись в Room без блокировки UI.
- [x] `GlucoseRepositoryImpl` с Flow для стриминга данных в UI.

### Этап 4: Presentation-слой (Jetpack Compose + Material 3 Bento Grid) [Completed]
- [x] UI-компоненты дизайн-системы:
  - `BentoCard` со скруглением 24.dp, мягкими градиентами/тенями и тактильным откликом (HapticFeedback).
  - Селекторы диапазонов (TIR vs TING, mmol/L vs mg/dL, 7D/14D/30D/90D/Все время).
- [x] Экран 1: **Focus Screen (Dashboard)**:
  - Bento-виджеты: Текущий сахар со стрелкой тренда, TIR/TING прогресс, Streak counter, %CV, GMI, Mean.
  - Карточка Target Compensator с динамическим расчетом.
  - Инсайт ночной стабильности (00:00–06:00).
- [x] Экран 2: **Trends & AGP Screen**:
  - График модального дня (Canvas AGP Curve: 10–90%, 25–75%, медиана).
  - 24-часовая тепловая карта (Heatmap).
- [x] Экран 3: **Medical Reports Screen**:
  - Превью стандартизированного A4 AGP отчета.
  - Экспорт и шеринг через системный `Intent.ACTION_SEND`.
- [x] Экран 4: **Settings Screen**:
  - Выбор языка (RU/EN), единиц (ммоль/л / мг/дл), настройка порогов TIR/TING, импорт файлов через SAF.

### Этап 5: Генератор A4 AGP PDF и финализация [Completed]
- [x] `AgpPdfGenerator` на нативном `PdfDocument` (стандартизированный медицинский вид AGP).
- [x] Полная интеграция и структурирование проекта.
