package com.tirup.app.data.alert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import com.tirup.app.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object MedicalSoundPlayer {

    private const val TAG = "MedicalSoundPlayer"
    private const val SAMPLE_RATE = 44100

    private val audioScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isCriticalActive = false
    @Volatile
    private var isSignalLossActive = false
    @Volatile
    private var isPlayingActive = true
    private var previousAlarmVolume: Int? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentlyPlayingTag = MutableStateFlow<String?>(null)
    val currentlyPlayingTag: StateFlow<String?> = _currentlyPlayingTag.asStateFlow()

    private var currentAudioTrack: AudioTrack? = null
    private var currentMediaPlayer: MediaPlayer? = null

    /**
     * Plays a pre-mastered MP3 audio resource with full hardware-independent loudness
     * on the USAGE_ALARM audio stream. Supports clean repeating / looping and instant stopping.
     */
    private fun playRawResource(
        @RawRes resId: Int,
        repeatCount: Int = 1,
        volumePercent: Int = 80,
        customTag: String? = null,
        pauseBetweenRepeatsMs: Long = 0L,
        boostVolume: Boolean = false
    ) {
        stopAll()
        isPlayingActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = customTag
        if (boostVolume) {
            boostAlarmVolumeIfNeeded()
        }
        audioScope.launch {
            try {
                val context = try { com.tirup.app.TirupApplication.instance } catch (_: Exception) { null } ?: return@launch
                ensureAlarmStreamAudible()
                val factor = (volumePercent / 100f).coerceIn(0.05f, 1.0f)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                for (cycle in 0 until repeatCount) {
                    if (!isPlayingActive) break

                    val mp = MediaPlayer()
                    synchronized(this@MedicalSoundPlayer) {
                        if (!isPlayingActive) {
                            try { mp.release() } catch (_: Exception) {}
                            return@launch
                        }
                        currentMediaPlayer = mp
                    }

                    mp.setAudioAttributes(audioAttributes)
                    val afd = context.resources.openRawResourceFd(resId) ?: run {
                        synchronized(this@MedicalSoundPlayer) {
                            try { mp.release() } catch (_: Exception) {}
                            if (currentMediaPlayer == mp) currentMediaPlayer = null
                        }
                        return@launch
                    }
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    mp.setVolume(factor, factor)
                    mp.prepare()

                    val completionDeferred = CompletableDeferred<Unit>()
                    mp.setOnCompletionListener {
                        completionDeferred.complete(Unit)
                    }
                    mp.setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                        completionDeferred.complete(Unit)
                        true
                    }

                    mp.start()

                    while (!completionDeferred.isCompleted && isPlayingActive) {
                        delay(40L)
                    }

                    synchronized(this@MedicalSoundPlayer) {
                        try {
                            if (mp.isPlaying) mp.stop()
                        } catch (_: Exception) {}
                        try {
                            mp.release()
                        } catch (_: Exception) {}
                        if (currentMediaPlayer == mp) {
                            currentMediaPlayer = null
                        }
                    }

                    if (cycle < repeatCount - 1 && isPlayingActive && pauseBetweenRepeatsMs > 0) {
                        var pausedElapsed = 0L
                        while (pausedElapsed < pauseBetweenRepeatsMs && isPlayingActive) {
                            val step = 50L.coerceAtMost(pauseBetweenRepeatsMs - pausedElapsed)
                            delay(step)
                            pausedElapsed += step
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing raw resource $resId: ${e.message}")
            } finally {
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
                if (boostVolume) {
                    restoreAlarmVolumeIfNeeded()
                }
            }
        }
    }

    fun playSound(tier: AlertTier, volumePercent: Int = 80, customTag: String? = null) {
        when (tier) {
            AlertTier.PREDICTIVE -> playPredictiveAlert(volumePercent, customTag)
            AlertTier.MAIN -> playLowAlert(volumePercent, repeatCount = 1, customTag = customTag)
            AlertTier.CRITICAL -> playProlongedHypoAlert(volumePercent, customTag)
            AlertTier.SIGNAL_LOSS -> playSignalLossAlert(volumePercent, customTag)
            AlertTier.RAPID_CHANGE -> playFallingFastAlert(volumePercent, customTag)
        }
    }

    /**
     * Plays a rich, clear melodic test chime to allow accurate volume evaluation.
     */
    fun playTestSound(volumePercent: Int = 80) {
        playRawResource(
            resId = R.raw.tirup_predict,
            repeatCount = 1,
            volumePercent = volumePercent,
            customTag = "TEST_SOUND"
        )
    }

    private fun ensureAlarmStreamAudible() {
        try {
            val context = try { com.tirup.app.TirupApplication.instance } catch (_: Exception) { null } ?: return
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val minDesiredVol = (maxVol * 0.85).toInt().coerceAtLeast(1)
            if (currentVol < minDesiredVol) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, minDesiredVol, 0)
                Log.i(TAG, "Ensured STREAM_ALARM volume to $minDesiredVol (was $currentVol)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure alarm volume: ${e.message}")
        }
    }

    /**
     * Plays a pleasant harmonic ascending "pop-chime" when the floating bubble appears.
     */
    fun playBubblePopIn() {
        audioScope.launch {
            try {
                val pop = generateBubbleSweep(startFreq = 587.33, endFreq = 1046.5, durationMs = 50, volume = 0.65f)
                val chime = generateSineWave(freq = 1174.66, durationMs = 55, volume = 0.55f)
                val audioData = ShortArray(pop.size + chime.size)
                System.arraycopy(pop, 0, audioData, 0, pop.size)
                System.arraycopy(chime, 0, audioData, pop.size, chime.size)
                playShortTone(audioData)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play bubble pop-in: ${e.message}")
            }
        }
    }

    /**
     * Plays a soft, organic descending "bubble burst" when tapping the floating bubble to dismiss/snooze.
     */
    fun playBubblePopOut() {
        audioScope.launch {
            try {
                val pop = generateBubbleSweep(startFreq = 540.0, endFreq = 175.0, durationMs = 55, volume = 0.60f)
                playShortTone(pop)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play bubble pop-out: ${e.message}")
            }
        }
    }

    /**
     * Plays a distinct urgent ascending harmonic dual-chime for the Daily Compensator "Last Chance to reach daily TIR" alert.
     * Triggered when remaining time in day is barely enough to achieve daily TIR target.
     * Uses USAGE_ALARM stream to guarantee audibility.
     */
    fun playLastChanceAlertTone() {
        isPlayingActive = true
        audioScope.launch {
            try {
                boostAlarmVolumeIfNeeded()
                // A5 (880 Hz) -> D6 (1174.66 Hz), repeated twice with short pause
                val ping1 = generateSineWave(freq = 880.0, durationMs = 120, volume = 0.95f)
                val ping2 = generateSineWave(freq = 1174.66, durationMs = 240, volume = 1.0f)
                val shortPause = ShortArray((SAMPLE_RATE * 0.10).toInt())
                val burst = ShortArray(ping1.size + ping2.size)
                System.arraycopy(ping1, 0, burst, 0, ping1.size)
                System.arraycopy(ping2, 0, burst, ping1.size, ping2.size)

                val fullTone = ShortArray((burst.size * 2) + shortPause.size)
                var off = 0
                System.arraycopy(burst, 0, fullTone, off, burst.size); off += burst.size
                System.arraycopy(shortPause, 0, fullTone, off, shortPause.size); off += shortPause.size
                System.arraycopy(burst, 0, fullTone, off, burst.size)

                playRawPcm(fullTone, usage = AudioAttributes.USAGE_ALARM)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play last chance alert tone: ${e.message}")
            } finally {
                restoreAlarmVolumeIfNeeded()
            }
        }
    }

    private fun boostAlarmVolumeToMax() {
        try {
            val context = try { com.tirup.app.TirupApplication.instance } catch (_: Exception) { null } ?: return
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            if (previousAlarmVolume == null) {
                previousAlarmVolume = currentVol
            }
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVol, 0)
            Log.i(TAG, "Alarm volume boosted to 100% ($maxVol) for Caregiver SOS")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to boost alarm volume to max: ${e.message}")
        }
    }

    private fun boostAlarmVolumeIfNeeded() {
        try {
            val context = try { com.tirup.app.TirupApplication.instance } catch (_: Exception) { null } ?: return
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val minDesiredVol = (maxVol * 0.80).toInt().coerceAtLeast(1)
            if (currentVol < minDesiredVol) {
                previousAlarmVolume = currentVol
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, minDesiredVol, 0)
                Log.i(TAG, "Alarm volume boosted from $currentVol to $minDesiredVol for AlertTier")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to boost alarm volume: ${e.message}")
        }
    }

    private fun restoreAlarmVolumeIfNeeded() {
        val prev = previousAlarmVolume ?: return
        try {
            val context = try { com.tirup.app.TirupApplication.instance } catch (_: Exception) { null } ?: return
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, prev, 0)
            Log.i(TAG, "Restored alarm volume to $prev")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore alarm volume: ${e.message}")
        } finally {
            previousAlarmVolume = null
        }
    }

    /**
     * Tier 4: Signal loss urgent descending alarm tone (659 Hz -> 440 Hz) on USAGE_ALARM stream.
     */
    private fun playSignalLossTone() {
        isSignalLossActive = true
        try {
            val note1 = generateSineWave(freq = 659.25, durationMs = 200, volume = 0.85f)
            val note2 = generateSineWave(freq = 440.00, durationMs = 300, volume = 0.90f)
            val singlePattern = ShortArray(note1.size + note2.size)
            System.arraycopy(note1, 0, singlePattern, 0, note1.size)
            System.arraycopy(note2, 0, singlePattern, note1.size, note2.size)

            val pause = ShortArray((SAMPLE_RATE * 0.4).toInt()) // 400ms pause
            val totalLen = (singlePattern.size * 2) + pause.size
            val audioData = ShortArray(totalLen)
            var offset = 0
            System.arraycopy(singlePattern, 0, audioData, offset, singlePattern.size); offset += singlePattern.size
            System.arraycopy(pause, 0, audioData, offset, pause.size); offset += pause.size
            System.arraycopy(singlePattern, 0, audioData, offset, singlePattern.size)

            playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
        } finally {
            isSignalLossActive = false
            restoreAlarmVolumeIfNeeded()
        }
    }

    fun stopAll() {
        isCriticalActive = false
        isSignalLossActive = false
        isPlayingActive = false
        _isPlaying.value = false
        _currentlyPlayingTag.value = null
        synchronized(this) {
            try {
                currentMediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {}
            currentMediaPlayer = null

            try {
                currentAudioTrack?.stop()
                currentAudioTrack?.release()
            } catch (_: Exception) {}
            currentAudioTrack = null
        }
        restoreAlarmVolumeIfNeeded()
    }

    /**
     * Plays a unique, dedicated battery alert tone based on the threshold (15%, 10%, or 5%).
     * Decoupled from glucose alerts to allow instant auditory recognition of phone battery level.
     */
    fun playBatteryAlert(threshold: Int, volumePercent: Int = 80, customTag: String? = null) {
        isPlayingActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = customTag ?: "BATTERY_$threshold"
        audioScope.launch {
            try {
                when {
                    threshold <= 5 -> {
                        boostAlarmVolumeIfNeeded()
                        try {
                            playBatteryLow5(volumePercent)
                        } finally {
                            restoreAlarmVolumeIfNeeded()
                        }
                    }
                    threshold <= 10 -> playBatteryLow10(volumePercent)
                    else -> playBatteryLow15(volumePercent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play battery alert sound for threshold=$threshold: ${e.message}")
            } finally {
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
            }
        }
    }

    /**
     * Battery < 15%: Soft melodic descending 3-note chime «Energy Drop» (523 Hz -> 440 Hz -> 349 Hz).
     */
    private fun playBatteryLow15(volumePercent: Int = 80) {
        ensureAlarmStreamAudible()
        val factor = (volumePercent / 100f).coerceIn(0.15f, 1.0f)
        val note1 = generateSineWave(freq = 523.25, durationMs = 150, volume = 0.65f * factor) // C5
        val note2 = generateSineWave(freq = 440.00, durationMs = 160, volume = 0.60f * factor) // A4
        val note3 = generateSineWave(freq = 349.23, durationMs = 280, volume = 0.70f * factor) // F4
        val totalLen = note1.size + note2.size + note3.size
        val audioData = ShortArray(totalLen)
        var off = 0
        System.arraycopy(note1, 0, audioData, off, note1.size); off += note1.size
        System.arraycopy(note2, 0, audioData, off, note2.size); off += note2.size
        System.arraycopy(note3, 0, audioData, off, note3.size)
        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
    }

    /**
     * Battery < 10%: Urgent double descending warning pulses ([587 Hz -> 392 Hz], 140ms pause, [587 Hz -> 392 Hz]).
     */
    private fun playBatteryLow10(volumePercent: Int = 80) {
        ensureAlarmStreamAudible()
        val factor = (volumePercent / 100f).coerceIn(0.15f, 1.0f)
        val note1 = generateSineWave(freq = 587.33, durationMs = 120, volume = 0.80f * factor) // D5
        val note2 = generateSineWave(freq = 392.00, durationMs = 180, volume = 0.85f * factor) // G4
        val pulse = ShortArray(note1.size + note2.size)
        System.arraycopy(note1, 0, pulse, 0, note1.size)
        System.arraycopy(note2, 0, pulse, note1.size, note2.size)

        val pause = ShortArray((SAMPLE_RATE * 0.14).toInt()) // 140ms silence
        val totalLen = (pulse.size * 2) + pause.size
        val audioData = ShortArray(totalLen)
        var off = 0
        System.arraycopy(pulse, 0, audioData, off, pulse.size); off += pulse.size
        System.arraycopy(pause, 0, audioData, off, pause.size); off += pause.size
        System.arraycopy(pulse, 0, audioData, off, pulse.size)
        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
    }

    /**
     * Battery < 5%: Critical low battery alarm beacon (4 rapid descending pulses x 2 series on STREAM_ALARM).
     */
    private fun playBatteryLow5(volumePercent: Int = 80) {
        val factor = (volumePercent / 100f).coerceIn(0.20f, 1.0f)
        val nHigh = generateSineWave(freq = 659.25, durationMs = 75, volume = 0.95f * factor) // E5
        val nLow = generateSineWave(freq = 523.25, durationMs = 95, volume = 1.0f * factor)   // C5
        val gap = ShortArray((SAMPLE_RATE * 0.05).toInt()) // 50ms gap

        val burstUnit = ShortArray(nHigh.size + nLow.size + gap.size)
        var bOff = 0
        System.arraycopy(nHigh, 0, burstUnit, bOff, nHigh.size); bOff += nHigh.size
        System.arraycopy(nLow, 0, burstUnit, bOff, nLow.size); bOff += nLow.size
        System.arraycopy(gap, 0, burstUnit, bOff, gap.size)

        val seriesLen = burstUnit.size * 4
        val singleSeries = ShortArray(seriesLen)
        for (i in 0 until 4) {
            System.arraycopy(burstUnit, 0, singleSeries, i * burstUnit.size, burstUnit.size)
        }

        val pause = ShortArray((SAMPLE_RATE * 0.25).toInt()) // 250ms silence between series
        val totalLen = (singleSeries.size * 2) + pause.size
        val audioData = ShortArray(totalLen)
        var off = 0
        System.arraycopy(singleSeries, 0, audioData, off, singleSeries.size); off += singleSeries.size
        System.arraycopy(pause, 0, audioData, off, pause.size); off += pause.size
        System.arraycopy(singleSeries, 0, audioData, off, singleSeries.size)

        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
    }

    /**
     * Tier 1: Soft melodic dual-tone chime (587 Hz -> 880 Hz).
     */
    private fun playPredictiveChime(volumePercent: Int = 80) {
        ensureAlarmStreamAudible()
        val factor = (volumePercent / 100f).coerceIn(0.15f, 1.0f)
        val note1 = generateSineWave(freq = 587.33, durationMs = 180, volume = 0.55f * factor)
        val note2 = generateSineWave(freq = 880.00, durationMs = 260, volume = 0.65f * factor)
        val audioData = ShortArray(note1.size + note2.size)
        System.arraycopy(note1, 0, audioData, 0, note1.size)
        System.arraycopy(note2, 0, audioData, note1.size, note2.size)

        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
    }

    /**
     * Tier 2: Main alert played 3 times with 1.5 second pause between each.
     */
    private fun playTripleMainBeep(volumePercent: Int = 80) {
        ensureAlarmStreamAudible()
        val factor = (volumePercent / 100f).coerceIn(0.15f, 1.0f)
        val note1 = generateSineWave(freq = 784.0, durationMs = 140, volume = 0.85f * factor)
        val note2 = generateSineWave(freq = 987.77, durationMs = 180, volume = 0.90f * factor)
        val singleBeep = ShortArray(note1.size + note2.size)
        System.arraycopy(note1, 0, singleBeep, 0, note1.size)
        System.arraycopy(note2, 0, singleBeep, note1.size, note2.size)

        val pause = ShortArray((SAMPLE_RATE * 1.5).toInt()) // 1.5 seconds silence

        val totalLen = (singleBeep.size * 3) + (pause.size * 2)
        val audioData = ShortArray(totalLen)
        var offset = 0

        // 1st play
        System.arraycopy(singleBeep, 0, audioData, offset, singleBeep.size); offset += singleBeep.size
        // 1.5s pause
        System.arraycopy(pause, 0, audioData, offset, pause.size); offset += pause.size
        // 2nd play
        System.arraycopy(singleBeep, 0, audioData, offset, singleBeep.size); offset += singleBeep.size
        // 1.5s pause
        System.arraycopy(pause, 0, audioData, offset, pause.size); offset += pause.size
        // 3rd play
        System.arraycopy(singleBeep, 0, audioData, offset, singleBeep.size)

        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
    }

    /**
     * Tier 1: Soft predictive warning chime (1 play)
     */
    fun playPredictiveAlert(volumePercent: Int = 80, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_predict,
            repeatCount = 1,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.PREDICTIVE.name
        )
    }

    /**
     * Tier 2: Confirmed Low glucose (1 play by default)
     */
    fun playLowAlert(volumePercent: Int = 80, repeatCount: Int = 1, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_low,
            repeatCount = repeatCount,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.MAIN.name
        )
    }

    /**
     * Tier 2: Confirmed High glucose (1 play by default)
     */
    fun playHighAlert(volumePercent: Int = 80, repeatCount: Int = 1, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_high,
            repeatCount = repeatCount,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.MAIN.name
        )
    }

    /**
     * Tier 2 Speed: Rapid glucose drop (1 play)
     */
    fun playFallingFastAlert(volumePercent: Int = 80, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_falling_fast,
            repeatCount = 1,
            volumePercent = volumePercent,
            customTag = customTag ?: "FALLING_FAST"
        )
    }

    /**
     * Tier 2 Speed: Rapid glucose rise (1 play)
     */
    fun playRisingFastAlert(volumePercent: Int = 80, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_rising_fast,
            repeatCount = 1,
            volumePercent = volumePercent,
            customTag = customTag ?: "RISING_FAST"
        )
    }

    /**
     * Tier 4: Sensor signal loss (1 play)
     */
    fun playSignalLossAlert(volumePercent: Int = 80, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_obsolete,
            repeatCount = 1,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.SIGNAL_LOSS.name,
            boostVolume = true
        )
    }

    /**
     * Tier 3 Critical: Prolonged Hypo (< 3.9 for > 20 min) -> 5 repeats of tirup_low with 400ms pause
     */
    fun playProlongedHypoAlert(volumePercent: Int = 100, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_low,
            repeatCount = 5,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.CRITICAL.name,
            pauseBetweenRepeatsMs = 400L,
            boostVolume = true
        )
    }

    /**
     * Tier 3 Critical: Prolonged Hyper (> 10.0 for > 90 min) -> 5 repeats of tirup_high with 400ms pause
     */
    fun playProlongedHyperAlert(volumePercent: Int = 100, customTag: String? = null) {
        playRawResource(
            resId = R.raw.tirup_high,
            repeatCount = 5,
            volumePercent = volumePercent,
            customTag = customTag ?: AlertTier.CRITICAL.name,
            pauseBetweenRepeatsMs = 400L,
            boostVolume = true
        )
    }

    /**
     * Tier 3 Critical: Severe / Extreme Hypo (< 3.0 mmol/l) -> 20 cycles (~60s) of tirup_very_low siren
     */
    fun playExtraHypoSiren() {
        playRawResource(
            resId = R.raw.tirup_very_low,
            repeatCount = 20,
            volumePercent = 100,
            customTag = "EXTRA_HYPO",
            boostVolume = true
        )
    }

    /**
     * Tier 3 Critical: Severe / Extreme Hyper (> 13.9 mmol/l) -> 10 cycles (~35s) of tirup_very_high alarm
     */
    fun playExtraHyperAlarm() {
        playRawResource(
            resId = R.raw.tirup_very_high,
            repeatCount = 10,
            volumePercent = 100,
            customTag = "EXTRA_HYPER",
            boostVolume = true
        )
    }

    fun playSuperHypoSiren() = playExtraHypoSiren()
    fun playSuperHyperAlarm() = playExtraHyperAlarm()

    /**
     * Caregiver SOS Wakeup: 20 cycles of tirup_very_low on USAGE_ALARM stream at 100% volume
     */
    fun playCaregiverSosAlarm() {
        playRawResource(
            resId = R.raw.tirup_very_low,
            repeatCount = 20,
            volumePercent = 100,
            customTag = "CAREGIVER_SOS",
            boostVolume = true
        )
    }

    enum class CriticalToneType {
        STANDARD,      // Prolonged out-of-range alarm
        EXTRA_HYPO,    // ~60s tirup_very_low emergency siren
        EXTRA_HYPER    // ~35s tirup_very_high emergency alarm
    }

    fun playCriticalAlarm(type: CriticalToneType = CriticalToneType.STANDARD) {
        when (type) {
            CriticalToneType.STANDARD -> playProlongedHypoAlert()
            CriticalToneType.EXTRA_HYPO -> playExtraHypoSiren()
            CriticalToneType.EXTRA_HYPER -> playExtraHyperAlarm()
        }
    }

    private fun generateSineWave(freq: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)
        val angularFreq = 2.0 * PI * freq

        // Hann window smoothing for first 15ms and last 15ms to prevent clicks
        val fadeSamples = (SAMPLE_RATE * 0.015).toInt().coerceAtMost(numSamples / 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var envelope = 1.0
            if (i < fadeSamples) {
                envelope = 0.5 * (1.0 - kotlin.math.cos(PI * i / fadeSamples))
            } else if (i > numSamples - fadeSamples) {
                val rem = numSamples - i
                envelope = 0.5 * (1.0 - kotlin.math.cos(PI * rem / fadeSamples))
            }

            val sampleVal = (sin(angularFreq * t) * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun playRawPcm(data: ShortArray, usage: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(data.size * 2)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        var track: AudioTrack? = null
        try {
            track = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STATIC,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            currentAudioTrack = track
            track.write(data, 0, data.size)
            track.play()
            val sleepTimeMs = (data.size.toDouble() / SAMPLE_RATE * 1000.0).toLong() + 30L
            val step = 100L
            var elapsed = 0L
            while (elapsed < sleepTimeMs && isPlayingActive) {
                Thread.sleep(step)
                elapsed += step
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
                if (currentAudioTrack == track) currentAudioTrack = null
            } catch (_: Exception) {}
        }
    }

    private fun generateBubbleSweep(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Float
    ): ShortArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        val fadeSamples = (SAMPLE_RATE * 0.005).toInt().coerceAtMost(numSamples / 4)

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq * Math.pow(endFreq / startFreq, progress)
            phase += 2.0 * PI * currentFreq / SAMPLE_RATE

            val attack = if (i < fadeSamples) (i.toDouble() / fadeSamples) else 1.0
            val decay = Math.exp(-3.5 * progress)
            val envelope = attack * decay

            val sampleVal = ((sin(phase) + sin(phase * 2.0) * 0.2) / 1.2 * envelope * volume * Short.MAX_VALUE).toInt()
            samples[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun playShortTone(data: ShortArray) {
        var track: AudioTrack? = null
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(data.size * 2)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            track = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STATIC,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(data, 0, data.size)
            track.play()
            val durationMs = (data.size.toDouble() / SAMPLE_RATE * 1000.0).toLong() + 35L
            Thread.sleep(durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Short tone playback error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
