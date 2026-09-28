package com.tirup.app.data.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsQueryReceiverTest {

    @Test
    fun testValidTriggers() {
        // Single char & command words
        assertTrue(SmsQueryReceiver.isQueryTrigger("?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("сахар"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("sugar"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("bg"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("глюкоза"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("tir"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("help"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("статус"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("инфо"))

        // With punctuation / prefix
        assertTrue(SmsQueryReceiver.isQueryTrigger("?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("сахар?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("sugar?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("bg?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("глюкоза?"))

        // Natural language query patterns
        assertTrue(SmsQueryReceiver.isQueryTrigger("какой сахар?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("сколько сахар"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("что с сахаром?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("уровень сахара"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("как сахар?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("how is sugar?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("what is bg?"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("скинь сахар"))
        assertTrue(SmsQueryReceiver.isQueryTrigger("проверь сахар"))
    }

    @Test
    fun testRejectsConversationalSugarPhrases() {
        // Must reject casual sentences containing 'сахар'
        assertFalse(SmsQueryReceiver.isQueryTrigger("купи сахар в магазине"))
        assertFalse(SmsQueryReceiver.isQueryTrigger("купи 1 кг сахара"))
        assertFalse(SmsQueryReceiver.isQueryTrigger("чай без сахара"))
        assertFalse(SmsQueryReceiver.isQueryTrigger("я в магазине, нужен ли сахар?"))
        assertFalse(SmsQueryReceiver.isQueryTrigger("Привет, как дела?"))
        assertFalse(SmsQueryReceiver.isQueryTrigger("Код подтверждения: 1234"))
        assertFalse(SmsQueryReceiver.isQueryTrigger(""))
        assertFalse(SmsQueryReceiver.isQueryTrigger("   "))
    }
}
