package com.tirup.app.domain.ai

import com.tirup.app.domain.calculator.GlucoseMetricsCalculator
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.model.UserSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AiContextPeriod(val titleRu: String, val titleEn: String) {
    NO_DATA("Без данных", "No data"),
    TODAY("Сегодня", "Today"),
    YESTERDAY("Вчера", "Yesterday"),
    CUSTOM_DAY("Выбрать день", "Select day"),
    DAYS_7("За 7 дней", "Last 7 days"),
    DAYS_14("За 14 дней", "Last 14 days"),
    DAYS_30("За 30 дней", "Last 30 days");

    fun getDisplayName(isRu: Boolean): String = if (isRu) titleRu else titleEn
}

object AiGlycemicContextBuilder {

    fun getTimeBounds(period: AiContextPeriod, customDayTimestamp: Long? = null): Pair<Long, Long>? {
        if (period == AiContextPeriod.NO_DATA) return null
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (period) {
            AiContextPeriod.NO_DATA -> null
            AiContextPeriod.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                Pair(start, now)
            }
            AiContextPeriod.YESTERDAY -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            AiContextPeriod.CUSTOM_DAY -> {
                val targetTs = customDayTimestamp ?: now
                calendar.timeInMillis = targetTs
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            AiContextPeriod.DAYS_7 -> {
                val start = now - 7L * 24 * 3600 * 1000L
                Pair(start, now)
            }
            AiContextPeriod.DAYS_14 -> {
                val start = now - 14L * 24 * 3600 * 1000L
                Pair(start, now)
            }
            AiContextPeriod.DAYS_30 -> {
                val start = now - 30L * 24 * 3600 * 1000L
                Pair(start, now)
            }
        }
    }

    fun buildContext(
        period: AiContextPeriod,
        readings: List<GlucoseReading>,
        treatments: List<Treatment>,
        userSettings: UserSettings
    ): String {
        if (period == AiContextPeriod.NO_DATA) return ""
        val isRu = userSettings.language.equals("RU", ignoreCase = true)
        val isMmol = userSettings.unit == GlucoseUnit.MMOL_L
        val unitLabel = if (isMmol) (if (isRu) "ммоль/л" else "mmol/L") else (if (isRu) "мг/дл" else "mg/dL")

        if (readings.isEmpty()) {
            return if (isRu) "За выбранный период (${period.titleRu}) данные непрерывного мониторинга (CGM) отсутствуют."
            else "No continuous glucose monitoring (CGM) data recorded for the selected period (${period.titleEn})."
        }

        fun formatGluc(mmol: Double): String {
            return if (isMmol) String.format(Locale.US, "%.1f", mmol)
            else String.format(Locale.US, "%.0f", mmol * 18.0182)
        }

        val stats = GlucoseMetricsCalculator.calculateStatistics(readings, userSettings.targetRanges)
        val gmi = stats.gmiPercent

        val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.append(if (isRu) "--- СВОДКА ДАННЫХ ПАЦИЕНТА (${period.titleRu}) ---\n"
                  else "--- PATIENT GLYCEMIC SUMMARY (${period.titleEn}) ---\n")
        sb.append(if (isRu) "• Единицы: $unitLabel | Точек замера: ${readings.size}\n"
                  else "• Unit: $unitLabel | Data points: ${readings.size}\n")
        sb.append(if (isRu) "• Средний сахар: ${formatGluc(stats.meanMmol)} $unitLabel | Вариабельность (CV): ${String.format(Locale.US, "%.1f", stats.cvPercent)}% | GMI: ${String.format(Locale.US, "%.1f", gmi)}%\n"
                  else "• Mean glucose: ${formatGluc(stats.meanMmol)} $unitLabel | Variability (CV): ${String.format(Locale.US, "%.1f", stats.cvPercent)}% | GMI: ${String.format(Locale.US, "%.1f", gmi)}%\n")
        sb.append(if (isRu) "• В целевом диапазоне (TIR): ${String.format(Locale.US, "%.0f", stats.tirPercent)}% (цель: >70%)\n"
                  else "• Time in Range (TIR): ${String.format(Locale.US, "%.0f", stats.tirPercent)}% (goal: >70%)\n")
        sb.append(if (isRu) "• Гипогликемия (TBR <3.9): ${String.format(Locale.US, "%.1f", stats.tbrLowPercent + stats.tbrVeryLowPercent)}% (тяжёлая <3.0: ${String.format(Locale.US, "%.1f", stats.tbrVeryLowPercent)}%)\n"
                  else "• Below Range (TBR <3.9): ${String.format(Locale.US, "%.1f", stats.tbrLowPercent + stats.tbrVeryLowPercent)}% (severe <3.0: ${String.format(Locale.US, "%.1f", stats.tbrVeryLowPercent)}%)\n")
        sb.append(if (isRu) "• Гипергликемия (TAR >10.0): ${String.format(Locale.US, "%.1f", stats.tarHighPercent + stats.tarVeryHighPercent)}% (очень высокая >13.9: ${String.format(Locale.US, "%.1f", stats.tarVeryHighPercent)}%)\n"
                  else "• Above Range (TAR >10.0): ${String.format(Locale.US, "%.1f", stats.tarHighPercent + stats.tarVeryHighPercent)}% (very high >13.9: ${String.format(Locale.US, "%.1f", stats.tarVeryHighPercent)}%)\n")

        // Diurnal Breakdown (Night 00-06, Morning 06-12, Afternoon 12-18, Evening 18-24)
        val cal = Calendar.getInstance()
        val nightReadings = readings.filter { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) in 0..5 }
        val morningReadings = readings.filter { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) in 6..11 }
        val afternoonReadings = readings.filter { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) in 12..17 }
        val eveningReadings = readings.filter { cal.timeInMillis = it.timestamp; cal.get(Calendar.HOUR_OF_DAY) in 18..23 }

        sb.append(if (isRu) "\n• По времени суток (среднее [мин - макс]):\n" else "\n• Diurnal breakdown (mean [min - max]):\n")
        fun appendSlot(title: String, slot: List<GlucoseReading>) {
            if (slot.isNotEmpty()) {
                val mean = slot.map { it.valueMmol }.average()
                val min = slot.minOf { it.valueMmol }
                val max = slot.maxOf { it.valueMmol }
                sb.append("  - $title: ${formatGluc(mean)} [${formatGluc(min)} - ${formatGluc(max)}]\n")
            }
        }
        appendSlot(if (isRu) "Ночь (00-06)" else "Night (00-06)", nightReadings)
        appendSlot(if (isRu) "Утро (06-12)" else "Morning (06-12)", morningReadings)
        appendSlot(if (isRu) "День (12-18)" else "Afternoon (12-18)", afternoonReadings)
        appendSlot(if (isRu) "Вечер (18-24)" else "Evening (18-24)", eveningReadings)

        // Key Hypo and Hyper Extremes
        val hypos = readings.filter { it.valueMmol < 3.9 }
        if (hypos.isNotEmpty()) {
            val minPoint = hypos.minByOrNull { it.valueMmol }
            if (minPoint != null) {
                sb.append(if (isRu) "\n• Минимальный зафиксированный сахар: ${formatGluc(minPoint.valueMmol)} $unitLabel в ${dateFormat.format(Date(minPoint.timestamp))}\n"
                          else "\n• Lowest recorded glucose: ${formatGluc(minPoint.valueMmol)} $unitLabel at ${dateFormat.format(Date(minPoint.timestamp))}\n")
            }
        }
        val hypers = readings.filter { it.valueMmol > 10.0 }
        if (hypers.isNotEmpty()) {
            val maxPoint = hypers.maxByOrNull { it.valueMmol }
            if (maxPoint != null) {
                sb.append(if (isRu) "• Максимальный пик сахара: ${formatGluc(maxPoint.valueMmol)} $unitLabel в ${dateFormat.format(Date(maxPoint.timestamp))}\n"
                          else "• Highest glucose peak: ${formatGluc(maxPoint.valueMmol)} $unitLabel at ${dateFormat.format(Date(maxPoint.timestamp))}\n")
            }
        }

        // Treatments (Insulin & Meals)
        if (treatments.isNotEmpty()) {
            sb.append(if (isRu) "\n• Записи приёмов пищи и инсулина (${treatments.size}):\n"
                      else "\n• Logged meals and insulin doses (${treatments.size}):\n")
            val recentTreatments = treatments.takeLast(15) // Keep compact
            for (t in recentTreatments) {
                val parts = mutableListOf<String>()
                t.carbsGrams?.let { if (it > 0) parts.add(if (isRu) "${String.format(Locale.US, "%.0f", it)}г углев" else "${String.format(Locale.US, "%.0f", it)}g carbs") }
                t.insulinUnits?.let { if (it > 0) parts.add(if (isRu) "${String.format(Locale.US, "%.1f", it)}ед инс" else "${String.format(Locale.US, "%.1f", it)}u ins") }
                t.notes?.takeIf { it.isNotBlank() }?.let { parts.add("\"$it\"") }
                val timeStr = if (period in listOf(AiContextPeriod.TODAY, AiContextPeriod.YESTERDAY, AiContextPeriod.CUSTOM_DAY)) {
                    timeFormat.format(Date(t.timestamp))
                } else {
                    dateFormat.format(Date(t.timestamp))
                }
                sb.append("  - $timeStr: ${parts.joinToString(", ")}\n")
            }
        }

        return sb.toString().trimEnd()
    }

    fun buildSystemPrompt(userSettings: UserSettings, glycemicContext: String): String {
        val isRu = userSettings.language.equals("RU", ignoreCase = true)
        val isMmol = userSettings.unit == GlucoseUnit.MMOL_L
        val unitStr = if (isMmol) "ммоль/л" else "мг/дл"

        val sb = StringBuilder()
        if (isRu) {
            sb.append("Ты — клинически компетентный и поддерживающий ИИ-ассистент в диабетическом приложении TIRUp.\n")
            sb.append("Приложение отслеживает показатели непрерывного мониторинга глюкозы (CGM), время в целевом диапазоне (TIR) и дневник терапии.\n\n")

            sb.append("КРИТИЧЕСКИЕ ПРАВИЛА БЕЗОПАСНОСТИ:\n")
            sb.append("1. Ты НЕ врач и НЕ медицинский прибор. Ты КАТЕГОРИЧЕСКИ НЕ имеешь права назначать или корректировать дозировки инсулина или лекарств!\n")
            sb.append("2. Все твои выводы носят информационно-аналитический характер. В рискованных ситуациях (гипогликемия, ацетон, длительный гипер) всегда напоминай немедленно перепроверить сахар по глюкометру и обратиться к врачу.\n\n")

            sb.append("ТВОИ ВОЗМОЖНОСТИ И ЗАДАЧИ:\n")
            sb.append("1. Анализ трендов и корреляций: сопоставляй приёмы пищи, паузы перед едой, подколки инсулина и поведение сахара по предоставленным данным.\n")
            sb.append("2. Разбор фото и блюд (БЖУ): если пользователь присылает фото или описание блюда, детально оцени состав, вес порции, Углеводы (г), Белки (г), Жиры (г), Хлебные Единицы (ХЕ из расчёта 1 ХЕ = 10–12г углеводов), Гликемический Индекс (ГИ) и риск белково-жирового хвоста (подъёма сахара через 3–5 часов).\n")
            sb.append("3. СТРУКТУРИРОВАННЫЙ ВЫВОД ДЛЯ ЕДЫ: При любом анализе еды (по фото или тексту), в самом конце твоего ответа ОБЯЗАТЕЛЬНО добавь служебный JSON-блок в формате:\n")
            sb.append("```json:food\n")
            sb.append("{\"food_detected\":true,\"title\":\"Краткое название блюда\",\"carbs_g\":45.0,\"protein_g\":20.0,\"fat_g\":10.0,\"bu\":3.8,\"comment\":\"Порция ~250г\"}\n")
            sb.append("```\n")
            sb.append("Если на фото не еда или пользователь не спрашивает про еду, не добавляй этот блок (или укажи `{\"food_detected\":false}`).\n\n")
            sb.append("Текущие единицы измерения пользователя в приложении: $unitStr.\n")
        } else {
            sb.append("You are a clinically knowledgeable and supportive AI assistant in the diabetes management app TIRUp.\n")
            sb.append("The app tracks continuous glucose monitoring (CGM), Time-In-Range (TIR), and therapy logs.\n\n")

            sb.append("CRITICAL SAFETY RULES:\n")
            sb.append("1. You are NOT a doctor or medical device. You MUST NEVER prescribe or alter insulin dosages or medications!\n")
            sb.append("2. All insights are educational/analytical. In risky situations (hypo, prolonged high glucose, ketones), remind the user to verify with a fingerstick blood glucose meter and consult their healthcare provider.\n\n")

            sb.append("YOUR TASKS:\n")
            sb.append("1. Trend and correlation analysis: correlate meals, pre-bolus timings, and glucose curves based on provided patient data.\n")
            sb.append("2. Meal and photo analysis: estimate composition, portion weight, Carbs (g), Protein (g), Fat (g), Bread Units (BU / carbs exchanges), Glycemic Index (GI), and protein-fat delay risk.\n")
            sb.append("3. STRUCTURED FOOD OUTPUT: Whenever analyzing food (photo or text), conclude your message with a structured JSON block:\n")
            sb.append("```json:food\n")
            sb.append("{\"food_detected\":true,\"title\":\"Meal name\",\"carbs_g\":45.0,\"protein_g\":20.0,\"fat_g\":10.0,\"bu\":3.8,\"comment\":\"~250g portion\"}\n")
            sb.append("```\n")
            sb.append("If no food is detected, omit this block or return `{\"food_detected\":false}`.\n\n")
            sb.append("User's active units: $unitStr.\n")
        }

        if (glycemicContext.isNotBlank()) {
            sb.append("\n========================================\n")
            sb.append(glycemicContext)
            sb.append("\n========================================\n")
        }

        return sb.toString()
    }
}

