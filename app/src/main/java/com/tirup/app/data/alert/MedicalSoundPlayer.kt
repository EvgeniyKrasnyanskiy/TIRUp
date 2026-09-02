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

    fun playSound(tier: AlertTier) {
        audioScope.launch {
            try {
                when (tier) {
                    AlertTier.PREDICTIVE -> playPredictiveChime()
                    AlertTier.MAIN -> playTripleMainBeep()
                    AlertTier.CRITICAL -> playCriticalAlarmSiren()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play synthesized medical sound for tier=$tier: ${e.message}")
            }
        }
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
     */
    private fun playCriticalAlarmSiren() {
        val pulse1 = generateSineWave(freq = 1046.5, durationMs = 180, volume = 1.0f)
        val pulse2 = generateSineWave(freq = 784.0, durationMs = 180, volume = 1.0f)
        val pulse3 = generateSineWave(freq = 1046.5, durationMs = 180, volume = 1.0f)
        val pulse4 = generateSineWave(freq = 784.0, durationMs = 180, volume = 1.0f)
        val pulse5 = generateSineWave(freq = 1174.66, durationMs = 380, volume = 1.0f)

        val totalLen = pulse1.size + pulse2.size + pulse3.size + pulse4.size + pulse5.size
        val audioData = ShortArray(totalLen)
        var offset = 0

        System.arraycopy(pulse1, 0, audioData, offset, pulse1.size); offset += pulse1.size
        System.arraycopy(pulse2, 0, audioData, offset, pulse2.size); offset += pulse2.size
        System.arraycopy(pulse3, 0, audioData, offset, pulse3.size); offset += pulse3.size
        System.arraycopy(pulse4, 0, audioData, offset, pulse4.size); offset += pulse4.size
        System.arraycopy(pulse5, 0, audioData, offset, pulse5.size)

        playRawPcm(audioData, usage = AudioAttributes.USAGE_ALARM)
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
            track.write(data, 0, data.size)
            track.play()
            Thread.sleep((data.size.toDouble() / SAMPLE_RATE * 1000.0).toLong() + 50L)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
