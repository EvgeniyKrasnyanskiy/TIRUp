package com.tirup.app.domain.ai

import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.Treatment
import com.tirup.app.domain.model.UserSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGlycemicContextBuilderTest {

    @Test
    fun testTimeBounds() {
        assertNull(AiGlycemicContextBuilder.getTimeBounds(AiContextPeriod.NO_DATA))

        val todayBounds = AiGlycemicContextBuilder.getTimeBounds(AiContextPeriod.TODAY)
        assertNotNull(todayBounds)
        assertTrue(todayBounds!!.second >= todayBounds.first)

        val days7Bounds = AiGlycemicContextBuilder.getTimeBounds(AiContextPeriod.DAYS_7)
        assertNotNull(days7Bounds)
        assertTrue(days7Bounds!!.second - days7Bounds.first >= 6L * 24 * 3600 * 1000L)
    }

    @Test
    fun testBuildContextWithReadingsAndTreatments() {
        val now = System.currentTimeMillis()
        val readings = listOf(
            GlucoseReading(timestamp = now - 3600000, valueMmol = 6.5),
            GlucoseReading(timestamp = now - 1800000, valueMmol = 4.2),
            GlucoseReading(timestamp = now, valueMmol = 8.1)
        )
        val treatments = listOf(
            Treatment(timestamp = now - 3600000, carbsGrams = 35.0, notes = "Яблоко и сыр")
        )

        val settings = UserSettings(unit = GlucoseUnit.MMOL_L, language = "RU")
        val context = AiGlycemicContextBuilder.buildContext(
            period = AiContextPeriod.TODAY,
            readings = readings,
            treatments = treatments,
            userSettings = settings
        )

        assertTrue(context.contains("TIR"))
        assertTrue(context.contains("ммоль/л"))
        assertTrue(context.contains("35г углев"))
        assertTrue(context.contains("Яблоко и сыр"))
    }

    @Test
    fun testBuildSystemPromptSafetyRules() {
        val settingsRu = UserSettings(language = "RU")
        val promptRu = AiGlycemicContextBuilder.buildSystemPrompt(settingsRu, "Test Context")
        assertTrue(promptRu.contains("НЕ имеешь права назначать или корректировать дозировки инсулина"))
        assertTrue(promptRu.contains("Test Context"))
        assertTrue(promptRu.contains("json:food"))

        val settingsEn = UserSettings(language = "EN")
        val promptEn = AiGlycemicContextBuilder.buildSystemPrompt(settingsEn, "")
        assertTrue(promptEn.contains("MUST NEVER prescribe or alter insulin dosages"))
    }
}

