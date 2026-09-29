# План реализации: Реорганизация Настроек, BLE-мост, Аппаратный фонарик, Сворачивание карточек и UI-полировка

## 1. Порядок блоков в «Дополнительных настройках»
- В `SettingsScreen.kt`:
  1. `XdripLanFollowerCard` («Wi-Fi LAN Follower»)
  2. `BleBridgeCard` («BLE-мост» — опущен под Wi-Fi LAN)
  3. `EmergencySmsCard` («Экстренное SMS»)
  4. `AlwaysOnDisplayCard` («Ночной экран (AoD)» — поднят сразу под Экстренное SMS)
  5. `WeeklyDigestCard` («Воскресный дайджест»)
  6. `DeviceRemindersCard` («Напоминание об устройствах»)
  7. `ClinicalStandardsCard` («Клинические стандарты»)
  8. `LockscreenNotificationCard` («Параметры отображения / Экран блокировки»)
  9. `FloatingGlucoseBubbleCard` («Плавающий пузырёк с сахаром»)
  10. `WidgetPreviewCard` («Прозрачность подложки виджетов»)
  11. `AutoBackupCard` («Ежедневный автобэкап»)
  12. `ClearDataCard` («Сброс данных»)
  13. `DeveloperTestingCard` («Тестирование систем»)
  14. `NightscoutSyncCard` («Сервер синхронизации»)

---

## 2. Переработка карточки «BLE-мост»
- В `IntegrationsSection.kt`:
  - Переименовать заголовок: `Локальный BLE-мост` -> `BLE-мост` (EN: `BLE Bridge`).
  - Добавить в шапку карточки стандартный `Switch`:
    - При выключении: `onUpdateBleSettings(ble.copy(isEnabled = false))`.
    - При включении: запрашивать пермишены и включать активную роль.
    - Динамический цвет переключателя:
      - `PrimaryEmerald` (зелёный), если активна роль «Приёмник».
      - `ActionBlue` (синий), если активна роль «Вещатель».
  - Заменить строку выбора ролей:
    - Удалить плашку `✖️ Выкл` (так как включение/выключение перенесено на главный `Switch`).
    - Сделать 2 широкие кнопки с текстовыми названиями: `Вещатель` (синий акцент `ActionBlue`) и `Приёмник` (зелёный акцент `PrimaryEmerald`).

---

## 3. Аппаратная регулировка силы тыльного LED-фонарика
- В `AodScreen.kt`:
  - Получить `maxTorchStrengthLevel` из `CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` (для Android 13+, API 33 `TIRAMISU`).
  - При свайпе пальца вверх/вниз:
    - Если `maxTorchStrengthLevel > 1` и API >= 33: вызывать `cameraManager.turnOnTorchWithStrengthLevel(cameraId, level)`, где `level = (rearTorchBrightness * maxTorchStrengthLevel).roundToInt().coerceIn(1, maxTorchStrengthLevel)`.
    - Иначе: использовать стандартный `setTorchMode(cameraId, true)`.
  - При закрытии/отключении: `setTorchMode(cameraId, false)`.

---

## 4. Унификация цветов стрелок разворота и переключателей AoD
- **Стрелки разворота карточек**:
  - Во всех карточках (`SettingsScreen.kt`, `IntegrationsSection.kt`, `SystemDisplaySection.kt`, `DataBackupSection.kt`, `DeveloperTestingSection.kt`) привести иконки `ExpandLess` / `ExpandMore` к единому фирменному синему стилю `tint = ActionBlue`.
- **Элементы AoD**:
  - В `AlwaysOnDisplayCard`: перекрасить переключатели, кнопки выбора режима (`Pulse Wake` / `Always On`) и статус из зелёного в фирменный синий цвет `ActionBlue`.

---

## 5. Сворачивание/разворачивание для 9 блоков настроек
- Добавить `var isExpanded by rememberSaveable { mutableStateOf(false) }`, кликабельную строку заголовка со стрелкой `ExpandLess`/`ExpandMore` цвета `ActionBlue` и контент внутри `AnimatedVisibility(isExpanded)` для карточек:
  1. `DisplayPreferencesCard` («Параметры отображения»)
  2. `WeeklyDigestCard` («Воскресный дайджест»)
  3. `DeviceRemindersCard` («Напоминание об устройствах»)
  4. `ClinicalStandardsCard` («Клинические стандарты»)
  5. `FloatingGlucoseBubbleCard` («Плавающий пузырёк с сахаром»)
  6. `WidgetPreviewCard` («Прозрачность подложки виджетов»)
  7. `AutoBackupCard` («Ежедневный автобэкап»)
  8. `DeveloperTestingCard` («Тестирование систем»)
  9. `NightscoutSyncCard` («Сервер синхронизации»)

---

## 6. Жёлтая рамка у пиктограммы «месяц» (🌙) в шапке
- В `FocusScreen.kt` (`HeroGlucoseCard`):
  - Задать кнопке `🌙` границу `BorderStroke(0.8.dp, ColorHigh.copy(alpha = 0.5f))` и фон `ColorHigh.copy(alpha = 0.12f)` симметрично кнопке уведомлений 🔔 на противоположном конце строки.

---

## 7. Жёлтый цвет плашки углеводов на главном экране
- В `FocusScreen.kt` (`HeroGlucoseCard`):
  - Заменить цвет плашки `🍞 XX г` с `PrimaryEmerald` на `ColorHigh` (жёлтый/янтарный).

---

## 8. Плашки инсулина и углеводов в Quick Glance HUD (окно по удержанию центральной кнопки)
- В `MainActivity.kt` (`showQuickHud`):
  - Плашка «Инсулин» (`💉`): фон `ActionBlue.copy(alpha = 0.15f)`, рамка `ActionBlue.copy(alpha = 0.5f)`, текст и цифры в синем акценте.
  - Плашка «Углеводы» (`🥖`/`🍞`): фон `ColorHigh.copy(alpha = 0.15f)`, рамка `ColorHigh.copy(alpha = 0.5f)`, текст и цифры в жёлтом акценте.

---

## 9. Селекторы времени по умолчанию
- В `ReportsViewModel.kt`:
  - `_livePeriod` по умолчанию: `TrendPeriod.PERIOD_1D` («1д»).
- В `TrendsViewModel.kt`:
  - `_selectedPeriod` по умолчанию: `TrendPeriod.PERIOD_7D` («7д»).

---

## 10. Актуализация документации и стратегия версионирования
- **README и Руководство**:
  - Обновить `README.md`, `UserManualPdfGenerator.kt` и `GuidebookPdfGenerator.kt`: описать ночной дрейф кружка, ночной запуск AoD при блокировке, экранный фонарик 100с с тапом сброса, тыльный фонарик со ступенчатой силой вспышки, переключатель BLE-моста.
- **Версионирование**:
  - Установить `versionName = "1.0.0"`, при этом `versionCode = 21` (для гарантии корректного обновления без конфликта `INSTALL_FAILED_VERSION_DOWNGRADE`).
