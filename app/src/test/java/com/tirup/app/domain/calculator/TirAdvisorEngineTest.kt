package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.AGPPercentileBin
import com.tirup.app.domain.model.GlucoseStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TirAdvisorEngineTest {

    @Test
    fun testLowDataReturnsCollectingInfo() {
        val insights = TirAdvisorEngine.generateInsights(
            bins = emptyList(),
            stats = GlucoseStatistics(totalCount = 10)
        )
        assertEquals(1, insights.size)
        assertEquals("collect_more_data", insights[0].id)
    }

    @Test
    fun testSafetyPriorityWhenTbrHigh() {
        val stats = GlucoseStatistics(
            totalCount = 100,
            tbrLowPercent = 5.5,
            tbrVeryLowPercent = 1.0,
            tarHighPercent = 30.0
        )
        val insights = TirAdvisorEngine.generateInsights(emptyList(), stats)
        assertEquals("tbr_safety", insights[0].id)
        assertEquals(1, insights[0].priority)
    }

    @Test
    fun testPreBolusTriggerWhenTarHighAndTbrSafe() {
        val stats = GlucoseStatistics(
            totalCount = 150,
            tbrLowPercent = 1.0,
            tbrVeryLowPercent = 0.0,
            tarHighPercent = 28.0
        )
        val insights = TirAdvisorEngine.generateInsights(emptyList(), stats)
        assertTrue(insights.any { it.id == "pre_bolus_timing" })
    }

    @Test
    fun testHighPerformanceTightRangeTrigger() {
        val stats = GlucoseStatistics(
            totalCount = 200,
            tirPercent = 82.0,
            tbrLowPercent = 2.0,
            tbrVeryLowPercent = 0.0,
            tarHighPercent = 10.0,
            cvPercent = 25.0
        )
        val insights = TirAdvisorEngine.generateInsights(emptyList(), stats)
        assertTrue(insights.any { it.id == "tight_range_focus" })
    }
}
