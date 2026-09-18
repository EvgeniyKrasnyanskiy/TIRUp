# План реализации: Исправление пробивки экрана блокировки, скрытый блок тестирования (5 тапов), дефолты и очистка тостов

## 1. Анализ вопросов и причин

### 1.1. Почему экран спасения не пробивался через блокировку (1 раз из 5)?
- **Причина найдена:**
  1. В Android 10+ (API 29+) действуют ограничения на запуск Activity из фона (Background Activity Launch Restrictions). Если экран выключен и заблокирован, прямой вызов `context.startActivity()` блокируется системой Android.
  2. В `CaregiverSosAlarmManager` экран опекуна пробивается надёжно, потому что там используются два обязательных механизма:
     - Захват `WakeLock` с флагом `PowerManager.ACQUIRE_CAUSES_WAKEUP` (физически включает дисплей).
     - Публикация уведомления максимального приоритета с `fullScreenIntent` на канале с `CATEGORY_ALARM`.
  3. В `launchPatientRescueScreen` в `GlucoseAlertManager.kt` вызывался только `appContext.startActivity()` без `WakeLock` и без `fullScreenIntent`. Поэтому при выключенном экране система в 4 случаях из 5 сбрасывала запуск.
- **Нужны ли логи?** Нет, причина на 100% ясна из архитектуры Android и подтверждается кодом.
- **Решение:** Добавить в `launchPatientRescueScreen` захват `WakeLock` с `ACQUIRE_CAUSES_WAKEUP` и публикацию полноэкранного уведомления с `fullScreenIntent` (как в реальной тревоге и в SOS опекуна), а также добавить совместимость с OEM-прошивками (MIUI/OneUI) через флаги окна.

### 1.2. Проверочное SMS -> Тестовое SMS
- Кнопку переименовываем в **«Отправить тестовое SMS»** и перемещаем в блок «Экстренное SMS» в самый низ.

### 1.3. Скрытый блок «Тестирование систем» (Dev Mode)
- Перемещаем блок в самый низ расширенных настроек (прямо перед кнопкой «Свернуть дополнительные настройки»).
- По умолчанию блок **скрыт**.
- Активация: быстрый 5-кратный тап по тексту внизу `TIRUp • Версия x.x.x`.
  - При тапах 1..4: короткий тост «Тапните ещё X раз для открытия тестов».
  - На 5-й тап: тост «Режим тестирования активирован», и блок плавно открывается.
- Автоматическое скрытие через 5 минут после выхода из настроек.

### 1.4. Значения по умолчанию
- `emergencySmsDelayMinutes`: меняем с 5 на **3 минуты** (в `AlertSettings.kt` и `SettingsRepositoryImpl.kt`).
- `lastChanceBufferMinutes` («Последний шанс для TIR»): меняем с 90 минут на **120 минут (2 часа)**.

### 1.5. Проверка сохранения в бэкап
- Аудит `AutoBackupManager.kt`:
  - `criticalLowThresholdMmol`, `criticalHighThresholdMmol`, `isCaregiverSosWakeupEnabled`, `emergencySmsDelayMinutes`, `secondaryEmergencyContactPhone`, `secondaryEmergencyContactName`, `includeLocationInEmergencySms`, `lastChanceBufferMinutes`, `isLastChanceAlertEnabled` — **ВСЕ параметры уже добавлены** и в запись JSON, и в чтение при восстановлении.

### 1.6. Дублирующие тосты BLE и кнопка «Тест дальности»
- **Причина 2 тостов:** В `SettingsScreen.kt` одновременно выводился верхний баннер `bleSignalBannerText` и нижний системный `Toast.makeText()`, а в `SettingsViewModel.kt` при нажатии кнопки отправлялся дополнительный тост.
- **Решение:**
  - Убираем нижний системный тост на приём пакета — остаётся только аккуратный верхний баннер в приложении.
  - В `SettingsViewModel.kt` убираем лишний системный тост.
  - Из названия кнопки убираем `(10 сек)`.
  - Длительность теста и таймер блокировки кнопки меняем на **5 секунд** с обратным отсчётом `(5с)... (4с)...`.
  - В диалоге справки `(i)` заменяем упоминание 10 сек на 5 сек.

---

## 2. Предлагаемые изменения по файлам

1. **[GlucoseAlertManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt)**:
   - В `launchPatientRescueScreen`: добавить `WakeLock` с `ACQUIRE_CAUSES_WAKEUP` и публикацию полноэкранного `NotificationCompat.Builder.setFullScreenIntent(...)` для 100% пробивки заблокированного экрана.
2. **[PatientCriticalHypoActivity.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/alert/PatientCriticalHypoActivity.kt)**:
   - Добавить явные флаги окна `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, `FLAG_KEEP_SCREEN_ON`, `FLAG_DISMISS_KEYGUARD` для надёжности на смартфонах всех вендоров.
3. **[AlertSettings.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/model/AlertSettings.kt)** & **[SettingsRepositoryImpl.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt)**:
   - Дефолт `emergencySmsDelayMinutes = 3`.
   - Дефолт `lastChanceBufferMinutes = 120`.
4. **[SettingsScreen.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt)**:
   - Перенести кнопку «Отправить тестовое SMS» в блок «Экстренное SMS».
   - Перенести блок «Тестирование систем» в конец расширенных настроек и скрыть по умолчанию.
   - Добавить обработчик 5 тапов по версии для открытия режима разработчика со счётчиком кликов и авто-скрытием через 5 минут.
   - Убрать нижний дублирующий тост при приёме BLE-сигнала.
   - Сделать блокировку кнопки «Тест дальности» на 5 секунд с таймером и обновить текст справки.
5. **[SettingsViewModel.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/SettingsViewModel.kt)**:
   - Убрать дублирующий тост в `startBleRangeTest` и передавать 5 сек по умолчанию.
   - Добавить логику контроля времени открытия тестов разработчика (5 минут).

---

## 3. Глобальный BLE-баннер и акцентная кнопка навигации (FAB-стиль)

### 3.1. Глобальный BLE-баннер в приложении
- **Проблема:** Ранее плашка всплывала только на экране `SettingsScreen`. Если пользователь был на главном графике, плашка не отображалась.
- **Решение:**
  - Перенести `AnimatedVisibility` баннера на уровень `MainActivity` поверх всех экранов приложения (`NavHost`).
  - Убрать дублирующий локальный баннер из `SettingsScreen.kt`.
  - Добавить настройку `showPacketBanner: Boolean = true` в `BleBridgeSettings`, `SettingsRepositoryImpl.kt` и `AutoBackupManager.kt`.
  - Добавить чекбокс в блоке «BLE-мост: приёмник» (`SettingsScreen.kt`) для вкл/выкл баннера.
  - Логика: если чекбокс выключен, баннер скрыт для обычных фоновых замеров раз в 5 минут, но **гарантированно отображается** во время «Поиска вещателя» (`boostRemaining > 0`) и «Теста дальности» (тестовый пинг).

### 3.2. Премиальный акцентный круг (FAB-стиль) центральной кнопки навигации
- В `MainActivity.kt` сделать центральную кнопку (индекс 1, «Фокус») в виде акцентного круга `42.dp × 42.dp`:
  - Когда активна (главный экран): заливка `ActionBlue`, белая иконка `24.dp`, мягкая тень (elevation) и светлый ободок.
  - Когда не активна (выбраны другие экраны): полупрозрачная подложка `ActionBlue.copy(alpha = 0.14f)` с окантовкой `ActionBlue.copy(alpha = 0.45f)` и иконкой `ActionBlue`.

---

## 4. План верификации
- `.\gradlew testDebugUnitTest` — проверка всех тестов.
- `.\gradlew assembleRelease` — сборка релизного APK.
- Ручное тестирование:
  1. Отображение BLE-баннера на главном экране («Фокус» / график).
  2. Переключение чекбокса в настройках приёмника (проверка работы при поиске вещателя и в обычном режиме).
  3. Визуальная оценка акцентной кнопки навигации в активном и неактивном состояниях.
