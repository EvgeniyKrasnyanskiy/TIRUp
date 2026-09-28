# [Completed] План реализации: Сквозное удаление лечения с сервера Nightscout по UUID (v2.3.1)

## Описание задачи
Обеспечить удаление записей терапии (инсулин, углеводы, замеры, заметки) не только локально в TIRUp, но и на сервере Nightscout (FastAPI из `H:\Diabetes\xDripWidget`):
1. Сохранять `uuid` каждой записи при отправке из TIRUp и при приёме из xDrip (`treatments.json`).
2. В `NightscoutUploadManager` реализовать вызов `DELETE /api/v1/treatments/{uuid}?token={api_secret}` с авторизацией по SHA-1 хешу.
3. При нажатии «Удалить» на графике сахара в `FocusScreen`:
   - Если запись имеет `uuid` и настроен Nightscout, отправлять `DELETE` на сервер.
   - Сервер выполняет `_mark_voided` (`is_voided = 1`, обнуляет дозы).
   - Удалять запись из локальной базы данных TIRUp.
   - Выводить Toast-уведомление пользователю.
4. Итог: удалённые метки не «воскресают» в xDrip+ и TIRUp при последующих сеансах синхронизации. Совместимо по UUID с десктопным виджетом Windows.

---

## Архитектура решения

```
  ┌────────────────────────────────────────────────────────┐
  │                 TIRUp (FocusScreen)                    │
  │     Пользователь нажимает "Удалить" на графике        │
  └──────────────────────────┬─────────────────────────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
   Локальное удаление              HTTP DELETE /api/v1/treatments/{uuid}
   (Room DB: treatments)                     │
                                             ▼
                               ┌─────────────────────────────┐
                               │  Микро-бэкенд Nightscout    │
                               │  _mark_voided(uuid)         │
                               │  is_voided = 1, дозы = 0    │
                               └─────────────┬───────────────┘
                                             │
                                             │ Следующая синхронизация xDrip
                                             ▼
                               ┌─────────────────────────────┐
                               │           xDrip+            │
                               │ Запись аннулирована и       │
                               │ больше не восстанавливается │
                               └─────────────────────────────┘
```

---

## Этапы разработки

### Этап 1: Модель данных и Room БД
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/model/Treatment.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/local/entity/TreatmentEntity.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/local/dao/TreatmentDao.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/local/AppDatabase.kt` [MODIFY] (версия 7, миграция MIGRATION_6_7)
- **Детали**:
  - Добавить поле `uuid: String? = null`.
  - Добавить индекс по `uuid` в Room.
  - В `TreatmentDao`: добавить методы `getById(id: Long)`, `getByUuid(uuid: String)`.

### Этап 2: Парсинг UUID в источнике данных и сетевой клиент
- **Файлы**:
  - `app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/network/NightscoutUploadManager.kt` [MODIFY]
- **Детали**:
  - В `parseTreatmentsJson`: считывать `uuid`, `_id`, `sysid`.
  - В `saveTreatmentIfNew`: сохранять `uuid` и обновлять существующие записи, если появился UUID.
  - В `NightscoutUploadManager`:
    - Добавить `deleteTreatment(settings: NightscoutSettings, uuid: String): Result<Unit>`.

### Этап 3: Репозиторий и ViewModel
- **Файлы**:
  - `app/src/main/java/com/tirup/app/domain/repository/GlucoseRepository.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/data/repository/GlucoseRepositoryImpl.kt` [MODIFY]
  - `app/src/main/java/com/tirup/app/presentation/focus/FocusViewModel.kt` [MODIFY]
  - `app/build.gradle.kts` [MODIFY] (версия v2.3.1, versionCode 19)
- **Детали**:
  - `getTreatmentById(id: Long): Treatment?` в репозитории.
  - В `deleteTreatment(id)`: извлекать запись, считывать `uuid`, вызывать `NightscoutUploadManager.deleteTreatment`, удалять из Room и показывать Toast.
  - В `addTreatment`: сохранять возвращаемый `itemUuid` при локальной вставке.

---

## План верификации
1. Юнит-тесты: `.\gradlew.bat testDebugUnitTest`.
2. Сборка релизного APK: `.\gradlew.bat assembleRelease`.
3. Установка на устройство: `adb -s af27386b install -r app/build/outputs/apk/release/TIRUp-v2.3.1-release.apk`.
4. Git commit и отчёт.
