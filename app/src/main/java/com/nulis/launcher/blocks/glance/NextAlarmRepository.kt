// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.glance

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The next alarm the phone knows about, or nothing set. */
data class AlarmState(val at: LocalDateTime? = null)

/**
 * The next alarm clock, from whichever app set it.
 *
 * `getNextAlarmClock` needs no permission and gives nothing away about the app that set it - only
 * when it goes off - which is why the Glance block can show it and why Nulis never asks for the
 * right to set alarms itself. The system broadcasts when it changes, so nothing is polled.
 */
class NextAlarmRepository(private val context: Context) {

    val state: Flow<AlarmState> = callbackFlow {
        val alarms = context.getSystemService<AlarmManager>()
        fun publish() {
            val next = alarms?.nextAlarmClock?.triggerTime
            trySend(AlarmState(next?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = publish()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        publish()
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
