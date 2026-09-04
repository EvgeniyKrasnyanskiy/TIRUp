package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.domain.model.WeeklyDigest
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object WeeklyDigestCalculator {

    private const val MGDL_FACTOR = 18.01559
    const val MIN_CGM_ACTIVE_PERCENT = 70.0
    const val MIN_POINTS_FOR_ANALYSIS = 200 // Safety minimum for 7 days

    /**
     * Counts distinct hypoglycemia episodes.
     * An episode starts when glucose drops below [thresholdMmol].
     * An episode is considered finished when glucose returns above threshold and stays there for >= 20 min.
     */
    fun countHypoEpisodes(readings: List<GlucoseReading>, thresholdMmol: Double = 3.9): Int {
        if (readings.isEmpty()) return 0
        val sorted = readings.sortedBy { it.timestamp }
        var episodes = 0
        var inHypo = false
        var lastHypoTime = 0L

        for (r in sorted) {
            if (r.valueMmol < thresholdMmol) {
                if (!inHypo) {
                    episodes++
                    inHypo = true
                }
                lastHypoTime = r.timestamp
            } else {
                if (inHypo && (r.timestamp - lastHypoTime > 20 * 60 * 1000L)) {
                    inHypo = false
                }
            }
        }
        return episodes
    }

    /**
     * Calculates weekly digest comparing [currentWeekReadings] to [previousWeekReadings].
     */
    fun calculateDigest(
        currentWeekReadings: List<GlucoseReading>,
        previousWeekReadings: List<GlucoseReading>,
        currentStart: Long,
        currentEnd: Long,
        prevStart: Long,
        prevEnd: Long,
        settings: UserSettings
    ): WeeklyDigest {
        val isRu = settings.language.equals("RU", ignoreCase = true)
        val isMmol = settings.unit == GlucoseUnit.MMOL_L

        val currentResampled = GlucoseMetricsCalculator.resampleTo5Minutes(currentWeekReadings)
        val prevResampled = GlucoseMetricsCalculator.resampleTo5Minutes(previousWeekReadings)

        val currentStats = GlucoseMetricsCalculator.calculateStatistics(
            readings = currentResampled,
            targetRanges = settings.targetRanges,
            nightStartHour = settings.nightStartHour,
            nightEndHour = settings.nightEndHour,
            language = settings.language,
            unit = settings.unit
        )

        val prevStats = GlucoseMetricsCalculator.calculateStatistics(
            readings = prevResampled,
            targetRanges = settings.targetRanges,
            nightStartHour = settings.nightStartHour,
            nightEndHour = settings.nightEndHour,
            language = settings.language,
            unit = settings.unit
        )

        val hasSufficientData = currentStats.activeTimePercent >= MIN_CGM_ACTIVE_PERCENT &&
                currentStats.totalCount >= MIN_POINTS_FOR_ANALYSIS

        val hypoThreshold = settings.targetRanges.tirLowMmol
        val hypoCountCurrent = countHypoEpisodes(currentResampled, hypoThreshold)
        val hypoCountPrevious = countHypoEpisodes(prevResampled, hypoThreshold)

        val tirDelta = roundOneDecimal(currentStats.tirPercent - prevStats.tirPercent)
        val tingDelta = roundOneDecimal(currentStats.tingPercent - prevStats.tingPercent)
        val totalTbrCurr = currentStats.tbrLowPercent + currentStats.tbrVeryLowPercent
        val totalTbrPrev = prevStats.tbrLowPercent + prevStats.tbrVeryLowPercent
        val tbrDelta = roundOneDecimal(totalTbrCurr - totalTbrPrev)
        val cvDelta = roundOneDecimal(currentStats.cvPercent - prevStats.cvPercent)
        val meanDeltaMmol = roundOneDecimal(currentStats.meanMmol - prevStats.meanMmol)

        val (headline, insights, recommendation) = generateInsightsAndAdvice(
            hasSufficientData = hasSufficientData,
            activePercent = currentStats.activeTimePercent,
            currentTir = currentStats.tirPercent,
            tirDelta = tirDelta,
            currentTing = currentStats.tingPercent,
            tingDelta = tingDelta,
            currentTbr = totalTbrCurr,
            tbrDelta = tbrDelta,
            currentCv = currentStats.cvPercent,
            cvDelta = cvDelta,
            currentMeanMmol = currentStats.meanMmol,
            meanDeltaMmol = meanDeltaMmol,
            hypoCountCurrent = hypoCountCurrent,
            hypoCountPrev = hypoCountPrevious,
            isRu = isRu,
            isMmol = isMmol,
            prevDataAvailable = prevStats.totalCount >= MIN_POINTS_FOR_ANALYSIS
        )

        return WeeklyDigest(
            currentWeekStart = currentStart,
            currentWeekEnd = currentEnd,
            previousWeekStart = prevStart,
            previousWeekEnd = prevEnd,
            currentStats = currentStats,
            previousStats = prevStats,
            hasSufficientData = hasSufficientData,
            tirDelta = tirDelta,
            tingDelta = tingDelta,
            tbrDelta = tbrDelta,
            cvDelta = cvDelta,
            meanDeltaMmol = meanDeltaMmol,
            hypoCountCurrent = hypoCountCurrent,
            hypoCountPrevious = hypoCountPrevious,
            headline = headline,
            keyInsights = insights,
            recommendation = recommendation
        )
    }

    /**
     * Helper to compute digest for a given reference timestamp (e.g. now or Sunday 20:00).
     */
    fun calculateForReferenceTimestamp(
        allReadings: List<GlucoseReading>,
        referenceTime: Long,
        settings: UserSettings
    ): WeeklyDigest {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTime }
        val currentEnd = referenceTime
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val currentStart = cal.timeInMillis
        val prevEnd = currentStart
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val prevStart = cal.timeInMillis

        val currentReadings = allReadings.filter { it.timestamp in currentStart..currentEnd }
        val prevReadings = allReadings.filter { it.timestamp in prevStart until currentStart }

        return calculateDigest(
            currentWeekReadings = currentReadings,
            previousWeekReadings = prevReadings,
            currentStart = currentStart,
            currentEnd = currentEnd,
            prevStart = prevStart,
            prevEnd = prevEnd,
            settings = settings
        )
    }

    private fun generateInsightsAndAdvice(
        hasSufficientData: Boolean,
        activePercent: Double,
        currentTir: Double,
        tirDelta: Double,
        currentTing: Double,
        tingDelta: Double,
        currentTbr: Double,
        tbrDelta: Double,
        currentCv: Double,
        cvDelta: Double,
        currentMeanMmol: Double,
        meanDeltaMmol: Double,
        hypoCountCurrent: Int,
        hypoCountPrev: Int,
        isRu: Boolean,
        isMmol: Boolean,
        prevDataAvailable: Boolean
    ): Triple<String, List<String>, String> {
        if (!hasSufficientData) {
            val headline = if (isRu) {
                "📊 Недельный дайджест: Данные накапливаются"
            } else {
                "📊 Weekly Digest: Accumulating Data"
            }
            val insights = if (isRu) {
                listOf(
                    "Активность CGM за неделю: ${roundOneDecimal(activePercent)}% (стандарт ATTD ≥70%).",
                    "Для точного медицинского анализа недели требуется не менее 70% непрерывных замеров.",
                    "Продолжайте непрерывный мониторинг — дайджест сформируется автоматически."
                )
            } else {
                listOf(
                    "CGM active time this week: ${roundOneDecimal(activePercent)}% (ATTD goal ≥70%).",
                    "At least 70% continuous sensor readings are required for clinical analysis.",
                    "Keep monitoring continuously — weekly digest will update automatically."
                )
            }
            val rec = if (isRu) {
                "Проверьте стабильность связи с трансмиттером/сенсором в xDrip+."
            } else {
                "Ensure steady sensor connection and background sync in xDrip+."
            }
            return Triple(headline, insights, rec)
        }

        val insights = mutableListOf<String>()

        // 1. Safety / Hypo insight
        if (currentTbr > 4.0) {
            val deltaStr = if (prevDataAvailable) {
                if (tbrDelta > 0) " (+${tbrDelta}%)" else " (${tbrDelta}%)"
            } else ""
            insights.add(
                if (isRu) {
                    "🚨 Внимание к гипо: TBR ${roundOneDecimal(currentTbr)}%$deltaStr превышает норму (цель <4%). Эпизодов: $hypoCountCurrent."
                } else {
                    "🚨 Hypo Alert: TBR ${roundOneDecimal(currentTbr)}%$deltaStr exceeds clinical target (<4%). Episodes: $hypoCountCurrent."
                }
            )
        } else if (hypoCountCurrent == 0) {
            insights.add(
                if (isRu) {
                    "🛡️ Отличная безопасность: за неделю не зафиксировано эпизодов гипогликемии (TBR ${roundOneDecimal(currentTbr)}%)."
                } else {
                    "🛡️ Excellent safety: zero hypo episodes recorded this week (TBR ${roundOneDecimal(currentTbr)}%)."
                }
            )
        } else {
            val compStr = if (prevDataAvailable) {
                if (hypoCountCurrent < hypoCountPrev) {
                    if (isRu) " (снижение с $hypoCountPrev)" else " (down from $hypoCountPrev)"
                } else if (hypoCountCurrent > hypoCountPrev) {
                    if (isRu) " (рост с $hypoCountPrev)" else " (up from $hypoCountPrev)"
                } else ""
            } else ""
            insights.add(
                if (isRu) {
                    "✅ Гипогликемии под контролем: TBR ${roundOneDecimal(currentTbr)}% (норма <4%), $hypoCountCurrent эпизодов$compStr."
                } else {
                    "✅ Hypos controlled: TBR ${roundOneDecimal(currentTbr)}% (target <4%), $hypoCountCurrent episodes$compStr."
                }
            )
        }

        // 2. TIR Dynamics
        if (prevDataAvailable) {
            val sign = if (tirDelta > 0) "+$tirDelta%" else "$tirDelta%"
            if (tirDelta >= 3.0) {
                insights.add(
                    if (isRu) {
                        "📈 TIR вырос на $sign до ${roundOneDecimal(currentTir)}% — заметный прогресс компенсации!"
                    } else {
                        "📈 TIR increased by $sign to ${roundOneDecimal(currentTir)}% — noticeable progress!"
                    }
                )
            } else if (tirDelta <= -3.0) {
                insights.add(
                    if (isRu) {
                        "📉 TIR снизился на $sign до ${roundOneDecimal(currentTir)}% — требуется внимание к профилю."
                    } else {
                        "📉 TIR decreased by $sign to ${roundOneDecimal(currentTir)}% — requires attention."
                    }
                )
            } else {
                insights.add(
                    if (isRu) {
                        "➡️ TIR стабилен: ${roundOneDecimal(currentTir)}% ($sign за неделю, цель ≥70%)."
                    } else {
                        "➡️ TIR steady: ${roundOneDecimal(currentTir)}% ($sign this week, target ≥70%)."
                    }
                )
            }
        } else {
            insights.add(
                if (isRu) {
                    "🎯 Время в диапазоне (TIR): ${roundOneDecimal(currentTir)}% (клиническая цель ≥70%)."
                } else {
                    "🎯 Time in Range (TIR): ${roundOneDecimal(currentTir)}% (clinical target ≥70%)."
                }
            )
        }

        // 3. Variability (%CV)
        if (currentCv <= 36.0) {
            insights.add(
                if (isRu) {
                    "🎯 Вариабельность сахара в норме: CV ${roundOneDecimal(currentCv)}% (цель ≤36%). Профиль стабилен."
                } else {
                    "🎯 Glucose variability in range: CV ${roundOneDecimal(currentCv)}% (target ≤36%). Profile is stable."
                }
            )
        } else {
            insights.add(
                if (isRu) {
                    "⚠️ Повышенная вариабельность: CV ${roundOneDecimal(currentCv)}% (цель ≤36%). Колебания требуют сглаживания."
                } else {
                    "⚠️ Elevated variability: CV ${roundOneDecimal(currentCv)}% (target ≤36%). Blood sugar shows volatility."
                }
            )
        }

        // 4. Mean glucose / TING
        if (currentTing >= 50.0) {
            insights.add(
                if (isRu) {
                    "🌟 Узкий диапазон (TING 3.9–7.8): ${roundOneDecimal(currentTing)}% времени — отличная плато-гликемия."
                } else {
                    "🌟 Tight range (TING 70–140): ${roundOneDecimal(currentTing)}% of time — excellent plateau control."
                }
            )
        }

        // Headline
        val headline = when {
            currentTbr > 4.0 -> {
                if (isRu) "⚠️ Недельный дайджест: фокус на устранении гипо"
                else "⚠️ Weekly Digest: Focus on Hypo Safety"
            }
            prevDataAvailable && tirDelta >= 3.0 -> {
                if (isRu) "🚀 Прогресс недели: TIR ${roundOneDecimal(currentTir)}% (+${tirDelta}%)"
                else "🚀 Progress this week: TIR ${roundOneDecimal(currentTir)}% (+${tirDelta}%)"
            }
            currentTir >= 70.0 -> {
                if (isRu) "🌟 Отличная неделя: TIR ${roundOneDecimal(currentTir)}% в норме"
                else "🌟 Great week: TIR ${roundOneDecimal(currentTir)}% on target"
            }
            else -> {
                if (isRu) "📊 Недельный дайджест: TIR ${roundOneDecimal(currentTir)}%"
                else "📊 Weekly Digest: TIR ${roundOneDecimal(currentTir)}%"
            }
        }

        // Recommendation
        val recommendation = when {
            currentTbr > 4.0 -> {
                if (isRu) {
                    "Первоочередная задача: купировать падения строго 1–1.5 ХЕ без переедания и проверить дозы базала в часы падений."
                } else {
                    "Top priority: treat low sugar with strictly 1–1.5 XE fast carbs and review basal rates around hypo hours."
                }
            }
            currentCv > 36.0 -> {
                if (isRu) {
                    "Для снижения вариабельности обратите внимание на предпаузу перед приёмом пищи и точность подсчёта углеводов."
                } else {
                    "To smooth variability, optimize pre-bolus timing and carbohydrate counting accuracy."
                }
            }
            currentTir >= 70.0 && currentTbr <= 4.0 -> {
                if (isRu) {
                    "Терапевтический профиль превосходен! Продолжайте придерживаться текущего режима питания и активности."
                } else {
                    "Your therapeutic profile is outstanding! Maintain your current dietary and activity routine."
                }
            }
            else -> {
                if (isRu) {
                    "Сосредоточьтесь на сглаживании постпрандиальных подъёмов для постепенного вывода TIR в зону ≥70%."
                } else {
                    "Focus on smoothing post-meal peaks to steadily guide your TIR above 70%."
                }
            }
        }

        return Triple(headline, insights, recommendation)
    }

    private fun roundOneDecimal(value: Double): Double {
        return (value * 10.0).roundToInt() / 10.0
    }
}
