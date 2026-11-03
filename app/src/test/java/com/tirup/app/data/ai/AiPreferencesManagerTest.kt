package com.tirup.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.tirup.app.domain.model.AiChatMessage
import com.tirup.app.domain.model.AiFoodEstimation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AiPreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var tempDir: File
    private val memoryStore = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        tempDir = Files.createTempDirectory("ai_cache_test").toFile()
        every { context.cacheDir } returns tempDir
        every { context.getSharedPreferences(any(), any()) } returns prefs

        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            memoryStore[key] = value
            editor
        }
        every { editor.remove(any()) } answers {
            val key = firstArg<String>()
            memoryStore.remove(key)
            editor
        }
        every { editor.apply() } answers {}
        every { prefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            (memoryStore[key] as? String) ?: default
        }
        every { editor.putInt(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Int>()
            memoryStore[key] = value
            editor
        }
        every { prefs.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Int>()
            (memoryStore[key] as? Int) ?: default
        }
        every { editor.putBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Boolean>()
            memoryStore[key] = value
            editor
        }
        every { prefs.getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Boolean>()
            (memoryStore[key] as? Boolean) ?: default
        }
    }

    @Test
    fun testChatMessagesSaveAndLoad() {
        val manager = AiPreferencesManager(context)

        val food = AiFoodEstimation(
            title = "Гречка с курицей",
            carbsGrams = 45.0,
            proteinGrams = 28.0,
            fatGrams = 8.0,
            breadUnits = 4.0,
            comment = "Порция 200г"
        )
        val msg1 = AiChatMessage(
            sender = "user",
            text = "Сколько углеводов в тарелке?"
        )
        val msg2 = AiChatMessage(
            sender = "model",
            text = "В этой тарелке примерно 45г углеводов.",
            modelBadge = "gemini-3.6-flash",
            foodEstimation = food
        )

        manager.saveChatMessages(listOf(msg1, msg2))

        val loaded = manager.loadChatMessages()
        assertEquals(2, loaded.size)
        assertEquals("user", loaded[0].sender)
        assertEquals("model", loaded[1].sender)
        assertEquals("gemini-3.6-flash", loaded[1].modelBadge)
        assertEquals(45.0, loaded[1].foodEstimation?.carbsGrams ?: 0.0, 0.01)
        assertEquals("Гречка с курицей", loaded[1].foodEstimation?.title)

        manager.clearChatHistory()
        assertTrue(manager.loadChatMessages().isEmpty())
    }

    @Test
    fun testMaxMessagesTrim() {
        val manager = AiPreferencesManager(context)
        val list = mutableListOf<AiChatMessage>()
        for (i in 1..40) {
            list.add(AiChatMessage(sender = if (i % 2 == 0) "model" else "user", text = "Message $i"))
        }
        manager.saveChatMessages(list)

        val loaded = manager.loadChatMessages()
        assertEquals(AiPreferencesManager.MAX_CHAT_CACHE_MESSAGES, loaded.size)
        assertEquals("Message 11", loaded.first().text)
        assertEquals("Message 40", loaded.last().text)
    }

    @Test
    fun testModelStatusesPersistence() {
        val manager = AiPreferencesManager(context)
        val statuses = mapOf(
            "gemini-3.8-flash" to 200,
            "gemini-3.6-flash" to 200,
            "gemini-2.5-flash" to 429
        )
        manager.saveModelStatuses(statuses)

        val retrieved = manager.getModelStatuses()
        assertEquals(200, retrieved["gemini-3.8-flash"])
        assertEquals(200, retrieved["gemini-3.6-flash"])
        assertEquals(429, retrieved["gemini-2.5-flash"])

        manager.saveLastSuccessfulModel("gemini-3.6-flash")
        assertEquals("gemini-3.6-flash", manager.getLastSuccessfulModel())
    }

    @Test
    fun testApiKeySaveAndFallback() {
        val manager = AiPreferencesManager(context)
        assertFalse(manager.hasApiKey())

        manager.saveApiKey("AIzaSyTest12345Key")
        assertTrue(manager.hasApiKey())
        assertEquals("AIzaSyTest12345Key", manager.getApiKey())

        manager.clearApiKey()
        assertFalse(manager.hasApiKey())
        assertEquals("", manager.getApiKey())
    }

    @Test
    fun testDailyQuotaUsageTracking() {
        val manager = AiPreferencesManager(context)
        val initial = manager.getDailyQuotaUsage()
        assertEquals(0, initial.requestsUsed)
        assertEquals(0, initial.tokensUsed)
        assertFalse(initial.isQuotaExhausted)

        val updated = manager.recordRequestUsage(350)
        assertEquals(1, updated.requestsUsed)
        assertEquals(350, updated.tokensUsed)
        assertEquals(1499, updated.remainingRequests)

        manager.recordRequestUsage(150)
        val state2 = manager.getDailyQuotaUsage()
        assertEquals(2, state2.requestsUsed)
        assertEquals(500, state2.tokensUsed)

        manager.markQuotaExhausted(true)
        val exhaustedState = manager.getDailyQuotaUsage()
        assertTrue(exhaustedState.isQuotaExhausted)
        assertEquals(1.0f, exhaustedState.progressFraction, 0.001f)
    }
}