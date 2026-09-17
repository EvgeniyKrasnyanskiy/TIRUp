package com.tirup.app.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tirup.app.domain.model.LabHba1cRecord
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Hba1cHistoryDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hba1cHistoryDialog_rendersTitleAndActions_andDismisses() {
        var dismissed = false
        val records = listOf(
            LabHba1cRecord(
                id = 1L,
                timestamp = System.currentTimeMillis() - 30 * 86_400_000L,
                valuePercent = 6.4,
                labName = "Инвитро",
                notes = "Плановый контроль"
            )
        )

        composeTestRule.setContent {
            Hba1cHistoryDialog(
                records = records,
                sensorGmi90d = 6.2,
                meanGlucose90dMmol = 6.5,
                tirPercent90d = 82,
                skippedQuarterTimestamp = 0L,
                isRu = true,
                onAddRecord = { _, _, _, _ -> },
                onDeleteRecord = {},
                onSkipQuarter = {},
                onExportPdf = null,
                onDismiss = { dismissed = true }
            )
        }

        // Title and metrics check
        composeTestRule.onNodeWithText("Журнал HbA1c").assertIsDisplayed()
        composeTestRule.onNodeWithText("6.4%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Выписка PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Закрыть").assertIsDisplayed()

        // Click Close
        composeTestRule.onNodeWithText("Закрыть").performClick()
        assertTrue("onDismiss should be called on Close click", dismissed)
    }

    @Test
    fun hba1cHistoryDialog_submittingEmptyValue_showsValidationError() {
        composeTestRule.setContent {
            Hba1cHistoryDialog(
                records = emptyList(),
                sensorGmi90d = null,
                meanGlucose90dMmol = null,
                tirPercent90d = null,
                skippedQuarterTimestamp = 0L,
                isRu = true,
                onAddRecord = { _, _, _, _ -> },
                onDeleteRecord = {},
                onSkipQuarter = {},
                onExportPdf = null,
                onDismiss = {}
            )
        }

        // Click Save Result with empty input
        composeTestRule.onNodeWithText("Сохранить анализ").performClick()

        // Validation error should be displayed
        composeTestRule.onNodeWithText("Введите значение от 3.0 до 20.0%").assertIsDisplayed()
    }
}
