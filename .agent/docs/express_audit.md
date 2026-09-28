# 🔍 Экспресс-аудит TIRUp v2.4.0 — Критичные зоны

> **Методология**: Из ~44 000 строк кода проверены **8 наиболее опасных модулей** (~5 000 строк), где ошибка может привести к угрозе жизни пациента, утечке данных или молчаливой деградации мониторинга.

---

## 🚨 Уровень 1: КРИТИЧНЫЕ (угроза жизни)

### 1.1 `GlucoseAlertManager.checkAndAlert()` — Сердце системы безопасности
**Файл**: [GlucoseAlertManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L1289-L1638)
**Метрика**: cognitive complexity = **240** (!!), 350 строк монолит

#### ✅ Что сделано хорошо
- Чёткая 4-уровневая иерархия тревог (Predictive → Main → Critical → Signal Loss)
- Smart Adaptive Snooze: разные стратегии для гипо (15 мин) и гипер (30-45 мин с учётом IoB)
- Safety override: пробивает снуз при `delta ≤ -0.3` или при `glucose < 2.8` без роста
- Гиппократовский приоритет: **гипо-защита работает даже при выключенном мастер-переключателе** (строка 1301-1306)
- `isHypoProtectionActive` проверяет `criticalHypoPauseUntilTimestamp` — т.е. даже при паузе гипо через 2ч восстановится

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 1 | **Auto-dismiss при активном использовании телефона** — строка 1309-1311: `if (isCriticalAlarmActive && isPhoneInActiveUse(context)) dismissCriticalAlarm(fromUser=true)`. Пациент может случайно разблокировать телефон в панике, и это **отменит SOS-таймер** (`cancelEmergencySmsTimer()` вызывается внутри `dismissCriticalAlarm`). Если пациент теряет сознание через секунду после разблокировки — SOS не уйдёт. | 🔴 **Высокий** | [L1309-1311](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L1309-L1311) |
| 2 | **Гонка состояний на `@Volatile` полях** — `lastHypoAlertTimestamp`, `userAcknowledgedHypoTimestamp` и т.д. используют `@Volatile` без синхронизации. Два одновременных вызова `checkAndAlert()` (например, от BLE + Broadcast одновременно) могут привести к пропуску тревоги или двойному срабатыванию. | 🟡 **Средний** | [L288-318](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L288-L318) |
| 3 | **`return` после Signal Loss пропускает все остальные тревоги** — строка 1324-1326: если `checkSignalLoss` возвращает true, вся функция выходит. Но если последний замер был **критически низким** (2.5 ммоль/л) 21 минуту назад, гипо-тревога не сработает, хотя она приоритетнее. | 🟡 **Средний** | [L1324-1326](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L1324-L1326) |

---

### 1.2 `EmergencySmsManager` — SOS-отправка близким
**Файл**: [EmergencySmsManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/EmergencySmsManager.kt)

#### ✅ Что сделано хорошо
- 30-минутный антиспам-кулдаун (`COOLDOWN_MILLIS = 30 * 60 * 1000L`)
- Проверка разрешения `SEND_SMS` перед отправкой
- Поддержка двух контактов (`phone1`, `phone2`)
- `sendMultipartTextMessage` для длинных SMS

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 4 | **`updateLastSentTimestamp` в fire-and-forget корутине** — строка 222-236: если `CoroutineScope(Dispatchers.IO)` бросает исключение при записи timestamp, то кулдаун не будет записан, и следующий `checkAndAlert` через 5 минут может **повторно отправить SOS**. Учитывая 30-минутный кулдаун, проблема самолечится, но при сбое записи может быть 2 SOS за 5 минут. | 🟡 **Средний** | [L222-236](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/EmergencySmsManager.kt#L222-L236) |
| 5 | **GPS fallback только `getLastKnownLocation`** — при отсутствии кэшированной локации (GPS выключен, нет недавних фиксов) координаты будут `null`. Для SOS это может быть критично. Нет попытки запросить свежую фиксацию через `requestSingleUpdate`. | 🟡 **Средний** | [L190-219](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/EmergencySmsManager.kt#L190-L219) |
| 6 | **Нет `SentIntent` / `DeliveryIntent`** — `sendTextMessage(phone, null, message, null, null)`: приложение не узнает, доставлено ли SMS. Для экстренного SOS это критично — нет fallback-стратегии (повтор, другой канал). | 🟡 **Средний** | [L174-188](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/EmergencySmsManager.kt#L174-L188) |

---

### 1.3 `scheduleEmergencySmsIfEnabled()` — Таймер SOS
**Файл**: [GlucoseAlertManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L861-L903)

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 7 | **`CoroutineScope(Dispatchers.IO)` вместо процесс-привязанного scope** — строка 885: если приложение будет убито Android'ом (OOM, Doze) во время обратного отсчёта SOS, `emergencySmsJob` исчезнет. Для 3-5 минутного таймера SOS это **потенциально смертельно**. Нужен `AlarmManager.setExactAndAllowWhileIdle()`. | 🔴 **Высокий** | [L885-902](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L885-L902) |
| 8 | **Значение `glucoseValue` «заморожено» при старте таймера** — если сахар за 5 минут вырос (пациент принял углеводы и пришёл в себя), SOS всё равно отправится с устаревшим значением. Нужна повторная проверка актуального уровня перед отправкой. | 🟡 **Средний** | [L887-898](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L887-L898) |

---

## 🟠 Уровень 2: ВАЖНЫЕ (деградация мониторинга)

### 2.1 `BleObserverManager` — BLE-мост 24/7
**Файл**: [BleObserverManager.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt)

#### ✅ Что сделано хорошо
- **Mutex-защита** всех операций start/stop/restart (`mutex.withLock`)
- **60-секундный anti-spam cooldown** для рестартов (защита от AOSP 5/30s троттлинга)
- **Проактивный сброс** каждые 27 минут (до AOSP 30-мин лимита)
- **Реактивный watchdog** — перезапуск при тишине >6 минут
- **WakeLock** во время рестартов (15 сек макс, не-рефсчётный)
- **Fail-safe откат** с Coded PHY на Legacy 1M при `SCAN_FAILED_FEATURE_UNSUPPORTED`
- **Manufacturer ScanFilter** с magic bytes `'TU'` — аппаратная фильтрация для работы при выключенном экране

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 9 | **`boostScanFor60Sec` создаёт корутину без mutex** — строка 198-207: `boostJob` запускается в `scope.launch` и через 60 сек вызывает `stopScanningInternal()` → `startScanningInternal()`. Но `boostScanFor60Sec` сама уже внутри `scope.launch`. Если `boostScanFor60Sec` вызывается дважды за 60 сек (двойной тап), два boostJob будут конкурировать. Первый отменяется (`boostJob?.cancel()`), но остаётся окно гонки. | 🟡 **Низкий** | [L198-207](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt#L198-L207) |
| 10 | **`insertReading` без приоритета источника** — строка 622: `glucoseRepository.insertReading(newReading)` вызывает `insertReadingFromSource(…, LOCAL_XDRIP)`, а должен быть `BLE_BRIDGE` для корректной дедупликации. BLE-мост всегда пишется как `LOCAL_XDRIP` (приоритет 4), хотя по матрице должен быть 3. | 🟡 **Средний** | [L622](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt#L622) |

---

### 2.2 `GlucoseRepositoryImpl.insertReadingFromSource()` — Дедупликация
**Файл**: [GlucoseRepositoryImpl.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/repository/GlucoseRepositoryImpl.kt#L88-L115)

#### ✅ Что сделано хорошо
- 25-секундное окно дедупликации (сбалансировано для 1-мин сенсоров)
- Обогащение полей (IoB, CoB, trendArrow) без перезаписи значения глюкозы

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 11 | **Окно 25 сек может пропустить дубли от Wi-Fi LAN** — Wi-Fi LAN poller и Broadcast могут приходить с разницей >25 сек (если сеть медленная или broadcast задержался). В этом случае одна минутная точка будет записана дважды, искажая TIR/CV. | 🟡 **Низкий** | [L92](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/repository/GlucoseRepositoryImpl.kt#L92) |
| 12 | **`shouldUpdateValue` проверяет только `>= WIFI_LAN.rank`** — BLE-мост (который должен быть rank 3) пишется как `LOCAL_XDRIP` (rank 4) из-за бага #10 выше. Это значит, что BLE-данные могут перезаписать WiFi-данные, нарушая матрицу приоритетов. | 🟡 **Средний** | [L103](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/repository/GlucoseRepositoryImpl.kt#L103) |

---

### 2.3 `DexdripBroadcastReceiver` — Основной приёмник данных
**Файл**: [DexdripBroadcastReceiver.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt)

#### ✅ Что сделано хорошо
- **Безопасная конвертация единиц** (строки 86-114): IEC 62304-совместимая логика, не полагается только на порог >35 — проверяет ключи, action, metadata
- Дедупликация echo-broadcast'ов в окне 4 сек
- Грамотный fallback: extras → JSON → statusLine → кэш → DB

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 13 | **Кэш IoB/CoB shared через `@Volatile` в companion** — значения `cachedIob`, `cachedCob` — глобальные volatile переменные. Два одновременных broadcast'а (от xDrip и GDH) могут конкурентно перезаписать кэш. При гонке `cachedIob` может быть от одного источника, а `cachedCob` от другого с разными timestamp'ами. | 🟡 **Низкий** | [L582-589](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt#L582-L589) |
| 14 | **`scope = CoroutineScope(SupervisorJob())` в инстансе BroadcastReceiver** — строка 21: Android может создавать новый инстанс `DexdripBroadcastReceiver` для каждого broadcast'а. `scope` как поле инстанса, а не companion, создаёт утечку Job'ов. Однако `goAsync()` + `pendingResult.finish()` спасает от реальных утечек — Android убьёт процесс после `finish()`. | 🟡 **Низкий** | [L21](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/DexdripBroadcastReceiver.kt#L21) |

---

## 🟡 Уровень 3: ЗАМЕТНЫЕ (безопасность / UX)

### 3.1 `SmsQueryReceiver` — Входящие SMS от фоловера
**Файл**: [SmsQueryReceiver.kt](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/SmsQueryReceiver.kt)

#### ✅ Что сделано хорошо
- Белый список по последним 10 цифрам номера
- 60-секундный anti-loop кулдаун
- SOS-сообщения проверяются отдельно от query-запросов

#### ⚠️ Потенциальные проблемы

| # | Проблема | Риск | Строки |
|---|----------|------|--------|
| 15 | **`isQueryTrigger` слишком агрессивный** — `clean.contains("сахар")` и `clean.contains("sugar")` означают, что **любое** SMS от доверенного контакта содержащее слово «сахар» (напр. «купи сахар в магазине») вызовет автоответ. Это скорее UX-проблема, но может запутать пользователя. | 🟡 **Низкий** | [L227-246](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/SmsQueryReceiver.kt#L227-L246) |
| 16 | **SOS-парсинг перехватывает SMS до query-проверки** — строка 53-73: если `isSosMessage` ошибочно распознаёт обычное SMS как SOS (ложноположительное), это вызовет сирену у фоловера. Необходимо убедиться, что `SosSmsParser.isSosMessage()` достаточно строгий. | 🟡 **Средний** | [L53-73](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/receiver/SmsQueryReceiver.kt#L53-L73) |

---

## 📊 Сводка находок

| Уровень | Кол-во | Описание |
|---------|--------|----------|
| 🔴 Критичный | **2** | SOS-таймер на корутине (может быть убит OOM), auto-dismiss при разблокировке |
| 🟡 Средний | **8** | Гонки на volatile, Signal Loss блокирует гипо-тревогу, BLE пишет неверный приоритет, нет delivery-подтверждения SMS |
| 🟢 Низкий | **6** | Кэш IoB/CoB без синхронизации, агрессивный query-trigger, scope в инстансе receiver'а |

---

## 💡 Рекомендации к действию (по приоритету)

### Немедленно (P0)
1. **SOS-таймер → AlarmManager**: Заменить `CoroutineScope(Dispatchers.IO).launch { delay(delayMillis) }` на `AlarmManager.setExactAndAllowWhileIdle()` для гарантированной доставки SOS даже если процесс убит.
2. **Auto-dismiss → добавить guard**: Не отменять SOS-таймер при `isPhoneInActiveUse` если `glucose < criticalLowThreshold`. Пациент в панике может случайно разблокировать телефон.

### Скоро (P1)
3. **BLE-мост → `insertReadingFromSource(…, BLE_BRIDGE)`**: Исправить приоритет источника данных.
4. **Signal Loss → не блокировать гипо**: Если последний замер был критически низким, тревога гипо приоритетнее signal loss.
5. **SMS → `SentIntent`/`DeliveryIntent`**: Добавить подтверждение доставки и повторную попытку для экстренных SOS.

### Планово (P2)
6. **`checkAndAlert()` → разбить на подфункции**: Cognitive complexity 240 — серьёзный риск регрессий при любом изменении.
7. **`@Volatile` → Mutex или AtomicReference**: Для полей снуз-таймстампов в `GlucoseAlertManager`.

> [!IMPORTANT]
> Несмотря на найденные проблемы, общее качество кода **высокое для медицинского приложения уровня DIY-сообщества**. Система тревог продумана, гипо-защита работает независимо от master-switch'а, дедупликация корректна для типовых сценариев. Самые критичные находки (#1, #7) — это edge cases при экстремальных условиях (OOM kill во время SOS).
