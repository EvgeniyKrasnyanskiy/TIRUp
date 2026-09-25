package com.tirup.app.presentation.aod

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tirup.app.TirupApplication
import com.tirup.app.presentation.theme.TIRUpTheme

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AodActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Allow display over lock screen and wake up
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Set low initial window brightness
        setWindowBrightness(0.01f)

        // Immersive sticky fullscreen
        hideSystemBars()

        val app = application as TirupApplication
        val glucoseRepo = app.glucoseRepository
        val settingsRepo = app.settingsRepository

        setContent {
            TIRUpTheme(darkTheme = true) {
                AodScreen(
                    settingsFlow = settingsRepo.getSettings(),
                    latestReadingFlow = glucoseRepo.getLatestReading(),
                    recentReadingsFlow = glucoseRepo.getRecentReadings(12),
                    onSetWindowBrightness = { brightness -> setWindowBrightness(brightness) },
                    onSaveBrightness = { brightness ->
                        lifecycleScope.launch {
                            val current = settingsRepo.getSettings().first()
                            settingsRepo.updateSettings(
                                current.copy(
                                    aodSettings = current.aodSettings.copy(
                                        customBrightness = brightness
                                    )
                                )
                            )
                        }
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setWindowBrightness(value: Float) {
        try {
            val lp = window.attributes
            lp.screenBrightness = value.coerceIn(0.001f, 1.0f)
            window.attributes = lp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
