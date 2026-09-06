package com.tirup.app.presentation.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.tirup.app.R
import com.tirup.app.TirupApplication
import com.tirup.app.data.alert.GlucoseAlertManager
import com.tirup.app.data.alert.MedicalSoundPlayer
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleRipple: View? = null
    private var bubbleContainer: View? = null
    private var tvGlucose: TextView? = null
    private var tvArrow: TextView? = null
    private var tvDelta: TextView? = null

    private var rippleAnimatorSet: AnimatorSet? = null
    private var edgeAnimator: ValueAnimator? = null
    private var snoozeJob: Job? = null
    @Volatile
    private var snoozeUntilTimestamp: Long = 0L
    @Volatile
    private var lastKnownReading: GlucoseReading? = null
    @Volatile
    private var lastKnownSettings: UserSettings? = null
    private var wasOutOfRange: Boolean = false
    private var wasHypoActive: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission missing. Stopping service.")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createAndAttachBubble()
        observeData()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createAndAttachBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_glucose_bubble, null)
        bubbleView = view
        bubbleRipple = view.findViewById(R.id.bubble_ripple)
        bubbleContainer = view.findViewById(R.id.bubble_container)
        tvGlucose = view.findViewById(R.id.tv_bubble_glucose)
        tvArrow = view.findViewById(R.id.tv_bubble_arrow)
        tvDelta = view.findViewById(R.id.tv_bubble_delta)

        // Initially hidden until data indicates out-of-range
        view.visibility = View.GONE

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleSizePx = dpToPx(76)
        val params = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(12)
            y = dpToPx(200)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchStartTime
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (duration < 250 && dx < 20 && dy < 20) {
                        // Instantly silence actively playing sound and dismiss alarm
                        GlucoseAlertManager.silenceCurrentSoundOnly()
                        GlucoseAlertManager.dismissCriticalAlarm(this@FloatingBubbleService, fromUser = true)

                        // Play soft bubble pop-out sound feedback
                        MedicalSoundPlayer.playBubblePopOut()

                        val currentMmol = lastKnownReading?.valueMmol ?: 5.0
                        val alertSettings = lastKnownSettings?.alertSettings
                        val isHypo = currentMmol < 3.9
                        val iob = lastKnownReading?.iob ?: 0.0
                        val isExtremeHigh = currentMmol > 13.9
                        val requiredIob = if (isExtremeHigh) 0.5 else 0.2

                        val snoozeMinutes = if (isHypo) {
                            alertSettings?.snoozeHypoMinutes ?: 15
                        } else {
                            val baseHyper = alertSettings?.snoozeHyperMinutes ?: 45
                            if (iob >= requiredIob) maxOf(baseHyper, 60) else baseHyper
                        }

                        val snoozeDuration = snoozeMinutes * 60 * 1000L
                        snoozeUntilTimestamp = System.currentTimeMillis() + snoozeDuration
                        bubbleView?.visibility = View.GONE
                        setHypoRipple(false)

                        snoozeJob?.cancel()
                        snoozeJob = serviceScope.launch {
                            delay(snoozeDuration)
                            recheckCurrentBubble()
                        }
                    } else {
                        snapToEdge(params)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view: ${e.message}")
            stopSelf()
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = bubbleView?.width ?: dpToPx(76)
        val targetX = if (params.x + bubbleWidth / 2 < screenWidth / 2) {
            dpToPx(4)
        } else {
            screenWidth - bubbleWidth - dpToPx(4)
        }

        val startX = params.x
        edgeAnimator?.cancel()
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                bubbleView?.let { bv ->
                    if (bv.isAttachedToWindow) {
                        windowManager.updateViewLayout(bv, params)
                    }
                }
            }
        }
        edgeAnimator = animator
        animator.start()
    }

    private fun observeData() {
        serviceScope.launch {
            try {
                val app = TirupApplication.instance
                combine(
                    app.glucoseRepository.getLatestReading(),
                    app.settingsRepository.getSettings()
                ) { reading, settings ->
                    Pair(reading, settings)
                }.collectLatest { (reading, settings) ->
                    if (!settings.isFloatingBubbleEnabled) {
                        stopSelf()
                        return@collectLatest
                    }
                    if (reading != null) {
                        updateBubble(reading, settings)
                    } else {
                        bubbleView?.visibility = View.GONE
                        setHypoRipple(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in observeData: ${e.message}")
            }
        }
    }

    private fun recheckCurrentBubble() {
        serviceScope.launch {
            try {
                val app = TirupApplication.instance
                val latest = app.glucoseRepository.getLatestReading().first()
                val settings = app.settingsRepository.getSettings().first()
                if (latest != null && settings.isFloatingBubbleEnabled) {
                    updateBubble(latest, settings)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recheck bubble: ${e.message}")
            }
        }
    }

    private fun updateBubble(reading: GlucoseReading, settings: UserSettings) {
        lastKnownReading = reading
        lastKnownSettings = settings
        val valueMmol = reading.valueMmol
        val now = System.currentTimeMillis()

        // 1. Hysteresis calculation for out-of-range state
        // When in-range: triggers out-of-range if < 3.9 or > 10.0
        // When out-of-range: stays out-of-range until comfortably back in target:
        //    Low -> must rise to >= 4.1
        //    High -> must drop to <= 9.8
        val isOutOfRange = if (wasOutOfRange) {
            when {
                wasHypoActive -> valueMmol < 4.1
                else -> valueMmol > 9.8
            }
        } else {
            valueMmol < 3.9 || valueMmol > 10.0
        }

        val isHypo = valueMmol < 3.9

        // 2. Safety override for snooze:
        // If glucose is plummeting dangerously (<3.0) or dropping fast into hypo during hyper snooze, break snooze early!
        if (now < snoozeUntilTimestamp) {
            val shouldBreakSnooze = (isHypo && !wasHypoActive) || (valueMmol < 3.0)
            if (shouldBreakSnooze) {
                snoozeUntilTimestamp = 0L
                snoozeJob?.cancel()
            }
        }

        val isSnoozed = now < snoozeUntilTimestamp

        if (!isOutOfRange || isSnoozed) {
            bubbleView?.visibility = View.GONE
            setHypoRipple(false)
            if (!isOutOfRange) {
                wasOutOfRange = false
                wasHypoActive = false
            }
            return
        } else {
            val wasPreviouslyOutOfRange = wasOutOfRange
            wasOutOfRange = true
            wasHypoActive = isHypo

            bubbleView?.visibility = View.VISIBLE

            // Play PopIn sound ONLY on first transition into out-of-range!
            // Never play sound when quietly re-appearing after snooze expiration!
            if (!wasPreviouslyOutOfRange) {
                MedicalSoundPlayer.playBubblePopIn()
            }
        }

        val isMmol = settings.unit == GlucoseUnit.MMOL_L
        val displayVal = if (isMmol) {
            String.format(Locale.US, "%.1f", reading.valueMmol)
        } else {
            reading.getValue(GlucoseUnit.MG_DL).toInt().toString()
        }

        val arrow = reading.trendArrow?.trim() ?: "→"
        tvGlucose?.text = displayVal
        tvArrow?.text = arrow

        // Range colors strictly adhering to requirements:
        // <3.9 Red, 3.9..7.8 Pale Green (#4ADE80), 7.9..10.0 Emerald (#10B981), 10.1..13.9 Orange, >13.9 Purple
        val ringColor = when {
            valueMmol < 3.9 -> Color.parseColor("#EF4444")
            valueMmol <= 7.8 -> Color.parseColor("#4ADE80")
            valueMmol <= 10.0 -> Color.parseColor("#10B981")
            valueMmol <= 13.9 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F00F172A")) // Deep dark slate (94% opaque)
            setStroke(dpToPx(3), ringColor)
        }
        bubbleContainer?.background = bg

        // Water ripple waves on hypoglycemia (< 3.9 mmol/L)
        setHypoRipple(isHypo)
    }

    private fun setHypoRipple(isHypo: Boolean) {
        val container = bubbleContainer ?: return
        val ripple = bubbleRipple ?: return

        if (isHypo) {
            val rippleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26EF4444")) // Semi-transparent red wave
                setStroke(dpToPx(2), Color.parseColor("#EF4444"))
            }
            ripple.background = rippleBg
            ripple.visibility = View.VISIBLE

            if (rippleAnimatorSet == null || !rippleAnimatorSet!!.isRunning) {
                val scaleX = ObjectAnimator.ofFloat(ripple, "scaleX", 1.0f, 1.28f).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                }
                val scaleY = ObjectAnimator.ofFloat(ripple, "scaleY", 1.0f, 1.28f).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                }
                val alpha = ObjectAnimator.ofFloat(ripple, "alpha", 0.85f, 0.0f).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                }
                val pulseX = ObjectAnimator.ofFloat(container, "scaleX", 1.0f, 1.05f, 1.0f).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                }
                val pulseY = ObjectAnimator.ofFloat(container, "scaleY", 1.0f, 1.05f, 1.0f).apply {
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                }

                rippleAnimatorSet = AnimatorSet().apply {
                    duration = 1200
                    interpolator = DecelerateInterpolator()
                    playTogether(scaleX, scaleY, alpha, pulseX, pulseY)
                    start()
                }
            }
        } else {
            rippleAnimatorSet?.cancel()
            rippleAnimatorSet = null
            ripple.visibility = View.GONE
            ripple.scaleX = 1.0f
            ripple.scaleY = 1.0f
            ripple.alpha = 1.0f
            container.scaleX = 1.0f
            container.scaleY = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        snoozeJob?.cancel()
        edgeAnimator?.cancel()
        edgeAnimator = null
        rippleAnimatorSet?.cancel()
        rippleAnimatorSet = null
        try {
            bubbleView?.let { bv ->
                if (bv.isAttachedToWindow) {
                    windowManager.removeView(bv)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing bubble view: ${e.message}")
        }
        bubbleView = null
        bubbleRipple = null
        bubbleContainer = null
        tvGlucose = null
        tvArrow = null
        tvDelta = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatingBubbleService"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                try {
                    val intent = Intent(context, FloatingBubbleService::class.java)
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "start failed: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingBubbleService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "stop failed: ${e.message}")
            }
        }
    }
}
