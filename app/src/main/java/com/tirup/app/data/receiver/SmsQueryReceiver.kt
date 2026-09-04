package com.tirup.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.tirup.app.TirupApplication
import com.tirup.app.data.alert.EmergencySmsManager
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.alert.EmergencySmsBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class SmsQueryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingSms(context, sender, fullBody)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS query: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIncomingSms(context: Context, senderPhone: String, messageBody: String) {
        val repo = (context.applicationContext as? TirupApplication)?.settingsRepository
            ?: SettingsRepositoryImpl(context.applicationContext)
        val settings = repo.getSettings().first()
        val alerts = settings.alertSettings

        // 1. Check if SMS query auto-reply is enabled in settings
        if (!alerts.isSmsQueryReplyEnabled) {
            Log.d(TAG, "SMS query reply is disabled in settings, ignoring.")
            return
        }

        val trustedPhone = alerts.emergencyContactPhone.trim()
        if (trustedPhone.isBlank()) {
            Log.d(TAG, "No emergency contact phone configured, ignoring incoming SMS.")
            return
        }

        // 2. Strict Whitelist Check: Sender MUST match trusted emergency contact
        if (!isMatchingPhone(senderPhone, trustedPhone)) {
            Log.d(TAG, "Incoming SMS sender does not match trusted contact. Ignoring for security.")
            return
        }

        // 3. Keyword Check: Ensure message is an intentional glucose request
        if (!isQueryTrigger(messageBody)) {
            Log.d(TAG, "Incoming SMS from trusted contact does not contain a query keyword. Ignoring.")
            return
        }

        // 4. Anti-loop / Anti-spam Cooldown (1 minute)
        val now = System.currentTimeMillis()
        if (now - lastReplyTimestamp < COOLDOWN_MS) {
            Log.w(TAG, "SMS reply cooldown active (${(now - lastReplyTimestamp) / 1000}s < 60s). Skipping duplicate reply.")
            return
        }

        val isRu = settings.language.equals("RU", ignoreCase = true)
        val db = AppDatabase.getInstance(context)
        val recentEntities = db.glucoseReadingDao().getRecentReadingsSync(2)
        val latestEntity = recentEntities.firstOrNull()

        val replyText = if (latestEntity == null || (now - latestEntity.timestamp > 30 * 60_000L)) {
            EmergencySmsBuilder.buildNoDataReplyMessage(
                patientName = settings.patientProfile.fullName,
                isRu = isRu
            )
        } else {
            // Calculate delta from previous reading if available
            val previousEntity = if (recentEntities.size > 1) recentEntities[1] else null
            val delta = if (previousEntity != null && (latestEntity.timestamp - previousEntity.timestamp <= 15 * 60_000L)) {
                latestEntity.valueMmol - previousEntity.valueMmol
            } else null

            // Calculate today's TIR percent
            val startOfDay = getStartOfDayMillis()
            val todayReadings = db.glucoseReadingDao().getReadingsBetweenSync(startOfDay, now)
            val tirPercent = if (todayReadings.isNotEmpty()) {
                val tirLow = settings.targetRanges.tirLowMmol
                val tirHigh = if (settings.targetMode.name == "TING") settings.targetRanges.tingHighMmol else settings.targetRanges.tirHighMmol
                val inRangeCount = todayReadings.count { it.valueMmol in tirLow..tirHigh }
                ((inRangeCount.toDouble() / todayReadings.size) * 100).toInt()
            } else null

            EmergencySmsBuilder.buildQueryReplyMessage(
                patientName = settings.patientProfile.fullName,
                glucoseValueMmol = latestEntity.valueMmol,
                trendArrow = latestEntity.trendArrow ?: "",
                deltaMmol = delta,
                readingTimestamp = latestEntity.timestamp,
                todayTirPercent = tirPercent,
                iob = latestEntity.iob,
                isRu = isRu,
                unit = settings.unit
            )
        }

        try {
            sendSmsDirect(context, senderPhone, replyText)
            lastReplyTimestamp = now
            Log.i(TAG, "Successfully replied to trusted contact SMS query: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send query reply SMS: ${e.message}", e)
        }
    }

    private fun sendSmsDirect(context: Context, phone: String, message: String) {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(phone, null, message, null, null)
        }
    }

    private fun getStartOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "SmsQueryReceiver"
        private const val COOLDOWN_MS = 60_000L

        @Volatile
        private var lastReplyTimestamp = 0L

        /**
         * Checks if the sender phone matches the trusted phone number by comparing their last 10 digits.
         */
        fun isMatchingPhone(senderPhone: String, trustedPhone: String): Boolean {
            val cleanSender = senderPhone.filter { it.isDigit() }
            val cleanTrusted = trustedPhone.filter { it.isDigit() }
            if (cleanSender.length < 10 || cleanTrusted.length < 10) {
                return cleanSender.isNotEmpty() && cleanSender == cleanTrusted
            }
            return cleanSender.takeLast(10) == cleanTrusted.takeLast(10)
        }

        /**
         * Checks whether the incoming message body contains a recognized glucose query trigger word.
         */
        fun isQueryTrigger(body: String): Boolean {
            val clean = body.trim().lowercase()
            return clean == "?" ||
                    clean == "сахар" ||
                    clean == "sugar" ||
                    clean == "bg" ||
                    clean == "глюкоза" ||
                    clean == "tir" ||
                    clean.startsWith("?") ||
                    clean.startsWith("сахар") ||
                    clean.startsWith("сахор") ||
                    clean.startsWith("глюкоз") ||
                    clean.startsWith("инфо") ||
                    clean.startsWith("статус") ||
                    clean.startsWith("sugar") ||
                    clean.startsWith("bg") ||
                    clean.startsWith("help") ||
                    clean.contains("сахар") ||
                    clean.contains("глюкоз") ||
                    clean.contains("sugar")
        }
    }
}
