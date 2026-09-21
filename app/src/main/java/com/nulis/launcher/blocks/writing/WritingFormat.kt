// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.writing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Built per call: a formatter made once would keep the language the app first started in. */
private fun dayFormat() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** "23:12" today, "Sat 19 Sep · 23:12" on other days. */
fun formatStamp(epochMillis: Long, now: LocalDateTime = LocalDateTime.now()): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    val time = at.format(timeFormat)
    return if (at.toLocalDate() == now.toLocalDate()) time else "${at.format(dayFormat())} · $time"
}

fun isToday(epochMillis: Long, today: LocalDate = LocalDate.now()): Boolean =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toLocalDate() == today
