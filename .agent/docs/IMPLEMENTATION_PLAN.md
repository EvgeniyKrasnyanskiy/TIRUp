# План реализации: Экспериментальный режим повышенной дальности BLE-моста (Long Range / LE Coded PHY)

План реализации опционального (opt-in) режима повышенной дальности Bluetooth 5.0 Long Range (LE Coded PHY) для прямого BLE-моста между смартфонами пациента (Вещатель) и наблюдателя (Приёмник).

## Важное замечание (Клиническая безопасность и обратная совместимость)

> **Сохранение формата пакета `BlePacketCodec`:**  
> В [BlePacketCodec.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BlePacketCodec.kt#L93-L96) старые версии приложения делают строгое прямое сравнение PIN: `if (packetPin != myPin) return null`.  
> Если модифицировать биты PIN-кода, старые версии приложения гарантированно отбросят пакет как неверный PIN.  
> **Решение:** Формат 16-байтного пакета `BlePacketCodec` остаётся **полностью неизменным**.  
> В Android API сканер получает тип физического уровня напрямую из радиодрайвера через `ScanResult.primaryPhy == BluetoothDevice.PHY_LE_CODED`.

---

## Декомпозиция задач на атомарные этапы (Task Splitting)

### Этап 1: Доменная модель и вещатель (Broadcaster)
- [x] [BleBridgeSettings.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/model/BleBridgeSettings.kt): добавить `useLongRange: Boolean = false`.
- [x] [SettingsRepositoryImpl.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt): сохранение и загрузка `KEY_BLE_BRIDGE_USE_LONG_RANGE`.
- [x] [AutoBackupManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/backup/AutoBackupManager.kt):
  - Сериализация `useLongRange` в JSON бэкапа.
  - **Обратная совместимость старых бэкапов:** при десериализации старых JSON (где поле отсутствует) гарантировать значение по умолчанию `false` без исключений.
- [x] [BleBroadcaster.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleBroadcaster.kt):
  - Проверка аппаратных возможностей (`isLeCodedPhySupported && isLeExtendedAdvertisingSupported`).
  - Поддержка `AdvertisingSetParameters` с `PHY_LE_CODED` при `useLongRange == true`.
  - Fail-safe fallback на `startAdvertising(AdvertiseSettings)` при ошибке в `AdvertisingSetCallback` (например, `ADVERTISE_FAILED_FEATURE_UNSUPPORTED`).

### Этап 2: Всеядный сканер на стороне приёмника (Observer)
- [x] [BleObserverManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt):
  - Проверка `isLeExtendedAdvertisingSupported` при инициализации сканера.
  - Настройка `ScanSettings`: `setLegacy(false)` и `setPhy(PHY_LE_ALL_SUPPORTED)` для параллельного приёма Legacy 1M и Coded PHY.
  - Fail-safe fallback: при `SCAN_FAILED_FEATURE_UNSUPPORTED` мгновенный прозрачный перезапуск с `setLegacy(true)`.
  - Детекция приёма Long Range в `ScanResult.primaryPhy` для отображения в UI.

### Этап 3: Пользовательский интерфейс и диагностика (UI / UX)
- [x] [SettingsScreen.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt):
  - Тумблер с аппаратной блокировкой (`isLeCodedPhySupported && isLeExtendedAdvertisingSupported`).
  - Диалог подтверждения со спокойными предупреждениями.
  - Кнопка Test Ping (30 сек) с визуальным отсчётом таймера.
- [x] [FocusScreen.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/focus/FocusScreen.kt):
  - Обновление бейджа `BleBridgeBadge`: `📡 Long Range` (вещатель) и `📻 Dual / Standard` (приёмник).
  - При получении пакета во время Test Ping: мгновенная анимация/вспышка бейджа и обновление метки «Только что», дающее родителю наглядное подтверждение «поймал».
  - Адресное сообщение при срабатывании таймаута тишины (`SILENCE_TIMEOUT_MS = 6 мин`) на Legacy-приёмнике.

---

## План верификации
- **Unit-тесты:**
  - Тест сохранения и восстановления `BleBridgeSettings.useLongRange` в `SettingsRepositoryImpl`.
  - Тест десериализации старого JSON бэкапа без поля `useLongRange` в `AutoBackupManagerTest` (проверка дефолта `false`).
  - Симуляционный тест fail-safe отката (перехват статуса ошибки `ADVERTISE_FAILED_FEATURE_UNSUPPORTED`).
- **Сборка проекта:** `./gradlew assembleDebug` без ошибок линковки API 26+.
- **Чек-лист ручного тестирования:** проверка на устройствах с разными чипсетами (включая проверку отсутствия сбоев вещателя на MIUI/Transsion).
