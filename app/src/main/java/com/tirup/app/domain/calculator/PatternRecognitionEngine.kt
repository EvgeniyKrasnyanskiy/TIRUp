package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseStatistics

enum class PatternSeverity {
    POSITIVE,
    INFO,
    WARNING,
    ALERT
}

data class DetectedPattern(
    val id: String = "",
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val severity: PatternSeverity,
    val icon: String
)

object PatternRecognitionEngine {

    fun analyze(
        bins: List<AGPPercentileBin>,
        stats: GlucoseStatistics,
        isMmol: Boolean = true
    ): List<DetectedPattern> {
        if (bins.isEmpty() || stats.totalCount < 50) {
            return listOf(
                DetectedPattern(
                    id = "collecting_data",
                    titleRu = "Сбор данных",
                    titleEn = "Collecting Data",
                    descriptionRu = "Недостаточно измерений для выявления устойчивых суточных паттернов. Продолжайте носить датчик.",
                    descriptionEn = "Insufficient readings to detect repeating daily patterns. Continue wearing the sensor.",
                    severity = PatternSeverity.INFO,
                    icon = "📊"
                )
            )
        }

        val patterns = mutableListOf<DetectedPattern>()

        // 1. Ночные провалы (00:00–06:00: minuteOfDay in 0..360)
        val nightBins = bins.filter { it.minuteOfDay in 0..360 && it.readingsCount > 0 }
        val hasNightHypo = nightBins.any { it.p10 < 3.9 || it.p25 < 3.5 }
        if (hasNightHypo) {
            patterns.add(
                DetectedPattern(
                    id = "night_drops",
                    titleRu = "Ночные провалы (00:00–06:00)",
                    titleEn = "Night Drops (00:00–06:00)",
                    descriptionRu = "Обнаружена повторяющаяся склонность к гипогликемии в ночное время. Рекомендуется оценить дозу вечернего базального инсулина или лёгкий перекус перед сном.",
                    descriptionEn = "Repeating nocturnal hypoglycemia tendency detected. Consider reviewing evening basal insulin or having a light bedtime snack.",
                    severity = PatternSeverity.ALERT,
                    icon = "🌙"
                )
            )
        }

        // 2. Феномен «Утренней зари» (05:00–09:00: minuteOfDay in 300..540)
        val earlyNightBins = bins.filter { it.minuteOfDay in 60..240 && it.readingsCount > 0 }
        val dawnBins = bins.filter { it.minuteOfDay in 330..540 && it.readingsCount > 0 }
        val earlyNightMedian = if (earlyNightBins.isNotEmpty()) earlyNightBins.map { it.p50 }.average() else 6.0
        val dawnMedian = if (dawnBins.isNotEmpty()) dawnBins.map { it.p50 }.maxOrNull() ?: 0.0 else 0.0

        if (dawnMedian >= 9.5 && earlyNightMedian in 4.0..7.5) {
            patterns.add(
                DetectedPattern(
                    id = "dawn_phenomenon",
                    titleRu = "Феномен «Утренней зари» (06:00–09:00)",
                    titleEn = "Dawn Phenomenon (06:00–09:00)",
                    descriptionRu = "Регулярный подъём сахара в утренние часы без ночной гипогликемии. Обусловлен естественным выбросом контринсулярных гормонов на рассвете.",
                    descriptionEn = "Regular morning glucose rise without nocturnal hypoglycemia, typically driven by early morning counter-regulatory cortisol and growth hormones.",
                    severity = PatternSeverity.WARNING,
                    icon = "🌅"
                )
            )
        }

        // 3. Постобеденный / вечерний подъём (13:00–21:00: minuteOfDay in 780..1260)
        val postMealBins = bins.filter { it.minuteOfDay in 780..1260 && it.readingsCount > 0 }
        val highPostMealCount = postMealBins.count { it.p75 > 10.5 }
        if (highPostMealCount >= 4) {
            patterns.add(
                DetectedPattern(
                    id = "postprandial_spikes",
                    titleRu = "Постпрандиальные всплески (13:00–21:00)",
                    titleEn = "Postprandial Spikes (13:00–21:00)",
                    descriptionRu = "Повторяющийся выход выше целевых 10.0 ммоль/л после основных приёмов пищи. Может помочь увеличение паузы перед едой или пересмотр углеводного коэффициента.",
                    descriptionEn = "Repeating glucose excursions above 10.0 mmol/L after major meals. Adjusting pre-meal bolus timing or insulin-to-carb ratios may help.",
                    severity = PatternSeverity.WARNING,
                    icon = "🍽️"
                )
            )
        }

        // 4. Дневная лабильность (высокий размах p90 - p10 > 6.0 ммоль/л)
        val maxSpreadBin = bins.maxByOrNull { it.p90 - it.p10 }
        if (maxSpreadBin != null && (maxSpreadBin.p90 - maxSpreadBin.p10) >= 6.0 && stats.cvPercent > 36.0) {
            patterns.add(
                DetectedPattern(
                    id = "high_fluctuation",
                    titleRu = "Высокая амплитуда колебаний",
                    titleEn = "High Glucose Fluctuation",
                    descriptionRu = "В районе ${maxSpreadBin.formattedTime} наблюдается широкий разброс сахаров между днями. График показывает повышенную чувствительность к углеводам и физической активности.",
                    descriptionEn = "Wide day-to-day spread observed around ${maxSpreadBin.formattedTime}. High variability (%CV > 36%) suggests inconsistent carbohydrate absorption or activity shifts.",
                    severity = PatternSeverity.WARNING,
                    icon = "⚡"
                )
            )
        }

        // 5. Если декомпенсаций нет — фиксируем позитивный паттерн
        if (patterns.isEmpty()) {
            patterns.add(
                DetectedPattern(
                    id = "stable_profile",
                    titleRu = "Стабильный профиль без паттернов декомпенсации",
                    titleEn = "Stable Profile — No Adverse Patterns",
                    descriptionRu = "Повторяющихся ночных гипогликемий, всплесков «зари» и выраженной вариабельности не выявлено. Профиль предсказуем и сбалансирован.",
                    descriptionEn = "No recurring nocturnal hypoglycemia, dawn spikes, or high-variability clusters detected. Glucose trajectory is stable and predictable.",
                    severity = PatternSeverity.POSITIVE,
                    icon = "✔"
                )
            )
        }

        return patterns
    }
}
