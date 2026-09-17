package com.tirup.app.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tirup.app.domain.model.LancetStatus
import com.tirup.app.domain.model.PumpSetStatus
import com.tirup.app.domain.model.SensorStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceStatusModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deviceStatusModal_rendersAllSuppliesSections() {
        val now = System.currentTimeMillis()
        val sensor = SensorStatus(installedAt = now - 86_400_000L, lastUsedDurationDays = 14)
        val pump = PumpSetStatus(installedAt = now - 43_200_000L, lastUsedDurationDays = 3)
        val lancet = LancetStatus(installedAt = now - 10_000_000L, lastUsedDurationDays = 3)

        composeTestRule.setContent {
            DeviceStatusModal(
                sensorStatus = sensor,
                pumpSetStatus = pump,
                lancetStatus = lancet,
                showSensor = true,
                showPump = true,
                showLancet = true,
                isRu = true,
                onDismiss = {},
                onNewSensor = { _, _ -> },
                onNewPumpSet = { _, _ -> },
                onNewLancet = { _, _ -> }
            )
        }

        // Check Modal Title and all 3 sections
        composeTestRule.onNodeWithText("Устройства и расходники").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сенсор CGM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Инфузионный набор").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ланцет").assertIsDisplayed()
        composeTestRule.onNodeWithText("Новый сенсор").assertIsDisplayed()
    }

    @Test
    fun deviceStatusModal_clickingNewSensor_showsConfirmationDialog() {
        val now = System.currentTimeMillis()
        val sensor = SensorStatus(installedAt = now - 86_400_000L, lastUsedDurationDays = 14)
        val pump = PumpSetStatus(installedAt = now, lastUsedDurationDays = 3)
        val lancet = LancetStatus(installedAt = now, lastUsedDurationDays = 3)

        var confirmedSensorDays = 0
        composeTestRule.setContent {
            DeviceStatusModal(
                sensorStatus = sensor,
                pumpSetStatus = pump,
                lancetStatus = lancet,
                showSensor = true,
                showPump = true,
                showLancet = true,
                isRu = true,
                onDismiss = {},
                onNewSensor = { days, _ -> confirmedSensorDays = days },
                onNewPumpSet = { _, _ -> },
                onNewLancet = { _, _ -> }
            )
        }

        // Click "Новый сенсор" button to open confirmation dialog
        composeTestRule.onNodeWithText("Новый сенсор").performClick()

        // Confirmation dialog title should appear
        composeTestRule.onNodeWithText("Подтверждение смены сенсора").assertIsDisplayed()

        // Click confirm in the dialog
        composeTestRule.onNodeWithText("Подтвердить").performClick()
        assertTrue("Sensor change should be confirmed with positive duration days", confirmedSensorDays > 0)
    }
}
