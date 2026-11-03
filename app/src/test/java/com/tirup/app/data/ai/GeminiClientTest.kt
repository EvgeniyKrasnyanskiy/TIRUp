package com.tirup.app.data.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientTest {

    @Test
    fun testBuildRequestBodyWithTextAndSystemPrompt() {
        val jsonStr = GeminiClient.buildRequestBody(
            systemPrompt = "You are a clinical assistant",
            userPrompt = "What is my TIR?",
            imageBase64 = null,
            imageMimeType = "image/jpeg"
        )

        val json = JSONObject(jsonStr)
        val contents = json.getJSONArray("contents")
        assertEquals(1, contents.length())

        val parts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals(1, parts.length())
        assertEquals("What is my TIR?", parts.getJSONObject(0).getString("text"))

        val systemInstruction = json.getJSONObject("systemInstruction")
        val sysParts = systemInstruction.getJSONArray("parts")
        assertEquals("You are a clinical assistant", sysParts.getJSONObject(0).getString("text"))
    }

    @Test
    fun testBuildRequestBodyWithImage() {
        val dummyBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val jsonStr = GeminiClient.buildRequestBody(
            systemPrompt = null,
            userPrompt = "Оцени состав блюда",
            imageBase64 = dummyBase64,
            imageMimeType = "image/jpeg"
        )

        val json = JSONObject(jsonStr)
        val parts = json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())
        assertEquals("Оцени состав блюда", parts.getJSONObject(0).getString("text"))

        val inlineData = parts.getJSONObject(1).getJSONObject("inlineData")
        assertEquals("image/jpeg", inlineData.getString("mimeType"))
        assertEquals(dummyBase64, inlineData.getString("data"))
    }

    @Test
    fun testParseCandidateText() {
        val sampleResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Hello, your blood sugar is stable!"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val text = GeminiClient.parseCandidateText(sampleResponse)
        assertEquals("Hello, your blood sugar is stable!", text)
    }

    @Test
    fun testExtractFoodEstimationMarkdownFence() {
        val rawAiMessage = """
            На фотографии тарелка гречневой каши с куриной грудкой и овощами.
            Вес порции примерно 300г. Гликемический индекс средний.
            
            ```json:food
            {
              "food_detected": true,
              "title": "Гречка с куриной грудкой",
              "carbs_g": 48.0,
              "protein_g": 32.0,
              "fat_g": 6.5,
              "bu": 4.0,
              "comment": "Порция 300г"
            }
            ```
        """.trimIndent()

        val (cleanText, estimation) = GeminiClient.extractFoodEstimation(rawAiMessage)

        assertNotNull(estimation)
        assertEquals("Гречка с куриной грудкой", estimation!!.title)
        assertEquals(48.0, estimation.carbsGrams, 0.01)
        assertEquals(32.0, estimation.proteinGrams, 0.01)
        assertEquals(6.5, estimation.fatGrams, 0.01)
        assertEquals(4.0, estimation.breadUnits!!, 0.01)

        // Make sure the code fence is cleanly stripped from visible text
        assertTrue(!cleanText.contains("```json:food"))
        assertTrue(!cleanText.contains("carbs_g"))
        assertTrue(cleanText.contains("На фотографии тарелка гречневой каши"))
    }

    @Test
    fun testExtractFoodEstimationFallbackRawJson() {
        val rawAiMessage = """
            Блюдо: Сырники 3 шт со сметаной.
            {"food_detected":true,"title":"Сырники 3 шт","carbs_g":24.0,"protein_g":18.0,"fat_g":14.0,"bu":2.0,"comment":"3 шт со сметаной"}
        """.trimIndent()

        val (cleanText, estimation) = GeminiClient.extractFoodEstimation(rawAiMessage)

        assertNotNull(estimation)
        assertEquals("Сырники 3 шт", estimation!!.title)
        assertEquals(24.0, estimation.carbsGrams, 0.01)
        assertEquals(18.0, estimation.proteinGrams, 0.01)
        assertEquals(14.0, estimation.fatGrams, 0.01)
        assertEquals("Блюдо: Сырники 3 шт со сметаной.", cleanText)
    }

    @Test
    fun testExtractFoodEstimationWhenNoFood() {
        val nonFoodMessage = "Это фотография кота, здесь нет еды."
        val (cleanText, estimation) = GeminiClient.extractFoodEstimation(nonFoodMessage)
        assertNull(estimation)
        assertEquals(nonFoodMessage, cleanText)
    }
}

