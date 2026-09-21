// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * Starts the given app in its own task. When [origin] (the tapped item's bounds in window
 * coordinates) is known, the app reveals outward from that spot instead of the stock transition.
 * Silently ignores apps that can no longer be launched.
 */
fun launchApp(activity: Activity, app: AppInfo, origin: Rect? = null) {
    val options = revealOptions(activity, origin)
    // An app in another profile cannot be started with a plain intent; if that path fails we
    // fall through to the ordinary one rather than doing nothing.
    if (app.isWork && WorkProfiles.launch(activity, app, origin?.toAndroidRect(), options)) return
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.packageName, app.activityName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    startSafely(activity, intent, options)
}

private fun Rect.toAndroidRect(): android.graphics.Rect =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

private fun revealOptions(activity: Activity, origin: Rect?): Bundle? {
    if (origin == null || origin.isEmpty) return null
    return ActivityOptions.makeClipRevealAnimation(
        activity.window.decorView,
        origin.left.roundToInt(),
        origin.top.roundToInt(),
        origin.width.roundToInt(),
        origin.height.roundToInt(),
    ).toBundle()
}

/** Opens the system "App info" settings page for the app. */
fun openAppInfo(context: Context, app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(app))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startSafely(context, intent)
}

/** Asks the system to uninstall the app. The system shows its own confirmation dialog. */
fun requestUninstall(context: Context, app: AppInfo) {
    val intent = Intent(Intent.ACTION_DELETE, packageUri(app))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startSafely(context, intent)
}

private fun packageUri(app: AppInfo): Uri = Uri.fromParts("package", app.packageName, null)

private fun startSafely(context: Context, intent: Intent, options: Bundle? = null) {
    try {
        context.startActivity(intent, options)
    } catch (e: ActivityNotFoundException) {
        // Target no longer exists; the app list reloads on the next resume.
    } catch (e: SecurityException) {
        // Not launchable by us; nothing sensible to do.
    }
}
