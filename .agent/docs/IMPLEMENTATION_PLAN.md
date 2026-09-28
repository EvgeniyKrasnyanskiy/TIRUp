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

### Этап 2.1: Выделение `AlertsConfigSection.kt` [Completed]
- Создан файл `app/src/main/java/com/tirup/app/presentation/settings/sections/AlertsConfigSection.kt`.
- Вынесены карточки 4 эшелонов тревог, слайдер громкости, тест звуков разряда батареи, Last Chance TIR.
- Заменено в `SettingsScreen.kt`, проверена компиляция и тесты.
- Коммит: `c27415e`.

### Этап 2.2: Выделение `IntegrationsSection.kt` [Completed]
- Создан файл `app/src/main/java/com/tirup/app/presentation/settings/sections/IntegrationsSection.kt`.
- Вынесены карточки:
  - `BleBridgeCard` (роли, радиомост, аппаратный скан, Long Range, PIN).
  - `XdripLanFollowerCard` (Wi-Fi LAN приёмник, прямой опрос порта 17580, автопоиск).
  - `NightscoutSyncCard` (синхронизация treatments, URL, Secret).
- Удален дубликат `BleCountdownText`, из `SettingsScreen.kt` убрано 1190 строк.
- Проверена компиляция и тесты.
- Коммит: `351a492`.

### Этап 2.3: Выделение `SafetySmsSection.kt` [Completed]
- Создан файл `app/src/main/java/com/tirup/app/presentation/settings/sections/SafetySmsSection.kt`.
- Вынесены:
  - `DeviceRoleCard` (роль устройства: Мастер vs Фоловер, переключение, объяснения).
  - `EmergencySmsCard` (экстренные SMS, SOS-сирена фоловера, телефонные номера, шаблоны сообщений, обратные отсчёты тестов).
- Из `SettingsScreen.kt` убрано 875 строк и очищены локальные переменные телефонов и таймеров.
- Проверена компиляция Kotlin и unit-тесты (BUILD SUCCESSFUL).

### Этап 2.4: Выделение `SystemDisplaySection.kt`
- Создать `SystemDisplaySection.kt`.
- Вынести:
  - `DisplayPreferencesCard` (язык, единицы ммоль/л vs мг/дл, темы, контрастность, предикция на графике).
  - `AlwaysOnDisplayCard` (AOD 2.0, ночной монитор, яркость, фонарик, жесты).
  - `WidgetPreviewCard` (интерактивное превью виджетов и прозрачность фона).
  - `DeviceRemindersCard` (напоминания о сенсоре, замене ланцета, тихий ночной режим).
  - `LockscreenAndBubbleCard` (плавающий пузырёк поверх всех окон и уведомление на экране блокировки).
  - `WeeklyDigestCard` (воскресный дайджест недели).
- Подключить в `SettingsScreen.kt`.
- Проверить компиляцию и тесты.

### Этап 2.5: Выделение `DeveloperTestingSection.kt` и финализация `SettingsScreen.kt`
- Создать `DeveloperTestingSection.kt` (инструменты разработчика, эмуляция стрессовых ситуаций, экстренный тест сирен).
- Очистить `SettingsScreen.kt`, оставив лаконичный декларативный каркас (~500 строк).
- Финальная верификация, компиляция и прогон unit-тестов.
