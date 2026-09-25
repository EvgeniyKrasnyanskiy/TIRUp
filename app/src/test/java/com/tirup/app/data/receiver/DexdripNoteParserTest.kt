package com.tirup.app.data.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DexdripNoteParserTest {

    @Test
    fun testParseDaysFromNote_variousFormats() {
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("продлен на 7 дней"))
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("продлен на 7 дн"))
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("продлен на 7д"))
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("ланцет +7"))
        assertEquals(14, DexdripBroadcastReceiver.parseDaysFromNote("+14"))
        assertEquals(14, DexdripBroadcastReceiver.parseDaysFromNote("restart 14 days"))
        assertEquals(5, DexdripBroadcastReceiver.parseDaysFromNote("extend 5 days"))
        assertEquals(10, DexdripBroadcastReceiver.parseDaysFromNote("перезапуск 10"))
        assertEquals(3, DexdripBroadcastReceiver.parseDaysFromNote("продлить 3 дня"))
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("ланцет на 7 дней"))
        assertEquals(14, DexdripBroadcastReceiver.parseDaysFromNote("restart lancet 14 days"))
        assertEquals(3, DexdripBroadcastReceiver.parseDaysFromNote("рестарт канюли 3"))
        assertEquals(7, DexdripBroadcastReceiver.parseDaysFromNote("продлить сенсор 7"))
        assertEquals(10, DexdripBroadcastReceiver.parseDaysFromNote("продлить сенсор на 10 дней"))
        assertEquals(4, DexdripBroadcastReceiver.parseDaysFromNote("extend cannula 4"))
    }

    @Test
    fun testParseDaysFromNote_noDurationSpecified() {
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("ланцет"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("новый ланцет"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("смена ланцета"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("замена ланцета"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("прокалыватель"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("новая игла"))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote(""))
        assertNull(DexdripBroadcastReceiver.parseDaysFromNote("   "))
    }

    @Test
    fun testIsLancetNote() {
        // Positive matches
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("ланцет"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("новый ланцет"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("смена ланцета"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("замена ланцета"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("прокалыватель"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("игла"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("новая игла"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("смена иглы"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("needle"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("lancet restart"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("ланцет +7"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isLancetNote("продлить ланцет на 7 дней"))

        // Warnings / alerts should be excluded
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("ланцет warning: expired"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("ланцет alarm"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("ланцет закончится через 1 день"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("ланцет ошибка"))

        // Unrelated notes
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("сенсор"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("канюля"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote("обед 5 ХЕ"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote(null))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isLancetNote(""))
    }

    @Test
    fun testIsCannulaNote() {
        // Positive matches
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("канюля"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("Канюля"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("канюля продлена на 3 дня"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("канюля +3"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("инфузионный набор"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("cannula change"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("infusion set"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("смена катетера"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isCannulaNote("катетер +3"))

        // Warnings / alerts should be excluded
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote("канюля warning: replace"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote("инфуз ошибка"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote("канюля истек срок"))

        // Unrelated
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote("ланцет"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote("сенсор"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isCannulaNote(null))
    }

    @Test
    fun testIsSensorNote() {
        // Positive matches
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("сенсор"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("Сенсор"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("новый сенсор"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("sensor start"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("сенсор продлен на 7 дней"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("сенсор +7"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("рестарт сенсора 14 days"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("libre restart"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("dexcom change"))
        org.junit.Assert.assertTrue(DexdripBroadcastReceiver.isSensorNote("датчик замена"))

        // Warnings / alerts should be excluded
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote("сенсор warning: expired"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote("сенсор закончится через 2 часа"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote("сенсор error 373"))

        // Unrelated
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote("ланцет"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote("канюля"))
        org.junit.Assert.assertFalse(DexdripBroadcastReceiver.isSensorNote(null))
    }
}
