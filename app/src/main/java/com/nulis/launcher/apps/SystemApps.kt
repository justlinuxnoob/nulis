// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log

/** A system surface a block can send the user to. No permission: these are ordinary intents. */
enum class SystemApp { CLOCK, CALENDAR }

private const val TAG = "SystemApps"

/**
 * Opens the phone's own clock or calendar.
 *
 * Deliberately permission-free. `ACTION_SHOW_ALARMS` would land on the alarm list directly, but
 * the system requires `SET_ALARM` to send it, and a launcher has no business holding the right
 * to set alarms. So the clock is found by asking who handles that intent and then opening that
 * app at its own front door, which is where a tap on a clock should go anyway. The calendar's
 * "show me this instant" intent needs nothing, so it is tried first and only falls back the
 * same way.
 */
fun openSystemApp(context: Context, target: SystemApp) {
    val packageManager = context.packageManager
    val direct = when (target) {
        SystemApp.CLOCK -> emptyList()
        SystemApp.CALENDAR -> listOf(
            Intent(Intent.ACTION_VIEW).setData(
                CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .appendPath(System.currentTimeMillis().toString())
                    .build(),
            ),
            Intent(Intent.ACTION_VIEW).setData("content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()),
        )
    }
    // 1. A direct intent that one app already owns: today's date, opened in the calendar.
    for (intent in direct) {
        if (owner(packageManager, intent) != null && start(context, intent)) return
    }
    // 2. Otherwise the app that handles this kind of thing, opened at its own front door.
    val probes = when (target) {
        SystemApp.CLOCK -> listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK"),
        )
        SystemApp.CALENDAR -> listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
    }
    for (probe in probes) {
        val packageName = owner(packageManager, probe) ?: continue
        val launch = runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull() ?: continue
        if (start(context, launch)) return
    }
    // 3. Last resort: send it anyway and let the system ask which app to use.
    for (intent in direct) {
        if (start(context, intent)) return
    }
    Log.d(TAG, "Nothing on this phone handles $target")
}

/**
 * The single app that owns [intent], or null when nothing does or when the system would put up
 * a chooser. A launcher should not answer a tap with a dialogue if it can help it.
 */
private fun owner(packageManager: PackageManager, intent: Intent): String? {
    val name = runCatching {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull() ?: return null
    return name.takeIf { it != "android" }
}

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Log.d(TAG, "No handler for $intent", e)
    false
} catch (e: SecurityException) {
    Log.d(TAG, "Not allowed to start $intent", e)
    false
}
