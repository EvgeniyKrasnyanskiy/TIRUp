package com.tirup.app.data.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.TirupApplication
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.alert.EmergencySmsBuilder
import com.tirup.app.domain.model.AlertSettings
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.PatientProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object EmergencySmsManager {

    private const val TAG = "EmergencySmsManager"

    /**
     * Anti-spam cooldown: minimum 30 minutes between automated emergency SMS.
     */
    const val COOLDOWN_MILLIS = 30 * 60 * 1000L

    /**
     * Dispatches an emergency SMS alert to the configured trusted contact if enabled and cooldown has passed.
     *
     * @return true if SMS was successfully dispatched, false if skipped or failed.
     */
    fun sendEmergencyAlert(
        context: Context,
        glucoseValue: Double,
        trendArrow: String,
        delayMinutes: Int,
        settings: AlertSettings,
        patientProfile: PatientProfile,
        isRu: Boolean,
        unit: GlucoseUnit
    ): Boolean {
        if (!settings.isEmergencySmsEnabled) {
            Log.d(TAG, "Emergency SMS is disabled in settings, skipping.")
            return false
        }

        val phone = settings.emergencyContactPhone.trim()
        if (phone.isBlank()) {
            Log.w(TAG, "Emergency contact phone is empty, cannot send SMS.")
            return false
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot send emergency SMS: SEND_SMS permission is not granted!")
            return false
        }

        val now = System.currentTimeMillis()
        if (now - settings.lastEmergencySmsTimestamp < COOLDOWN_MILLIS) {
            val remainingSec = (COOLDOWN_MILLIS - (now - settings.lastEmergencySmsTimestamp)) / 1000
            Log.w(TAG, "Emergency SMS cooldown active ($remainingSec seconds remaining). Skipping duplicate SMS.")
            return false
        }

        val (lat, lon) = if (settings.includeLocationInEmergencySms) {
            getLastKnownLocation(context)
        } else {
            Pair(null, null)
        }

        val message = EmergencySmsBuilder.buildEmergencyMessage(
            patientName = patientProfile.fullName,
            glucoseValueMmol = glucoseValue,
            trendArrow = trendArrow,
            delayMinutes = delayMinutes,
            latitude = lat,
            longitude = lon,
            isRu = isRu,
            unit = unit
        )

        return try {
            sendSmsInternal(context, phone, message)
            Log.i(TAG, "Emergency SMS successfully dispatched to $phone")
            updateLastSentTimestamp(context, now)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency SMS: ${e.message}", e)
            false
        }
    }

    /**
     * Sends a manual test SMS to verify phone number and SMS permission from Settings.
     */
    fun sendTestSms(
        context: Context,
        phone: String,
        patientName: String,
        isRu: Boolean
    ): Result<Unit> {
        val trimmedPhone = phone.trim()
        if (trimmedPhone.isBlank()) {
            return Result.failure(IllegalArgumentException(if (isRu) "Номер телефона не указан" else "Phone number is empty"))
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException(if (isRu) "Разрешение на отправку SMS не предоставлено" else "SEND_SMS permission not granted"))
        }

        return try {
            val message = EmergencySmsBuilder.buildTestMessage(patientName, isRu)
            sendSmsInternal(context, trimmedPhone, message)
            Log.i(TAG, "Test SMS sent to $trimmedPhone")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send test SMS: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun sendSmsInternal(context: Context, phone: String, message: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(phone, null, message, null, null)
        }
    }

    private fun getLastKnownLocation(context: Context): Pair<Double?, Double?> {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.d(TAG, "Location permissions not granted, omitting coordinates from SMS.")
            return Pair(null, null)
        }

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return Pair(null, null)

            val providers = lm.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            }

            if (bestLocation != null) {
                Pair(bestLocation.latitude, bestLocation.longitude)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve last known location: ${e.message}")
            Pair(null, null)
        }
    }

    private fun updateLastSentTimestamp(context: Context, timestamp: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as? TirupApplication)?.settingsRepository
                    ?: SettingsRepositoryImpl(context.applicationContext)
                val current = repo.getSettings().first()
                val updated = current.copy(
                    alertSettings = current.alertSettings.copy(lastEmergencySmsTimestamp = timestamp)
                )
                repo.updateSettings(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist lastEmergencySmsTimestamp: ${e.message}")
            }
        }
    }
}
