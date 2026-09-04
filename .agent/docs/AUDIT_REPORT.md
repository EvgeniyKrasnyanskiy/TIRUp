# 🩺 TIRUp — Исчерпывающий Аудит Архитектуры и Клинической Безопасности

> **Дата**: 2026-09-05 · **Версия приложения**: 2.0.4 (versionCode 6) · **Аудитор**: AI-ведущий Android-архитектор (Claude Opus 4.6)

---

## Оглавление

- [Раздел 1. Executive Summary](#раздел-1-executive-summary)
- [Раздел 2. Критические находки (CRITICAL)](#раздел-2-критические-находки-critical)
- [Раздел 3. Высокие риски (HIGH)](#раздел-3-высокие-риски-high)
- [Раздел 4. Средние риски (MEDIUM)](#раздел-4-средние-риски-medium)
- [Раздел 5. Низкие / Информационные (LOW / INFO)](#раздел-5-низкие--информационные-low--info)
- [Раздел 6. Валидация клинической математики](#раздел-6-валидация-клинической-математики)
- [Раздел 7. Итоговый вердикт и оценка зрелости](#раздел-7-итоговый-вердикт-и-оценка-зрелости)
- [Раздел 8. ТОП-3 неочевидных уязвимостей с решениями](#раздел-8-топ-3-неочевидных-уязвимостей-с-решениями)

---

## Раздел 1. Executive Summary

| Категория | CRITICAL | HIGH | MEDIUM | LOW/INFO |
|:--|:--:|:--:|:--:|:--:|
| Клиническая безопасность | 1 | 2 | 1 | 1 |
| Архитектура & Lifecycle | 1 | 3 | 2 | — |
| Данные & Storage | 1 | 1 | — | — |
| **Итого** | **3** | **6** | **3** | **1** |

### Ложное заявление об Offline-first

В описании проекта заявлен «100% Offline-first, без интернета». Однако **строка 5 манифеста** содержит:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

А также `android:usesCleartextTraffic="true"` (строка 33). Это прямо противоречит заявлению о приватности и может быть проблемой при сертификации как SaMD.

---

## Раздел 2. Критические находки (CRITICAL)

### 🔴 CRIT-01 · Ложная интерпретация единиц при экстремальной гипогликемии [ИСПРАВЛЕНО]

**Файл**: `DexdripBroadcastReceiver.kt`, строки 70-75

```kotlin
// Convert mg/dL to mmol/L if needed (values > 35 are in mg/dL)
val valueMmol = if (glucoseVal > 35.0) {
    glucoseVal / 18.0182
} else {
    glucoseVal
}
```

**Проблема**: Значения 10.0–35.0 mg/dL — это **тяжелейшая гипогликемия** (0.55–1.94 ммоль/л, кома, смерть). Но код интерпретирует их как mmol/L (10.0–35.0 ммоль/л = тяжёлая **гипер**гликемия), что **инвертирует тревогу**.

**Статус**: ✅ Исправлено в коммите (добавлена проверка метаданных broadcast intent + проверка контекста истории).

---

### 🔴 CRIT-02 · Потеря всех медицинских данных при обновлении [ИСПРАВЛЕНО]

**Файл**: `AppDatabase.kt`, строки 60-73

```kotlin
.addMigrations(MIGRATION_3_4, MIGRATION_4_5)
.fallbackToDestructiveMigration()
```

**Проблема**: Нет миграций 1→2 и 2→3. При обновлении с ранней версии Room не находит путь и вызывает `fallbackToDestructiveMigration()` → полное удаление базы.

**Статус**: ✅ Исправлено в коммите (добавлены MIGRATION_1_2, MIGRATION_2_3, убран destructive fallback).

---

### 🔴 CRIT-03 · OOM-краш при автобэкапе

**Файл**: `AutoBackupManager.kt` (~строка 261)

```kotlin
val readings = database.glucoseReadingDao().getReadingsBetweenSync(0L, Long.MAX_VALUE)
```

**Проблема**: CGM генерирует ~288 записей/день. За 1 год = ~105,000 записей. Загрузка всех в RAM → `OutOfMemoryError`.

**Решение**: Рефакторинг на пагинированный streaming export (по 5000 записей за раз). Требует изменений в DAO + BackupManager — рекомендуется как отдельная задача.

**Статус**: ⏳ Оставлено для исполнителя (требует рефакторинг DAO + JSON writer).

---

## Раздел 3. Высокие риски (HIGH)

### 🟠 HIGH-01 · Hardware-кнопки глушат сирену И отменяют экстренную SMS [ИСПРАВЛЕНО]

**Файл**: `GlucoseAlertManager.kt`, строки 309-317

```kotlin
fun silenceCurrentSoundOnly() {
    cancelEmergencySmsTimer()  // ← ПРОБЛЕМА
    MedicalSoundPlayer.stopAll()
}
```

**Проблема**: Рефлекторное нажатие кнопки громкости отменяет SMS-таймер экстренного оповещения при коме.

**Статус**: ✅ Исправлено в коммите (убран cancelEmergencySmsTimer из silenceCurrentSoundOnly).

---

### 🟠 HIGH-02 · Снуз <2.8 невозможно использовать (alarm fatigue)

**Файл**: `GlucoseAlertManager.kt`, строки 668-673

**Проблема**: `isDroppingDangerously` срабатывает на статическом значении <2.8 без проверки тренда. Пациент после приёма углеводов получает сирену каждые 5 минут.

**Решение**: Добавить проверку тренда — срывать снуз только если сахар продолжает падать.

```kotlin
val isDroppingDangerously = when {
    sorted.size >= 2 && (latest.valueMmol - sorted[sorted.size - 2].valueMmol) <= -0.3 -> true
    latest.valueMmol < 2.8 && sorted.size >= 2 &&
        latest.valueMmol <= sorted[sorted.size - 2].valueMmol -> true
    else -> false
}
```

**Статус**: ⏳ Оставлено для исполнителя.

---

### 🟠 HIGH-03 · FloatingBubbleService не является Foreground Service

**Файл**: `FloatingBubbleService.kt`

**Проблема**: Сервис запускается без `startForeground()`. На Android 8.0+ система убьёт его в течение ~1 минуты.

**Решение**: Промотировать в Foreground Service с ongoing notification и foregroundServiceType для Android 14+.

**Статус**: ⏳ Оставлено для исполнителя.

---

### 🟠 HIGH-04 · SecurityException для Exact Alarms на Android 14+

**Файлы**: `AutoBackupManager.kt`, `GlucoseAlertManager.kt`

**Проблема**: `setExactAndAllowWhileIdle()` без проверки `canScheduleExactAlarms()` → SecurityException на Android 14+.

**Решение**:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
} else {
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
}
```

**Статус**: ⏳ Оставлено для исполнителя.

---

### 🟠 HIGH-05 · ViewModels пересоздаются при повороте экрана

**Файл**: `MainActivity.kt`, строки 126-129

**Проблема**: ViewModel создаётся как обычная переменная, не привязана к `ViewModelStore`.

**Решение**: Использовать `viewModel(factory = ...)` из Compose.

**Статус**: ⏳ Оставлено для исполнителя.

---

### 🟠 HIGH-06 · Разрыв границ TIR между калькулятором и UI

**Файл**: `GlucoseMetricsCalculator.kt`, строки 108-118

**Проблема**: TIR верхняя граница exclusive (`< 180`), нижняя — `>= 70.27` вместо `>= 70.0`. UI использует inclusive. Разные TIR% на экране и в отчёте.

**Решение**:
```kotlin
val inRangeCount = rawMgdl.count { it >= 70.0 && it <= 180.0 }
```

**Статус**: ⏳ Оставлено для исполнителя.

---

## Раздел 4. Средние риски (MEDIUM)

### 🟡 MED-01 · AudioTrack CONTENT_TYPE_SONIFICATION вместо CONTENT_TYPE_ALARM
**Файл**: `MedicalSoundPlayer.kt`, строка 267
**Статус**: ⏳ Оставлено

### 🟡 MED-02 · ANR-риск в BackupAlarmReceiver (goAsync 10s timeout)
**Файл**: `BackupAlarmReceiver.kt`
**Решение**: Делегировать в WorkManager.
**Статус**: ⏳ Оставлено

### 🟡 MED-03 · collectAsState() вместо collectAsStateWithLifecycle()
**Файл**: `MainActivity.kt`
**Статус**: ⏳ Оставлено

---

## Раздел 5. Низкие / Информационные (LOW / INFO)

- **LOW-01**: Конвертационная константа 18.0182 vs стандартная 18.01559
- **INFO-01**: Dawn Phenomenon window 05:30 vs текст 06:00 (`PatternRecognitionEngine.kt`)
- **INFO-02**: DST не учтён в `WeeklyDigestCalculator.kt`

---

## Раздел 6. Валидация клинической математики

| Метрика | Формула в коде | Эталон ATTD/ADA | Статус |
|:--|:--|:--|:--:|
| eA1c (ADAG) | `(mean_mmol + 2.59) / 1.59` | Верно | ✅ |
| GRI | `3.0*(VLow + 0.8*Low) + 1.6*(VHigh + 0.5*High)` | Klonoff 2022 | ✅ |
| %CV | `(SD / Mean) × 100` | Standard | ✅ |
| GVI | xDrip-style | xDrip+ reference | ✅ |
| PGS | `GVI × ⌊mean_mgdl⌋ × (1 − ⌊TIR%⌋/100)` | xDrip+ reference | ✅ |
| AGP перцентили | Linear interpolation | ATTD standard | ✅ |
| Boundary handling | exclusive upper | inclusive по ATTD | ⚠️ HIGH-06 |

---

## Раздел 7. Итоговый вердикт

### Оценка зрелости: **6.5 / 10**

**Сильные стороны**: Клиническая математика, 4-уровневые тревоги, offline-подход, синтезатор звука, AGP/PDF генерация, SMS-протокол.

**Блокеры**: Инверсия единиц (CRIT-01), деструктивная миграция (CRIT-02), OOM бэкапа (CRIT-03), SMS race condition (HIGH-01), Exact Alarms crash (HIGH-04), TIR boundary mismatch (HIGH-06).

---

## Раздел 8. Приоритет исправлений

| Приоритет | ID | Описание | Статус |
|:--:|:--|:--|:--:|
| 1 | CRIT-01 | Инверсия единиц при гипо | ✅ Исправлено |
| 2 | HIGH-01 | SMS-таймер при hardware silence | ✅ Исправлено |
| 3 | CRIT-02 | Миграции БД | ✅ Исправлено |
| 4 | CRIT-03 | OOM бэкап | ⏳ Требует рефакторинг |
| 5 | HIGH-04 | Exact Alarms Android 14+ | ⏳ |
| 6 | HIGH-02 | Alarm fatigue снуза | ⏳ |
| 7 | HIGH-03 | FloatingBubble lifecycle | ⏳ |
| 8 | HIGH-05 | ViewModel recreation | ⏳ |
| 9 | HIGH-06 | TIR boundary mismatch | ⏳ |
| 10 | MED-* | Средние риски | ⏳ |
