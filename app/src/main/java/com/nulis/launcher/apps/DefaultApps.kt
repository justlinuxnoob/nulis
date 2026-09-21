// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri

/**
 * The six apps a phone is mostly picked up for. A fresh install puts these on the home page so
 * the first screen is usable before anyone has chosen anything.
 */
enum class DefaultAppRole(val label: String) {
    PHONE("Phone"),
    MESSAGES("Messages"),
    CAMERA("Camera"),
    BROWSER("Browser"),
    GALLERY("Photos"),
    CLOCK("Clock"),
}

private const val TAG = "DefaultApps"

/**
 * Asks the system which app owns each role, several ways per role, and returns the packages it
 * found in role order.
 *
 * Every probe is an ordinary intent and needs no permission: this only reads what the phone would
 * do anyway if the user tapped a phone number or a photo. A role nothing answers is simply left
 * out - a tablet with no dialer gets five apps, not a broken sixth - so the caller never has to
 * deal with a placeholder.
 */
fun defaultAppPackages(context: Context): List<String> {
    val packageManager = context.packageManager
    val found = LinkedHashSet<String>()
    DefaultAppRole.entries.forEach { role ->
        val packageName = probesFor(role).firstNotNullOfOrNull { resolve(packageManager, it) }
        if (packageName == null) {
            Log.d(TAG, "Nothing on this phone owns $role")
        } else {
            // A phone where one app is both the dialer and the messenger gets it once.
            found += packageName
        }
    }
    return found.toList()
}

/**
 * The ids of the default apps that are actually launchable here, in role order. Anything the
 * package manager will not open is dropped rather than drawn as a dead tile.
 */
fun defaultFavoriteIds(context: Context, apps: List<AppInfo>): List<String> =
    defaultAppPackages(context).mapNotNull { packageName ->
        apps.firstOrNull { it.packageName == packageName && !it.isWork && !it.isNulisSettings }?.id
    }

private fun probesFor(role: DefaultAppRole): List<Intent> = when (role) {
    DefaultAppRole.PHONE -> listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CONTACTS"),
    )
    DefaultAppRole.MESSAGES -> listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
    )
    DefaultAppRole.CAMERA -> listOf(
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )
    DefaultAppRole.BROWSER -> listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
        Intent(Intent.ACTION_VIEW, "https://example.com".toUri()),
    )
    DefaultAppRole.GALLERY -> listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        Intent(Intent.ACTION_VIEW).setType("image/*"),
    )
    DefaultAppRole.CLOCK -> listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK"),
    )
}

/** The package that owns [intent], or null when nothing does or the system would ask. */
private fun resolve(packageManager: PackageManager, intent: Intent): String? {
    val name = runCatching {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull() ?: return null
    // "android" is the chooser, not an app.
    return name.takeIf { it != "android" }
}
