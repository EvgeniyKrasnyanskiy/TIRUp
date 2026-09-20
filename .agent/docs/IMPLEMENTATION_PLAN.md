# Итерация 23: Гибридный опрос локального Pebble/HTTP сервиса xDrip (127.0.0.1:17580)

## Проблема
xDrip+ в режиме фолловера (Desert Sync / xDrip Cloud) обновляет базу данных и локальный веб-сервер, но подавляет генерацию Android Intents (`com.eveningoutpost.dexdrip.BgEstimate`). Сторонние виджеты успешно работают через прямой опрос `http://127.0.0.1:17580/pebble`. TIRUp исторически зависел только от броадкастов, поэтому оставался без данных.

## План изменений (4 файла)

1. **`DexdripBroadcastReceiver.kt`**:
   - Вынести сохранение замера и запуск алертов/виджетов в общий метод `persistAndDistributeReading(...)`.
   - Добавить companion-метод `syncFromLocalXdrip(context: Context, force: Boolean = false)`:
     - Опрос `http://127.0.0.1:17580/pebble` и `http://127.0.0.1:17580/sgv.json?count=24`.
     - Извлечение сахара (`sgv`), стрелки тренда (`direction`), времени (`datetime`), активного инсулина (`iob`) и углеводов (`cob`).
     - Сохранение в базу данных с защитой от дубликатов (`timestamp == latest.timestamp` или разница < 10 сек).
     - Бэкфилл пропущенных точек за последние 2 часа из `sgv.json`.
     - Запуск `syncIobCobFromPebble` для синхронизации болюсов и заметок канюли/сенсора.

2. **`FocusViewModel.kt`**:
   - При старте и каждые 30 секунд в фоне активного экрана вызывать `DexdripBroadcastReceiver.syncFromLocalXdrip(context)`.

3. **`MainActivity.kt`**:
   - В `onResume()` вызывать `DexdripBroadcastReceiver.syncFromLocalXdrip(this@MainActivity)`.

4. **`StalenessAlarmReceiver.kt`**:
   - При срабатывании алерта устаревания данных выполнять опрос `syncFromLocalXdrip(context)`, чтобы при наличии свежих данных в xDrip мгновенно обновить сахара вместо отображения устаревшего статуса.

## Верификация
1. Проверка сборки проекта (`./gradlew assembleDebug`).
2. Установка на подключенное устройство `af27386b` (OnePlus CPH2653).
3. Проверка получения актуального сахара (10.4 ммоль/л, Flat, IoB 2.87 Ед) на главном экране и виджетах.
