package com.tirup.app.data.alert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private var currentAudioTrack: AudioTrack? = null

    fun playSound(tier: AlertTier) {
        isPlayingActive = true
        audioScope.launch {
            try {
                when (tier) {
                    AlertTier.PREDICTIVE -> playPredictiveChime()
                    AlertTier.MAIN -> playTripleMainBeep()
                    AlertTier.CRITICAL -> {
                        boostAlarmVolumeIfNeeded()
                        playCriticalAlarmSeries()
                    }
                    AlertTier.SIGNAL_LOSS -> {
                        boostAlarmVolumeIfNeeded()
                        playSignalLossTone()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play synthesized medical sound for tier=$tier: ${e.message}")
            }
        }
    }

    /**
     * Plays a short gentle ascending "beep" (pop-in) when the floating bubble appears during hypoglycemia.
     */
    fun playBubblePopIn() {
        audioScope.launch {
            try {
                val p1 = generateSineWave(freq = 880.0, durationMs = 45, volume = 0.55f)
                val p2 = generateSineWave(freq = 1174.66, durationMs = 65, volume = 0.65f)
                val audioData = ShortArray(p1.size + p2.size)
                System.arraycopy(p1, 0, audioData, 0, p1.size)
                System.arraycopy(p2, 0, audioData, p1.size, p2.size)
                playRawPcm(audioData, AudioAttributes.USAGE_NOTIFICATION)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play bubble pop-in: ${e.message}")
            }
        }
    }

    /**
     * Plays a short soft descending "bob" (reverse pop-out) when tapping the floating bubble to dismiss/snooze.
     */
    fun playBubblePopOut() {
        audioScope.launch {
            try {
                val p1 = generateSineWave(freq = 659.25, durationMs = 45, volume = 0.55f)
                val p2 = generateSineWave(freq = 369.99, durationMs = 65, volume = 0.50f)
                val audioData = ShortArray(p1.size + p2.size)
                System.arraycopy(p1, 0, audioData, 0, p1.size)
                System.arraycopy(p2, 0, audioData, p1.size, p2.size)
                playRawPcm(audioData, AudioAttributes.USAGE_NOTIFICATION)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play bubble pop-out: ${e.message}")
            }
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
    private fun playPredictiveChime() {
        val note1 = generateSineWave(freq = 587.33, durationMs = 180, volume = 0.55f)
        val note2 = generateSineWave(freq = 880.00, durationMs = 260, volume = 0.65f)
        val audioData = ShortArray(note1.size + note2.size)
        System.arraycopy(note1, 0, audioData, 0, note1.size)
        System.arraycopy(note2, 0, audioData, note1.size, note2.size)

        playRawPcm(audioData, usage = AudioAttributes.USAGE_NOTIFICATION)
    }

    /**
     * Tier 2: Main alert played 3 times with 1.5 second pause between each.
     */
    private fun playTripleMainBeep() {
        val note1 = generateSineWave(freq = 784.0, durationMs = 140, volume = 0.85f)
        val note2 = generateSineWave(freq = 987.77, durationMs = 180, volume = 0.90f)
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

        playRawPcm(audioData, usage = AudioAttributes.USAGE_NOTIFICATION)
    }

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
            if (!isCriticalActive) break
            playRawPcm(burstData, usage = AudioAttributes.USAGE_ALARM)
        }
        isCriticalActive = false
        restoreAlarmVolumeIfNeeded()
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

        val contentType = if (usage == AudioAttributes.USAGE_ALARM) {
            AudioAttributes.CONTENT_TYPE_ALARM
        } else {
            AudioAttributes.CONTENT_TYPE_SONIFICATION
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
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
            while (elapsed < sleepTimeMs && isPlayingActive && (usage != AudioAttributes.USAGE_ALARM || isCriticalActive || isSignalLossActive)) {
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
}
