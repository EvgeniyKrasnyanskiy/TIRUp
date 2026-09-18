# План: Исправление Caregiver SOS теста, телеметрия BLE-моста и аудит нововведений

## 1. Ответы на вопросы и аудит

### 1.1. Клиническое поведение по «Правилу 15» (Patient Rescue Screen)
- **Вопрос:** Сработает ли тревога снова, если сахар по сенсору всё ещё ниже порога по истечении 15 минут?
- **Ответ:** **ДА, обязательно.** В [GlucoseAlertManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt) уже реализована клиническая модель:
  1. При нажатии гигантской кнопки «Принял 15г углеводов» текущая сирена, вспышка и вибрация отключаются, таймер экстренного SMS отменяется (`dismissCriticalAlarm(fromUser = true)`), и фиксируется время подтверждения (`userAcknowledgedHypoTimestamp = now`).
  2. Включается 15-минутный клинический снуз (`snoozeHypoMinutes = 15`). Если за это время сахар стремительно падает ($\le -0.3$ ммоль/мин) или $< 2.8$ и не растёт, система прерывает снуз досрочно.
  3. Если пациент принял углеводы, прошло 15 минут, и на очередном замере сенсора сахар **всё ещё ниже критического порога** ($< 3.0$ или настроенного), условие `shouldTriggerHypo` возвращает `true`:
     - Снова включается сирена на 100% громкости, вибрация и стробоскоп.
     - Полноэкранное окно спасения ([PatientCriticalHypoActivity.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/alert/PatientCriticalHypoActivity.kt)) снова всплывает поверх блокировки с актуальным сахаром и кнопкой углеводов.
     - Заново запускается обратный отсчёт экстренного SMS опекунам (5 минут).

### 1.2. Баг теста SOS для опекуна (экран тревоги не запустился)
- **Причина:** В предыдущем коммите тег `[ТЕСТ]` был помещён перед `SOS!`: `"[ТЕСТ] SOS! Ваня - критич. гипо..."`.
- Приёмник SMS на телефоне опекуна ([SosSmsParser.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/alert/SosSmsParser.kt)) строго проверяет:
  `if (!trimmed.startsWith("SOS!", ignoreCase = true)) return false`.
- Поскольку сообщение начиналось с символа `[`, парсер не признал его за SOS и проигнорировал. SMS поступило в обычный мессенджер, но [CaregiverSosAlarmManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/CaregiverSosAlarmManager.kt) не сработал.

### 1.3. Телеметрия сахара в тесте дальности BLE-моста
- **Вопрос:** Для сахара берутся реальные значения? А если их ещё нет?
- **Ответ:**
  - **Реальные значения:** ДА, если сенсор активен или в БД есть замеры, вещатель транслирует реальный сахар, стрелку, активный инсулин и батарею.
  - **Если замеров ещё нет:** Ранее стояла заглушка 6.0 ммоль/л. В ходе аудита обнаружено, что приёмник ([BleObserverManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt)) мог сохранить эту фиктивную точку в свою базу Room!
  - **Решение:** При отсутствии замеров вещатель передаёт маркер `0.0`. Приёмник не сохраняет его в БД, а на экране выводит: `"🟢 BLE: 📡 Тест связи (нет данных сенсора) (RSSI: -65 dBm)"`.

---

## 2. Предлагаемые изменения

### 2.1. Исправление формата SMS и парсера
- **[EmergencySmsBuilder.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/alert/EmergencySmsBuilder.kt)**:
  - Тег `[ТЕСТ]` ставится после `SOS! `: `"SOS! $testPrefix$name - критич. гипо: ..."` -> `"SOS! [ТЕСТ] Ваня..."`.
- **[SosSmsParser.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/alert/SosSmsParser.kt)**:
  - Очистка любых тегов в скобках перед проверкой `startsWith("SOS!")` и в regex имени для гарантированного распознавания обоих форматов (`"SOS! [ТЕСТ]..."` и `"[ТЕСТ] SOS!..."`).
- **[SosSmsParserTest.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/test/java/com/tirup/app/domain/alert/SosSmsParserTest.kt)**:
  - Добавить тесты на оба варианта.

### 2.2. Защита от фиктивного сахара в BLE-мосте
- **[BleBroadcaster.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleBroadcaster.kt)**:
  - Если `reading == null`, передавать маркер `valueMmol = 0.0` и `timestamp = 0L`.
- **[BleObserverManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt)**:
  - Игнорировать сохранение в Room DB при `packet.valueMmol <= 0.1` или `packet.timestamp == 0L`.
- **[SettingsScreen.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt)**:
  - Показывать баннер `"$signalDot BLE: 📡 Тест связи (нет данных сенсора) (RSSI: $rssi dBm)"` при получении тестового пакета без данных.

### 2.3. Локальный тест экрана опекуна
- В блоке «Тестирование систем» сделать кнопку проверки сирены опекуна полноценной: не только звук, но и показ тестового экрана [CaregiverSosActivity.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/alert/CaregiverSosActivity.kt) на 3 секунды для наглядной проверки прямо на своём телефоне.

---

## 3. План верификации
- Запуск тестов `./gradlew testDebugUnitTest`
- Сборка релизного APK `./gradlew assembleRelease`
- Установка на телефон `af27386b`
