# План реализации: Кнопки тестирования BLE-моста и увеличение поиска до 60 сек

## 1. Статус выполнения задач

1. [Выполнено] **Увеличение времени быстрого поиска вещателя до 60 секунд** (`BleObserverManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`):
   - В `BleObserverManager` длительность активного поиска (Boost) увеличена с 30 до 60 секунд (`_boostRemainingSec.value = 60`), добавлен метод `boostScanFor60Sec`.
   - В `SettingsViewModel` и `SettingsScreen` обновлены тексты кнопок и тостов («Быстрый поиск вещателя (60 сек)»).

2. [Выполнено] **Методы запуска теста связи и форсированного поиска в FocusViewModel** (`FocusViewModel.kt`):
   - Добавлены методы `sendBleTestPing()` (импульс вещателя 30 сек) и `boostBleObserverScan()` (форсированный поиск 60 сек) с поддержкой контекста.

3. [Выполнено] **Кнопки тестирования в диалоге BLE-моста на главном экране** (`FocusScreen.kt`):
   - Добавлена проверка включённости Bluetooth и необходимых runtime-разрешений (`BLUETOOTH_ADVERTISE` / `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`).
   - Подписались на `BleObserverManager.boostRemainingSec`.
   - В `BleStatusDialog` добавлены кнопки тестирования:
     - Для вещателя: «📡 Тест связи (импульс 30 сек)» / при активности «📡 Импульс вещания (Xs)...» с блокировкой (`enabled = !isBleBroadcasting`).
     - Для приёмника: «🔍 Быстрый поиск вещателя (60 сек)» / при активности «⚡ Активный поиск (Xs)...» с блокировкой (`enabled = boostRemainingSec <= 0`).
