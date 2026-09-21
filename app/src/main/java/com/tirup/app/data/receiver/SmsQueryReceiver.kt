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

        val trustedPhone1 = alerts.emergencyContactPhone.trim()
        val trustedPhone2 = alerts.secondaryEmergencyContactPhone.trim()
        val trustedPhones = listOf(trustedPhone1, trustedPhone2).filter { it.isNotBlank() }

        // 1. Check for Caregiver SOS Wakeup Alarm (Emergency alert from patient)
        if (alerts.isCaregiverSosWakeupEnabled && com.tirup.app.domain.alert.SosSmsParser.isSosMessage(messageBody)) {
            Log.i(TAG, "Incoming SMS identified as emergency SOS message: $messageBody")
            val isSenderTrusted = trustedPhones.any { isMatchingPhone(senderPhone, it) }
            if (isSenderTrusted) {
                val sosData = com.tirup.app.domain.alert.SosSmsParser.parse(messageBody, senderPhone)
                if (sosData != null) {
                    Log.i(TAG, "Valid SOS alert from trusted contact $senderPhone! Triggering CaregiverSosAlarmManager...")
                    com.tirup.app.data.alert.CaregiverSosAlarmManager.triggerCaregiverSos(context, sosData)
                    return
                }
            } else {
                Log.w(TAG, "SOS SMS received but sender ($senderPhone) is not in trusted contacts ($trustedPhones). Ignoring for anti-spam security.")
                return
            }
        }

        // 2. Check if SMS query auto-reply is enabled in settings
        if (trustedPhones.isEmpty()) {
            Log.d(TAG, "No emergency contact phone configured, ignoring incoming SMS.")
            return
        }

        // 2. Strict Whitelist Check: Sender MUST match one of trusted emergency contacts
        val isSenderTrusted = trustedPhones.any { isMatchingPhone(senderPhone, it) }
        if (!isSenderTrusted) {
            Log.d(TAG, "Incoming SMS sender does not match any trusted contact. Ignoring for security.")
            return
        }

        // 3. Show Heads-Up HUD screen with gentle vibration over lockscreen
        val contactName = if (isMatchingPhone(senderPhone, trustedPhone1)) {
            alerts.emergencyContactName.ifBlank { if (alerts.isCaregiverRole) "Мастер" else "Фоловер" }
        } else {
            alerts.secondaryEmergencyContactName.ifBlank { if (alerts.isCaregiverRole) "Мастер" else "Фоловер" }
        }
        launchHeadsUpMessage(
            context = context,
            senderName = contactName,
            senderPhone = senderPhone,
            messageText = messageBody,
            isSenderMaster = alerts.isCaregiverRole
        )

        // 4. If message is a glucose query trigger and query reply is enabled, send auto-reply
        if (!alerts.isSmsQueryReplyEnabled || !isQueryTrigger(messageBody)) {
            return
        }

        // 5. Anti-loop / Anti-spam Cooldown (1 minute)
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
        fun launchHeadsUpMessage(
            context: Context,
            senderName: String,
            senderPhone: String,
            messageText: String,
            isSenderMaster: Boolean
        ) {
            try {
                val intent = Intent(context, com.tirup.app.presentation.alert.HeadsUpMessageActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(com.tirup.app.presentation.alert.HeadsUpMessageActivity.EXTRA_SENDER_NAME, senderName)
                    putExtra(com.tirup.app.presentation.alert.HeadsUpMessageActivity.EXTRA_SENDER_PHONE, senderPhone)
                    putExtra(com.tirup.app.presentation.alert.HeadsUpMessageActivity.EXTRA_MESSAGE_TEXT, messageText)
                    putExtra(com.tirup.app.presentation.alert.HeadsUpMessageActivity.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    putExtra(com.tirup.app.presentation.alert.HeadsUpMessageActivity.EXTRA_IS_SENDER_MASTER, isSenderMaster)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not launch HeadsUpMessageActivity: ${e.message}", e)
            }
        }
    }
}
