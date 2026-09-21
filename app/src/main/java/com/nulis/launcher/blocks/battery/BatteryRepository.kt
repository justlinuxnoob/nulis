// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Immutable
data class BatteryState(val percent: Int = 100, val charging: Boolean = false)

/** Battery level and charging state from the system's sticky battery broadcast. No permission needed. */
class BatteryRepository(private val context: Context) {

    val state: Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent.toState())
            }
        }
        // Registering for the sticky broadcast returns the current state at once.
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { trySend(it.toState()) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun Intent.toState(): BatteryState {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return BatteryState(percent = percent.coerceIn(0, 100), charging = plugged)
    }
}
