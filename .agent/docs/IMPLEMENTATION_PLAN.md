# План реализации: Закрытие всех задач экспресс-аудита и аудита формулировок TIRUp

**Цель**: Устранить все выявленные клинические, архитектурные и UX-недочёты из файлов [express_audit.md](file:///d:/Users/physicist/Desktop/ken/TIRUp/.agent/docs/express_audit.md) и [formulation_audit.md](file:///d:/Users/physicist/Desktop/ken/TIRUp/.agent/docs/formulation_audit.md).

---

## Этап 1. Аудит формулировок клинических отчетов (GuidebookPdfGenerator)
**Файл**: `app/src/main/java/com/tirup/app/presentation/reports/GuidebookPdfGenerator.kt`

- **1.1. Исправление числовых примеров GMI/eA1c** (L152-153):
  - RU: `средний 8.5 ≈ eA1c 7.0%, а 10.0 ≈ eA1c 7.6%` (вместо некорректных 7.5% и 8.6%).
  - EN: `Mean 8.5 ≈ eA1c 7.0%, Mean 10.0 ≈ eA1c 7.6%`.
- **1.2. Унификация диапазона «натощак»** (L164-165):
  - Заменить `3.3–5.5 ммоль/л` на `3.9–5.5 ммоль/л` для согласованности со стандартом ADA и строкой 152.
- **1.3. Единицы SD в английской версии ночного профиля** (L235):
  - Заменить `Target: SD ≤27 mg/dL, TBR = 0%` на `Target: SD ≤1.5 mmol/L (≤27 mg/dL), TBR = 0%`.
- **1.4. Уточнение формулы GMI** (L158-159):
  - Указать, что формула в ммоль/л является валидированной клинической адаптацией формулы Bergenstal 2018.
- **1.5. Формулировка постпрандиального пика** (L242-243):
  - Уточнить: `прирост >2.2 ммоль/л от базы` (вместо неоднозначного `>2.5 ммоль/л`).

---

## Этап 2. Критическая безопасность SOS и защита от гипогликемии (P0)
**Файлы**: `GlucoseAlertManager.kt`, `AlertActionReceiver.kt`, `AndroidManifest.xml`, `EmergencySmsManager.kt`

- **2.1. Guard на Auto-dismiss при активном телефоне (Issue #1)**:
  - В `GlucoseAlertManager.checkAndAlert()`: перенести проверку `isPhoneInActiveUse` после определения `latest.valueMmol`.
  - Запретить автоматический сброс критического алерта и таймера SOS, если `latest.valueMmol < alerts.criticalLowThresholdMmol`. Пользователь в панике или полубессознательном состоянии при разблокировке не должен терять вызов SOS.
- **2.2. Перевод SOS-таймера на `AlarmManager` (Issue #7 & #8)**:
  - Вместо нестабильной корутины `CoroutineScope(Dispatchers.IO).launch { delay(...) }`, которая сбрасывается при убийстве процесса системой (Doze/OOM), использовать `AlarmManager.setExactAndAllowWhileIdle()` с `PendingIntent` на `AlertActionReceiver.ACTION_TRIGGER_EMERGENCY_SMS`.
  - В `cancelEmergencySmsTimer()` гарантированно отменять `PendingIntent` в `AlarmManager`.
  - При срабатывании будильника в `AlertActionReceiver`:
    - Проверить актуальное состояние `isCriticalAlarmActive`.
    - Запросить последнее свежее значение сахара из базы данных. Если пациент успел купировать гипогликемию и сахар поднялся $\ge$ порога тревоги — отменить отправку SMS и залогировать безопасную отмену.
- **2.3. Приоритет критической гипогликемии над потерей сигнала (Issue #3)**:
  - В `GlucoseAlertManager.checkAndAlert()`: проверку `checkSignalLoss` выполнять так, чтобы она не блокировала (`return`) обработку критической гипогликемии, если последнее доступное измерение было ниже критического порога.

---

## Этап 3. Надежность отправки экстренных SMS и геопозиции
**Файлы**: `EmergencySmsManager.kt`, `AlertActionReceiver.kt`, `AndroidManifest.xml`

- **3.1. Атомарный кулдаун отправки SMS (Issue #4)**:
  - Добавить in-memory отметку времени `inMemoryLastSentTimestamp`, устанавливаемую синхронно в момент успешной отправки, для гарантированного предотвращения спама даже при сбоях сохранения в DataStore.
- **3.2. Fallback геолокации для SOS (Issue #5)**:
  - Если кэшированная локация `getLastKnownLocation` возвращает `null`, запросить актуальную фиксацию через `LocationManager.getCurrentLocation` (Android 11+) или короткую единичную подписку с безопасным таймаутом (до 3 сек).
- **3.3. `SentIntent` / `DeliveryIntent` для контроля отправки (Issue #6)**:
  - Передавать `PendingIntent` статуса отправки в `sendTextMessage` / `sendMultipartTextMessage` с обработкой результата в `AlertActionReceiver` для ведения диагностического лога доставки SOS.

---

## Этап 4. BLE-мост, дедупликация и приёмники данных
**Файлы**: `BleObserverManager.kt`, `GlucoseRepositoryImpl.kt`, `DexdripBroadcastReceiver.kt`, `SmsQueryReceiver.kt`

- **4.1. Приоритет источника BLE-моста (Issue #10)**:
  - В `BleObserverManager.kt:622` вызывать `glucoseRepository.insertReadingFromSource(newReading, DataSourcePriority.BLE_BRIDGE)` вместо generic-метода с дефолтным `LOCAL_XDRIP`.
- **4.2. Синхронизация и защита от гонок в `boostScan` (Issue #9)**:
  - В `BleObserverManager.kt` управлять `boostJob` и переключением режимов сканера атомарно внутри существующего механизма `mutex.withLock`.
- **4.3. Оптимизация потоков и кэша в `DexdripBroadcastReceiver` (Issue #13 & #14)**:
  - Заменить поля `cachedIob` и `cachedCob` на атомарный объект `AtomicReference<TreatmentCache>` во избежание рассинхронизации IoB/CoB.
  - Использовать `companionScope` вместо создания экземпляра `scope = CoroutineScope(...)` при каждом вызове приемника.
- **4.4. Точный триггер запроса сахара в `SmsQueryReceiver` (Issue #15 & #16)**:
  - Исключить ложные срабатывания `isQueryTrigger` на бытовые сообщения со словом «сахар» («купи сахар в магазине»), требуя точного совпадения ключевых слов, вопросительного знака или регулярного выражения с границами слов (`\bсахар\b\s*\?`).

---

## Этап 5. Потокобезопасность и рефакторинг `GlucoseAlertManager` (P2)
**Файл**: `GlucoseAlertManager.kt`

- **5.1. Потокобезопасность полей и снуза (Issue #2)**:
  - Заменить переменные таймстампов на `AtomicLong` (`lastHypoAlertTimestamp`, `userAcknowledgedHypoTimestamp`, `lastHyperAlertTimestamp` и др.).
  - Добавить синхронизацию `@Synchronized` на `checkAndAlert()`, чтобы исключить конкурентные вызовы от BLE, Broadcast и Wi-Fi LAN.
- **5.2. Снижение когнитивной сложности `checkAndAlert()` (Issue #6 в P2)**:
  - Декомпозировать монолитную функцию на компактные изолированные методы:
    - `evaluateCriticalHypo(...)`
    - `evaluateCriticalHyper(...)`
    - `evaluateMainAlert(...)`
    - `evaluatePredictiveAlert(...)`
    - `evaluateSignalLoss(...)`

---

## План верификации
1. **Сборка проекта**: `gradlew assembleDebug` — проверка компиляции всех модулей.
2. **Юнит-тесты**: `gradlew testDebugUnitTest` — проверка регрессий в тестах алертов, парсеров и репозиториев.
3. **Ручная проверка**:
   - Генерация отчета через `GuidebookPdfGenerator` (проверка текста и формул).
   - Тест сценария `isPhoneInActiveUse` при критической гипогликемии.
   - Тест срабатывания `ACTION_TRIGGER_EMERGENCY_SMS` через AlarmManager.
   - Тест фильтрации SMS-триггеров («купи сахар» vs «сахар?»).
