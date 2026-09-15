package com.tirup.app.presentation.focus

import com.tirup.app.domain.model.Treatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreatmentClusterTest {

    @Test
    fun testEmptyTreatmentsReturnEmptyClusters() {
        val insulinClusters = clusterTreatments(emptyList(), isInsulin = true)
        val carbsClusters = clusterTreatments(emptyList(), isInsulin = false)
        assertTrue(insulinClusters.isEmpty())
        assertTrue(carbsClusters.isEmpty())
    }

    @Test
    fun testSingleInsulinTreatment() {
        val treatments = listOf(
            Treatment(id = 1L, timestamp = 1000000L, insulinUnits = 4.0)
        )
        val clusters = clusterTreatments(treatments, isInsulin = true)
        assertEquals(1, clusters.size)
        val cluster = clusters.first()
        assertEquals(1L, cluster.id)
        assertTrue(cluster.isSingle)
        assertEquals(4.0, cluster.totalInsulin, 0.001)
        assertEquals("4U", cluster.displayText)
    }

    @Test
    fun testSingleDecimalInsulinTreatment() {
        val treatments = listOf(
            Treatment(id = 1L, timestamp = 1000000L, insulinUnits = 1.5)
        )
        val clusters = clusterTreatments(treatments, isInsulin = true)
        assertEquals("1.5U", clusters.first().displayText)
    }

    @Test
    fun testInsulinClusteringWithin5Minutes() {
        val baseTime = 1000000L
        val treatments = listOf(
            Treatment(id = 1L, timestamp = baseTime, insulinUnits = 4.0),
            Treatment(id = 2L, timestamp = baseTime + 2 * 60 * 1000L, insulinUnits = 2.0) // +2 min
        )
        val clusters = clusterTreatments(treatments, isInsulin = true)
        assertEquals(1, clusters.size)
        val cluster = clusters.first()
        assertFalse(cluster.isSingle)
        assertEquals(2, cluster.treatments.size)
        assertEquals(6.0, cluster.totalInsulin, 0.001)
        assertEquals("4+2U", cluster.displayText)
    }

    @Test
    fun testInsulinLongClusteringFallback() {
        val baseTime = 1000000L
        val treatments = listOf(
            Treatment(id = 1L, timestamp = baseTime, insulinUnits = 2.0),
            Treatment(id = 2L, timestamp = baseTime + 60 * 1000L, insulinUnits = 3.0),
            Treatment(id = 3L, timestamp = baseTime + 120 * 1000L, insulinUnits = 2.0),
            Treatment(id = 4L, timestamp = baseTime + 180 * 1000L, insulinUnits = 4.0),
            Treatment(id = 5L, timestamp = baseTime + 240 * 1000L, insulinUnits = 1.0)
        ) // "2+3+2+4+1U" is 10 chars > 8 chars limit -> should fallback to "12U (+)"
        val clusters = clusterTreatments(treatments, isInsulin = true)
        assertEquals(1, clusters.size)
        assertEquals("12U (+)", clusters.first().displayText)
    }

    @Test
    fun testTreatmentsBeyond5MinutesDoNotCluster() {
        val baseTime = 1000000L
        val treatments = listOf(
            Treatment(id = 1L, timestamp = baseTime, insulinUnits = 4.0),
            Treatment(id = 2L, timestamp = baseTime + 6 * 60 * 1000L, insulinUnits = 2.0) // +6 min
        )
        val clusters = clusterTreatments(treatments, isInsulin = true)
        assertEquals(2, clusters.size)
        assertEquals("4U", clusters[0].displayText)
        assertEquals("2U", clusters[1].displayText)
    }

    @Test
    fun testCarbsClustering() {
        val baseTime = 1000000L
        val treatments = listOf(
            Treatment(id = 1L, timestamp = baseTime, carbsGrams = 20.0),
            Treatment(id = 2L, timestamp = baseTime + 3 * 60 * 1000L, carbsGrams = 15.0)
        )
        val clusters = clusterTreatments(treatments, isInsulin = false)
        assertEquals(1, clusters.size)
        assertEquals("20+15g", clusters.first().displayText)
        assertEquals(35.0, clusters.first().totalCarbs, 0.001)
    }

    @Test
    fun testCarbsLongClusteringFallback() {
        val baseTime = 1000000L
        val treatments = listOf(
            Treatment(id = 1L, timestamp = baseTime, carbsGrams = 20.0),
            Treatment(id = 2L, timestamp = baseTime + 60 * 1000L, carbsGrams = 30.0),
            Treatment(id = 3L, timestamp = baseTime + 120 * 1000L, carbsGrams = 15.0)
        ) // "20+30+15g" is 9 chars > 8 -> fallback to "65g (+)"
        val clusters = clusterTreatments(treatments, isInsulin = false)
        assertEquals(1, clusters.size)
        assertEquals("65g (+)", clusters.first().displayText)
    }

    @Test
    fun testComboTreatmentHandling() {
        val baseTime = 1000000L
        val combo = Treatment(id = 1L, timestamp = baseTime, insulinUnits = 5.0, carbsGrams = 40.0, notes = "Pizza")
        val insulinClusters = clusterTreatments(listOf(combo), isInsulin = true)
        val carbsClusters = clusterTreatments(listOf(combo), isInsulin = false)

        assertEquals(1, insulinClusters.size)
        assertTrue(insulinClusters.first().isCombo)
        assertEquals("5U", insulinClusters.first().displayText)
        assertEquals("Pizza", insulinClusters.first().notes)

        assertEquals(1, carbsClusters.size)
        assertTrue(carbsClusters.first().isCombo)
        assertEquals("40g", carbsClusters.first().displayText)
        assertEquals("Pizza", carbsClusters.first().notes)
    }

    @Test
    fun testNoteOnlyTreatmentHandling() {
        val baseTime = 1000000L
        val noteTreatment = Treatment(id = 2L, timestamp = baseTime, notes = "тест")
        assertTrue(noteTreatment.isNoteOnly)
        assertFalse(noteTreatment.hasInsulin)
        assertFalse(noteTreatment.hasCarbs)

        val noteClusters = clusterNoteTreatments(listOf(noteTreatment))
        assertEquals(1, noteClusters.size)
        val cluster = noteClusters.first()
        assertTrue(cluster.isNoteOnly)
        assertEquals("💬 тест", cluster.displayText)
        assertEquals("тест", cluster.notes)
    }
}
