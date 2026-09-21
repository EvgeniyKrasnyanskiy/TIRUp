# План реализации: Переход на роли «Мастер / Фоловер», перенос переключателя под «Мой профиль» и разделение тестов тревог

## Цель
1. Поднять переключатель роли в самый верх экрана настроек (сразу под карточкой «Мой профиль»).
2. Заменить терминологию «Пациент / Опекун» на «Мастер (Сенсор) / Фоловер (Наблюдатель)» во всех интерфейсах, уведомлениях, настройках и системных текстах.
3. Разделить тест критической тревоги (Tier 3):
   - В роли **Мастера** — запуск экрана спасения при гипо (`PatientCriticalHypoActivity`: прием углеводов, таймер купирования, звонок близким).
   - В роли **Фоловера** — запуск экрана тревоги фоловера (`CaregiverSosActivity`: карточка мастера, сахар 2.8 ⇊, задержка ответа, звонок мастеру, координаты).

---

## Пакет 1: UI выбора роли вверху настроек и разделение теста тревог
### Файлы:
1. **`app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt`**:
   - Добавить карточку выбора роли сразу под `PatientProfileSummaryCard`:
     - Две акцентные плашки-кнопки (segmented/bento):
       👑 **Мастер (Сенсор)** (синий акцент ActionBlue)
       👁️ **Фоловер (Наблюдатель)** (изумрудный акцент PrimaryEmerald)
     - Подписи с пояснением назначения каждой роли.
   - Удалить старый глубоко спрятанный чекбокс роли из карточки «Экстренное SMS».
   - В строке Tier 3 (Критическая тревога):
     - Кнопка «Тест» проверяет `alerts.isCaregiverRole`:
       - Если `isCaregiverRole == true` (Фоловер) -> вызывать `viewModel.testCaregiverSosScreen()` (экран фоловера).
       - Если `false` (Мастер) -> вызывать `viewModel.testAlert(AlertTier.CRITICAL)` (экран мастера).
2. **`app/src/main/java/com/tirup/app/presentation/settings/SettingsViewModel.kt`**:
   - В методе `testAlert(tier)`: при `tier == AlertTier.CRITICAL` и роли `isCaregiverRole` направлять на тест экрана фоловера.
   - Обновить текст снекбара при отправке тестового SMS: «🚨 Тестовое SOS-SMS отправлено фоловеру».
3. **`app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt`**:
   - При запуске `sendTestAlert` для `AlertTier.CRITICAL` учитывать роль устройства, если уведомление открывает активность.

---

## Пакет 2: Переименование терминологии «Пациент / Опекун» во всех UI-текстах
### Файлы:
1. **`app/src/main/java/com/tirup/app/presentation/alert/CaregiverSosActivity.kt`**:
   - Тексты заголовков и карточек:
     - «Проверка экстренного канала фоловера»
     - «Сирена на телефоне мастера $delayMinutes мин без ответа!»
     - «Срочно свяжитесь с мастером!»
2. **`app/src/main/java/com/tirup/app/presentation/alert/PatientCriticalHypoActivity.kt`**:
   - Текст кнопки экстренного звонка: «Позвонить фоловеру» (вместо «Позвонить опекуну»).
3. **`app/src/main/java/com/tirup/app/presentation/settings/dialogs/BleBridgeDialogs.kt` & `AlertThresholdDialogs.kt`**:
   - Замена «смартфон пациента / смартфон опекуна» на «смартфон мастера / смартфон фоловера».
   - «отсчёт таймера SOS фоловерам».
4. **`app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt`**:
   - Обновление текстов в руководстве пользователя: Глава «SOS SMS и режим Фоловера (Наблюдателя)».

---

## План верификации
1. Запуск unit-тестов: `.\gradlew.bat testDebugUnitTest --no-daemon`.
2. Проверка сборки без регрессий.
3. Проверка интерфейса настроек и переключения ролей.
