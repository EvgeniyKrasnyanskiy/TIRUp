# Полный финальный аудит проекта TIRUp

Документ для фиксации результатов последовательного глубокого аудита перед релизом.

---

## 1. План проведения аудита

- [x] **Блок 1: Статический анализ, предупреждения компилятора и тесты (Lint & Warnings)**
  - Устранены 4 блокирующие ошибки Android Lint (сборка `lintDebug` завершена со статусом `BUILD SUCCESSFUL`, 0 errors).
  - Выявлены и подготовлены к устранению ключевые предупреждения безопасности и утечек памяти (StaticFieldLeak, UnprotectedSMSBroadcastReceiver).

- [ ] **Блок 2: Слой данных и хранилище (Data Layer & Storage)**
  - Room Entities: индексы по `timestamp`, `year`, `date`, транзакции в DAO.
  - Потоки DataStore / SharedPreferences и репозитории (`SettingsRepository`, `GlucoseReadingRepository`).
  - Механизмы бэкапа и экспорта (`AutoBackupManager`, работа с CSV/JSON/ZIP, закрытие I/O потоков).

- [ ] **Блок 3: Доменная логика и медицинские расчеты (Domain & Clinical Math)**
  - Валидация формул (TIR, TAR, TBR, CV, SD, eA1c ADAG, GMI, AGP процентили 5/10/25/50/75/90/95).
  - Устойчивость к экстремальным значениям, пустой базе, делению на 0, `Float.NaN`.
  - Модели трендов и предикции (`GlucoseTrendPredictor`).

- [ ] **Блок 4: Фоновые процессы, жизненный цикл Android и BLE (System & Hardware)**
  - `FloatingBubbleService`: утечки WindowManager, жизненный цикл при смене ориентации/разрешений.
  - `WorkManager` воркеры (`AutoBackupWorker`, `YearEndDigestWorker`, `Hba1cReminderWorker`).
  - `Family BLE Bridge`: валидация пакетов, таймауты, корректное освобождение `BluetoothLeAdvertiser` и `BluetoothLeScanner`.

- [ ] **Блок 5: Presentation-слой, Compose и генераторы PDF (UI/UX & Export)**
  - Рекомпозиции Compose: стабильность моделей, мемоизация `derivedStateOf`, ключи в `LazyColumn`.
  - Производительность графиков на Canvas (`DailyGlucoseChart`, `AgpChart`).
  - Безопасность и утечки памяти в генераторах PDF (`close()`, переполнение Canvas).

- [ ] **Блок 6: Локализация, грамматика и типографика (L10n & Text Consistency)**
  - Согласованность терминологии (RU / EN).
  - Склонения числительных через `PluralUtils`.
  - Отсутствие захардкоженных строк без языковой ветки `isRu`.

---

## 2. Результаты и выявленные замечания

### Блок 1: Статический анализ и компиляция (Lint & Compiler)

1. **[Исправлено] 4 критические ошибки Android Lint**:
   * `BleBroadcaster.kt:309` и `BleObserverManager.kt:273`: вызовы `stopAdvertising` и `stopScan` требовали явного перехвата `SecurityException` и аннотации `@SuppressLint("MissingPermission")` (права проверяются перед запуском).
   * `AndroidManifest.xml`: требование `<uses-feature android:name="android.hardware.telephony" android:required="false" />` при объявлении SMS-разрешений. Без этого приложение не могло устанавливаться на планшеты и устройства без модуля сотовой связи.
   * **Результат**: `.\gradlew lintDebug` теперь проходит чисто: `0 errors, 306 warnings` (`BUILD SUCCESSFUL`).

2. **[Выявлено в коде] Защита SMS BroadcastReceiver (`UnprotectedSMSBroadcastReceiver`)**:
   * В `AndroidManifest.xml` для `SmsQueryReceiver` отсутствует атрибут `android:permission="android.permission.BROADCAST_SMS"`.
   * **Риск**: Стороннее вредоносное приложение могло посылать поддельные широковещательные интенты с имитацией входящих SMS для вызова внутренней логики.
   * **Решение**: Добавить защиту `android:permission="android.permission.BROADCAST_SMS"`.

3. **[Выявлено в коде] Потенциальные утечки памяти в ViewModel (`StaticFieldLeak`)**:
   * В `FocusViewModel`, `ReportsViewModel` и `SettingsViewModel` в первичных конструкторах хранились неиспользуемые поля `private val context: Context`.
   * **Риск**: Удержание ссылки на Context внутри жизненного цикла ViewModel (живущей дольше Activity) приводит к утечкам памяти в Android.
   * **Решение**: Удалить неиспользуемые параметры `context` из конструкторов этих трёх ViewModel и их создания в `MainActivity.kt`.

4. **[Выявлено в манифесте] Разрешения точных будильников (`SCHEDULE_EXACT_ALARM`)**:
   * Предупреждения в `AutoBackupManager.kt` и `GlucoseAlertManager.kt` на вызовы `setExactAndAllowWhileIdle`.
   * **Оценка**: Корректно для медицинского приложения мониторинга диабета и ночного архивирования. Соответствует политике Google Play (Use Cases: Alarms & Timers / Medical monitoring).

