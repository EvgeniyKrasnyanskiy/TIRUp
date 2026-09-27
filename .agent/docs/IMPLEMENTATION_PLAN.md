# План реализации: Фонарик AoD, плашка яркости, перенос AoD в настройках, сброс таймеров по заметкам и BLE-уведомления

## Описание задач
1. **Фонарик в режиме AoD**:
   - Устранение выключения фонарика при перевороте (повороте) экрана, в том числе в момент разгорания.
   - Перенос бегунка яркости в нижнюю часть экрана для удобного управления пальцем одной руки.
   - Уменьшение времени разгорания фонарика с 15 до 5 секунд.
2. **Плашка регулировки яркости в AoD**:
   - Устранение «залипания» HUD плашки яркости после окончания регулировки (гарантированное скрытие через 1.5 секунды после окончания или отмены жеста).
3. **Настройки AoD**:
   - Перенос карточки выключателя и параметров AoD в блок дополнительных настроек, строго под настройку «Плавающий пузырёк с сахаром».
   - Проверка значения по умолчанию: выключен (`isEnabled = false`).
4. **Сброс счётчиков оборудования по заметкам («канюля», «ланцет», «сенсор»)**:
   - Устранение критической ошибки `PatternSyntaxException`: неподдерживаемый в Android регулярных выражениях флаг `(?U)` в `parseDaysFromNote` приводил к падению метода и блокировке обновления счётчиков.
   - Поддержка составных заметок (например, «канюля → ланцет»), чтобы сбрасывались оба соответствующих таймера.
   - Мгновенная обработка заметок при приходе Broadcast `TREATMENT`, без ожидания опроса веб-сервера.
5. **Баннер и уведомление BLE-моста**:
   - Отключение назойливого всплывающего баннера приёма пакетов BLE по умолчанию (`showPacketBanner = false`).
   - Устранение фолбэк-уведомления «Мост BLE активен • Ожидание данных» из шторки.

---

## Декомпозиция на этапы разработки

### Этап 1: Исправление Regex и автопродления счётчиков по заметкам [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt`
  - `app/src/test/java/com/tirup/app/data/receiver/DexdripNoteParserTest.kt`
- **Изменения**:
  - Удаление флага `(?U)` из регулярных выражений `parseDaysFromNote`. Замена на совместимые с Android выражения `[а-яёa-z]`.
  - Вынесение логики автопродления счётчиков в метод `processDeviceRenewals(context, treatments)`.
  - Вызов `processDeviceRenewals` при получении broadcast-интента `TREATMENT` / `NEW_TREATMENT` напрямую в `onReceive`.
  - Поддержка независимого срабатывания для составных заметок, содержащих несколько устройств (например, `канюля → ланцет`).
  - Добавление юнит-тестов для составных заметок и проверки парсинга дней на Android regex.

### Этап 2: Фонарик AoD (ориентация, тайминг 5с, слайдер снизу, защита от залипания плашки) [Completed]
- **Файлы**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/tirup/app/presentation/aod/AodScreen.kt`
  - `app/src/main/java/com/tirup/app/presentation/aod/AodActivity.kt`
- **Изменения**:
  - В `AndroidManifest.xml` для `AodActivity` добавить `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode"`.
  - В `AodScreen.kt` перевести `isFlashlightActive`, `isFlashlightPaused`, `flashlightProgress` на `rememberSaveable`.
  - Уменьшить шаг разгорания до 5 секунд: `stepIncrement = 50f / 5_000f`.
  - Перенести колонку фонарика со слайдером и кнопкой закрытия в `Alignment.BottomCenter` (`padding(bottom = bottomPadding + 16.dp)`).
  - В жестах свайпа яркости добавить отслеживание `onDragEnd` и `onDragCancel` с надёжным запуском таймера скрытия HUD через 1500 мс.

### Этап 3: Перенос выключателя AoD в Дополнительные настройки и отключение BLE-баннера [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt`
  - `app/src/main/java/com/tirup/app/domain/model/BleBridgeSettings.kt`
  - `app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt`
  - `app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt`
- **Изменения**:
  - В `SettingsScreen.kt` переместить карточку «Always-On Display (AOD / Ночной монитор)» из верхнего блока в секцию под «Плавающий пузырёк с сахаром».
  - В `BleBridgeSettings.kt`, `SettingsRepositoryImpl.kt` и `AutoBackupManager.kt` изменить значение по умолчанию `showPacketBanner` на `false`.
  - В `GlucoseAlertManager.kt` исключить вывод фолбэк-текста «Мост BLE активен • Ожидание данных».

---

### Этап 4: Отображение IoB в режиме Always-on Display (AoD) [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/aod/AodScreen.kt`
- **Изменения**:
  - Под строкой сахара, если `reading.iob > 0.01`, добавлено компактное отображение активного инсулина: `💧 X.XX U` с мягким голубым акцентом (`#38BDF8`), отделённое точкой `•` от дельты и времени замера.

### Этап 5: Оповещение о критическом разряде батареи телефона (<15%, <10%, <5%) [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/model/AlertSettings.kt`
  - `app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt`
  - `app/src/main/java/com/tirup/app/data/backup/AutoBackupManager.kt`
  - `app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt`
  - `app/src/main/java/com/tirup/app/data/receiver/AlertActionReceiver.kt`
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt`
- **Изменения**:
  - В модель `AlertSettings` добавлено поле `isLowBatteryAlertEnabled = true`, поддержано сохранение в `SharedPreferences` и резервных копиях `AutoBackupManager`.
  - В `GlucoseAlertManager` реализован метод `checkDeviceBattery`: мгновенный опрос батареи через sticky-интент, ступенчатые пороги 15%, 10% и 5% с гистерезисом (срабатывает однократно на каждый порог, предотвращая повторный спам; сбрасывается при подключении к зарядному устройству или уровне >20%).
  - Поддержка отмены тревоги через кнопку «ОК» в шторке (`AlertActionReceiver`).
  - В `SettingsScreen` в блок «Настройки тревог» добавлена карточка с бейджем `< 15%, 10%, 5%` и тумблером для управления оповещением.

---

### Этап 6: Полировка настроек, компактная строка AoD, диплинки уведомлений и очистка тестовых кнопок [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt`
  - `app/src/main/java/com/tirup/app/presentation/aod/AodScreen.kt`
  - `app/src/main/java/com/tirup/app/presentation/MainActivity.kt`
  - `app/src/main/java/com/tirup/app/presentation/trends/TrendsViewModel.kt`
- **Изменения**:
  - В `SettingsScreen.kt` удалена плашка-бейдж `< 15%, 10%, 5%` сразу за текстом заголовка батареи для аккуратного внешнего вида.
  - В скрытых настройках разработчика удалены 3 избыточные кнопки сирен («Экстра-ГИПО (50с)», «Экстра-ГИПЕР (16с)» и «⏹️») перед «Тест дальности». Звуки проверяются в стандартных настройках тревог, а для спасательных экранов предусмотрены отдельные кнопки.
  - В `AodScreen.kt` заменён длинный текст «Только что» на компактный формат возраста замера (`0 мин` / `N мин`), размер шрифта подстроки сахара в портретной ориентации уменьшен до `24.sp` с `maxLines = 1`, чтобы дельта, возраст и `💧 X.XX U` всегда помещались в одну строку без переносов.
  - В `MainActivity.kt` события навигации по уведомлениям (`navigateToDigestEvent`, `navigateToYearEndEvent`, `navigateToHba1cEvent`, `navigateToFocusEvent`) переведены на `replay = 1` с атомарным сбросом кеша через `resetReplayCache()`. Обработка intent вынесена в единый метод `handleNotificationIntent` с очисткой extra, устраняя пропуск открытия экрана при холодном старте приложения.
  - В `TrendsViewModel.kt` в метод `openWeeklyDigest()` добавлена немедленная фоновая инициализация дайджеста, если он ещё не был рассчитан при старте приложения по клику из уведомления.

---

### Этап 7: Унификация 6-диапазонной клинической шкалы цветов сахара (AGP/TIR) [Completed]
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/theme/Theme.kt`
  - `app/src/main/java/com/tirup/app/presentation/MainActivity.kt`
  - `app/src/main/java/com/tirup/app/presentation/aod/AodScreen.kt`
  - `app/src/main/java/com/tirup/app/presentation/trends/AgpChart.kt`
  - `app/src/main/java/com/tirup/app/presentation/focus/DailyGlucoseChart.kt`
  - `app/src/main/java/com/tirup/app/presentation/focus/FocusScreen.kt`
  - `app/src/main/java/com/tirup/app/presentation/widget/TirupWidgetUpdater.kt`
  - `app/src/main/java/com/tirup/app/presentation/overlay/FloatingBubbleService.kt`
- **Изменения**:
  - В `Theme.kt` внедрены централизованные функции и константы 6-диапазонной клинической шкалы AGP/TIR:
    - `< 3.0` ммоль/л — `ColorVeryLow` (`#EF4444`, Критически низкий)
    - `3.0 – 3.8` ммоль/л — `ColorLow` (`#F59E0B`, Низкий)
    - `3.9 – 7.8` ммоль/л — `ColorTight` (`#4ADE80`, Целевой идеальный)
    - `7.9 – 10.0` ммоль/л — `ColorTarget` (`#10B981`, Допустимый верхний)
    - `10.1 – 13.9` ммоль/л — `ColorHigh` (`#F59E0B`, Высокий)
    - `> 13.9` ммоль/л — `ColorVeryHigh` (`#EF4444`, Критически высокий)
    - Добавлены хелперы `getGlucoseColor` (для Compose), `getGlucoseColorInt` (для Canvas/RemoteViews/Drawables) и `getGlucoseColorHex`.
  - В `MainActivity.kt`: исправлен расчет цвета сахара для плашки и QuickHUD оверлея, устранена ошибка пропуска `ColorLow` для сахара 3.0–3.8.
  - В `AodScreen.kt`: цвета сахара переведены на `getGlucoseColor`, отступ между сахаром со стрелкой и подстрокой уменьшен в 2 раза (до 4dp/2dp).
  - В `AgpChart.kt`: исправлена верхняя направляющая целевого диапазона (10.0) на `ColorHigh`, добавлены направляющие критических уровней (<3.0 и >13.9).
  - В `DailyGlucoseChart.kt`: цвета точек `dotColor`, выбранной точки `selColor` и фоновые направляющие переведены на унифицированные 6 диапазонов.
  - В `FocusScreen.kt`: цвет карточки `HeroGlucoseCard` (`valueColor`) переведён на `getGlucoseColor`.
  - В `TirupWidgetUpdater.kt`: все 5 форматов виджетов рабочего стола (полоса, дашборд, компакт и график тренда) переведены на `getGlucoseColorInt`.
  - В `FloatingBubbleService.kt`: цвет прогресс-кольца плавающего пузырька переведён на `getGlucoseColorInt`.

---

## План верификации

1. **Юнит-тесты**:
   - `.\gradlew.bat testDebugUnitTest` (успешно пройдены, 26 задач).
2. **Сборка проекта**:
   - `.\gradlew.bat assembleRelease` (успешно собрано).
3. **Установка на устройство**:
   - `adb -s af27386b install -r app\build\outputs\apk\release\TIRUp-v2.2.4-release.apk` (успешно установлено на `af27386b`).
4. **Проверка на смартфоне**:
   - Сахар в диапазоне 3.0–3.8 корректно отображается янтарным цветом на всех экранах и в виджетах.
   - Критический сахар <3.0 и >13.9 отображается ярко-красным.
   - В AoD отступ между крупным сахаром и строкой телеметрии аккуратно уменьшен в 2 раза.
