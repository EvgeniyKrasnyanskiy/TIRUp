package com.tirup.app.domain.calculator

import com.tirup.app.domain.model.BmiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbRecommendationCalculatorTest {

    @Test
    fun testPediatricToddlerGroup() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 4,
            gender = "M",
            bmiCategory = BmiCategory.NORMAL
        )

        assertEquals(130..160, rec.dailyGramsRange)
        assertTrue(rec.dailyXeRange.start >= 10.5)
        assertTrue(rec.dailyXeRange.endInclusive <= 13.5)
        assertNotNull(rec.clinicalWarningRu)
        assertTrue(rec.clinicalWarningRu!!.contains("ISPAD"))
    }

    @Test
    fun testPediatricPrimarySchoolGroup() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 8,
            gender = "F",
            bmiCategory = BmiCategory.NORMAL
        )

        assertEquals(160..210, rec.dailyGramsRange)
        assertNotNull(rec.clinicalWarningRu)
    }

    @Test
    fun testPediatricTeenagerGroup() {
        val boyRec = CarbRecommendationCalculator.calculate(
            age = 15,
            gender = "M",
            bmiCategory = BmiCategory.NORMAL
        )
        assertEquals(230..300, boyRec.dailyGramsRange)

        val girlRec = CarbRecommendationCalculator.calculate(
            age = 16,
            gender = "F",
            bmiCategory = BmiCategory.NORMAL
        )
        assertEquals(200..260, girlRec.dailyGramsRange)
    }

    @Test
    fun testAdultNormalWeight() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 30,
            gender = "M",
            bmiCategory = BmiCategory.NORMAL
        )

        assertEquals(180..240, rec.dailyGramsRange)
        assertEquals(null, rec.clinicalWarningRu)
        assertTrue(rec.clinicalRationaleRu.contains("Нормальная масса тела"))
    }

    @Test
    fun testAdultOverweight() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 45,
            gender = "M",
            bmiCategory = BmiCategory.OVERWEIGHT
        )

        assertEquals(140..180, rec.dailyGramsRange)
        assertTrue(rec.clinicalRationaleRu.contains("Избыточная масса тела"))
    }

    @Test
    fun testAdultObese() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 50,
            gender = "F",
            bmiCategory = BmiCategory.OBESE_1
        )

        assertEquals(130..155, rec.dailyGramsRange)
        assertNotNull(rec.clinicalWarningRu)
        assertTrue(rec.clinicalWarningRu!!.contains("130 г"))
    }

    @Test
    fun testAdultUnderweight() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 22,
            gender = "F",
            bmiCategory = BmiCategory.UNDERWEIGHT
        )

        assertEquals(220..280, rec.dailyGramsRange)
        assertTrue(rec.clinicalRationaleRu.contains("Дефицит массы тела"))
    }

    @Test
    fun testMealDistributionConsistency() {
        val rec = CarbRecommendationCalculator.calculate(
            age = 30,
            gender = "M",
            bmiCategory = BmiCategory.NORMAL
        )

        val dist = rec.distribution
        assertTrue(dist.breakfastGrams.first > 0)
        assertTrue(dist.lunchGrams.first > dist.breakfastGrams.first)
        assertTrue(dist.dinnerGrams.first > 0)
        assertTrue(dist.snacksGrams.first > 0)

        val summaryRu = rec.formatDailySummary(isRu = true)
        assertTrue(summaryRu.contains("180–240 г/сут"))
        assertTrue(summaryRu.contains("ХЕ"))

        val bFastStr = dist.formatBreakfast(isRu = true)
        assertTrue(bFastStr.contains("ХЕ"))
    }
}
