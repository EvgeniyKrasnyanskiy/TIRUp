# План реализации: Архитектурный рефакторинг и декомпозиция SettingsScreen.kt (v2.5+)

## Описание задачи
Файл `SettingsScreen.kt` достиг монолитного размера в 6 203 строки (368 КБ). 
Цель рефакторинга — разбить экран настроек на слабосвязанные модульные секции внутри пакета `com.tirup.app.presentation.settings.sections`:
1. **`AlertsConfigSection.kt`**: Настройки порогов тревог (критическая гипо, основная гипо/гипер, предиктивная тревога, потеря сигнала, разряд батареи с чипами тестирования звуков, DND, тихий режим, вибрация).
2. **`IntegrationsSection.kt`**: Внешние интеграции (Wi-Fi LAN Follower, BLE радиомост Broadcaster/Observer, Nightscout Cloud API, диалоги конфигурации).
3. **`ProfileTargetsSection.kt`**: Профиль пациента (тип СД, терапия, вес, рост), целевые диапазоны сахара (TIR/TING, ADA/ATTD), коэффициенты инсулинотерапии (IC/CF), расчет дозировок.
4. **`SystemDisplaySection.kt`**: Системный UI и отображение (Always-on Display 2.0, системные виджеты, плавающий пузырь, резервное копирование и восстановление Room DB, экспорт/импорт настроек, сброс к заводским значениям).
5. **Облегчённый `SettingsScreen.kt`**: Компактный координирующий экран (~600–700 строк), управляющий `Scaffold`, верхним тулбаром, скроллом, диалогами верхнего уровня и вызовом секций.

---

## Архитектура модулей

```
com.tirup.app.presentation.settings/
├── SettingsScreen.kt              <-- Тулбар, верхние стейты диалогов, сборка секций
├── SettingsViewModel.kt           <-- Единое состояние UiState, сохранение через SettingsRepository
├── dialogs/                       <-- Вынесенные модальные диалоги ввода
│   ├── BleBridgeHelpDialog.kt
│   ├── BleFamilyPinDialog.kt
│   ├── CriticalThresholdDialog.kt
│   ├── MainThresholdDialog.kt
│   ├── PatientProfileDialogs.kt
│   ├── NightscoutSettingsDialog.kt
│   └── XdripLanSettingsDialog.kt
└── sections/                      <-- Новые специализированные секции (Jetpack Compose)
    ├── AlertsConfigSection.kt     <-- Пороги, эшелоны, звуки батареи, DND
    ├── IntegrationsSection.kt     <-- Wi-Fi LAN Follower, BLE Bridge, Nightscout
    ├── ProfileTargetsSection.kt   <-- Профиль пациента, TIR/TING, калькулятор IC/CF
    └── SystemDisplaySection.kt    <-- AoD 2.0, виджеты, оверлей, бэкапы Room DB
```

---

## Этапы выполнения (Task Splitting)

### Этап 2.1: Выделение `AlertsConfigSection.kt`
- Создать пакет `app/src/main/java/com/tirup/app/presentation/settings/sections/`.
- Вынести карточки оповещений:
  - Карточка порогов и эшелонов тревог (`AlertTierConfigRow`, пауза тревог, выбор уровней).
  - Карточка звуков критического разряда телефона (<15%, <10%, <5%) с кнопками тестирования.
  - Настройки ночного режима и «Не беспокоить» (DND / bypass).
  - Вспомогательные компоненты селекторов часов (`DropdownHourSelector`).
- Подключить `AlertsConfigSection` в `SettingsScreen.kt`.
- Проверить компиляцию Kotlin и запустить unit-тесты.

### Этап 2.2: Выделение `IntegrationsSection.kt`
- Создать `IntegrationsSection.kt`.
- Вынести:
  - Карточка Wi-Fi LAN Follower (статус, IP мастера, батарея, автопоиск).
  - Карточка BLE радиомоста (роли Broadcaster/Observer, аппаратный скан, дальность, семейный PIN, статус пакетов).
  - Карточка Nightscout Cloud API (URL, Secret, статус синхронизации treatments).
- Подключить `IntegrationsSection` в `SettingsScreen.kt`.
- Проверить компиляцию и тесты.

### Этап 2.3: Выделение `ProfileTargetsSection.kt`
- Создать `ProfileTargetsSection.kt`.
- Вынести:
  - Карточка сводки профиля пациента (`PatientProfileSummaryCard`).
  - Целевые диапазоны TIR/TING, строгие нормы ADA, переключение единиц (ммоль/л vs мг/дл).
  - Коэффициенты чувствительности к инсулину (ISF/CF) и углеводный коэффициент (ICR/УК).
  - Интерактивный калькулятор рекомендаций углеводов при гипогликемии.
- Подключить `ProfileTargetsSection` в `SettingsScreen.kt`.
- Проверить компиляцию и тесты.

### Этап 2.4: Выделение `SystemDisplaySection.kt` и финализация `SettingsScreen.kt`
- Создать `SystemDisplaySection.kt`.
- Вынести:
  - Always-on Display 2.0 (яркость, фонарик, жесты, отображение IoB).
  - Виджеты рабочего стола и плавающий пузырь (Floating Bubble / Overlay).
  - Управление данными: экспорт/импорт Room DB, JSON бэкапы, сброс данных.
  - Выбор языка (`LanguageChip`) и переключение цветовых тем.
- Очистить `SettingsScreen.kt`, оставить чистый структурированный контейнер.
- Финальная верификация, сборка Release APK и прогон тестов.
