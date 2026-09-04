package com.tirup.app.domain.alert

import com.tirup.app.domain.model.GlucoseUnit
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
        // Mock 14:35 timestamp
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 35)
        }
        val msg = EmergencySmsBuilder.buildQueryReplyMessage(
            patientName = "Иван",
            glucoseValueMmol = 6.4,
            trendArrow = "→",
            deltaMmol = 0.2,
            readingTimestamp = calendar.timeInMillis,
            todayTirPercent = 82,
            iob = 1.2,
            isRu = true,
            unit = GlucoseUnit.MMOL_L
        )

        assertTrue(msg.contains("TIRUp: Иван 6.4 ммоль (→) в 14:35 (+0.2). TIR: 82%. IoB: 1.2U"))
        assertTrue("Query reply SMS length (${msg.length}) must be <= 70 chars", msg.length <= 70)
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
