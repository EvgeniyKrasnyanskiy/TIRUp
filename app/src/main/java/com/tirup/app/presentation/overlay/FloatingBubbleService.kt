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
import com.tirup.app.domain.model.GlucoseReading
import com.tirup.app.domain.model.GlucoseUnit
import com.tirup.app.domain.model.UserSettings
import com.tirup.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleContainer: View? = null
    private var tvGlucose: TextView? = null
    private var tvArrow: TextView? = null
    private var tvDelta: TextView? = null

    private var pulseAnimatorSet: AnimatorSet? = null

    override fun onBind(intent: Intent?): IBinder? = null

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
        bubbleContainer = view.findViewById(R.id.bubble_container)
        tvGlucose = view.findViewById(R.id.tv_bubble_glucose)
        tvArrow = view.findViewById(R.id.tv_bubble_arrow)
        tvDelta = view.findViewById(R.id.tv_bubble_delta)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
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
                        // Open main screen
                        val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
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
        val bubbleWidth = bubbleView?.width ?: dpToPx(66)
        val targetX = if (params.x + bubbleWidth / 2 < screenWidth / 2) {
            dpToPx(8)
        } else {
            screenWidth - bubbleWidth - dpToPx(8)
        }

        val startX = params.x
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
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in observeData: ${e.message}")
            }
        }
    }

    private fun updateBubble(reading: GlucoseReading, settings: UserSettings) {
        val isMmol = settings.unit == GlucoseUnit.MMOL_L
        val displayVal = if (isMmol) {
            String.format(Locale.US, "%.1f", reading.valueMmol)
        } else {
            reading.getValue(GlucoseUnit.MG_DL).toInt().toString()
        }

        val arrow = reading.trendArrow?.trim() ?: "→"
        tvGlucose?.text = displayVal
        tvArrow?.text = arrow

        val valueMmol = reading.valueMmol
        val tirLow = settings.targetRanges.tirLowMmol
        val tirHigh = settings.targetRanges.tirHighMmol

        val ringColor = when {
            valueMmol < tirLow -> Color.parseColor("#EF4444") // Red
            valueMmol > tirHigh -> Color.parseColor("#F59E0B") // Amber
            else -> Color.parseColor("#10B981") // Green
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EE0F172A")) // Deep dark slate (93% opaque)
            setStroke(dpToPx(3), ringColor)
        }
        bubbleContainer?.background = bg

        // Pulsing animation if hypo (< 3.9 mmol/L)
        val isHypo = valueMmol < tirLow
        setHypoPulse(isHypo)
    }

    private fun setHypoPulse(isHypo: Boolean) {
        val container = bubbleContainer ?: return
        if (isHypo) {
            if (pulseAnimatorSet == null || !pulseAnimatorSet!!.isRunning) {
                val animX = ObjectAnimator.ofFloat(container, "scaleX", 1.0f, 1.14f, 1.0f).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val animY = ObjectAnimator.ofFloat(container, "scaleY", 1.0f, 1.14f, 1.0f).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = AccelerateDecelerateInterpolator()
                }
                pulseAnimatorSet = AnimatorSet().apply {
                    playTogether(animX, animY)
                    start()
                }
            }
        } else {
            pulseAnimatorSet?.cancel()
            pulseAnimatorSet = null
            container.scaleX = 1.0f
            container.scaleY = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null
        try {
            bubbleView?.let { bv ->
                if (bv.isAttachedToWindow) {
                    windowManager.removeView(bv)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing bubble view: ${e.message}")
        }
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
