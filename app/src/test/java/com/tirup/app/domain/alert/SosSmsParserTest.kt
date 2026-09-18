package com.tirup.app.domain.alert

import org.junit.Assert.*
import org.junit.Test

class SosSmsParserTest {

    @Test
    fun testIsSosMessage() {
        // Valid SOS messages
        assertTrue(SosSmsParser.isSosMessage("SOS! Алексей - критич. гипо: 2.6 ммоль (↓↓)! Сирена 5м без реакции"))
        assertTrue(SosSmsParser.isSosMessage("SOS! John - critical hypo: 45 mg/dL (↓↓)! Alarm 10m no reaction"))
        assertTrue(SosSmsParser.isSosMessage("SOS! [ТЕСТ ОПЕКУНА] Ваня - критич. гипо: 2.8 ммоль/л (↓)! Сирена 5м без реакции"))
        assertTrue(SosSmsParser.isSosMessage("SOS! [ТЕСТ] Ваня - критич. гипо: 2.8 ммоль (→)! Сирена 5м без реакции"))
        assertTrue(SosSmsParser.isSosMessage("[ТЕСТ] SOS! Ваня - критич. гипо: 2.8 ммоль (→)! Сирена 5м без реакции"))
        
        // Irrelevant messages
        assertFalse(SosSmsParser.isSosMessage("сахар"))
        assertFalse(SosSmsParser.isSosMessage("Привет, как дела?"))
        assertFalse(SosSmsParser.isSosMessage("Код подтверждения: 1234"))
        assertFalse(SosSmsParser.isSosMessage("SOS! Скидки в магазине 50%"))
    }

    @Test
    fun testParseRussianSosWithLocation() {
        val body = "SOS! Алексей - критич. гипо: 2.6 ммоль (↓↓)! Сирена 5м без реакции. Гео: https://maps.google.com/?q=55.755800,37.617300"
        val sender = "+79991234567"
        val result = SosSmsParser.parse(body, sender)

        assertNotNull(result)
        result!!
        assertEquals(sender, result.senderPhone)
        assertEquals("Алексей", result.patientName)
        assertEquals("2.6 ммоль", result.glucoseDisplay)
        assertEquals("↓↓", result.trendArrow)
        assertEquals(5, result.delayMinutes)
        assertEquals("https://maps.google.com/?q=55.755800,37.617300", result.mapsUrl)
        assertFalse(result.isTest)
    }

    @Test
    fun testParseRussianSosWithoutLocation() {
        val body = "SOS! Елена - критич. гипо: 2.4 ммоль (↓)! Сирена 3м без реакции"
        val sender = "+79997654321"
        val result = SosSmsParser.parse(body, sender)

        assertNotNull(result)
        result!!
        assertEquals(sender, result.senderPhone)
        assertEquals("Елена", result.patientName)
        assertEquals("2.4 ммоль", result.glucoseDisplay)
        assertEquals("↓", result.trendArrow)
        assertEquals(3, result.delayMinutes)
        assertNull(result.mapsUrl)
        assertFalse(result.isTest)
    }

    @Test
    fun testParseEnglishSosWithLocation() {
        val body = "SOS! John - critical hypo: 45 mg/dL (↓↓)! Alarm 10m no reaction. Loc: https://maps.google.com/?q=40.712800,-74.006000"
        val sender = "+12025550199"
        val result = SosSmsParser.parse(body, sender)

        assertNotNull(result)
        result!!
        assertEquals(sender, result.senderPhone)
        assertEquals("John", result.patientName)
        assertEquals("45 mg/dL", result.glucoseDisplay)
        assertEquals("↓↓", result.trendArrow)
        assertEquals(10, result.delayMinutes)
        assertEquals("https://maps.google.com/?q=40.712800,-74.006000", result.mapsUrl)
        assertFalse(result.isTest)
    }

    @Test
    fun testParseCaregiverTestMessage() {
        val body = "SOS! [ТЕСТ ОПЕКУНА] Михаил - критич. гипо: 2.8 ммоль/л (↓)! Сирена 5м без реакции. Гео: https://maps.google.com/?q=55.75,37.61"
        val sender = "+79001112233"
        val result = SosSmsParser.parse(body, sender)

        assertNotNull(result)
        result!!
        assertTrue(result.isTest)
        assertEquals("Михаил", result.patientName)
        assertEquals("2.8 ммоль/л", result.glucoseDisplay)
        assertEquals("↓", result.trendArrow)
        assertEquals(5, result.delayMinutes)
        assertNotNull(result.mapsUrl)
    }

    @Test
    fun testParseLeadingTestPrefix() {
        val body = "[ТЕСТ] SOS! Ваня - критич. гипо: 2.8 ммоль (→)! Сирена 5м без реакции"
        val sender = "+79001112233"
        val result = SosSmsParser.parse(body, sender)

        assertNotNull(result)
        result!!
        assertTrue(result.isTest)
        assertEquals("Ваня", result.patientName)
        assertEquals("2.8 ммоль", result.glucoseDisplay)
        assertEquals("→", result.trendArrow)
        assertEquals(5, result.delayMinutes)
    }

    @Test
    fun testParseNonSosReturnsNull() {
        val result = SosSmsParser.parse("Обычное текстовое сообщение", "+79001112233")
        assertNull(result)
    }
}
