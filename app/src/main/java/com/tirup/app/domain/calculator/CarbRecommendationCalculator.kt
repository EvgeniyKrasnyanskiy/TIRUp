package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.BmiCategory
import java.util.Locale

data class MealCarbDistribution(
    val breakfastGrams: IntRange,
    val breakfastXe: ClosedFloatingPointRange<Double>,
    val lunchGrams: IntRange,
    val lunchXe: ClosedFloatingPointRange<Double>,
    val dinnerGrams: IntRange,
    val dinnerXe: ClosedFloatingPointRange<Double>,
    val snacksGrams: IntRange,
    val snacksXe: ClosedFloatingPointRange<Double>
) {
    fun formatBreakfast(isRu: Boolean): String {
        return "${breakfastGrams.first}–${breakfastGrams.last} ${if (isRu) "г" else "g"} (${formatXeRange(breakfastXe, isRu)})"
    }

    fun formatLunch(isRu: Boolean): String {
        return "${lunchGrams.first}–${lunchGrams.last} ${if (isRu) "г" else "g"} (${formatXeRange(lunchXe, isRu)})"
    }

    fun formatDinner(isRu: Boolean): String {
        return "${dinnerGrams.first}–${dinnerGrams.last} ${if (isRu) "г" else "g"} (${formatXeRange(dinnerXe, isRu)})"
    }

    fun formatSnacks(isRu: Boolean): String {
        return "${snacksGrams.first}–${snacksGrams.last} ${if (isRu) "г" else "g"} (${formatXeRange(snacksXe, isRu)})"
    }

    private fun formatXeRange(range: ClosedFloatingPointRange<Double>, isRu: Boolean): String {
        val unit = if (isRu) "ХЕ" else "BU"
        return String.format(Locale.US, "%.1f–%.1f %s", range.start, range.endInclusive, unit)
    }
}

data class CarbRecommendation(
    val dailyGramsRange: IntRange,
    val dailyXeRange: ClosedFloatingPointRange<Double>,
    val distribution: MealCarbDistribution,
    val clinicalRationaleRu: String,
    val clinicalRationaleEn: String,
    val clinicalWarningRu: String? = null,
    val clinicalWarningEn: String? = null
) {
    fun formatDailySummary(isRu: Boolean): String {
        val gUnit = if (isRu) "г/сут" else "g/day"
        val xeUnit = if (isRu) "ХЕ" else "BU"
        return String.format(
            Locale.US,
            "%d–%d %s (~%.1f–%.1f %s)",
            dailyGramsRange.first,
            dailyGramsRange.last,
            gUnit,
            dailyXeRange.start,
            dailyXeRange.endInclusive,
            xeUnit
        )
    }
}

object CarbRecommendationCalculator {

    private const val GRAMS_PER_XE = 12.0

    /**
     * Calculates recommended daily carbohydrate intake in grams and Bread Units (XE)
     * based on age, gender and BMI category according to ISPAD, ADA and clinical endocrinology standards.
     */
    fun calculate(
        age: Int,
        gender: String = "M",
        bmiCategory: BmiCategory = BmiCategory.NORMAL
    ): CarbRecommendation {
        val isChild = age in 2..17

        val dailyGrams: IntRange = if (isChild) {
            when {
                age in 2..5 -> 130..160
                age in 6..10 -> 160..210
                age in 11..14 -> if (gender.equals("M", ignoreCase = true)) 210..270 else 190..250
                else -> if (gender.equals("M", ignoreCase = true)) 230..300 else 200..260
            }
        } else {
            when (bmiCategory) {
                BmiCategory.UNDERWEIGHT -> 220..280
                BmiCategory.NORMAL -> 180..240
                BmiCategory.OVERWEIGHT -> 140..180
                BmiCategory.OBESE_1, BmiCategory.OBESE_2_3, BmiCategory.PEDIATRIC_OBESE -> 130..155
            }
        }

        val startXe = (dailyGrams.first / GRAMS_PER_XE * 10.0).toInt() / 10.0
        val endXe = (dailyGrams.last / GRAMS_PER_XE * 10.0).toInt() / 10.0
        val dailyXe = startXe..endXe

        // Meal distribution:
        // Breakfast: 20-25%
        // Lunch: 30-35%
        // Dinner: 25-30%
        // Snacks: 10-15%
        val distribution = MealCarbDistribution(
            breakfastGrams = (dailyGrams.first * 0.20).toInt()..(dailyGrams.last * 0.25).toInt(),
            breakfastXe = roundXe(dailyXe.start * 0.20)..roundXe(dailyXe.endInclusive * 0.25),
            lunchGrams = (dailyGrams.first * 0.30).toInt()..(dailyGrams.last * 0.35).toInt(),
            lunchXe = roundXe(dailyXe.start * 0.30)..roundXe(dailyXe.endInclusive * 0.35),
            dinnerGrams = (dailyGrams.first * 0.25).toInt()..(dailyGrams.last * 0.30).toInt(),
            dinnerXe = roundXe(dailyXe.start * 0.25)..roundXe(dailyXe.endInclusive * 0.30),
            snacksGrams = (dailyGrams.first * 0.10).toInt()..(dailyGrams.last * 0.15).toInt(),
            snacksXe = roundXe(dailyXe.start * 0.10)..roundXe(dailyXe.endInclusive * 0.15)
        )

        val rationaleRu: String
        val rationaleEn: String
        var warningRu: String? = null
        var warningEn: String? = null

        if (isChild) {
            val sexStr = if (gender.equals("F", ignoreCase = true)) "девочек" else "мальчиков"
            val sexStrEn = if (gender.equals("F", ignoreCase = true)) "girls" else "boys"
            rationaleRu = "Педиатрический стандарт ISPAD для $sexStr $age лет. 45–55% суточного калоража должно поступать из сложных углеводов для обеспечения непрерывного линейного роста и развития головного мозга."
            rationaleEn = "ISPAD pediatric standard for $sexStrEn aged $age. 45–55% of daily energy should come from complex carbohydrates to support growth, cognitive development and hormonal balance."

            warningRu = "⚠️ ISPAD строго не рекомендует низкоуглеводные (<130 г/сут) и кетогенные диеты детям и подросткам из-за высокого риска задержки роста, остеопении и эугликемического кетоацидоза."
            warningEn = "⚠️ ISPAD strongly advises against low-carb (<130 g/day) and ketogenic diets in youth with diabetes due to risks of growth failure, bone loss and euglycemic DKA."
        } else {
            when (bmiCategory) {
                BmiCategory.UNDERWEIGHT -> {
                    rationaleRu = "Дефицит массы тела: увеличена норма углеводов для восстановления белково-энергетического баланса, анаболизма и профилактики частых ночных гипогликемий."
                    rationaleEn = "Underweight: increased carbohydrate allowance to restore lean body mass, support anabolic recovery and prevent hypoglycemia."
                }
                BmiCategory.NORMAL -> {
                    rationaleRu = "Нормальная масса тела: сбалансированное питание (45–50% от суточного калоража) преимущественно за счёт цельных злаков, овощей и бобовых с низким гликемическим индексом."
                    rationaleEn = "Normal weight: balanced intake (45–50% of daily calories) prioritizing whole grains, vegetables, and low-glycemic index foods."
                }
                BmiCategory.OVERWEIGHT -> {
                    rationaleRu = "Избыточная масса тела: умеренное ограничение углеводов с заменой быстрых сахаров на медленноусвояемые и клетчатку (>30 г/сут) для снижения постпрандиальных пиков и резистентности к инсулину."
                    rationaleEn = "Overweight: moderate carbohydrate restriction substituting fast sugars with fiber-rich complex carbs (>30 g/day) to blunt spikes and improve insulin sensitivity."
                }
                BmiCategory.OBESE_1, BmiCategory.OBESE_2_3, BmiCategory.PEDIATRIC_OBESE -> {
                    rationaleRu = "Ожирение: лечебное умеренное ограничение до 130–155 г/сут. Сохраняется физиологический минимум для ЦНС при повышении чувствительности к инсулину."
                    rationaleEn = "Obesity: clinical moderate reduction to 130–155 g/day. Preserves vital CNS metabolic needs while improving peripheral insulin sensitivity."
                    warningRu = "⚠️ Физиологический порог ЦНС — не менее 130 г глюкозы в сутки. Урезание углеводов ниже 100 г/сут требует обязательного согласования с лечащим эндокринологом."
                    warningEn = "⚠️ Physiological brain glucose requirement is ≥130 g/day. Carbohydrate restriction below 100 g/day requires physician supervision to avoid dyslipidemia and ketosis."
                }
            }
        }

        return CarbRecommendation(
            dailyGramsRange = dailyGrams,
            dailyXeRange = dailyXe,
            distribution = distribution,
            clinicalRationaleRu = rationaleRu,
            clinicalRationaleEn = rationaleEn,
            clinicalWarningRu = warningRu,
            clinicalWarningEn = warningEn
        )
    }

    private fun roundXe(value: Double): Double {
        return (value * 10.0).toInt() / 10.0
    }
}
