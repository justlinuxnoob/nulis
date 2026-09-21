// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.apps

import android.os.Build
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** When each app was last opened, and which were opened most recently. */
@Immutable
data class AppUsage(
    val granted: Boolean = false,
    /** Package name -> last time it was in the foreground, in millis. */
    val lastUsed: Map<String, Long> = emptyMap(),
    /** Packages by how recently they were used, newest first. */
    val recentPackages: List<String> = emptyList(),
)

/**
 * Reads how long ago each app was last opened, which is what makes "hide apps I have not used in
 * N days" and a recents row possible. Same permission as the screen-time block - if it has not
 * been granted, both features simply stay off rather than asking for it out of nowhere.
 */
class AppUsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Usage access check refused", e)
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** A year of coarse buckets is enough to answer "when did I last open this". */
    suspend fun load(): AppUsage = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext AppUsage(granted = false)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext AppUsage(granted = false)
        val now = System.currentTimeMillis()
        val stats = try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - YearMillis, now)
        } catch (e: SecurityException) {
            Log.w(TAG, "Usage stats refused", e)
            return@withContext AppUsage(granted = false)
        } ?: emptyList()
        val lastUsed = HashMap<String, Long>(stats.size)
        stats.forEach { stat ->
            // Several buckets can cover the same package; the newest one wins.
            val previous = lastUsed[stat.packageName] ?: 0L
            if (stat.lastTimeUsed > previous) lastUsed[stat.packageName] = stat.lastTimeUsed
        }
        AppUsage(
            granted = true,
            lastUsed = lastUsed,
            recentPackages = lastUsed.entries.sortedByDescending { it.value }.map { it.key },
        )
    }

    private companion object {
        const val TAG = "AppUsageRepository"
        const val YearMillis = 365L * 24 * 60 * 60 * 1000
    }
}

/**
 * The apps to keep out of the drawer because they have not been opened in [days].
 *
 * An app with no record at all is left alone: no record usually means the stats do not go back
 * far enough, and hiding something for lack of evidence would be the wrong way round. So is a
 * favourite, an app in one of the user's own groups, and Nulis's own settings row.
 */
fun autoHiddenApps(
    apps: List<AppInfo>,
    usage: AppUsage,
    days: Int,
    now: Long = System.currentTimeMillis(),
    keepIds: Set<String> = emptySet(),
): Set<String> {
    if (days <= 0 || !usage.granted) return emptySet()
    val cutoff = now - days * 24L * 60 * 60 * 1000
    return apps.asSequence()
        .filterNot { it.isNulisSettings || it.id in keepIds }
        .filter { app ->
            val last = usage.lastUsed[app.packageName] ?: return@filter false
            last in 1 until cutoff
        }
        .map { it.id }
        .toSet()
}
