# План реализации: Закрытие всех задач экспресс-аудита и аудита формулировок TIRUp [Completed]

**Цель**: Устранить все выявленные клинические, архитектурные и UX-недочёты из файлов [express_audit.md](file:///d:/Users/physicist/Desktop/ken/TIRUp/.agent/docs/express_audit.md) и [formulation_audit.md](file:///d:/Users/physicist/Desktop/ken/TIRUp/.agent/docs/formulation_audit.md).

**Статус**: Все этапы успешно выполнены, верифицированы тестами и зафиксированы коммитами.

---

## Этап 1. Аудит формулировок клинических отчетов (GuidebookPdfGenerator) [Completed]
**Файл**: `app/src/main/java/com/tirup/app/presentation/reports/GuidebookPdfGenerator.kt`
- **Статус**: Выполнен (коммит `f1e5c84`)

- **1.1. Исправление числовых примеров GMI/eA1c** (L152-153): [Completed]
  - RU: `средний 8.5 ≈ eA1c 7.0%, а 10.0 ≈ eA1c 7.6%` (вместо некорректных 7.5% и 8.6%).
  - EN: `Mean 8.5 ≈ eA1c 7.0%, Mean 10.0 ≈ eA1c 7.6%`.
- **1.2. Унификация диапазона «натощак»** (L164-165): [Completed]
  - Заменить `3.3–5.5 ммоль/л` на `3.9–5.5 ммоль/л` для согласованности со стандартом ADA и строкой 152.
- **1.3. Единицы SD в английской версии ночного профиля** (L235): [Completed]
  - Заменить `Target: SD ≤27 mg/dL, TBR = 0%` на `Target: SD ≤1.5 mmol/L (≤27 mg/dL), TBR = 0%`.
- **1.4. Уточнение формулы GMI** (L158-159): [Completed]
  - Указать, что формула в ммоль/л является валидированной клинической адаптацией формулы Bergenstal 2018.
- **1.5. Формулировка постпрандиального пика** (L242-243): [Completed]
  - Уточнить: `прирост >2.2 ммоль/л от базы` (вместо неоднозначного `>2.5 ммоль/л`).

---

## Этап 2. Критическая безопасность SOS и защита от гипогликемии (P0) [Completed]
**Файлы**: `GlucoseAlertManager.kt`, `AlertActionReceiver.kt`, `AndroidManifest.xml`, `EmergencySmsManager.kt`
- **Статус**: Выполнен (коммит `bb823ea`)

- **2.1. Guard на Auto-dismiss при активном телефоне (Issue #1)**: [Completed]
  - В `GlucoseAlertManager.checkAndAlert()`: перенесена проверка `isPhoneInActiveUse` после определения `latest.valueMmol`.
  - Запрещен автоматический сброс критического алерта и таймера SOS, если `latest.valueMmol < alerts.criticalLowThresholdMmol`.
- **2.2. Перевод SOS-таймера на `AlarmManager` (Issue #7 & #8)**: [Completed]
  - Вместо нестабильной корутины используется `AlarmManager.setExactAndAllowWhileIdle()` с `PendingIntent` на `AlertActionReceiver.ACTION_TRIGGER_EMERGENCY_SMS`.
  - В `cancelEmergencySmsTimer()` гарантированно отменяется `PendingIntent` в `AlarmManager`.
  - При срабатывании будильника в `AlertActionReceiver`:
    - Проверяется актуальное состояние `isCriticalAlarmActive`.
    - Запрашивается последнее свежее значение сахара из базы данных. Если пациент успел купировать гипогликемию ($\ge$ порога тревоги) — отправка SMS безопасно отменяется.
- **2.3. Приоритет критической гипогликемии над потерей сигнала (Issue #3)**: [Completed]
  - В `GlucoseAlertManager.checkAndAlert()`: критическая гипогликемия оценивается до проверки потери сигнала, исключая подавление сирены и SOS-таймера.

---

## Этап 3. Надежность отправки экстренных SMS и геопозиции [Completed]
**Файлы**: `EmergencySmsManager.kt`, `AlertActionReceiver.kt`, `AndroidManifest.xml`
- **Статус**: Выполнен (коммит `bb823ea`)

- **3.1. Атомарный кулдаун отправки SMS (Issue #4)**: [Completed]
  - Добавлена in-memory отметка времени `inMemoryLastSentTimestamp`, устанавливаемая синхронно в момент успешной отправки для предотвращения спама при задержках DataStore.
- **3.2. Fallback геолокации для SOS (Issue #5)**: [Completed]
  - Если кэшированная локация устарела или `null`, запрашивается свежая фиксация через `LocationManager.getCurrentLocation` (Android 11+) с таймаутом 3.5 сек.
- **3.3. `SentIntent` / `DeliveryIntent` для контроля отправки (Issue #6)**: [Completed]
  - Передаются `PendingIntent` статуса отправки в `sendTextMessage` / `sendMultipartTextMessage` с обработкой результата в `AlertActionReceiver`.

---

## Этап 4. BLE-мост, дедупликация и приёмники данных [Completed]
**Файлы**: `BleObserverManager.kt`, `GlucoseRepositoryImpl.kt`, `DexdripBroadcastReceiver.kt`, `SmsQueryReceiver.kt`
- **Статус**: Выполнен (коммит `bb823ea`)

- **4.1. Приоритет источника BLE-моста (Issue #10)**: [Completed]
  - В `BleObserverManager.kt` вызывается `glucoseRepository.insertReadingFromSource(newReading, DataSourcePriority.BLE_BRIDGE)` с правильным рангом 3.
- **4.2. Синхронизация и защита от гонок в `boostScan` (Issue #9)**: [Completed]
  - В `BleObserverManager.kt` выделен `stopScanningInternalLocked()` и переключение режимов сканера обернуто в `mutex.withLock`.
- **4.3. Оптимизация потоков и кэша в `DexdripBroadcastReceiver` (Issue #13 & #14)**: [Completed]
  - Поля `cachedIob` и `cachedCob` заменены на атомарный контейнер `AtomicReference<TreatmentCache>`.
  - Используется `companionScope` вместо создания инстанс-полей `scope = CoroutineScope(...)`.
- **4.4. Точный триггер запроса сахара в `SmsQueryReceiver` (Issue #15 & #16)**: [Completed]
  - Исключены ложные срабатывания `isQueryTrigger` на бытовые сообщения («купи сахар в магазине»). Добавлены юнит-тесты `SmsQueryReceiverTest`.
  - Ужесточен `isSosMessage` в `SosSmsParser`, исключая ложные сирены у фоловеров.

---

## Этап 5. Потокобезопасность и рефакторинг `GlucoseAlertManager` (P2) [Completed]
**Файл**: `GlucoseAlertManager.kt`
- **Статус**: Выполнен (коммит `bb823ea`)

- **5.1. Потокобезопасность полей и снуза (Issue #2)**: [Completed]
  - Заменены переменные таймстампов на `AtomicLong` и `AtomicInteger`.
  - Добавлена синхронизация `@Synchronized` на `checkAndAlert()`.
- **5.2. Снижение когнитивной сложности `checkAndAlert()` (Issue #6 в P2)**: [Completed]
  - Декомпозирована монолитная функция на компактные изолированные методы:
    - `evaluateCriticalHypo(...)`
    - `evaluateCriticalHyper(...)`
    - `evaluateMainAlert(...)`
    - `evaluatePredictiveAlert(...)`

---

## План верификации
1. **Сборка проекта**: `gradlew assembleDebug` — проверка компиляции всех модулей.
2. **Юнит-тесты**: `gradlew testDebugUnitTest` — проверка регрессий в тестах алертов, парсеров и репозиториев.
3. **Ручная проверка**:
   - Генерация отчета через `GuidebookPdfGenerator` (проверка текста и формул).
   - Тест сценария `isPhoneInActiveUse` при критической гипогликемии.
   - Тест срабатывания `ACTION_TRIGGER_EMERGENCY_SMS` через AlarmManager.
   - Тест фильтрации SMS-триггеров («купи сахар» vs «сахар?»).
