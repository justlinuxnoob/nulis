// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.widgets

import android.app.Activity
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A widget waiting on the picker or its setup screen survives a restart and the id sweep. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WidgetSetupTest {

    private fun host() = WidgetHost(ApplicationProvider.getApplicationContext())

    @Test
    fun an_id_in_flight_is_never_swept() {
        val host = host()
        host.hold(3)
        assertEquals(setOf(9), host.staleIds(known = setOf(1, 3, 9), live = setOf(1)))
        host.release(3)
        assertEquals(setOf(3, 9), host.staleIds(known = setOf(1, 3, 9), live = setOf(1)))
    }

    @Test
    fun a_pending_setup_survives_the_process_and_reports_its_outcome() {
        val before = host()
        val pending = WidgetHost.PendingSetup(widgetId = 42, blockId = "block-1", previousId = 7, reconfigure = false)
        // Starting needs an activity; what matters here is what is remembered, so record it the
        // way startSetup does and then lose the process.
        before.javaClass.getDeclaredField("pendingSetup").apply { isAccessible = true }.set(before, pending)
        before.hold(42)
        val state = Bundle().also(before::saveState)

        val after = host()
        after.restoreState(state)
        after.onSetupResult(Activity.RESULT_OK)
        val outcome = after.setupOutcome.value
        assertEquals(WidgetHost.SetupOutcome(pending, placed = true), outcome)

        after.consume(outcome!!)
        assertNull(after.setupOutcome.value)
    }

    @Test
    fun a_cancelled_setup_is_not_placed() {
        val host = host()
        val pending = WidgetHost.PendingSetup(5, "b", WidgetHost.INVALID, reconfigure = false)
        host.javaClass.getDeclaredField("pendingSetup").apply { isAccessible = true }.set(host, pending)
        host.onSetupResult(Activity.RESULT_CANCELED)
        assertEquals(false, host.setupOutcome.value?.placed)
    }
}
