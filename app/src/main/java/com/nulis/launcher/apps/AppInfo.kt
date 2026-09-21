// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

/** A launchable app entry as shown in the app list. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    /**
     * Changes whenever the app is updated, so a cached icon bitmap is never shown for a version
     * that no longer exists. Free to read: it comes off the same query as the label.
     */
    val versionTag: String = "",
    /**
     * The user this app belongs to. 0 is the phone's own profile, which is every app on a phone
     * with no work profile; anything else is a work (or private) profile and is launched through
     * [android.content.pm.LauncherApps] rather than a plain intent.
     */
    val userSerial: Long = 0L,
) {
    /** True for an app that belongs to a work or private profile rather than the main one. */
    val isWork: Boolean get() = userSerial != 0L

    /** Stable identity for list keys. */
    val id: String get() = if (userSerial == 0L) "$packageName/$activityName" else "$packageName/$activityName@$userSerial"

    /**
     * The launcher's own row in its own drawer. It is not a real activity: tapping it opens the
     * settings overlay. It exists so no gesture remapping can ever lock the user out of settings.
     */
    val isNulisSettings: Boolean get() = activityName == NULIS_SETTINGS

    companion object {
        /** Sentinel activity name; no real component ever has it. */
        const val NULIS_SETTINGS = "nulis.settings"
    }
}
