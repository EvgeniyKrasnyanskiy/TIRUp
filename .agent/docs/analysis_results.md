# Ревью UserManualPdfGenerator.kt

## 1. Код: обнаруженные проблемы

### 🔴 Критические (могут привести к визуальному дефекту)

| # | Проблема | Строка | Описание |
|---|----------|--------|----------|
| 1 | **Мёртвый код в `drawBulletPoint`** | [162](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L162) | `val limit = if (isFirst) maxWidth - 14f else maxWidth - 14f` — условие `isFirst` бессмысленно: обе ветки идентичны. Изначально, вероятно, планировалось для первой строки давать меньше места (из-за маркера «•»), но сейчас обе ветки дают одинаковый `maxWidth - 14f`. |
| 2 | **Переполнение callout box** | [222](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L222) | Если текст не помещается в заданную фиксированную `height`, он обрезается через `break`. Русские тексты значительно длиннее, а `height` задаётся как `44f` по умолчанию — **страницы 2, 3, 4 и 5 передают `height` по умолчанию (44px)**, чего может быть недостаточно для длинных русских текстов, особенно на странице 2 (чеклист перед ночью) и странице 3 (протокол купирования). |
| 3 | **Возможное переполнение страницы** | Все страницы | Нет проверки на вылет `y` за нижнюю границу страницы (812px — футер). Если текст длиннее, чем расчёт, он наложится на футер или обрежется. Особенно критично для стр. 1 и 2, где много контента. |

### 🟡 Средние

| # | Проблема | Строка | Описание |
|---|----------|--------|----------|
| 4 | **Одинаковый footer для RU и EN** | [112-113](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L112-L113) | Обе ветки `if (isRu)` / `else` содержат идентичный текст `"TIRUp v2.2.0 • Zero-Lag Architecture • 100% Offline Medical Companion"`. Для русской версии стоит перевести. |
| 5 | **Жёстко зашитая версия** | [112](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L112) | `v2.2.0` захардкожена. При обновлении версии в `build.gradle.kts` мануал будет показывать устаревшую. Лучше брать из `BuildConfig.VERSION_NAME`. |
| 6 | **Нет bold для заголовков bullet'ов** | [151-176](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L151-L176) | `drawBulletPoint` рисует и title, и description одним и тем же `bodyPaint`. Заявленная переменная `titlePrefix` никак не выделена визуально — весь текст рисуется обычным шрифтом. Логично рисовать title болдом через `bulletPaint`, а desc обычным. |

### 🟢 Мелкие

| # | Проблема | Строка |
|---|----------|--------|
| 7 | `drawParagraph` всегда рисует с x=36f, игнорируя bullet-отступ — если вызвать из контекста с отступом, текст будет на неправильной позиции. Сейчас не вызывается с отступом, но метод не гибкий. | [130](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/UserManualPdfGenerator.kt#L130) |

---

## 2. Контент: соответствие реальному коду

### ✅ Корректно описано

| Функция | Вердикт | Подтверждение |
|---------|---------|---------------|
| 4-уровневая система тревог (PREDICTIVE, MAIN, CRITICAL, SIGNAL_LOSS) | ✅ Точно | [AlertTier enum](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L53-L58) |
| Критические пороги <3.0 и >13.9 | ✅ Точно | [AlertSettings](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/model/AlertSettings.kt#L31-L32) |
| Адаптивные точки: 3 для 5-мин, 5 для 1-мин | ✅ Точно | [Adaptive points](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L1346) |
| Coma Guard при <2.8 ммоль/л | ✅ Точно | [GlucoseAlertManager:1221](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/GlucoseAlertManager.kt#L1221) |
| Patient Rescue Screen (полноэкранный) | ✅ Точно | [PatientCriticalHypoActivity](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/alert/PatientCriticalHypoActivity.kt) |
| Caregiver SOS Activity | ✅ Точно | [CaregiverSosActivity](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/alert/CaregiverSosActivity.kt) |
| Emergency SMS с GPS | ✅ Точно | [EmergencySmsManager](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/alert/EmergencySmsManager.kt) |
| BLE Bridge (Broadcaster + Observer) | ✅ Точно | [BleBroadcaster](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleBroadcaster.kt), [BleObserverManager](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/ble/BleObserverManager.kt) |
| Виджеты рабочего стола | ✅ Есть | [TirupWidgetProviders](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/widget/TirupWidgetProviders.kt) |
| 5 тапов для dev-mode | ✅ Точно | [SettingsScreen:3558](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/settings/SettingsScreen.kt#L3558) |
| Версия v2.2.0 | ✅ Совпадает | [build.gradle.kts:43](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/build.gradle.kts#L43) |
| SMS задержка 3 мин по умолчанию | ✅ Точно | [AlertSettings:54](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/model/AlertSettings.kt#L54) |
| Signal Loss >20 мин | ✅ Точно | [AlertSettings:36](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/model/AlertSettings.kt#L36) |

### ⚠️ Неточности в контенте

| # | Место в мануале | Проблема |
|---|----------------|----------|
| 1 | **Стр. 3, Tier 2** (строка 389) | Написано «требуется подтверждение 3 точек подряд (для 5-мин датчиков) или 5 точек (для 1-мин)». В коде настройка по умолчанию = 5, а для 5-мин датчиков `minOf(5, 3) = 3`. Формулировка корректна по сути, но может запутать: пользователь настраивает «5 точек», а для 5-мин датчиков система **автоматически ограничивает до 3**. Стоит уточнить, что это адаптивная логика, а не ручная настройка. |
| 2 | **Стр. 3, Tier 1** (строка 383) | Написано «за последние 30 минут», а `predictiveMinutesAhead = 15`. Нужно проверить алгоритм предикции — окно анализа (30 мин) и горизонт предсказания (25 мин вперёд) могут не совпадать с описанным. |
| 3 | **Стр. 2, чеклист** (строка 355) | «В самом низу 5 быстрых тапов по **номеру версии**» — из кода видно, что тап идёт по текстовому элементу внизу настроек, но это может быть не сам «номер версии», а другой элемент. Стоит проверить конкретный UI-элемент. |

---

## 3. Полнота и понятность для пользователей

### ✅ Сильные стороны

- **Профессиональная структура**: 6 глав логично покрывают весь user journey от настройки связи до ежедневного использования
- **Пошаговые OEM-инструкции**: Отличные чеклисты для Samsung, Xiaomi, BBK — реально полезно для пользователей
- **Клинические стандарты**: Упоминание ATTD/ADA, Правила 15 — повышает доверие
- **Билингвальность**: Полная поддержка RU/EN — отлично для международной аудитории
- **Callout boxes**: Удачное использование INFO/TIP/WARNING/CRITICAL визуальных акцентов

### ⚠️ Что стоит добавить/улучшить

| # | Рекомендация | Приоритет |
|---|-------------|-----------|
| 1 | **Оглавление (Table of Contents)** — 6 страниц без оглавления затруднят навигацию | Высокий |
| 2 | **Раздел «Быстрый старт»** — сейчас сразу погружение в архитектуру BLE, а новому пользователю нужен 5-минутный гайд | Высокий |
| 3 | **Скриншоты/иллюстрации** — чисто текстовый PDF тяжело воспринимается, хотя бы схема экрана спасения была бы ценна | Средний |
| 4 | **Huawei/Honor (EMUI)** — отсутствует крупный вендор с агрессивным энергосбережением | Средний |
| 5 | **Google Pixel** — упомянуть «Адаптивный расход батареи» | Низкий |
| 6 | **Ночное расписание тревог** — Tier 4 упоминает sleep window, но нет инструкции, как его настроить | Средний |
| 7 | **FAQ / Troubleshooting** — типичные вопросы: «Почему тревога не сработала?», «Почему виджет не обновляется?» | Средний |

---

## Резюме

> **Код**: 3 критических бага (мёртвый код в `drawBulletPoint`, потенциальное обрезание callout'ов, отсутствие проверки переполнения страницы) + 3 средних (футер без перевода, хардкод версии, отсутствие bold для заголовков пунктов).
>
> **Контент**: Описание функциональности в целом **точно соответствует коду**. Все ключевые фичи (4 тира тревог, BLE-мост, экстренные SMS, экран спасения, Coma Guard, виджеты) подтверждены в реальном коде. 2-3 мелких неточности в формулировках.
>
> **Полнота**: Для опытного пользователя — **отлично**. Для нового пользователя не хватает раздела «Быстрый старт» и оглавления.
