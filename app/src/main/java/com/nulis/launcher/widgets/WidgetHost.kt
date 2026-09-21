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

    /** The provider's own setup screen, for the widgets that have one. Null when it has none. */
    fun configureIntent(activity: Activity, widgetId: Int, info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        return runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, REQUEST_CONFIGURE, null)
            true
        }.getOrDefault(false)
    }

    companion object {
        /** Written into the system's records. Changing it orphans every widget already placed. */
        const val HOST_ID = 0x4E55 // "NU"

        const val INVALID = AppWidgetManager.INVALID_APPWIDGET_ID
        const val REQUEST_CONFIGURE = 0x4E01
    }
}

/** The host, for the one block that needs it. Absent in previews and in unit tests. */
val LocalWidgetHost = staticCompositionLocalOf<WidgetHost?> { null }
