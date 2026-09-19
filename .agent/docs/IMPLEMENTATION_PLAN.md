# План реализации: Модуляризация экрана настроек (SettingsScreen.kt)

## 1. Цель задачи
Файл `SettingsScreen.kt` достиг размера ~6 900 строк кода. Экран содержит множество крупных диалогов и вспомогательных компонентов, затрудняющих навигацию и замедляющих инкрементальную компиляцию.
Цель: безопасно разгрузить `SettingsScreen.kt`, вынеся изолированные диалоги в новый пакет `com.tirup.app.presentation.settings.dialogs` с пошаговой проверкой компиляции.

---

## 2. Поэтапный план рефакторинга

### Шаг 1: Вынос автономных диалогов HbA1c и YearEndDigest (~1 000 строк)
- [x] Создать директорию `app/src/main/java/com/tirup/app/presentation/settings/dialogs/`.
- [x] Создать `Hba1cHistoryDialog.kt`:
  - Перенести `@Composable fun Hba1cHistoryDialog` (~570 строк).
  - Настроить импорты моделей `LabHba1cRecord` и Jetpack Compose.
- [x] Создать `YearEndDigestDialog.kt`:
  - Перенести `@Composable fun YearEndDigestDialog` (~410 строк).
  - Настроить импорты моделей `YearEndStats`, `ExportPdf`.
- [x] Удалить перенесённые функции из `SettingsScreen.kt` и добавить импорты из `com.tirup.app.presentation.settings.dialogs.*`.
- [x] **Контроль качества:** запустить `.\gradlew compileDebugKotlin` и убедиться в успешной компиляции.

### Шаг 2: Вынос профиля пациента и селектора года (~700 строк)
- [x] Создать `PatientProfileDialogs.kt`:
  - Перенести `@Composable fun PatientProfileSummaryCard` (~120 строк).
  - Перенести `@Composable fun PatientProfileEditDialog` (~510 строк).
  - Перенести `@Composable fun DropdownYearSelector` (~45 строк).
  - Настроить импорты `PatientProfile`, `PluralUtils`.
- [x] Удалить перенесённые функции из `SettingsScreen.kt` и импортировать их.
- [x] **Контроль качества:** запустить `.\gradlew compileDebugKotlin` и убедиться в успешной компиляции.

### Шаг 3: Вынос диалогов BLE-моста (~400 строк)
- [x] Создать `BleBridgeDialogs.kt`:
  - Перенести `@Composable fun BleBridgeHelpDialog` (~150 строк).
  - Перенести `@Composable fun BleFamilyPinDialog` (~120 строк).
  - Перенести диалог подтверждения Long Range LE Coded PHY.
  - Настроить импорты `BlePacketCodec` и Compose компонентов.
- [x] Удалить перенесённые функции из `SettingsScreen.kt` и импортировать их.
- [x] **Контроль качества:** запустить `.\gradlew compileDebugKotlin` и убедиться в успешной компиляции.

### Шаг 4: Вынос диалогов порогов тревог и безопасности (~600 строк)
- [x] Создать `AlertThresholdDialogs.kt`:
  - Вынести `MainThresholdDialog` (верхний/нижний порог сахара, затяжная гипо/гипергликемия).
  - Вынести `CriticalThresholdDialog` (критическая гипо/гипергликемия, стробоскоп вспышки, громкость сирены).
  - Вынести `PredictiveHorizonDialog` и `PredictiveInfoDialog` (горизонт 15–25 мин, предиктивный колокольчик).
  - Вынести `CriticalHypoSafetyDialog` (Coma Guard, лимит снуза 5 мин).
- [x] Подключить их вызовы в `SettingsScreen.kt`.
- [x] **Контроль качества:** запустить `.\gradlew compileDebugKotlin`.
- [x] Обновить `ROADMAP.md` (отметить задачу как выполненную) и зафиксировать результат в `ARCHIVE.md`.
- [x] Коммит и пуш в `origin/main`.

