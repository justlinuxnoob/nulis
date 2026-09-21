// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.focus.FocusState
import com.nulis.launcher.blocks.screentime.WeeklyScreenTime
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.blocks.steps.StepsState
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.format.TextStyle
import java.util.Locale

/**
 * The week, in four numbers and two charts: how long the screen was on, how far you walked, how
 * many minutes of focus you kept, and which apps took the most of it. Read-only, on-device, and
 * built from data the blocks already hold.
 */
@Composable
fun WeeklySummaryScreen(
    screenTime: WeeklyScreenTime,
    steps: StepsState,
    focus: FocusState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    val type = NulisTheme.type
    val locale = Locale.getDefault()

    Box(modifier.fillMaxSize().background(colors.background)) {
        NulisScreen(label = "Last seven days", title = "Your week", onBack = onClose) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Four headline numbers, two to a row.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(
                        value = if (screenTime.granted) formatMinutes(screenTime.totalMinutes) else "--",
                        label = "on screen",
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        value = if (screenTime.granted) "${formatMinutes(screenTime.dailyAverage)}" else "--",
                        label = "a day",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(
                        value = if (steps.granted) "%,d".format(locale, steps.history.sumOf { it.steps }) else "--",
                        label = "steps",
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        value = "${focus.weekMinutes}",
                        label = "mindful minutes",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("Screen time", trailing = if (screenTime.granted) null else "No access")
                Spacer(Modifier.height(8.dp))
                // "Nothing recorded yet" promises the numbers will turn up on their own. When the
                // permission is simply off they never will, and saying which one it is turns a
                // dead end into something the reader can act on.
                if (screenTime.days.isEmpty()) {
                    Caption(
                        if (screenTime.granted) "Nothing recorded yet"
                        else "Usage access is off, so there is nothing to count. The screen time block asks for it.",
                        lines = 2,
                    )
                } else {
                    DayChart(screenTime, Modifier.fillMaxWidth().height(140.dp))
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("Steps", trailing = if (steps.granted) "goal ${"%,d".format(locale, steps.goal)}" else "No access")
                Spacer(Modifier.height(8.dp))
                if (steps.history.isEmpty()) {
                    Caption(
                        if (steps.granted) "Nothing recorded yet"
                        else "The step counter is off, so there is nothing to count. The steps block asks for it.",
                        lines = 2,
                    )
                } else {
                    com.nulis.launcher.blocks.steps.WeekChart(
                        state = steps,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        labels = true,
                        values = true,
                    )
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("Most opened", trailing = "${screenTime.apps.size}")
                Spacer(Modifier.height(4.dp))
                if (screenTime.apps.isEmpty()) {
                    Caption(
                        if (screenTime.granted) "Nothing opened yet today"
                        else "Usage access is off, so there is nothing to count.",
                        lines = 2,
                    )
                } else {
                    val top = screenTime.apps.take(8)
                    val max = top.firstOrNull()?.minutes?.coerceAtLeast(1) ?: 1
                    top.forEach { app ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.label,
                                    style = type.bodyL,
                                    color = colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Caption(formatMinutes(app.minutes))
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceRaised, NulisShapes.pill)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(app.minutes / max.toFloat())
                                        .height(6.dp)
                                        .background(colors.onBackground, NulisShapes.pill),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("Focus")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = focusSentence(focus),
                    style = type.bodyM,
                    color = colors.secondary,
                )
                Spacer(Modifier.height(24.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Caption("Everything here is read on this phone and stays on it", lines = 2)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        com.nulis.launcher.ui.components.FitWidth {
            Text(
                text = value,
                style = NulisTheme.type.displayL.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = NulisTheme.colors.onBackground,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        Caption(label)
    }
}

/** Seven bars, today last, with the average as a hairline across. */
@Composable
private fun DayChart(week: WeeklyScreenTime, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    val locale = Locale.getDefault()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val days = week.days
            if (days.isEmpty()) return@Canvas
            val max = days.maxOf { it.minutes }.coerceAtLeast(1)
            val gap = 8.dp.toPx()
            val barWidth = (size.width - gap * (days.size - 1)) / days.size
            val averageY = size.height - size.height * (week.dailyAverage / max.toFloat())
            drawLine(colors.hairline, Offset(0f, averageY), Offset(size.width, averageY), 1.dp.toPx())
            days.forEachIndexed { index, day ->
                val height = (size.height * (day.minutes / max.toFloat())).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = if (index == days.lastIndex) colors.onBackground else colors.tertiary,
                    topLeft = Offset(index * (barWidth + gap), size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            week.days.forEach { day ->
                Text(
                    text = NulisTheme.type.labelCase(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2)),
                    style = NulisTheme.type.label,
                    color = NulisTheme.colors.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun focusSentence(focus: FocusState): String = when {
    focus.weekMinutes == 0 -> "No focus sessions this week. The Focus block starts one."
    focus.todayMinutes > 0 -> "${focus.weekMinutes} minutes this week, ${focus.todayMinutes} of them today."
    else -> "${focus.weekMinutes} minutes this week. None yet today."
}
