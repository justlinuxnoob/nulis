// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.steps

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One read of the step counter just before midnight, so a day's steps are filed on that day even
 * when the launcher has not been opened since the morning.
 *
 * Deliberately not a service: a single inexact alarm wakes the app, takes one sensor reading,
 * hands it to [StepsRepository] and stops. It needs no new permission. Alarms do not survive a
 * reboot, so [schedule] runs again on every resume of the launcher, which for a home screen is
 * within moments of the phone coming back up.
 */
object StepsAlarm {

    /** Late enough that almost every step of the day is in, early enough to land before midnight. */
    private val ReadAt: LocalTime = LocalTime.of(23, 55)

    private const val TAG = "StepsAlarm"
    private const val REQUEST_CODE = 1
    private const val ACTION = "com.nulis.launcher.steps.DAILY_READ"

    /** (Re)schedules the next read. Cheap and idempotent: the pending intent is always replaced. */
    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt(), pendingIntent(context))
        }.onFailure { Log.w(TAG, "Could not schedule the nightly step read", it) }
    }

    private fun nextTriggerAt(): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).atTime(ReadAt)
        val next = if (today.isAfter(java.time.LocalDateTime.now(zone))) today else today.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, StepsAlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * Wakes for a few seconds, folds one counter reading into today's total and schedules tomorrow's
 * read. If the sensor says nothing in time (no permission, no sensor, a device that holds sensor
 * events back while idle) nothing is written and the count behaves exactly as it did before.
 */
class StepsAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        StepsAlarm.schedule(app)
        val repository = StepsRepository(app)
        if (!repository.hasPermission()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(READ_TIMEOUT_MS) { repository.counterReadings().first() }
                    ?.let { repository.onCounter(it) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Well inside the ten seconds a broadcast receiver is given. */
        const val READ_TIMEOUT_MS = 8_000L
    }
}
