# План калибровки расчётов по эталону DiaKiaBot и исправления PDF

## 1. Точная математика расчёта метрик (как в `glycemia_processor.py`)
- **Проблема**: В TIRUp расчёт метрик выполнялся на ресемплированном массиве `clean5MinReadings` (усреднение по 5-минутным корзинам), что сглаживало пики, искажало Mean BG, SD, CV, eA1c, GVI, PGS, GRI и количество измерений.
- **Решение в [`GlucoseMetricsCalculator.kt`](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/domain/calculator/GlucoseMetricsCalculator.kt)**:
  - Исключить деструктивное усреднение корзин: рассчитывать Mean, Median, SD, CV, eA1c, TIR, TBR, TAR, GVI, PGS, GRI непосредственно на сырых валидных точках (`UDT_CGMS_mgdl > 38.0`).
  - Применить точные пороговые значения в мг/дл:
    - Нижняя граница целевого диапазона (TIR Low / TING Low): `3.9 * 18.0182 = 70.27` мг/дл.
    - Верхняя граница TIR High: `180.0` мг/дл (строго `< 180.0`).
    - Верхняя граница TING High: `140.0` мг/дл (строго `< 140.0`).
    - Очень низкий TBR (<3.0): `54.0` мг/дл.
    - Очень высокий TAR (>13.9): `250.0` мг/дл (`>= 250.0`).
    - GRI компоненты: Low `[54.0, 68.47)` мг/дл, High `[181.88, 250.0)` мг/дл.
  - PGS: `GVI * floor(mean_mgdl) * (1 - int(TIR)/100)`.
  - Целочисленный баланс диапазонов для сводки: `TIR = 100 - round(TBR) - round(TAR)`.

## 2. Импорт исторических файлов и автоопределение единиц
- **Проблема в [`StreamingGlucoseImporter.kt`](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/data/importer/StreamingGlucoseImporter.kt)**:
  - Округление меток времени `roundedTs = (ts / 300000L) * 300000L` приводило к схлопыванию близких точек из-за первичного ключа `timestamp`.
  - Эвристика определения единиц должна соответствовать `is_probably_mmol` (`value <= 30.0`).
- **Решение**:
  - В `HistoricalReadingEntity.kt` добавить автогенерируемый ключ `id`, сохранив точный `timestamp`.
  - В `StreamingGlucoseImporter.kt` сохранять точный `timestamp` каждого измерения и правильно определять единицы (ммоль/л vs мг/дл).

## 3. Генератор PDF: двухколоночная «Клиническая оценка»
- **Проблема в [`AgpPdfGenerator.kt`](file:///d:/Users/physicist/Desktop/ken/TIRUp/app/src/main/java/com/tirup/app/presentation/reports/AgpPdfGenerator.kt)**:
  - 14 строк метрик в один столбец вылезали вниз страницы и накладывались на подвал (footer).
- **Решение**:
  - Расположить 14 параметров в **2 столбца** (по 7 параметров в левом и правом столбцах).
  - Вывод заключения и рекомендаций разместить под двухколоночным блоком с достаточным отступом от подвала.
