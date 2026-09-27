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

## План верификации

1. **Юнит-тесты**:
   - `.\gradlew.bat testDebugUnitTest --tests com.tirup.app.data.receiver.DexdripNoteParserTest`
   - Запуск полного набора юнит-тестов `.\gradlew.bat testDebugUnitTest`
2. **Сборка проекта**:
   - `.\gradlew.bat assembleDebug`
3. **Проверка на подключенном смартфоне через adb**:
   - Установка и проверка логов `DexdripReceiver`: отсутствие ошибок `PatternSyntaxException`.
   - Проверка работы заметки `канюля → ланцет`: успешный сброс обоих счётчиков.
   - Проверка AoD: поворот экрана при активном фонарике и при разгорании (фонарик не гаснет).
   - Проверка положения бегунка фонарика (внизу) и времени разгорания (5 секунд).
   - Проверка плавного скрытия плашки яркости без залипаний.
   - Проверка экрана Настроек: блок AoD находится под «Плавающий пузырёк».
   - Проверка отсутствия баннеров и лишних уведомлений BLE.
