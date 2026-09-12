# План реализации: Комплексный пакет багфиксов и синхронизации вычислений (8 пунктов)

## 1. Задачи по пунктам багрепорта

### Пункт 1. [Completed] Руководство пользователя (`UserManualPdfGenerator.kt`)
- Добавить в Секцию 6 информацию об отслеживании расходных материалов:
  - Счётчики срока службы сенсора CGM, инфузионного набора/канюли помпы и ланцета прокалывателя.
  - Напоминания об истечении срока, автоопределение смены канюли из заметок xDrip+, цветовая индикация (зелёный >24ч, жёлтый ≤24ч, красный при просрочке).

### Пункт 2. [Completed] Стабилизация ввода ФИО в профиле (`SettingsScreen.kt`)
- Введено локальное состояние `remember(profile) { mutableStateOf(profile) }` в `PatientProfileEditDialog`.
- Убран триггер асинхронной записи в БД и тяжелого автобэкапа на каждый ввод символа.
- Применение изменений профиля происходит локально и синхронно без скачков курсора и удаления слов.

### Пункт 3. [Completed] Исправление тавтологии в рекомендации ланцета (`DeviceStatusModal.kt`)
- В строке 278 заменена фраза:
  `"травмирует пальцы и может стать источником микротравм кожи"` на:
  `"вызывает болезненные ощущения и повреждает кожу подушечек пальцев"`.

### Пункт 4. [Completed] Синхронизация и улучшение отсчёта времени устройств (`DeviceStatus.kt`, `DeviceStatusModal.kt`, `DeviceStatusChips.kt`)
- Устранена проблема «залипания 1д»: добавлена функция `formatDeviceRemainingTime` с дробным отображением суток (например, `1.5д`) или часов в модалке (`1д 12 ч`).
- Синхронизирована логика между чипами и модальным окном.

### Пункт 5. [Completed] Скрытие Семейного PIN под спойлер-замазку в BLE-модале (`FocusScreen.kt`)
- Реализован компонент `SpoilerPinBadge` со скрытием PIN под точками (`•••`) и переключением видимости по тапу с иконкой глаза.

### Пункт 6. [Completed] Очистка карточек параметров от 3-й строки с единицами (`Components.kt`)
- В `BentoMetricCompact` удалена 3-я строка с единицами измерения (`unit`), улучшена читаемость карточек.

### Пункт 7. [Completed] Кликабельный путь сохранения файлов с открытием папки (`ReportsScreen.kt`)
- Заменён системный Toast на `Snackbar` с действием «Открыть» (`openSavedFileFolder`) через `DownloadManager.ACTION_VIEW_DOWNLOADS` и `FileProvider`.

### Пункт 8. [Completed] 100% синхронизация алгоритмов расчета с DiaKiaBot (`StreamingGlucoseImporter.kt`, `GlucoseMetricsCalculator.kt`, `DexdripBroadcastReceiver.kt`, `WeeklyDigestCalculator.kt`)
- Удалена лишняя дедупликация в стриминговом импортере (`seenTimestamps`), восстановив полный набор точек (6561).
- Приведен коэффициент `MGDL_FACTOR` к `18.0182`.
- Синхронизированы полуинтервалы TIR, TING, TBR, TAR, диапазоны GRI и формула xDrip `tir = 100 - tbr - tar`.

## 2. Итерация 2: [Completed] Доработка модальных окон, Snackbar и спойлера PIN (Пункты 5 и 7)

### Пункт 5. [Completed] Спойлер PIN в модальном окне «BLE-мост: Приёмник/Вещатель» (`FocusScreen.kt`, `SettingsScreen.kt`)
- В `FocusScreen.kt` в диалоге `BleStatusDialog` открытый PIN удален из всех текстовых шаблонов описания.
- Добавлена аккуратная плашка `Семейный PIN: [ SpoilerPinBadge ]`, скрывающая PIN под `•••` и открывающая его по нажатию.
- В `SettingsScreen.kt` в плитке настроек отображается реальный установленный PIN (`Код: $familyPin`).

### Пункт 7. [Completed] Snackbar внутри диалогов и «Сохранить руководство» (`ReportsScreen.kt`, `AgpSheetPreviewModal.kt`, `HelpAndDisclaimerDialog.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`)
- В `ReportsScreen.kt` и `AgpSheetPreviewModal.kt`:
  - Встроен `SnackbarHost` внутрь `ParametersGuidebookModal` и `AgpSheetPreviewModal`, Snackbar с кнопкой «Открыть» отображается сразу при сохранении без необходимости закрывать модальное окно.
- В `HelpAndDisclaimerDialog.kt`:
  - Кнопка переименована в «Сохранить руководство» (EN: «Save User Manual»).
  - Убран лишний эмодзи `📄`, используется единственная векторная иконка `Download`.
  - Встроен `SnackbarHost` для отображения статуса сохранения прямо в диалоге.
- В `SettingsViewModel.kt` и `SettingsScreen.kt`:
  - Добавлен метод `saveUserManualToDownloads()` с сохранением PDF руководства в системную папку «Загрузки».
  - Подключен `Snackbar` с действием «Открыть» через `openSavedFileFolder`.

