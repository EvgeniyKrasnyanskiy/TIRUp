package com.tirup.app.domain.util

object PluralUtils {

    /**
     * Russian pluralization with count prefix:
     * pluralizeRu(1, "день", "дня", "дней") -> "1 день"
     * pluralizeRu(2, "день", "дня", "дней") -> "2 дня"
     * pluralizeRu(5, "день", "дня", "дней") -> "5 дней"
     */
    fun pluralizeRu(count: Int, one: String, few: String, many: String): String {
        val word = wordRu(count, one, few, many)
        return "$count $word"
    }

    /**
     * Returns appropriate Russian word form without count prefix:
     * wordRu(1, "событие", "события", "событий") -> "событие"
     * wordRu(3, "событие", "события", "событий") -> "события"
     * wordRu(5, "событие", "события", "событий") -> "событий"
     */
    fun wordRu(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..19 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    /**
     * "1 событие", "2 события", "5 событий" (RU) or "1 event", "2 events" (EN)
     */
    fun formatEvents(count: Int, isRu: Boolean): String {
        return if (isRu) {
            pluralizeRu(count, "событие", "события", "событий")
        } else {
            if (count == 1) "1 event" else "$count events"
        }
    }

    /**
     * "1 год", "2 года", "5 лет", "21 год" (RU) or "1 yr", "2 yrs" (EN)
     */
    fun formatYears(count: Int, isRu: Boolean): String {
        return if (isRu) {
            pluralizeRu(count, "год", "года", "лет")
        } else {
            if (count == 1) "1 yr" else "$count yrs"
        }
    }

    /**
     * "1 день", "2 дня", "5 дней", "21 день" (RU) or "1 day", "2 days" (EN)
     */
    fun formatDays(count: Int, isRu: Boolean): String {
        return if (isRu) {
            pluralizeRu(count, "день", "дня", "дней")
        } else {
            if (count == 1) "1 day" else "$count days"
        }
    }
}
