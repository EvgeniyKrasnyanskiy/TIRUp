package com.tirup.app.domain.alert

/**
 * Utility for converting Cyrillic text and Unicode symbols to strict GSM 7-bit alphabet.
 *
 * This ensures that outgoing SMS messages can utilize the full single-SMS segment limit of 160 characters
 * instead of falling back to UCS-2 (Unicode) encoding which restricts a single SMS to 70 characters.
 */
object Gsm7Transliteration {

    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",

        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D",
        'Е' to "E", 'Ё' to "E", 'Ж' to "Zh", 'З' to "Z", 'И' to "I",
        'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
        'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
        'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch",
        'Ш' to "Sh", 'Щ' to "Shch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "",
        'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
    )

    /**
     * Transliterates Cyrillic characters to Latin equivalents.
     */
    fun transliterate(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length + 8)
        for (ch in input) {
            val replacement = CYRILLIC_MAP[ch]
            if (replacement != null) {
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Converts Unicode trend arrows into safe ASCII equivalents to prevent UCS-2 encoding fallback.
     */
    fun normalizeArrow(arrow: String): String {
        val clean = arrow.trim()
        return when (clean) {
            "→", "->" -> "->"
            "↑", "^" -> "^"
            "↓", "v" -> "v"
            "↗", "^>" -> "^>"
            "↘", "v>" -> "v>"
            "⇈", "^^" -> "^^"
            "⇊", "vv" -> "vv"
            "↔", "<->" -> "<->"
            "←", "<-" -> "<-"
            else -> {
                // Handle compound or raw strings
                clean.replace("→", "->")
                    .replace("↑", "^")
                    .replace("↓", "v")
                    .replace("↗", "^>")
                    .replace("↘", "v>")
                    .replace("⇈", "^^")
                    .replace("⇊", "vv")
                    .replace("↔", "<->")
                    .replace("←", "<-")
            }
        }
    }

    /**
     * Ensures the entire string consists only of GSM 7-bit safe characters.
     * Replaces dashes, quotes, and strips any unrecognized symbols or emoji.
     */
    fun toGsm7Safe(input: String): String {
        // 1. Normalize arrows and transliterate
        val step1 = transliterate(input)
        val step2 = normalizeArrow(step1)

        // 2. Clean typography symbols
        val step3 = step2
            .replace('—', '-')
            .replace('–', '-')
            .replace('−', '-')
            .replace('«', '"')
            .replace('»', '"')
            .replace('“', '"')
            .replace('”', '"')
            .replace('’', '\'')
            .replace('`', '\'')
            .replace('•', '-')

        // 3. Keep only basic GSM 7-bit and extended ASCII characters
        val sb = StringBuilder(step3.length)
        for (ch in step3) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' -> sb.append(ch)
                ch in " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~" -> sb.append(ch)
                ch == '\n' || ch == '\r' -> sb.append(ch)
                else -> {
                    // Skip unsupported unicode / emoji to protect 160-char SMS limit
                }
            }
        }
        return sb.toString()
    }
}
