// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.ui.sound.UiSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The sounds are arithmetic, so they can be checked like arithmetic: the right length, no click
 * at either end, actually audible, and quiet enough to live with.
 */
class SoundTest {

    @Test
    fun `every sound is the length it says it is`() {
        UiSound.entries.forEach { sound ->
            val expected = UiSound.SampleRate * sound.millis / 1000
            assertEquals(sound.name, expected, sound.render(1f).size)
        }
    }

    @Test
    fun `no sound starts or ends with a click`() {
        UiSound.entries.forEach { sound ->
            val samples = sound.render(1f)
            // A click is a jump from silence; both ends must be near zero.
            assertTrue("${sound.name} starts at ${samples.first()}", abs(samples.first().toInt()) < 400)
            assertTrue("${sound.name} ends at ${samples.last()}", abs(samples.last().toInt()) < 1_200)
        }
    }

    @Test
    fun `every sound is actually audible`() {
        UiSound.entries.forEach { sound ->
            val peak = sound.render(1f).maxOf { abs(it.toInt()) }
            assertTrue("${sound.name} peaks at $peak", peak > 3_000)
        }
    }

    @Test
    fun `no sound is loud enough to be rude`() {
        UiSound.entries.forEach { sound ->
            val peak = sound.render(1f).maxOf { abs(it.toInt()) }
            assertTrue("${sound.name} peaks at $peak", peak < 18_000)
        }
    }

    @Test
    fun `volume scales the samples and zero is silence`() {
        val loud = UiSound.TICK.render(1f).maxOf { abs(it.toInt()) }
        val quiet = UiSound.TICK.render(0.5f).maxOf { abs(it.toInt()) }
        assertTrue(quiet < loud)
        assertTrue(abs(quiet * 2 - loud) < loud / 8)
        assertEquals(0, UiSound.TICK.render(0f).maxOf { abs(it.toInt()) })
    }

    @Test
    fun `the ticks are the shortest things in the set`() {
        val longest = UiSound.entries.maxOf { it.millis }
        assertTrue(UiSound.TICK.millis < longest)
        assertTrue(UiSound.FREQUENT_TICK.millis <= UiSound.TICK.millis)
        // Nothing is long enough to get in the way of the next tap.
        assertTrue(longest <= 120)
    }

    @Test
    fun `a sound decays rather than stopping flat`() {
        UiSound.entries.forEach { sound ->
            val samples = sound.render(1f)
            val peak = samples.maxOf { abs(it.toInt()) }
            val tail = samples.takeLast(samples.size / 10).maxOf { abs(it.toInt()) }
            assertTrue("${sound.name} does not fade", tail < peak / 2)
        }
    }
}
