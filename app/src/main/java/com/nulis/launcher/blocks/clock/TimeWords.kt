// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.clock

import java.time.LocalTime

/** English clock words, e.g. 7:06 -> "six past seven", 7:45 -> "quarter to eight". */
fun timeInWords(time: LocalTime): String {
    val minute = time.minute
    val thisHour = hourWord(time.hour)
    val nextHour = hourWord(time.hour + 1)
    return when {
        minute == 0 -> "$thisHour o'clock"
        minute == 15 -> "quarter past $thisHour"
        minute == 30 -> "half past $thisHour"
        minute == 45 -> "quarter to $nextHour"
        minute < 30 -> "${numberWord(minute)} past $thisHour"
        else -> "${numberWord(60 - minute)} to $nextHour"
    }
}

private fun hourWord(hour24: Int): String = HOURS[(hour24 + 11) % 12]

private fun numberWord(n: Int): String = when {
    n < 20 -> ONES[n]
    n % 10 == 0 -> "twenty"
    else -> "twenty-${ONES[n % 10]}"
}

private val HOURS = listOf(
    "one", "two", "three", "four", "five", "six",
    "seven", "eight", "nine", "ten", "eleven", "twelve",
)

private val ONES = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)
