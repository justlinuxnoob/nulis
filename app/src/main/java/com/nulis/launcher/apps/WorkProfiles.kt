// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/**
 * Work (and, on newer Androids, private) profile support.
 *
 * Deliberately additive: the main profile's apps still come from the package manager, on the
 * path that has been working all along, and everything here only ever *adds* rows. A phone with
 * no second profile - which is most phones, and is this one - behaves exactly as before, and a
 * device that refuses any of these calls ends up with an empty list rather than a broken drawer.
 */
object WorkProfiles {

    /** Every launchable app belonging to a profile that is not the main one. */
    fun load(context: Context): List<AppInfo> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return emptyList()
        val own = Process.myUserHandle()
        return try {
            launcherApps.profiles
                .filter { it != own }
                .flatMap { profile ->
                    val serial = userManager.getSerialNumberForUser(profile)
                    launcherApps.getActivityList(null, profile).map { activity ->
                        AppInfo(
                            label = activity.label.toString(),
                            packageName = activity.componentName.packageName,
                            activityName = activity.componentName.className,
                            versionTag = activity.applicationInfo.sourceDir.hashCode().toString(16),
                            userSerial = if (serial == -1L) 1L else serial,
                        )
                    }
                }
        } catch (e: SecurityException) {
            Log.d(TAG, "Not allowed to list other profiles", e)
            emptyList()
        } catch (e: RuntimeException) {
            // Some OEM builds throw from getActivityList for a locked or quiet profile.
            Log.d(TAG, "Could not list other profiles", e)
            emptyList()
        }
    }

    /**
     * Launches an app that belongs to another profile. Returns false when it could not, so the
     * caller can fall back to the ordinary intent.
     */
    fun launch(activity: Activity, app: AppInfo, origin: Rect?, options: android.os.Bundle?): Boolean {
        if (!app.isWork) return false
        val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return false
        val userManager = activity.getSystemService(Context.USER_SERVICE) as? UserManager ?: return false
        val user: UserHandle = userManager.getUserForSerialNumber(app.userSerial) ?: return false
        return try {
            launcherApps.startMainActivity(
                ComponentName(app.packageName, app.activityName),
                user,
                origin,
                options,
            )
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "Not allowed to start ${app.id}", e)
            false
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Profile unavailable for ${app.id}", e)
            false
        }
    }

    /** True when this phone has a second profile at all, so the drawer can offer a tab. */
    fun hasOtherProfile(context: Context): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return false
        return try {
            launcherApps.profiles.any { it != Process.myUserHandle() }
        } catch (e: SecurityException) {
            false
        }
    }

    /** Whether the OS calls the second profile "work" or something else. Only used for a label. */
    fun label(context: Context): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) "Work" else "Work"

    private const val TAG = "WorkProfiles"
}
