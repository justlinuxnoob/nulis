// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.screentime

/**
 * One change in whether the screen is on and unlocked, from the usage event stream.
 *
 * Android records these alongside the app events (API 28 and up): the screen going interactive
 * and non-interactive, and the lock screen being shown and dismissed.
 */
data class ScreenEvent(val kind: Kind, val at: Long) {
    enum class Kind { SCREEN_ON, SCREEN_OFF, LOCKED, UNLOCKED }
}

/**
 * How long the screen was on and unlocked between [from] and [to].
 *
 * This is the number Digital Wellbeing's "screen time" is really answering: the time somebody
 * spent looking at their phone, including the home screen, the notification shade and the
 * switcher between apps - all the places the per-app total rightly leaves out, because nobody
 * went there on purpose. The two totals are different questions, so Nulis shows both.
 *
 * [events] must be in time order and may start well before [from]; the ones before it only
 * establish the state the day began in, because the stream says nothing at midnight itself.
 * A phone that reports its lock screen at all is assumed locked until the stream says it was
 * unlocked, so time spent looking at the lock screen is never counted; a phone with no lock
 * reports nothing of the kind, and for it the screen being on is enough.
 */
fun screenOnMillis(events: List<ScreenEvent>, from: Long, to: Long): Long {
    if (to <= from) return 0L
    var on = false
    var locked = events.any { it.kind == ScreenEvent.Kind.LOCKED || it.kind == ScreenEvent.Kind.UNLOCKED }
    var countingSince: Long? = null
    var total = 0L

    fun update(at: Long) {
        val counting = on && !locked
        val clamped = at.coerceIn(from, to)
        if (counting && countingSince == null) {
            countingSince = clamped
        } else if (!counting && countingSince != null) {
            total += (clamped - countingSince!!).coerceAtLeast(0L)
            countingSince = null
        }
    }

    for (event in events) {
        if (event.at > to) break
        when (event.kind) {
            ScreenEvent.Kind.SCREEN_ON -> on = true
            // A screen that goes off and straight back on without locking - a lock delay, a
            // trusted place - reports no lock, and is counted again the moment it is on.
            ScreenEvent.Kind.SCREEN_OFF -> on = false
            ScreenEvent.Kind.LOCKED -> locked = true
            ScreenEvent.Kind.UNLOCKED -> locked = false
        }
        update(event.at)
    }
    // Still on and unlocked right now: that counts up to now.
    countingSince?.let { total += (to - it).coerceAtLeast(0L) }
    return total
}
