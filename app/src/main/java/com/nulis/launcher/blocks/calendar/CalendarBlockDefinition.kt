// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.calendar

import com.nulis.launcher.blocks.GridSpan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.apps.SystemApp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PermissionScreen
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The week, and what is in it. The week strip needs no permission at all - it is a calendar in
 * the sense a paper one is. The agenda needs read access to the phone's calendar, asked for
 * behind an explanation and declinable; declined, the block simply keeps being a week strip.
 */
object CalendarBlockDefinition : BlockDefinition {
    override val type = "calendar"
    override val label = "Calendar"

    override val minSpan = GridSpan(3, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(WeekStyle, AgendaStyle, NextStyle)
    override val previewHeight get() = 110.dp

    /** Request arg that opens the explanation directly. */
    const val PERMISSION = "permission"

    override fun tapAction(block: Block, context: BlockContext): () -> Unit = {
        if (context.calendar.granted) {
            context.openSystemApp(SystemApp.CALENDAR)
        } else {
            context.openScreen(ScreenRequest(type, PERMISSION))
        }
    }

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) {
        if (!context.calendar.granted || request.arg == PERMISSION) {
            CalendarPermissionExplanation(context, onClose)
        } else {
            AgendaScreen(context, onClose)
        }
    }

    /** Seven days with today marked, and a dot under any day that has something on it. */
    private object WeekStyle : BlockStyle {
        override val id = "week"
        override val label = "Week"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val type = NulisTheme.type
            val today = context.time.toLocalDate()
            val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
            val start = remember(today, firstDay) {
                today.minusDays(((today.dayOfWeek.value - firstDay.value) + 7) % 7L)
            }
            val cell = if (wide) 40.dp else 30.dp
            BlockColumn(modifier) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 5.dp)) {
                    repeat(7) { index ->
                        val day = start.plusDays(index.toLong())
                        val isToday = day == today
                        val events = context.calendar.on(day)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                                style = type.label,
                                color = if (isToday) colors.onBackground else colors.tertiary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .background(if (isToday) colors.onBackground else colors.surface, NulisShapes.pill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    style = type.displayS.copy(fontSize = if (wide) 17.sp else 13.sp),
                                    color = if (isToday) colors.background else colors.secondary,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            // Up to three dots: a day with things on it should look different
                            // from an empty one at a glance, without counting.
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(4.dp)) {
                                events.take(3).forEach { event ->
                                    Box(Modifier.size(3.dp).background(dotColor(event, colors.secondary), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Today's entries, each with its time. */
    private object AgendaStyle : BlockStyle {
        override val id = "agenda"
        override val label = "Agenda"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val state = context.calendar
            val today = context.time.toLocalDate()
            val events = state.on(today)
            BlockColumn(modifier) {
                BlockCaptionRow("Today", trailing = if (state.granted) "${events.size}" else null)
                Spacer(Modifier.height(4.dp))
                when {
                    !state.granted -> Hint("Tap to show your calendar")
                    events.isEmpty() -> Hint("Nothing in the diary")
                    else -> events.take(if (wide) 5 else 3).forEach { event ->
                        EventRow(event, wide)
                    }
                }
            }
        }
    }

    /** Only the next thing, big enough to read on the way out of the door. */
    private object NextStyle : BlockStyle {
        override val id = "next"
        override val label = "Next"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val state = context.calendar
            val next = state.next(context.time)
            BlockColumn(modifier) {
                BlockCaptionRow("Next")
                Spacer(Modifier.height(4.dp))
                when {
                    !state.granted -> Hint("Tap to show your calendar")
                    next == null -> Hint("Nothing coming up")
                    else -> {
                        Text(
                            text = next.title,
                            style = if (wide) NulisTheme.type.bodyXl.copy(fontSize = 26.sp, lineHeight = 32.sp) else NulisTheme.type.bodyL,
                            color = colors.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = blockAlign().textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        BlockCaptionRow(whenText(next, context.time))
                    }
                }
            }
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text = text,
            style = NulisTheme.type.bodyM,
            color = NulisTheme.colors.secondary,
            textAlign = blockAlign().textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun EventRow(event: CalendarEvent, wide: Boolean) {
        val colors = NulisTheme.colors
        BlockRow(Modifier.padding(vertical = if (wide) 5.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(dotColor(event, colors.secondary), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                text = event.title,
                style = if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Caption(if (event.allDay) "all day" else event.start.format(TimeFormat))
        }
    }

    private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

    /** "in 40 min", "14:30", "Thu 25 Sep" - whichever is the most useful thing to say. */
    private fun whenText(event: CalendarEvent, now: LocalDateTime): String {
        val minutes = java.time.Duration.between(now, event.start).toMinutes()
        return when {
            minutes in 0..90 -> "in $minutes min"
            event.date == now.toLocalDate() -> if (event.allDay) "today, all day" else "today ${event.start.format(TimeFormat)}"
            event.date == now.toLocalDate().plusDays(1) -> if (event.allDay) "tomorrow" else "tomorrow ${event.start.format(TimeFormat)}"
            else -> event.start.format(DayFormat)
        }
    }

    /**
     * A calendar's own colour if it has a usable one, otherwise the theme's secondary. Some
     * calendars report black, which on a black background would be an invisible dot.
     */
    private fun dotColor(event: CalendarEvent, fallback: Color): Color {
        if (event.color == 0) return fallback
        val color = Color(event.color)
        return if (color.red + color.green + color.blue < 0.15f) fallback else color
    }
}

@Composable
private fun CalendarPermissionExplanation(context: BlockContext, onClose: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        context.calendarActions.permissionChanged()
    }
    PermissionScreen(
        title = "Calendar",
        explanation = "To show what is in your diary, Nulis needs to read your calendar. The week strip works without it.",
        points = listOf(
            "Nulis reads the title, time and colour of events in the next two weeks. Nothing is written, changed or deleted.",
            "Everything stays on this phone. Nulis has no internet permission.",
            "Revoke it any time in app settings; the block goes back to being a plain week.",
        ),
        onAllow = { launcher.launch(Manifest.permission.READ_CALENDAR) },
        onNotNow = onClose,
    )
}

/** Two weeks of diary, grouped by day. */
@Composable
private fun AgendaScreen(context: BlockContext, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    val events = context.calendar.events
    val byDay = remember(events) { events.groupBy { it.date } }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    NulisScreen(label = "Calendar", title = "${events.size} coming up", onBack = onClose) {
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            byDay.forEach { (date, dayEvents) ->
                item(key = "day-$date") {
                    Spacer(Modifier.height(16.dp))
                    com.nulis.launcher.ui.components.SectionLabel(
                        text = if (date == LocalDate.now()) "Today" else date.format(dayFormat),
                    )
                }
                items(dayEvents, key = { it.id.toString() + it.start }) { event ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = if (event.allDay) "all day" else event.start.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = NulisTheme.type.label,
                            color = colors.tertiary,
                            modifier = Modifier.width(72.dp).padding(top = 3.dp),
                        )
                        Text(
                            text = event.title,
                            style = NulisTheme.type.bodyL,
                            color = colors.onBackground,
                        )
                    }
                    com.nulis.launcher.ui.components.Hairline()
                }
            }
        }
    }
}

/** Re-checking the permission after the system prompt; implemented by the view model. */
interface CalendarActions {
    fun permissionChanged()

    object None : CalendarActions {
        override fun permissionChanged() = Unit
    }
}
