package com.tirup.app.domain.alert

import com.tirup.app.domain.model.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySmsBuilderTest {

    @Test
    fun testBuildEmergencyMessageRussianWithLocation() {
        val msg = EmergencySmsBuilder.buildEmergencyMessage(
            patientName = "Алексей",
            glucoseValueMmol = 2.6,
            trendArrow = "↓↓",
            delayMinutes = 5,
            latitude = 55.7558,
            longitude = 37.6173,
            isRu = true,
            unit = GlucoseUnit.MMOL_L
        )

        assertTrue(msg.contains("SOS! Алексей - критич. гипо: 2.6 ммоль (↓↓)! Сирена 5м без реакции"))
        assertTrue(msg.contains("https://maps.google.com/?q=55.755800,37.617300"))
    }

    @Test
    fun testBuildEmergencyMessageWithoutLocation() {
        val msg = EmergencySmsBuilder.buildEmergencyMessage(
            patientName = "Елена",
            glucoseValueMmol = 2.4,
            trendArrow = "↓",
            delayMinutes = 3,
            latitude = null,
            longitude = null,
            isRu = true,
            unit = GlucoseUnit.MMOL_L
        )

        assertTrue(msg == "SOS! Елена - критич. гипо: 2.4 ммоль (↓)! Сирена 3м без реакции")
        assertTrue("SMS message length (${msg.length}) must be <= 67 chars for single SMS segment", msg.length <= 67)
        assertTrue(!msg.contains("https://maps"))
    }

    @Test
    fun testBuildEmergencyMessageTestFlag() {
        val msg = EmergencySmsBuilder.buildEmergencyMessage(
            patientName = "Иванов Иван",
            glucoseValueMmol = 2.8,
            trendArrow = "→",
            delayMinutes = 5,
            latitude = null,
            longitude = null,
            isRu = true,
            unit = GlucoseUnit.MMOL_L,
            isTest = true
        )

        assertTrue(msg.startsWith("SOS! [ТЕСТ] Иван - критич. гипо: 2.8 ммоль"))
        assertTrue("Test SOS SMS (${msg.length}) must be <= 70 chars for single SMS segment", msg.length <= 70)
        assertEquals("SOS! [ТЕСТ] Иван - критич. гипо: 2.8 ммоль (→)! Сирена 5м", msg)
    }

    @Test
    fun testBuildEmergencyMessageEnglishWithMgDl() {
        val msg = EmergencySmsBuilder.buildEmergencyMessage(
            patientName = "John",
            glucoseValueMmol = 2.5, // 2.5 * 18.0182 ~ 45 mg/dL
            trendArrow = "↓↓",
            delayMinutes = 10,
            latitude = 40.7128,
            longitude = -74.0060,
            isRu = false,
            unit = GlucoseUnit.MG_DL
        )

        assertTrue(msg.contains("SOS! John - critical hypo: 45 mg/dL (↓↓)! Alarm 10m no reaction"))
        assertTrue(msg.contains("https://maps.google.com/?q=40.712800,-74.006000"))
    }

    @Test
    fun testBuildTestMessage() {
        val msgRu = EmergencySmsBuilder.buildTestMessage("Мария", isRu = true)
        assertTrue(msgRu.contains("Мария"))
        assertTrue(msgRu.contains("Тест SMS"))
        assertTrue("Test SMS length (${msgRu.length}) must fit in single SMS (<= 70 chars)", msgRu.length <= 70)

        val msgEn = EmergencySmsBuilder.buildTestMessage("", isRu = false)
        assertTrue(msgEn.contains("patient"))
        assertTrue(msgEn.contains("Test SMS"))
        assertTrue("Test SMS length (${msgEn.length}) must fit in single SMS (<= 70 chars)", msgEn.length <= 70)
    }

    @Test
    fun testBuildQueryReplyMessage() {
        // Mock 08:45 timestamp
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 45)
        }
        val msg = EmergencySmsBuilder.buildQueryReplyMessage(
            patientName = "Дамир",
            glucoseValueMmol = 5.8,
            trendArrow = "→",
            deltaMmol = 0.1,
            readingTimestamp = calendar.timeInMillis,
            todayTirPercent = 99,
            iob = 0.1,
            cob = 15.0,
            batteryPercent = 33,
            historyPoints = listOf(5.2, 5.5, 5.7),
            isRu = true,
            unit = GlucoseUnit.MMOL_L
        )

        assertEquals("Damir 5.8 (->) at 08:45 (+0.1) [5.2 > 5.5 > 5.7] TIR: 99% IoB: 0.1U CoB: 15g BAT: 33%", msg)
        assertTrue("Query reply SMS length (${msg.length}) must be <= 160 chars", msg.length <= EmergencySmsBuilder.MAX_QUERY_SMS_CHARS)
        // Verify 100% pure Latin/GSM-7 (no Cyrillic letters)
        assertTrue("Must not contain Cyrillic", !msg.matches(Regex(".*[а-яА-ЯёЁ].*")))
        assertTrue("Must not contain Unicode arrow", !msg.contains("→"))
    }

    @Test
    fun testStrictLengthLimit160CharsWithFullFio() {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
        }
        val msg = EmergencySmsBuilder.buildQueryReplyMessage(
            patientName = "Краснянский Евгений Юрьевич",
            glucoseValueMmol = 15.8,
            trendArrow = "⇈",
            deltaMmol = 2.4,
            readingTimestamp = calendar.timeInMillis,
            todayTirPercent = 74,
            iob = 4.5,
            cob = 20.0,
            batteryPercent = 88,
            historyPoints = listOf(11.2, 13.0, 14.5),
            isRu = true,
            unit = GlucoseUnit.MMOL_L
        )

        assertTrue("Should extract and transliterate 2nd word 'Evgeniy': $msg", msg.contains("Evgeniy"))
        assertTrue("Should not include surname", !msg.contains("Краснянский") && !msg.contains("Krasnyanskiy"))
        assertTrue("Message length (${msg.length}) must strictly be <= 160 chars: '$msg'", msg.length <= EmergencySmsBuilder.MAX_QUERY_SMS_CHARS)
        assertTrue("Must not contain Cyrillic", !msg.matches(Regex(".*[а-яА-ЯёЁ].*")))
    }

    @Test
    fun testGsm7TransliterationAndArrows() {
        assertEquals("Damir", Gsm7Transliteration.transliterate("Дамир"))
        assertEquals("Evgeniy", Gsm7Transliteration.transliterate("Евгений"))
        assertEquals("Aleksandr", Gsm7Transliteration.transliterate("Александр"))

        assertEquals("->", Gsm7Transliteration.normalizeArrow("→"))
        assertEquals("^", Gsm7Transliteration.normalizeArrow("↑"))
        assertEquals("v", Gsm7Transliteration.normalizeArrow("↓"))
        assertEquals("^>", Gsm7Transliteration.normalizeArrow("↗"))
        assertEquals("v>", Gsm7Transliteration.normalizeArrow("↘"))
        assertEquals("^^", Gsm7Transliteration.normalizeArrow("⇈"))
        assertEquals("vv", Gsm7Transliteration.normalizeArrow("⇊"))
    }

    @Test
    fun testBuildNoDataReplyMessage() {
        val msg = EmergencySmsBuilder.buildNoDataReplyMessage("Иван", isRu = true)
        assertTrue(msg.contains("Иван"))
        assertTrue(msg.contains("нет свежих данных"))
        assertTrue("No data SMS length (${msg.length}) must be <= 70 chars", msg.length <= 70)
    }

    @Test
    fun testMatchingPhone() {
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isMatchingPhone("+7 (999) 123-45-67", "89991234567"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isMatchingPhone("+79991234567", "8 (999) 123-45-67"))
        assertTrue(!com.tirup.app.data.receiver.SmsQueryReceiver.isMatchingPhone("+79991112233", "+79998889900"))
    }

    @Test
    fun testExtractShortName() {
        org.junit.Assert.assertEquals("Иван", EmergencySmsBuilder.extractShortName("Иванов Иван Иванович"))
        org.junit.Assert.assertEquals("Алексей", EmergencySmsBuilder.extractShortName("Петров Алексей"))
        org.junit.Assert.assertEquals("Елена", EmergencySmsBuilder.extractShortName("Елена"))
        org.junit.Assert.assertEquals("пациент", EmergencySmsBuilder.extractShortName(""))
        org.junit.Assert.assertEquals("пациент", EmergencySmsBuilder.extractShortName("   "))
        org.junit.Assert.assertEquals("John", EmergencySmsBuilder.extractShortName("Smith John", isRu = false))
        org.junit.Assert.assertEquals("patient", EmergencySmsBuilder.extractShortName("", isRu = false))
    }

    @Test
    fun testQueryTrigger() {
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("?"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("сахар"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("Сахар?"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("какой сахар?"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("sugar"))
        assertTrue(com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("bg"))
        assertTrue(!com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("Привет, как дела?"))
        assertTrue(!com.tirup.app.data.receiver.SmsQueryReceiver.isQueryTrigger("Купи хлеба"))
    }
}
