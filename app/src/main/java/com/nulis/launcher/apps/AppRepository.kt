// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * Loads the list of launchable apps from the system. Fully offline, on-device only.
 * Kept separate from the UI so screens only depend on [AppInfo].
 */
class AppRepository(
    private val context: android.content.Context,
    private val packageManager: PackageManager,
    private val ownPackageName: String,
    /** Label of the launcher's own settings row, listed in place of the launcher's activity. */
    private val settingsLabel: String,
) {

    /**
     * Returns every app with a MAIN/LAUNCHER activity, sorted alphabetically by label. The
     * launcher's own activity is replaced by a settings row, so settings can always be reached
     * from the drawer whatever the gestures are mapped to.
     */
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val collator = Collator.getInstance()
        val settingsRow = AppInfo(settingsLabel, ownPackageName, AppInfo.NULIS_SETTINGS)
        (resolved
            .asSequence()
            .filter { it.activityInfo.packageName != ownPackageName }
            .map {
                AppInfo(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    versionTag = versionTag(it.activityInfo.applicationInfo),
                )
            }
            .distinctBy { it.id }
            .toList() + settingsRow + WorkProfiles.load(context))
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** The name the app ships with, so renaming back to it clears the override instead of storing it. */
    fun originalLabel(app: AppInfo): String = if (app.isNulisSettings) settingsLabel else runCatching {
        packageManager.getActivityInfo(ComponentName(app.packageName, app.activityName), 0)
            .loadLabel(packageManager).toString()
    }.getOrNull() ?: app.label

    /**
     * The path of the installed APK. Android gives every install its own random directory, so
     * this changes on every update of a normal app - and it costs nothing, unlike asking the
     * package manager for a version code per app. A system app updated in place keeps its path;
     * its cached icons are dropped by the cache's own expiry instead.
     */
    private fun versionTag(info: ApplicationInfo): String = info.sourceDir.hashCode().toString(16)
}
