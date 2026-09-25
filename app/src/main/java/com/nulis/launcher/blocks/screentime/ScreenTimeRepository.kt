// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Foreground time for one entry in the list.
 *
 * [packageName] is empty for the single grouped row: everything that has foreground time but no
 * icon you could have tapped to get there - a permission dialog, a settings panel, an app whose
 * launcher entry is hidden. It is called **Other**, because it used to be called System and sat
 * near the top of the list at eleven minutes, which read as "your phone spent eleven minutes on
 * itself" when most of it was the notification shade and the launcher you were looking at.
 */
@Immutable
data class AppUsage(val packageName: String, val label: String, val minutes: Int) {
    val isOther: Boolean get() = packageName.isEmpty()

    companion object {
        const val OTHER = "Other"

        /** One line, shown when somebody taps the row to ask what it is. */
        const val OTHER_EXPLANATION =
            "Time in things you cannot open yourself: system dialogs, settings panels, and apps with no icon in the drawer."
    }
}

/** Today's foreground time. [granted] false means usage access has not been allowed yet. */
@Immutable
data class ScreenTimeState(
    val granted: Boolean = false,
    val totalMinutes: Int = 0,
    /** Every app with foreground time today, most used first. */
    val apps: List<AppUsage> = emptyList(),
    /**
     * How long the screen has been on and unlocked today, home screen and all: the number
     * Digital Wellbeing reports. Null where the phone does not record it (before Android 9).
     */
    val screenOnMinutes: Int? = null,
) {
    /** Per-package minutes for app lists; the grouped row has no package and is left out. */
    val minutesByPackage: Map<String, Int> get() = apps.filterNot { it.isOther }.associate { it.packageName to it.minutes }
}

/**
 * Reads today's per-app foreground time from usage events (resumed/paused pairs), which is far
 * more accurate than the aggregated daily buckets. Needs the special usage-access permission,
 * which only the user can grant in system settings.
 */
class ScreenTimeRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // unsafeCheckOpNoThrow only exists from Android 10; before that the old name is the one.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun load(): ScreenTimeState = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext ScreenTimeState(granted = false)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A few hours before midnight as well, and only so that whatever was already open - and
        // whether the screen was on - at the start of the day is known. Nothing before [start]
        // is ever counted.
        val events = manager.queryEvents(start - LOOKBACK_MILLIS, now)
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val screen = ArrayList<ScreenEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            screenEventOf(event.eventType, event.timeStamp)?.let(screen::add)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val from = (resumedAt.remove(pkg) ?: continue).coerceAtLeast(start)
                    total[pkg] = (total[pkg] ?: 0L) + (event.timeStamp - from).coerceAtLeast(0L)
                }
            }
        }
        // Whatever is still in front counts up to now.
        resumedAt.forEach { (pkg, from) -> total[pkg] = (total[pkg] ?: 0L) + (now - from.coerceAtLeast(start)).coerceAtLeast(0L) }
        excluded().forEach(total::remove)
        // Only apps you can actually open get their own row; everything left over is summed into
        // one "Other" row, never a package name.
        val launchable = launchablePackages()
        var otherMinutes = 0
        val named = ArrayList<AppUsage>(total.size)
        total.forEach { (pkg, millis) ->
            val minutes = (millis / 60_000L).toInt()
            if (minutes <= 0) return@forEach
            if (pkg in launchable) named += AppUsage(pkg, label(pkg), minutes) else otherMinutes += minutes
        }
        if (otherMinutes > 0) named += AppUsage("", AppUsage.OTHER, otherMinutes)
        val apps = named.sortedByDescending { it.minutes }
        val screenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (screenOnMillis(screen, start, now) / 60_000L).toInt() else null
        ScreenTimeState(granted = true, totalMinutes = apps.sumOf { it.minutes }, apps = apps, screenOnMinutes = screenOn)
    }

    /**
     * Seven days of totals, one number per day plus the apps that took the most of it. Uses the
     * daily buckets rather than the event stream: a week of events is a lot to walk through for
     * a summary screen, and the buckets are accurate enough for "where did the week go".
     */
    suspend fun loadWeek(days: Int = 7): WeeklyScreenTime = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext WeeklyScreenTime(granted = false)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val launchable = launchablePackages()
        val excluded = excluded()
        val perDay = ArrayList<DayMinutes>(days)
        val perApp = HashMap<String, Long>()
        for (offset in (days - 1) downTo 0) {
            val date = today.minusDays(offset.toLong())
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
            if (to <= from) {
                perDay += DayMinutes(date, 0)
                continue
            }
            val stats = runCatching { manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to) }.getOrNull().orEmpty()
            var dayMillis = 0L
            // A package can appear in several buckets for one day; they add up.
            val dayByPackage = HashMap<String, Long>()
            stats.forEach { stat ->
                if (stat.packageName in excluded) return@forEach
                if (stat.totalTimeInForeground <= 0) return@forEach
                dayByPackage[stat.packageName] = (dayByPackage[stat.packageName] ?: 0L) + stat.totalTimeInForeground
            }
            dayByPackage.forEach { (pkg, millis) ->
                dayMillis += millis
                if (pkg in launchable) perApp[pkg] = (perApp[pkg] ?: 0L) + millis
            }
            perDay += DayMinutes(date, (dayMillis / 60_000L).toInt())
        }
        val apps = perApp.entries
            .map { AppUsage(it.key, label(it.key), (it.value / 60_000L).toInt()) }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }
        WeeklyScreenTime(granted = true, days = perDay, apps = apps)
    }

    /**
     * What never counts as screen time, here or anywhere downstream of here.
     *
     * - **Nulis itself.** Looking at your own home screen is not using an app, and a launcher
     *   that put itself near the top of your own screen-time list would be absurd.
     * - **Every other home app**, for the same reason - including whichever launcher was the
     *   default before this one, which kept showing up as anonymous minutes.
     * - **The system UI**: the notification shade, quick settings and the recents switcher. They
     *   are drawn over whatever you were doing; they are not somewhere you went.
     */
    private fun excluded(): Set<String> = buildSet {
        add(context.packageName)
        addAll(SYSTEM_UI_PACKAGES)
        addAll(resolvePackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)))
    }

    private fun launchablePackages(): Set<String> {
        return resolvePackages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    }

    private fun resolvePackages(intent: Intent): Set<String> {
        val pm = context.packageManager
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrNull().orEmpty()
        return resolved.mapTo(HashSet()) { it.activityInfo.packageName }
    }

    /** The installed app's own label. Never a package name: an unreadable one joins the Other row. */
    private fun label(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString().takeIf { it.isNotBlank() }
    }.getOrNull() ?: AppUsage.OTHER

    /** The screen and lock-screen events Android 9 and up record among the app events. */
    private fun screenEventOf(type: Int, at: Long): ScreenEvent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val kind = when (type) {
            UsageEvents.Event.SCREEN_INTERACTIVE -> ScreenEvent.Kind.SCREEN_ON
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ScreenEvent.Kind.SCREEN_OFF
            UsageEvents.Event.KEYGUARD_SHOWN -> ScreenEvent.Kind.LOCKED
            UsageEvents.Event.KEYGUARD_HIDDEN -> ScreenEvent.Kind.UNLOCKED
            else -> return null
        }
        return ScreenEvent(kind, at)
    }

    private companion object {
        /** How far before midnight the event stream is read to learn how the day began. */
        const val LOOKBACK_MILLIS = 12 * 60 * 60 * 1000L

        /**
         * The shade, quick settings and recents. Named rather than resolved: there is no intent
         * that means "the system UI", and these are the package names every Android build uses.
         */
        val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.systemui.plugins",
        )
    }
}

/** One day's total, for the weekly summary's chart. */
@Immutable
data class DayMinutes(val date: LocalDate, val minutes: Int)

/** A week of screen time: a number per day and the apps that took the most of it. */
@Immutable
data class WeeklyScreenTime(
    val granted: Boolean = false,
    val days: List<DayMinutes> = emptyList(),
    val apps: List<AppUsage> = emptyList(),
) {
    val totalMinutes: Int get() = days.sumOf { it.minutes }
    val dailyAverage: Int get() = if (days.isEmpty()) 0 else totalMinutes / days.size
}

/** "2h 14m", "48m", "0m". */
fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
