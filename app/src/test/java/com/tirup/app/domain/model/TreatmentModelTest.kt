package com.tirup.app.domain.model

import com.tirup.app.data.local.entity.TreatmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreatmentModelTest {

    @Test
    fun testTreatmentFlags() {
        val insulinOnly = Treatment(timestamp = 1000L, insulinUnits = 4.5, carbsGrams = null)
        assertTrue(insulinOnly.hasInsulin)
        assertFalse(insulinOnly.hasCarbs)
        assertFalse(insulinOnly.isCombo)

        val carbsOnly = Treatment(timestamp = 1000L, insulinUnits = 0.0, carbsGrams = 50.0)
        assertFalse(carbsOnly.hasInsulin)
        assertTrue(carbsOnly.hasCarbs)
        assertFalse(carbsOnly.isCombo)

        val combo = Treatment(timestamp = 1000L, insulinUnits = 3.0, carbsGrams = 40.0)
        assertTrue(combo.hasInsulin)
        assertTrue(combo.hasCarbs)
        assertTrue(combo.isCombo)
    }

    @Test
    fun testEntityDomainMapping() {
        val original = Treatment(
            id = 42L,
            timestamp = 1700000000000L,
            insulinUnits = 5.5,
            carbsGrams = 60.0,
            notes = "Lunch bolus",
            source = "XDRIP"
        )

        val entity = TreatmentEntity.fromDomain(original)
        assertEquals(42L, entity.id)
        assertEquals(1700000000000L, entity.timestamp)
        assertEquals(5.5, entity.insulinUnits!!, 0.001)
        assertEquals(60.0, entity.carbsGrams!!, 0.001)
        assertEquals("Lunch bolus", entity.notes)
        assertEquals("XDRIP", entity.source)

        val mappedBack = entity.toDomain()
        assertEquals(original, mappedBack)
    }
}
