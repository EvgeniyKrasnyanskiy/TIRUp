package com.tirup.app.presentation.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tirup.app.domain.model.GlucoseStatistics
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.WeeklyDigest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyDigestSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createSampleDigest(): WeeklyDigest {
        val now = System.currentTimeMillis()
        val weekMs = 7 * 86_400_000L
        return WeeklyDigest(
            currentWeekStart = now - weekMs,
            currentWeekEnd = now,
            previousWeekStart = now - 2 * weekMs,
            previousWeekEnd = now - weekMs,
            currentStats = GlucoseStatistics(
                tirPercent = 78.0,
                tingPercent = 55.0,
                tbrLowPercent = 1.8,
                tbrVeryLowPercent = 0.2,
                cvPercent = 29.5,
                meanMmol = 6.4,
                activeTimePercent = 95.0
            ),
            previousStats = GlucoseStatistics(
                tirPercent = 72.0,
                tingPercent = 50.0,
                tbrLowPercent = 2.5,
                tbrVeryLowPercent = 0.5,
                cvPercent = 33.0,
                meanMmol = 6.9,
                activeTimePercent = 90.0
            ),
            hasSufficientData = true,
            tirDelta = 6.0,
            tingDelta = 5.0,
            tbrDelta = -1.0,
            cvDelta = -3.5,
            meanDeltaMmol = -0.5,
            hypoCountCurrent = 1,
            hypoCountPrevious = 3,
            headline = "Отличная неделя: TIR вырос на 6%!",
            keyInsights = listOf(
                "Стабильный ночной профиль",
                "Снижение гипогликемий с 3 до 1"
            ),
            recommendation = "Продолжайте придерживаться текущего графика болюсов."
        )
    }

    @Test
    fun weeklyDigestSheet_rendersTitleAndActions_andDismissesOnClick() {
        var dismissed = false
        val digest = createSampleDigest()

        composeTestRule.setContent {
            WeeklyDigestSheet(
                digest = digest,
                isRu = true,
                unit = GlucoseUnit.MMOL_L,
                onDismiss = { dismissed = true }
            )
        }

        // 1. Verify Dialog Title & Content are displayed
        composeTestRule.onNodeWithText("Воскресный дайджест").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отличная неделя: TIR вырос на 6%!").assertIsDisplayed()

        // 2. Verify Actions Bar has "Поделиться" and "Закрыть"
        composeTestRule.onNodeWithText("Поделиться").assertIsDisplayed()
        composeTestRule.onNodeWithText("Закрыть").assertIsDisplayed()

        // 3. Click "Закрыть" and verify callback triggered
        composeTestRule.onNodeWithText("Закрыть").performClick()
        assertTrue("onDismiss should be called when Close button is clicked", dismissed)
    }

    @Test
    fun weeklyDigestSheet_englishLocalization_rendersCorrectLabels() {
        var dismissed = false
        val digest = createSampleDigest().copy(
            headline = "Great week: TIR improved by 6%!",
            keyInsights = listOf("Stable nocturnal profile"),
            recommendation = "Keep your current insulin routine."
        )

        composeTestRule.setContent {
            WeeklyDigestSheet(
                digest = digest,
                isRu = false,
                unit = GlucoseUnit.MMOL_L,
                onDismiss = { dismissed = true }
            )
        }

        // English Title & Actions
        composeTestRule.onNodeWithText("Weekly Sunday Digest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()

        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue("onDismiss should be called on English Close click", dismissed)
    }
}
