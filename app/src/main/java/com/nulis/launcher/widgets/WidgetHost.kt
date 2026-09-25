// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one [AppWidgetHost] Nulis has.
 *
 * A widget is somebody else's view running inside this process, and the host is what the system
 * talks to when that view needs updating. It must be listening while the launcher is on screen
 * and quiet when it is not, or every widget on every page keeps a broadcast receiver alive behind
 * a screen nobody is looking at.
 *
 * [HOST_ID] is written into the system's own records the first time a widget is bound, so it must
 * never change: a new number would orphan every widget anybody had already placed, and orphaned
 * ids are not reclaimed by anything.
 */
class WidgetHost(context: Context) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private var listening = false

    fun startListening() {
        if (listening) return
        // Throws on some builds when the host has never bound anything; that is not a failure.
        runCatching { host.startListening() }
        listening = true
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    /** A fresh id to bind a widget to. Free it again with [forget] if the binding falls through. */
    fun allocateId(): Int = host.allocateAppWidgetId()

    /**
     * Gives an id back to the system. Called when a widget block is deleted, when the picker is
     * cancelled, and when a saved id turns out to name a widget that is no longer installed -
     * anything else leaks an id that nothing will ever reclaim.
     */
    fun forget(widgetId: Int) {
        if (widgetId == INVALID) return
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    /**
     * Every id this host still holds, or null when the system will not say.
     *
     * Null is not "none". A caller tidying up has to do nothing at all rather than read an
     * unanswered question as an empty list, because the difference between the two is every
     * widget on the phone.
     */
    fun knownIds(): Set<Int>? = runCatching { host.appWidgetIds.toSet() }.getOrNull()

    /**
     * Hands back every id this host holds that [live] does not mention, and answers how many.
     *
     * Deleting a widget block cannot release its id on the spot, because Undo puts that block
     * straight back and an id is not handed back the same way it was given: the widget would
     * return as an empty rectangle asking to be picked again. So nothing is released at the
     * moment of deleting. Instead everything that can still name an id - a saved page, a saved
     * setup, the editor's undo stack - is counted up once the editor is gone, and whatever the
     * host is holding beyond that is what nothing can reach any more.
     *
     * A [live] set that is short by even one page would throw away a widget somebody is looking
     * at, so the caller builds it from the store or does not call at all.
     */
    fun releaseUnreferenced(live: Set<Int>): Int {
        // An id half-way through the picker or its setup screen is on no page yet, and is the one
        // thing this must not hand back.
        val stale = staleIds(knownIds() ?: return 0, live)
        stale.forEach { forget(it) }
        return stale.size
    }

    /** Of the ids the host holds, the ones nothing can reach: not on a page, not in flight. */
    fun staleIds(known: Set<Int>, live: Set<Int>): Set<Int> = known - live - inFlight

    /** What is bound to [widgetId], or null when nothing is - an uninstalled app, a stale id. */
    fun providerFor(widgetId: Int): AppWidgetProviderInfo? =
        if (widgetId == INVALID) null else runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()

    /** The hosted view for a bound widget, or null when it cannot be made. */
    fun createView(context: Context, widgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView? =
        runCatching { host.createView(context, widgetId, info) }.getOrNull()

    /**
     * Tells the widget how much room it has, in dp, so a provider that ships several layouts
     * picks the right one. Android 12 replaced the four-number call with a list of sizes; both
     * are given the same single rectangle, because a block is one rectangle.
     */
    fun setSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val w = widthDp.coerceAtLeast(1)
        val h = heightDp.coerceAtLeast(1)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle.EMPTY, listOf(android.util.SizeF(w.toFloat(), h.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(Bundle.EMPTY, w, h, w, h)
            }
        }
    }

    /**
     * The system's own widget picker, which also does the binding.
     *
     * Nulis cannot bind a widget by itself: `BIND_APPWIDGET` is a permission only a system
     * launcher holds, and asking for it in the manifest of an app installed from a store gets
     * nobody anywhere. The picker is the supported route - the user chooses, and the system binds
     * on their say-so. [allocateId] first, because the picker needs an id to bind to.
     */
    fun pickIntent(widgetId: Int) = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // No custom entries: Nulis has no shortcuts of its own to offer here.
        .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
        .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())

    /**
     * Ids between being allocated and landing on a page: in the system picker, or in the
     * provider's setup screen. Kept in the activity's saved state, because Android is free to
     * end this process while somebody is in either, and an id the sweep did not know about would
     * be handed back underneath a widget that is about to be placed.
     */
    private val inFlight = mutableSetOf<Int>()

    fun hold(widgetId: Int) {
        if (widgetId != INVALID) inFlight += widgetId
    }

    fun release(widgetId: Int) {
        inFlight -= widgetId
    }

    /** A widget waiting on its own setup screen, and the block it is for. */
    data class PendingSetup(
        val widgetId: Int,
        val blockId: String,
        /** The widget this one replaces, released once the new one is in; [INVALID] for none. */
        val previousId: Int,
        /** Changing the settings of a widget already on the page, rather than placing one. */
        val reconfigure: Boolean,
    )

    /** What came of a setup screen: the widget goes on the page, or it does not. */
    data class SetupOutcome(val pending: PendingSetup, val placed: Boolean)

    private var pendingSetup: PendingSetup? = null
    private val outcomes = MutableStateFlow<SetupOutcome?>(null)

    /** The last setup screen's outcome until somebody [consume]s it; survives a restart. */
    val setupOutcome: StateFlow<SetupOutcome?> = outcomes.asStateFlow()

    fun consume(outcome: SetupOutcome) {
        if (outcomes.value == outcome) outcomes.value = null
        if (!outcome.pending.reconfigure) release(outcome.pending.widgetId)
    }

    /**
     * True when [info] asks to be set up before it is placed.
     *
     * Android 12 lets a provider say its setup screen is optional; those are placed with their
     * defaults straight away, the way every other launcher does, and can be set up later from
     * the block's options.
     */
    fun needsSetup(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        ) {
            return false
        }
        return true
    }

    /** True when the widget on the page can be set up again, and says so. */
    fun canReconfigure(info: AppWidgetProviderInfo?): Boolean =
        info?.configure != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    /**
     * Opens the provider's own setup screen through the host, which is the one way that works
     * for every widget: a setup activity that is not exported, or one that belongs to a work
     * profile, cannot be started by an ordinary intent from Nulis, and used to fall through to
     * placing the widget unconfigured. The answer comes back to [MainActivity] and from there to
     * [onSetupResult]. False when the setup screen could not be opened at all.
     */
    fun startSetup(activity: Activity, pending: PendingSetup): Boolean {
        pendingSetup = pending
        hold(pending.widgetId)
        return runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, pending.widgetId, 0, REQUEST_CONFIGURE, null)
            true
        }.getOrElse {
            pendingSetup = null
            if (!pending.reconfigure) release(pending.widgetId)
            false
        }
    }

    /** The setup screen has answered. Cancelled means the widget is not wanted. */
    fun onSetupResult(resultCode: Int) {
        val pending = pendingSetup ?: return
        pendingSetup = null
        outcomes.value = SetupOutcome(pending, placed = resultCode == Activity.RESULT_OK)
    }

    fun saveState(out: Bundle) {
        out.putIntArray(STATE_IN_FLIGHT, inFlight.toIntArray())
        pendingSetup?.let {
            out.putIntArray(STATE_SETUP_IDS, intArrayOf(it.widgetId, it.previousId, if (it.reconfigure) 1 else 0))
            out.putString(STATE_SETUP_BLOCK, it.blockId)
        }
    }

    fun restoreState(state: Bundle?) {
        state ?: return
        state.getIntArray(STATE_IN_FLIGHT)?.forEach { inFlight += it }
        val ids = state.getIntArray(STATE_SETUP_IDS)
        val block = state.getString(STATE_SETUP_BLOCK)
        if (ids != null && ids.size == 3 && block != null) {
            pendingSetup = PendingSetup(ids[0], block, ids[1], reconfigure = ids[2] == 1)
        }
    }

    companion object {
        /** Written into the system's records. Changing it orphans every widget already placed. */
        const val HOST_ID = 0x4E55 // "NU"

        const val INVALID = AppWidgetManager.INVALID_APPWIDGET_ID
        const val REQUEST_CONFIGURE = 0x4E01

        private const val STATE_IN_FLIGHT = "nulis.widgets.inFlight"
        private const val STATE_SETUP_IDS = "nulis.widgets.setupIds"
        private const val STATE_SETUP_BLOCK = "nulis.widgets.setupBlock"
    }
}

/** The host, for the one block that needs it. Absent in previews and in unit tests. */
val LocalWidgetHost = staticCompositionLocalOf<WidgetHost?> { null }
