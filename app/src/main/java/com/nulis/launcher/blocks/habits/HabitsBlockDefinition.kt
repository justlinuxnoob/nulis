// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.ChecklistRows
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.blocks.writing.Habit
import com.nulis.launcher.blocks.writing.Habits
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The few things somebody means to do every day, and whether they did.
 *
 * A habit tracker on the home screen is the one place it is looked at without being opened: a
 * row of dots for the week, filled in or not, where the clock already is. Tapping a habit ticks
 * today; the full screen adds, removes and fixes a day that was forgotten. Nothing is scored,
 * nothing nags and nothing is sent anywhere - it is a row of dots, and it is yours.
 */
object HabitsBlockDefinition : BlockDefinition {
    override val type = "habits"
    override val label = "Habits"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(WeekStyle, TodayStyle, StreakStyle)
    override val previewHeight get() = 96.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val habits = context.writing.habits
        if (habits.habits.isEmpty()) return "Habits, none yet"
        return "Habits, ${habits.doneOn(context.time.toLocalDate())} of ${habits.habits.size} done today"
    }

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) = HabitsScreen(context, onClose)

    /** Each habit with the last seven days beside it; a tap ticks today. */
    private object WeekStyle : BlockStyle {
        override val id = "week"
        override val label = "Week"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val habits = context.writing.habits
            val today = context.time.toLocalDate()
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier) {
                BlockCaptionRow(
                    label = "Habits",
                    trailing = if (habits.habits.isEmpty()) null else "${habits.doneOn(today)} of ${habits.habits.size} today",
                    modifier = Modifier.opensScreen(context).padding(bottom = 4.dp),
                )
                if (habits.habits.isEmpty()) {
                    Empty(context, rows = if (wide) 3 else 2)
                } else {
                    // Every name gets the width of the longest, so the seven days line up in
                    // columns down the block whichever way it is aligned: a week read across
                    // the rows is the point of drawing it at all.
                    val shown = habits.habits.take(Habits.MaxHabits)
                    val measurer = rememberTextMeasurer()
                    val style = NulisTheme.type.bodyL
                    val density = LocalDensity.current
                    val longest = remember(shown, style, density) {
                        shown.maxOf { measurer.measure(it.name, style, maxLines = 1).size.width }
                    }
                    val nameWidth = with(density) { longest.toDp() }.coerceAtMost(blockArea().width * 0.5f)
                    shown.forEach { habit -> WeekRow(habit, habits, today, context, wide, nameWidth) }
                }
            }
        }
    }

    /** Today's habits as pills, filled once done. The quickest way to tick three things. */
    private object TodayStyle : BlockStyle {
        override val id = "today"
        override val label = "Today"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val habits = context.writing.habits
            val today = context.time.toLocalDate()
            BlockColumn(modifier) {
                BlockCaptionRow(
                    label = "Today",
                    trailing = if (habits.habits.isEmpty()) null else "${habits.doneOn(today)} of ${habits.habits.size}",
                    modifier = Modifier.opensScreen(context).padding(bottom = 8.dp),
                )
                if (habits.habits.isEmpty()) {
                    Empty(context, rows = 2)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, blockAlign().horizontal),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        habits.habits.forEach { habit -> TodayPill(habit, habits.isDone(habit.id, today), today, context) }
                    }
                }
            }
        }
    }

    /** The longest run going, big, and whose it is. */
    private object StreakStyle : BlockStyle {
        override val id = "streak"
        override val label = "Streak"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val habits = context.writing.habits
            val today = context.time.toLocalDate()
            val wide = block.size == BlockSize.WIDE
            val best = habits.habits.maxByOrNull { habits.streak(it.id, today) }
            val days = best?.let { habits.streak(it.id, today) } ?: 0
            BlockColumn(modifier.opensScreen(context)) {
                BlockRow(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (best == null) "--" else days.toString(),
                        style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM,
                        color = colors.onBackground,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    Caption(if (days == 1) "Day" else "Days", modifier = Modifier.padding(bottom = if (wide) 8.dp else 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        best == null -> "Tap to add a habit"
                        days == 0 -> "A fresh start today"
                        else -> "of ${best.name}"
                    },
                    style = NulisTheme.type.bodyM,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = blockAlign().textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun Modifier.opensScreen(context: BlockContext): Modifier = this
        .pressFeedback()
        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }

    /** Nothing tracked yet: the shape of a checklist, and a tap that starts one. */
    @Composable
    private fun Empty(context: BlockContext, rows: Int) {
        Box(Modifier.fillMaxWidth().opensScreen(context).padding(vertical = 6.dp)) {
            ChecklistRows(rows = rows)
        }
    }

    @Composable
    private fun WeekRow(habit: Habit, habits: Habits, today: LocalDate, context: BlockContext, wide: Boolean, nameWidth: Dp) {
        val colors = NulisTheme.colors
        val haptics = LocalHapticFeedback.current
        val done = habits.isDone(habit.id, today)
        val streak = habits.streak(habit.id, today)
        val toggle = {
            haptics.performHapticFeedback(if (done) NulisHaptics.toggleOff else NulisHaptics.toggleOn)
            context.writingActions.toggleHabit(habit.id, today)
        }
        BlockRow(
            modifier = Modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { toggle() }
                .clearAndSetSemantics {
                    contentDescription = habit.name
                    stateDescription = (if (done) "Done today" else "Not done today") +
                        if (streak > 1) ", $streak days in a row" else ""
                    onClick(label = if (done) "Untick today" else "Tick today") { toggle(); true }
                }
                .padding(vertical = if (wide) 7.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                habit.name,
                style = NulisTheme.type.bodyL,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(nameWidth),
            )
            Spacer(Modifier.width(14.dp))
            WeekDots(habits, habit.id, today, dot = if (wide) 10.dp else 8.dp)
        }
    }

    @Composable
    private fun TodayPill(habit: Habit, done: Boolean, today: LocalDate, context: BlockContext) {
        val colors = NulisTheme.colors
        val haptics = LocalHapticFeedback.current
        Box(
            Modifier
                .pressFeedback()
                .background(if (done) colors.onBackground else colors.surface, NulisShapes.pill)
                .border(1.dp, if (done) colors.onBackground else colors.hairline, NulisShapes.pill)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(if (done) NulisHaptics.toggleOff else NulisHaptics.toggleOn)
                    context.writingActions.toggleHabit(habit.id, today)
                }
                .semantics { stateDescription = if (done) "Done today" else "Not done today" }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(habit.name, style = NulisTheme.type.bodyM, color = if (done) colors.background else colors.onBackground, maxLines = 1)
        }
    }
}

/**
 * Seven days, oldest first and today last: filled for a day that was done, a ring for one that
 * was not. Today's ring is drawn in the ink so the one dot that can still change stands out.
 */
@Composable
private fun WeekDots(habits: Habits, habitId: String, today: LocalDate, dot: Dp) {
    val colors = NulisTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(dot * 0.6f), verticalAlignment = Alignment.CenterVertically) {
        for (back in 6 downTo 0) {
            val day = today.minusDays(back.toLong())
            val done = habits.isDone(habitId, day)
            Box(
                Modifier
                    .size(dot)
                    .then(
                        if (done) {
                            Modifier.background(colors.onBackground, CircleShape)
                        } else {
                            Modifier.border(1.dp, if (back == 0) colors.secondary else colors.hairline, CircleShape)
                        },
                    ),
            )
        }
    }
}

/**
 * Every habit, its streak and its week, with each day tappable so a forgotten tick can be put
 * right. Adding one is a line of text; there is no schedule, no goal and no reminder.
 */
@Composable
private fun HabitsScreen(context: BlockContext, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val actions = context.writingActions
    val habits = context.writing.habits
    val today = context.time.toLocalDate()
    val locale = Locale.getDefault()
    var draft by rememberSaveable { mutableStateOf("") }
    val full = habits.habits.size >= Habits.MaxHabits
    fun submit() {
        if (draft.isBlank() || full) return
        haptics.performHapticFeedback(NulisHaptics.confirm)
        actions.addHabit(draft)
        draft = ""
    }
    NulisScreen(
        label = "Habits",
        title = if (habits.habits.isEmpty()) "Start one" else "${habits.doneOn(today)} of ${habits.habits.size} today",
        onBack = onClose,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = draft,
                onValueChange = { draft = it.take(40) },
                placeholder = if (full) "Eight is plenty" else "Read, walk, water the plants",
                modifier = Modifier.weight(1f),
                singleLine = true,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
            )
            Spacer(Modifier.width(12.dp))
            PillButton(text = "Add", onClick = { submit() }, tone = PillTone.Primary, compact = true, enabled = draft.isNotBlank() && !full)
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
        LazyColumn(Modifier.fillMaxWidth().weight(1f).fadeTop()) {
            items(habits.habits, key = { it.id }) { habit ->
                val streak = habits.streak(habit.id, today)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(habit.name, style = NulisTheme.type.bodyL, color = colors.onBackground)
                            Caption(
                                when (streak) {
                                    0 -> "No run going"
                                    1 -> "1 day"
                                    else -> "$streak days in a row"
                                },
                            )
                        }
                        NulisIconButton(Glyph.Close, onClick = { actions.deleteHabit(habit.id) }, contentDescription = "Remove ${habit.name}")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (back in 6 downTo 0) {
                            val day = today.minusDays(back.toLong())
                            val done = habits.isDone(habit.id, day)
                            val name = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .pressFeedback()
                                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                                        haptics.performHapticFeedback(if (done) NulisHaptics.toggleOff else NulisHaptics.toggleOn)
                                        actions.toggleHabit(habit.id, day)
                                    }
                                    .clearAndSetSemantics {
                                        contentDescription = "$name ${day.dayOfMonth}"
                                        stateDescription = if (done) "Done" else "Not done"
                                    }
                                    .padding(4.dp),
                            ) {
                                Caption(name.take(2))
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .then(
                                            if (done) {
                                                Modifier.background(colors.onBackground, CircleShape)
                                            } else {
                                                Modifier.border(1.dp, if (back == 0) colors.secondary else colors.hairline, CircleShape)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${day.dayOfMonth}",
                                        style = NulisTheme.type.label,
                                        color = if (done) colors.background else colors.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(12.dp))
        Caption("Tap a day to tick it or untick it. A streak waits for today until midnight.", lines = 2)
        Spacer(Modifier.height(16.dp))
    }
}
