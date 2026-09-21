// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.nulis.launcher.ui.theme.NulisHaptics
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The launcher's voice. Eight very short sounds, one per haptic role, **generated in code** -
 * there is not a single audio file in this project, and nothing is sampled from anywhere.
 *
 * Each one is a sine (sometimes two) under a raised-cosine attack and an exponential decay, so
 * none of them start or end with a click of their own. They are deliberately quiet, deliberately
 * brief, and off unless the user turns them on.
 */
enum class UiSound(
    /** Where the tone starts, in hertz. */
    val fromHz: Double,
    /** Where it ends; the same as [fromHz] for a flat tone. */
    val toHz: Double,
    val millis: Int,
    /** How fast it dies away. Larger is shorter. */
    val decay: Double,
    val gain: Double,
) {
    /** Choosing an option. The lightest thing here. */
    TICK(1_250.0, 1_250.0, 14, 60.0, 0.35),

    /** Sliding along a scale: shorter and higher, so a run of them does not become a buzz. */
    FREQUENT_TICK(1_750.0, 1_750.0, 9, 110.0, 0.22),

    /** Something lifting off the page. */
    PICKUP(300.0, 240.0, 45, 26.0, 0.5),

    /** Letting it go. */
    DROP(240.0, 190.0, 60, 20.0, 0.45),

    /** A drawer or sheet committing to open: a small rise. */
    THRESHOLD(620.0, 940.0, 55, 22.0, 0.4),

    /** Finishing something: a fifth, which is the only interval in here. */
    CONFIRM(880.0, 1_320.0, 95, 14.0, 0.4),

    TOGGLE_ON(640.0, 960.0, 38, 30.0, 0.35),
    TOGGLE_OFF(960.0, 640.0, 38, 30.0, 0.35),
    ;

    /** 16-bit mono PCM at [SampleRate]. */
    fun render(volume: Float): ShortArray {
        val frames = SampleRate * millis / 1000
        val out = ShortArray(frames)
        val attack = (frames * 0.12f).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until frames) {
            val t = i.toDouble() / frames
            // Sweep the frequency rather than jumping, so a rise sounds like one sound.
            val hz = fromHz + (toHz - fromHz) * t
            phase += 2 * PI * hz / SampleRate
            // Raised cosine in, exponential out: no click at either end.
            val envelope = if (i < attack) {
                0.5 - 0.5 * kotlin.math.cos(PI * i / attack)
            } else {
                exp(-decay * (i - attack).toDouble() / frames)
            }
            val value = sin(phase) * envelope * gain * volume
            out[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        return out
    }

    companion object {
        const val SampleRate = 44_100
    }
}

/**
 * Plays the sounds, or does nothing at all when they are off.
 *
 * One [AudioTrack] per sound, filled once and replayed from the start; a new tick cuts off the
 * one before it, which is what a run of ticks should sound like. Everything is built lazily on
 * first use, so a launcher with sound off never allocates an audio buffer.
 */
@Stable
class SoundPlayer {

    private val tracks = HashMap<UiSound, AudioTrack>()
    private var enabled = false
    private var volume = 0.4f

    fun configure(enabled: Boolean, volume: Float) {
        val volumeChanged = volume != this.volume
        this.enabled = enabled
        this.volume = volume.coerceIn(0f, 1f)
        // The samples carry the volume, so changing it means re-rendering them.
        if (volumeChanged || !enabled) release()
    }

    fun play(sound: UiSound) {
        if (!enabled || volume <= 0f) return
        val track = tracks.getOrPut(sound) { build(sound) ?: return }
        try {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Could not play $sound", e)
        }
    }

    fun release() {
        tracks.values.forEach { track ->
            runCatching {
                track.stop()
                track.release()
            }
        }
        tracks.clear()
    }

    private fun build(sound: UiSound): AudioTrack? = try {
        val samples = sound.render(volume)
        val bytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Sonification is exactly what this is: a sound that marks an action.
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(UiSound.SampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "No audio track for $sound", e)
        null
    } catch (e: UnsupportedOperationException) {
        Log.w(TAG, "Audio unavailable", e)
        null
    }

    private companion object {
        const val TAG = "SoundPlayer"
    }
}

/**
 * Haptics and sound in one call.
 *
 * Every control in Nulis already speaks the haptic vocabulary; wrapping the platform's
 * [HapticFeedback] means the sound follows it exactly, with the same role at the same moment,
 * and not one call site has to know sound exists.
 */
class HapticsWithSound(
    private val delegate: HapticFeedback,
    private val player: SoundPlayer,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        delegate.performHapticFeedback(hapticFeedbackType)
        soundFor(hapticFeedbackType)?.let(player::play)
    }
}

/** The sound that goes with a haptic role. Unknown roles stay silent rather than guessing. */
fun soundFor(type: HapticFeedbackType): UiSound? = when (type) {
    NulisHaptics.tick -> UiSound.TICK
    NulisHaptics.frequentTick -> UiSound.FREQUENT_TICK
    NulisHaptics.pickup -> UiSound.PICKUP
    NulisHaptics.drop -> UiSound.DROP
    NulisHaptics.threshold -> UiSound.THRESHOLD
    NulisHaptics.confirm -> UiSound.CONFIRM
    NulisHaptics.toggleOn -> UiSound.TOGGLE_ON
    NulisHaptics.toggleOff -> UiSound.TOGGLE_OFF
    else -> null
}

/** The player for this process, so a screen can make a sound without one being passed down. */
val LocalSoundPlayer = staticCompositionLocalOf { SoundPlayer() }

/** Unused, but kept so a future non-haptic sound has somewhere obvious to go. */
@Suppress("unused")
val AudioManagerStreams = AudioManager.STREAM_SYSTEM
