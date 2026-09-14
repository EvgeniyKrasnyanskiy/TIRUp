package com.tirup.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralUtilsTest {

    @Test
    fun testWordRu() {
        assertEquals("событие", PluralUtils.wordRu(1, "событие", "события", "событий"))
        assertEquals("события", PluralUtils.wordRu(2, "событие", "события", "событий"))
        assertEquals("события", PluralUtils.wordRu(3, "событие", "события", "событий"))
        assertEquals("события", PluralUtils.wordRu(4, "событие", "события", "событий"))
        assertEquals("событий", PluralUtils.wordRu(5, "событие", "события", "событий"))
        assertEquals("событий", PluralUtils.wordRu(11, "событие", "события", "событий"))
        assertEquals("событий", PluralUtils.wordRu(12, "событие", "события", "событий"))
        assertEquals("событий", PluralUtils.wordRu(14, "событие", "события", "событий"))
        assertEquals("событие", PluralUtils.wordRu(21, "событие", "события", "событий"))
        assertEquals("события", PluralUtils.wordRu(22, "событие", "события", "событий"))
        assertEquals("событий", PluralUtils.wordRu(25, "событие", "события", "событий"))
    }

    @Test
    fun testFormatEvents() {
        assertEquals("1 событие", PluralUtils.formatEvents(1, true))
        assertEquals("2 события", PluralUtils.formatEvents(2, true))
        assertEquals("5 событий", PluralUtils.formatEvents(5, true))
        assertEquals("11 событий", PluralUtils.formatEvents(11, true))
        assertEquals("21 событие", PluralUtils.formatEvents(21, true))

        assertEquals("1 event", PluralUtils.formatEvents(1, false))
        assertEquals("2 events", PluralUtils.formatEvents(2, false))
        assertEquals("0 events", PluralUtils.formatEvents(0, false))
    }

    @Test
    fun testFormatYears() {
        assertEquals("1 год", PluralUtils.formatYears(1, true))
        assertEquals("2 года", PluralUtils.formatYears(2, true))
        assertEquals("4 года", PluralUtils.formatYears(4, true))
        assertEquals("5 лет", PluralUtils.formatYears(5, true))
        assertEquals("11 лет", PluralUtils.formatYears(11, true))
        assertEquals("14 лет", PluralUtils.formatYears(14, true))
        assertEquals("21 год", PluralUtils.formatYears(21, true))
        assertEquals("24 года", PluralUtils.formatYears(24, true))
        assertEquals("25 лет", PluralUtils.formatYears(25, true))

        assertEquals("1 yr", PluralUtils.formatYears(1, false))
        assertEquals("5 yrs", PluralUtils.formatYears(5, false))
    }

    @Test
    fun testFormatDays() {
        assertEquals("1 день", PluralUtils.formatDays(1, true))
        assertEquals("2 дня", PluralUtils.formatDays(2, true))
        assertEquals("5 дней", PluralUtils.formatDays(5, true))
        assertEquals("11 дней", PluralUtils.formatDays(11, true))
        assertEquals("21 день", PluralUtils.formatDays(21, true))
        assertEquals("22 дня", PluralUtils.formatDays(22, true))

        assertEquals("1 day", PluralUtils.formatDays(1, false))
        assertEquals("7 days", PluralUtils.formatDays(7, false))
    }
}
