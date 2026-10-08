package com.tirup.app.data.ble

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tirup.app.TirupApplication
import com.tirup.app.data.alert.GlucoseAlertManager

class BleObserverService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                val ctx = context ?: return
                BleObserverManager.onScreenTurnedOn(ctx)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        BleObserverManager.isServiceRunning = true
        Log.i(TAG, "BleObserverService created")

        if (!isScreenReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
                ContextCompat.registerReceiver(
                    this,
                    screenReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isScreenReceiverRegistered = true
                Log.d(TAG, "ScreenReceiver registered for ACTION_SCREEN_ON")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register screenReceiver: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "BleObserverService onStartCommand")
        BleObserverManager.isServiceRunning = true
        val app = applicationContext as? TirupApplication
        val settingsRepo = app?.settingsRepository
        val glucoseRepo = app?.glucoseRepository

        if (settingsRepo == null || glucoseRepo == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = GlucoseAlertManager.getOrCreateLockscreenNotification(applicationContext)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    GlucoseAlertManager.NOTIFICATION_ID_LOCKSCREEN,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(GlucoseAlertManager.NOTIFICATION_ID_LOCKSCREEN, notif)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground error: ${e.message}")
        }

        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        try {
            if (wakeLock == null) {
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TIRUp:BleObserverWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire observer WakeLock: ${e.message}")
        }

        BleObserverManager.startScanningFromService(applicationContext, settingsRepo, glucoseRepo)
        BleScanKeepAliveReceiver.scheduleKeepAlive(applicationContext)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        BleObserverManager.isServiceRunning = false
        BleScanKeepAliveReceiver.cancelKeepAlive(applicationContext)
        Log.i(TAG, "BleObserverService onDestroy")

        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isScreenReceiverRegistered = false
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BleObserverService"
    }
}
