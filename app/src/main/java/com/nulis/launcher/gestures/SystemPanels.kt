// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import android.content.Context

/**
 * Pulls down the notification shade or the quick settings panel.
 *
 * There is no public API for this: the system's own launcher talks to SystemUI directly, and the
 * only sanctioned alternative is an Accessibility Service, which this build deliberately does not
 * have. `StatusBarManager` exposes both panels as unsupported-but-reachable methods, so we call
 * them by name and treat any failure as "this phone will not do it" rather than crashing.
 */
fun expandNotifications(context: Context): Boolean = callStatusBar(context, "expandNotificationsPanel")

fun expandQuickSettings(context: Context): Boolean = callStatusBar(context, "expandSettingsPanel")

private fun callStatusBar(context: Context, method: String): Boolean = runCatching {
    val service = context.getSystemService("statusbar") ?: return false
    Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
    true
}.getOrDefault(false)
