# [Completed] План реализации: Ввод лечения (IoB, CoB, BG, заметки) и передача в xDrip+ через микро-бэкенд Nightscout

## Описание задачи
Реализация задачи 1 из [ROADMAP.md](file:///h:/Diabetes/TIRUp/.agent/docs/ROADMAP.md):
- Создание отдельного блока настроек сервера Nightscout в скрытом меню разработчика (рядом с системными тестами).
- Передача введённых данных (инсулин, углеводы, замер сахара глюкометром, заметка) на микро-бэкенд Nightscout (FastAPI-сервер из [`H:\Diabetes\xDripWidget`](file:///h:/Diabetes/xDripWidget)).
- Связка с xDrip+: xDrip+ штатно синхронизирует лечение с этого сервера по REST API.
- Верификация доставки: запись подтверждается и отображается на графике TIRUp после того, как поступила и зафиксировалась в xDrip+.

---

## Архитектура решения

```
 ┌──────────────────────┐         HTTP POST /api/v1/treatments         ┌─────────────────────────────────┐
 │        TIRUp         │ ───────────────────────────────────────────> │  Микро-бэкенд Nightscout        │
 │  (FocusScreen input) │                                              │  (FastAPI из xDripWidget)       │
 └──────────────────────┘                                              └─────────────────────────────────┘
            │                                                                           │
            │ Локальный опрос (порт 17580)                                              │ Nightscout REST API Sync
            │ http://127.0.0.1:17580/treatments.json                                    │ (Cloud upload/download)
            ▼                                                                           ▼
 ┌──────────────────────┐                                              ┌─────────────────────────────────┐
 │ Подтверждение        │ ◄─────────────────────────────────────────── │              xDrip+             │
 │ в базе & на графике  │         Лечение скачано в базу xDrip         │  (рассчитывает системный IoB)   │
 └──────────────────────┘                                              └─────────────────────────────────┘
```

---

## Декомпозиция на этапы разработки

### Этап 1: Модель настроек Nightscout и хранилище
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/model/NightscoutSettings.kt` [NEW]
  - `app/src/main/java/com/tirup/app/domain/model/UserSettings.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/repository/SettingsRepositoryImpl.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/backup/AutoBackupManager.kt` [MODIFY]
- **Детали**:
  - Создание класса `NightscoutSettings`:
    - `isEnabled: Boolean = false`
    - `serverUrl: String = ""` (например, `http://xx.xxx.228.105:8085`)
    - `apiSecret: String = ""`
    - `requireXdripConfirmation: Boolean = true` (отображать на графике только после подтверждения из xDrip+)
  - Включение `nightscoutSettings` в `UserSettings`.
  - Сериализация/десериализация в SharedPreferences и резервные копии JSON.

### Этап 2: Блок управления сервером в скрытых настройках
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/presentation/settings/SettingsViewModel.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/presentation/settings/dialogs/NightscoutSettingsDialog.kt` [NEW]
- **Детали**:
  - В `SettingsScreen.kt` внутри `if (isDevTestsUnlocked)` (открывается 5 тапами по версии приложения):
    - Добавление отдельной карточки `BentoCard`: **«🌐 Сервер синхронизации (Nightscout API)»**.
    - Тумблер включения отправки.
    - Текущий URL сервера и маскированный API Secret со статусом подключения.
    - Кнопка вызова диалога редактирования адреса и ключа.
    - Кнопка **«Проверить связь»** (вызывает `GET /api/v1/status` или `GET /health` с выводом версии `Micro-Nightscout` и задержки ответа в мс).
    - Описание схемы работы (TIRUp -> Сервер -> xDrip+ -> График).

### Этап 3: Менеджер отправки Treatment и проверки связи
- **Файлы**:
  - `app/src/main/java/com/tirup/app/data/network/NightscoutUploadManager.kt` [NEW]
- **Детали**:
  - Легковесный менеджер на базе `HttpURLConnection` (без сторонних библиотек):
    - `checkConnection(url: String, secret: String): Result<String>` (`GET /api/v1/status.json` с заголовком `api-secret`).
    - `uploadTreatment(settings: NightscoutSettings, treatment: Treatment, eventType: String, glucose: Double?): Result<String>` (`POST /api/v1/treatments` с JSON-структурой, совместимой с `xDripWidget\widget.py`).
    - Автогенерация `uuid` и даты в формате ISO-8601 UTC.

### Этап 4: Активация быстрого ввода на FocusScreen и логика подтверждения
- **Файлы**:
  - `app/src/main/java/com/tirup/app/presentation/focus/FocusScreen.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/presentation/focus/FocusViewModel.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt` [MODIFY]
- **Детали**:
  - В `FocusScreen.kt`:
    - Раскомментировать `QuickActionStrip` и отображать его только при `userSettings.nightscoutSettings.isEnabled`.
    - Раскомментировать `TreatmentInputBottomSheet`.
  - В `FocusViewModel.kt`:
    - При добавлении лечения отправлять его в фоновом режиме через `NightscoutUploadManager`.
    - Показывать всплывающее сообщение (Toast / Snackbar): «Отправлено на сервер • Ожидание подтверждения от xDrip+».
    - Если `requireXdripConfirmation == false`, сразу сохранять в Room с пометкой `PENDING`.
  - В `DexdripBroadcastReceiver.kt`:
    - При фоновом опросе `127.0.0.1:17580/treatments.json` сопоставлять полученные записи с отправленными и обновлять их статус на `CONFIRMED`.

---

## План верификации
1. **Юнит-тесты**:
   - Тест формирования JSON payload для Nightscout REST API (`NightscoutPayloadTest`).
   - Тест проверки авторизации и URL хелперов.
2. **Проверка подключения (ручная)**:
   - Ввод параметров тестового сервера и нажатие «Проверить связь».
3. **Сборка приложения**:
   - `.\gradlew.bat testDebugUnitTest`
   - `.\gradlew.bat assembleRelease`
4. **Установка на телефон и проверка на живом устройстве**:
   - `adb -s af27386b install -r app\build\outputs\apk\release\TIRUp-v2.2.4-release.apk`
   - Проверка разблокировки меню по 5 тапам, ввода URL/ключа, отправки болюса/углеводов.
