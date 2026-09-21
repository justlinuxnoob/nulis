// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.date

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nulis.launcher.apps.SystemApp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Today, eight ways. Every skin follows the Look and the block's alignment, and a tap on any of
 * them opens the phone's own calendar.
 */
object DateBlockDefinition : BlockDefinition {
    override val type = "date"
    override val label = "Date"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 1)

    override val styles: List<BlockStyle> = listOf(
        UppercaseLineStyle,
        BigDayStyle,
        GhostMonthStyle,
        WeekStripStyle,
        DayProgressStyle,
        YearDotsStyle,
        IsoStyle,
        WordsStyle,
    )

    override fun tapAction(block: Block, context: BlockContext): () -> Unit =
        { context.openSystemApp(SystemApp.CALENDAR) }

    // The year-dot field and the week strip need more than a text line's worth of room.
    override val previewHeight get() = 104.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val locale = Locale.getDefault()
        return context.time.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
    }

    /** "SUNDAY, 20 SEPTEMBER" on one line in the display face. */
    private object UppercaseLineStyle : BlockStyle {
        override val id = "uppercase"
        override val label = "Uppercase"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
            val wide = block.size == BlockSize.WIDE
            // A date is one line or it is nothing: split across three, "SEPTEMBE / R" reads as a
            // fault. In a half or third of a row the type shrinks instead.
            FitWidth(modifier, align = blockAlign().horizontal) {
                Text(
                    text = context.time.format(formatter).uppercase(locale),
                    style = NulisTheme.type.displayS.copy(
                        fontSize = if (wide) 20.sp else 15.sp,
                        lineHeight = if (wide) 24.sp else 18.sp,
                        letterSpacing = 0.06.em,
                    ),
                    color = NulisTheme.colors.secondary,
                    textAlign = blockAlign().textAlign,
                    maxLines = 1,
                )
            }
        }
    }

    /** A big day number with the weekday and month as mono labels beside it. */
    private object BigDayStyle : BlockStyle {
        override val id = "bigday"
        override val label = "Big day"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
            val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val type = NulisTheme.type
            BlockRow(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.time.dayOfMonth.toString(),
                    style = (if (wide) type.displayL else type.displayM).copy(
                        fontSize = if (wide) 72.sp else 44.sp,
                        lineHeight = if (wide) 72.sp else 44.sp,
                    ),
                    color = colors.onBackground,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.padding(top = 2.dp)) {
                    Text(context.time.format(weekday).uppercase(locale), style = if (wide) type.labelL else type.label, color = colors.onBackground)
                    Text(context.time.format(month).uppercase(locale), style = if (wide) type.labelL else type.label, color = colors.secondary)
                }
            }
        }
    }

    /**
     * The month as a watermark with the day number sitting on top of it. In the Clean look the
     * month is a true outline; in the Dot look an outlined dot-matrix face is just noise, so it
     * is a filled wash one step off the background instead. Either way the month is the texture
     * and the number is the message, and the weekday stays clear of both.
     */
    private object GhostMonthStyle : BlockStyle {
        override val id = "ghostmonth"
        override val label = "Ghost month"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }
            val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val type = NulisTheme.type
            val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
            val align = blockAlign()
            val monthSize = if (wide) 46.sp else 30.sp
            val base = type.displayM.copy(fontSize = monthSize, lineHeight = monthSize, letterSpacing = 0.02.em)
            BlockColumn(modifier) {
                Box(contentAlignment = align.box) {
                    Text(
                        text = context.time.format(month).uppercase(locale),
                        style = if (dotted) base else base.copy(drawStyle = Stroke(width = if (wide) 2.2f else 1.5f)),
                        color = if (dotted) colors.surfaceRaised else colors.tertiary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = context.time.dayOfMonth.toString(),
                        style = type.displayL.copy(
                            fontSize = if (wide) 76.sp else 46.sp,
                            lineHeight = if (wide) 76.sp else 46.sp,
                        ),
                        color = colors.onBackground,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = context.time.format(weekday).uppercase(locale),
                    style = if (wide) type.labelL else type.label,
                    color = colors.secondary,
                )
            }
        }
    }

    /** This week as seven marks, today filled in. */
    private object WeekStripStyle : BlockStyle {
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
                val shift = ((today.dayOfWeek.value - firstDay.value) + 7) % 7
                today.minusDays(shift.toLong())
            }
            val cell = if (wide) 40.dp else 30.dp
            BlockColumn(modifier) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 5.dp)) {
                    repeat(7) { index ->
                        val day = start.plusDays(index.toLong())
                        val isToday = day == today
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale).uppercase(locale),
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
                        }
                    }
                }
            }
        }
    }

    /** How much of today is behind you, as a bar with the hours left beside it. */
    private object DayProgressStyle : BlockStyle {
        override val id = "dayprogress"
        override val label = "Day bar"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val minutes = context.time.hour * 60 + context.time.minute
            val fraction = minutes / 1440f
            val hoursLeft = (1440 - minutes + 59) / 60
            BlockColumn(modifier) {
                com.nulis.launcher.blocks.BlockCaptionRow(
                    label = context.time.format(formatter),
                    trailing = "${(fraction * 100).toInt()}%",
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(if (wide) 1f else 0.6f)
                        .height(if (wide) 10.dp else 6.dp)
                        .background(colors.surfaceRaised, NulisShapes.pill),
                    contentAlignment = blockAlign().box,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(if (wide) 10.dp else 6.dp)
                            .background(colors.onBackground, NulisShapes.pill),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Caption(if (hoursLeft == 1) "1 hour left" else "$hoursLeft hours left")
            }
        }
    }

    /** The year as a field of dots, one per day, filled up to today. */
    private object YearDotsStyle : BlockStyle {
        override val id = "yeardots"
        override val label = "Year dots"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val today = context.time.toLocalDate()
            val daysInYear = if (today.isLeapYear) 366 else 365
            val elapsed = today.dayOfYear
            val columns = if (wide) 28 else 19
            val rows = (daysInYear + columns - 1) / columns
            BlockColumn(modifier) {
                Canvas(
                    Modifier
                        .fillMaxWidth(if (wide) 1f else 0.62f)
                        .height((rows * (if (wide) 9 else 7)).dp),
                ) {
                    val stepX = size.width / columns
                    val stepY = size.height / rows
                    val r = minOf(stepX, stepY) * 0.26f
                    repeat(daysInYear) { index ->
                        val x = (index % columns + 0.5f) * stepX
                        val y = (index / columns + 0.5f) * stepY
                        when {
                            index + 1 < elapsed -> drawCircle(colors.secondary, r, Offset(x, y))
                            index + 1 == elapsed -> drawCircle(colors.onBackground, r * 1.7f, Offset(x, y))
                            else -> drawCircle(colors.hairline, r, Offset(x, y))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Caption("Day $elapsed of $daysInYear · ${daysInYear - elapsed} left")
            }
        }
    }

    /** The machine-readable one: 2026-09-21, week 39, day 264. */
    private object IsoStyle : BlockStyle {
        override val id = "iso"
        override val label = "ISO"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            val today = context.time.toLocalDate()
            val week = remember(today, locale) { today.get(WeekFields.of(locale).weekOfWeekBasedYear()) }
            BlockColumn(modifier) {
                Text(
                    text = today.toString(),
                    style = NulisTheme.type.mono.copy(fontSize = if (wide) 30.sp else 20.sp, letterSpacing = (-0.02).em),
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Caption("W$week · Day ${today.dayOfYear}")
            }
        }
    }

    /** The date spelled out, the way you would say it. */
    private object WordsStyle : BlockStyle {
        override val id = "words"
        override val label = "Words"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val wide = block.size == BlockSize.WIDE
            val today = context.time.toLocalDate()
            val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }
            val text = "${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).lowercase(locale)}, " +
                "the ${ordinalWord(today.dayOfMonth)} of ${today.format(month).lowercase(locale)}"
            BlockBox(modifier) {
                Text(
                    text = text,
                    style = NulisTheme.type.displayS.copy(
                        fontSize = if (wide) 26.sp else 18.sp,
                        lineHeight = if (wide) 32.sp else 23.sp,
                    ),
                    color = NulisTheme.colors.onBackground,
                    textAlign = if (blockAlign() == com.nulis.launcher.blocks.BlockAlign.LEFT) TextAlign.Start else blockAlign().textAlign,
                    modifier = Modifier.fillMaxWidth(if (wide) 0.82f else 1f),
                )
            }
        }
    }
}

/** Days since the start of the year, used by the dot field. Kept for readability at the call site. */
internal fun dayOfYear(date: LocalDate): Int = ChronoUnit.DAYS.between(date.withDayOfYear(1), date).toInt() + 1

private val ORDINAL_ONES = listOf(
    "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth",
    "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth",
    "seventeenth", "eighteenth", "nineteenth", "twentieth",
)

private val CARDINAL_ONES = listOf(
    "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth",
)

/** 1..31 written out: "first", "twenty-second", "thirty-first". */
private fun ordinalWord(day: Int): String = when {
    day <= 20 -> ORDINAL_ONES[day]
    day == 30 -> "thirtieth"
    day < 30 -> "twenty-${CARDINAL_ONES[day - 20]}"
    else -> "thirty-${CARDINAL_ONES[day - 30]}"
}
