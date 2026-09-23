package com.tirup.app.data.alert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    fun playSound(tier: AlertTier, volumePercent: Int = 80) {
        isPlayingActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = tier.name
        audioScope.launch {
            try {
                when (tier) {
                    AlertTier.PREDICTIVE -> playPredictiveChime(volumePercent)
                    AlertTier.MAIN -> playTripleMainBeep(volumePercent)
                    AlertTier.CRITICAL -> {
                        boostAlarmVolumeIfNeeded()
                        try {
                            playCriticalAlarmSeries()
                        } finally {
                            isCriticalActive = false
                            restoreAlarmVolumeIfNeeded()
                        }
                    }
                    AlertTier.SIGNAL_LOSS -> {
                        boostAlarmVolumeIfNeeded()
                        playSignalLossTone()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play synthesized medical sound for tier=$tier: ${e.message}")
            } finally {
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
            }
        }
    }

    /**
     * Plays a rich, clear melodic test chime (~1.0s) to allow accurate volume evaluation.
     */
    fun playTestSound(volumePercent: Int = 80) {
        isPlayingActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = "TEST_SOUND"
        audioScope.launch {
            try {
                ensureAlarmStreamAudible()
                val factor = (volumePercent / 100f).coerceIn(0.15f, 1.0f)
                val prefix = ShortArray((SAMPLE_RATE * 0.05).toInt()) // 50ms silence for smooth DAC wake
                val n1 = generateSineWave(freq = 587.33, durationMs = 180, volume = 0.65f * factor)
                val n2 = generateSineWave(freq = 783.99, durationMs = 180, volume = 0.75f * factor)
                val n3 = generateSineWave(freq = 880.00, durationMs = 200, volume = 0.85f * factor)
                val n4 = generateSineWave(freq = 1174.66, durationMs = 350, volume = 0.95f * factor)

                val totalLen = prefix.size + n1.size + n2.size + n3.size + n4.size
                val audioData = ShortArray(totalLen)
                var off = 0
                System.arraycopy(prefix, 0, audioData, off, prefix.size); off += prefix.size
                System.arraycopy(n1, 0, audioData, off, n1.size); off += n1.size
                System.arraycopy(n2, 0, audioData, off, n2.size); off += n2.size
                System.arraycopy(n3, 0, audioData, off, n3.size); off += n3.size
                System.arraycopy(n4, 0, audioData, off, n4.size)

                playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
            } catch (e: Exception) {
                Log.w(TAG, "Test sound failed: ${e.message}")
            } finally {
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
            }
        }
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
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
            currentAudioTrack = null
        } catch (_: Exception) {}
        restoreAlarmVolumeIfNeeded()
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

    enum class CriticalToneType {
        STANDARD,      // ~12s 5-tone medical alarm
        EXTRA_HYPO,    // ~50s GDH FM sweep siren (450-850 Hz)
        EXTRA_HYPER    // ~16s high-pitched pulsed alarm
    }

    /**
     * Plays a critical alarm with the specified tone type.
     */
    fun playCriticalAlarm(type: CriticalToneType = CriticalToneType.STANDARD) {
        isPlayingActive = true
        isCriticalActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = when (type) {
            CriticalToneType.STANDARD -> AlertTier.CRITICAL.name
            CriticalToneType.EXTRA_HYPO -> "EXTRA_HYPO"
            CriticalToneType.EXTRA_HYPER -> "EXTRA_HYPER"
        }
        audioScope.launch {
            try {
                boostAlarmVolumeIfNeeded()
                when (type) {
                    CriticalToneType.STANDARD -> playCriticalAlarmSeries()
                    CriticalToneType.EXTRA_HYPO -> runExtraHypoSirenLoop()
                    CriticalToneType.EXTRA_HYPER -> runExtraHyperAlarmLoop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical alarm playback failed for type=$type: ${e.message}")
            } finally {
                isCriticalActive = false
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
                restoreAlarmVolumeIfNeeded()
            }
        }
    }

    /**
     * Extra-HYPO civil defense / GDH siren (~50 seconds) for glucose below critical threshold.
     */
    fun playExtraHypoSiren() {
        playCriticalAlarm(CriticalToneType.EXTRA_HYPO)
    }

    /**
     * Extra-HYPER piercing rapid pulsed alarm (~16 seconds) for glucose above critical threshold.
     */
    fun playExtraHyperAlarm() {
        playCriticalAlarm(CriticalToneType.EXTRA_HYPER)
    }

    fun playSuperHypoSiren() = playExtraHypoSiren()
    fun playSuperHyperAlarm() = playExtraHyperAlarm()

    /**
     * Tier 3: High-urgency alternating alarm siren (USAGE_ALARM).
     * Plays a series lasting ~12 seconds (8 bursts with 300ms pause), cancellable anytime.
     */
    private fun playCriticalAlarmSeries() {
        isCriticalActive = true

        val pulse1 = generateSineWave(freq = 1046.5, durationMs = 150, volume = 1.0f)
        val pulse2 = generateSineWave(freq = 784.0, durationMs = 150, volume = 1.0f)
        val pulse3 = generateSineWave(freq = 1046.5, durationMs = 150, volume = 1.0f)
        val pulse4 = generateSineWave(freq = 784.0, durationMs = 150, volume = 1.0f)
        val pulse5 = generateSineWave(freq = 1174.66, durationMs = 320, volume = 1.0f)
        val pause = ShortArray((SAMPLE_RATE * 0.35).toInt()) // 350ms pause

        val burstLen = pulse1.size + pulse2.size + pulse3.size + pulse4.size + pulse5.size + pause.size
        val burstData = ShortArray(burstLen)
        var offset = 0
        System.arraycopy(pulse1, 0, burstData, offset, pulse1.size); offset += pulse1.size
        System.arraycopy(pulse2, 0, burstData, offset, pulse2.size); offset += pulse2.size
        System.arraycopy(pulse3, 0, burstData, offset, pulse3.size); offset += pulse3.size
        System.arraycopy(pulse4, 0, burstData, offset, pulse4.size); offset += pulse4.size
        System.arraycopy(pulse5, 0, burstData, offset, pulse5.size); offset += pulse5.size
        System.arraycopy(pause, 0, burstData, offset, pause.size)

        // Repeat 8 times (~12 seconds total), but stop immediately if cancelled
        for (cycle in 0 until 8) {
            if (!isCriticalActive || !isPlayingActive) break
            playRawPcm(burstData, usage = AudioAttributes.USAGE_ALARM)
        }
    }

    /**
     * Generates and plays the 50-second continuous civil defense / GDH air-raid siren.
     * Uses FM modulation oscillating smoothly between 450 Hz and 850 Hz with 2nd harmonic.
     */
    private fun runExtraHypoSirenLoop() {
        val cycleSec = 3.0
        val numSamples = (SAMPLE_RATE * cycleSec).toInt()
        val sirenCycle = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Cosine modulation: starts at 450 Hz, peaks at 850 Hz at 1.5s, returns to 450 Hz at 3.0s
            val modProgress = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * t / cycleSec))
            val currentFreq = 450.0 + (850.0 - 450.0) * modProgress
            phase += 2.0 * PI * currentFreq / SAMPLE_RATE
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            // Authentic air-raid siren timbre: fundamental + 2nd harmonic
            val raw = 0.72 * sin(phase) + 0.28 * sin(2.0 * phase)
            val sampleVal = (raw * Short.MAX_VALUE).toInt()
            sirenCycle[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        // Loop 17 cycles (~51 seconds total)
        for (cycle in 0 until 17) {
            if (!isCriticalActive || !isPlayingActive) break
            playRawPcm(sirenCycle, usage = AudioAttributes.USAGE_ALARM)
        }
    }

    /**
     * Generates and plays the 16-second piercing pulsed high-urgency alarm for extreme hyper.
     * High-pitched alternating chime bursts (1760 Hz & 2349 Hz).
     */
    private fun runExtraHyperAlarmLoop() {
        val b1 = generateSineWave(freq = 1760.00, durationMs = 80, volume = 0.95f)
        val b2 = generateSineWave(freq = 2349.32, durationMs = 80, volume = 1.0f)
        val b3 = generateSineWave(freq = 1760.00, durationMs = 80, volume = 0.95f)
        val b4 = generateSineWave(freq = 2349.32, durationMs = 140, volume = 1.0f)
        val pause = ShortArray((SAMPLE_RATE * 0.62).toInt()) // 620ms pause -> 1.0s total cycle

        val burstLen = b1.size + b2.size + b3.size + b4.size + pause.size
        val burstData = ShortArray(burstLen)
        var offset = 0
        System.arraycopy(b1, 0, burstData, offset, b1.size); offset += b1.size
        System.arraycopy(b2, 0, burstData, offset, b2.size); offset += b2.size
        System.arraycopy(b3, 0, burstData, offset, b3.size); offset += b3.size
        System.arraycopy(b4, 0, burstData, offset, b4.size); offset += b4.size
        System.arraycopy(pause, 0, burstData, offset, pause.size)

        // Loop 16 cycles (~16 seconds total)
        for (cycle in 0 until 16) {
            if (!isCriticalActive || !isPlayingActive) break
            playRawPcm(burstData, usage = AudioAttributes.USAGE_ALARM)
        }
    }

    /**
     * Caregiver SOS Wakeup: Plays 50-second continuous civil defense Extra-Hypo siren
     * with volume forced to 100% on USAGE_ALARM stream.
     */
    fun playCaregiverSosAlarm() {
        isPlayingActive = true
        isCriticalActive = true
        _isPlaying.value = true
        _currentlyPlayingTag.value = "CAREGIVER_SOS"
        audioScope.launch {
            try {
                boostAlarmVolumeToMax()
                runExtraHypoSirenLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Caregiver SOS alarm playback error: ${e.message}")
            } finally {
                isCriticalActive = false
                _isPlaying.value = false
                _currentlyPlayingTag.value = null
                restoreAlarmVolumeIfNeeded()
            }
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
