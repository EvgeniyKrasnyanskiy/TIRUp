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

        assertTrue(msg.contains("ВНИМАНИЕ!"))
        assertTrue(msg.contains("Алексей"))
        assertTrue(msg.contains("2.6 ммоль/л (↓↓)"))
        assertTrue(msg.contains("5 мин"))
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

        assertTrue(msg.contains("Елена"))
        assertTrue(msg.contains("2.4 ммоль/л (↓)"))
        assertTrue(msg.contains("3 мин"))
        assertTrue(!msg.contains("Координаты"))
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

        assertTrue(msg.contains("EMERGENCY!"))
        assertTrue(msg.contains("John"))
        assertTrue(msg.contains("45 mg/dL (↓↓)"))
        assertTrue(msg.contains("10 min"))
        assertTrue(msg.contains("https://maps.google.com/?q=40.712800,-74.006000"))
    }

    @Test
    fun testBuildTestMessage() {
        val msgRu = EmergencySmsBuilder.buildTestMessage("Мария", isRu = true)
        assertTrue(msgRu.contains("Мария"))
        assertTrue(msgRu.contains("Тестовое"))

        val msgEn = EmergencySmsBuilder.buildTestMessage("", isRu = false)
        assertTrue(msgEn.contains("patient"))
        assertTrue(msgEn.contains("Test alert"))
    }
}
