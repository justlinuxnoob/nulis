// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.screentime.ScreenEvent
import com.nulis.launcher.blocks.screentime.ScreenEvent.Kind.LOCKED
import com.nulis.launcher.blocks.screentime.ScreenEvent.Kind.SCREEN_OFF
import com.nulis.launcher.blocks.screentime.ScreenEvent.Kind.SCREEN_ON
import com.nulis.launcher.blocks.screentime.ScreenEvent.Kind.UNLOCKED
import com.nulis.launcher.blocks.screentime.screenOnMillis
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOnTest {

    private val min = 60_000L
    private fun e(kind: ScreenEvent.Kind, minute: Long) = ScreenEvent(kind, minute * min)

    @Test
    fun counts_unlocked_time_only() {
        val events = listOf(
            e(SCREEN_ON, 10), e(UNLOCKED, 11), e(LOCKED, 30), e(SCREEN_OFF, 30),
            e(SCREEN_ON, 40), e(SCREEN_OFF, 42), // looked at the lock screen, never unlocked
        )
        assertEquals(19 * min, screenOnMillis(events, 0, 100 * min))
    }

    @Test
    fun a_phone_with_no_lock_counts_from_screen_on() {
        val events = listOf(e(SCREEN_ON, 10), e(SCREEN_OFF, 25))
        assertEquals(15 * min, screenOnMillis(events, 0, 100 * min))
    }

    @Test
    fun a_session_across_midnight_counts_only_after_it() {
        val events = listOf(e(SCREEN_ON, -20), e(UNLOCKED, -19), e(SCREEN_OFF, 5))
        assertEquals(5 * min, screenOnMillis(events, 0, 100 * min))
    }

    @Test
    fun still_on_counts_up_to_now() {
        val events = listOf(e(SCREEN_ON, 90), e(UNLOCKED, 91))
        assertEquals(9 * min, screenOnMillis(events, 0, 100 * min))
    }

    @Test
    fun screen_off_while_unlocked_then_back_on_counts_both_stretches() {
        // Some phones do not report the lock screen on a quick screen-off; the next unlock is.
        val events = listOf(e(SCREEN_ON, 0), e(UNLOCKED, 0), e(SCREEN_OFF, 10), e(SCREEN_ON, 20), e(SCREEN_OFF, 25))
        assertEquals(15 * min, screenOnMillis(events, 0, 100 * min))
    }
}
