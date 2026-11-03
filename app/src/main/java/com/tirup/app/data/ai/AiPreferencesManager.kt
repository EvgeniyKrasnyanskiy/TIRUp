package com.tirup.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import com.tirup.app.domain.model.AiChatMessage
import com.tirup.app.domain.model.AiDailyQuotaUsage
import com.tirup.app.domain.model.AiFoodEstimation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiPreferencesManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // region API Key Management with KeyStore / AES-GCM & safe fallback
    fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey()
            return
        }
        try {
            val encrypted = encryptWithKeyStore(trimmed)
            prefs.edit()
                .putString(KEY_ENCRYPTED_API_KEY, encrypted)
                .remove(KEY_FALLBACK_API_KEY)
                .apply()
        } catch (_: Exception) {
            // Safe obfuscation fallback for pure JVM tests or devices with broken KeyStore
            val obfuscated = obfuscate(trimmed)
            prefs.edit()
                .putString(KEY_FALLBACK_API_KEY, obfuscated)
                .remove(KEY_ENCRYPTED_API_KEY)
                .apply()
        }
    }

    fun getApiKey(): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED_API_KEY, null)
        if (!encrypted.isNullOrBlank()) {
            try {
                return decryptWithKeyStore(encrypted)
            } catch (_: Exception) {
                // If KeyStore decryption failed (e.g. reinstall or key invalidation)
            }
        }
        val fallback = prefs.getString(KEY_FALLBACK_API_KEY, null)
        if (!fallback.isNullOrBlank()) {
            try {
                return deobfuscate(fallback)
            } catch (_: Exception) {}
        }
        return ""
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_API_KEY)
            .remove(KEY_FALLBACK_API_KEY)
            .apply()
    }

    private fun encryptWithKeyStore(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decryptWithKeyStore(encryptedBase64: String): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        if (combined.size < GCM_IV_LENGTH) return ""
        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun obfuscate(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor OBFUSCATION_MASK).toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun deobfuscate(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor OBFUSCATION_MASK).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
    // endregion

    // region Model Status Persistence
    fun saveModelStatuses(statuses: Map<String, Int>) {
        val json = JSONObject()
        for ((model, code) in statuses) {
            json.put(model, code)
        }
        prefs.edit().putString(KEY_MODEL_STATUSES, json.toString()).apply()
    }

    fun getModelStatuses(): Map<String, Int> {
        val raw = prefs.getString(KEY_MODEL_STATUSES, null) ?: return emptyMap()
        val result = mutableMapOf<String, Int>()
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optInt(key, 0)
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveLastSuccessfulModel(modelName: String) {
        prefs.edit().putString(KEY_LAST_SUCCESSFUL_MODEL, modelName).apply()
    }

    fun getLastSuccessfulModel(): String? {
        return prefs.getString(KEY_LAST_SUCCESSFUL_MODEL, null)
    }
    // endregion

    // region Daily Quota & Usage Tracking
    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @Synchronized
    fun getDailyQuotaUsage(): AiDailyQuotaUsage {
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DAILY_USAGE_DATE, "") ?: ""
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_DAILY_USAGE_DATE, today)
                .putInt(KEY_DAILY_REQUESTS_COUNT, 0)
                .putInt(KEY_DAILY_TOKENS_COUNT, 0)
                .putBoolean(KEY_DAILY_QUOTA_EXHAUSTED, false)
                .apply()
            return AiDailyQuotaUsage(requestsUsed = 0, tokensUsed = 0, isQuotaExhausted = false)
        }

        val requests = prefs.getInt(KEY_DAILY_REQUESTS_COUNT, 0)
        val tokens = prefs.getInt(KEY_DAILY_TOKENS_COUNT, 0)
        val exhausted = prefs.getBoolean(KEY_DAILY_QUOTA_EXHAUSTED, false)
        return AiDailyQuotaUsage(
            requestsUsed = requests,
            tokensUsed = tokens,
            isQuotaExhausted = exhausted
        )
    }

    @Synchronized
    fun recordRequestUsage(tokensCount: Int): AiDailyQuotaUsage {
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DAILY_USAGE_DATE, "") ?: ""
        val (currentRequests, currentTokens) = if (savedDate != today) {
            Pair(0, 0)
        } else {
            Pair(prefs.getInt(KEY_DAILY_REQUESTS_COUNT, 0), prefs.getInt(KEY_DAILY_TOKENS_COUNT, 0))
        }

        val newRequests = currentRequests + 1
        val newTokens = currentTokens + tokensCount.coerceAtLeast(0)

        prefs.edit()
            .putString(KEY_DAILY_USAGE_DATE, today)
            .putInt(KEY_DAILY_REQUESTS_COUNT, newRequests)
            .putInt(KEY_DAILY_TOKENS_COUNT, newTokens)
            .apply()

        return AiDailyQuotaUsage(
            requestsUsed = newRequests,
            tokensUsed = newTokens,
            isQuotaExhausted = prefs.getBoolean(KEY_DAILY_QUOTA_EXHAUSTED, false)
        )
    }

    @Synchronized
    fun markQuotaExhausted(isExhausted: Boolean = true): AiDailyQuotaUsage {
        val today = getTodayDateString()
        prefs.edit()
            .putString(KEY_DAILY_USAGE_DATE, today)
            .putBoolean(KEY_DAILY_QUOTA_EXHAUSTED, isExhausted)
            .apply()
        return getDailyQuotaUsage()
    }
    // endregion

    // region Chat Cache (Max 30 messages in cacheDir/ai_chat_cache.json)
    private fun getChatCacheFile(): File = File(context.cacheDir, CHAT_CACHE_FILE_NAME)

    fun loadChatMessages(): List<AiChatMessage> {
        val file = getChatCacheFile()
        if (!file.exists()) return emptyList()
        val list = mutableListOf<AiChatMessage>()
        try {
            val text = file.readText(Charsets.UTF_8)
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val foodObj = obj.optJSONObject("foodEstimation")
                val food = foodObj?.let {
                    AiFoodEstimation(
                        title = it.optString("title", ""),
                        carbsGrams = it.optDouble("carbsGrams", 0.0),
                        proteinGrams = it.optDouble("proteinGrams", 0.0),
                        fatGrams = it.optDouble("fatGrams", 0.0),
                        breadUnits = if (it.has("breadUnits")) it.optDouble("breadUnits") else null,
                        comment = it.optString("comment", ""),
                        isAddedToChart = it.optBoolean("isAddedToChart", false),
                        isDismissed = it.optBoolean("isDismissed", false)
                    )
                }
                list.add(
                    AiChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        sender = obj.optString("sender", "model"),
                        text = obj.optString("text", ""),
                        modelBadge = if (obj.has("modelBadge")) obj.optString("modelBadge") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        foodEstimation = food,
                        photoUri = if (obj.has("photoUri")) obj.optString("photoUri") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveChatMessages(messages: List<AiChatMessage>) {
        val file = getChatCacheFile()
        val trimmed = if (messages.size > MAX_CHAT_CACHE_MESSAGES) {
            messages.takeLast(MAX_CHAT_CACHE_MESSAGES)
        } else {
            messages
        }
        try {
            val array = JSONArray()
            for (msg in trimmed) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender", msg.sender)
                    put("text", msg.text)
                    msg.modelBadge?.let { put("modelBadge", it) }
                    put("timestamp", msg.timestamp)
                    msg.photoUri?.let { put("photoUri", it) }
                    msg.foodEstimation?.let { food ->
                        val fObj = JSONObject().apply {
                            put("title", food.title)
                            put("carbsGrams", food.carbsGrams)
                            put("proteinGrams", food.proteinGrams)
                            put("fatGrams", food.fatGrams)
                            food.breadUnits?.let { put("breadUnits", it) }
                            put("comment", food.comment)
                            put("isAddedToChart", food.isAddedToChart)
                            put("isDismissed", food.isDismissed)
                        }
                        put("foodEstimation", fObj)
                    }
                }
                array.put(obj)
            }
            file.writeText(array.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun clearChatHistory() {
        try {
            val file = getChatCacheFile()
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
    // endregion

    companion object {
        private const val PREFS_NAME = "tirup_ai_preferences"
        private const val KEY_ENCRYPTED_API_KEY = "key_encrypted_gemini_api_key"
        private const val KEY_FALLBACK_API_KEY = "key_fallback_gemini_api_key"
        private const val KEY_MODEL_STATUSES = "key_gemini_model_statuses"
        private const val KEY_LAST_SUCCESSFUL_MODEL = "key_last_successful_gemini_model"
        private const val KEY_DAILY_USAGE_DATE = "key_ai_daily_usage_date"
        private const val KEY_DAILY_REQUESTS_COUNT = "key_ai_daily_requests_count"
        private const val KEY_DAILY_TOKENS_COUNT = "key_ai_daily_tokens_count"
        private const val KEY_DAILY_QUOTA_EXHAUSTED = "key_ai_daily_quota_exhausted"


        private const val CHAT_CACHE_FILE_NAME = "ai_chat_cache.json"
        const val MAX_CHAT_CACHE_MESSAGES = 30

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "TirupAiMasterKey"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val OBFUSCATION_MASK = 0x5A
    }
}

