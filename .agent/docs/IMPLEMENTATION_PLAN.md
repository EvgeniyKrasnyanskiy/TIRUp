# План реализации: Линия прогноза на суточном графике (25 минут)

## 1. Задачи для реализации

1. [Выполнено] **Математика генерации прогнозных точек на 25 минут** (`GlucoseTrendPredictor.kt`):
   - Добавлена модель `ForecastPoint(timeMillis, glucoseMmol, deltaMinutes)`.
   - Добавлена функция `generateForecastPoints(readings: List<GlucoseReading>, horizonMinutes: Int = 25, stepMinutes: Int = 5): List<ForecastPoint>`.
   - Проверка свежести данных (не старше 15 минут), минимальное кол-во точек (5 шт).
   - Линейная регрессия за последние 30 минут с экспоненциальным затуханием наклона (damping factor от 1.0 до 0.4) и ограничением значений 1.5–25.0 ммоль/л.

2. [Выполнено] **Настройка в UserSettings и SettingsScreen** (`UserSettings.kt`, `SettingsRepositoryImpl.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`):
   - Добавлено поле `showPredictionOnChart: Boolean = true` в `UserSettings`.
   - Реализовано сохранение и загрузка в `SettingsRepositoryImpl` и `SettingsViewModel`.
   - В `SettingsScreen` добавлен тумблер: «Линия прогноза на графике (25 мин)».
   - Проброшен флаг через `FocusScreen` в `DailyGlucoseChart`.

3. [Выполнено] **Отрисовка прогноза на суточном графике** (`DailyGlucoseChart.kt`):
   - При `showPrediction = true` рассчитываются прогнозные точки от последнего замера.
   - Отрисовываются светящиеся фиолетовые точки (`#A855F7`) на отметках `+5м`, `+10м`, `+15м`, `+20м`, `+25м`.
   - Точки соединены пунктирной фиолетовой линией (`DashPathEffect(floatArrayOf(8f, 8f), 0f)`).
   - Реализован тап по точкам прогноза с отображением инспектор-баннера: `🔮 Прогноз HH:mm (+Xм): ~X.X ммоль/л`.

4. [Выполнено] **Обновление документации** (`UserManualPdfGenerator.kt`, `README.md`):
   - В `UserManualPdfGenerator.kt` добавлено описание фиолетовой пунктирной линии прогноза на 25 минут.
   - В `README.md` добавлен раздел 5 с описанием фичи, математики затухания и визуализации.

