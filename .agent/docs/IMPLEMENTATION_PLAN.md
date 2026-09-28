# [Completed] План реализации: Прямой приём от xDrip+ по Wi-Fi (LAN Follower) — Этап 1: Сетевой клиент и дедупликация в Room DB (v2.4.0)

## Описание задачи
Реализация прямого фонового приёма данных сахара, активного инсулина (IoB), активных углеводов (CoB), меток лечения (болюсы, еда, замеры) и уровня заряда батареи мастера от веб-сервера xDrip+ по локальной сети Wi-Fi (порт 17580) без необходимости выхода в интернет:
1. Модель конфигурации `XdripLanSettings` с сохранением в `UserSettings`, `SettingsRepository` и JSON-бэкапах.
2. Сетевой клиент `XdripLanClient` с поддержкой авторизации по `API Secret` (SHA-1), опросом `sgv.json`, `pebble`, `treatments.json`.
3. Система сквозной дедупликации данных и приоритетов источников (`LOCAL_XDRIP` > `BLE_BRIDGE` > `WIFI_LAN` > `NIGHTSCOUT_CLOUD`) в `GlucoseRepository` и Room DB.
4. Фоновый менеджер опроса `XdripLanManager` с реактивным `StateFlow` статуса связи, заряда батареи мастера и запуском при наличии Wi-Fi.
5. Диалог настройки `XdripLanSettingsDialog` с функцией проверки связи в реальном времени в настройках приложения.
6. Модульные тесты парсинга и сетевой логики.

---

## Архитектура решения

```
┌────────────────────────────────────────────────────────┐
│             xDrip+ Master Phone (Wi-Fi)                │
│             Web Server: http://<ip>:17580              │
│   Endpoints: /sgv.json, /pebble, /treatments.json      │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTP GET (LAN / Hotspot)
                           ▼
┌────────────────────────────────────────────────────────┐
│                   XdripLanClient                       │
│  - Парсинг SGV (мг/дл → ммоль/л, стрелки)             │
│  - Парсинг Pebble (IoB, CoB, батарея мастера 🔋)       │
│  - Парсинг Treatments (болюсы, углеводы, UUID)         │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                   XdripLanManager                      │
│  - Фоновый периодический опрос (раз в 60 с)            │
│  - Проверка активного Wi-Fi соединения                │
│  - StateFlow<XdripLanStatus> (IP, батарея, статус)     │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
              ▼                            ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│     GlucoseRepository     │ │    GlucoseAlertManager   │
│   (Сквозная дедупликация  │ │   & TirupWidgetUpdater   │
│    и матрица приоритетов) │ │  (Проверка порогов/звуков│
│       ▼                   │ │   и обновление виджетов) │
│   Room DB (Readings/Treat)│ └──────────────────────────┘
└───────────────────────────┘
```

---

## Этапы разработки

### Этап 1: Модель данных, настройки и бэкапы
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/model/XdripLanSettings.kt` [NEW]
  - `app/src/main/java/com/tirup/app/domain/model/UserSettings.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/backup/AutoBackupManager.kt` [MODIFY]
- **Детали**:
  - Добавить `XdripLanSettings(isEnabled, masterHost, port = 17580, apiSecret, pollIntervalSeconds = 60)`.
  - Валидация хоста, очистка URL, SHA-1 хеширование секрета.
  - Интеграция в `UserSettings` и сохранение в SharedPreferences.
  - Поддержка экспорта/импорта в JSON-бэкапах.

### Этап 2: Сетевой клиент XdripLanClient
- **Файлы**:
  - `app/src/main/java/com/tirup/app/data/network/XdripLanClient.kt` [NEW]
  - `app/src/main/java/com/tirup/app/domain/model/XdripLanStatus.kt` [NEW]
- **Детали**:
  - `testConnection(settings)`: проверка доступности сервера мастера, возврат уровня батареи, версии и последнего сахара.
  - `fetchSgv(settings, count)`: запрос `/sgv.json`, конвертация mg/dL в mmol/L, сопоставление `direction` со стрелками тренда TIRUp (`↑`, `↗`, `→`, `↘`, `↓`, `⇈`, `⇊`).
  - `fetchPebble(settings)`: запрос `/pebble`, извлечение `iob`, `cob`, `battery` мастера.
  - `fetchTreatments(settings, count)`: запрос `/api/v1/treatments.json` (с fallback на `/treatments.json`), извлечение болюсов, углеводов, заметок и `uuid`.

### Этап 3: Дедупликация и приоритеты источников в GlucoseRepository
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/model/DataSourcePriority.kt` [NEW]
  - `app/src/main/java/com/tirup/app/domain/repository/GlucoseRepository.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/repository/GlucoseRepositoryImpl.kt` [MODIFY]
- **Детали**:
  - Ввести шкалу приоритетов: `LOCAL_XDRIP` (4) > `BLE_BRIDGE` (3) > `WIFI_LAN` (2) > `NIGHTSCOUT_CLOUD` (1).
  - Реализовать дедупликацию в окне 25 с: более высокий приоритет не затирается более низким, но обогащается полями `iob`, `cob`, `trendArrow`, если они отсутствовали.
  - Сквозное сохранение treatments с проверкой `uuid` и 45-с окна дедупликации.

### Этап 4: Фоновый менеджер XdripLanManager
- **Файлы**:
  - `app/src/main/java/com/tirup/app/data/network/XdripLanManager.kt` [NEW]
  - `app/src/main/java/com/tirup/app/TirupApplication.kt` [MODIFY]
- **Детали**:
  - Синглтон с методом `syncWithSettings(context, settingsRepository, glucoseRepository, database)`.
  - Фоновый цикл опроса с защитой от запуска вне локальной сети (проверка Wi-Fi / Hotspot).
  - При получении новых данных: сохранение в Room, вызов `TirupWidgetUpdater.updateAllWidgets` и `GlucoseAlertManager.checkAndAlert`.
  - Экспорт состояния `StateFlow<XdripLanStatus>` (онлайн/офлайн, заряд батареи мастера, время последнего успешного опроса, ошибки).

### Этап 5: UI настроек и проверка соединения
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/settings/dialogs/XdripLanSettingsDialog.kt` [NEW]
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsViewModel.kt` [MODIFY]
- **Детали**:
  - Добавить карточку «📡 Wi-Fi LAN Follower (xDrip+)» в меню настроек интеграций/разработчика.
  - Отображение текущего статуса связи и заряда батареи мастера в чипе.
  - Диалог редактирования параметров (IP, порт, секрет, интервал) с кнопкой «Проверить связь» (живой тест связи с отображением ответа).

### Этап 6: Юнит-тесты и версионирование
- **Файлы**:
  - `app/src/test/java/com/tirup/app/data/network/XdripLanClientTest.kt` [NEW]
  - `app/src/test/java/com/tirup/app/domain/model/XdripLanSettingsTest.kt` [NEW]
  - `app/build.gradle.kts` [MODIFY] (v2.4.0, versionCode 20)
- **Детали**:
  - Тестирование парсинга JSON ответов `sgv.json`, `pebble`, `treatments.json`.
  - Тестирование SHA-1 хеширования API Secret и валидации хоста.

---

## План верификации
1. **Автоматические тесты**:
   - `.\gradlew.bat testDebugUnitTest` — проверка всех тестов клиента, парсеров и валидации настроек.
2. **Сборка релизного APK**:
   - `.\gradlew.bat assembleRelease` — проверка компиляции и сборки APK.
3. **Установка и проверка**:
   - `adb -s af27386b install -r app/build/outputs/apk/release/TIRUp-v2.4.0-release.apk`.
4. **Git Commit**:
   - Фиксация атомарных изменений согласно Conventional Commits.
