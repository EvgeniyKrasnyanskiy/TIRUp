package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseStatistics

data class TirInsight(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val adviceRu: String,
    val adviceEn: String,
    val badgeRu: String,
    val badgeEn: String,
    val icon: String = "💡",
    val priority: Int
)

object TirAdvisorEngine {

    /**
     * Generates 1-2 prioritized, actionable behavioral insights to improve Time in Range.
     */
    fun generateInsights(
        bins: List<AGPPercentileBin>,
        stats: GlucoseStatistics,
        maxCount: Int = 2
    ): List<TirInsight> {
        if (stats.totalCount < 30) {
            return listOf(
                TirInsight(
                    id = "collect_more_data",
                    titleRu = "Накопление данных",
                    titleEn = "Collecting Data",
                    adviceRu = "Продолжайте непрерывный мониторинг. После накопления замеров здесь появятся персональные фокусы для роста вашего TIR.",
                    adviceEn = "Keep monitoring continuously. Once more readings accumulate, personalized focuses for increasing your TIR will appear here.",
                    badgeRu = "Сбор данных",
                    badgeEn = "Collecting",
                    icon = "📊",
                    priority = 100
                )
            )
        }

        val insights = mutableListOf<TirInsight>()

        // 1. Safety Priority: TBR > 4% (Eliminate hypos before flattening hypers)
        val totalTbr = stats.tbrLowPercent + stats.tbrVeryLowPercent
        if (totalTbr > 4.0) {
            insights.add(
                TirInsight(
                    id = "tbr_safety",
                    titleRu = "Приоритет: безопасное купирование гипо",
                    titleEn = "Priority: Safe Hypo Treatment",
                    adviceRu = "Сначала устраняем гипогликемии, затем снижаем пики. Купируйте падения строго 1–1.5 ХЕ быстрых углеводов без переедания (правило 15 минут), чтобы не спровоцировать рикошет в гипергликемию.",
                    adviceEn = "Eliminate hypos before flattening spikes. Treat low glucose with strictly 1–1.5 XE of fast-acting carbs and wait 15 minutes before re-checking to avoid rebound spikes.",
                    badgeRu = "Безопасность",
                    badgeEn = "Safety First",
                    icon = "🛡️",
                    priority = 1
                )
            )
        }

        // 2. Pre-bolus Timing: High TAR or Postprandial Daytime Spikes
        val totalTar = stats.tarHighPercent + stats.tarVeryHighPercent
        val dayBins = bins.filter { it.minuteOfDay in 480..1320 && it.readingsCount > 0 } // 08:00 - 22:00
        val hasDaytimeSpikes = dayBins.any { it.p50 >= 9.5 || it.p75 >= 11.5 }

        if (totalTar > 25.0 || hasDaytimeSpikes) {
            insights.add(
                TirInsight(
                    id = "pre_bolus_timing",
                    titleRu = "Пауза перед едой (Пре-болюс)",
                    titleEn = "Pre-Bolus Timing",
                    adviceRu = "Выдерживайте паузу 10–15 минут между уколом пищевого инсулина и едой. Инсулин успеет развернуться одновременно с углеводами, сгладит пик через час и добавит до +5–8% к вашему TIR.",
                    adviceEn = "Take a 10–15 minute pause between bolusing mealtime insulin and eating. This aligns insulin peak with carb absorption, flattening post-meal spikes and boosting TIR by +5–8%.",
                    badgeRu = "Прирост TIR",
                    badgeEn = "TIR Boost",
                    icon = "⏱️",
                    priority = 2
                )
            )
        }

        // 3. Morning Dawn Stability (05:00 - 09:00 rise)
        val earlyNightBins = bins.filter { it.minuteOfDay in 60..240 && it.readingsCount > 0 }
        val dawnBins = bins.filter { it.minuteOfDay in 300..540 && it.readingsCount > 0 }
        val earlyNightMedian = if (earlyNightBins.isNotEmpty()) earlyNightBins.map { it.p50 }.average() else 6.0
        val dawnMedian = if (dawnBins.isNotEmpty()) dawnBins.map { it.p50 }.maxOrNull() ?: 0.0 else 0.0

        if (dawnMedian >= 8.5 && dawnMedian - earlyNightMedian >= 2.0) {
            insights.add(
                TirInsight(
                    id = "morning_rise",
                    titleRu = "Утренний подъем сахара",
                    titleEn = "Morning Glucose Rise",
                    adviceRu = "Сахар стабильно прирастает под утро без еды. Обсудите с лечащим врачом сдвиг вечерней базы или микроболюс сразу при пробуждении (до подъема с постели).",
                    adviceEn = "Glucose steadily climbs in early morning without food. Discuss adjusting evening basal timing or taking a wake-up micro-bolus before getting out of bed with your physician.",
                    badgeRu = "Утренний старт",
                    badgeEn = "Morning Focus",
                    icon = "🌅",
                    priority = 3
                )
            )
        }

        // 4. High Variability Flattening (%CV > 36%)
        if (stats.cvPercent > 36.0) {
            insights.add(
                TirInsight(
                    id = "variability_flattening",
                    titleRu = "Снижение вариабельности (%CV)",
                    titleEn = "Flattening Variability (%CV)",
                    adviceRu = "Высокие качели сахара (%CV > 36%). Проверьте дневной углеводный коэффициент (К1) — в обед потребность в инсулине часто отличается от утренней.",
                    adviceEn = "High glucose swings (%CV > 36%). Check your daytime insulin-to-carb ratio (ICR) — daytime insulin sensitivity often differs from morning.",
                    badgeRu = "Плавность",
                    badgeEn = "Smooth Profile",
                    icon = "📉",
                    priority = 4
                )
            )
        }

        // 5. Late Evening / Dinner Fat-Protein Effect (21:00 - 01:00)
        val lateBins = bins.filter { (it.minuteOfDay >= 1260 || it.minuteOfDay <= 60) && it.readingsCount > 0 }
        val lateMedian = if (lateBins.isNotEmpty()) lateBins.map { it.p50 }.average() else 0.0
        if (lateMedian >= 8.8) {
            insights.add(
                TirInsight(
                    id = "evening_fat_protein",
                    titleRu = "Поздние ужины и белки с жирами",
                    titleEn = "Late Dinners & Protein-Fat",
                    adviceRu = "Белки и жиры на ужин усваиваются 3–5 часов и дают поздний подъем сахара. Рассмотрите перенос ужина на более раннее время или растянутый болюс.",
                    adviceEn = "Proteins and fats at dinner digest over 3–5 hours causing late climbs. Consider having dinner earlier or using a split/extended bolus.",
                    badgeRu = "Вечерний ужин",
                    badgeEn = "Evening Habit",
                    icon = "🍽️",
                    priority = 5
                )
            )
        }

        // 6. High Performers (TIR >= 75%): Tight Range Target (TING)
        if (stats.tirPercent >= 75.0 && totalTbr <= 4.0) {
            insights.add(
                TirInsight(
                    id = "tight_range_focus",
                    titleRu = "Фокус на нормогликемии (TING)",
                    titleEn = "Focus on Tight Range (TING)",
                    adviceRu = "Отличный контроль (TIR ≥ 75%)! Следующий шаг к профилю здорового человека — расширение времени в строгом коридоре TING (3.9–7.8 ммоль/л).",
                    adviceEn = "Outstanding glycemic control (TIR ≥ 75%)! The next step towards euglycemia is expanding Time in Tight Range (TING 3.9–7.8 mmol/L).",
                    badgeRu = "Продвинутый уровень",
                    badgeEn = "Pro Level",
                    icon = "🎯",
                    priority = 6
                )
            )
        }

        // 7. General Core Tip fallback if no specific trigger fired
        if (insights.isEmpty()) {
            insights.add(
                TirInsight(
                    id = "core_carb_tracking",
                    titleRu = "Точность подсчета углеводов",
                    titleEn = "Accurate Carb Counting",
                    adviceRu = "Взвешивание углеводов на кухонных весах и фиксация пауз перед едой — самый надёжный способ удерживать сахар в целевом диапазоне.",
                    adviceEn = "Weighing carbohydrates with kitchen scales and keeping consistent pre-meal pauses is the most reliable way to maintain high TIR.",
                    badgeRu = "Базовый фокус",
                    badgeEn = "Core Habit",
                    icon = "⚖️",
                    priority = 10
                )
            )
        }

        return insights.sortedBy { it.priority }.take(maxCount)
    }
}
