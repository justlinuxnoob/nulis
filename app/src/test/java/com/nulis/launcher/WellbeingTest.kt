// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.wellbeing.PauseReason
import com.nulis.launcher.wellbeing.Wellbeing
import com.nulis.launcher.wellbeing.isDimmedByFocus
import com.nulis.launcher.wellbeing.pauseDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingTest {

    private val plain = Wellbeing()

    @Test
    fun `nothing pauses by default`() {
        assertNull(pauseDecision("com.example", plain, minutesToday = 300, focusRunning = false).reason)
    }

    @Test
    fun `an app the user chose pauses`() {
        val settings = plain.copy(pausedPackages = setOf("com.example"))
        assertEquals(PauseReason.CHOSEN, pauseDecision("com.example", settings, 0, false).reason)
        assertNull(pauseDecision("com.other", settings, 0, false).reason)
    }

    @Test
    fun `a limit only bites once it is reached`() {
        val settings = plain.copy(limits = mapOf("com.example" to 30))
        assertNull(pauseDecision("com.example", settings, 29, false).reason)
        assertEquals(PauseReason.OVER_LIMIT, pauseDecision("com.example", settings, 30, false).reason)
        assertEquals(PauseReason.OVER_LIMIT, pauseDecision("com.example", settings, 120, false).reason)
    }

    @Test
    fun `the decision carries the numbers the screen needs to explain itself`() {
        val settings = plain.copy(limits = mapOf("com.example" to 30))
        val decision = pauseDecision("com.example", settings, 42, false)
        assertEquals(42, decision.minutesToday)
        assertEquals(30, decision.limit)
    }

    @Test
    fun `a focus session outranks everything`() {
        val settings = plain.copy(limits = mapOf("com.example" to 30), pausedPackages = setOf("com.example"))
        assertEquals(PauseReason.FOCUS, pauseDecision("com.example", settings, 120, focusRunning = true).reason)
    }

    @Test
    fun `an allowed app goes straight through a focus session`() {
        val settings = plain.copy(focusAllowed = setOf("com.example"))
        assertNull(pauseDecision("com.example", settings, 0, focusRunning = true).reason)
    }

    @Test
    fun `an allowed app still pauses for its own reasons`() {
        val settings = plain.copy(focusAllowed = setOf("com.example"), pausedPackages = setOf("com.example"))
        assertEquals(PauseReason.CHOSEN, pauseDecision("com.example", settings, 0, focusRunning = true).reason)
    }

    @Test
    fun `turning the focus guard off lets everything through`() {
        val settings = plain.copy(focusGuards = false)
        assertNull(pauseDecision("com.example", settings, 0, focusRunning = true).reason)
        assertFalse(isDimmedByFocus("com.example", settings, focusRunning = true))
    }

    @Test
    fun `apps fade only while a session is actually running`() {
        assertFalse(isDimmedByFocus("com.example", plain, focusRunning = false))
        assertTrue(isDimmedByFocus("com.example", plain, focusRunning = true))
        assertFalse(isDimmedByFocus("com.example", plain.copy(focusAllowed = setOf("com.example")), focusRunning = true))
    }
}
