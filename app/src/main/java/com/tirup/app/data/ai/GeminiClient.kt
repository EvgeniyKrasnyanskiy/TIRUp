package com.tirup.app.data.ai

import com.tirup.app.domain.model.AiFoodEstimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object GeminiClient {

    val DEFAULT_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-2.5-flash"
    )

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    class QuotaExceededException(
        message: String = "Суточный лимит запросов Gemini исчерпан (HTTP 429). Попробуйте позже или смените ключ."
    ) : RuntimeException(message)

    data class GeminiResponse(
        val text: String,
        val modelUsed: String,
        val foodEstimation: AiFoodEstimation? = null,
        val promptTokens: Int = 0,
        val candidatesTokens: Int = 0,
        val totalTokens: Int = 0
    )

    suspend fun testModels(
        apiKey: String,
        models: List<String> = DEFAULT_MODELS
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Int>()
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            models.forEach { results[it] = 401 }
            return@withContext results
        }

        val pingBody = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().apply { put("text", "Ping") })
            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)
        }.toString()

        for (model in models) {
            val code = try {
                val url = URL("$BASE_URL/$model:generateContent?key=$trimmedKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(pingBody) }
                val responseCode = conn.responseCode
                conn.disconnect()
                responseCode
            } catch (_: Exception) {
                503 // Network / connection failure
            }
            results[model] = code
        }
        results
    }

    suspend fun generateContent(
        apiKey: String,
        systemPrompt: String?,
        userPrompt: String,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg",
        preferredModel: String? = null,
        models: List<String> = DEFAULT_MODELS
    ): Result<GeminiResponse> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API key is empty"))
        }

        // Build fallback chain: preferred first, then remaining
        val orderedModels = mutableListOf<String>()
        if (!preferredModel.isNullOrBlank() && models.contains(preferredModel)) {
            orderedModels.add(preferredModel)
        }
        for (m in models) {
            if (!orderedModels.contains(m)) {
                orderedModels.add(m)
            }
        }

        val jsonBody = buildRequestBody(systemPrompt, userPrompt, imageBase64, imageMimeType)
        var lastError: Exception? = null
        var hadQuotaExceeded = false

        for (model in orderedModels) {
            try {
                val url = URL("$BASE_URL/$model:generateContent?key=$trimmedKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                        it.readText()
                    }
                    conn.disconnect()

                    val rawCandidateText = parseCandidateText(responseStr)
                    val (cleanText, food) = extractFoodEstimation(rawCandidateText)
                    val (pTokens, cTokens, tTokens) = parseUsageMetadata(responseStr)
                    return@withContext Result.success(
                        GeminiResponse(
                            text = cleanText,
                            modelUsed = model,
                            foodEstimation = food,
                            promptTokens = pTokens,
                            candidatesTokens = cTokens,
                            totalTokens = tTokens
                        )
                    )
                } else {
                    val errorBody = try {
                        val stream = conn.errorStream ?: conn.inputStream
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    } catch (_: Exception) { "" }
                    conn.disconnect()

                    // Regional restriction check (e.g. 400 User location is not supported)
                    if (errorBody.contains("User location is not supported", ignoreCase = true)) {
                        return@withContext Result.failure(
                            IllegalStateException("Google AI Studio не доступен в вашем регионе (User location is not supported). Используйте VPN/прокси.")
                        )
                    }
                    if (errorBody.contains("API_KEY_INVALID", ignoreCase = true) || responseCode == 401) {
                        return@withContext Result.failure(
                            IllegalArgumentException("API-ключ недействителен (API_KEY_INVALID). Проверьте ключ в настройках.")
                        )
                    }

                    if (responseCode == 429) {
                        hadQuotaExceeded = true
                    }
                    // For fallback HTTP codes (404, 429, 500, 503), log and try next model
                    val message = "Model $model returned HTTP $responseCode: $errorBody"
                    lastError = RuntimeException(message)
                    continue
                }
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }

        if (hadQuotaExceeded) {
            return@withContext Result.failure(QuotaExceededException())
        }
        Result.failure(lastError ?: RuntimeException("All Gemini models failed to respond"))
    }

    internal fun parseUsageMetadata(responseJson: String): Triple<Int, Int, Int> {
        return try {
            val root = JSONObject(responseJson)
            val usage = root.optJSONObject("usageMetadata") ?: return Triple(0, 0, 0)
            val prompt = usage.optInt("promptTokenCount", 0)
            val candidates = usage.optInt("candidatesTokenCount", 0)
            val total = usage.optInt("totalTokenCount", prompt + candidates)
            Triple(prompt, candidates, total)
        } catch (_: Exception) {
            Triple(0, 0, 0)
        }
    }

    fun buildRequestBody(
        systemPrompt: String?,
        userPrompt: String,
        imageBase64: String?,
        imageMimeType: String
    ): String {
        val root = JSONObject()

        // 1. Contents
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()

        if (userPrompt.isNotBlank()) {
            partsArray.put(JSONObject().apply { put("text", userPrompt) })
        } else if (imageBase64 != null) {
            partsArray.put(JSONObject().apply { put("text", "Рассчитай состав блюда и БЖУ по этой фотографии.") })
        }

        if (!imageBase64.isNullOrBlank()) {
            val inlineData = JSONObject().apply {
                put("mimeType", imageMimeType)
                put("data", imageBase64)
            }
            partsArray.put(JSONObject().apply { put("inlineData", inlineData) })
        }

        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        // 2. System Instruction
        if (!systemPrompt.isNullOrBlank()) {
            val systemObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().apply { put("text", systemPrompt) })
            systemObj.put("parts", sysParts)
            root.put("systemInstruction", systemObj)
        }

        return root.toString()
    }

    fun parseCandidateText(jsonResponse: String): String {
        val json = JSONObject(jsonResponse)
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            sb.append(part.optString("text", ""))
        }
        return sb.toString().trim()
    }

    fun extractFoodEstimation(rawText: String): Pair<String, AiFoodEstimation?> {
        val pattern = Pattern.compile("```(?:json:food|json)?\\s*(\\{[\\s\\S]*?\"food_detected\"[\\s\\S]*?\\})\\s*```", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(rawText)

        var cleanText = rawText
        var estimation: AiFoodEstimation? = null

        if (matcher.find()) {
            val fullMatch = matcher.group(0) ?: ""
            val jsonString = matcher.group(1) ?: ""
            try {
                val json = JSONObject(jsonString)
                val detected = json.optBoolean("food_detected", false)
                if (detected) {
                    val title = json.optString("title", "")
                    val carbs = json.optDouble("carbs_g", 0.0)
                    val protein = json.optDouble("protein_g", 0.0)
                    val fat = json.optDouble("fat_g", 0.0)
                    val bu = if (json.has("bu")) json.optDouble("bu") else null
                    val comment = json.optString("comment", "")
                    estimation = AiFoodEstimation(
                        title = title,
                        carbsGrams = carbs,
                        proteinGrams = protein,
                        fatGrams = fat,
                        breadUnits = bu,
                        comment = comment
                    )
                }
                cleanText = rawText.replace(fullMatch, "").trim()
            } catch (_: Exception) {}
        } else {
            // Also attempt fallback match if no markdown code fences were used
            val rawPattern = Pattern.compile("(\\{[^{}]*\"food_detected\"\\s*:\\s*true[^{}]*\\})", Pattern.CASE_INSENSITIVE)
            val rawMatcher = rawPattern.matcher(rawText)
            if (rawMatcher.find()) {
                val jsonString = rawMatcher.group(1) ?: ""
                try {
                    val json = JSONObject(jsonString)
                    val title = json.optString("title", "")
                    val carbs = json.optDouble("carbs_g", 0.0)
                    val protein = json.optDouble("protein_g", 0.0)
                    val fat = json.optDouble("fat_g", 0.0)
                    val bu = if (json.has("bu")) json.optDouble("bu") else null
                    val comment = json.optString("comment", "")
                    estimation = AiFoodEstimation(
                        title = title,
                        carbsGrams = carbs,
                        proteinGrams = protein,
                        fatGrams = fat,
                        breadUnits = bu,
                        comment = comment
                    )
                    cleanText = rawText.replace(jsonString, "").trim()
                } catch (_: Exception) {}
            }
        }

        return Pair(cleanText, estimation)
    }
}

